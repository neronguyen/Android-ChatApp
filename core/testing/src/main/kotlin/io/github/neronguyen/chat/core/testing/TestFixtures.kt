package io.github.neronguyen.chat.core.testing

import io.github.neronguyen.chat.core.model.ChatMessage
import io.github.neronguyen.chat.core.model.ChatParticipant
import io.github.neronguyen.chat.core.model.ChatRoom
import io.github.neronguyen.chat.core.model.ChatRoomType
import io.github.neronguyen.chat.core.model.MessageDeliveryStatus
import io.github.neronguyen.chat.core.model.User
import kotlin.time.Instant

object TestFixtures {
    val defaultTimestamp: Instant = Instant.parse("2026-11-16T12:00:00Z")

    fun user(
        id: String = "user-me",
        email: String = "alice@example.com",
        displayName: String = "Alice",
        isEmailVerified: Boolean = true,
    ): User = User(
        id = id,
        email = email,
        displayName = displayName,
        isEmailVerified = isEmailVerified,
    )

    fun participant(
        userId: String = "user-me",
        email: String = "alice@example.com",
        displayName: String = "Alice",
        profilePictureUrl: String? = null,
    ): ChatParticipant = ChatParticipant(
        userId = userId,
        email = email,
        displayName = displayName,
        profilePictureUrl = profilePictureUrl,
    )

    fun directRoom(
        id: String = "room-1",
        creator: ChatParticipant = participant(),
        peer: ChatParticipant = participant(
            userId = "user-bob",
            email = "bob@example.com",
            displayName = "Bob",
        ),
        lastMessage: ChatMessage? = null,
        lastActivityAt: Instant? = defaultTimestamp,
    ): ChatRoom = ChatRoom(
        id = id,
        type = ChatRoomType.DIRECT,
        creator = creator,
        participants = listOf(creator, peer),
        lastMessage = lastMessage,
        lastActivityAt = lastActivityAt,
    )

    fun groupRoom(
        id: String = "group-1",
        name: String? = "Engineering",
        creator: ChatParticipant = participant(),
        participants: List<ChatParticipant> = listOf(
            creator,
            participant(userId = "user-bob", email = "bob@example.com", displayName = "Bob"),
            participant(userId = "user-carol", email = "carol@example.com", displayName = "Carol"),
        ),
        lastMessage: ChatMessage? = null,
        lastActivityAt: Instant? = defaultTimestamp,
    ): ChatRoom = ChatRoom(
        id = id,
        type = ChatRoomType.GROUP,
        name = name,
        creator = creator,
        participants = participants,
        lastMessage = lastMessage,
        lastActivityAt = lastActivityAt,
    )

    fun message(
        id: String = "msg-1",
        roomId: String = "room-1",
        senderId: String = "user-me",
        content: String = "Hello",
        timestamp: Instant = defaultTimestamp,
        deliveryStatus: MessageDeliveryStatus = MessageDeliveryStatus.SENT,
    ): ChatMessage = ChatMessage(
        id = id,
        roomId = roomId,
        senderId = senderId,
        content = content,
        timestamp = timestamp,
        deliveryStatus = deliveryStatus,
    )
}
