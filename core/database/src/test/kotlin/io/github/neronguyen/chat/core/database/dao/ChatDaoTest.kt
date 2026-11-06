package io.github.neronguyen.chat.core.database.dao

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import app.cash.turbine.test
import io.github.neronguyen.chat.core.database.ChatDatabase
import io.github.neronguyen.chat.core.database.entity.ChatMessageEntity
import io.github.neronguyen.chat.core.database.entity.ChatParticipantEntity
import io.github.neronguyen.chat.core.database.entity.ChatRoomEntity
import io.github.neronguyen.chat.core.database.entity.ChatRoomParticipantCrossRef
import io.github.neronguyen.chat.core.model.ChatRoomType
import io.github.neronguyen.chat.core.model.MessageDeliveryStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ChatDaoTest {

    private lateinit var database: ChatDatabase
    private lateinit var chatDao: ChatDao

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, ChatDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        chatDao = database.chatDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // Helper functions
    private suspend fun createRoom(
        id: String = "room-1",
        creatorId: String = "user-1",
        lastActivityAt: Long? = 1000L,
        lastMessageId: String? = null
    ): ChatRoomEntity {
        val room = ChatRoomEntity(
            id = id,
            type = ChatRoomType.DIRECT,
            name = "Test Room",
            creatorId = creatorId,
            lastMessageId = lastMessageId,
            lastActivityAt = lastActivityAt
        )
        chatDao.upsertRoom(room)
        return room
    }

    private suspend fun createParticipant(
        userId: String = "user-1",
        email: String = "user1@example.com",
        displayName: String = "User One"
    ): ChatParticipantEntity {
        val participant = ChatParticipantEntity(
            userId = userId,
            email = email,
            displayName = displayName,
            profilePictureUrl = null
        )
        chatDao.upsertParticipant(participant)
        return participant
    }

    private fun createMessage(
        id: String,
        roomId: String = "room-1",
        senderId: String = "user-1",
        content: String = "Hello",
        timestamp: Long = 1000L,
        deliveryStatus: MessageDeliveryStatus = MessageDeliveryStatus.SENT
    ) = ChatMessageEntity(
        id = id,
        roomId = roomId,
        senderId = senderId,
        content = content,
        timestamp = timestamp,
        deliveryStatus = deliveryStatus
    )

    // =========================================================================
    // Group 1: Message Delivery Lifecycle & State Transitions
    // =========================================================================

    @Test
    fun insertSendingMessage_storesSendingStatus_andUpdatesRoomLastActivity() = runTest {
        createRoom("room-1")
        val msg = createMessage("msg-1", timestamp = 2000L, deliveryStatus = MessageDeliveryStatus.SENDING)

        chatDao.insertSendingMessage(msg)

        val stored = chatDao.getMessageById("msg-1")
        assertNotNull(stored)
        assertEquals(MessageDeliveryStatus.SENDING, stored.deliveryStatus)

        val room = chatDao.getChatWithDetails("room-1").first()
        assertNotNull(room)
        assertEquals("msg-1", room.room.lastMessageId)
        assertEquals(2000L, room.room.lastActivityAt)
    }

    @Test
    fun upsertIncomingOrAckedMessage_transitionsSendingToSent_withoutDuplicating() = runTest {
        createRoom("room-1")
        val sending = createMessage("msg-1", timestamp = 2000L, deliveryStatus = MessageDeliveryStatus.SENDING)
        chatDao.insertSendingMessage(sending)

        val acked = createMessage("msg-1", timestamp = 2050L, deliveryStatus = MessageDeliveryStatus.SENT)
        chatDao.upsertIncomingOrAckedMessage(acked)

        val stored = chatDao.getMessageById("msg-1")
        assertNotNull(stored)
        assertEquals(MessageDeliveryStatus.SENT, stored.deliveryStatus)
        assertEquals(2050L, stored.timestamp)

        val recent = chatDao.getRecentMessages("room-1", 10).first()
        assertEquals(1, recent.size)

        val room = chatDao.getChatWithDetails("room-1").first()
        assertEquals("msg-1", room?.room?.lastMessageId)
        assertEquals(2050L, room?.room?.lastActivityAt)
    }

    @Test
    fun markSendingMessagesAsFailed_transitionsOnlySending_leavingSentAndDeletedIntact() = runTest {
        createRoom("room-1")
        val msgSending1 = createMessage("msg-s1", timestamp = 100L, deliveryStatus = MessageDeliveryStatus.SENDING)
        val msgSending2 = createMessage("msg-s2", timestamp = 200L, deliveryStatus = MessageDeliveryStatus.SENDING)
        val msgSent = createMessage("msg-ok", timestamp = 300L, deliveryStatus = MessageDeliveryStatus.SENT)
        val msgDeleted = createMessage("msg-del", timestamp = 400L, deliveryStatus = MessageDeliveryStatus.DELETED)

        chatDao.upsertMessages(listOf(msgSending1, msgSending2, msgSent, msgDeleted))

        chatDao.markSendingMessagesAsFailed()

        assertEquals(MessageDeliveryStatus.FAILED, chatDao.getMessageById("msg-s1")?.deliveryStatus)
        assertEquals(MessageDeliveryStatus.FAILED, chatDao.getMessageById("msg-s2")?.deliveryStatus)
        assertEquals(MessageDeliveryStatus.SENT, chatDao.getMessageById("msg-ok")?.deliveryStatus)
        assertEquals(MessageDeliveryStatus.DELETED, chatDao.getMessageById("msg-del")?.deliveryStatus)
    }

    @Test
    fun updateDeliveryStatus_transitionsFailedToSendingOnRetry_keepingSameId() = runTest {
        createRoom("room-1")
        val failed = createMessage("msg-1", deliveryStatus = MessageDeliveryStatus.FAILED)
        chatDao.upsertMessage(failed)

        chatDao.updateDeliveryStatus("msg-1", MessageDeliveryStatus.SENDING)

        val stored = chatDao.getMessageById("msg-1")
        assertNotNull(stored)
        assertEquals(MessageDeliveryStatus.SENDING, stored.deliveryStatus)
        assertEquals("msg-1", stored.id)
    }

    @Test
    fun updateDeliveryStatus_transitionsToDeleted_preservingRowAsTombstone() = runTest {
        createRoom("room-1")
        val msg = createMessage("msg-1", content = "Secret message", deliveryStatus = MessageDeliveryStatus.SENT)
        chatDao.upsertMessage(msg)

        chatDao.updateDeliveryStatus("msg-1", MessageDeliveryStatus.DELETED)

        val stored = chatDao.getMessageById("msg-1")
        assertNotNull(stored)
        assertEquals(MessageDeliveryStatus.DELETED, stored.deliveryStatus)
        assertEquals("Secret message", stored.content)
    }

    @Test
    fun deleteMessage_physicallyRemovesRowFromDatabase() = runTest {
        createRoom("room-1")
        val msg = createMessage("msg-1")
        chatDao.upsertMessage(msg)

        chatDao.deleteMessage("msg-1")

        assertNull(chatDao.getMessageById("msg-1"))
    }

    // =========================================================================
    // Group 2: syncRecentMessagesTransaction & Sync Gap Semantics
    // =========================================================================

    @Test
    fun syncRecentMessagesTransaction_noGap_mergesRemote_andPreservesPendingAndDeleted() = runTest {
        createRoom("room-1")
        // Local state: m1 (SENT, ts 100), mPending (SENDING, ts 250), mFailed (FAILED, ts 260), mDel (DELETED, ts 90)
        val m1 = createMessage("m1", timestamp = 100L, deliveryStatus = MessageDeliveryStatus.SENT)
        val mPending = createMessage("mPending", timestamp = 250L, deliveryStatus = MessageDeliveryStatus.SENDING)
        val mFailed = createMessage("mFailed", timestamp = 260L, deliveryStatus = MessageDeliveryStatus.FAILED)
        val mDel = createMessage("mDel", timestamp = 90L, deliveryStatus = MessageDeliveryStatus.DELETED)
        chatDao.upsertMessages(listOf(m1, mPending, mFailed, mDel))

        // Remote fetch returns m1 (updated ts) and new m2 (ts 200). Since oldestRemote (100) <= newestLocalSent (100), no gap.
        val remoteM1 = createMessage("m1", content = "m1 updated", timestamp = 100L, deliveryStatus = MessageDeliveryStatus.SENT)
        val remoteM2 = createMessage("m2", content = "m2", timestamp = 200L, deliveryStatus = MessageDeliveryStatus.SENT)

        chatDao.syncRecentMessagesTransaction("room-1", listOf(remoteM1, remoteM2))

        val allMessages = chatDao.getRecentMessages("room-1", 10).first()
        assertEquals(5, allMessages.size)
        assertEquals("m1 updated", chatDao.getMessageById("m1")?.content)
        assertNotNull(chatDao.getMessageById("m2"))
        assertNotNull(chatDao.getMessageById("mPending"))
        assertNotNull(chatDao.getMessageById("mFailed"))
        assertNotNull(chatDao.getMessageById("mDel"))
        assertEquals(MessageDeliveryStatus.SENDING, chatDao.getMessageById("mPending")?.deliveryStatus)
        assertEquals(MessageDeliveryStatus.FAILED, chatDao.getMessageById("mFailed")?.deliveryStatus)
        assertEquals(MessageDeliveryStatus.DELETED, chatDao.getMessageById("mDel")?.deliveryStatus)
    }

    @Test
    fun syncRecentMessagesTransaction_withGap_clearsOnlySent_preservingPendingAndDeleted() = runTest {
        createRoom("room-1")
        // Local state: m1 (SENT, ts 100), mPending (SENDING, ts 400), mDel (DELETED, ts 90)
        val m1 = createMessage("m1", timestamp = 100L, deliveryStatus = MessageDeliveryStatus.SENT)
        val mPending = createMessage("mPending", timestamp = 400L, deliveryStatus = MessageDeliveryStatus.SENDING)
        val mDel = createMessage("mDel", timestamp = 90L, deliveryStatus = MessageDeliveryStatus.DELETED)
        chatDao.upsertMessages(listOf(m1, mPending, mDel))

        // Remote fetch has oldestRemote (500) > newestLocalSent (100) -> GAP!
        val remoteM5 = createMessage("m5", timestamp = 500L, deliveryStatus = MessageDeliveryStatus.SENT)
        val remoteM6 = createMessage("m6", timestamp = 600L, deliveryStatus = MessageDeliveryStatus.SENT)

        chatDao.syncRecentMessagesTransaction("room-1", listOf(remoteM5, remoteM6))

        // m1 (SENT) should have been cleared because of gap
        assertNull(chatDao.getMessageById("m1"))
        // Local SENDING and DELETED must survive!
        assertNotNull(chatDao.getMessageById("mPending"))
        assertNotNull(chatDao.getMessageById("mDel"))
        // Remote messages upserted
        assertNotNull(chatDao.getMessageById("m5"))
        assertNotNull(chatDao.getMessageById("m6"))
    }

    @Test
    fun syncRecentMessagesTransaction_emptyRemote_clearsOnlySentMessages() = runTest {
        createRoom("room-1")
        val mSent = createMessage("mSent", timestamp = 100L, deliveryStatus = MessageDeliveryStatus.SENT)
        val mSending = createMessage("mSending", timestamp = 200L, deliveryStatus = MessageDeliveryStatus.SENDING)
        val mDeleted = createMessage("mDeleted", timestamp = 50L, deliveryStatus = MessageDeliveryStatus.DELETED)
        chatDao.upsertMessages(listOf(mSent, mSending, mDeleted))

        chatDao.syncRecentMessagesTransaction("room-1", emptyList())

        assertNull(chatDao.getMessageById("mSent"))
        assertNotNull(chatDao.getMessageById("mSending"))
        assertNotNull(chatDao.getMessageById("mDeleted"))
    }

    @Test
    fun syncRecentMessagesTransaction_updatesRoomLastActivity_includingLocalPending() = runTest {
        createRoom("room-1")
        // Local pending message with high timestamp (9999L)
        val mPending = createMessage("mPending", timestamp = 9999L, deliveryStatus = MessageDeliveryStatus.SENDING)
        chatDao.insertSendingMessage(mPending)

        // Remote messages with lower timestamps (1000L, 2000L)
        val remoteM1 = createMessage("m1", timestamp = 1000L, deliveryStatus = MessageDeliveryStatus.SENT)
        val remoteM2 = createMessage("m2", timestamp = 2000L, deliveryStatus = MessageDeliveryStatus.SENT)

        chatDao.syncRecentMessagesTransaction("room-1", listOf(remoteM1, remoteM2))

        val room = chatDao.getChatWithDetails("room-1").first()
        assertEquals("mPending", room?.room?.lastMessageId)
        assertEquals(9999L, room?.room?.lastActivityAt)
    }

    @Test
    fun syncRecentMessagesTransaction_validatesAllMessagesBelongToRoom() = runTest {
        createRoom("room-1")
        createRoom("room-2")
        val m1 = createMessage("m1", roomId = "room-1")
        val m2 = createMessage("m2", roomId = "room-2")

        assertFailsWith<IllegalArgumentException> {
            chatDao.syncRecentMessagesTransaction("room-1", listOf(m1, m2))
        }
    }

    @Test
    fun syncRecentMessagesTransaction_knownGap_offlineDeleteLeavesGhostSentMessage() = runTest {
        // Characterization test for Q2-A:
        // When a message is deleted server-side while the client is offline,
        // syncRecentMessagesTransaction does not prune local SENT messages if there is no gap.
        createRoom("room-1")
        val m1 = createMessage("m1", timestamp = 100L, deliveryStatus = MessageDeliveryStatus.SENT)
        val m2 = createMessage("m2", timestamp = 200L, deliveryStatus = MessageDeliveryStatus.SENT)
        val m3 = createMessage("m3", timestamp = 300L, deliveryStatus = MessageDeliveryStatus.SENT)
        chatDao.upsertMessages(listOf(m1, m2, m3))

        // Remote server hard-deleted m2. Remote page returns [m1, m3].
        // Since oldestRemote (100) <= newestLocalSent (300), hasMessageGap is false.
        chatDao.syncRecentMessagesTransaction("room-1", listOf(m1, m3))

        // KNOWN GAP: m2 is NOT pruned by syncRecentMessagesTransaction and survives as a ghost SENT message.
        val ghostMessage = chatDao.getMessageById("m2")
        assertNotNull(ghostMessage, "Known gap: m2 survives as a ghost message because sync does not prune missing items within window")
        assertEquals(MessageDeliveryStatus.SENT, ghostMessage.deliveryStatus)
    }

    // =========================================================================
    // Group 3: Foreign Key Constraints & Cascade Deletion
    // =========================================================================

    @Test
    fun insertMessage_forUnknownRoom_throwsSQLiteConstraintException_characterizingQ1() = runTest {
        // Characterization test for Q1:
        // chat_messages has a foreign key referencing chat_rooms(id).
        // Inserting a message before the room exists in the local database throws SQLiteConstraintException.
        val msg = createMessage("msg-unknown-room", roomId = "non-existent-room")

        assertFailsWith<SQLiteConstraintException> {
            chatDao.insertSendingMessage(msg)
        }
    }

    @Test
    fun upsertIncomingOrAckedMessage_forUnknownRoom_throwsSQLiteConstraintException_characterizingQ1() = runTest {
        val msg = createMessage("msg-unknown-room", roomId = "non-existent-room")

        assertFailsWith<SQLiteConstraintException> {
            chatDao.upsertIncomingOrAckedMessage(msg)
        }
    }

    @Test
    fun deleteRoom_cascadesAndRemovesMessagesAndCrossRefs() = runTest {
        createRoom("room-1")
        createParticipant("user-1")
        createParticipant("user-2")
        chatDao.upsertCrossRefs(
            listOf(
                ChatRoomParticipantCrossRef("room-1", "user-1"),
                ChatRoomParticipantCrossRef("room-1", "user-2")
            )
        )
        chatDao.upsertMessage(createMessage("m1", roomId = "room-1"))
        chatDao.upsertMessage(createMessage("m2", roomId = "room-1"))

        // Delete the parent room
        chatDao.deleteRoom("room-1")

        // Foreign keys CASCADE should clean up messages and cross-refs
        assertNull(chatDao.getMessageById("m1"))
        assertNull(chatDao.getMessageById("m2"))
        val crossRefs = chatDao.getCrossRefsForRooms(listOf("room-1"))
        assertTrue(crossRefs.isEmpty())
        // Participants themselves are independent and not deleted
        assertNotNull(createParticipant("user-1"))
    }

    @Test
    fun removeRoomAndData_cleansUpRoomMessagesAndCrossRefs() = runTest {
        createRoom("room-1")
        createParticipant("user-1")
        chatDao.upsertCrossRefs(listOf(ChatRoomParticipantCrossRef("room-1", "user-1")))
        chatDao.upsertMessage(createMessage("m1", roomId = "room-1"))

        chatDao.removeRoomAndData("room-1")

        val room = chatDao.getChatWithDetails("room-1").first()
        assertNull(room)
        assertNull(chatDao.getMessageById("m1"))
        assertTrue(chatDao.getCrossRefsForRooms(listOf("room-1")).isEmpty())
    }

    @Test
    fun clearAllChatData_emptiesAllTables() = runTest {
        createRoom("room-1")
        createParticipant("user-1")
        chatDao.upsertCrossRefs(listOf(ChatRoomParticipantCrossRef("room-1", "user-1")))
        chatDao.upsertMessage(createMessage("m1", roomId = "room-1"))

        chatDao.clearAllChatData()

        val rooms = chatDao.getChatsWithDetails().first()
        assertTrue(rooms.isEmpty())
        assertNull(chatDao.getMessageById("m1"))
        assertTrue(chatDao.getCrossRefsForRooms(listOf("room-1")).isEmpty())
    }

    // =========================================================================
    // Group 4: Multi-Entity Operations (insertFullChat & insertFullChats)
    // =========================================================================

    @Test
    fun insertFullChat_upsertsRoomParticipantsAndLastMessageIdempotently() = runTest {
        val room = ChatRoomEntity(id = "room-1", creatorId = "u1", lastMessageId = "m1", lastActivityAt = 1000L)
        val p1 = ChatParticipantEntity("u1", "u1@test.com", "U1")
        val p2 = ChatParticipantEntity("u2", "u2@test.com", "U2")
        val lastMsg = createMessage("m1", roomId = "room-1", senderId = "u1", timestamp = 1000L)

        chatDao.insertFullChat(room, listOf(p1, p2), lastMsg)

        val detail = chatDao.getChatWithDetails("room-1").first()
        assertNotNull(detail)
        assertEquals("room-1", detail.room.id)
        assertEquals(2, detail.participants.size)
        assertEquals("m1", detail.lastMessage?.id)

        // Idempotent re-insert with updated user display name
        val p2Updated = ChatParticipantEntity("u2", "u2@test.com", "U2 Updated")
        chatDao.insertFullChat(room, listOf(p1, p2Updated), lastMsg)

        val detailAfter = chatDao.getChatWithDetails("room-1").first()
        assertNotNull(detailAfter)
        val u2 = detailAfter.participants.first { it.userId == "u2" }
        assertEquals("U2 Updated", u2.displayName)
    }

    @Test
    fun insertFullChats_prunesStaleCrossRefs_andRetainsCurrentOnes() = runTest {
        val room1 = ChatRoomEntity(id = "r1", creatorId = "u1")
        val p1 = ChatParticipantEntity("u1", "u1@test.com", "U1")
        val p2 = ChatParticipantEntity("u2", "u2@test.com", "U2")
        val p3 = ChatParticipantEntity("u3", "u3@test.com", "U3")

        // Initially room1 has participants u1 and u2
        chatDao.insertFullChat(room1, listOf(p1, p2), null)
        assertEquals(2, chatDao.getCrossRefsForRooms(listOf("r1")).size)

        // Next sync: room1 now has participants u2 and u3 (u1 was removed / stale)
        val newCrossRefs = listOf(
            ChatRoomParticipantCrossRef("r1", "u2"),
            ChatRoomParticipantCrossRef("r1", "u3")
        )
        chatDao.insertFullChats(
            rooms = listOf(room1),
            participants = listOf(p2, p3),
            lastMessages = emptyList(),
            crossRefs = newCrossRefs
        )

        val currentCrossRefs = chatDao.getCrossRefsForRooms(listOf("r1"))
        assertEquals(2, currentCrossRefs.size)
        val userIds = currentCrossRefs.map { it.userId }.toSet()
        assertEquals(setOf("u2", "u3"), userIds)
    }

    @Test
    fun insertFullChats_emptyRoomsList_isNoOp() = runTest {
        createRoom("r1")
        val before = chatDao.getChatsWithDetails().first()

        chatDao.insertFullChats(emptyList(), emptyList(), emptyList(), emptyList())

        val after = chatDao.getChatsWithDetails().first()
        assertEquals(before.size, after.size)
    }

    @Test
    fun insertFullChats_handlesLargeBatchOver500ChunkLimit() = runTest {
        // Create 600 participants and cross-refs to exercise the 500-chunk threshold
        val room = ChatRoomEntity(id = "r-large", creatorId = "u0")
        chatDao.upsertRoom(room)

        val participants = (0 until 600).map { i ->
            ChatParticipantEntity("user-$i", "u$i@test.com", "User $i")
        }
        val crossRefs = (0 until 600).map { i ->
            ChatRoomParticipantCrossRef("r-large", "user-$i")
        }

        chatDao.insertFullChats(
            rooms = listOf(room),
            participants = participants,
            lastMessages = emptyList(),
            crossRefs = crossRefs
        )

        val storedCrossRefs = chatDao.getCrossRefsForRooms(listOf("r-large"))
        assertEquals(600, storedCrossRefs.size)
    }

    // =========================================================================
    // Group 5: Participant Membership Operations
    // =========================================================================

    @Test
    fun handleParticipantJoined_upsertsNewUsersAndCrossRefs() = runTest {
        createRoom("room-1")
        val inviter = createParticipant("admin", "admin@test.com", "Admin")
        val joiner1 = ChatParticipantEntity("joiner1", "j1@test.com", "Joiner 1")
        val joiner2 = ChatParticipantEntity("joiner2", "j2@test.com", "Joiner 2")

        chatDao.handleParticipantJoined(
            roomId = "room-1",
            newUsers = listOf(joiner1, joiner2),
            addedBy = inviter
        )

        val crossRefs = chatDao.getCrossRefsForRooms(listOf("room-1"))
        assertEquals(2, crossRefs.size)
        val userIds = crossRefs.map { it.userId }.toSet()
        assertEquals(setOf("joiner1", "joiner2"), userIds)
    }

    @Test
    fun handleParticipantLeft_removesCrossRef_preservingParticipantEntity() = runTest {
        createRoom("room-1")
        val user1 = createParticipant("u1")
        val user2 = createParticipant("u2")
        chatDao.upsertCrossRefs(
            listOf(
                ChatRoomParticipantCrossRef("room-1", "u1"),
                ChatRoomParticipantCrossRef("room-1", "u2")
            )
        )

        chatDao.handleParticipantLeft("room-1", user2)

        val crossRefs = chatDao.getCrossRefsForRooms(listOf("room-1"))
        assertEquals(1, crossRefs.size)
        assertEquals("u1", crossRefs.first().userId)

        // Participant record itself is preserved in chat_participants
        val detail = chatDao.getChatWithDetails("room-1").first()
        assertNotNull(detail)
        assertEquals(1, detail.participants.size)
    }

    // =========================================================================
    // Group 6: Queries, Timestamp Invariants & Flow Observations
    // =========================================================================

    @Test
    fun getRecentMessages_ordersByTimestampDesc_thenIdDesc_andRespectsLimit() = runTest {
        createRoom("room-1")
        // Three messages: m1 (ts 100), m2 (ts 200, id "b"), m3 (ts 200, id "a")
        val m1 = createMessage("m1", timestamp = 100L)
        val m2 = createMessage("b", timestamp = 200L)
        val m3 = createMessage("a", timestamp = 200L)
        chatDao.upsertMessages(listOf(m1, m2, m3))

        chatDao.getRecentMessages("room-1", limit = 2).test {
            val list = awaitItem()
            assertEquals(2, list.size)
            // ts 200 items first; id "b" before "a" because of ORDER BY timestamp DESC, id DESC
            assertEquals("b", list[0].id)
            assertEquals("a", list[1].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getOldestAndNewestSentMessageTimestamp_ignoresNonSentMessages() = runTest {
        createRoom("room-1")
        // Non-SENT messages with lower and higher timestamps
        val mSendingLow = createMessage("m0", timestamp = 10L, deliveryStatus = MessageDeliveryStatus.SENDING)
        val mSent1 = createMessage("m1", timestamp = 100L, deliveryStatus = MessageDeliveryStatus.SENT)
        val mSent2 = createMessage("m2", timestamp = 200L, deliveryStatus = MessageDeliveryStatus.SENT)
        val mFailedHigh = createMessage("m3", timestamp = 500L, deliveryStatus = MessageDeliveryStatus.FAILED)
        val mDeletedHigh = createMessage("m4", timestamp = 600L, deliveryStatus = MessageDeliveryStatus.DELETED)

        chatDao.upsertMessages(listOf(mSendingLow, mSent1, mSent2, mFailedHigh, mDeletedHigh))

        assertEquals(100L, chatDao.getOldestSentMessageTimestamp("room-1"))
        assertEquals(200L, chatDao.getNewestSentMessageTimestamp("room-1"))
    }

    @Test
    fun getChatsWithDetails_ordersRoomsByLastActivityAtDesc() = runTest {
        createParticipant("u1")
        val r1 = createRoom("r1", creatorId = "u1", lastActivityAt = 1000L)
        val r2 = createRoom("r2", creatorId = "u1", lastActivityAt = 3000L)
        val r3 = createRoom("r3", creatorId = "u1", lastActivityAt = 2000L)

        chatDao.getChatsWithDetails().test {
            val rooms = awaitItem()
            assertEquals(3, rooms.size)
            assertEquals("r2", rooms[0].room.id)
            assertEquals("r3", rooms[1].room.id)
            assertEquals("r1", rooms[2].room.id)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
