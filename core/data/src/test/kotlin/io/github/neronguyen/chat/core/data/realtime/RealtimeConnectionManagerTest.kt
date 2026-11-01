package io.github.neronguyen.chat.core.data.realtime

import androidx.lifecycle.LifecycleOwner
import arrow.core.Either
import io.github.neronguyen.chat.core.datastore.TokenDataSource
import io.github.neronguyen.chat.core.model.AuthState
import io.github.neronguyen.chat.core.model.User
import io.github.neronguyen.chat.core.model.error.DataError
import io.github.neronguyen.chat.core.network.ChatRealtimeDataSource
import io.github.neronguyenvn.nerochat.contract.chat.WsClientMessage
import io.github.neronguyenvn.nerochat.contract.chat.WsServerEvent
import io.github.neronguyen.chat.core.network.model.websocket.SocketConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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

    private class FakeChatRealtimeDataSource : ChatRealtimeDataSource {
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
        override val authState: Flow<AuthState> = flowOf(initialAuthState)
        override suspend fun getRefreshToken(): String? = "refresh_token"
        override suspend fun getAccessToken(): String? = "access_token"
        override suspend fun saveAuthData(user: User, accessToken: String, refreshToken: String) {}
        override suspend fun saveTokens(accessToken: String, refreshToken: String) {}
        override suspend fun clearAuthData() {}
    }

    private class FakeLifecycleOwner : LifecycleOwner {
        override val lifecycle get() = error("Not needed for unit tests")
    }

    @Test
    fun `onStart triggers connection supervision and connects realtime data source`() = runTest {
        val externalScope = TestScope(testDispatcher)
        val fakeDataSource = FakeChatRealtimeDataSource()
        val fakeToken = FakeTokenDataSource()

        val manager = RealtimeConnectionManagerImpl(
            realtimeDataSource = fakeDataSource,
            tokenDataSource = fakeToken,
            appScope = externalScope,
            ioDispatcher = testDispatcher
        )

        val owner = FakeLifecycleOwner()
        manager.onStart(owner)

        testScheduler.advanceUntilIdle()
        assertEquals(1, fakeDataSource.connectCalledCount)
    }

    @Test
    fun `onStart retries connection on Failed state with backoff`() = runTest {
        val externalScope = TestScope(testDispatcher)
        val fakeDataSource = FakeChatRealtimeDataSource()
        val fakeToken = FakeTokenDataSource()

        val manager = RealtimeConnectionManagerImpl(
            realtimeDataSource = fakeDataSource,
            tokenDataSource = fakeToken,
            appScope = externalScope,
            ioDispatcher = testDispatcher
        )

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
    fun `onStop disconnects realtime data source`() = runTest {
        val externalScope = TestScope(testDispatcher)
        val fakeDataSource = FakeChatRealtimeDataSource()
        val fakeToken = FakeTokenDataSource()

        val manager = RealtimeConnectionManagerImpl(
            realtimeDataSource = fakeDataSource,
            tokenDataSource = fakeToken,
            appScope = externalScope,
            ioDispatcher = testDispatcher
        )

        val owner = FakeLifecycleOwner()
        manager.onStart(owner)
        manager.onStop(owner)

        testScheduler.advanceUntilIdle()
        assertEquals(1, fakeDataSource.disconnectCalledCount)
    }

    @Test
    fun `onStart disconnects realtime data source when unauthenticated`() = runTest {
        val externalScope = TestScope(testDispatcher)
        val fakeDataSource = FakeChatRealtimeDataSource()
        val fakeToken = FakeTokenDataSource(initialAuthState = AuthState.Unauthenticated)

        val manager = RealtimeConnectionManagerImpl(
            realtimeDataSource = fakeDataSource,
            tokenDataSource = fakeToken,
            appScope = externalScope,
            ioDispatcher = testDispatcher
        )

        val owner = FakeLifecycleOwner()
        manager.onStart(owner)

        testScheduler.advanceUntilIdle()
        assertEquals(0, fakeDataSource.connectCalledCount)
        assertTrue(fakeDataSource.disconnectCalledCount >= 1)
    }

    @Test
    fun `connectionState maps socket states to domain RealtimeConnectionState`() = runTest {
        val externalScope = TestScope(testDispatcher)
        val fakeDataSource = FakeChatRealtimeDataSource()
        val fakeToken = FakeTokenDataSource()

        val manager = RealtimeConnectionManagerImpl(
            realtimeDataSource = fakeDataSource,
            tokenDataSource = fakeToken,
            appScope = externalScope,
            ioDispatcher = testDispatcher
        )

        assertEquals(
            io.github.neronguyen.chat.core.model.RealtimeConnectionState.DISCONNECTED,
            manager.connectionState.first()
        )

        fakeDataSource.connectionStateFlow.value = SocketConnectionState.Connecting
        assertEquals(
            io.github.neronguyen.chat.core.model.RealtimeConnectionState.CONNECTING,
            manager.connectionState.first()
        )

        fakeDataSource.connectionStateFlow.value = SocketConnectionState.Connected
        assertEquals(
            io.github.neronguyen.chat.core.model.RealtimeConnectionState.CONNECTED,
            manager.connectionState.first()
        )

        fakeDataSource.connectionStateFlow.value = SocketConnectionState.Failed()
        assertEquals(
            io.github.neronguyen.chat.core.model.RealtimeConnectionState.FAILED,
            manager.connectionState.first()
        )
    }
}


