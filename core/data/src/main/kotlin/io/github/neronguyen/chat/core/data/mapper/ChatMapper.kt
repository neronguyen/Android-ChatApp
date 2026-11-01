package io.github.neronguyen.chat.core.data.mapper

import io.github.neronguyen.chat.core.database.entity.ChatMessageEntity
import io.github.neronguyen.chat.core.database.entity.ChatParticipantEntity
import io.github.neronguyen.chat.core.database.entity.ChatRoomEntity
import io.github.neronguyen.chat.core.database.model.ChatRoomWithDetails
import io.github.neronguyen.chat.core.model.ChatMessage
import io.github.neronguyen.chat.core.model.ChatParticipant
import io.github.neronguyen.chat.core.model.ChatRoom
import io.github.neronguyen.chat.core.model.ChatRoomType
import io.github.neronguyen.chat.core.model.MessageDeliveryStatus
import io.github.neronguyen.chat.core.model.RealtimeConnectionState
import io.github.neronguyen.chat.core.model.RealtimeConnectionState.CONNECTED
import io.github.neronguyen.chat.core.model.RealtimeConnectionState.CONNECTING
import io.github.neronguyen.chat.core.model.RealtimeConnectionState.DISCONNECTED
import io.github.neronguyen.chat.core.model.RealtimeConnectionState.FAILED
import io.github.neronguyen.chat.core.network.model.websocket.SocketConnectionState
import io.github.neronguyen.chat.core.network.model.websocket.SocketConnectionState.Connected
import io.github.neronguyen.chat.core.network.model.websocket.SocketConnectionState.Connecting
import io.github.neronguyen.chat.core.network.model.websocket.SocketConnectionState.Disconnected
import io.github.neronguyen.chat.core.network.model.websocket.SocketConnectionState.Failed
import io.github.neronguyenvn.nerochat.contract.chat.ChatMessageDto
import io.github.neronguyenvn.nerochat.contract.chat.ChatParticipantDto
import io.github.neronguyenvn.nerochat.contract.chat.ChatRoomDto
import io.github.neronguyenvn.nerochat.contract.chat.ChatRoomType as ContractChatRoomType
import kotlin.time.Instant

fun ChatParticipantDto.toDomain(): ChatParticipant {
    return ChatParticipant(
        userId = userId,
        email = email,
        displayName = displayName,
        profilePictureUrl = profilePictureUrl
    )
}

fun ChatParticipantDto.toEntity(): ChatParticipantEntity {
    return ChatParticipantEntity(
        userId = userId,
        email = email,
        displayName = displayName,
        profilePictureUrl = profilePictureUrl
    )
}

fun ChatParticipantEntity.toDomain(): ChatParticipant {
    return ChatParticipant(
        userId = userId,
        email = email,
        displayName = displayName,
        profilePictureUrl = profilePictureUrl
    )
}

fun ChatMessageDto.toDomain(
    status: MessageDeliveryStatus = MessageDeliveryStatus.SENT
): ChatMessage {
    return ChatMessage(
        id = id,
        roomId = roomId,
        senderId = senderId,
        content = content,
        timestamp = createdAt,
        deliveryStatus = status
    )
}

fun ChatMessageDto.toEntity(
    status: MessageDeliveryStatus = MessageDeliveryStatus.SENT
): ChatMessageEntity {
    return ChatMessageEntity(
        id = id,
        roomId = roomId,
        senderId = senderId,
        content = content,
        timestamp = createdAt.toEpochMilliseconds(),
        deliveryStatus = status
    )
}

fun ChatMessageEntity.toDomain(): ChatMessage {
    return ChatMessage(
        id = id,
        roomId = roomId,
        senderId = senderId,
        content = content,
        timestamp = Instant.fromEpochMilliseconds(timestamp),
        deliveryStatus = deliveryStatus
    )
}

fun ChatMessage.toEntity(): ChatMessageEntity {
    return ChatMessageEntity(
        id = id,
        roomId = roomId,
        senderId = senderId,
        content = content,
        timestamp = timestamp.toEpochMilliseconds(),
        deliveryStatus = deliveryStatus
    )
}

fun ContractChatRoomType.toDomain(): ChatRoomType = when (this) {
    ContractChatRoomType.DIRECT -> ChatRoomType.DIRECT
    ContractChatRoomType.GROUP -> ChatRoomType.GROUP
}

fun ContractChatRoomType.toEntity(): ChatRoomType = toDomain()

fun ChatRoomDto.toDomain(): ChatRoom {
    return ChatRoom(
        id = id,
        type = type.toDomain(),
        name = name,
        creator = creator.toDomain(),
        participants = participants.map { it.toDomain() },
        lastMessage = lastMessage?.toDomain(),
        lastActivityAt = lastActivityAt
    )
}

fun ChatRoomDto.toEntity(): ChatRoomEntity {
    return ChatRoomEntity(
        id = id,
        type = type.toEntity(),
        name = name,
        creatorId = creator.userId,
        lastMessageId = lastMessage?.id,
        lastActivityAt = lastActivityAt.toEpochMilliseconds()
    )
}

fun ChatRoomWithDetails.toDomain(): ChatRoom {
    val creatorParticipant = creator?.toDomain()
        ?: ChatParticipant(userId = room.creatorId, email = "", displayName = "")
    return ChatRoom(
        id = room.id,
        type = room.type,
        name = room.name,
        creator = creatorParticipant,
        participants = participants.map { it.toDomain() },
        lastMessage = lastMessage?.toDomain(),
        lastActivityAt = room.lastActivityAt?.let { Instant.fromEpochMilliseconds(it) }
    )
}

fun SocketConnectionState.toDomain(): RealtimeConnectionState = when (this) {
    is Connecting -> CONNECTING
    is Connected -> CONNECTED
    is Disconnected -> DISCONNECTED
    is Failed -> FAILED
}

