package io.github.neronguyen.chat.feature.auth.presentation.forgot

sealed interface ForgotPasswordUiEvent {
    data class EmailChanged(val email: String) : ForgotPasswordUiEvent
    data object Submit : ForgotPasswordUiEvent
}
