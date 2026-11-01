package io.github.neronguyen.chat.feature.chat.presentation.conversation

import io.github.neronguyen.chat.core.model.ChatMessage
import io.github.neronguyen.chat.core.model.ChatParticipant
import io.github.neronguyen.chat.core.model.ChatRoom
import io.github.neronguyen.chat.core.model.ChatRoomType
import io.github.neronguyen.chat.core.model.MessageDeliveryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Instant

class ConversationUiMapperTest {

    private val fixedZoneId = ZoneId.of("UTC")
    private val now = Instant.parse("2026-10-17T12:00:00Z")

    private val me = ChatParticipant("user-me", "me@example.com", "Me")
    private val elena = ChatParticipant(
        userId = "user-elena",
        email = "elena@example.com",
        displayName = "Elena Rostova",
        profilePictureUrl = "https://example.com/elena.png",
    )
    private val marcus = ChatParticipant(
        userId = "user-marcus",
        email = "marcus@example.com",
        displayName = "Marcus Vance",
    )

    @Test
    fun `mapConversationHeader for DIRECT room derives other participant details`() {
        val room = ChatRoom(
            id = "room-direct",
            type = ChatRoomType.DIRECT,
            creator = me,
            participants = listOf(elena),
        )

        val header = mapConversationHeader(room, currentUserId = "user-me")

        assertEquals("Elena Rostova", header.title)
        assertEquals("elena@example.com", header.subtitle)
        assertEquals("ER", header.initials)
        assertEquals("https://example.com/elena.png", header.avatarUrl)
        assertEquals(ChatRoomType.DIRECT, header.roomType)
    }

    @Test
    fun `mapConversationHeader for GROUP room derives name and participant count`() {
        val room = ChatRoom(
            id = "room-group",
            type = ChatRoomType.GROUP,
            name = "Backend Core Team",
            creator = me,
            participants = listOf(elena, marcus),
        )

        val header = mapConversationHeader(room, currentUserId = "user-me")

        assertEquals("Backend Core Team", header.title)
        assertEquals("3 members", header.subtitle)
        assertEquals("BC", header.initials)
        assertNull(header.avatarUrl)
        assertEquals(ChatRoomType.GROUP, header.roomType)
    }

    @Test
    fun `mapConversationHeader for null room returns fallbacks`() {
        val header = mapConversationHeader(null, currentUserId = "user-me")

        assertEquals("Conversation", header.title)
        assertEquals("", header.subtitle)
        assertEquals("?", header.initials)
        assertNull(header.avatarUrl)
        assertEquals(ChatRoomType.UNKNOWN, header.roomType)
    }

    @Test
    fun `mapConversationItems handles empty messages`() {
        val items = mapConversationItems(
            messages = emptyList(),
            currentUserId = "user-me",
            room = null,
            now = now,
            zoneId = fixedZoneId,
        )
        assertTrue(items.isEmpty())
    }

    @Test
    fun `mapConversationItems marks outgoing and incoming messages correctly`() {
        val messages = listOf(
            ChatMessage(
                id = "m2",
                roomId = "r1",
                senderId = "user-me",
                content = "Outgoing message",
                timestamp = Instant.parse("2026-10-17T11:00:00Z"),
                deliveryStatus = MessageDeliveryStatus.SENT,
            ),
            ChatMessage(
                id = "m1",
                roomId = "r1",
                senderId = "user-elena",
                content = "Incoming message",
                timestamp = Instant.parse("2026-10-17T10:00:00Z"),
                deliveryStatus = MessageDeliveryStatus.SENT,
            ),
        )

        val room = ChatRoom(
            id = "r1",
            type = ChatRoomType.DIRECT,
            creator = me,
            participants = listOf(elena),
        )

        val items = mapConversationItems(
            messages = messages,
            currentUserId = "user-me",
            room = room,
            now = now,
            zoneId = fixedZoneId,
        )

        // Result should contain Message m2, Message m1, and DateHeader Today
        val messageItems = items.filterIsInstance<ConversationListItem.MessageItem>()
        assertEquals(2, messageItems.size)

        val m2Ui = messageItems[0].message
        assertTrue(m2Ui.isOutgoing)
        assertEquals("Outgoing message", m2Ui.content)
        assertEquals(MessageDeliveryStatus.SENT, m2Ui.deliveryStatus)

        val m1Ui = messageItems[1].message
        assertFalse(m1Ui.isOutgoing)
        assertEquals("Incoming message", m1Ui.content)

        // DateHeader for Today should be inserted after m1 (visually above m1 in reverseLayout)
        val dateHeader = items.filterIsInstance<ConversationListItem.DateHeader>().firstOrNull()
        assertEquals(TODAY_LABEL, dateHeader?.label)
    }

