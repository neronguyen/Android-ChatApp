package io.github.neronguyen.chat.feature.chat.presentation.conversation

sealed interface ConversationUiEvent {
    data object SendMessage : ConversationUiEvent
    data class RetryMessage(val messageId: String) : ConversationUiEvent
    data class DeleteMessage(val messageId: String) : ConversationUiEvent
    data object LoadOlderMessages : ConversationUiEvent
    data object RetrySync : ConversationUiEvent
    data object DismissBanner : ConversationUiEvent
    data object ClearActionError : ConversationUiEvent
    data object LeaveRoom : ConversationUiEvent

    data object OpenAddParticipantDialog : ConversationUiEvent
    data object DismissAddParticipantDialog : ConversationUiEvent
    data object SearchParticipantByEmail : ConversationUiEvent
    data object ClearAddParticipantSearch : ConversationUiEvent
    data object ConfirmAddParticipant : ConversationUiEvent
}
