package io.github.neronguyen.chat.core.testing

import arrow.core.Either
import io.github.neronguyen.chat.core.data.repository.ChatRoomRepository
import io.github.neronguyen.chat.core.model.ChatParticipant
import io.github.neronguyen.chat.core.model.ChatRoom
import io.github.neronguyen.chat.core.model.ChatRoomType
import io.github.neronguyen.chat.core.model.error.ChatError
import io.github.neronguyen.chat.core.model.error.DataError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class FakeChatRoomRepository(
    initialRooms: List<ChatRoom> = emptyList(),
) : ChatRoomRepository {

    val roomsFlow = MutableStateFlow(initialRooms)

    var syncChatsHandler: suspend () -> Either<DataError, Unit> = { Either.Right(Unit) }

    var syncChatsAndGetChangedRoomsHandler: suspend () -> Either<DataError, List<String>> =
        { Either.Right(emptyList()) }

    var findParticipantHandler: suspend (email: String) -> Either<ChatError.FindParticipant, ChatParticipant> =
        { Either.Left(ChatError.ParticipantNotFound) }

    var createDirectRoomHandler: suspend (
        targetUserId: String,
        message: String,
    ) -> Either<ChatError.CreateRoom, ChatRoom> = { targetUserId, _ ->
        val room = TestFixtures.directRoom(
            id = "room-$targetUserId",
            peer = TestFixtures.participant(
                userId = targetUserId,
                email = "bob@example.com",
                displayName = "Bob",
            ),
        )
        roomsFlow.value = listOf(room) + roomsFlow.value.filterNot { it.id == room.id }
        Either.Right(room)
    }

    var createGroupRoomHandler: suspend (
        participantIds: List<String>,
        name: String?,
    ) -> Either<ChatError.CreateRoom, ChatRoom> = { participantIds, name ->
        val creator = TestFixtures.participant()
        val others = participantIds.mapIndexed { idx, id ->
            TestFixtures.participant(
                userId = id,
                email = "$id@example.com",
                displayName = "Member ${idx + 1}",
            )
        }
        val room = ChatRoom(
            id = "group-${roomsFlow.value.size + 1}",
            type = ChatRoomType.GROUP,
            name = name,
            creator = creator,
            participants = listOf(creator) + others,
            lastActivityAt = TestFixtures.defaultTimestamp,
        )
        roomsFlow.value = listOf(room) + roomsFlow.value
        Either.Right(room)
    }

    var syncRoomHandler: suspend (roomId: String) -> Either<ChatError.SyncRoom, Unit> =
        { Either.Right(Unit) }

    var addParticipantsHandler: suspend (
        roomId: String,
        userIds: List<String>,
    ) -> Either<ChatError.AddParticipants, ChatRoom> = { roomId, userIds ->
        val existing = roomsFlow.value.firstOrNull { it.id == roomId }
        if (existing == null) {
            Either.Left(ChatError.RoomNotFound)
        } else {
            val added = userIds
                .filter { id -> existing.participants.none { it.userId == id } }
                .map { id ->
                    TestFixtures.participant(
                        userId = id,
                        email = "$id@example.com",
                        displayName = id,
                    )
                }
            val updated = existing.copy(participants = existing.participants + added)
            roomsFlow.value = roomsFlow.value.map { if (it.id == roomId) updated else it }
            Either.Right(updated)
        }
    }

    var leaveRoomHandler: suspend (roomId: String) -> Either<ChatError.LeaveRoom, Unit> =
        { roomId ->
            roomsFlow.value = roomsFlow.value.filterNot { it.id == roomId }
            Either.Right(Unit)
        }

    var syncChatsCallCount = 0
    val findParticipantCalls = mutableListOf<String>()
    val createDirectRoomCalls = mutableListOf<Pair<String, String>>()
    val createGroupRoomCalls = mutableListOf<Pair<List<String>, String?>>()
    val syncRoomCalls = mutableListOf<String>()
    val addParticipantsCalls = mutableListOf<Pair<String, List<String>>>()
    val leaveRoomCalls = mutableListOf<String>()

    override fun getChats(): Flow<List<ChatRoom>> = roomsFlow.asStateFlow()

    override suspend fun syncChats(): Either<DataError, Unit> {
        syncChatsCallCount++
        return syncChatsHandler()
    }

    override suspend fun syncChatsAndGetChangedRooms(): Either<DataError, List<String>> =
        syncChatsAndGetChangedRoomsHandler()

    override suspend fun findChatParticipantByEmail(
        email: String,
    ): Either<ChatError.FindParticipant, ChatParticipant> {
        findParticipantCalls.add(email)
        return findParticipantHandler(email)
    }

    override suspend fun createDirectChatRoom(
        targetUserId: String,
        message: String,
    ): Either<ChatError.CreateRoom, ChatRoom> {
        createDirectRoomCalls.add(targetUserId to message)
        return createDirectRoomHandler(targetUserId, message)
    }

    override suspend fun createGroupChatRoom(
        participantIds: List<String>,
        name: String?,
    ): Either<ChatError.CreateRoom, ChatRoom> {
        createGroupRoomCalls.add(participantIds to name)
        return createGroupRoomHandler(participantIds, name)
    }

    override fun getChatRoom(roomId: String): Flow<ChatRoom?> =
        roomsFlow.map { rooms -> rooms.firstOrNull { it.id == roomId } }

    override suspend fun syncChatRoom(roomId: String): Either<ChatError.SyncRoom, Unit> {
        syncRoomCalls.add(roomId)
        return syncRoomHandler(roomId)
    }

    override suspend fun addParticipantsToGroup(
        roomId: String,
        userIds: List<String>,
    ): Either<ChatError.AddParticipants, ChatRoom> {
        addParticipantsCalls.add(roomId to userIds)
        return addParticipantsHandler(roomId, userIds)
    }

    override suspend fun leaveChatRoom(roomId: String): Either<ChatError.LeaveRoom, Unit> {
        leaveRoomCalls.add(roomId)
        return leaveRoomHandler(roomId)
    }
}
