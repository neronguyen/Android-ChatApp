package io.github.neronguyen.chat.core.data.realtime

import androidx.lifecycle.LifecycleOwner
import io.github.neronguyen.chat.core.common.coroutine.ExponentialBackoff
import io.github.neronguyen.chat.core.data.testing.TestNetworkMonitor
import io.github.neronguyen.chat.core.datastore.TokenDataSource
import io.github.neronguyen.chat.core.model.AuthState
import io.github.neronguyen.chat.core.model.RealtimeConnectionState
import io.github.neronguyen.chat.core.model.User
import io.github.neronguyen.chat.core.network.ChatRealtimeDataSource
import io.github.neronguyen.chat.core.network.interceptor.TokenRefresher
import io.github.neronguyen.chat.core.network.model.websocket.SocketConnectionState
import io.github.neronguyenvn.nerochat.contract.chat.WsClientMessage
import io.github.neronguyenvn.nerochat.contract.chat.WsServerEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class RealtimeConnectionManagerStateMachineTest {

    private val seeds = listOf(101L, 2026L, 4242L)

    @Test
    fun `randomized lifecycle, auth, network, token refresh, and socket transitions preserve connection invariants`() {
        for (seed in seeds) {
            runSeededTrace(seed)
        }
    }

    private fun runSeededTrace(seed: Long) {
        val dispatcher = StandardTestDispatcher()
        val testScope = TestScope(dispatcher)

        try {
            testScope.runTest {
                val random = Random(seed)
                val tokenDataSource = StateMachineTokenDataSource()
                val realtimeDataSource = StateMachineRealtimeDataSource(tokenDataSource)
                val networkMonitor = TestNetworkMonitor(initialOnline = true)
                var refreshShouldSucceed = true
                val tokenRefresher = TokenRefresher {
                    if (refreshShouldSucceed) "refreshed-token" else null
                }
                val lifecycleOwner = object : LifecycleOwner {
                    override val lifecycle get() = throw UnsupportedOperationException()
                }

                val manager = RealtimeConnectionManagerImpl(
                    realtimeDataSource = realtimeDataSource,
                    tokenDataSource = tokenDataSource,
                    tokenRefresher = dagger.Lazy { tokenRefresher },
                    networkMonitor = networkMonitor,
                    appScope = backgroundScope,
                    ioDispatcher = dispatcher,
                    backoff = ExponentialBackoff(
                        initialDelay = 100.milliseconds,
                        maxDelay = 1_000.milliseconds,
                        factor = 2.0,
                        jitterRatio = 0.0,
                    ),
                )

                var isForeground = false
                var isOnline = true

                repeat(70) { step ->
                    when (random.nextInt(8)) {
                        0 -> {
                            realtimeDataSource.failNextConnectAttempts = 0
                            tokenDataSource.setAuthState(
                                AuthState.Authenticated(
                                    user = User("user-1", "alice@example.com", "Alice", true),
                                    accessToken = "access-token",
                                ),
                            )
                        }

                        1 -> {
                            tokenDataSource.setAuthState(AuthState.Unauthenticated)
                        }

                        2 -> {
                            isForeground = true
                            manager.onStart(lifecycleOwner)
                        }

                        3 -> {
                            isForeground = false
                            manager.onStop(lifecycleOwner)
                        }

                        4 -> {
                            isOnline = !isOnline
                            networkMonitor.setConnected(isOnline)
                        }

                        5 -> {
                            if (realtimeDataSource.connectionStateFlow.value == SocketConnectionState.Connected) {
                                realtimeDataSource.connectionStateFlow.value =
                                    SocketConnectionState.Failed(RuntimeException("Simulated drop"))
                            } else {
                                manager.reconnect()
                            }
                        }

                        6 -> {
                            // Exercise AuthenticationFailed with either refresh success or refresh failure
                            refreshShouldSucceed = random.nextBoolean()
                            if (tokenDataSource.isAuthenticated() && isForeground && isOnline) {
                                realtimeDataSource.connectionStateFlow.value =
                                    SocketConnectionState.AuthenticationFailed()
                            }
                        }

                        7 -> {
                            // Exercise bounded backoff exhaustion (MAX_RETRY_ATTEMPTS = 5 -> FAILED)
                            realtimeDataSource.failNextConnectAttempts = 6
                            if (realtimeDataSource.connectionStateFlow.value == SocketConnectionState.Connected) {
                                realtimeDataSource.connectionStateFlow.value =
                                    SocketConnectionState.Failed(RuntimeException("Outage"))
                            }
                        }
                    }

                    // Advance past grace period & bounded backoff
                    advanceTimeBy(31_000L)
                    runCurrent()

                    assertEquals(
                        "seed=$seed step=$step: connect() must never be called while unauthenticated",
                        0,
                        realtimeDataSource.connectCallsWhileUnauthenticated,
                    )

                    val isAuthenticated = tokenDataSource.isAuthenticated()
                    val state = manager.connectionState.value
                    if (!isAuthenticated) {
                        assertEquals(
                            "seed=$seed step=$step: unauthenticated state must be DISCONNECTED",
                            RealtimeConnectionState.DISCONNECTED,
                            state,
                        )
                    }
                    if (!isForeground && isAuthenticated) {
                        assertEquals(
                            "seed=$seed step=$step: backgrounded past grace period must be DISCONNECTED",
                            RealtimeConnectionState.DISCONNECTED,
                            state,
                        )
                    }
                    if (isAuthenticated && isForeground && !isOnline) {
                        assertTrue(
                            "seed=$seed step=$step: offline must not report CONNECTED (was $state)",
                            state != RealtimeConnectionState.CONNECTED,
                        )
                    }
                }

                // Final convergence check: authenticated + foreground + online + healthy socket -> CONNECTED
                refreshShouldSucceed = true
                realtimeDataSource.failNextConnectAttempts = 0
                tokenDataSource.setAuthState(
                    AuthState.Authenticated(
                        user = User("user-1", "alice@example.com", "Alice", true),
                        accessToken = "access-token",
                    ),
                )
                manager.onStart(lifecycleOwner)
                networkMonitor.setConnected(true)
                manager.reconnect()
                advanceTimeBy(5_000L)
                runCurrent()

                assertEquals(
                    "seed=$seed: healthy authenticated foreground online state must converge to CONNECTED",
                    RealtimeConnectionState.CONNECTED,
                    manager.connectionState.value,
                )
            }
        } finally {
            testScope.cancel()
        }
    }

    private class StateMachineRealtimeDataSource(
        private val tokenDataSource: StateMachineTokenDataSource,
    ) : ChatRealtimeDataSource {
        override val incomingEvents: Flow<WsServerEvent> = MutableSharedFlow()
        val connectionStateFlow = MutableStateFlow<SocketConnectionState>(SocketConnectionState.Disconnected)
        override val connectionState: StateFlow<SocketConnectionState> = connectionStateFlow

        var failNextConnectAttempts = 0
        var connectCallsWhileUnauthenticated = 0

        override suspend fun connect() {
            if (!tokenDataSource.isAuthenticated()) {
                connectCallsWhileUnauthenticated++
            }
            if (failNextConnectAttempts > 0) {
                failNextConnectAttempts--
                connectionStateFlow.value = SocketConnectionState.Failed(RuntimeException("Connect refused"))
            } else {
                connectionStateFlow.value = SocketConnectionState.Connected
            }
        }

        override suspend fun disconnect() {
            connectionStateFlow.value = SocketConnectionState.Disconnected
        }

        override suspend fun sendMessage(message: WsClientMessage): Boolean = true
    }

    private class StateMachineTokenDataSource : TokenDataSource {
        private val authStateFlow = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
        override val authState: Flow<AuthState> = authStateFlow
        fun isAuthenticated(): Boolean = authStateFlow.value is AuthState.Authenticated
        fun setAuthState(state: AuthState) {
            authStateFlow.value = state
        }

        override suspend fun saveAuthData(user: User, accessToken: String, refreshToken: String) {}
        override suspend fun saveTokens(accessToken: String, refreshToken: String) {}
        override suspend fun clearAuthData() {
            authStateFlow.value = AuthState.Unauthenticated
        }

        override suspend fun getAccessToken(): String? =
            (authStateFlow.value as? AuthState.Authenticated)?.accessToken

        override suspend fun getRefreshToken(): String? = null
    }
}
