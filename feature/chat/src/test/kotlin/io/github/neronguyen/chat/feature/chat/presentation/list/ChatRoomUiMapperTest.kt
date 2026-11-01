package io.github.neronguyen.chat.feature.chat.presentation.list

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
import java.time.ZoneId
import kotlin.time.Instant

class ChatRoomUiMapperTest {

    private val currentUserId = "user-me"
    private val currentUser = ChatParticipant(
        userId = currentUserId,
        email = "me@example.com",
        displayName = "Me",
    )
    private val elena = ChatParticipant(
        userId = "user-elena",
        email = "elena@example.com",
        displayName = "Elena Rostova",
        profilePictureUrl = "https://example.com/elena.png",
    )
    private val marcus = ChatParticipant(
        userId = "user-marcus",
        email = "marcus@example.com",
        displayName = "Marcus",
    )
    private val utcZone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-03-15T14:00:00Z")

    @Test
    fun `direct chat derives title and avatar from other participant`() {
        val room = ChatRoom(
            id = "room-1",
            type = ChatRoomType.DIRECT,
            name = null,
            creator = currentUser,
            participants = listOf(currentUser, elena),
            lastMessage = ChatMessage(
                id = "msg-1",
                roomId = "room-1",
                senderId = elena.userId,
                content = "The sync contracts are finalized.",
                timestamp = Instant.parse("2026-03-15T10:42:00Z"),
                deliveryStatus = MessageDeliveryStatus.SENT,
            ),
            lastActivityAt = Instant.parse("2026-03-15T10:42:00Z"),
        )

        val uiModel = room.toChatRoomUiModel(
            currentUserId = currentUserId,
            now = now,
            zoneId = utcZone,
        )

        assertEquals("Elena Rostova", uiModel.title)
        assertEquals("ER", uiModel.initials)
        assertEquals("https://example.com/elena.png", uiModel.avatarUrl)
        assertEquals("The sync contracts are finalized.", uiModel.previewText)
        assertFalse(uiModel.isPreviewItalic)
        assertEquals("10:42 AM", uiModel.formattedTime)
    }

    @Test
    fun `direct chat falls back to email then Direct chat when displayName is blank`() {
        val emailOnlyParticipant = ChatParticipant(
            userId = "user-2",
            email = "alex@example.com",
            displayName = "   ",
        )
        val roomWithEmail = ChatRoom(
            id = "room-email",
            type = ChatRoomType.DIRECT,
            creator = currentUser,
            participants = listOf(currentUser, emailOnlyParticipant),
        )

        val emailUiModel = roomWithEmail.toChatRoomUiModel(
            currentUserId = currentUserId,
            now = now,
            zoneId = utcZone,
        )
        assertEquals("alex@example.com", emailUiModel.title)

        val blankParticipant = ChatParticipant(
            userId = "user-3",
            email = "",
            displayName = "",
        )
        val roomWithFallback = ChatRoom(
            id = "room-fallback",
            type = ChatRoomType.DIRECT,
            creator = currentUser,
            participants = listOf(currentUser, blankParticipant),
        )

        val fallbackUiModel = roomWithFallback.toChatRoomUiModel(
            currentUserId = currentUserId,
            now = now,
            zoneId = utcZone,
        )
        assertEquals("Direct chat", fallbackUiModel.title)
    }

