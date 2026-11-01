package io.github.neronguyen.chat.feature.chat.presentation.conversation

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import arrow.core.Either
import io.github.neronguyen.chat.core.data.realtime.RealtimeConnectionManager
import io.github.neronguyen.chat.core.data.repository.AuthRepository
import io.github.neronguyen.chat.core.data.repository.ChatMessageRepository
import io.github.neronguyen.chat.core.data.repository.ChatRoomRepository
import io.github.neronguyen.chat.core.model.AuthState
import io.github.neronguyen.chat.core.model.ChatMessage
import io.github.neronguyen.chat.core.model.ChatParticipant
import io.github.neronguyen.chat.core.model.ChatRoom
import io.github.neronguyen.chat.core.model.ChatRoomType
import io.github.neronguyen.chat.core.model.MessageDeliveryStatus
import io.github.neronguyen.chat.core.model.RealtimeConnectionState
import io.github.neronguyen.chat.core.model.User
import io.github.neronguyen.chat.core.model.error.AuthError
import io.github.neronguyen.chat.core.model.error.ChatError
import io.github.neronguyen.chat.core.model.error.DataError
import io.github.neronguyenvn.nerochat.contract.validation.chat.ChatValidationError
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeChatRoomRepository: FakeChatRoomRepository
    private lateinit var fakeChatMessageRepository: FakeChatMessageRepository
    private lateinit var fakeRealtimeConnectionManager: FakeRealtimeConnectionManager
    private lateinit var fakeAuthRepository: FakeAuthRepository
    private lateinit var viewModel: ConversationViewModel

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

    private val sampleRoom = ChatRoom(
        id = "room-123",
        type = ChatRoomType.DIRECT,
        creator = meParticipant,
        participants = listOf(elenaParticipant),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeChatRoomRepository = FakeChatRoomRepository(initialRoom = sampleRoom)
        fakeChatMessageRepository = FakeChatMessageRepository()
        fakeRealtimeConnectionManager = FakeRealtimeConnectionManager()
        fakeAuthRepository = FakeAuthRepository(
            initialAuthState = AuthState.Authenticated(currentUser, "token"),
        )

        viewModel = ConversationViewModel(
            roomId = "room-123",
            chatRoomRepository = fakeChatRoomRepository,
            chatMessageRepository = fakeChatMessageRepository,
            realtimeConnectionManager = fakeRealtimeConnectionManager,
            authRepository = fakeAuthRepository,
        ).apply {
            nowProvider = { Instant.parse("2026-10-17T12:00:00Z") }
            zoneIdProvider = { ZoneId.of("UTC") }
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init triggers room sync and message sync, mapping uiState correctly`() = runTest {
        val messages = listOf(
            ChatMessage(
                id = "m1",
                roomId = "room-123",
                senderId = "user-elena",
                content = "Hello there",
                timestamp = Instant.parse("2026-10-17T10:00:00Z"),
                deliveryStatus = MessageDeliveryStatus.SENT,
            ),
        )
        fakeChatMessageRepository.messagesFlow.value = messages

        testScheduler.advanceUntilIdle()

        assertEquals("Elena Rostova", viewModel.uiState.value.title)
        assertEquals("elena@example.com", viewModel.uiState.value.subtitle)
        assertEquals(ChatRoomType.DIRECT, viewModel.uiState.value.roomType)
        assertFalse(viewModel.uiState.value.isInitialLoading)
        assertEquals(1, fakeChatRoomRepository.syncChatRoomCalls.size)
        assertEquals(1, fakeChatMessageRepository.syncRecentMessagesCalls.size)

        val messageItems =
            viewModel.uiState.value.items.filterIsInstance<ConversationListItem.MessageItem>()
        assertEquals(1, messageItems.size)
        assertEquals("Hello there", messageItems[0].message.content)
    }

    @Test
    fun `sendMessage with text clears composer and invokes chatMessageRepository`() = runTest {
        testScheduler.advanceUntilIdle()

        viewModel.composerState.setTextAndPlaceCursorAtEnd("Ready to review.")
        viewModel.onEvent(ConversationUiEvent.SendMessage)

        testScheduler.advanceUntilIdle()

        assertEquals("", viewModel.composerState.text.toString())
        assertEquals(1, fakeChatMessageRepository.sentMessages.size)
        assertEquals(
            "room-123" to "Ready to review.",
            fakeChatMessageRepository.sentMessages.first()
        )
    }

    @Test
    fun `sendMessage with blank text does nothing`() = runTest {
        testScheduler.advanceUntilIdle()

        viewModel.composerState.setTextAndPlaceCursorAtEnd("   ")
        viewModel.onEvent(ConversationUiEvent.SendMessage)

        testScheduler.advanceUntilIdle()

        assertEquals(0, fakeChatMessageRepository.sentMessages.size)
    }

    @Test
    fun `retryMessage invokes chatMessageRepository retryMessage`() = runTest {
        testScheduler.advanceUntilIdle()

        viewModel.onEvent(ConversationUiEvent.RetryMessage("failed-msg-id"))

        testScheduler.advanceUntilIdle()

        assertEquals(1, fakeChatMessageRepository.retriedMessageIds.size)
        assertEquals("failed-msg-id", fakeChatMessageRepository.retriedMessageIds.first())
    }

    @Test
    fun `deleteMessage invokes chatMessageRepository deleteMessage`() = runTest {
        testScheduler.advanceUntilIdle()

        viewModel.onEvent(ConversationUiEvent.DeleteMessage("msg-to-delete"))

        testScheduler.advanceUntilIdle()

        assertEquals(1, fakeChatMessageRepository.deletedMessageIds.size)
        assertEquals("msg-to-delete", fakeChatMessageRepository.deletedMessageIds.first())
    }

    @Test
    fun `loadOlderMessages increments page and updates hasMoreOlderMessages`() = runTest {
        fakeChatMessageRepository.loadOlderMessagesResult = Either.Right(false)
        testScheduler.advanceUntilIdle()

        viewModel.onEvent(ConversationUiEvent.LoadOlderMessages)

        testScheduler.advanceUntilIdle()

        assertEquals(1, fakeChatMessageRepository.loadOlderMessagesCalls.size)
        assertFalse(viewModel.uiState.value.hasMoreOlderMessages)
        assertFalse(viewModel.uiState.value.isLoadingOlder)
    }

    @Test
    fun `connectionState changes are reflected in uiState showConnectionBanner`() = runTest {
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showConnectionBanner)

        fakeRealtimeConnectionManager.connectionStateFlow.value = RealtimeConnectionState.CONNECTING
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showConnectionBanner)
        assertEquals(RealtimeConnectionState.CONNECTING, viewModel.uiState.value.connectionState)
    }

    @Test
    fun `leaveRoom on success sets hasLeftRoom to true`() = runTest {
        testScheduler.advanceUntilIdle()

        viewModel.onEvent(ConversationUiEvent.LeaveRoom)

        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasLeftRoom)
        assertFalse(viewModel.uiState.value.isLeavingRoom)
        assertEquals(1, fakeChatRoomRepository.leaveChatRoomCalls.size)
    }

    @Test
    fun `leaveRoom on error sets actionError`() = runTest {
        val leaveError = ChatError.Data(DataError.Remote.Forbidden)
        fakeChatRoomRepository.leaveChatRoomResult = Either.Left(leaveError)
        testScheduler.advanceUntilIdle()

        viewModel.onEvent(ConversationUiEvent.LeaveRoom)

        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasLeftRoom)
        assertFalse(viewModel.uiState.value.isLeavingRoom)
        assertEquals(leaveError, viewModel.uiState.value.actionError)
    }

    @Test
    fun `searchParticipantByEmail with invalid email sets validationError without network call`() = runTest {
        testScheduler.advanceUntilIdle()

        viewModel.onEvent(ConversationUiEvent.OpenAddParticipantDialog)
        testScheduler.advanceUntilIdle()

        viewModel.addParticipantEmailState.setTextAndPlaceCursorAtEnd("not-an-email")
        viewModel.onEvent(ConversationUiEvent.SearchParticipantByEmail)

        testScheduler.advanceUntilIdle()

        val dialog = viewModel.uiState.value.addParticipantDialogState
        assertEquals(ChatValidationError.InvalidSearchEmail, dialog?.validationError)
        assertNull(dialog?.searchedParticipant)
    }

    @Test
    fun `searchParticipantByEmail with self email sets SelfParticipantNotAllowed`() = runTest {
        testScheduler.advanceUntilIdle()

        viewModel.onEvent(ConversationUiEvent.OpenAddParticipantDialog)
        testScheduler.advanceUntilIdle()

        viewModel.addParticipantEmailState.setTextAndPlaceCursorAtEnd(currentUser.email)
        viewModel.onEvent(ConversationUiEvent.SearchParticipantByEmail)

        testScheduler.advanceUntilIdle()

        val dialog = viewModel.uiState.value.addParticipantDialogState
        assertEquals(ChatValidationError.SelfParticipantNotAllowed, dialog?.validationError)
        assertNull(dialog?.searchedParticipant)
    }

    @Test
    fun `open and dismiss add participant dialog updates state`() = runTest {
        testScheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.addParticipantDialogState)

        viewModel.onEvent(ConversationUiEvent.OpenAddParticipantDialog)
        testScheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.addParticipantDialogState)

        viewModel.onEvent(ConversationUiEvent.DismissAddParticipantDialog)
        testScheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.addParticipantDialogState)
    }

    @Test
    fun `searchParticipantByEmail and confirmAddParticipant invokes repository`() = runTest {
        fakeChatRoomRepository.findParticipantResult = Either.Right(elenaParticipant)
        fakeChatRoomRepository.addParticipantsResult = Either.Right(sampleRoom)
        testScheduler.advanceUntilIdle()

        viewModel.onEvent(ConversationUiEvent.OpenAddParticipantDialog)
        testScheduler.advanceUntilIdle()

        viewModel.addParticipantEmailState.setTextAndPlaceCursorAtEnd("elena@example.com")
        viewModel.onEvent(ConversationUiEvent.SearchParticipantByEmail)

        testScheduler.advanceUntilIdle()

        val dialog = viewModel.uiState.value.addParticipantDialogState
        assertNotNull(dialog?.searchedParticipant)
        assertEquals("Elena Rostova", dialog?.searchedParticipant?.displayName)

        viewModel.onEvent(ConversationUiEvent.ConfirmAddParticipant)

        testScheduler.advanceUntilIdle()

        assertEquals(1, fakeChatRoomRepository.addParticipantsCalls.size)
        assertNull(viewModel.uiState.value.addParticipantDialogState)
    }

    // --- Test Doubles ---

    private class FakeChatRoomRepository(initialRoom: ChatRoom? = null) : ChatRoomRepository {
        val roomFlow = MutableStateFlow(initialRoom)
        val syncChatRoomCalls = mutableListOf<String>()
        var syncChatRoomResult: Either<ChatError.SyncRoom, Unit> = Either.Right(Unit)

        val leaveChatRoomCalls = mutableListOf<String>()
        var leaveChatRoomResult: Either<ChatError.LeaveRoom, Unit> = Either.Right(Unit)

        var findParticipantResult: Either<ChatError.FindParticipant, ChatParticipant> =
            Either.Left(ChatError.ParticipantNotFound)
        val addParticipantsCalls = mutableListOf<Pair<String, List<String>>>()
        var addParticipantsResult: Either<ChatError.AddParticipants, ChatRoom> =
            Either.Left(ChatError.ParticipantNotFound)

        override fun getChatRoom(roomId: String): Flow<ChatRoom?> = roomFlow
        override suspend fun syncChatRoom(roomId: String): Either<ChatError.SyncRoom, Unit> {
            syncChatRoomCalls.add(roomId)
            return syncChatRoomResult
        }

        override suspend fun leaveChatRoom(roomId: String): Either<ChatError.LeaveRoom, Unit> {
            leaveChatRoomCalls.add(roomId)
            return leaveChatRoomResult
        }

        override suspend fun findChatParticipantByEmail(email: String): Either<ChatError.FindParticipant, ChatParticipant> {
            return findParticipantResult
        }

        override suspend fun addParticipantsToGroup(
            roomId: String,
            userIds: List<String>
        ): Either<ChatError.AddParticipants, ChatRoom> {
            addParticipantsCalls.add(roomId to userIds)
            return addParticipantsResult
        }

        override fun getChats(): Flow<List<ChatRoom>> = MutableStateFlow(emptyList())
        override suspend fun syncChats(): Either<DataError, Unit> = Either.Right(Unit)
        override suspend fun createDirectChatRoom(
            targetUserId: String,
            message: String
        ): Either<ChatError.CreateRoom, ChatRoom> =
            Either.Left(ChatError.ParticipantNotFound)

        override suspend fun createGroupChatRoom(
            participantIds: List<String>,
            name: String?
        ): Either<ChatError.CreateRoom, ChatRoom> =
            Either.Left(ChatError.ParticipantNotFound)
    }

    private class FakeChatMessageRepository : ChatMessageRepository {
        val messagesFlow = MutableStateFlow<List<ChatMessage>>(emptyList())
        val syncRecentMessagesCalls = mutableListOf<String>()
        var syncRecentMessagesResult: Either<DataError, Boolean> = Either.Right(true)

        val loadOlderMessagesCalls = mutableListOf<String>()
        var loadOlderMessagesResult: Either<DataError, Boolean> = Either.Right(false)

        val sentMessages = mutableListOf<Pair<String, String>>()
        val retriedMessageIds = mutableListOf<String>()

        val deletedMessageIds = mutableListOf<String>()
        var deleteMessageResult: Either<ChatError.DeleteMessage, Unit> = Either.Right(Unit)

        override fun getMessages(roomId: String, limit: Int): Flow<List<ChatMessage>> = messagesFlow

        override suspend fun syncRecentMessages(roomId: String): Either<DataError, Boolean> {
            syncRecentMessagesCalls.add(roomId)
            return syncRecentMessagesResult
        }

        override suspend fun loadOlderMessages(roomId: String): Either<DataError, Boolean> {
            loadOlderMessagesCalls.add(roomId)
            return loadOlderMessagesResult
        }

        override suspend fun sendMessage(roomId: String, message: String) {
            sentMessages.add(roomId to message)
        }

        override suspend fun retryMessage(messageId: String) {
            retriedMessageIds.add(messageId)
        }

        override suspend fun deleteMessage(messageId: String): Either<ChatError.DeleteMessage, Unit> {
            deletedMessageIds.add(messageId)
            return deleteMessageResult
        }
    }

    private class FakeRealtimeConnectionManager(
        initialState: RealtimeConnectionState = RealtimeConnectionState.CONNECTED
    ) : RealtimeConnectionManager {
        val connectionStateFlow = MutableStateFlow(initialState)
        override val connectionState: Flow<RealtimeConnectionState> = connectionStateFlow
        override fun initialize() {}
    }

    private class FakeAuthRepository(
        initialAuthState: AuthState = AuthState.Unauthenticated,
        private val user: User = User("user-me", "me@example.com", "Me", true),
    ) : AuthRepository {
        val authStateFlow = MutableStateFlow(initialAuthState)
        override fun getAuthState(): Flow<AuthState> = authStateFlow
        override suspend fun login(
            email: String,
            password: String
        ): Either<AuthError.Login, User> = Either.Right(user)

        override suspend fun register(
            email: String,
            displayName: String,
            password: String
        ): Either<AuthError.Register, User> = Either.Right(user)

        override suspend fun logout(): Either<DataError, Unit> = Either.Right(Unit)
    }
}
