package io.github.neronguyen.chat.feature.chat.presentation.list

import arrow.core.Either
import io.github.neronguyen.chat.core.data.repository.AuthRepository
import io.github.neronguyen.chat.core.data.repository.ChatRoomRepository
import io.github.neronguyen.chat.core.model.AuthState
import io.github.neronguyen.chat.core.model.ChatMessage
import io.github.neronguyen.chat.core.model.ChatParticipant
import io.github.neronguyen.chat.core.model.ChatRoom
import io.github.neronguyen.chat.core.model.ChatRoomType
import io.github.neronguyen.chat.core.model.MessageDeliveryStatus
import io.github.neronguyen.chat.core.model.User
import io.github.neronguyen.chat.core.model.error.AuthError
import io.github.neronguyen.chat.core.model.error.ChatError
import io.github.neronguyen.chat.core.model.error.DataError
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertNull
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
        assertFalse(state.isSyncing)
        assertTrue(state.isInitialLoadComplete)
        assertNull(state.syncError)
        assertEquals(1, fakeChatRoomRepository.syncCallCount)
        assertEquals(listOf("room-newer", "room-older"), state.chats.map { it.id })
        assertEquals("Elena Rostova", state.chats[0].title)
        assertEquals("Backend Core Team", state.chats[1].title)
    }

    @Test
    fun `when sync fails, cached chats remain visible and syncError is exposed until dismissed`() =
        runTest {
            val cachedRoom = ChatRoom(
                id = "room-1",
                type = ChatRoomType.DIRECT,
                creator = meParticipant,
                participants = listOf(meParticipant, elenaParticipant),
            )
            fakeChatRoomRepository.roomsFlow.value = listOf(cachedRoom)
            fakeChatRoomRepository.syncResult = Either.Left(DataError.Connectivity.NoInternet)

            viewModel = createViewModel()
            testScheduler.advanceUntilIdle()

            val stateWithError = viewModel.uiState.value
            assertEquals(1, stateWithError.chats.size)
            assertEquals(DataError.Connectivity.NoInternet, stateWithError.syncError)
            assertTrue(stateWithError.showSyncBanner)

            viewModel.onEvent(ChatListUiEvent.DismissSyncBanner)
            testScheduler.advanceUntilIdle()

            assertFalse(viewModel.uiState.value.showSyncBanner)
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

    @Test
    fun `RefreshChats during active sync does not start duplicate sync call`() = runTest {
        val deferred = CompletableDeferred<Either<DataError, Unit>>()
        fakeChatRoomRepository.syncCompletable = deferred

        viewModel = createViewModel()
        testScheduler.runCurrent()
        assertEquals(1, fakeChatRoomRepository.syncCallCount)
        assertTrue(viewModel.uiState.value.isSyncing)

        viewModel.onEvent(ChatListUiEvent.RefreshChats)
        testScheduler.runCurrent()
        assertEquals(1, fakeChatRoomRepository.syncCallCount)

        deferred.complete(Either.Right(Unit))
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSyncing)
    }

    private fun createViewModel(): ChatListViewModel {
        return ChatListViewModel(
            chatRoomRepository = fakeChatRoomRepository,
            authRepository = fakeAuthRepository,
        ).apply {
            nowProvider = { Instant.parse("2026-03-15T14:00:00Z") }
            zoneIdProvider = { ZoneId.of("UTC") }
        }
    }
}

private class FakeChatRoomRepository : ChatRoomRepository {
    val roomsFlow = MutableStateFlow<List<ChatRoom>>(emptyList())
    var syncCallCount = 0
    var syncResult: Either<DataError, Unit> = Either.Right(Unit)
    var syncCompletable: CompletableDeferred<Either<DataError, Unit>>? = null

    override fun getChats(): Flow<List<ChatRoom>> = roomsFlow

    override suspend fun syncChats(): Either<DataError, Unit> {
        syncCallCount++
        val completable = syncCompletable
        return if (completable != null) {
            completable.await()
        } else {
            syncResult
        }
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

    override suspend fun logout(): Either<DataError, Unit> = Either.Right(Unit)
}
