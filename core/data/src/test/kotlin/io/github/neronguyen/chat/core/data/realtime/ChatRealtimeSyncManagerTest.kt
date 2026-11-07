package io.github.neronguyen.chat.core.data.realtime

import arrow.core.Either
import io.github.neronguyen.chat.core.data.repository.ChatRoomRepository
import io.github.neronguyen.chat.core.database.dao.ChatDao
import io.github.neronguyen.chat.core.database.entity.ChatMessageEntity
import io.github.neronguyen.chat.core.database.entity.ChatParticipantEntity
import io.github.neronguyen.chat.core.database.entity.ChatRoomEntity
import io.github.neronguyen.chat.core.database.entity.ChatRoomParticipantCrossRef
import io.github.neronguyen.chat.core.database.model.ChatRoomWithDetails
import io.github.neronguyen.chat.core.model.ChatParticipant
import io.github.neronguyen.chat.core.model.ChatRoom
import io.github.neronguyen.chat.core.model.MessageDeliveryStatus
import io.github.neronguyen.chat.core.model.error.ChatError
import io.github.neronguyen.chat.core.model.error.DataError
import io.github.neronguyen.chat.core.network.ChatRealtimeDataSource
import io.github.neronguyen.chat.core.network.model.websocket.SocketConnectionState
import io.github.neronguyenvn.nerochat.contract.chat.ChatMessageDto
import io.github.neronguyenvn.nerochat.contract.chat.ChatParticipantDto
import io.github.neronguyenvn.nerochat.contract.chat.WsClientMessage
import io.github.neronguyenvn.nerochat.contract.chat.WsServerEvent
import io.github.neronguyenvn.nerochat.contract.chat.error.ChatServerError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ChatRealtimeSyncManagerTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeChatRealtimeDataSource : ChatRealtimeDataSource {
        val incomingEventsFlow = MutableSharedFlow<WsServerEvent>()
        val connectionStateFlow =
            MutableStateFlow<SocketConnectionState>(SocketConnectionState.Disconnected)

        override val incomingEvents: Flow<WsServerEvent> = incomingEventsFlow
        override val connectionState: StateFlow<SocketConnectionState> = connectionStateFlow

        override suspend fun connect() {}
        override suspend fun disconnect() {}

        override suspend fun sendMessage(
            message: WsClientMessage
        ): Boolean {
            return true
        }
    }

    private class FakeChatDao : ChatDao {
        val existingRooms = mutableSetOf<String>("room-1")
        val upsertedMessages = mutableListOf<ChatMessageEntity>()
        val upsertedParticipants = mutableListOf<ChatParticipantEntity>()
        val upsertedCrossRefs = mutableListOf<ChatRoomParticipantCrossRef>()
        val deletedMessages = mutableListOf<String>()
        val deletedCrossRefs = mutableListOf<Pair<String, String>>()
        val updatedStatuses = mutableMapOf<String, MessageDeliveryStatus>()
        var markSendingFailedCount = 0
        var upsertedIncomingOrAckedMessage: ChatMessageEntity? = null

        override fun getChatsWithDetails(): Flow<List<ChatRoomWithDetails>> = emptyFlow()
        override fun getChatWithDetails(roomId: String): Flow<ChatRoomWithDetails?> =
            emptyFlow()

        override suspend fun roomExists(roomId: String): Boolean = roomId in existingRooms

        override fun getRecentMessages(
            roomId: String,
            limit: Int
        ): Flow<List<ChatMessageEntity>> = emptyFlow()

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
        override suspend fun deleteRoom(roomId: String) {}
        override suspend fun deleteCrossRefsForRoom(roomId: String) {}
        override suspend fun deleteMessagesForRoom(roomId: String) {}
        override suspend fun clearRooms() {}
        override suspend fun clearMessages() {}
        override suspend fun clearParticipants() {}
        override suspend fun clearCrossRefs() {}

        override suspend fun upsertRoom(room: ChatRoomEntity) {
            existingRooms.add(room.id)
        }
        override suspend fun upsertRooms(rooms: List<ChatRoomEntity>) {
            existingRooms.addAll(rooms.map { it.id })
        }
        override suspend fun upsertParticipant(participant: ChatParticipantEntity) {
            upsertedParticipants.add(participant)
        }

        override suspend fun upsertParticipants(participants: List<ChatParticipantEntity>) {
            upsertedParticipants.addAll(participants)
        }

        override suspend fun upsertCrossRefs(crossRefs: List<ChatRoomParticipantCrossRef>) {
            upsertedCrossRefs.addAll(crossRefs)
        }

        override suspend fun upsertMessage(message: ChatMessageEntity) {
            upsertedMessages.add(message)
        }

        override suspend fun upsertMessages(messages: List<ChatMessageEntity>) {
            upsertedMessages.addAll(messages)
        }

        override suspend fun updateDeliveryStatus(
            messageId: String,
            status: MessageDeliveryStatus
        ) {
            updatedStatuses[messageId] = status
        }

        override suspend fun markSendingMessagesAsFailed(
            sendingStatus: MessageDeliveryStatus,
            failedStatus: MessageDeliveryStatus
        ) {
            markSendingFailedCount++
        }

        override suspend fun upsertIncomingOrAckedMessage(message: ChatMessageEntity) {
            upsertedIncomingOrAckedMessage = message
            upsertedMessages.add(message)
        }

        override suspend fun deleteMessage(messageId: String) {
            deletedMessages.add(messageId)
        }

        override suspend fun deleteParticipantCrossRef(roomId: String, userId: String) {
            deletedCrossRefs.add(roomId to userId)
        }

        override suspend fun handleParticipantJoined(
            roomId: String,
            newUsers: List<ChatParticipantEntity>,
            addedBy: ChatParticipantEntity?
        ) {
            addedBy?.let { upsertedParticipants.add(it) }
            if (newUsers.isNotEmpty()) {
                upsertedParticipants.addAll(newUsers)
                val crossRefs = newUsers.map { participant ->
                    ChatRoomParticipantCrossRef(roomId = roomId, userId = participant.userId)
                }
                upsertedCrossRefs.addAll(crossRefs)
            }
        }

        override suspend fun handleParticipantLeft(
            roomId: String,
            leftUser: ChatParticipantEntity,
            kickedBy: ChatParticipantEntity?
        ) {
            kickedBy?.let { upsertedParticipants.add(it) }
            deletedCrossRefs.add(roomId to leftUser.userId)
        }

        override suspend fun getNewestMessageForRoom(roomId: String): ChatMessageEntity? = null

        override suspend fun updateRoomLastActivity(
            roomId: String,
            messageId: String?,
            timestamp: Long?
        ) {
        }
    }

    private class FakeChatRoomRepository : ChatRoomRepository {
        val syncedRooms = mutableListOf<String>()
        var onSyncRoom: suspend (String) -> Either<ChatError.SyncRoom, Unit> = { Either.Right(Unit) }

        override fun getChats(): Flow<List<ChatRoom>> = emptyFlow()
        override suspend fun syncChats(): Either<DataError, Unit> = Either.Right(Unit)
        override suspend fun findChatParticipantByEmail(email: String): Either<ChatError.FindParticipant, ChatParticipant> =
            Either.Left(ChatError.ParticipantNotFound)
        override suspend fun createDirectChatRoom(targetUserId: String, message: String): Either<ChatError.CreateRoom, ChatRoom> =
            Either.Left(ChatError.ParticipantNotFound)
        override suspend fun createGroupChatRoom(participantIds: List<String>, name: String?): Either<ChatError.CreateRoom, ChatRoom> =
            Either.Left(ChatError.InvalidRoomSize)
        override fun getChatRoom(roomId: String): Flow<ChatRoom?> = emptyFlow()

        override suspend fun syncChatRoom(roomId: String): Either<ChatError.SyncRoom, Unit> {
            syncedRooms.add(roomId)
            return onSyncRoom(roomId)
        }

        override suspend fun addParticipantsToGroup(roomId: String, userIds: List<String>): Either<ChatError.AddParticipants, ChatRoom> =
            Either.Left(ChatError.AlreadyInRoom)
        override suspend fun leaveChatRoom(roomId: String): Either<ChatError.LeaveRoom, Unit> = Either.Right(Unit)
    }

    @Test
    fun `initialize handles incoming NewMessage event and upserts incoming or acked message`() =
        runTest {
            val externalScope = TestScope(testDispatcher)
            val fakeDataSource = FakeChatRealtimeDataSource()
            val fakeDao = FakeChatDao()
            val fakeRoomRepo = FakeChatRoomRepository()

            val syncManager = ChatRealtimeSyncManager(
                realtimeDataSource = fakeDataSource,
                chatDao = fakeDao,
                chatRoomRepository = fakeRoomRepo,
                appScope = externalScope,
                ioDispatcher = testDispatcher
            )

            syncManager.initialize()

            val messageDto = ChatMessageDto(
                id = "server-456",
                roomId = "room-1",
                senderId = "user-1",
                content = "Hello",
                createdAt = Instant.fromEpochMilliseconds(1000L)
            )

            fakeDataSource.incomingEventsFlow.emit(WsServerEvent.NewMessage(messageDto))

            testScheduler.advanceUntilIdle()
            assertEquals("server-456", fakeDao.upsertedIncomingOrAckedMessage?.id)
            assertEquals("room-1", fakeDao.upsertedIncomingOrAckedMessage?.roomId)
            assertEquals(1000L, fakeDao.upsertedIncomingOrAckedMessage?.timestamp)
            assertEquals(
                MessageDeliveryStatus.SENT,
                fakeDao.upsertedIncomingOrAckedMessage?.deliveryStatus
            )
        }

    @Test
    fun `incoming NewMessage for unknown room buffers message, triggers syncChatRoom, and drains queued message upon success`() =
        runTest {
            val externalScope = TestScope(testDispatcher)
            val fakeDataSource = FakeChatRealtimeDataSource()
            val fakeDao = FakeChatDao()
            fakeDao.existingRooms.clear()
            val fakeRoomRepo = FakeChatRoomRepository()
            fakeRoomRepo.onSyncRoom = { roomId ->
                fakeDao.existingRooms.add(roomId)
                Either.Right(Unit)
            }

            val syncManager = ChatRealtimeSyncManager(
                realtimeDataSource = fakeDataSource,
                chatDao = fakeDao,
                chatRoomRepository = fakeRoomRepo,
                appScope = externalScope,
                ioDispatcher = testDispatcher
            )

            syncManager.initialize()

            val messageDto = ChatMessageDto(
                id = "msg-buffered-1",
                roomId = "unknown-room-1",
                senderId = "user-1",
                content = "Hello unknown room",
                createdAt = Instant.fromEpochMilliseconds(2000L)
            )

            fakeDataSource.incomingEventsFlow.emit(WsServerEvent.NewMessage(messageDto))

            testScheduler.advanceUntilIdle()
            assertEquals(listOf("unknown-room-1"), fakeRoomRepo.syncedRooms)
            assertEquals("msg-buffered-1", fakeDao.upsertedIncomingOrAckedMessage?.id)
            assertEquals("unknown-room-1", fakeDao.upsertedIncomingOrAckedMessage?.roomId)
            assertEquals(MessageDeliveryStatus.SENT, fakeDao.upsertedIncomingOrAckedMessage?.deliveryStatus)
        }

    @Test
    fun `multiple concurrent NewMessages for unknown room single-flight fetch metadata and drain all in order`() =
        runTest {
            val externalScope = TestScope(testDispatcher)
            val fakeDataSource = FakeChatRealtimeDataSource()
            val fakeDao = FakeChatDao()
            fakeDao.existingRooms.clear()
            val fakeRoomRepo = FakeChatRoomRepository()
            fakeRoomRepo.onSyncRoom = { roomId ->
                fakeDao.existingRooms.add(roomId)
                Either.Right(Unit)
            }

            val syncManager = ChatRealtimeSyncManager(
                realtimeDataSource = fakeDataSource,
                chatDao = fakeDao,
                chatRoomRepository = fakeRoomRepo,
                appScope = externalScope,
                ioDispatcher = testDispatcher
            )

            syncManager.initialize()

            val msg1 = ChatMessageDto(
                id = "msg-burst-1",
                roomId = "burst-room",
                senderId = "user-1",
                content = "Burst 1",
                createdAt = Instant.fromEpochMilliseconds(1000L)
            )
            val msg2 = ChatMessageDto(
                id = "msg-burst-2",
                roomId = "burst-room",
                senderId = "user-2",
                content = "Burst 2",
                createdAt = Instant.fromEpochMilliseconds(2000L)
            )

            fakeDataSource.incomingEventsFlow.emit(WsServerEvent.NewMessage(msg1))
            fakeDataSource.incomingEventsFlow.emit(WsServerEvent.NewMessage(msg2))

            testScheduler.advanceUntilIdle()
            // Single-flight: syncChatRoom should be called exactly once for "burst-room"
            assertEquals(listOf("burst-room"), fakeRoomRepo.syncedRooms)
            // Both messages should be drained and upserted in arrival order
            val upsertedIds = fakeDao.upsertedMessages.map { it.id }
            assertTrue(upsertedIds.contains("msg-burst-1"))
            assertTrue(upsertedIds.contains("msg-burst-2"))
        }

    @Test
    fun `failed syncChatRoom drops buffered messages safely without throwing SQLiteConstraintException`() =
        runTest {
            val externalScope = TestScope(testDispatcher)
            val fakeDataSource = FakeChatRealtimeDataSource()
            val fakeDao = FakeChatDao()
            fakeDao.existingRooms.clear()
            val fakeRoomRepo = FakeChatRoomRepository()
            fakeRoomRepo.onSyncRoom = {
                Either.Left(ChatError.RoomNotFound)
            }

            val syncManager = ChatRealtimeSyncManager(
                realtimeDataSource = fakeDataSource,
                chatDao = fakeDao,
                chatRoomRepository = fakeRoomRepo,
                appScope = externalScope,
                ioDispatcher = testDispatcher
            )

            syncManager.initialize()

            val messageDto = ChatMessageDto(
                id = "msg-failed-room",
                roomId = "unreachable-room",
                senderId = "user-1",
                content = "Will drop safely",
                createdAt = Instant.fromEpochMilliseconds(3000L)
            )

            fakeDataSource.incomingEventsFlow.emit(WsServerEvent.NewMessage(messageDto))

            testScheduler.advanceUntilIdle()
            assertEquals(listOf("unreachable-room"), fakeRoomRepo.syncedRooms)
            // No messages upserted since room metadata fetch failed
            assertTrue(fakeDao.upsertedMessages.isEmpty())
        }

    @Test
    fun `incoming NewMessage for known room takes fast path without invoking syncChatRoom`() =
        runTest {
            val externalScope = TestScope(testDispatcher)
            val fakeDataSource = FakeChatRealtimeDataSource()
            val fakeDao = FakeChatDao()
            fakeDao.existingRooms.add("cached-room")
            val fakeRoomRepo = FakeChatRoomRepository()

            val syncManager = ChatRealtimeSyncManager(
                realtimeDataSource = fakeDataSource,
                chatDao = fakeDao,
                chatRoomRepository = fakeRoomRepo,
                appScope = externalScope,
                ioDispatcher = testDispatcher
            )

            syncManager.initialize()

            val messageDto = ChatMessageDto(
                id = "msg-fast-path",
                roomId = "cached-room",
                senderId = "user-1",
                content = "Fast path",
                createdAt = Instant.fromEpochMilliseconds(4000L)
            )

            fakeDataSource.incomingEventsFlow.emit(WsServerEvent.NewMessage(messageDto))

            testScheduler.advanceUntilIdle()
            assertTrue(fakeRoomRepo.syncedRooms.isEmpty())
            assertEquals("msg-fast-path", fakeDao.upsertedIncomingOrAckedMessage?.id)
        }

    @Test
    fun `incoming ParticipantJoined for unknown room syncs room before applying cross refs`() =
        runTest {
            val externalScope = TestScope(testDispatcher)
            val fakeDataSource = FakeChatRealtimeDataSource()
            val fakeDao = FakeChatDao()
            fakeDao.existingRooms.clear()
            val fakeRoomRepo = FakeChatRoomRepository()
            fakeRoomRepo.onSyncRoom = { roomId ->
                fakeDao.existingRooms.add(roomId)
                Either.Right(Unit)
            }

            val syncManager = ChatRealtimeSyncManager(
                realtimeDataSource = fakeDataSource,
                chatDao = fakeDao,
                chatRoomRepository = fakeRoomRepo,
                appScope = externalScope,
                ioDispatcher = testDispatcher
            )

            syncManager.initialize()

            val newParticipant = ChatParticipantDto(
                userId = "user-invitee",
                email = "invitee@test.com",
                displayName = "Invitee"
            )

            fakeDataSource.incomingEventsFlow.emit(
                WsServerEvent.ParticipantJoined(
                    roomId = "new-room-invite",
                    newUsers = listOf(newParticipant),
                    addedBy = null
                )
            )

            testScheduler.advanceUntilIdle()
            assertEquals(listOf("new-room-invite"), fakeRoomRepo.syncedRooms)
            assertEquals(1, fakeDao.upsertedCrossRefs.size)
            assertEquals("new-room-invite", fakeDao.upsertedCrossRefs[0].roomId)
            assertEquals("user-invitee", fakeDao.upsertedCrossRefs[0].userId)
        }

    @Test
    fun `initialize handles MessageDeleted event and updates message status to DELETED in DAO`() =
        runTest {
            val externalScope = TestScope(testDispatcher)
            val fakeDataSource = FakeChatRealtimeDataSource()
            val fakeDao = FakeChatDao()
            val fakeRoomRepo = FakeChatRoomRepository()

            val syncManager = ChatRealtimeSyncManager(
                realtimeDataSource = fakeDataSource,
                chatDao = fakeDao,
                chatRoomRepository = fakeRoomRepo,
                appScope = externalScope,
                ioDispatcher = testDispatcher
            )

            syncManager.initialize()

            fakeDataSource.incomingEventsFlow.emit(
                WsServerEvent.MessageDeleted(roomId = "room-1", messageId = "msg-999")
            )

            testScheduler.advanceUntilIdle()
            assertEquals(MessageDeliveryStatus.DELETED, fakeDao.updatedStatuses["msg-999"])
        }

    @Test
    fun `initialize handles ParticipantJoined event and upserts participants and cross refs`() =
        runTest {
            val externalScope = TestScope(testDispatcher)
            val fakeDataSource = FakeChatRealtimeDataSource()
            val fakeDao = FakeChatDao()
            val fakeRoomRepo = FakeChatRoomRepository()

            val syncManager = ChatRealtimeSyncManager(
                realtimeDataSource = fakeDataSource,
                chatDao = fakeDao,
                chatRoomRepository = fakeRoomRepo,
                appScope = externalScope,
                ioDispatcher = testDispatcher
            )

            syncManager.initialize()

            val newParticipant = ChatParticipantDto(
                userId = "user-2",
                email = "p2@test.com",
                displayName = "Participant 2"
            )
            val addedBy = ChatParticipantDto(
                userId = "user-1",
                email = "p1@test.com",
                displayName = "Participant 1"
            )

            fakeDataSource.incomingEventsFlow.emit(
                WsServerEvent.ParticipantJoined(
                    roomId = "room-1",
                    newUsers = listOf(newParticipant),
                    addedBy = addedBy
                )
            )

            testScheduler.advanceUntilIdle()
            assertEquals(2, fakeDao.upsertedParticipants.size)
            assertEquals(1, fakeDao.upsertedCrossRefs.size)
            assertEquals("room-1", fakeDao.upsertedCrossRefs[0].roomId)
            assertEquals("user-2", fakeDao.upsertedCrossRefs[0].userId)
        }

    @Test
    fun `initialize handles ParticipantLeft event and deletes participant cross ref`() = runTest {
        val externalScope = TestScope(testDispatcher)
        val fakeDataSource = FakeChatRealtimeDataSource()
        val fakeDao = FakeChatDao()
        val fakeRoomRepo = FakeChatRoomRepository()

        val syncManager = ChatRealtimeSyncManager(
            realtimeDataSource = fakeDataSource,
            chatDao = fakeDao,
            chatRoomRepository = fakeRoomRepo,
            appScope = externalScope,
            ioDispatcher = testDispatcher
        )

        syncManager.initialize()

        val leftUser = ChatParticipantDto(
            userId = "user-2",
            email = "p2@test.com",
            displayName = "Participant 2"
        )

        fakeDataSource.incomingEventsFlow.emit(
            WsServerEvent.ParticipantLeft(
                roomId = "room-1",
                leftUser = leftUser
            )
        )

        testScheduler.advanceUntilIdle()
        assertEquals(listOf("room-1" to "user-2"), fakeDao.deletedCrossRefs)
    }

    @Test
    fun `initialize marks sending messages as failed when connectionState becomes Failed or Disconnected`() =
        runTest {
            val externalScope = TestScope(testDispatcher)
            val fakeDataSource = FakeChatRealtimeDataSource()
            val fakeDao = FakeChatDao()
            val fakeRoomRepo = FakeChatRoomRepository()

            val syncManager = ChatRealtimeSyncManager(
                realtimeDataSource = fakeDataSource,
                chatDao = fakeDao,
                chatRoomRepository = fakeRoomRepo,
                appScope = externalScope,
                ioDispatcher = testDispatcher
            )

            syncManager.initialize()
            testScheduler.advanceUntilIdle()
            val initialCount = fakeDao.markSendingFailedCount

            fakeDataSource.connectionStateFlow.value =
                SocketConnectionState.Failed(RuntimeException("Socket dropped"))

            testScheduler.advanceUntilIdle()
            assertEquals(initialCount + 1, fakeDao.markSendingFailedCount)
        }

    @Test
    fun `WsServerEvent Error with messageId marks message delivery status as FAILED`() = runTest {
        val externalScope = TestScope(testDispatcher)
        val fakeDataSource = FakeChatRealtimeDataSource()
        val fakeDao = FakeChatDao()
        val fakeRoomRepo = FakeChatRoomRepository()

        val syncManager = ChatRealtimeSyncManager(
            realtimeDataSource = fakeDataSource,
            chatDao = fakeDao,
            chatRoomRepository = fakeRoomRepo,
            appScope = externalScope,
            ioDispatcher = testDispatcher
        )

        syncManager.initialize()
        testScheduler.advanceUntilIdle()

        fakeDataSource.incomingEventsFlow.emit(
            WsServerEvent.Error(
                error = ChatServerError.DuplicateMessageId,
                message = "Message already processed",
                messageId = "msg-123"
            )
        )

        testScheduler.advanceUntilIdle()
        assertEquals(MessageDeliveryStatus.FAILED, fakeDao.updatedStatuses["msg-123"])
    }
}
