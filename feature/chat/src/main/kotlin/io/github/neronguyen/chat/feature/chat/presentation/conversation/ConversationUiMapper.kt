package io.github.neronguyen.chat.feature.chat.presentation.conversation

import io.github.neronguyen.chat.core.model.ChatMessage
import io.github.neronguyen.chat.core.model.ChatParticipant
import io.github.neronguyen.chat.core.model.ChatRoom
import io.github.neronguyen.chat.core.model.ChatRoomType
import io.github.neronguyen.chat.feature.chat.presentation.list.FALLBACK_DIRECT_TITLE
import io.github.neronguyen.chat.feature.chat.presentation.list.FALLBACK_GROUP_TITLE
import io.github.neronguyen.chat.feature.chat.presentation.list.FALLBACK_UNKNOWN_PARTICIPANT
import io.github.neronguyen.chat.feature.chat.presentation.list.FALLBACK_UNKNOWN_TITLE
import io.github.neronguyen.chat.feature.chat.presentation.list.deriveInitials
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Instant

private val MESSAGE_TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val MONTH_DAY_FORMATTER = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val FULL_DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

internal const val TODAY_LABEL = "Today"
internal const val YESTERDAY_LABEL = "Yesterday"

data class ConversationHeaderUiModel(
    val title: String,
    val subtitle: String,
    val initials: String,
    val avatarUrl: String?,
    val roomType: ChatRoomType,
)

fun mapConversationHeader(
    room: ChatRoom?,
    currentUserId: String?,
): ConversationHeaderUiModel {
    if (room == null) {
        return ConversationHeaderUiModel(
            title = FALLBACK_UNKNOWN_TITLE,
            subtitle = "",
            initials = "?",
            avatarUrl = null,
            roomType = ChatRoomType.UNKNOWN,
        )
    }

    val allParticipants = room.deduplicatedParticipants()
    val otherParticipants = if (currentUserId != null) {
        allParticipants.filter { it.userId != currentUserId }
    } else {
        allParticipants
    }

    val title = when (room.type) {
        ChatRoomType.DIRECT -> {
            val other = otherParticipants.firstOrNull()
            other?.displayName?.trim()?.takeIf { it.isNotEmpty() }
                ?: other?.email?.trim()?.takeIf { it.isNotEmpty() }
                ?: FALLBACK_DIRECT_TITLE
        }

        ChatRoomType.GROUP -> {
            val explicitName = room.name?.trim()?.takeIf { it.isNotEmpty() }
            if (explicitName != null) {
                explicitName
            } else {
                val participantLabels = otherParticipants.mapNotNull { p ->
                    p.displayName.trim().takeIf { it.isNotEmpty() }
                        ?: p.email.trim().takeIf { it.isNotEmpty() }
                }
                if (participantLabels.isNotEmpty()) {
                    participantLabels.joinToString(", ")
                } else {
                    FALLBACK_GROUP_TITLE
                }
            }
        }

        ChatRoomType.UNKNOWN -> {
            room.name?.trim()?.takeIf { it.isNotEmpty() } ?: FALLBACK_UNKNOWN_TITLE
        }
    }

    val subtitle = when (room.type) {
        ChatRoomType.DIRECT -> {
            val other = otherParticipants.firstOrNull()
            if (other != null && other.displayName.isNotBlank() && other.email.isNotBlank()) {
                other.email
            } else {
                FALLBACK_DIRECT_TITLE
            }
        }

        ChatRoomType.GROUP -> {
            val totalCount = allParticipants.size
            if (totalCount == 1) "1 member" else "$totalCount members"
        }

        ChatRoomType.UNKNOWN -> ""
    }

    val avatarUrl = if (room.type == ChatRoomType.DIRECT) {
        otherParticipants.firstOrNull()?.profilePictureUrl?.trim()?.takeIf { it.isNotEmpty() }
    } else {
        null
    }

    return ConversationHeaderUiModel(
        title = title,
        subtitle = subtitle,
        initials = deriveInitials(title),
        avatarUrl = avatarUrl,
        roomType = room.type,
    )
}

