package io.github.neronguyen.chat.feature.chat.presentation.list

sealed interface ChatListUiEvent {
    data object RefreshChats : ChatListUiEvent
    data object DismissSyncBanner : ChatListUiEvent
    data object ClearSearch : ChatListUiEvent
}
