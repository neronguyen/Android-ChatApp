package io.github.neronguyen.chat.feature.chat.presentation.list

import io.github.neronguyen.chat.core.model.ChatRoomType
import io.github.neronguyen.chat.core.model.error.DataError
import kotlin.time.Instant

data class ChatListUiState(
    val chats: List<ChatRoomUiModel> = emptyList(),
    val searchQuery: String = "",
    val isSyncing: Boolean = false,
    val isInitialLoadComplete: Boolean = false,
    val syncError: DataError? = null,
    val isBannerDismissed: Boolean = false,
) {
    val isInitialLoading: Boolean
        get() = isSyncing && !isInitialLoadComplete && chats.isEmpty()

    val showSyncBanner: Boolean
        get() = !isBannerDismissed && (isSyncing || syncError != null)
}

data class ChatRoomUiModel(
    val id: String,
    val type: ChatRoomType,
    val title: String,
    val initials: String,
    val avatarUrl: String?,
    val preview: ChatMessagePreviewUi,
    val formattedTime: String,
    val lastActivityAt: Instant?,
    val searchableText: String,
) {
    val previewText: String
        get() = preview.plainText

    val isPreviewItalic: Boolean
        get() = preview.isItalic
}

sealed interface ChatMessagePreviewUi {
    val plainText: String
    val isItalic: Boolean

    data object NoMessages : ChatMessagePreviewUi {
        override val plainText: String = "No messages yet"
        override val isItalic: Boolean = true
    }

    data object Deleted : ChatMessagePreviewUi {
        override val plainText: String = "Message deleted"
        override val isItalic: Boolean = true
    }

    data class Outgoing(val content: String) : ChatMessagePreviewUi {
        override val plainText: String = "You: $content"
        override val isItalic: Boolean = false
    }

    data class IncomingDirect(val content: String) : ChatMessagePreviewUi {
        override val plainText: String = content
        override val isItalic: Boolean = false
    }

    data class IncomingGroup(
        val senderName: String,
        val content: String,
    ) : ChatMessagePreviewUi {
        override val plainText: String = "$senderName: $content"
        override val isItalic: Boolean = false
    }
}
