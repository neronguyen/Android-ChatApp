package io.github.neronguyen.chat.feature.chat.presentation

import arrow.core.Either
import io.github.neronguyen.chat.core.data.repository.AuthRepository
import io.github.neronguyen.chat.core.data.repository.ChatMessageRepository
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

internal class FakeChatAuthRepository(
    initialUser: User = User(
        id = "user-me",
        email = "alice@example.com",
        displayName = "Alice",
        isEmailVerified = true,
    ),
) : AuthRepository {

    val authStateFlow =
        MutableStateFlow<AuthState>(AuthState.Authenticated(initialUser, "access-token"))
    var logoutCallCount = 0
    var logoutResult: Either<DataError, Unit> = Either.Right(Unit)

    override fun getAuthState(): Flow<AuthState> = authStateFlow.asStateFlow()

    override suspend fun login(email: String, password: String): Either<AuthError.Login, User> =
        Either.Left(AuthError.InvalidCredentials)

    override suspend fun register(
        email: String,
        displayName: String,
        password: String,
    ): Either<AuthError.Register, User> = Either.Left(AuthError.EmailAlreadyInUse)

    override suspend fun verifyEmail(
        email: String,
        code: String
    ): Either<AuthError.VerifyEmail, Unit> =
        Either.Right(Unit)

    override suspend fun resendVerification(email: String): Either<DataError, Unit> =
        Either.Right(Unit)

    override suspend fun forgotPassword(email: String): Either<DataError, Unit> =
        Either.Right(Unit)

    override suspend fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
    ): Either<AuthError.ResetPassword, Unit> = Either.Right(Unit)

    override suspend fun logout(): Either<DataError, Unit> {
        logoutCallCount++
        if (logoutResult.isRight()) {
            authStateFlow.value = AuthState.Unauthenticated
        }
        return logoutResult
    }
}

internal class FakeChatRoomRepository(
    initialRooms: List<ChatRoom> = emptyList(),
) : ChatRoomRepository {

    val roomsFlow = MutableStateFlow(initialRooms)

    var findParticipantHandler: suspend (String) -> Either<ChatError.FindParticipant, ChatParticipant> =
        {
            Either.Left(ChatError.ParticipantNotFound)
        }

    var createDirectRoomHandler: suspend (String, String) -> Either<ChatError.CreateRoom, ChatRoom> =
        { targetUserId, _ ->
            Either.Right(
                ChatRoom(
                    id = "room-$targetUserId",
                    type = ChatRoomType.DIRECT,
                    creator = ChatParticipant("user-me", "alice@example.com", "Alice"),
                    participants = listOf(
                        ChatParticipant("user-me", "alice@example.com", "Alice"),
                        ChatParticipant(targetUserId, "bob@example.com", "Bob"),
                    ),
                ),
            )
        }

    override fun getChats(): Flow<List<ChatRoom>> = roomsFlow.asStateFlow()

    override suspend fun syncChats(): Either<DataError, Unit> = Either.Right(Unit)

    override suspend fun syncChatsAndGetChangedRooms(): Either<DataError, List<String>> =
        Either.Right(emptyList())

    override suspend fun findChatParticipantByEmail(
        email: String,
    ): Either<ChatError.FindParticipant, ChatParticipant> = findParticipantHandler(email)

    override suspend fun createDirectChatRoom(
        targetUserId: String,
        message: String,
    ): Either<ChatError.CreateRoom, ChatRoom> = createDirectRoomHandler(targetUserId, message)

    override suspend fun createGroupChatRoom(
        participantIds: List<String>,
        name: String?,
    ): Either<ChatError.CreateRoom, ChatRoom> = Either.Left(ChatError.InvalidRoomSize)

    override fun getChatRoom(roomId: String): Flow<ChatRoom?> =
        roomsFlow.map { rooms -> rooms.firstOrNull { it.id == roomId } }

    override suspend fun syncChatRoom(roomId: String): Either<ChatError.SyncRoom, Unit> =
        Either.Right(Unit)

    override suspend fun addParticipantsToGroup(
        roomId: String,
        userIds: List<String>,
    ): Either<ChatError.AddParticipants, ChatRoom> = Either.Left(ChatError.RoomNotFound)

    override suspend fun leaveChatRoom(roomId: String): Either<ChatError.LeaveRoom, Unit> =
        Either.Right(Unit)
}

internal class FakeChatMessageRepository(
    initialMessages: List<ChatMessage> = emptyList(),
) : ChatMessageRepository {

    val messagesFlow = MutableStateFlow(initialMessages)
    val sentMessages = mutableListOf<Pair<String, String>>()

    var sendMessageHandler: suspend (String, String) -> Either<DataError, Unit> =
        { roomId, content ->
            val nextId = "msg-${messagesFlow.value.size + 1}"
            messagesFlow.value = listOf(
                ChatMessage(
                    id = nextId,
                    roomId = roomId,
                    senderId = "user-me",
                    content = content,
                    timestamp = Instant.parse("2026-11-16T12:00:00Z"),
                    deliveryStatus = MessageDeliveryStatus.SENT,
                ),
            ) + messagesFlow.value
            Either.Right(Unit)
        }

    override fun getMessages(roomId: String, limit: Int): Flow<List<ChatMessage>> =
        messagesFlow.map { msgs -> msgs.filter { it.roomId == roomId }.take(limit) }

    override suspend fun syncRecentMessages(roomId: String): Either<DataError, Boolean> =
        Either.Right(false)

    override suspend fun loadOlderMessages(roomId: String): Either<DataError, Boolean> =
        Either.Right(false)

    override suspend fun sendMessage(roomId: String, content: String): Either<DataError, Unit> {
        sentMessages.add(roomId to content)
        return sendMessageHandler(roomId, content)
    }

    override suspend fun retryMessage(messageId: String): Either<DataError, Unit> =
        Either.Right(Unit)

    override suspend fun deleteMessage(messageId: String): Either<ChatError.DeleteMessage, Unit> =
        Either.Right(Unit)
}
