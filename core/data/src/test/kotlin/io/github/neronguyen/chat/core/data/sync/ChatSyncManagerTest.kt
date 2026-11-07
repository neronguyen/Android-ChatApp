package io.github.neronguyen.chat.core.data.sync

import arrow.core.Either
import io.github.neronguyen.chat.core.data.repository.ChatMessageRepository
import io.github.neronguyen.chat.core.data.repository.ChatRoomRepository
import io.github.neronguyen.chat.core.datastore.TokenDataSource
import io.github.neronguyen.chat.core.model.AuthState
import io.github.neronguyen.chat.core.model.ChatMessage
import io.github.neronguyen.chat.core.model.ChatParticipant
import io.github.neronguyen.chat.core.model.ChatRoom
import io.github.neronguyen.chat.core.model.User
import io.github.neronguyen.chat.core.model.error.ChatError
import io.github.neronguyen.chat.core.model.error.DataError
import io.github.neronguyen.chat.core.network.ChatRealtimeDataSource
import io.github.neronguyen.chat.core.network.model.websocket.SocketConnectionState
import io.github.neronguyenvn.nerochat.contract.chat.WsClientMessage
import io.github.neronguyenvn.nerochat.contract.chat.WsServerEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatSyncManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testScope: TestScope

    private lateinit var fakeChatRoomRepository: FakeChatRoomRepository
    private lateinit var fakeChatMessageRepository: FakeChatMessageRepository
    private lateinit var fakeSyncScheduler: FakeSyncScheduler
    private lateinit var fakeTokenDataSource: FakeTokenDataSource
    private lateinit var fakeRealtimeDataSource: FakeRealtimeDataSource
    private lateinit var chatSyncManager: ChatSyncManagerImpl

    @Before
    fun setUp() {
        testScope = TestScope(testDispatcher)
        fakeChatRoomRepository = FakeChatRoomRepository()
        fakeChatMessageRepository = FakeChatMessageRepository()
        fakeSyncScheduler = FakeSyncScheduler()
        fakeTokenDataSource = FakeTokenDataSource()
        fakeRealtimeDataSource = FakeRealtimeDataSource()

        chatSyncManager = ChatSyncManagerImpl(
            chatRoomRepository = fakeChatRoomRepository,
            chatMessageRepository = fakeChatMessageRepository,
            chatSyncScheduler = fakeSyncScheduler,
            tokenDataSource = fakeTokenDataSource,
            realtimeDataSource = fakeRealtimeDataSource,
            appScope = testScope,
            ioDispatcher = testDispatcher,
        )
    }

    @Test
    fun `when authState transitions to Authenticated, periodic sync is scheduled`() = runTest(testDispatcher) {
        chatSyncManager.initialize()
        testScheduler.runCurrent()

        assertEquals(0, fakeSyncScheduler.schedulePeriodicCallCount)

        val user = User(id = "u1", email = "u1@example.com", displayName = "User 1", isEmailVerified = true)
        fakeTokenDataSource.authStateFlow.value = AuthState.Authenticated(user = user, accessToken = "token")
        testScheduler.runCurrent()

        assertEquals(1, fakeSyncScheduler.schedulePeriodicCallCount)
    }

    @Test
    fun `when authState transitions to Unauthenticated, sync work is cancelled`() = runTest(testDispatcher) {
        val user = User(id = "u1", email = "u1@example.com", displayName = "User 1", isEmailVerified = true)
        fakeTokenDataSource.authStateFlow.value = AuthState.Authenticated(user = user, accessToken = "token")

        chatSyncManager.initialize()
        testScheduler.runCurrent()
        assertEquals(1, fakeSyncScheduler.schedulePeriodicCallCount)

        fakeTokenDataSource.authStateFlow.value = AuthState.Unauthenticated
        testScheduler.runCurrent()

        assertEquals(1, fakeSyncScheduler.cancelAllCallCount)
    }

    @Test
    fun `websocket reconnect from Disconnected to Connected triggers catch-up sync and prefetches messages`() = runTest(testDispatcher) {
        val user = User(id = "u1", email = "u1@example.com", displayName = "User 1", isEmailVerified = true)
        fakeTokenDataSource.authStateFlow.value = AuthState.Authenticated(user = user, accessToken = "token")
        fakeChatRoomRepository.changedRoomIdsResult = Either.Right(listOf("room-1", "room-2"))

        chatSyncManager.initialize()
        testScheduler.runCurrent()

        // Socket connects initially -> does not trigger catch-up yet
        fakeRealtimeDataSource.connectionStateFlow.value = SocketConnectionState.Connected
        testScheduler.runCurrent()
        assertEquals(0, fakeChatRoomRepository.syncChatsWithChangedRoomsCallCount)

        // Socket drops
        fakeRealtimeDataSource.connectionStateFlow.value = SocketConnectionState.Disconnected
        testScheduler.runCurrent()

        // Socket reconnects
        fakeRealtimeDataSource.connectionStateFlow.value = SocketConnectionState.Connected
        testScheduler.runCurrent()

        assertEquals(1, fakeChatRoomRepository.syncChatsWithChangedRoomsCallCount)
        assertEquals(listOf("room-1", "room-2"), fakeChatMessageRepository.syncedRoomIds)
    }

    @Test
    fun `isRetryable correctly identifies transient network and server errors`() {
        assertTrue(DataError.Connectivity.NoInternet.isRetryable())
        assertTrue(DataError.Connectivity.Timeout.isRetryable())
        assertTrue(DataError.Remote.RateLimited().isRetryable())
        assertTrue(DataError.Remote.Unavailable.isRetryable())

        assertFalse(DataError.Remote.Unauthorized.isRetryable())
        assertFalse(DataError.Remote.Forbidden.isRetryable())
        assertFalse(DataError.Unexpected.Serialization.isRetryable())
        assertFalse(DataError.Unexpected.UnknownCode(500).isRetryable())
        assertFalse(DataError.Local.NotAuthenticated.isRetryable())
    }

    private class FakeChatRoomRepository : ChatRoomRepository {
        var syncChatsWithChangedRoomsCallCount = 0
        var changedRoomIdsResult: Either<DataError, List<String>> = Either.Right(emptyList())

        override fun getChats(): Flow<List<ChatRoom>> = emptyFlow()
        override suspend fun syncChats(): Either<DataError, Unit> = Either.Right(Unit)

        override suspend fun syncChatsWithChangedRooms(): Either<DataError, List<String>> {
            syncChatsWithChangedRoomsCallCount++
            return changedRoomIdsResult
        }

        override suspend fun findChatParticipantByEmail(email: String): Either<ChatError.FindParticipant, ChatParticipant> =
            throw UnsupportedOperationException()

        override suspend fun createDirectChatRoom(targetUserId: String, message: String): Either<ChatError.CreateRoom, ChatRoom> =
            throw UnsupportedOperationException()

        override suspend fun createGroupChatRoom(participantIds: List<String>, name: String?): Either<ChatError.CreateRoom, ChatRoom> =
            throw UnsupportedOperationException()

        override fun getChatRoom(roomId: String): Flow<ChatRoom?> = emptyFlow()
        override suspend fun syncChatRoom(roomId: String): Either<ChatError.SyncRoom, Unit> = Either.Right(Unit)
        override suspend fun addParticipantsToGroup(roomId: String, userIds: List<String>): Either<ChatError.AddParticipants, ChatRoom> =
            throw UnsupportedOperationException()

        override suspend fun leaveChatRoom(roomId: String): Either<ChatError.LeaveRoom, Unit> = Either.Right(Unit)
    }

    private class FakeChatMessageRepository : ChatMessageRepository {
        val syncedRoomIds = mutableListOf<String>()

        override fun getMessages(roomId: String, limit: Int): Flow<List<ChatMessage>> = emptyFlow()

        override suspend fun syncRecentMessages(roomId: String): Either<DataError, Boolean> {
            syncedRoomIds.add(roomId)
            return Either.Right(false)
        }

        override suspend fun loadOlderMessages(roomId: String): Either<DataError, Boolean> = Either.Right(false)
        override suspend fun sendMessage(roomId: String, message: String) {}
        override suspend fun retryMessage(messageId: String) {}
        override suspend fun deleteMessage(messageId: String): Either<ChatError.DeleteMessage, Unit> = Either.Right(Unit)
    }

    private class FakeSyncScheduler : ChatSyncScheduler {
        var schedulePeriodicCallCount = 0
        var enqueueOneTimeCallCount = 0
        var cancelAllCallCount = 0

        override fun schedulePeriodicSync() {
            schedulePeriodicCallCount++
        }

        override fun enqueueOneTimeCatchUpSync() {
            enqueueOneTimeCallCount++
        }

        override fun cancelAllSyncWork() {
            cancelAllCallCount++
        }
    }

    private class FakeTokenDataSource : TokenDataSource {
        val authStateFlow = MutableStateFlow<AuthState>(AuthState.Unauthenticated)

        override val authState: Flow<AuthState> = authStateFlow
        override suspend fun getAccessToken(): String? = null
        override suspend fun getRefreshToken(): String? = null
        override suspend fun saveAuthData(user: User, accessToken: String, refreshToken: String) {}
        override suspend fun saveTokens(accessToken: String, refreshToken: String) {}
        override suspend fun clearAuthData() {}
    }

    private class FakeRealtimeDataSource : ChatRealtimeDataSource {
        val connectionStateFlow = MutableStateFlow<SocketConnectionState>(SocketConnectionState.Disconnected)
        val incomingEventsFlow = MutableSharedFlow<WsServerEvent>()

        override val connectionState: StateFlow<SocketConnectionState> = connectionStateFlow
        override val incomingEvents: Flow<WsServerEvent> = incomingEventsFlow
        override suspend fun connect() {}
        override suspend fun disconnect() {}
        override suspend fun sendMessage(message: WsClientMessage): Boolean = true
    }
}
