package io.github.neronguyen.chat.core.network

import arrow.core.Either
import io.github.neronguyen.chat.core.network.util.NetworkError
import io.github.neronguyenvn.nerochat.contract.chat.ChatMessageSliceDto
import io.github.neronguyenvn.nerochat.contract.chat.ChatParticipantDto
import io.github.neronguyenvn.nerochat.contract.chat.ChatRoomDto
import io.github.neronguyenvn.nerochat.contract.chat.error.ChatServerError
import kotlin.time.Instant

interface ChatNetworkDataSource {
    suspend fun getChatRoomsForUser(): Either<NetworkError<Nothing>, List<ChatRoomDto>>

    suspend fun getChatRoom(roomId: String): Either<NetworkError<ChatServerError.GetRoom>, ChatRoomDto>

    suspend fun getMessagesForChatRoom(
        roomId: String,
        before: Instant? = null
    ): Either<NetworkError<ChatServerError.GetMessages>, ChatMessageSliceDto>

    suspend fun getParticipantByEmail(email: String): Either<NetworkError<ChatServerError.GetParticipant>, ChatParticipantDto>

    suspend fun createDirectChatRoom(
        targetUserId: String,
        message: String
    ): Either<NetworkError<ChatServerError.CreateRoom>, ChatRoomDto>

    suspend fun createGroupChatRoom(
        participantIds: List<String>,
        name: String?
    ): Either<NetworkError<ChatServerError.CreateRoom>, ChatRoomDto>

    suspend fun addParticipantsToGroup(
        roomId: String,
        userIds: List<String>
    ): Either<NetworkError<ChatServerError.AddParticipants>, ChatRoomDto>

    suspend fun leaveChatRoom(roomId: String): Either<NetworkError<ChatServerError.LeaveRoom>, Unit>

    suspend fun deleteMessage(messageId: String): Either<NetworkError<ChatServerError.DeleteMessage>, Unit>
}
