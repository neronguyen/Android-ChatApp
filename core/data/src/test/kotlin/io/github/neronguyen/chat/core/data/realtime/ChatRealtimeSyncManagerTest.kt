package io.github.neronguyen.chat.core.data.realtime

import arrow.core.Either
import io.github.neronguyen.chat.core.database.dao.ChatDao
import io.github.neronguyen.chat.core.database.entity.ChatMessageEntity
import io.github.neronguyen.chat.core.database.entity.ChatParticipantEntity
import io.github.neronguyen.chat.core.database.entity.ChatRoomEntity
import io.github.neronguyen.chat.core.database.entity.ChatRoomParticipantCrossRef
import io.github.neronguyen.chat.core.database.model.ChatRoomWithDetails
import io.github.neronguyen.chat.core.model.MessageDeliveryStatus
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

        override suspend fun upsertRoom(room: ChatRoomEntity) {}
        override suspend fun upsertRooms(rooms: List<ChatRoomEntity>) {}
        override suspend fun upsertParticipant(participant: ChatParticipantEntity) {
            upsertedParticipants.add(participant)
        }

        override suspend fun upsertParticipants(participants: List<ChatParticipantEntity>) {
            upsertedParticipants.addAll(participants)
        }

        override suspend fun upsertCrossRefs(crossRefs: List<ChatRoomParticipantCrossRef>) {
            upsertedCrossRefs.addAll(crossRefs)
        }

        override suspend fun upsertMessage(message: ChatMessageEntity) {}

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

    @Test
    fun `initialize handles incoming NewMessage event and upserts incoming or acked message`() =
        runTest {
        val externalScope = TestScope(testDispatcher)
        val fakeDataSource = FakeChatRealtimeDataSource()
        val fakeDao = FakeChatDao()

        val syncManager = ChatRealtimeSyncManager(
            realtimeDataSource = fakeDataSource,
            chatDao = fakeDao,
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
    fun `initialize handles MessageDeleted event and updates message status to DELETED in DAO`() =
        runTest {
            val externalScope = TestScope(testDispatcher)
            val fakeDataSource = FakeChatRealtimeDataSource()
            val fakeDao = FakeChatDao()

            val syncManager = ChatRealtimeSyncManager(
                realtimeDataSource = fakeDataSource,
                chatDao = fakeDao,
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

            val syncManager = ChatRealtimeSyncManager(
                realtimeDataSource = fakeDataSource,
                chatDao = fakeDao,
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

        val syncManager = ChatRealtimeSyncManager(
            realtimeDataSource = fakeDataSource,
            chatDao = fakeDao,
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

            val syncManager = ChatRealtimeSyncManager(
                realtimeDataSource = fakeDataSource,
                chatDao = fakeDao,
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

        val syncManager = ChatRealtimeSyncManager(
            realtimeDataSource = fakeDataSource,
            chatDao = fakeDao,
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
