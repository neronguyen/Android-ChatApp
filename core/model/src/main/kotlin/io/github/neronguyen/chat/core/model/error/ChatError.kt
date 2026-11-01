package io.github.neronguyen.chat.core.model.error

sealed interface ChatError {

    sealed interface FindParticipant : ChatError
    sealed interface SyncRoom : ChatError
    sealed interface CreateRoom : ChatError
    sealed interface AddParticipants : ChatError
    sealed interface LeaveRoom : ChatError
    sealed interface DeleteMessage : ChatError

    data object ParticipantNotFound : FindParticipant, CreateRoom, AddParticipants, LeaveRoom
    data object RoomNotFound : SyncRoom, AddParticipants, LeaveRoom
    data object InvalidRoomSize : CreateRoom
    data object AlreadyInRoom : AddParticipants
    data object ParticipantNotInRoom : AddParticipants
    data object MessageNotFound : DeleteMessage
    data object NotMessageSender : DeleteMessage

    data class Data(val error: DataError) :
        FindParticipant,
        SyncRoom,
        CreateRoom,
        AddParticipants,
        LeaveRoom,
        DeleteMessage
}