    @Test
    fun `group chat uses explicit trimmed name or derives from participants`() {
        val namedGroup = ChatRoom(
            id = "room-group-1",
            type = ChatRoomType.GROUP,
            name = "  Backend Core Team  ",
            creator = currentUser,
            participants = listOf(currentUser, elena, marcus),
            lastMessage = ChatMessage(
                id = "msg-2",
                roomId = "room-group-1",
                senderId = marcus.userId,
                content = "Reviewed the message persistence layer.",
                timestamp = Instant.parse("2026-03-14T18:00:00Z"),
                deliveryStatus = MessageDeliveryStatus.SENT,
            ),
            lastActivityAt = Instant.parse("2026-03-14T18:00:00Z"),
        )

        val namedUi = namedGroup.toChatRoomUiModel(
            currentUserId = currentUserId,
            now = now,
            zoneId = utcZone,
        )
        assertEquals("Backend Core Team", namedUi.title)
        assertEquals("BC", namedUi.initials)
        assertNull(namedUi.avatarUrl)
        assertEquals("Marcus: Reviewed the message persistence layer.", namedUi.previewText)
        assertEquals("Yesterday", namedUi.formattedTime)

        val unnamedGroup = namedGroup.copy(name = "   ")
        val unnamedUi = unnamedGroup.toChatRoomUiModel(
            currentUserId = currentUserId,
            now = now,
            zoneId = utcZone,
        )
        assertEquals("Elena Rostova, Marcus", unnamedUi.title)
    }

    @Test
    fun `deleted, outgoing, and empty message states map with expected preview and italic flag`() {
        val deletedRoom = ChatRoom(
            id = "room-deleted",
            type = ChatRoomType.DIRECT,
            creator = currentUser,
            participants = listOf(currentUser, elena),
            lastMessage = ChatMessage(
                id = "msg-del",
                roomId = "room-deleted",
                senderId = elena.userId,
                content = "Secret",
                timestamp = Instant.parse("2026-03-12T09:00:00Z"),
                deliveryStatus = MessageDeliveryStatus.DELETED,
            ),
            lastActivityAt = Instant.parse("2026-03-12T09:00:00Z"),
        ).toChatRoomUiModel(currentUserId = currentUserId, now = now, zoneId = utcZone)

        assertEquals("Message deleted", deletedRoom.previewText)
        assertTrue(deletedRoom.isPreviewItalic)
        assertEquals("Mar 12", deletedRoom.formattedTime)

        val outgoingRoom = ChatRoom(
            id = "room-outgoing",
            type = ChatRoomType.DIRECT,
            creator = currentUser,
            participants = listOf(currentUser, elena),
            lastMessage = ChatMessage(
                id = "msg-out",
                roomId = "room-outgoing",
                senderId = currentUserId,
                content = "Let me know when you test login.",
                timestamp = Instant.parse("2026-03-10T09:00:00Z"),
                deliveryStatus = MessageDeliveryStatus.SENT,
            ),
            lastActivityAt = Instant.parse("2026-03-10T09:00:00Z"),
        ).toChatRoomUiModel(currentUserId = currentUserId, now = now, zoneId = utcZone)

        assertEquals("You: Let me know when you test login.", outgoingRoom.previewText)
        assertFalse(outgoingRoom.isPreviewItalic)
        assertEquals("Mar 10", outgoingRoom.formattedTime)

        val emptyRoom = ChatRoom(
            id = "room-empty",
            type = ChatRoomType.GROUP,
            name = "Architecture sync",
            creator = currentUser,
            participants = listOf(currentUser, elena),
            lastMessage = null,
            lastActivityAt = null,
        ).toChatRoomUiModel(currentUserId = currentUserId, now = now, zoneId = utcZone)

        assertEquals("No messages yet", emptyRoom.previewText)
        assertTrue(emptyRoom.isPreviewItalic)
        assertEquals("", emptyRoom.formattedTime)
    }

    @Test
    fun `unresolved group sender uses Unknown participant fallback and never exposes raw id`() {
        val groupRoom = ChatRoom(
            id = "room-unknown-sender",
            type = ChatRoomType.GROUP,
            name = "Ops",
            creator = currentUser,
            participants = listOf(currentUser),
            lastMessage = ChatMessage(
                id = "msg-1",
                roomId = "room-unknown-sender",
                senderId = "raw-uuid-999",
                content = "Hello team",
                timestamp = now,
                deliveryStatus = MessageDeliveryStatus.SENT,
            ),
            lastActivityAt = now,
        ).toChatRoomUiModel(currentUserId = currentUserId, now = now, zoneId = utcZone)

        assertEquals("Unknown participant: Hello team", groupRoom.previewText)
    }
}
