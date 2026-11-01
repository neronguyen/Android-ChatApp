package io.github.neronguyen.chat.feature.chat.presentation.account

sealed interface AccountUiEvent {
    data object RequestSignOut : AccountUiEvent
    data object ConfirmSignOut : AccountUiEvent
    data object DismissSignOutConfirmation : AccountUiEvent
    data object DismissError : AccountUiEvent
}