    @Test
    fun `mapConversationItems clusters group messages and assigns sender headers`() {
        // Reversed order (newest first): m3 (Marcus), m2 (Marcus), m1 (Elena)
        val messages = listOf(
            ChatMessage(
                id = "m3",
                roomId = "r-group",
                senderId = "user-marcus",
                content = "Marcus second message",
                timestamp = Instant.parse("2026-10-17T10:05:00Z"),
                deliveryStatus = MessageDeliveryStatus.SENT,
            ),
            ChatMessage(
                id = "m2",
                roomId = "r-group",
                senderId = "user-marcus",
                content = "Marcus first message",
                timestamp = Instant.parse("2026-10-17T10:00:00Z"),
                deliveryStatus = MessageDeliveryStatus.SENT,
            ),
            ChatMessage(
                id = "m1",
                roomId = "r-group",
                senderId = "user-elena",
                content = "Elena message",
                timestamp = Instant.parse("2026-10-17T09:00:00Z"),
                deliveryStatus = MessageDeliveryStatus.SENT,
            ),
        )

        val room = ChatRoom(
            id = "r-group",
            type = ChatRoomType.GROUP,
            name = "Group",
            creator = me,
            participants = listOf(elena, marcus),
        )

        val items = mapConversationItems(
            messages = messages,
            currentUserId = "user-me",
            room = room,
            now = now,
            zoneId = fixedZoneId,
        )

        val messageItems = items.filterIsInstance<ConversationListItem.MessageItem>()

        val m3Ui = messageItems[0].message // newest Marcus message
        assertFalse(m3Ui.showSenderHeader) // m2 is above it chronologically from same sender
        assertTrue(m3Ui.showSenderAvatar) // bottom of Marcus streak

        val m2Ui = messageItems[1].message // oldest Marcus message
        assertTrue(m2Ui.showSenderHeader) // start of Marcus streak! (m1 is from Elena)
        assertFalse(m2Ui.showSenderAvatar) // not at bottom of streak

        val m1Ui = messageItems[2].message // Elena message
        assertTrue(m1Ui.showSenderHeader)
        assertTrue(m1Ui.showSenderAvatar)
        assertEquals("Elena Rostova", m1Ui.senderName)
    }

    @Test
    fun `mapConversationItems separates different calendar days with date headers`() {
        val messages = listOf(
            ChatMessage(
                id = "m2",
                roomId = "r1",
                senderId = "user-me",
                content = "Today message",
                timestamp = Instant.parse("2026-10-17T10:00:00Z"), // Today
                deliveryStatus = MessageDeliveryStatus.SENT,
            ),
            ChatMessage(
                id = "m1",
                roomId = "r1",
                senderId = "user-elena",
                content = "Yesterday message",
                timestamp = Instant.parse("2026-10-16T10:00:00Z"), // Yesterday
                deliveryStatus = MessageDeliveryStatus.SENT,
            ),
        )

        val room = ChatRoom(
            id = "r1",
            type = ChatRoomType.DIRECT,
            creator = me,
            participants = listOf(elena),
        )

        val items = mapConversationItems(
            messages = messages,
            currentUserId = "user-me",
            room = room,
            now = now,
            zoneId = fixedZoneId,
        )

        val headers = items.filterIsInstance<ConversationListItem.DateHeader>()
        assertEquals(2, headers.size)
        assertEquals(TODAY_LABEL, headers[0].label)
        assertEquals(YESTERDAY_LABEL, headers[1].label)
    }

    @Test
    fun `formatDateHeader formats various dates correctly`() {
        val today = LocalDate.of(2026, 10, 17)

        assertEquals("Today", formatDateHeader(today, today))
        assertEquals("Yesterday", formatDateHeader(today.minusDays(1), today))
        assertEquals("Mar 12", formatDateHeader(LocalDate.of(2026, 3, 12), today))
        assertEquals("Mar 12, 2025", formatDateHeader(LocalDate.of(2025, 3, 12), today))
    }
}
