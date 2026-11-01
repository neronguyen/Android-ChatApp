package io.github.neronguyen.chat.feature.chat.presentation.list

import io.github.neronguyen.chat.core.model.ChatMessage
import io.github.neronguyen.chat.core.model.ChatParticipant
import io.github.neronguyen.chat.core.model.ChatRoom
import io.github.neronguyen.chat.core.model.ChatRoomType
import io.github.neronguyen.chat.core.model.MessageDeliveryStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Instant

internal const val FALLBACK_DIRECT_TITLE = "Direct chat"
internal const val FALLBACK_GROUP_TITLE = "Group chat"
internal const val FALLBACK_UNKNOWN_TITLE = "Conversation"
internal const val FALLBACK_UNKNOWN_PARTICIPANT = "Unknown participant"
internal const val YESTERDAY_LABEL = "Yesterday"

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val MONTH_DAY_FORMATTER = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val FULL_DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

fun List<ChatRoom>.toChatRoomUiModels(
    currentUserId: String?,
    now: Instant = Clock.System.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<ChatRoomUiModel> {
    return map { room ->
        room.toChatRoomUiModel(
            currentUserId = currentUserId,
            now = now,
            zoneId = zoneId,
        )
    }.sortedWith(
        compareByDescending<ChatRoomUiModel> { it.lastActivityAt }
            .thenBy { it.title.lowercase(Locale.ROOT) }
    )
}

fun ChatRoom.toChatRoomUiModel(
    currentUserId: String?,
    now: Instant = Clock.System.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): ChatRoomUiModel {
    val allParticipants = deduplicatedParticipants()
    val otherParticipants = if (currentUserId != null) {
        allParticipants.filter { it.userId != currentUserId }
    } else {
        allParticipants
    }

    val resolvedTitle = resolveTitle(otherParticipants)
    val avatarUrl = resolveAvatarUrl(otherParticipants)
    val initials = deriveInitials(resolvedTitle)
    val preview = resolvePreview(
        lastMessage = lastMessage,
        roomType = type,
        currentUserId = currentUserId,
        allParticipants = allParticipants,
    )
    val effectiveActivityAt = lastActivityAt ?: lastMessage?.timestamp
    val formattedTime = formatActivityTime(
        activityInstant = effectiveActivityAt,
        now = now,
        zoneId = zoneId,
    )

    val participantTerms = otherParticipants.flatMap { participant ->
        listOf(participant.displayName, participant.email)
    }
    val searchableText = buildList {
        add(resolvedTitle)
        add(preview.plainText)
        addAll(participantTerms)
    }.filter { it.isNotBlank() }
        .joinToString(separator = " ")
        .lowercase(Locale.ROOT)

    return ChatRoomUiModel(
        id = id,
        type = type,
        title = resolvedTitle,
        initials = initials,
        avatarUrl = avatarUrl,
        preview = preview,
        formattedTime = formattedTime,
        lastActivityAt = effectiveActivityAt,
        searchableText = searchableText,
    )
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

private fun ChatRoom.resolveTitle(otherParticipants: List<ChatParticipant>): String {
    return when (type) {
        ChatRoomType.DIRECT -> {
            val other = otherParticipants.firstOrNull()
            other?.displayName?.trim()?.takeIf { it.isNotEmpty() }
                ?: other?.email?.trim()?.takeIf { it.isNotEmpty() }
                ?: FALLBACK_DIRECT_TITLE
        }

        ChatRoomType.GROUP -> {
            val explicitName = name?.trim()?.takeIf { it.isNotEmpty() }
            if (explicitName != null) {
                explicitName
            } else {
                val participantLabels = otherParticipants.mapNotNull { participant ->
                    participant.displayName.trim().takeIf { it.isNotEmpty() }
                        ?: participant.email.trim().takeIf { it.isNotEmpty() }
                }
                if (participantLabels.isNotEmpty()) {
                    participantLabels.joinToString(separator = ", ")
                } else {
                    FALLBACK_GROUP_TITLE
                }
            }
        }

        ChatRoomType.UNKNOWN -> {
            name?.trim()?.takeIf { it.isNotEmpty() } ?: FALLBACK_UNKNOWN_TITLE
        }
    }
}

private fun ChatRoom.resolveAvatarUrl(otherParticipants: List<ChatParticipant>): String? {
    return when (type) {
        ChatRoomType.DIRECT -> {
            otherParticipants.firstOrNull()?.profilePictureUrl?.trim()?.takeIf { it.isNotEmpty() }
        }

        ChatRoomType.GROUP,
        ChatRoomType.UNKNOWN -> null
    }
}

internal fun deriveInitials(title: String): String {
    val words = title.trim()
        .split(Regex("[\\s,@._-]+"))
        .mapNotNull { token -> token.firstOrNull { it.isLetterOrDigit() } }
    return when {
        words.size >= 2 -> "${words[0].uppercaseChar()}${words[1].uppercaseChar()}"
        words.size == 1 -> {
            val alnum = title.trim().filter { it.isLetterOrDigit() }
            alnum.take(2).uppercase(Locale.ROOT)
        }

        else -> "?"
    }
}

private fun resolvePreview(
    lastMessage: ChatMessage?,
    roomType: ChatRoomType,
    currentUserId: String?,
    allParticipants: List<ChatParticipant>,
): ChatMessagePreviewUi {
    if (lastMessage == null) {
        return ChatMessagePreviewUi.NoMessages
    }
    if (lastMessage.deliveryStatus == MessageDeliveryStatus.DELETED) {
        return ChatMessagePreviewUi.Deleted
    }
    if (currentUserId != null && lastMessage.senderId == currentUserId) {
        return ChatMessagePreviewUi.Outgoing(content = lastMessage.content)
    }
    return when (roomType) {
        ChatRoomType.GROUP -> {
            val sender = allParticipants.firstOrNull { it.userId == lastMessage.senderId }
            val senderName = sender?.displayName?.trim()?.takeIf { it.isNotEmpty() }
                ?: sender?.email?.trim()?.takeIf { it.isNotEmpty() }
                ?: FALLBACK_UNKNOWN_PARTICIPANT
            ChatMessagePreviewUi.IncomingGroup(
                senderName = senderName,
                content = lastMessage.content,
            )
        }

        ChatRoomType.DIRECT,
        ChatRoomType.UNKNOWN -> {
            ChatMessagePreviewUi.IncomingDirect(content = lastMessage.content)
        }
    }
}

internal fun formatActivityTime(
    activityInstant: Instant?,
    now: Instant,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    if (activityInstant == null) return ""

    val activityZoned = java.time.Instant.ofEpochMilli(activityInstant.toEpochMilliseconds())
        .atZone(zoneId)
    val nowZoned = java.time.Instant.ofEpochMilli(now.toEpochMilliseconds())
        .atZone(zoneId)

    val activityDate = activityZoned.toLocalDate()
    val todayDate = nowZoned.toLocalDate()

    return when {
        activityDate == todayDate -> TIME_FORMATTER.format(activityZoned)
        activityDate == todayDate.minusDays(1) -> YESTERDAY_LABEL
        activityDate.year == todayDate.year -> MONTH_DAY_FORMATTER.format(activityZoned)
        else -> FULL_DATE_FORMATTER.format(activityZoned)
    }
}
