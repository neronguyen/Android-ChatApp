package io.github.neronguyen.chat.core.data.sync

import arrow.core.Either
import io.github.neronguyen.chat.core.data.realtime.RealtimeConnectionManager
import io.github.neronguyen.chat.core.data.repository.ChatMessageRepository
import io.github.neronguyen.chat.core.data.repository.ChatRoomRepository
import io.github.neronguyen.chat.core.data.testing.TestNetworkMonitor
import io.github.neronguyen.chat.core.datastore.TokenDataSource
import io.github.neronguyen.chat.core.model.AuthState
import io.github.neronguyen.chat.core.model.ChatMessage
import io.github.neronguyen.chat.core.model.ChatParticipant
import io.github.neronguyen.chat.core.model.ChatRoom
import io.github.neronguyen.chat.core.model.ChatSyncState
import io.github.neronguyen.chat.core.model.RealtimeConnectionState
import io.github.neronguyen.chat.core.model.User
import io.github.neronguyen.chat.core.model.error.ChatError
import io.github.neronguyen.chat.core.model.error.DataError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    private lateinit var fakeRealtimeConnectionManager: FakeRealtimeConnectionManager
    private lateinit var fakeNetworkMonitor: TestNetworkMonitor
    private lateinit var chatSyncManager: ChatSyncManagerImpl

    @Before
    fun setUp() {
        testScope = TestScope(testDispatcher)
        fakeChatRoomRepository = FakeChatRoomRepository()
        fakeChatMessageRepository = FakeChatMessageRepository()
        fakeSyncScheduler = FakeSyncScheduler()
        fakeTokenDataSource = FakeTokenDataSource()
        fakeRealtimeConnectionManager = FakeRealtimeConnectionManager()
        fakeNetworkMonitor = TestNetworkMonitor(initialOnline = true)

        chatSyncManager = ChatSyncManagerImpl(
            chatRoomRepository = fakeChatRoomRepository,
            chatMessageRepository = fakeChatMessageRepository,
            chatSyncScheduler = fakeSyncScheduler,
            tokenDataSource = fakeTokenDataSource,
            realtimeConnectionManager = fakeRealtimeConnectionManager,
            networkMonitor = fakeNetworkMonitor,
            appScope = testScope,
            ioDispatcher = testDispatcher,
        )
    }

    @Test
    fun `initial syncState is Idle`() {
        assertEquals(ChatSyncState.Idle, chatSyncManager.syncState.value)
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
    fun `when authState transitions to Unauthenticated, sync work is cancelled and syncState becomes Idle`() = runTest(testDispatcher) {
        val user = User(id = "u1", email = "u1@example.com", displayName = "User 1", isEmailVerified = true)
        fakeTokenDataSource.authStateFlow.value = AuthState.Authenticated(user = user, accessToken = "token")

        chatSyncManager.initialize()
        testScheduler.runCurrent()
        assertEquals(1, fakeSyncScheduler.schedulePeriodicCallCount)

        fakeTokenDataSource.authStateFlow.value = AuthState.Unauthenticated
        testScheduler.runCurrent()

        assertEquals(1, fakeSyncScheduler.cancelAllCallCount)
        assertEquals(ChatSyncState.Idle, chatSyncManager.syncState.value)
    }

    @Test
    fun `when networkMonitor emits isOnline false, syncState transitions to Offline`() = runTest(testDispatcher) {
        val user = User(id = "u1", email = "u1@example.com", displayName = "User 1", isEmailVerified = true)
        fakeTokenDataSource.authStateFlow.value = AuthState.Authenticated(user = user, accessToken = "token")

        chatSyncManager.initialize()
        testScheduler.runCurrent()

        fakeNetworkMonitor.setConnected(false)
        testScheduler.runCurrent()

        assertEquals(ChatSyncState.Offline, chatSyncManager.syncState.value)
    }

    @Test
    fun `when isOnline is true and connection is CONNECTING, syncState transitions to Connecting`() = runTest(testDispatcher) {
        val user = User(id = "u1", email = "u1@example.com", displayName = "User 1", isEmailVerified = true)
        fakeTokenDataSource.authStateFlow.value = AuthState.Authenticated(user = user, accessToken = "token")

        chatSyncManager.initialize()
        testScheduler.runCurrent()

        fakeNetworkMonitor.setConnected(true)
        fakeRealtimeConnectionManager.connectionStateFlow.value = RealtimeConnectionState.CONNECTING
        testScheduler.runCurrent()

        assertEquals(ChatSyncState.Connecting, chatSyncManager.syncState.value)
    }

    @Test
    fun `when connection transitions to CONNECTED, triggers catch-up sync and prefetches messages`() = runTest(testDispatcher) {
        val user = User(id = "u1", email = "u1@example.com", displayName = "User 1", isEmailVerified = true)
        fakeTokenDataSource.authStateFlow.value = AuthState.Authenticated(user = user, accessToken = "token")
        fakeChatRoomRepository.changedRoomIdsResult = Either.Right(listOf("room-1", "room-2"))

        chatSyncManager.initialize()
        testScheduler.runCurrent()

        fakeRealtimeConnectionManager.connectionStateFlow.value = RealtimeConnectionState.CONNECTED
        testScheduler.runCurrent()

        assertEquals(1, fakeChatRoomRepository.syncChatsAndGetChangedRoomsCallCount)
        assertEquals(listOf("room-1", "room-2"), fakeChatMessageRepository.syncedRoomIds)
        assertEquals(ChatSyncState.Synced, chatSyncManager.syncState.value)
    }

    @Test
    fun `when connection is FAILED, syncState transitions to Failed`() = runTest(testDispatcher) {
        val user = User(id = "u1", email = "u1@example.com", displayName = "User 1", isEmailVerified = true)
        fakeTokenDataSource.authStateFlow.value = AuthState.Authenticated(user = user, accessToken = "token")

        chatSyncManager.initialize()
        testScheduler.runCurrent()

        fakeRealtimeConnectionManager.connectionStateFlow.value = RealtimeConnectionState.FAILED
        testScheduler.runCurrent()

        assertEquals(ChatSyncState.Failed(DataError.Remote.Unavailable), chatSyncManager.syncState.value)
    }

    @Test
    fun `when syncState is Failed and connection transitions to CONNECTING, syncState transitions to Connecting`() = runTest(testDispatcher) {
        val user = User(id = "u1", email = "u1@example.com", displayName = "User 1", isEmailVerified = true)
        fakeTokenDataSource.authStateFlow.value = AuthState.Authenticated(user = user, accessToken = "token")

        chatSyncManager.initialize()
        testScheduler.runCurrent()

        fakeRealtimeConnectionManager.connectionStateFlow.value = RealtimeConnectionState.FAILED
        testScheduler.runCurrent()
        assertEquals(ChatSyncState.Failed(DataError.Remote.Unavailable), chatSyncManager.syncState.value)

        fakeRealtimeConnectionManager.connectionStateFlow.value = RealtimeConnectionState.CONNECTING
        testScheduler.runCurrent()
        assertEquals(ChatSyncState.Connecting, chatSyncManager.syncState.value)
    }

    @Test
    fun `performCatchUpSync succeeds and updates syncState to Synced`() = runTest(testDispatcher) {
        fakeChatRoomRepository.changedRoomIdsResult = Either.Right(listOf("room-1", "room-2"))

        val result = chatSyncManager.performCatchUpSync()

        assertTrue(result.isRight())
        assertEquals(listOf("room-1", "room-2"), fakeChatMessageRepository.syncedRoomIds)
        assertEquals(ChatSyncState.Synced, chatSyncManager.syncState.value)
    }

    @Test
    fun `performCatchUpSync fails when room sync fails and updates syncState to Failed`() = runTest(testDispatcher) {
        val networkError = DataError.Connectivity.NoInternet
        fakeChatRoomRepository.changedRoomIdsResult = Either.Left(networkError)

        val result = chatSyncManager.performCatchUpSync()

        assertTrue(result.isLeft())
        assertEquals(networkError, (result as Either.Left).value)
        assertEquals(ChatSyncState.Failed(networkError), chatSyncManager.syncState.value)
    }

    @Test
    fun `performCatchUpSync fails when any room message prefetch fails`() = runTest(testDispatcher) {
        fakeChatRoomRepository.changedRoomIdsResult = Either.Right(listOf("room-1", "room-2"))
        val roomError = DataError.Connectivity.Timeout
        fakeChatMessageRepository.syncResultForRoom = { roomId ->
            if (roomId == "room-2") Either.Left(roomError) else Either.Right(false)
        }

        val result = chatSyncManager.performCatchUpSync()

        assertTrue(result.isLeft())
        assertEquals(roomError, (result as Either.Left).value)
        assertEquals(ChatSyncState.Failed(roomError), chatSyncManager.syncState.value)
    }

    @Test
    fun `when appScope is cancelled during catch-up sync and online, syncState transitions to Connecting`() = runTest(testDispatcher) {
        val hangDeferred = CompletableDeferred<Either<DataError, List<String>>>()
        fakeChatRoomRepository.customSyncBlock = { hangDeferred.await() }

        launch {
            chatSyncManager.performCatchUpSync()
        }
        testScheduler.runCurrent()

        assertEquals(ChatSyncState.Syncing, chatSyncManager.syncState.value)

        testScope.cancel()
        testScheduler.runCurrent()

        assertEquals(ChatSyncState.Connecting, chatSyncManager.syncState.value)
    }

    @Test
    fun `when appScope is cancelled during catch-up sync and offline, syncState transitions to Offline`() = runTest(testDispatcher) {
        fakeNetworkMonitor.setConnected(false)
        testScheduler.runCurrent()

        val hangDeferred = CompletableDeferred<Either<DataError, List<String>>>()
        fakeChatRoomRepository.customSyncBlock = { hangDeferred.await() }

        launch {
            chatSyncManager.performCatchUpSync()
        }
        testScheduler.runCurrent()

        testScope.cancel()
        testScheduler.runCurrent()

        assertEquals(ChatSyncState.Offline, chatSyncManager.syncState.value)
    }

    @Test
    fun `when network drops mid-sync, syncState yields Offline instead of Synced or Failed`() = runTest(testDispatcher) {
        fakeChatRoomRepository.customSyncBlock = {
            fakeNetworkMonitor.setConnected(false)
            testScheduler.runCurrent()
            Either.Right(emptyList())
        }

        val result = chatSyncManager.performCatchUpSync()

        assertTrue(result.isRight())
        assertEquals(ChatSyncState.Offline, chatSyncManager.syncState.value)
    }

    @Test
    fun `retry calls reconnect only when connection is FAILED`() = runTest(testDispatcher) {
        fakeRealtimeConnectionManager.connectionStateFlow.value = RealtimeConnectionState.FAILED
        fakeChatRoomRepository.changedRoomIdsResult = Either.Right(emptyList())

        val result = chatSyncManager.retry()

        assertTrue(result.isRight())
        assertEquals(1, fakeRealtimeConnectionManager.reconnectCalledCount)
        assertEquals(ChatSyncState.Synced, chatSyncManager.syncState.value)
    }

    @Test
    fun `retry does not call reconnect when connection is not FAILED`() = runTest(testDispatcher) {
        fakeRealtimeConnectionManager.connectionStateFlow.value = RealtimeConnectionState.CONNECTED
        fakeChatRoomRepository.changedRoomIdsResult = Either.Right(emptyList())

        val result = chatSyncManager.retry()

        assertTrue(result.isRight())
        assertEquals(0, fakeRealtimeConnectionManager.reconnectCalledCount)
        assertEquals(ChatSyncState.Synced, chatSyncManager.syncState.value)
    }

    @Test
    fun `performCatchUpSync bounds concurrent room message prefetching to 3 via Semaphore`() =
        runTest(testDispatcher) {
            val roomIds = (1..6).map { "room-$it" }
            fakeChatRoomRepository.changedRoomIdsResult = Either.Right(roomIds)

            var currentActive = 0
            var maxObservedActive = 0
            val gates = roomIds.associateWith { CompletableDeferred<Either<DataError, Boolean>>() }

            fakeChatMessageRepository.customSyncRecentBlock = { roomId ->
                currentActive++
                if (currentActive > maxObservedActive) {
                    maxObservedActive = currentActive
                }
                try {
                    gates.getValue(roomId).await()
                } finally {
                    currentActive--
                }
            }

            val syncJob = launch {
                val result = chatSyncManager.performCatchUpSync()
                assertTrue(result.isRight())
            }
            testScheduler.runCurrent()

            // First wave: exactly 3 rooms should be actively executing inside Semaphore(3)
            assertEquals(3, currentActive)
            assertEquals(3, maxObservedActive)

            // Release 1 room -> 4th room enters, active count stays capped at 3
            gates.getValue("room-1").complete(Either.Right(false))
            testScheduler.runCurrent()
            assertEquals(3, currentActive)
            assertEquals(3, maxObservedActive)

            // Release remaining rooms
            roomIds.drop(1).forEach { gates.getValue(it).complete(Either.Right(false)) }
            testScheduler.advanceUntilIdle()
            syncJob.join()

            assertEquals(0, currentActive)
            assertEquals(3, maxObservedActive)
            assertEquals(6, fakeChatMessageRepository.syncedRoomIds.size)
            assertEquals(ChatSyncState.Synced, chatSyncManager.syncState.value)
        }

    @Test
    fun `performCatchUpSync coalesces concurrent calls and isolates caller cancellation`() =
        runTest(testDispatcher) {
            val gate = CompletableDeferred<Either<DataError, List<String>>>()
            fakeChatRoomRepository.customSyncBlock = { gate.await() }

            val caller1 = launch { chatSyncManager.performCatchUpSync() }
            var caller2Result: Either<DataError, Unit>? = null
            val caller2 = launch { caller2Result = chatSyncManager.performCatchUpSync() }

            testScheduler.runCurrent()
            assertEquals(1, fakeChatRoomRepository.syncChatsAndGetChangedRoomsCallCount)

            // Cancel caller 1 mid-flight; caller 2 must still complete from the shared job
            caller1.cancel()
            testScheduler.runCurrent()

            gate.complete(Either.Right(listOf("room-1")))
            testScheduler.advanceUntilIdle()
            caller2.join()

            assertEquals(Either.Right(Unit), caller2Result)
            assertEquals(1, fakeChatRoomRepository.syncChatsAndGetChangedRoomsCallCount)
            assertEquals(ChatSyncState.Synced, chatSyncManager.syncState.value)
        }

    private class FakeChatRoomRepository : ChatRoomRepository {
        var syncChatsAndGetChangedRoomsCallCount = 0
        var changedRoomIdsResult: Either<DataError, List<String>> = Either.Right(emptyList())
        var customSyncBlock: (suspend () -> Either<DataError, List<String>>)? = null

        override fun getChats(): Flow<List<ChatRoom>> = emptyFlow()
        override suspend fun syncChats(): Either<DataError, Unit> = Either.Right(Unit)

        override suspend fun syncChatsAndGetChangedRooms(): Either<DataError, List<String>> {
            syncChatsAndGetChangedRoomsCallCount++
            return customSyncBlock?.invoke() ?: changedRoomIdsResult
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
        var syncResultForRoom: (String) -> Either<DataError, Boolean> = { Either.Right(false) }
        var customSyncRecentBlock: (suspend (String) -> Either<DataError, Boolean>)? = null

        override fun getMessages(roomId: String, limit: Int): Flow<List<ChatMessage>> = emptyFlow()

        override suspend fun syncRecentMessages(roomId: String): Either<DataError, Boolean> {
            syncedRoomIds.add(roomId)
            return customSyncRecentBlock?.invoke(roomId) ?: syncResultForRoom(roomId)
        }

        override suspend fun loadOlderMessages(roomId: String): Either<DataError, Boolean> = Either.Right(false)
        override suspend fun sendMessage(roomId: String, content: String): Either<DataError, Unit> = Either.Right(Unit)
        override suspend fun retryMessage(messageId: String): Either<DataError, Unit> = Either.Right(Unit)
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

    private class FakeRealtimeConnectionManager : RealtimeConnectionManager {
        val connectionStateFlow = MutableStateFlow<RealtimeConnectionState>(RealtimeConnectionState.DISCONNECTED)
        override val connectionState: StateFlow<RealtimeConnectionState> = connectionStateFlow
        var reconnectCalledCount = 0

        override fun initialize() {}
        override fun reconnect() {
            reconnectCalledCount++
        }
    }
}
