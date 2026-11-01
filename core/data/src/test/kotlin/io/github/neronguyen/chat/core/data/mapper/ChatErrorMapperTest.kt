package io.github.neronguyen.chat.core.data.mapper

import io.github.neronguyen.chat.core.model.error.ChatError
import io.github.neronguyen.chat.core.model.error.DataError
import io.github.neronguyen.chat.core.network.util.NetworkError
import io.github.neronguyenvn.nerochat.contract.chat.error.ChatServerError
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatErrorMapperTest {

    @Test
    fun `pure ChatServerError toDomain and toDataError map accurately`() {
        assertEquals(DataError.Remote.Forbidden, (ChatServerError.ParticipantNotInRoom as ChatServerError.GetMessages).toDataError())
        assertEquals(ChatError.ParticipantNotFound, (ChatServerError.ChatParticipantNotFound as ChatServerError.GetParticipant).toDomain())
        assertEquals(ChatError.ParticipantNotFound, (ChatServerError.ChatParticipantNotFound as ChatServerError.CreateRoom).toDomain())
        assertEquals(ChatError.ParticipantNotFound, (ChatServerError.ChatParticipantNotFound as ChatServerError.AddParticipants).toDomain())
        assertEquals(ChatError.ParticipantNotFound, (ChatServerError.ChatParticipantNotFound as ChatServerError.LeaveRoom).toDomain())
        assertEquals(ChatError.RoomNotFound, (ChatServerError.ChatRoomNotFound as ChatServerError.GetRoom).toDomain())
        assertEquals(ChatError.RoomNotFound, (ChatServerError.ChatRoomNotFound as ChatServerError.AddParticipants).toDomain())
        assertEquals(ChatError.RoomNotFound, (ChatServerError.ChatRoomNotFound as ChatServerError.LeaveRoom).toDomain())
        assertEquals(ChatError.InvalidRoomSize, ChatServerError.InvalidChatRoomSize.toDomain())
        assertEquals(ChatError.AlreadyInRoom, ChatServerError.ParticipantAlreadyInRoom.toDomain())
        assertEquals(ChatError.ParticipantNotInRoom, (ChatServerError.ParticipantNotInRoom as ChatServerError.AddParticipants).toDomain())
        assertEquals(ChatError.MessageNotFound, ChatServerError.MessageNotFound.toDomain())
        assertEquals(ChatError.NotMessageSender, ChatServerError.NotMessageSender.toDomain())
    }

    @Test
    fun `Chat getMessages toDataError maps ParticipantNotInRoom to Forbidden`() {
        val participantNotInRoom: NetworkError<ChatServerError.GetMessages> = NetworkError.Business(
            error = ChatServerError.ParticipantNotInRoom,
            message = "Not in room"
        )
        assertEquals(DataError.Remote.Forbidden, participantNotInRoom.toDataError())
    }

    @Test
    fun `Chat getParticipant toFindParticipantError maps domain errors`() {
        val notFound: NetworkError<ChatServerError.GetParticipant> = NetworkError.Business(
            error = ChatServerError.ChatParticipantNotFound,
            message = "Not found"
        )
        assertEquals(ChatError.ParticipantNotFound, notFound.toFindParticipantError())
    }

    @Test
    fun `Chat getRoom toSyncRoomError maps domain errors`() {
        val notFound: NetworkError<ChatServerError.GetRoom> = NetworkError.Business(
            error = ChatServerError.ChatRoomNotFound,
            message = "Not found"
        )
        assertEquals(ChatError.RoomNotFound, notFound.toSyncRoomError())
    }

    @Test
    fun `Chat createRoom toCreateRoomError maps domain errors`() {
        val invalidSize: NetworkError<ChatServerError.CreateRoom> = NetworkError.Business(
            error = ChatServerError.InvalidChatRoomSize,
            message = "Invalid size"
        )
        assertEquals(ChatError.InvalidRoomSize, invalidSize.toCreateRoomError())
    }

    @Test
    fun `Chat addParticipants toAddParticipantsError maps domain errors`() {
        val notInRoom: NetworkError<ChatServerError.AddParticipants> = NetworkError.Business(
            error = ChatServerError.ParticipantNotInRoom,
            message = "Not in room"
        )
        assertEquals(ChatError.ParticipantNotInRoom, notInRoom.toAddParticipantsError())

        val alreadyInRoom: NetworkError<ChatServerError.AddParticipants> = NetworkError.Business(
            error = ChatServerError.ParticipantAlreadyInRoom,
            message = "Already in room"
        )
        assertEquals(ChatError.AlreadyInRoom, alreadyInRoom.toAddParticipantsError())
    }

    @Test
    fun `Chat leaveRoom toLeaveRoomError maps domain errors`() {
        val notFound: NetworkError<ChatServerError.LeaveRoom> = NetworkError.Business(
            error = ChatServerError.ChatRoomNotFound,
            message = "Not found"
        )
        assertEquals(ChatError.RoomNotFound, notFound.toLeaveRoomError())
    }

    @Test
    fun `Chat deleteMessage toDeleteMessageError maps MessageNotFound and NotMessageSender`() {
        val notFound: NetworkError<ChatServerError.DeleteMessage> = NetworkError.Business(
            error = ChatServerError.MessageNotFound,
            message = "Not found"
        )
        assertEquals(ChatError.MessageNotFound, notFound.toDeleteMessageError())

        val notSender: NetworkError<ChatServerError.DeleteMessage> = NetworkError.Business(
            error = ChatServerError.NotMessageSender,
            message = "Not sender"
        )
        assertEquals(ChatError.NotMessageSender, notSender.toDeleteMessageError())
    }
}
