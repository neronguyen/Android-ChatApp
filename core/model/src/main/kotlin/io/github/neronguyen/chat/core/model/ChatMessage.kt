package io.github.neronguyen.chat.core.model

import kotlin.time.Instant

data class ChatMessage(
    val id: String,
    val roomId: String,
    val senderId: String,
    val content: String,
    val timestamp: Instant,
    val deliveryStatus: MessageDeliveryStatus,
    val deletedAt: Instant? = null
)
