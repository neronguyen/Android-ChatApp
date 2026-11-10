package io.github.neronguyen.chat.core.data.realtime

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.neronguyen.chat.core.common.network.AppScope
import io.github.neronguyen.chat.core.common.network.ChatDispatchers
import io.github.neronguyen.chat.core.common.network.Dispatcher
import io.github.neronguyen.chat.core.data.mapper.toDomain
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalAtomicApi::class)
@Singleton
internal class RealtimeConnectionManagerImpl @Inject constructor(
    private val realtimeDataSource: ChatRealtimeDataSource,
    private val tokenDataSource: TokenDataSource,
    private val tokenRefresher: dagger.Lazy<TokenRefresher>,
    private val networkMonitor: NetworkMonitor,
    @AppScope private val appScope: CoroutineScope,
    @Dispatcher(ChatDispatchers.IO) private val ioDispatcher: CoroutineDispatcher
) : RealtimeConnectionManager, DefaultLifecycleObserver {

    override val connectionState: Flow<RealtimeConnectionState> = realtimeDataSource.connectionState
        .map { it.toDomain() }

    private val isForeground = MutableStateFlow(false)
    private val initialized = AtomicBoolean(false)
    private var retryAttempt = 0

    override fun initialize() {
        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        } catch (_: Exception) {
            // ProcessLifecycleOwner may not be available in unit test environments
        }
        startSupervisionIfNeeded()
    }

    override fun onStart(owner: LifecycleOwner) {
        startSupervisionIfNeeded()
        isForeground.value = true
    }

    override fun onStop(owner: LifecycleOwner) {
        isForeground.value = false
    }

    private fun startSupervisionIfNeeded() {
        if (!initialized.compareAndSet(expectedValue = false, newValue = true)) return

        appScope.launch(ioDispatcher) {
            combine(
                isForeground,
                tokenDataSource.authState,
                networkMonitor.isOnline
            ) { foreground, authState, isOnline ->
                Triple(foreground, authState is AuthState.Authenticated, isOnline)
            }
                .distinctUntilChanged()
                .collectLatest { (foreground, isAuthenticated, isOnline) ->
                    try {
                        when {
                            !isAuthenticated -> {
                                retryAttempt = 0
                                realtimeDataSource.disconnect()
                            }
                            !isOnline -> {
                                realtimeDataSource.disconnect()
                            }
                            foreground -> superviseConnection()
                            else -> {
                                delay(DISCONNECT_DELAY_MS.milliseconds)
                                realtimeDataSource.disconnect()
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

        realtimeDataSource.connectionState.collect { state ->
            when (state) {
                is SocketConnectionState.Connected -> {
                    retryAttempt = 0
                    isImmediateAttempt = false
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
                        if (retryAttempt > 0) {
                            delay(calculateBackoffDelay(retryAttempt).milliseconds)
                        }
                        retryAttempt++
                        realtimeDataSource.connect()
                    } else {
                        tokenDataSource.clearAuthData()
                    }
                }

                is SocketConnectionState.Failed,
                is SocketConnectionState.Disconnected -> {
                    if (!isImmediateAttempt && retryAttempt > 0) {
                        delay(calculateBackoffDelay(retryAttempt).milliseconds)
                    }

                    isImmediateAttempt = false
                    retryAttempt++
                    realtimeDataSource.connect()
                }

                is SocketConnectionState.Connecting -> Unit
            }
        }
    }

    private fun calculateBackoffDelay(attempt: Int): Long {
        val baseDelayMs = 1_000L
        val maxDelayMs = 30_000L
        val expDelay = baseDelayMs * (1L shl attempt.coerceAtMost(5))
        val cappedDelay = expDelay.coerceAtMost(maxDelayMs)
        val jitter = (cappedDelay * 0.2 * Random.nextDouble(-1.0, 1.0)).toLong()
        return (cappedDelay + jitter).coerceAtLeast(baseDelayMs)
    }

    companion object {
        private const val DISCONNECT_DELAY_MS = 30_000L
    }
}
