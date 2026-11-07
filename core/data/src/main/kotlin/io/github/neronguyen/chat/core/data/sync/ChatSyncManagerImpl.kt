package io.github.neronguyen.chat.core.data.sync

import arrow.core.Either
import io.github.neronguyen.chat.core.common.network.AppScope
import io.github.neronguyen.chat.core.common.network.ChatDispatchers
import io.github.neronguyen.chat.core.common.network.Dispatcher
import io.github.neronguyen.chat.core.data.repository.ChatMessageRepository
import io.github.neronguyen.chat.core.data.repository.ChatRoomRepository
import io.github.neronguyen.chat.core.datastore.TokenDataSource
import io.github.neronguyen.chat.core.model.AuthState
import io.github.neronguyen.chat.core.model.error.DataError
import io.github.neronguyen.chat.core.network.ChatRealtimeDataSource
import io.github.neronguyen.chat.core.network.model.websocket.SocketConnectionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
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
    private val realtimeDataSource: ChatRealtimeDataSource,
    @AppScope private val appScope: CoroutineScope,
    @Dispatcher(ChatDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : ChatSyncManager {

    private val initialized = AtomicBoolean(false)
    private var hasConnectedOnce = false
    private var wasDisconnected = false

    override fun initialize() {
        if (!initialized.compareAndSet(expectedValue = false, newValue = true)) return

        // 1. Observe auth state: schedule periodic work when Authenticated; cancel when Unauthenticated
        appScope.launch(ioDispatcher) {
            tokenDataSource.authState
                .distinctUntilChanged()
                .collect { authState ->
                    when (authState) {
                        is AuthState.Authenticated -> {
                            chatSyncScheduler.schedulePeriodicSync()
                        }
                        is AuthState.Unauthenticated -> {
                            chatSyncScheduler.cancelAllSyncWork()
                            hasConnectedOnce = false
                            wasDisconnected = false
                        }
                    }
                }
        }

        // 2. Observe realtime connection state: when reconnecting from Disconnected/Failed -> Connected, trigger catch-up
        appScope.launch(ioDispatcher) {
            realtimeDataSource.connectionState.collect { connectionState ->
                when (connectionState) {
                    is SocketConnectionState.Connected -> {
                        if (hasConnectedOnce && wasDisconnected) {
                            wasDisconnected = false
                            try {
                                performCatchUpSync()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Throwable) {
                                // Ignore non-cancellation errors
                            }
                        } else {
                            hasConnectedOnce = true
                            wasDisconnected = false
                        }
                    }
                    is SocketConnectionState.Disconnected,
                    is SocketConnectionState.Failed -> {
                        if (hasConnectedOnce) {
                            wasDisconnected = true
                        }
                    }
                    is SocketConnectionState.Connecting,
                    is SocketConnectionState.AuthenticationFailed -> Unit
                }
            }
        }
    }

    override suspend fun performCatchUpSync(): Either<DataError, Unit> {
        return when (val syncResult = chatRoomRepository.syncChatsWithChangedRooms()) {
            is Either.Left -> syncResult
            is Either.Right -> {
                val changedRoomIds = syncResult.value
                for (roomId in changedRoomIds) {
                    chatMessageRepository.syncRecentMessages(roomId)
                }
                Either.Right(Unit)
            }
        }
    }

    companion object {
        const val MAX_PREFETCH_ROOMS = 5
    }
}
