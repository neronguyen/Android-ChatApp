package io.github.neronguyen.chat.core.data.repository

import arrow.core.Either
import io.github.neronguyen.chat.core.database.dao.ChatDao
import io.github.neronguyen.chat.core.database.entity.ChatMessageEntity
import io.github.neronguyen.chat.core.database.entity.ChatParticipantEntity
import io.github.neronguyen.chat.core.database.entity.ChatRoomEntity
import io.github.neronguyen.chat.core.database.entity.ChatRoomParticipantCrossRef
import io.github.neronguyen.chat.core.database.model.ChatRoomWithDetails
import io.github.neronguyen.chat.core.model.MessageDeliveryStatus
import io.github.neronguyen.chat.core.network.ChatNetworkDataSource
import io.github.neronguyen.chat.core.network.util.NetworkError
import io.github.neronguyenvn.nerochat.contract.chat.ChatMessageSliceDto
import io.github.neronguyenvn.nerochat.contract.chat.ChatParticipantDto
import io.github.neronguyenvn.nerochat.contract.chat.ChatRoomDto
import io.github.neronguyenvn.nerochat.contract.chat.error.ChatServerError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import io.github.neronguyenvn.nerochat.contract.chat.ChatRoomType as ContractChatRoomType

@OptIn(ExperimentalCoroutinesApi::class)
class ChatRoomRepositoryImplTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private class FakeChatDao : ChatDao {
        val insertedMessages = mutableListOf<ChatMessageEntity>()
        val updatedStatuses = mutableMapOf<String, MessageDeliveryStatus>()
        var lastUpdatedRoomId: String? = null
        var lastUpdatedRoomMessageId: String? = null
        var lastUpdatedRoomTimestamp: Long? = null

        val chatWithDetailsFlow = MutableStateFlow<ChatRoomWithDetails?>(null)
        var deleteRoomCalledWith: String? = null

        override fun getChatsWithDetails(): Flow<List<ChatRoomWithDetails>> = emptyFlow()
        override fun getChatWithDetails(roomId: String): Flow<ChatRoomWithDetails?> =
            chatWithDetailsFlow
        override fun getRecentMessages(roomId: String, limit: Int): Flow<List<ChatMessageEntity>> =
            emptyFlow()
        override suspend fun getMessageById(id: String): ChatMessageEntity? = null
        override suspend fun getOldestSentMessageTimestamp(roomId: String): Long? = null
        override suspend fun getNewestSentMessageTimestamp(roomId: String): Long? = null
        override suspend fun clearSentMessagesForRoom(roomId: String) {}
        override suspend fun syncRecentMessagesTransaction(
            roomId: String,
            remoteMessages: List<ChatMessageEntity>
        ) {
        }
        override suspend fun getCrossRefsForRooms(roomIds: List<String>): List<ChatRoomParticipantCrossRef> =
            emptyList()

        override suspend fun deleteCrossRefs(crossRefs: List<ChatRoomParticipantCrossRef>) {}
        override suspend fun deleteRoom(roomId: String) {
            deleteRoomCalledWith = roomId
        }

        override suspend fun deleteCrossRefsForRoom(roomId: String) {}
        override suspend fun deleteMessagesForRoom(roomId: String) {}
        override suspend fun clearRooms() {}
        override suspend fun clearMessages() {}
        override suspend fun clearParticipants() {}
        override suspend fun clearCrossRefs() {}


        override suspend fun upsertRoom(room: ChatRoomEntity) {}
        override suspend fun upsertRooms(rooms: List<ChatRoomEntity>) {}
        override suspend fun upsertParticipant(participant: ChatParticipantEntity) {}
        override suspend fun upsertParticipants(participants: List<ChatParticipantEntity>) {}
        override suspend fun upsertCrossRefs(crossRefs: List<ChatRoomParticipantCrossRef>) {}
        override suspend fun upsertMessage(message: ChatMessageEntity) {
            insertedMessages.add(message)
        }
        override suspend fun upsertMessages(messages: List<ChatMessageEntity>) {}

        override suspend fun updateDeliveryStatus(messageId: String, status: MessageDeliveryStatus) {
            updatedStatuses[messageId] = status
        }

        override suspend fun markSendingMessagesAsFailed(
            sendingStatus: MessageDeliveryStatus,
            failedStatus: MessageDeliveryStatus
        ) {
        }

        override suspend fun insertSendingMessage(message: ChatMessageEntity) {
            insertedMessages.add(message)
            lastUpdatedRoomId = message.roomId
            lastUpdatedRoomMessageId = message.id
            lastUpdatedRoomTimestamp = message.timestamp
        }

