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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.runningFold
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

    override fun initialize() {
        if (!initialized.compareAndSet(expectedValue = false, newValue = true)) return

        appScope.launch(ioDispatcher) {
            tokenDataSource.authState
                .distinctUntilChanged()
                .collectLatest { authState ->
                    when (authState) {
                        is AuthState.Authenticated -> {
                            chatSyncScheduler.schedulePeriodicSync()
                            observeReconnectTransitions()
                        }
                        is AuthState.Unauthenticated -> {
                            chatSyncScheduler.cancelAllSyncWork()
                        }
                    }
                }
        }
    }

    private suspend fun observeReconnectTransitions() {
        realtimeDataSource.connectionState
            .runningFold<SocketConnectionState, Pair<ReconnectState, Boolean>>(ReconnectState.Initial to false) { (state, _), current ->
                when (current) {
                    is SocketConnectionState.Connected -> {
                        if (state is ReconnectState.Dropped) {
                            ReconnectState.ConnectedOnce to true
                        } else {
                            ReconnectState.ConnectedOnce to false
                        }
                    }
                    is SocketConnectionState.Disconnected,
                    is SocketConnectionState.Failed -> {
                        if (state is ReconnectState.ConnectedOnce) {
                            ReconnectState.Dropped to false
                        } else {
                            state to false
                        }
                    }
                    is SocketConnectionState.Connecting,
                    is SocketConnectionState.AuthenticationFailed -> {
                        state to false
                    }
                }
            }
            .collect { (_, triggerCatchUp) ->
                if (triggerCatchUp) {
                    try {
                        performCatchUpSync()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                        // Ignore non-cancellation errors
                    }
                }
            }
    }

    private sealed interface ReconnectState {
        data object Initial : ReconnectState
        data object ConnectedOnce : ReconnectState
        data object Dropped : ReconnectState
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
