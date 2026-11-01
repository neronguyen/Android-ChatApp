package io.github.neronguyen.chat.core.data.mapper

import io.github.neronguyen.chat.core.database.entity.ChatMessageEntity
import io.github.neronguyen.chat.core.database.entity.ChatParticipantEntity
import io.github.neronguyen.chat.core.database.entity.ChatRoomEntity
import io.github.neronguyen.chat.core.database.model.ChatRoomWithDetails
import io.github.neronguyen.chat.core.model.ChatRoomType
import io.github.neronguyen.chat.core.model.MessageDeliveryStatus
import io.github.neronguyenvn.nerochat.contract.chat.ChatMessageDto
import io.github.neronguyenvn.nerochat.contract.chat.ChatParticipantDto
import io.github.neronguyenvn.nerochat.contract.chat.ChatRoomDto
import io.github.neronguyenvn.nerochat.contract.chat.ChatRoomType as ContractChatRoomType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ChatMapperTest {

    @Test
    fun `ChatMessageDto toDomain maps Instant createdAt to domain timestamp`() {
        val expectedInstant = Instant.parse("2025-01-01T12:00:00Z")

        val dto = ChatMessageDto(
            id = "msg1",
            roomId = "room1",
            senderId = "sender1",
            content = "Hello there",
            createdAt = expectedInstant
        )

        val domain = dto.toDomain()

        assertEquals("msg1", domain.id)
        assertEquals("room1", domain.roomId)
        assertEquals("sender1", domain.senderId)
        assertEquals("Hello there", domain.content)
        assertEquals(expectedInstant, domain.timestamp)
        assertEquals(MessageDeliveryStatus.SENT, domain.deliveryStatus)
    }

    @Test
    fun `ChatMessageDto toEntity maps Instant createdAt to epoch millis`() {
        val expectedInstant = Instant.parse("2025-01-01T12:00:00Z")
        val expectedMillis = expectedInstant.toEpochMilliseconds()

        val dto = ChatMessageDto(
            id = "msg3",
            roomId = "room1",
            senderId = "sender1",
            content = "Entity test",
            createdAt = expectedInstant
        )

        val entity = dto.toEntity(status = MessageDeliveryStatus.SENDING)

        assertEquals("msg3", entity.id)
        assertEquals("room1", entity.roomId)
        assertEquals("sender1", entity.senderId)
        assertEquals("Entity test", entity.content)
        assertEquals(expectedMillis, entity.timestamp)
        assertEquals(MessageDeliveryStatus.SENDING, entity.deliveryStatus)
    }

    @Test
    fun `ChatMessageEntity toDomain and ChatMessage toEntity roundtrip preserves accuracy`() {
        val timeMillis = 1735732800000L // 2025-01-01T12:00:00Z
        val entity = ChatMessageEntity(
            id = "msg5",
            roomId = "room2",
            senderId = "sender2",
            content = "Roundtrip message",
            timestamp = timeMillis,
            deliveryStatus = MessageDeliveryStatus.SENT
        )

        val domain = entity.toDomain()
        assertEquals("msg5", domain.id)
        assertEquals("room2", domain.roomId)
        assertEquals("sender2", domain.senderId)
        assertEquals("Roundtrip message", domain.content)
        assertEquals(Instant.fromEpochMilliseconds(timeMillis), domain.timestamp)
        assertEquals(MessageDeliveryStatus.SENT, domain.deliveryStatus)

        val convertedEntity = domain.toEntity()
        assertEquals("msg5", convertedEntity.id)
        assertEquals("room2", convertedEntity.roomId)
        assertEquals("sender2", convertedEntity.senderId)
        assertEquals("Roundtrip message", convertedEntity.content)
        assertEquals(timeMillis, convertedEntity.timestamp)
        assertEquals(MessageDeliveryStatus.SENT, convertedEntity.deliveryStatus)
    }

    @Test
    fun `ChatParticipant mappings map all fields between DTO Entity and Domain`() {
        val dto = ChatParticipantDto(
            userId = "user1",
            email = "user1@example.com",
            displayName = "User One",
            profilePictureUrl = "https://example.com/pic.png"
        )

        val domainFromDto = dto.toDomain()
        assertEquals("user1", domainFromDto.userId)
        assertEquals("user1@example.com", domainFromDto.email)
        assertEquals("User One", domainFromDto.displayName)
        assertEquals("https://example.com/pic.png", domainFromDto.profilePictureUrl)

        val entityFromDto = dto.toEntity()
        assertEquals("user1", entityFromDto.userId)
        assertEquals("user1@example.com", entityFromDto.email)
        assertEquals("User One", entityFromDto.displayName)
        assertEquals("https://example.com/pic.png", entityFromDto.profilePictureUrl)

        val domainFromEntity = entityFromDto.toDomain()
        assertEquals(domainFromDto, domainFromEntity)
    }

    @Test
    fun `ChatRoomDto toDomain and toEntity map nested relations and lastMessage`() {
        val activityTime = Instant.parse("2025-01-01T12:00:00Z")
        val participantDto = ChatParticipantDto(
            userId = "creator1",
            email = "creator@example.com",
            displayName = "Creator"
        )
        val lastMessageDto = ChatMessageDto(
            id = "lastMsg1",
            roomId = "room10",
            senderId = "creator1",
            content = "Last message content",
            createdAt = activityTime
        )
        val chatRoomDto = ChatRoomDto(
            id = "room10",
            type = ContractChatRoomType.GROUP,
            name = "Group Chat",
            creator = participantDto,
            participants = listOf(participantDto),
            lastMessage = lastMessageDto,
            lastActivityAt = activityTime
        )

        val domainRoom = chatRoomDto.toDomain()
        assertEquals("room10", domainRoom.id)
        assertEquals(ChatRoomType.GROUP, domainRoom.type)
        assertEquals("Group Chat", domainRoom.name)
        assertEquals("creator1", domainRoom.creator.userId)
        assertEquals(1, domainRoom.participants.size)
        assertEquals("lastMsg1", domainRoom.lastMessage?.id)
        assertEquals(activityTime, domainRoom.lastActivityAt)

        val entityRoom = chatRoomDto.toEntity()
        assertEquals("room10", entityRoom.id)
        assertEquals(ChatRoomType.GROUP, entityRoom.type)
        assertEquals("Group Chat", entityRoom.name)
        assertEquals("creator1", entityRoom.creatorId)
        assertEquals("lastMsg1", entityRoom.lastMessageId)
        assertEquals(activityTime.toEpochMilliseconds(), entityRoom.lastActivityAt)
    }

    @Test
    fun `ChatRoomDto toDomain and toEntity handle DIRECT and GROUP room types`() {
        val activityTime = Instant.fromEpochMilliseconds(123456789L)
        val participantDto = ChatParticipantDto(
            userId = "creator1",
            email = "creator@example.com",
            displayName = "Creator User",
            profilePictureUrl = null
        )
        val chatRoomDto = ChatRoomDto(
            id = "room_direct",
            type = ContractChatRoomType.DIRECT,
            name = "Direct Chat",
            creator = participantDto,
            participants = listOf(participantDto),
            lastMessage = null,
            lastActivityAt = activityTime
        )

        val domainRoom = chatRoomDto.toDomain()
        assertEquals("room_direct", domainRoom.id)
        assertEquals(ChatRoomType.DIRECT, domainRoom.type)

        val entityRoom = chatRoomDto.toEntity()
        assertEquals("room_direct", entityRoom.id)
        assertEquals(ChatRoomType.DIRECT, entityRoom.type)
    }

    @Test
    fun `ChatRoomWithDetails toDomain maps room creator participants and lastMessage`() {
        val activityTimeMillis = 123456789L
        val roomEntity = ChatRoomEntity(
            id = "room20",
            type = ChatRoomType.DIRECT,
            name = null,
            creatorId = "userA",
            lastMessageId = "msg20",
            lastActivityAt = activityTimeMillis
        )
        val creatorEntity = ChatParticipantEntity(
            userId = "userA",
            email = "usera@example.com",
            displayName = "User A",
            profilePictureUrl = null
        )
        val participantBEntity = ChatParticipantEntity(
            userId = "userB",
            email = "userb@example.com",
            displayName = "User B",
            profilePictureUrl = null
        )
        val lastMessageEntity = ChatMessageEntity(
            id = "msg20",
            roomId = "room20",
            senderId = "userA",
            content = "Hey User B",
            timestamp = activityTimeMillis,
            deliveryStatus = MessageDeliveryStatus.SENT
        )

        val roomWithDetails = ChatRoomWithDetails(
            room = roomEntity,
            creator = creatorEntity,
            participants = listOf(creatorEntity, participantBEntity),
            lastMessage = lastMessageEntity
        )

        val domain = roomWithDetails.toDomain()
        assertEquals("room20", domain.id)
        assertEquals(ChatRoomType.DIRECT, domain.type)
        assertNull(domain.name)
        assertEquals("userA", domain.creator.userId)
        assertEquals(2, domain.participants.size)
        assertNotNull(domain.lastMessage)
        assertEquals("msg20", domain.lastMessage?.id)
        assertEquals(Instant.fromEpochMilliseconds(activityTimeMillis), domain.lastActivityAt)
    }

    @Test
    fun `ChatRoomWithDetails toDomain handles null creator and lastMessage gracefully`() {
        val activityTimeMillis = 987654321L
        val roomEntity = ChatRoomEntity(
            id = "room21",
            type = ChatRoomType.DIRECT,
            name = null,
            creatorId = "userFallback",
            lastMessageId = null,
            lastActivityAt = activityTimeMillis
        )

        val roomWithDetails = ChatRoomWithDetails(
            room = roomEntity,
            creator = null,
            participants = emptyList(),
            lastMessage = null
        )

        val domain = roomWithDetails.toDomain()
        assertEquals("room21", domain.id)
        assertEquals("userFallback", domain.creator.userId)
        assertTrue(domain.participants.isEmpty())
        assertNull(domain.lastMessage)
        assertEquals(Instant.fromEpochMilliseconds(activityTimeMillis), domain.lastActivityAt)
    }

    @Test
    fun `ChatRoomWithDetails toDomain maps null lastActivityAt correctly`() {
        val entity = ChatRoomEntity(
            id = "roomNullTime",
            type = ChatRoomType.DIRECT,
            name = null,
            creatorId = "creator1",
            lastMessageId = null,
            lastActivityAt = null
        )

        val roomWithDetails = ChatRoomWithDetails(
            room = entity,
            creator = null,
            participants = emptyList(),
            lastMessage = null
        )
        val domain = roomWithDetails.toDomain()
        assertNull(domain.lastActivityAt)
    }
}
