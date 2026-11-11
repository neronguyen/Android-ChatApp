package io.github.neronguyen.chat.core.data.sync

import arrow.core.Either
import io.github.neronguyen.chat.core.common.coroutine.SingleFlight
import io.github.neronguyen.chat.core.common.coroutine.mapConcurrent
import io.github.neronguyen.chat.core.common.network.AppScope
import io.github.neronguyen.chat.core.common.network.ChatDispatchers
import io.github.neronguyen.chat.core.common.network.Dispatcher
import io.github.neronguyen.chat.core.data.realtime.RealtimeConnectionManager
import io.github.neronguyen.chat.core.data.repository.ChatMessageRepository
import io.github.neronguyen.chat.core.data.repository.ChatRoomRepository
import io.github.neronguyen.chat.core.data.util.NetworkMonitor
import io.github.neronguyen.chat.core.datastore.TokenDataSource
import io.github.neronguyen.chat.core.model.AuthState
import io.github.neronguyen.chat.core.model.ChatSyncState
import io.github.neronguyen.chat.core.model.RealtimeConnectionState
import io.github.neronguyen.chat.core.model.error.DataError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
@Singleton
internal class ChatSyncManagerImpl @Inject constructor(
    private val chatRoomRepository: ChatRoomRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatSyncScheduler: ChatSyncScheduler,
    private val tokenDataSource: TokenDataSource,
    private val realtimeConnectionManager: RealtimeConnectionManager,
    private val networkMonitor: NetworkMonitor,
    @AppScope private val appScope: CoroutineScope,
    @Dispatcher(ChatDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : ChatSyncManager {

    private val initialized = AtomicBoolean(false)
    private val isOnline = AtomicBoolean(true)
    private val catchUpSyncFlight = SingleFlight<Either<DataError, Unit>>(
        scope = appScope,
        dispatcher = ioDispatcher,
    )
    private val _syncState = MutableStateFlow<ChatSyncState>(ChatSyncState.Idle)
    override val syncState: StateFlow<ChatSyncState> = _syncState.asStateFlow()

    init {
        appScope.launch(ioDispatcher) {
            networkMonitor.isOnline.collect { online ->
                isOnline.store(online)
            }
        }
    }

    override fun initialize() {
        if (!initialized.compareAndSet(expectedValue = false, newValue = true)) return

        appScope.launch(ioDispatcher) {
            tokenDataSource.authState
                .distinctUntilChanged()
                .collectLatest { authState ->
                    when (authState) {
                        is AuthState.Authenticated -> {
                            chatSyncScheduler.schedulePeriodicSync()
                            observeSyncCoordination()
                        }

                        is AuthState.Unauthenticated -> {
                            chatSyncScheduler.cancelAllSyncWork()
                            _syncState.value = ChatSyncState.Idle
                        }
                    }
                }
        }
    }

    private suspend fun observeSyncCoordination() {
        networkMonitor.isOnline
            .distinctUntilChanged()
            .collectLatest { isOnline ->
                this@ChatSyncManagerImpl.isOnline.store(isOnline)
                if (!isOnline) {
                    _syncState.value = ChatSyncState.Offline
                } else {
                    realtimeConnectionManager.connectionState
                        .collectLatest { connState ->
                            when (connState) {
                                RealtimeConnectionState.CONNECTED -> {
                                    try {
                                        performCatchUpSync()
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (_: Throwable) {
                                        // Ignore non-cancellation errors
                                    }
                                }

                                RealtimeConnectionState.CONNECTING -> {
                                    if (_syncState.value !is ChatSyncState.Syncing) {
                                        _syncState.value = ChatSyncState.Connecting
                                    }
                                }

                                RealtimeConnectionState.DISCONNECTED -> {
                                    if (_syncState.value !is ChatSyncState.Failed && _syncState.value !is ChatSyncState.Syncing) {
                                        _syncState.value = ChatSyncState.Connecting
                                    }
                                }

                                RealtimeConnectionState.FAILED -> {
                                    if (_syncState.value !is ChatSyncState.Syncing) {
                                        _syncState.value = ChatSyncState.Failed(DataError.Remote.Unavailable)
                                    }
                                }
                            }
                        }
                }
            }
    }

    override suspend fun performCatchUpSync(): Either<DataError, Unit> =
        catchUpSyncFlight.execute {
            executeCatchUpSync()
        }

    override suspend fun retry(): Either<DataError, Unit> {
        if (realtimeConnectionManager.connectionState.value == RealtimeConnectionState.FAILED) {
            realtimeConnectionManager.reconnect()
        }
        return performCatchUpSync()
    }

    private suspend fun executeCatchUpSync(): Either<DataError, Unit> {
        _syncState.value = ChatSyncState.Syncing
        try {
            return when (val syncResult = chatRoomRepository.syncChatsAndGetChangedRooms()) {
                is Either.Left -> {
                    if (!isOnline.load()) {
                        _syncState.value = ChatSyncState.Offline
                    } else {
                        _syncState.value = ChatSyncState.Failed(syncResult.value)
                    }
                    syncResult
                }

                is Either.Right -> {
                    val changedRoomIds = syncResult.value
                    val prefetchResults =
                        changedRoomIds.mapConcurrent(MAX_CONCURRENT_PREFETCH) { roomId ->
                            chatMessageRepository.syncRecentMessages(roomId)
                        }

                    val errors =
                        prefetchResults.filterIsInstance<Either.Left<DataError>>().map { it.value }
                    if (errors.isNotEmpty()) {
                        val firstError = errors.first()
                        if (!isOnline.load()) {
                            _syncState.value = ChatSyncState.Offline
                        } else {
                            _syncState.value = ChatSyncState.Failed(firstError)
                        }
                        Either.Left(firstError)
                    } else {
                        if (!isOnline.load()) {
                            _syncState.value = ChatSyncState.Offline
                        } else {
                            _syncState.value = ChatSyncState.Synced
                        }
                        Either.Right(Unit)
                    }
                }
            }
        } finally {
            if (_syncState.value is ChatSyncState.Syncing) {
                _syncState.value = if (!isOnline.load()) {
                    ChatSyncState.Offline
                } else {
                    ChatSyncState.Connecting
                }
            }
        }
    }

    private companion object {
        private const val MAX_CONCURRENT_PREFETCH = 3
    }
}
