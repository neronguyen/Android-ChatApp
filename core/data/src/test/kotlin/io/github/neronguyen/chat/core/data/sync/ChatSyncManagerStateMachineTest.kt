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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class ChatSyncManagerStateMachineTest {

    private val seeds = listOf(101L, 2026L, 4242L)

    @Test
    fun `randomized sync, message failure, network, and concurrent retry sequences never stick in Syncing`() {
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
                val roomRepo = StateMachineChatRoomRepository(tokenDataSource)
                val messageRepo = StateMachineChatMessageRepository()
                val scheduler = object : ChatSyncScheduler {
                    override fun enqueueOneTimeCatchUpSync() {}
                    override fun schedulePeriodicSync() {}
                    override fun cancelAllSyncWork() {}
                }
                val realtimeManager = object : RealtimeConnectionManager {
                    val flow = MutableStateFlow(RealtimeConnectionState.DISCONNECTED)
                    override val connectionState: StateFlow<RealtimeConnectionState> = flow
                    override fun initialize() {}
                    override fun reconnect() {}
                }
                val networkMonitor = TestNetworkMonitor(initialOnline = true)

                val manager = ChatSyncManagerImpl(
                    chatRoomRepository = roomRepo,
                    chatMessageRepository = messageRepo,
                    chatSyncScheduler = scheduler,
                    tokenDataSource = tokenDataSource,
                    realtimeConnectionManager = realtimeManager,
                    networkMonitor = networkMonitor,
                    appScope = backgroundScope,
                    ioDispatcher = dispatcher,
                )
                manager.initialize()
                runCurrent()

                var isAuthenticated = false

                repeat(60) { step ->
                    when (random.nextInt(7)) {
                        0 -> {
                            isAuthenticated = true
                            tokenDataSource.setAuthState(
                                AuthState.Authenticated(
                                    user = User("user-1", "alice@example.com", "Alice", true),
                                    accessToken = "access-token",
                                ),
                            )
                        }

                        1 -> {
                            isAuthenticated = false
                            tokenDataSource.setAuthState(AuthState.Unauthenticated)
                        }

                        2 -> networkMonitor.setConnected(true)
                        3 -> networkMonitor.setConnected(false)
                        4 -> {
                            roomRepo.nextResult = when (random.nextInt(4)) {
                                0 -> Either.Right(emptyList())
                                1 -> Either.Right(listOf("room-1", "room-2"))
                                2 -> Either.Left(DataError.Connectivity.NoInternet)
                                else -> Either.Left(DataError.Connectivity.Timeout)
                            }
                            messageRepo.nextResult = when (random.nextInt(3)) {
                                0 -> Either.Right(false)
                                1 -> Either.Left(DataError.Connectivity.Timeout)
                                else -> Either.Left(DataError.Remote.Unavailable)
                            }
                            manager.performCatchUpSync()
                        }

                        5 -> {
                            roomRepo.nextResult = Either.Right(listOf("room-1"))
                            messageRepo.nextResult = Either.Right(false)
                            manager.retry()
                        }

                        6 -> {
                            // Exercise concurrent catch-up and retry triggers through SingleFlight
                            roomRepo.nextResult = Either.Right(listOf("room-1", "room-2"))
                            messageRepo.nextResult = if (random.nextBoolean()) {
                                Either.Right(false)
                            } else {
                                Either.Left(DataError.Connectivity.Timeout)
                            }
                            val job1 = launch { manager.performCatchUpSync() }
                            val job2 = launch { manager.retry() }
                            runCurrent()
                            job1.join()
                            job2.join()
                        }
                    }

                    runCurrent()

                    val state = manager.syncState.value
                    assertTrue(
                        "seed=$seed step=$step: syncState must settle after runCurrent (was $state)",
                        state !is ChatSyncState.Syncing,
                    )
                    if (!isAuthenticated) {
                        assertTrue(
                            "seed=$seed step=$step: unauthenticated syncState must never be Synced (was $state)",
                            state != ChatSyncState.Synced,
                        )
                    }
                }

                // Final convergence: authenticated + online + healthy repos -> Synced
                tokenDataSource.setAuthState(
                    AuthState.Authenticated(
                        user = User("user-1", "alice@example.com", "Alice", true),
                        accessToken = "access-token",
                    ),
                )
                networkMonitor.setConnected(true)
                roomRepo.nextResult = Either.Right(listOf("room-1"))
                messageRepo.nextResult = Either.Right(false)
                runCurrent()

                val retryResult = manager.retry()
                runCurrent()

                assertTrue("seed=$seed: retry should succeed", retryResult.isRight())
                assertEquals(
                    "seed=$seed: healthy retry must converge to Synced",
                    ChatSyncState.Synced,
                    manager.syncState.value,
                )
            }
        } finally {
            testScope.cancel()
        }
    }

    private class StateMachineChatRoomRepository(
        private val tokenDataSource: StateMachineTokenDataSource,
    ) : ChatRoomRepository {
        var nextResult: Either<DataError, List<String>> = Either.Right(emptyList())

        override fun getChats(): Flow<List<ChatRoom>> = emptyFlow()
        override suspend fun syncChats(): Either<DataError, Unit> = syncChatsAndGetChangedRooms().map {}
        override suspend fun syncChatsAndGetChangedRooms(): Either<DataError, List<String>> =
            if (!tokenDataSource.isAuthenticated()) {
                Either.Left(DataError.Local.NotAuthenticated)
            } else {
                nextResult
            }

        override suspend fun findChatParticipantByEmail(email: String): Either<ChatError.FindParticipant, ChatParticipant> =
            Either.Left(ChatError.ParticipantNotFound)

        override suspend fun createDirectChatRoom(targetUserId: String, message: String): Either<ChatError.CreateRoom, ChatRoom> =
            Either.Left(ChatError.InvalidRoomSize)

        override suspend fun createGroupChatRoom(participantIds: List<String>, name: String?): Either<ChatError.CreateRoom, ChatRoom> =
            Either.Left(ChatError.InvalidRoomSize)

        override fun getChatRoom(roomId: String): Flow<ChatRoom?> = emptyFlow()
        override suspend fun syncChatRoom(roomId: String): Either<ChatError.SyncRoom, Unit> = Either.Right(Unit)
        override suspend fun addParticipantsToGroup(roomId: String, userIds: List<String>): Either<ChatError.AddParticipants, ChatRoom> =
            Either.Left(ChatError.RoomNotFound)

        override suspend fun leaveChatRoom(roomId: String): Either<ChatError.LeaveRoom, Unit> = Either.Right(Unit)
    }

    private class StateMachineChatMessageRepository : ChatMessageRepository {
        var nextResult: Either<DataError, Boolean> = Either.Right(false)

        override fun getMessages(roomId: String, limit: Int): Flow<List<ChatMessage>> = emptyFlow()
        override suspend fun syncRecentMessages(roomId: String): Either<DataError, Boolean> = nextResult
        override suspend fun loadOlderMessages(roomId: String): Either<DataError, Boolean> = Either.Right(false)
        override suspend fun sendMessage(roomId: String, content: String): Either<DataError, Unit> = Either.Right(Unit)
        override suspend fun retryMessage(messageId: String): Either<DataError, Unit> = Either.Right(Unit)
        override suspend fun deleteMessage(messageId: String): Either<ChatError.DeleteMessage, Unit> = Either.Right(Unit)
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

        override suspend fun getAccessToken(): String? = null
        override suspend fun getRefreshToken(): String? = null
    }
}
