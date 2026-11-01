package io.github.neronguyen.chat.feature.chat.presentation.newchat

sealed interface NewChatUiEvent {
    data class SelectMode(val mode: NewChatMode) : NewChatUiEvent
    data object SearchByEmail : NewChatUiEvent
    data object ClearSearchInput : NewChatUiEvent
    data object ClearSearchResult : NewChatUiEvent
    data object ToggleSearchedParticipant : NewChatUiEvent
    data object ClearDirectParticipant : NewChatUiEvent
    data class RemoveGroupParticipant(val userId: String) : NewChatUiEvent
    data object SubmitChat : NewChatUiEvent
    data object DismissCreateError : NewChatUiEvent
}
