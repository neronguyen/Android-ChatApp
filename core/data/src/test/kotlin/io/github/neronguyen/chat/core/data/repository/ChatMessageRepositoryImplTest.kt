package io.github.neronguyen.chat.core.data.repository

import arrow.core.Either
import io.github.neronguyen.chat.core.database.dao.ChatDao
import io.github.neronguyen.chat.core.database.entity.ChatMessageEntity
import io.github.neronguyen.chat.core.database.entity.ChatParticipantEntity
import io.github.neronguyen.chat.core.database.entity.ChatRoomEntity
import io.github.neronguyen.chat.core.database.entity.ChatRoomParticipantCrossRef
import io.github.neronguyen.chat.core.database.model.ChatRoomWithDetails
import io.github.neronguyen.chat.core.datastore.TokenDataSource
import io.github.neronguyen.chat.core.model.AuthState
import io.github.neronguyen.chat.core.model.MessageDeliveryStatus
import io.github.neronguyen.chat.core.model.User
import io.github.neronguyen.chat.core.network.ChatNetworkDataSource
import io.github.neronguyen.chat.core.network.ChatRealtimeDataSource
import io.github.neronguyen.chat.core.network.model.websocket.SocketConnectionState
import io.github.neronguyen.chat.core.network.util.NetworkError
import io.github.neronguyenvn.nerochat.contract.chat.ChatMessageDto
import io.github.neronguyenvn.nerochat.contract.chat.ChatMessageSliceDto
import io.github.neronguyenvn.nerochat.contract.chat.ChatParticipantDto
import io.github.neronguyenvn.nerochat.contract.chat.ChatRoomDto
import io.github.neronguyenvn.nerochat.contract.chat.WsClientMessage
import io.github.neronguyenvn.nerochat.contract.chat.WsServerEvent
import io.github.neronguyenvn.nerochat.contract.chat.error.ChatServerError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ChatMessageRepositoryImplTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private class FakeChatDao : ChatDao {
        val insertedMessages = mutableListOf<ChatMessageEntity>()
        val updatedStatuses = mutableMapOf<String, MessageDeliveryStatus>()
        var oldestSentTimestampToReturn: Long? = null
        var newestSentTimestampToReturn: Long? = null
        var clearSentMessagesCalledCount = 0
        var lastUpdatedRoomId: String? = null
        var lastUpdatedRoomMessageId: String? = null
        var lastUpdatedRoomTimestamp: Long? = null

        override fun getChatsWithDetails(): Flow<List<ChatRoomWithDetails>> = emptyFlow()
        override fun getChatWithDetails(roomId: String): Flow<ChatRoomWithDetails?> = emptyFlow()
        override fun getRecentMessages(roomId: String, limit: Int): Flow<List<ChatMessageEntity>> =
            emptyFlow()

        override suspend fun getMessageById(id: String): ChatMessageEntity? =
            insertedMessages.find { it.id == id }
        override suspend fun getOldestSentMessageTimestamp(roomId: String): Long? =
            oldestSentTimestampToReturn

        override suspend fun deleteRoom(roomId: String) {}
        override suspend fun deleteCrossRefsForRoom(roomId: String) {}
        override suspend fun deleteMessagesForRoom(roomId: String) {}
        override suspend fun clearRooms() {}
        override suspend fun clearMessages() {}
        override suspend fun clearParticipants() {}
        override suspend fun clearCrossRefs() {}


        override suspend fun getNewestSentMessageTimestamp(roomId: String): Long? =
            newestSentTimestampToReturn

        override suspend fun clearSentMessagesForRoom(roomId: String) {
            clearSentMessagesCalledCount++
            insertedMessages.removeAll { it.roomId == roomId && it.deliveryStatus == MessageDeliveryStatus.SENT }
        }

        override suspend fun syncRecentMessagesTransaction(
            roomId: String,
            remoteMessages: List<ChatMessageEntity>
        ) {
            require(remoteMessages.all { it.roomId == roomId }) {
                "All remote messages must belong to roomId=$roomId"
            }

            if (remoteMessages.isEmpty()) {
                clearSentMessagesForRoom(roomId)
            } else {
                val newestLocalTimestamp = getNewestSentMessageTimestamp(roomId)
                val oldestRemoteTimestamp = remoteMessages.minOf { it.timestamp }

                val hasMessageGap =
                    newestLocalTimestamp != null &&
                            oldestRemoteTimestamp > newestLocalTimestamp

                if (hasMessageGap) {
                    clearSentMessagesForRoom(roomId)
                }

                upsertMessages(remoteMessages)
            }

            val newestMessage = getNewestMessageForRoom(roomId)

            updateRoomLastActivity(
                roomId = roomId,
                messageId = newestMessage?.id,
                timestamp = newestMessage?.timestamp
            )
        }

        override suspend fun getCrossRefsForRooms(roomIds: List<String>): List<ChatRoomParticipantCrossRef> =
            emptyList()

        override suspend fun deleteCrossRefs(crossRefs: List<ChatRoomParticipantCrossRef>) {}

        override suspend fun upsertRoom(room: ChatRoomEntity) {}
        override suspend fun upsertRooms(rooms: List<ChatRoomEntity>) {}
        override suspend fun upsertParticipant(participant: ChatParticipantEntity) {}
        override suspend fun upsertParticipants(participants: List<ChatParticipantEntity>) {}
        override suspend fun upsertCrossRefs(crossRefs: List<ChatRoomParticipantCrossRef>) {}
        override suspend fun upsertMessage(message: ChatMessageEntity) {
            insertedMessages.add(message)
        }

        override suspend fun upsertMessages(messages: List<ChatMessageEntity>) {
            insertedMessages.addAll(messages)
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

        override suspend fun getNewestMessageForRoom(roomId: String): ChatMessageEntity? {
            return insertedMessages.filter { it.roomId == roomId }.maxByOrNull { it.timestamp }
        }

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

    private class FakeChatRealtimeDataSource : ChatRealtimeDataSource {
        var shouldSucceed = true

        override val incomingEvents: Flow<WsServerEvent> = emptyFlow()
        override val connectionState: StateFlow<SocketConnectionState> =
            MutableStateFlow(SocketConnectionState.Connected)

        override suspend fun connect() {}
        override suspend fun disconnect() {}

        override suspend fun sendMessage(
            message: WsClientMessage
        ): Boolean {
            return shouldSucceed
        }
    }

    private class FakeChatNetworkDataSource : ChatNetworkDataSource {
        var lastRequestedBefore: Instant? = null
        var messagesToReturn = ChatMessageSliceDto(messages = emptyList(), hasMore = false)

        override suspend fun getChatRoomsForUser(): Either<NetworkError<Nothing>, List<ChatRoomDto>> =
            Either.Right(emptyList())

        override suspend fun getChatRoom(roomId: String): Either<NetworkError<ChatServerError.GetRoom>, ChatRoomDto> =
            Either.Left(NetworkError.Transport.NoInternet)

        override suspend fun getMessagesForChatRoom(
            roomId: String,
            before: Instant?
        ): Either<NetworkError<ChatServerError.GetMessages>, ChatMessageSliceDto> {
            lastRequestedBefore = before
            return Either.Right(messagesToReturn)
        }

        override suspend fun getParticipantByEmail(email: String): Either<NetworkError<ChatServerError.GetParticipant>, ChatParticipantDto> =
            Either.Left(NetworkError.Transport.NoInternet)

        override suspend fun createDirectChatRoom(
            targetUserId: String,
            message: String
        ): Either<NetworkError<ChatServerError.CreateRoom>, ChatRoomDto> =
            Either.Left(NetworkError.Transport.NoInternet)

        override suspend fun createGroupChatRoom(
            participantIds: List<String>,
            name: String?
        ): Either<NetworkError<ChatServerError.CreateRoom>, ChatRoomDto> =
            Either.Left(NetworkError.Transport.NoInternet)

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

    private class FakeTokenDataSource(
        initialAuthState: AuthState = AuthState.Authenticated(
            user = User(
                id = "user123",
                email = "test@example.com",
                displayName = "Test User",
                isEmailVerified = true
            ),
            accessToken = "access_token"
        )
    ) : TokenDataSource {
        override val authState: Flow<AuthState> = flowOf(initialAuthState)
        override suspend fun getRefreshToken(): String? = "refresh_token"
        override suspend fun getAccessToken(): String? = "access_token"
        override suspend fun saveAuthData(user: User, accessToken: String, refreshToken: String) {}
        override suspend fun saveTokens(accessToken: String, refreshToken: String) {}
        override suspend fun clearAuthData() {}
    }

    private fun createRepository(
        chatDao: ChatDao,
        realtimeDataSource: ChatRealtimeDataSource,
        networkDataSource: ChatNetworkDataSource,
        tokenDataSource: TokenDataSource,
    ): ChatMessageRepositoryImpl = ChatMessageRepositoryImpl(
        chatDao = chatDao,
        realtimeDataSource = realtimeDataSource,
        networkDataSource = networkDataSource,
        tokenDataSource = tokenDataSource,
        defaultDispatcher = testDispatcher
    )

    @Test
    fun `getMessages validates limit greater than 0`() {
        val fakeDao = FakeChatDao()
        val fakeRealtime = FakeChatRealtimeDataSource()
        val fakeNetwork = FakeChatNetworkDataSource()
        val fakeToken = FakeTokenDataSource()

        val repository = createRepository(
            chatDao = fakeDao,
            realtimeDataSource = fakeRealtime,
            networkDataSource = fakeNetwork,
            tokenDataSource = fakeToken
        )


        assertFailsWith<IllegalArgumentException> {
            repository.getMessages("room1", limit = 0)
        }
    }

    @Test
    fun `syncRecentMessages queries network with before null and returns hasMore`() = runTest {
        val fakeDao = FakeChatDao()
        val fakeRealtime = FakeChatRealtimeDataSource()
        val sampleDto =
            ChatMessageDto("m1", "room1", "u1", "hello", Instant.fromEpochMilliseconds(1000L))
        val fakeNetwork = FakeChatNetworkDataSource().apply {
            messagesToReturn = ChatMessageSliceDto(messages = listOf(sampleDto), hasMore = true)
        }
        val fakeToken = FakeTokenDataSource()

        val repository = createRepository(
            chatDao = fakeDao,
            realtimeDataSource = fakeRealtime,
            networkDataSource = fakeNetwork,
            tokenDataSource = fakeToken
        )

        val result = repository.syncRecentMessages("room1")

        assertTrue(result.isRight())
        assertEquals(true, (result as Either.Right).value)
        assertNull(fakeNetwork.lastRequestedBefore)
        assertEquals(1, fakeDao.insertedMessages.size)
    }

    @Test
    fun `syncRecentMessages clears sent messages and updates room summary when remote is empty`() =
        runTest {
            val fakeDao = FakeChatDao().apply {
                insertedMessages.add(
                    ChatMessageEntity(
                        id = "sent-1",
                        roomId = "room1",
                        senderId = "u1",
                        content = "sent message",
                        timestamp = 1000L,
                        deliveryStatus = MessageDeliveryStatus.SENT
                    )
                )
            }
            val fakeNetwork = FakeChatNetworkDataSource().apply {
                messagesToReturn = ChatMessageSliceDto(messages = emptyList(), hasMore = false)
            }
            val fakeRealtime = FakeChatRealtimeDataSource()
            val fakeToken = FakeTokenDataSource()

            val repository = createRepository(
                chatDao = fakeDao,
                realtimeDataSource = fakeRealtime,
                networkDataSource = fakeNetwork,
                tokenDataSource = fakeToken
            )

            val result = repository.syncRecentMessages("room1")

            assertTrue(result.isRight())
            assertEquals(false, (result as Either.Right).value)
            assertEquals(1, fakeDao.clearSentMessagesCalledCount)
            assertEquals(0, fakeDao.insertedMessages.size)
            assertEquals("room1", fakeDao.lastUpdatedRoomId)
            assertNull(fakeDao.lastUpdatedRoomMessageId)
            assertNull(fakeDao.lastUpdatedRoomTimestamp)
    }

    @Test
    fun `loadOlderMessages passes oldest timestamp as before Instant to network`() = runTest {
        val oldestTimeMillis = 1700000000000L
        val fakeDao = FakeChatDao().apply {
            oldestSentTimestampToReturn = oldestTimeMillis
        }
        val fakeRealtime = FakeChatRealtimeDataSource()
        val sampleDto = ChatMessageDto(
            "m0",
            "room1",
            "u1",
            "older msg",
            Instant.fromEpochMilliseconds(oldestTimeMillis - 1000L)
        )
        val fakeNetwork = FakeChatNetworkDataSource().apply {
            messagesToReturn = ChatMessageSliceDto(messages = listOf(sampleDto), hasMore = false)
        }
        val fakeToken = FakeTokenDataSource()

        val repository = createRepository(
            chatDao = fakeDao,
            realtimeDataSource = fakeRealtime,
            networkDataSource = fakeNetwork,
            tokenDataSource = fakeToken
        )

        val result = repository.loadOlderMessages("room1")

        assertTrue(result.isRight())
        assertEquals(false, (result as Either.Right).value)
        assertEquals(
            Instant.fromEpochMilliseconds(oldestTimeMillis),
            fakeNetwork.lastRequestedBefore
        )
        assertEquals(1, fakeDao.insertedMessages.size)
    }

    @Test
    fun `syncRecentMessages clears sent messages when message gap is detected`() = runTest {
        val newestLocalTimestamp = 1000L
        val oldestRemoteTimestamp = 5000L // Remote is newer than newest local -> Gap!

        val fakeDao = FakeChatDao().apply {
            newestSentTimestampToReturn = newestLocalTimestamp
            insertedMessages.add(
                ChatMessageEntity(
                    id = "stale-1",
                    roomId = "room1",
                    senderId = "u1",
                    content = "stale",
                    timestamp = 500L,
                    deliveryStatus = MessageDeliveryStatus.SENT
                )
            )
            insertedMessages.add(
                ChatMessageEntity(
                    id = "sending-1",
                    roomId = "room1",
                    senderId = "me",
                    content = "sending",
                    timestamp = 6000L,
                    deliveryStatus = MessageDeliveryStatus.SENDING
                )
            )
        }

        val remoteDto = ChatMessageDto(
            id = "remote-1",
            roomId = "room1",
            senderId = "u2",
            content = "new remote msg",
            createdAt = Instant.fromEpochMilliseconds(oldestRemoteTimestamp)
        )
        val fakeNetwork = FakeChatNetworkDataSource().apply {
            messagesToReturn = ChatMessageSliceDto(messages = listOf(remoteDto), hasMore = false)
        }
        val fakeRealtime = FakeChatRealtimeDataSource()
        val fakeToken = FakeTokenDataSource()

        val repository = createRepository(
            chatDao = fakeDao,
            realtimeDataSource = fakeRealtime,
            networkDataSource = fakeNetwork,
            tokenDataSource = fakeToken
        )

        val result = repository.syncRecentMessages("room1")

        assertTrue(result.isRight())
        assertEquals(1, fakeDao.clearSentMessagesCalledCount)
        // Verify stale SENT message was cleared, but SENDING message and new remote message remain
        assertEquals(2, fakeDao.insertedMessages.size)
        assertTrue(fakeDao.insertedMessages.any { it.id == "sending-1" })
        assertTrue(fakeDao.insertedMessages.any { it.id == "remote-1" })
    }

    @Test
    fun `syncRecentMessages does not clear sent messages when no gap exists`() = runTest {
        val newestLocalTimestamp = 5000L
        val oldestRemoteTimestamp = 3000L // Overlapping data -> No gap

        val fakeDao = FakeChatDao().apply {
            newestSentTimestampToReturn = newestLocalTimestamp
        }

        val remoteDto = ChatMessageDto(
            id = "remote-1",
            roomId = "room1",
            senderId = "u2",
            content = "remote msg",
            createdAt = Instant.fromEpochMilliseconds(oldestRemoteTimestamp)
        )
        val fakeNetwork = FakeChatNetworkDataSource().apply {
            messagesToReturn = ChatMessageSliceDto(messages = listOf(remoteDto), hasMore = false)
        }
        val fakeRealtime = FakeChatRealtimeDataSource()
        val fakeToken = FakeTokenDataSource()

        val repository = createRepository(
            chatDao = fakeDao,
            realtimeDataSource = fakeRealtime,
            networkDataSource = fakeNetwork,
            tokenDataSource = fakeToken
        )

        val result = repository.syncRecentMessages("room1")

        assertTrue(result.isRight())
        assertEquals(0, fakeDao.clearSentMessagesCalledCount)
    }

    @Test
    fun `sendMessage inserts sending entity and keeps status SENDING on socket write success`() =
        runTest {
            val fakeDao = FakeChatDao()
            val fakeRealtime = FakeChatRealtimeDataSource().apply { shouldSucceed = true }
            val fakeNetwork = FakeChatNetworkDataSource()
            val fakeToken = FakeTokenDataSource()

            val repository = createRepository(
                chatDao = fakeDao,
                realtimeDataSource = fakeRealtime,
                networkDataSource = fakeNetwork,
                tokenDataSource = fakeToken
            )

            repository.sendMessage("room1", "Hello World")

            assertEquals(1, fakeDao.insertedMessages.size)

            val insertedMsg = fakeDao.insertedMessages.first()
            assertEquals("room1", insertedMsg.roomId)
            assertEquals("Hello World", insertedMsg.content)
            assertEquals(MessageDeliveryStatus.SENDING, insertedMsg.deliveryStatus)

            assertEquals("room1", fakeDao.lastUpdatedRoomId)
            assertEquals(insertedMsg.id, fakeDao.lastUpdatedRoomMessageId)
            assertEquals(insertedMsg.timestamp, fakeDao.lastUpdatedRoomTimestamp)

            assertNull(fakeDao.updatedStatuses[insertedMsg.id])
        }

    @Test
    fun `sendMessage updates status to FAILED when socket write fails`() = runTest {
        val fakeDao = FakeChatDao()
        val fakeRealtime = FakeChatRealtimeDataSource().apply { shouldSucceed = false }
        val fakeNetwork = FakeChatNetworkDataSource()
        val fakeToken = FakeTokenDataSource()

        val repository = createRepository(
            chatDao = fakeDao,
            realtimeDataSource = fakeRealtime,
            networkDataSource = fakeNetwork,
            tokenDataSource = fakeToken
        )

        repository.sendMessage("room1", "Hello World")

        assertEquals(1, fakeDao.insertedMessages.size)

        val insertedMsg = fakeDao.insertedMessages.first()
        assertEquals(MessageDeliveryStatus.FAILED, fakeDao.updatedStatuses[insertedMsg.id])
    }

    @Test
    fun `sendMessage does not insert when unauthenticated`() = runTest {
        val fakeDao = FakeChatDao()
        val fakeRealtime = FakeChatRealtimeDataSource()
        val fakeNetwork = FakeChatNetworkDataSource()
        val fakeToken = FakeTokenDataSource(initialAuthState = AuthState.Unauthenticated)

        val repository = createRepository(
            chatDao = fakeDao,
            realtimeDataSource = fakeRealtime,
            networkDataSource = fakeNetwork,
            tokenDataSource = fakeToken
        )

        repository.sendMessage("room1", "Hello World")

        assertEquals(0, fakeDao.insertedMessages.size)
    }

    @Test
    fun `retryMessage updates status to SENDING and sends through realtime`() = runTest {
        val fakeDao = FakeChatDao().apply {
            insertedMessages.add(
                ChatMessageEntity(
                    id = "msg-123",
                    roomId = "room1",
                    senderId = "u1",
                    content = "Retry me",
                    timestamp = 1000L,
                    deliveryStatus = MessageDeliveryStatus.FAILED
                )
            )
        }
        val fakeRealtime = FakeChatRealtimeDataSource().apply { shouldSucceed = true }
        val fakeNetwork = FakeChatNetworkDataSource()
        val fakeToken = FakeTokenDataSource()

        val repository = createRepository(
            chatDao = fakeDao,
            realtimeDataSource = fakeRealtime,
            networkDataSource = fakeNetwork,
            tokenDataSource = fakeToken
        )

        repository.retryMessage("msg-123")
        assertEquals(MessageDeliveryStatus.SENDING, fakeDao.updatedStatuses["msg-123"])
    }

    @Test
    fun `retryMessage does nothing when message is missing locally`() = runTest {
        val fakeDao = FakeChatDao()
        val repository = createRepository(
            chatDao = fakeDao,
            realtimeDataSource = FakeChatRealtimeDataSource().apply { shouldSucceed = true },
            networkDataSource = FakeChatNetworkDataSource(),
            tokenDataSource = FakeTokenDataSource()
        )

        repository.retryMessage("missing-id")

        assertTrue(fakeDao.updatedStatuses.isEmpty())
    }

    @Test
    fun `deleteMessage calls network and updates local delivery status to DELETED`() = runTest {
        val fakeDao = FakeChatDao().apply {
            insertedMessages.add(
                ChatMessageEntity(
                    id = "msg-456",
                    roomId = "room1",
                    senderId = "u1",
                    content = "Delete me",
                    timestamp = 1000L,
                    deliveryStatus = MessageDeliveryStatus.SENT
                )
            )
        }
        val fakeRealtime = FakeChatRealtimeDataSource()
        val fakeNetwork = FakeChatNetworkDataSource()
        val fakeToken = FakeTokenDataSource()

        val repository = createRepository(
            chatDao = fakeDao,
            realtimeDataSource = fakeRealtime,
            networkDataSource = fakeNetwork,
            tokenDataSource = fakeToken
        )

        val result = repository.deleteMessage("msg-456")
        assertTrue(result.isRight())
        assertEquals(MessageDeliveryStatus.DELETED, fakeDao.updatedStatuses["msg-456"])
    }
}

