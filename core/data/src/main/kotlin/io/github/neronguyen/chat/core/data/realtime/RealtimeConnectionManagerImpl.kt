package io.github.neronguyen.chat.core.data.realtime

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.neronguyen.chat.core.common.coroutine.ExponentialBackoff
import io.github.neronguyen.chat.core.common.network.AppScope
import io.github.neronguyen.chat.core.common.network.ChatDispatchers
import io.github.neronguyen.chat.core.common.network.Dispatcher
import io.github.neronguyen.chat.core.data.util.NetworkMonitor
import io.github.neronguyen.chat.core.datastore.TokenDataSource
import io.github.neronguyen.chat.core.model.AuthState
import io.github.neronguyen.chat.core.model.RealtimeConnectionState
import io.github.neronguyen.chat.core.network.ChatRealtimeDataSource
import io.github.neronguyen.chat.core.network.interceptor.TokenRefresher
import io.github.neronguyen.chat.core.network.model.websocket.SocketConnectionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

@Singleton
internal class RealtimeConnectionManagerImpl @Inject constructor(
    private val realtimeDataSource: ChatRealtimeDataSource,
    private val tokenDataSource: TokenDataSource,
    private val tokenRefresher: dagger.Lazy<TokenRefresher>,
    private val networkMonitor: NetworkMonitor,
    @AppScope private val appScope: CoroutineScope,
    @Dispatcher(ChatDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
    private val backoff: ExponentialBackoff = ExponentialBackoff()
) : RealtimeConnectionManager, DefaultLifecycleObserver {

    private val _connectionState = MutableStateFlow(RealtimeConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<RealtimeConnectionState> = _connectionState.asStateFlow()

    private val reconnectRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val isForeground = MutableStateFlow(false)
    private var retryAttempt = 0

    init {
        startSupervision()
    }

    override fun initialize() {
        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        } catch (_: Exception) {
            // ProcessLifecycleOwner may not be available in unit test environments
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        isForeground.value = true
    }

    override fun onStop(owner: LifecycleOwner) {
        isForeground.value = false
    }

    override fun reconnect() {
        reconnectRequests.tryEmit(Unit)
    }

    private sealed interface ConnectionPolicy {
        data object DisconnectImmediate : ConnectionPolicy
        data object DisconnectGracePeriod : ConnectionPolicy
        data object ActiveSupervision : ConnectionPolicy
    }

    private fun startSupervision() {
        appScope.launch(ioDispatcher) {
            combine(
                isForeground,
                tokenDataSource.authState.map { it is AuthState.Authenticated }.distinctUntilChanged(),
                networkMonitor.isOnline.distinctUntilChanged()
            ) { foreground, isAuthenticated, isOnline ->
                if (!isAuthenticated) {
                    retryAttempt = 0
                }
                when {
                    !isAuthenticated || !isOnline -> ConnectionPolicy.DisconnectImmediate
                    foreground -> ConnectionPolicy.ActiveSupervision
                    else -> ConnectionPolicy.DisconnectGracePeriod
                }
            }
                .distinctUntilChanged()
                .collectLatest { policy ->
                    try {
                        when (policy) {
                            ConnectionPolicy.DisconnectImmediate -> {
                                _connectionState.value = RealtimeConnectionState.DISCONNECTED
                                realtimeDataSource.disconnect()
                            }
                            ConnectionPolicy.DisconnectGracePeriod -> {
                                delay(DISCONNECT_DELAY_MS.milliseconds)
                                _connectionState.value = RealtimeConnectionState.DISCONNECTED
                                realtimeDataSource.disconnect()
                            }
                            ConnectionPolicy.ActiveSupervision -> {
                                superviseConnection()
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                        // Prevent non-cancellation exceptions from terminating supervision loop
                    }
                }
        }
    }

    private suspend fun superviseConnection() {
        var isImmediateAttempt = true
        _connectionState.value = RealtimeConnectionState.CONNECTING

        realtimeDataSource.connectionState.collect { state ->
            when (state) {
                is SocketConnectionState.Connected -> {
                    retryAttempt = 0
                    isImmediateAttempt = false
                    _connectionState.value = RealtimeConnectionState.CONNECTED
                }

                is SocketConnectionState.AuthenticationFailed -> {
                    isImmediateAttempt = false
                    val staleToken = tokenDataSource.getAccessToken().orEmpty()
                    val refreshedToken = try {
                        tokenRefresher.get().refresh(staleToken)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                        null
                    }

                    if (refreshedToken != null) {
                        _connectionState.value = RealtimeConnectionState.CONNECTING
                        if (retryAttempt > 0) {
                            delay(backoff.calculateDelay(retryAttempt))
                        }
                        retryAttempt++
                        realtimeDataSource.connect()
                    } else {
                        _connectionState.value = RealtimeConnectionState.DISCONNECTED
                        tokenDataSource.clearAuthData()
                    }
                }

                is SocketConnectionState.Failed,
                is SocketConnectionState.Disconnected -> {
                    if (retryAttempt >= MAX_RETRY_ATTEMPTS) {
                        _connectionState.value = RealtimeConnectionState.FAILED
                        reconnectRequests.first()
                        _connectionState.value = RealtimeConnectionState.CONNECTING
                        retryAttempt = 0
                        isImmediateAttempt = true
                    } else {
                        _connectionState.value = RealtimeConnectionState.CONNECTING
                    }

                    if (!isImmediateAttempt && retryAttempt > 0) {
                        delay(backoff.calculateDelay(retryAttempt))
                    }

                    isImmediateAttempt = false
                    retryAttempt++
                    realtimeDataSource.connect()
                }

                is SocketConnectionState.Connecting -> {
                    _connectionState.value = RealtimeConnectionState.CONNECTING
                }
            }
        }
    }

    companion object {
        private const val DISCONNECT_DELAY_MS = 30_000L
        private const val MAX_RETRY_ATTEMPTS = 5
    }
}
