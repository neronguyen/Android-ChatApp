package io.github.neronguyen.chat.core.data.mapper

import io.github.neronguyen.chat.core.model.error.ChatError
import io.github.neronguyen.chat.core.model.error.DataError
import io.github.neronguyen.chat.core.network.util.NetworkError
import io.github.neronguyenvn.nerochat.contract.chat.error.ChatServerError

internal fun ChatServerError.GetMessages.toDataError(): DataError = when (this) {
    ChatServerError.ParticipantNotInRoom -> DataError.Remote.Forbidden
}

internal fun ChatServerError.GetParticipant.toDomain(): ChatError.FindParticipant = when (this) {
    ChatServerError.ChatParticipantNotFound -> ChatError.ParticipantNotFound
}

internal fun ChatServerError.GetRoom.toDomain(): ChatError.SyncRoom = when (this) {
    ChatServerError.ChatRoomNotFound -> ChatError.RoomNotFound
}

internal fun ChatServerError.CreateRoom.toDomain(): ChatError.CreateRoom = when (this) {
    ChatServerError.ChatParticipantNotFound -> ChatError.ParticipantNotFound
    ChatServerError.InvalidChatRoomSize -> ChatError.InvalidRoomSize
}

internal fun ChatServerError.AddParticipants.toDomain(): ChatError.AddParticipants = when (this) {
    ChatServerError.ChatParticipantNotFound -> ChatError.ParticipantNotFound
    ChatServerError.ChatRoomNotFound -> ChatError.RoomNotFound
    ChatServerError.ParticipantAlreadyInRoom -> ChatError.AlreadyInRoom
    ChatServerError.ParticipantNotInRoom -> ChatError.ParticipantNotInRoom
}

internal fun ChatServerError.LeaveRoom.toDomain(): ChatError.LeaveRoom = when (this) {
    ChatServerError.ChatParticipantNotFound -> ChatError.ParticipantNotFound
    ChatServerError.ChatRoomNotFound -> ChatError.RoomNotFound
}

internal fun ChatServerError.DeleteMessage.toDomain(): ChatError.DeleteMessage = when (this) {
    ChatServerError.MessageNotFound -> ChatError.MessageNotFound
    ChatServerError.NotMessageSender -> ChatError.NotMessageSender
}

@JvmName("toGetMessagesDataError")
internal fun NetworkError<ChatServerError.GetMessages>.toDataError(): DataError =
    toDataError(ChatServerError.GetMessages::toDataError)

internal fun NetworkError<ChatServerError.GetParticipant>.toFindParticipantError(): ChatError.FindParticipant =
    toDomainError(ChatError::Data, ChatServerError.GetParticipant::toDomain)

internal fun NetworkError<ChatServerError.GetRoom>.toSyncRoomError(): ChatError.SyncRoom =
    toDomainError(ChatError::Data, ChatServerError.GetRoom::toDomain)

internal fun NetworkError<ChatServerError.CreateRoom>.toCreateRoomError(): ChatError.CreateRoom =
    toDomainError(ChatError::Data, ChatServerError.CreateRoom::toDomain)

internal fun NetworkError<ChatServerError.AddParticipants>.toAddParticipantsError(): ChatError.AddParticipants =
    toDomainError(ChatError::Data, ChatServerError.AddParticipants::toDomain)

internal fun NetworkError<ChatServerError.LeaveRoom>.toLeaveRoomError(): ChatError.LeaveRoom =
    toDomainError(ChatError::Data, ChatServerError.LeaveRoom::toDomain)

internal fun NetworkError<ChatServerError.DeleteMessage>.toDeleteMessageError(): ChatError.DeleteMessage =
    toDomainError(ChatError::Data, ChatServerError.DeleteMessage::toDomain)
