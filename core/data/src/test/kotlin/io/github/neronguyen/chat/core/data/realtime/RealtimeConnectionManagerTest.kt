package io.github.neronguyen.chat.core.data.realtime

import androidx.lifecycle.LifecycleOwner
import io.github.neronguyen.chat.core.data.testing.TestNetworkMonitor
import io.github.neronguyen.chat.core.data.util.NetworkMonitor
import io.github.neronguyen.chat.core.datastore.TokenDataSource
import io.github.neronguyen.chat.core.model.AuthState
import io.github.neronguyen.chat.core.model.RealtimeConnectionState
import io.github.neronguyen.chat.core.model.User
import io.github.neronguyen.chat.core.network.ChatRealtimeDataSource
import io.github.neronguyen.chat.core.network.interceptor.TokenRefresher
import io.github.neronguyen.chat.core.network.model.websocket.SocketConnectionState
import io.github.neronguyenvn.nerochat.contract.chat.WsClientMessage
import io.github.neronguyenvn.nerochat.contract.chat.WsServerEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RealtimeConnectionManagerTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private open class FakeChatRealtimeDataSource : ChatRealtimeDataSource {
        val incomingEventsFlow = MutableSharedFlow<WsServerEvent>()
        val connectionStateFlow = MutableStateFlow<SocketConnectionState>(SocketConnectionState.Disconnected)

        var connectCalledCount = 0
        var disconnectCalledCount = 0

        override val incomingEvents: Flow<WsServerEvent> = incomingEventsFlow
        override val connectionState: StateFlow<SocketConnectionState> = connectionStateFlow

        override suspend fun connect() {
            connectCalledCount++
            connectionStateFlow.value = SocketConnectionState.Connected
        }

        override suspend fun disconnect() {
            disconnectCalledCount++
            connectionStateFlow.value = SocketConnectionState.Disconnected
        }

        override suspend fun sendMessage(
            message: WsClientMessage
        ): Boolean {
            return true
        }
    }

    private class FakeTokenRefresher : TokenRefresher {
        var refreshCalledCount = 0
        var tokenToReturn: String? = "refreshed_access_token"

        override suspend fun refresh(staleToken: String): String? {
            refreshCalledCount++
            return tokenToReturn
        }
    }

    private class FakeTokenDataSource(
        initialAuthState: AuthState = AuthState.Authenticated(
            user = User(
                id = "user123",
                email = "test@example.com",
                displayName = "Test User",
                isEmailVerified = true
            ),
            accessToken = "access_token"
        )
    ) : TokenDataSource {
        val authStateFlow = MutableStateFlow(initialAuthState)
        var clearAuthDataCalledCount = 0

        override val authState: Flow<AuthState> = authStateFlow
        override suspend fun getRefreshToken(): String? = "refresh_token"
        override suspend fun getAccessToken(): String? = "access_token"
        override suspend fun saveAuthData(user: User, accessToken: String, refreshToken: String) {}
        override suspend fun saveTokens(accessToken: String, refreshToken: String) {}
        override suspend fun clearAuthData() {
            clearAuthDataCalledCount++
            authStateFlow.value = AuthState.Unauthenticated
        }
    }

    private class FakeLifecycleOwner : LifecycleOwner {
        override val lifecycle get() = error("Not needed for unit tests")
    }

    private fun createManager(
        fakeDataSource: FakeChatRealtimeDataSource,
        fakeToken: FakeTokenDataSource = FakeTokenDataSource(),
        fakeRefresher: FakeTokenRefresher = FakeTokenRefresher(),
        networkMonitor: NetworkMonitor = TestNetworkMonitor(),
        scope: CoroutineScope = TestScope(testDispatcher)
    ): RealtimeConnectionManagerImpl {
        return RealtimeConnectionManagerImpl(
            realtimeDataSource = fakeDataSource,
            tokenDataSource = fakeToken,
            tokenRefresher = { fakeRefresher },
            networkMonitor = networkMonitor,
            appScope = scope,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun `onStart triggers connection supervision and connects realtime data source`() = runTest {
        val fakeDataSource = FakeChatRealtimeDataSource()
        val manager = createManager(fakeDataSource)

        val owner = FakeLifecycleOwner()
        manager.onStart(owner)

        testScheduler.advanceUntilIdle()
        assertEquals(1, fakeDataSource.connectCalledCount)
    }

    @Test
    fun `onStart retries connection on Failed state with backoff`() = runTest {
        val fakeDataSource = FakeChatRealtimeDataSource()
        val manager = createManager(fakeDataSource)

        val owner = FakeLifecycleOwner()
        manager.onStart(owner)
        testScheduler.advanceUntilIdle()
        assertEquals(1, fakeDataSource.connectCalledCount)

        // Simulate connection failure
        fakeDataSource.connectionStateFlow.value =
            SocketConnectionState.Failed(RuntimeException("Connection lost"))

        testScheduler.advanceUntilIdle()
        // Should have re-connected
        assertEquals(2, fakeDataSource.connectCalledCount)
    }

    @Test
    fun `onStart intercepts AuthenticationFailed, calls TokenRefresher, and reconnects`() = runTest {
        val fakeDataSource = FakeChatRealtimeDataSource()
        val fakeRefresher = FakeTokenRefresher()
        val manager = createManager(fakeDataSource, fakeRefresher = fakeRefresher)

        val owner = FakeLifecycleOwner()
        manager.onStart(owner)
        testScheduler.advanceUntilIdle()
        assertEquals(1, fakeDataSource.connectCalledCount)

        // Simulate 1008 Authentication failure from WebSocket
        fakeDataSource.connectionStateFlow.value =
            SocketConnectionState.AuthenticationFailed(code = 1008, reason = "Policy Violation")

        testScheduler.advanceUntilIdle()
        // Verify TokenRefresher was invoked with stale token
        assertEquals(1, fakeRefresher.refreshCalledCount)
        // Should have re-connected with refreshed token
        assertEquals(2, fakeDataSource.connectCalledCount)
    }

    @Test
    fun `onStart clears auth data when TokenRefresher returns null on AuthenticationFailed`() = runTest {
        val fakeDataSource = FakeChatRealtimeDataSource()
        val fakeToken = FakeTokenDataSource()
        val fakeRefresher = FakeTokenRefresher().apply { tokenToReturn = null }
        val manager = createManager(fakeDataSource, fakeToken = fakeToken, fakeRefresher = fakeRefresher)

        val owner = FakeLifecycleOwner()
        manager.onStart(owner)
        testScheduler.advanceUntilIdle()
        assertEquals(1, fakeDataSource.connectCalledCount)

        // Simulate 1008 Authentication failure from WebSocket
        fakeDataSource.connectionStateFlow.value =
            SocketConnectionState.AuthenticationFailed(code = 1008, reason = "Policy Violation")

        testScheduler.advanceUntilIdle()
        assertEquals(1, fakeRefresher.refreshCalledCount)
        assertEquals(1, fakeToken.clearAuthDataCalledCount)
        assertEquals(1, fakeDataSource.disconnectCalledCount)
    }

    @Test
    fun `onStop disconnects realtime data source`() = runTest {
        val fakeDataSource = FakeChatRealtimeDataSource()
        val manager = createManager(fakeDataSource)

        val owner = FakeLifecycleOwner()
        manager.onStart(owner)
        manager.onStop(owner)

        testScheduler.advanceUntilIdle()
        assertEquals(1, fakeDataSource.disconnectCalledCount)
    }

    @Test
    fun `onStart disconnects realtime data source when unauthenticated`() = runTest {
        val fakeDataSource = FakeChatRealtimeDataSource()
        val fakeToken = FakeTokenDataSource(initialAuthState = AuthState.Unauthenticated)
        val manager = createManager(fakeDataSource, fakeToken = fakeToken)

        val owner = FakeLifecycleOwner()
        manager.onStart(owner)

        testScheduler.advanceUntilIdle()
        assertEquals(0, fakeDataSource.connectCalledCount)
        assertTrue(fakeDataSource.disconnectCalledCount >= 1)
    }

    @Test
    fun `network offline pauses reconnection and calls disconnect`() = runTest {
        val fakeDataSource = FakeChatRealtimeDataSource()
        val networkMonitor = TestNetworkMonitor(initialOnline = true)
        val manager = createManager(fakeDataSource, networkMonitor = networkMonitor)

        val owner = FakeLifecycleOwner()
        manager.onStart(owner)
        testScheduler.advanceUntilIdle()
        assertEquals(1, fakeDataSource.connectCalledCount)

        // Enter airplane mode / disconnect
        networkMonitor.setConnected(false)
        testScheduler.advanceUntilIdle()

        assertTrue(fakeDataSource.disconnectCalledCount >= 1)
    }

    @Test
    fun `network recovery triggers immediate 0ms reconnection attempt`() = runTest {
        val fakeDataSource = FakeChatRealtimeDataSource()
        val networkMonitor = TestNetworkMonitor(initialOnline = false)
        val manager = createManager(fakeDataSource, networkMonitor = networkMonitor)

        val owner = FakeLifecycleOwner()
        manager.onStart(owner)
        testScheduler.advanceUntilIdle()
        assertEquals(0, fakeDataSource.connectCalledCount)

        // Network returns online
        networkMonitor.setConnected(true)
        testScheduler.advanceUntilIdle()

        assertEquals(1, fakeDataSource.connectCalledCount)
    }

    @Test
    fun `network flapping before Connected preserves retryAttempt backoff and resets only after Connected`() =
        runTest {
            var shouldSucceedConnect = false
            val fakeDataSource = object : FakeChatRealtimeDataSource() {
                override suspend fun connect() {
                    connectCalledCount++
                    connectionStateFlow.value = if (shouldSucceedConnect) {
                        SocketConnectionState.Connected
                    } else {
                        SocketConnectionState.Failed(RuntimeException("Handshake failed #$connectCalledCount"))
                    }
                }
            }
            val networkMonitor = TestNetworkMonitor(initialOnline = true)
            val manager = createManager(fakeDataSource, networkMonitor = networkMonitor)

            val owner = FakeLifecycleOwner()
            manager.onStart(owner)
            testScheduler.runCurrent()

            // 1st attempt runs immediately (0ms) and fails; retryAttempt is now 1 (backoff ~1600..2400ms)
            assertEquals(1, fakeDataSource.connectCalledCount)

            // Flap network offline -> online before backoff completes
            networkMonitor.setConnected(false)
            testScheduler.runCurrent()
            networkMonitor.setConnected(true)
            testScheduler.runCurrent()

            // Online recovery triggers immediate attempt #2 (0ms), which also fails; retryAttempt is now 2 (backoff ~3200..4800ms)
            assertEquals(2, fakeDataSource.connectCalledCount)

            // Because retryAttempt was NOT reset to 0 by the online flap (it is 2, not 1),
            // advancing 2500ms (enough for attempt 1's max 2400ms backoff, but less than attempt 2's min 3200ms backoff)
            // must NOT trigger attempt #3 yet!
            testScheduler.advanceTimeBy(2_500)
            testScheduler.runCurrent()
            assertEquals(2, fakeDataSource.connectCalledCount)

            // Advancing past attempt 2's max backoff (4800ms total) triggers attempt #3, which succeeds -> resets retryAttempt = 0
            shouldSucceedConnect = true
            testScheduler.advanceTimeBy(2_500)
            testScheduler.runCurrent()
            assertEquals(3, fakeDataSource.connectCalledCount)
            assertEquals(SocketConnectionState.Connected, fakeDataSource.connectionStateFlow.value)
        }

    @Test
    fun `connectionState maps socket states to domain RealtimeConnectionState`() = runTest {
        val fakeDataSource = FakeChatRealtimeDataSource()
        val manager = createManager(fakeDataSource)

        assertEquals(
            RealtimeConnectionState.DISCONNECTED,
            manager.connectionState.first()
        )

        fakeDataSource.connectionStateFlow.value = SocketConnectionState.Connecting
        assertEquals(
            RealtimeConnectionState.CONNECTING,
            manager.connectionState.first()
        )

        fakeDataSource.connectionStateFlow.value = SocketConnectionState.Connected
        assertEquals(
            RealtimeConnectionState.CONNECTED,
            manager.connectionState.first()
        )

        fakeDataSource.connectionStateFlow.value = SocketConnectionState.AuthenticationFailed()
        assertEquals(
            RealtimeConnectionState.FAILED,
            manager.connectionState.first()
        )

        fakeDataSource.connectionStateFlow.value = SocketConnectionState.Failed()
        assertEquals(
            RealtimeConnectionState.FAILED,
            manager.connectionState.first()
        )
    }
}
