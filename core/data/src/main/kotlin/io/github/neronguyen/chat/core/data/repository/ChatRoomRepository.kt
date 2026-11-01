package io.github.neronguyen.chat.core.data.repository

import arrow.core.Either
import io.github.neronguyen.chat.core.model.ChatParticipant
import io.github.neronguyen.chat.core.model.ChatRoom
import io.github.neronguyen.chat.core.model.error.ChatError
import io.github.neronguyen.chat.core.model.error.DataError
import kotlinx.coroutines.flow.Flow

interface ChatRoomRepository {
    fun getChats(): Flow<List<ChatRoom>>

    suspend fun syncChats(): Either<DataError, Unit>

    suspend fun findChatParticipantByEmail(email: String): Either<ChatError.FindParticipant, ChatParticipant>

    suspend fun createDirectChatRoom(
        targetUserId: String,
        message: String
    ): Either<ChatError.CreateRoom, ChatRoom>

    suspend fun createGroupChatRoom(
        participantIds: List<String>,
        name: String? = null
    ): Either<ChatError.CreateRoom, ChatRoom>

    fun getChatRoom(roomId: String): Flow<ChatRoom?>

    suspend fun syncChatRoom(roomId: String): Either<ChatError.SyncRoom, Unit>

    suspend fun addParticipantsToGroup(
        roomId: String,
        userIds: List<String>
    ): Either<ChatError.AddParticipants, ChatRoom>

    suspend fun leaveChatRoom(roomId: String): Either<ChatError.LeaveRoom, Unit>
}