        override suspend fun upsertIncomingOrAckedMessage(message: ChatMessageEntity) {
            insertedMessages.add(message)
            lastUpdatedRoomId = message.roomId
            lastUpdatedRoomMessageId = message.id
            lastUpdatedRoomTimestamp = message.timestamp
        }

        override suspend fun deleteMessage(messageId: String) {}

        override suspend fun deleteParticipantCrossRef(roomId: String, userId: String) {}

        override suspend fun handleParticipantJoined(
            roomId: String,
            newUsers: List<ChatParticipantEntity>,
            addedBy: ChatParticipantEntity?
        ) {
        }

        override suspend fun handleParticipantLeft(
            roomId: String,
            leftUser: ChatParticipantEntity,
            kickedBy: ChatParticipantEntity?
        ) {
        }

        override suspend fun getNewestMessageForRoom(roomId: String): ChatMessageEntity? = null

        override suspend fun updateRoomLastActivity(
            roomId: String,
            messageId: String?,
            timestamp: Long?
        ) {
            lastUpdatedRoomId = roomId
            lastUpdatedRoomMessageId = messageId
            lastUpdatedRoomTimestamp = timestamp
        }
    }

    private class FakeChatNetworkDataSource : ChatNetworkDataSource {
        var lastDirectTargetUserId: String? = null
        var lastDirectMessage: String? = null
        var lastGroupParticipantIds: List<String>? = null
        var lastGroupName: String? = null
        var directChatResponse: Either<NetworkError<ChatServerError.CreateRoom>, ChatRoomDto> =
            Either.Left(NetworkError.Transport.NoInternet)
        var groupChatResponse: Either<NetworkError<ChatServerError.CreateRoom>, ChatRoomDto> =
            Either.Left(NetworkError.Transport.NoInternet)
        var participantResponse: Either<NetworkError<ChatServerError.GetParticipant>, ChatParticipantDto> =
            Either.Left(NetworkError.Transport.NoInternet)

        override suspend fun getChatRoomsForUser(): Either<NetworkError<Nothing>, List<ChatRoomDto>> =
            Either.Right(emptyList())

        override suspend fun getChatRoom(roomId: String): Either<NetworkError<ChatServerError.GetRoom>, ChatRoomDto> =
            Either.Left(NetworkError.Transport.NoInternet)

        override suspend fun getMessagesForChatRoom(
            roomId: String,
            before: Instant?
        ): Either<NetworkError<ChatServerError.GetMessages>, ChatMessageSliceDto> =
            Either.Right(ChatMessageSliceDto(messages = emptyList(), hasMore = false))

        override suspend fun getParticipantByEmail(email: String): Either<NetworkError<ChatServerError.GetParticipant>, ChatParticipantDto> =
            participantResponse

        override suspend fun createDirectChatRoom(
            targetUserId: String,
            message: String
        ): Either<NetworkError<ChatServerError.CreateRoom>, ChatRoomDto> {
            lastDirectTargetUserId = targetUserId
            lastDirectMessage = message
            return directChatResponse
        }

        override suspend fun createGroupChatRoom(
            participantIds: List<String>,
            name: String?
        ): Either<NetworkError<ChatServerError.CreateRoom>, ChatRoomDto> {
            lastGroupParticipantIds = participantIds
            lastGroupName = name
            return groupChatResponse
        }

        override suspend fun addParticipantsToGroup(
            roomId: String,
            userIds: List<String>
        ): Either<NetworkError<ChatServerError.AddParticipants>, ChatRoomDto> =
            Either.Left(NetworkError.Transport.NoInternet)

        override suspend fun leaveChatRoom(roomId: String): Either<NetworkError<ChatServerError.LeaveRoom>, Unit> =
            Either.Right(Unit)

        override suspend fun deleteMessage(messageId: String): Either<NetworkError<ChatServerError.DeleteMessage>, Unit> =
            Either.Right(Unit)
    }

    @Test
    fun `findChatParticipantByEmail delegates to networkDataSource`() = runTest {
        val fakeDao = FakeChatDao()
        val fakeNetwork = FakeChatNetworkDataSource().apply {
            participantResponse = Either.Right(
                ChatParticipantDto("user-abc", "abc@example.com", "ABC User")
            )
        }

        val repository = ChatRoomRepositoryImpl(
            chatDao = fakeDao,
            networkDataSource = fakeNetwork,
            defaultDispatcher = testDispatcher
        )

        val result = repository.findChatParticipantByEmail("abc@example.com")

        assertTrue(result.isRight())
        val participant = (result as Either.Right).value
        assertEquals("user-abc", participant.userId)
        assertEquals("abc@example.com", participant.email)
        assertEquals("ABC User", participant.displayName)
    }

    @Test
    fun `createDirectChatRoom passes targetUserId directly to networkDataSource without validation`() = runTest {
        val fakeDao = FakeChatDao()
        val targetId = "target-user-id"
        val fakeNetwork = FakeChatNetworkDataSource().apply {
            directChatResponse = Either.Right(
                ChatRoomDto(
                    id = "room1",
                    type = ContractChatRoomType.DIRECT,
                    name = null,
                    creator = ChatParticipantDto("user123", "me@example.com", "Me"),
                    participants = listOf(
                        ChatParticipantDto("user123", "me@example.com", "Me"),
                        ChatParticipantDto(targetId, "target@example.com", "Target")
                    ),
                    lastMessage = null,
                    lastActivityAt = Instant.parse("2026-10-10T12:00:00Z")
                )
            )
        }

        val repository = ChatRoomRepositoryImpl(
            chatDao = fakeDao,
            networkDataSource = fakeNetwork,
            defaultDispatcher = testDispatcher
        )

        val result = repository.createDirectChatRoom(targetId, "hello")

        assertTrue(result.isRight())
        assertEquals(targetId, fakeNetwork.lastDirectTargetUserId)
        assertEquals("hello", fakeNetwork.lastDirectMessage)
    }

    @Test
    fun `createGroupChatRoom passes participantIds directly to networkDataSource without validation`() = runTest {
        val fakeDao = FakeChatDao()
        val participantIds = listOf("user-1", "user-2")
        val fakeNetwork = FakeChatNetworkDataSource().apply {
            groupChatResponse = Either.Right(
                ChatRoomDto(
                    id = "group-room-1",
                    type = ContractChatRoomType.GROUP,
                    name = "Group Test",
                    creator = ChatParticipantDto("user123", "me@example.com", "Me"),
                    participants = listOf(
                        ChatParticipantDto("user123", "me@example.com", "Me"),
                        ChatParticipantDto("user-1", "u1@example.com", "U1"),
                        ChatParticipantDto("user-2", "u2@example.com", "U2")
                    ),
                    lastMessage = null,
                    lastActivityAt = Instant.parse("2026-10-10T12:00:00Z")
                )
            )
        }

        val repository = ChatRoomRepositoryImpl(
            chatDao = fakeDao,
            networkDataSource = fakeNetwork,
            defaultDispatcher = testDispatcher
        )

        val result = repository.createGroupChatRoom(participantIds, "  Group Test  ")

        assertTrue(result.isRight())
        assertEquals(participantIds, fakeNetwork.lastGroupParticipantIds)
        assertEquals("Group Test", fakeNetwork.lastGroupName)
    }

    @Test
    fun `getChatRoom observes dao and maps to domain`() = runTest {
        val fakeDao = FakeChatDao()
        val fakeNetwork = FakeChatNetworkDataSource()
        val repository = ChatRoomRepositoryImpl(fakeDao, fakeNetwork, testDispatcher)

        val sampleEntity = ChatRoomEntity(
            id = "room-1",
            type = io.github.neronguyen.chat.core.model.ChatRoomType.DIRECT,
            name = null,
            creatorId = "creator-1",
            lastMessageId = null,
            lastActivityAt = null
        )
        val creatorEntity = ChatParticipantEntity(
            userId = "creator-1",
            email = "creator@example.com",
            displayName = "Creator",
            profilePictureUrl = null
        )
        fakeDao.chatWithDetailsFlow.value = ChatRoomWithDetails(
            room = sampleEntity,
            creator = creatorEntity,
            participants = listOf(creatorEntity),
            lastMessage = null
        )

        val room = repository.getChatRoom("room-1").firstOrNull()
        assertEquals("room-1", room?.id)
        assertEquals("Creator", room?.creator?.displayName)
    }

    @Test
    fun `leaveChatRoom calls network and removes room and data from dao`() = runTest {
        val fakeDao = FakeChatDao()
        val fakeNetwork = FakeChatNetworkDataSource()
        val repository = ChatRoomRepositoryImpl(fakeDao, fakeNetwork, testDispatcher)

        val result = repository.leaveChatRoom("room-1")
        assertTrue(result.isRight())
        assertEquals("room-1", fakeDao.deleteRoomCalledWith)
    }
}

