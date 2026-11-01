package io.github.neronguyen.chat.feature.chat.presentation.conversation

import androidx.compose.runtime.Immutable
import io.github.neronguyen.chat.core.model.ChatRoomType
import io.github.neronguyen.chat.core.model.MessageDeliveryStatus
import io.github.neronguyen.chat.core.model.RealtimeConnectionState
import io.github.neronguyen.chat.core.model.error.ChatError
import io.github.neronguyen.chat.core.model.error.DataError
import io.github.neronguyen.chat.feature.chat.presentation.newchat.ParticipantUiModel
import io.github.neronguyenvn.nerochat.contract.validation.chat.ChatValidationError

@Immutable
data class ConversationUiState(
    val roomId: String,
    val roomType: ChatRoomType = ChatRoomType.UNKNOWN,
    val title: String = "",
    val subtitle: String = "",
    val initials: String = "?",
    val avatarUrl: String? = null,
    val items: List<ConversationListItem> = emptyList(),
    val connectionState: RealtimeConnectionState = RealtimeConnectionState.CONNECTED,
    val isInitialLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val syncError: ChatError.SyncRoom? = null,
    val isLoadingOlder: Boolean = false,
    val hasMoreOlderMessages: Boolean = true,
    val loadOlderError: DataError? = null,
    val actionError: ChatError? = null,
    val isLeavingRoom: Boolean = false,
    val hasLeftRoom: Boolean = false,
    val isBannerDismissed: Boolean = false,
    val addParticipantDialogState: AddParticipantDialogState? = null,
) {
    val showConnectionBanner: Boolean
        get() = !isBannerDismissed && (
                connectionState != RealtimeConnectionState.CONNECTED ||
                        syncError != null ||
                        loadOlderError != null
                )

    val isEmptyState: Boolean
        get() = !isInitialLoading && items.none { it is ConversationListItem.MessageItem }
}

@Immutable
sealed interface ConversationListItem {
    val id: String

    data class MessageItem(val message: MessageUiModel) : ConversationListItem {
        override val id: String = message.id
    }

    data class DateHeader(val headerId: String, val label: String) : ConversationListItem {
        override val id: String = headerId
    }
}

@Immutable
data class MessageUiModel(
    val id: String,
    val roomId: String,
    val content: String,
    val formattedTime: String,
    val isOutgoing: Boolean,
    val deliveryStatus: MessageDeliveryStatus,
    val senderId: String,
    val senderName: String? = null,
    val senderInitials: String? = null,
    val senderAvatarUrl: String? = null,
    val showSenderHeader: Boolean = false,
    val showSenderAvatar: Boolean = false,
)

@Immutable
data class AddParticipantDialogState(
    val isSearching: Boolean = false,
    val isSubmitting: Boolean = false,
    val searchedParticipant: ParticipantUiModel? = null,
    val validationError: ChatValidationError.SearchParticipant? = null,
    val searchError: ChatError.FindParticipant? = null,
    val submitError: ChatError.AddParticipants? = null,
)