fun mapConversationItems(
    messages: List<ChatMessage>,
    currentUserId: String?,
    room: ChatRoom?,
    now: Instant = Clock.System.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<ConversationListItem> {
    if (messages.isEmpty()) return emptyList()

    val participantsMap = room?.deduplicatedParticipants()?.associateBy { it.userId }.orEmpty()
    val isGroup = room?.type == ChatRoomType.GROUP

    val result = ArrayList<ConversationListItem>(messages.size + 8)

    for (i in messages.indices) {
        val current = messages[i]
        val isOutgoing = currentUserId != null && current.senderId == currentUserId

        val participant = participantsMap[current.senderId]
        val senderName = if (!isOutgoing) {
            participant?.displayName?.trim()?.takeIf { it.isNotEmpty() }
                ?: participant?.email?.trim()?.takeIf { it.isNotEmpty() }
                ?: FALLBACK_UNKNOWN_PARTICIPANT
        } else {
            null
        }
        val senderInitials = senderName?.let { deriveInitials(it) }
        val senderAvatarUrl = participant?.profilePictureUrl?.trim()?.takeIf { it.isNotEmpty() }

        // In reverseLayout (newest first):
        // The message visually above this message is at index i + 1 (an older message).
        // The message visually below this message is at index i - 1 (a newer message).
        val olderMessage = messages.getOrNull(i + 1)
        val newerMessage = messages.getOrNull(i - 1)

        val currentDate = current.timestamp.toLocalDate(zoneId)
        val olderDate = olderMessage?.timestamp?.toLocalDate(zoneId)

        // Show sender header at the top of a consecutive streak of incoming messages.
        // That means the message visually above it (olderMessage) is from a different sender or on a different date.
        val showSenderHeader = isGroup && !isOutgoing && (
                olderMessage == null ||
                        olderMessage.senderId != current.senderId ||
                        olderDate != currentDate
                )

        // Show sender avatar on the bottom-most message of a consecutive streak of incoming messages.
        // That means the message visually below it (newerMessage) is from a different sender or on a different date.
        val showSenderAvatar = isGroup && !isOutgoing && (
                newerMessage == null ||
                        newerMessage.senderId != current.senderId ||
                        newerMessage.timestamp.toLocalDate(zoneId) != currentDate
                )

        val formattedTime = current.timestamp.toFormattedTime(zoneId)

        result.add(
            ConversationListItem.MessageItem(
                message = MessageUiModel(
                    id = current.id,
                    roomId = current.roomId,
                    content = current.content,
                    formattedTime = formattedTime,
                    isOutgoing = isOutgoing,
                    deliveryStatus = current.deliveryStatus,
                    senderId = current.senderId,
                    senderName = senderName,
                    senderInitials = senderInitials,
                    senderAvatarUrl = senderAvatarUrl,
                    showSenderHeader = showSenderHeader,
                    showSenderAvatar = showSenderAvatar,
                ),
            )
        )

        // If this is the oldest message of its date (i.e. the next message is from an older date or end of list),
        // insert the DateHeader here (visually above this message in reverseLayout).
        if (olderDate != currentDate) {
            val dateLabel = formatDateHeader(currentDate, now.toLocalDate(zoneId))
            result.add(
                ConversationListItem.DateHeader(
                    headerId = "date_header_${currentDate}_$i",
                    label = dateLabel,
                )
            )
        }
    }

    return result
}

private fun ChatRoom.deduplicatedParticipants(): List<ChatParticipant> {
    val combined = participants + creator
    val byId = LinkedHashMap<String, ChatParticipant>()
    for (participant in combined) {
        val existing = byId[participant.userId]
        if (existing == null || (existing.displayName.isBlank() && existing.email.isBlank())) {
            byId[participant.userId] = participant
        }
    }
    return byId.values.toList()
}

private fun Instant.toLocalDate(zoneId: ZoneId): LocalDate {
    return java.time.Instant.ofEpochMilli(toEpochMilliseconds())
        .atZone(zoneId)
        .toLocalDate()
}

private fun Instant.toFormattedTime(zoneId: ZoneId): String {
    val zonedDateTime = java.time.Instant.ofEpochMilli(toEpochMilliseconds())
        .atZone(zoneId)
    return MESSAGE_TIME_FORMATTER.format(zonedDateTime)
}

internal fun formatDateHeader(date: LocalDate, today: LocalDate): String {
    return when {
        date == today -> TODAY_LABEL
        date == today.minusDays(1) -> YESTERDAY_LABEL
        date.year == today.year -> MONTH_DAY_FORMATTER.format(date)
        else -> FULL_DATE_FORMATTER.format(date)
    }
}
