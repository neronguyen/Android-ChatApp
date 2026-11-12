package io.github.neronguyen.chat.feature.chat.presentation.list

import arrow.core.Either
import io.github.neronguyen.chat.core.data.repository.AuthRepository
import io.github.neronguyen.chat.core.data.repository.ChatRoomRepository
import io.github.neronguyen.chat.core.model.AuthState
import io.github.neronguyen.chat.core.model.ChatMessage
import io.github.neronguyen.chat.core.model.ChatParticipant
import io.github.neronguyen.chat.core.model.ChatRoom
import io.github.neronguyen.chat.core.model.ChatRoomType
import io.github.neronguyen.chat.core.model.ChatSyncState
import io.github.neronguyen.chat.core.model.MessageDeliveryStatus
import io.github.neronguyen.chat.core.model.User
import io.github.neronguyen.chat.core.model.error.AuthError
import io.github.neronguyen.chat.core.model.error.ChatError
import io.github.neronguyen.chat.core.model.error.DataError
import io.github.neronguyen.chat.feature.chat.presentation.FakeChatSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ChatListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeChatRoomRepository: FakeChatRoomRepository
    private lateinit var fakeAuthRepository: FakeAuthRepository
    private lateinit var fakeChatSyncManager: FakeChatSyncManager
    private lateinit var viewModel: ChatListViewModel

    private val currentUser = User(
        id = "user-me",
        email = "me@example.com",
        displayName = "Me",
        isEmailVerified = true,
    )
    private val meParticipant = ChatParticipant(
        userId = currentUser.id,
        email = currentUser.email,
        displayName = currentUser.displayName,
    )
    private val elenaParticipant = ChatParticipant(
        userId = "user-elena",
        email = "elena@example.com",
        displayName = "Elena Rostova",
    )
    private val marcusParticipant = ChatParticipant(
        userId = "user-marcus",
        email = "marcus@example.com",
        displayName = "Marcus",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeChatRoomRepository = FakeChatRoomRepository()
        fakeAuthRepository = FakeAuthRepository(
            initialState = AuthState.Authenticated(currentUser, "token"),
        )
        fakeChatSyncManager = FakeChatSyncManager(initialState = ChatSyncState.Synced)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial sync loads rooms sorted by most recent activity`() = runTest {
        val olderRoom = ChatRoom(
            id = "room-older",
            type = ChatRoomType.GROUP,
            name = "Backend Core Team",
            creator = meParticipant,
            participants = listOf(meParticipant, marcusParticipant),
            lastMessage = ChatMessage(
                id = "msg-1",
                roomId = "room-older",
                senderId = marcusParticipant.userId,
                content = "Reviewed the message persistence layer.",
                timestamp = Instant.parse("2026-03-14T10:00:00Z"),
                deliveryStatus = MessageDeliveryStatus.SENT,
            ),
            lastActivityAt = Instant.parse("2026-03-14T10:00:00Z"),
        )
        val newerRoom = ChatRoom(
            id = "room-newer",
            type = ChatRoomType.DIRECT,
            creator = meParticipant,
            participants = listOf(meParticipant, elenaParticipant),
            lastMessage = ChatMessage(
                id = "msg-2",
                roomId = "room-newer",
                senderId = elenaParticipant.userId,
                content = "The sync contracts are finalized.",
                timestamp = Instant.parse("2026-03-15T10:42:00Z"),
                deliveryStatus = MessageDeliveryStatus.SENT,
            ),
            lastActivityAt = Instant.parse("2026-03-15T10:42:00Z"),
        )
        fakeChatRoomRepository.roomsFlow.value = listOf(olderRoom, newerRoom)

        viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ChatSyncState.Synced, state.syncState)
        assertTrue(state.isInitialLoadComplete)
        assertFalse(state.showSyncBanner)
        assertEquals(listOf("room-newer", "room-older"), state.chats.map { it.id })
        assertEquals("Elena Rostova", state.chats[0].title)
        assertEquals("Backend Core Team", state.chats[1].title)
    }

    @Test
    fun `when sync fails, cached chats remain visible and syncState is exposed until dismissed`() =
        runTest {
            val cachedRoom = ChatRoom(
                id = "room-1",
                type = ChatRoomType.DIRECT,
                creator = meParticipant,
                participants = listOf(meParticipant, elenaParticipant),
            )
            fakeChatRoomRepository.roomsFlow.value = listOf(cachedRoom)
            fakeChatSyncManager.syncStateFlow.value = ChatSyncState.Failed(DataError.Connectivity.NoInternet)

            viewModel = createViewModel()
            testScheduler.advanceUntilIdle()

            val stateWithError = viewModel.uiState.value
            assertEquals(1, stateWithError.chats.size)
            assertEquals(ChatSyncState.Failed(DataError.Connectivity.NoInternet), stateWithError.syncState)
            assertTrue(stateWithError.showSyncBanner)

            viewModel.onEvent(ChatListUiEvent.DismissSyncBanner)
            testScheduler.advanceUntilIdle()

            assertFalse(viewModel.uiState.value.showSyncBanner)
        }

    @Test
    fun `when sync fails offline and subsequent sync succeeds on refresh, sync banner is cleared`() =
        runTest {
            val cachedRoom = ChatRoom(
                id = "room-1",
                type = ChatRoomType.DIRECT,
                creator = meParticipant,
                participants = listOf(meParticipant, elenaParticipant),
            )
            fakeChatRoomRepository.roomsFlow.value = listOf(cachedRoom)
            fakeChatSyncManager.syncStateFlow.value = ChatSyncState.Offline

            viewModel = createViewModel()
            testScheduler.advanceUntilIdle()

            val stateWithError = viewModel.uiState.value
            assertEquals(ChatSyncState.Offline, stateWithError.syncState)
            assertTrue(stateWithError.showSyncBanner)

            // Subsequent refresh triggers retry
            viewModel.onEvent(ChatListUiEvent.RefreshChats)
            testScheduler.advanceUntilIdle()

            assertEquals(1, fakeChatSyncManager.retryCallCount)

            fakeChatSyncManager.syncStateFlow.value = ChatSyncState.Synced
            testScheduler.advanceUntilIdle()

            val recoveredState = viewModel.uiState.value
            assertEquals(ChatSyncState.Synced, recoveredState.syncState)
            assertFalse(recoveredState.showSyncBanner)
        }

    @Test
    fun `dismissal persists across positive recovery but resets on negative regression`() =
        runTest {
            fakeChatSyncManager.syncStateFlow.value = ChatSyncState.Offline
            viewModel = createViewModel()
            testScheduler.advanceUntilIdle()
            assertTrue(viewModel.uiState.value.showSyncBanner)

            // User dismisses banner while Offline
            viewModel.onEvent(ChatListUiEvent.DismissSyncBanner)
            testScheduler.advanceUntilIdle()
            assertFalse(viewModel.uiState.value.showSyncBanner)

            // Positive recovery progression: Offline -> Connecting -> Syncing -> Synced
            fakeChatSyncManager.syncStateFlow.value = ChatSyncState.Connecting
            testScheduler.advanceUntilIdle()
            assertFalse(viewModel.uiState.value.showSyncBanner)

            fakeChatSyncManager.syncStateFlow.value = ChatSyncState.Syncing
            testScheduler.advanceUntilIdle()
            assertFalse(viewModel.uiState.value.showSyncBanner)

            fakeChatSyncManager.syncStateFlow.value = ChatSyncState.Synced
            testScheduler.advanceUntilIdle()
            assertFalse(viewModel.uiState.value.showSyncBanner)

            // Negative regression back to Offline: dismissal resets
            fakeChatSyncManager.syncStateFlow.value = ChatSyncState.Offline
            testScheduler.advanceUntilIdle()
            assertTrue(viewModel.uiState.value.showSyncBanner)
        }

    @Test
    fun `search query filters conversations by title or preview and ClearSearch restores full list`() =
        runTest {
            val room1 = ChatRoom(
                id = "room-1",
                type = ChatRoomType.DIRECT,
                creator = meParticipant,
                participants = listOf(meParticipant, elenaParticipant),
                lastMessage = ChatMessage(
                    id = "msg-1",
                    roomId = "room-1",
                    senderId = elenaParticipant.userId,
                    content = "The sync contracts are finalized.",
                    timestamp = Instant.parse("2026-03-15T10:42:00Z"),
                    deliveryStatus = MessageDeliveryStatus.SENT,
                ),
                lastActivityAt = Instant.parse("2026-03-15T10:42:00Z"),
            )
            val room2 = ChatRoom(
                id = "room-2",
                type = ChatRoomType.GROUP,
                name = "Backend Core Team",
                creator = meParticipant,
                participants = listOf(meParticipant, marcusParticipant),
                lastMessage = ChatMessage(
                    id = "msg-2",
                    roomId = "room-2",
                    senderId = marcusParticipant.userId,
                    content = "Reviewed the message persistence layer.",
                    timestamp = Instant.parse("2026-03-14T10:00:00Z"),
                    deliveryStatus = MessageDeliveryStatus.SENT,
                ),
                lastActivityAt = Instant.parse("2026-03-14T10:00:00Z"),
            )
            fakeChatRoomRepository.roomsFlow.value = listOf(room1, room2)

            viewModel = createViewModel()
            testScheduler.advanceUntilIdle()
            assertEquals(2, viewModel.uiState.value.chats.size)

            viewModel.onSearchQueryChanged("persistence")
            testScheduler.advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.chats.size)
            assertEquals("Backend Core Team", viewModel.uiState.value.chats.single().title)

            viewModel.onEvent(ChatListUiEvent.ClearSearch)
            testScheduler.advanceUntilIdle()

            assertEquals("", viewModel.searchState.text.toString())
            assertEquals(2, viewModel.uiState.value.chats.size)
        }

    private fun createViewModel(): ChatListViewModel {
        return ChatListViewModel(
            chatRoomRepository = fakeChatRoomRepository,
            authRepository = fakeAuthRepository,
            chatSyncManager = fakeChatSyncManager,
        ).apply {
            nowProvider = { Instant.parse("2026-03-15T14:00:00Z") }
            zoneIdProvider = { ZoneId.of("UTC") }
        }
    }
}

private class FakeChatRoomRepository : ChatRoomRepository {
    val roomsFlow = MutableStateFlow<List<ChatRoom>>(emptyList())
    var syncCallCount = 0

    override fun getChats(): Flow<List<ChatRoom>> = roomsFlow

    override suspend fun syncChatsAndGetChangedRooms(): Either<DataError, List<String>> =
        Either.Right(emptyList())

    override suspend fun syncChats(): Either<DataError, Unit> {
        syncCallCount++
        return Either.Right(Unit)
    }

    override suspend fun findChatParticipantByEmail(
        email: String,
    ): Either<ChatError.FindParticipant, ChatParticipant> {
        throw UnsupportedOperationException()
    }

    override suspend fun createDirectChatRoom(
        targetUserId: String,
        message: String,
    ): Either<ChatError.CreateRoom, ChatRoom> {
        throw UnsupportedOperationException()
    }

    override suspend fun createGroupChatRoom(
        participantIds: List<String>,
        name: String?,
    ): Either<ChatError.CreateRoom, ChatRoom> {
        throw UnsupportedOperationException()
    }

    override fun getChatRoom(roomId: String): Flow<ChatRoom?> = kotlinx.coroutines.flow.emptyFlow()

    override suspend fun syncChatRoom(roomId: String): Either<ChatError.SyncRoom, Unit> =
        Either.Right(Unit)

    override suspend fun addParticipantsToGroup(
        roomId: String,
        userIds: List<String>
    ): Either<ChatError.AddParticipants, ChatRoom> = throw UnsupportedOperationException()

    override suspend fun leaveChatRoom(roomId: String): Either<ChatError.LeaveRoom, Unit> =
        Either.Right(Unit)
}

private class FakeAuthRepository(
    initialState: AuthState,
) : AuthRepository {
    private val stateFlow = MutableStateFlow(initialState)

    override fun getAuthState(): Flow<AuthState> = stateFlow

    override suspend fun login(
        email: String,
        password: String,
    ): Either<AuthError.Login, User> {
        throw UnsupportedOperationException()
    }

    override suspend fun register(
        email: String,
        displayName: String,
        password: String,
    ): Either<AuthError.Register, User> {
        throw UnsupportedOperationException()
    }

    override suspend fun verifyEmail(email: String, code: String): Either<AuthError.VerifyEmail, Unit> = Either.Right(Unit)

    override suspend fun resendVerification(email: String): Either<DataError, Unit> = Either.Right(Unit)

    override suspend fun forgotPassword(email: String): Either<DataError, Unit> = Either.Right(Unit)

    override suspend fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
    ): Either<AuthError.ResetPassword, Unit> = Either.Right(Unit)

    override suspend fun logout(): Either<DataError, Unit> = Either.Right(Unit)
}
