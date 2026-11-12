package io.github.neronguyen.chat.feature.auth.presentation.reset

sealed interface ResetPasswordUiEvent {
    data class CodeChanged(val code: String) : ResetPasswordUiEvent
    data class NewPasswordChanged(val password: String) : ResetPasswordUiEvent
    data class ConfirmPasswordChanged(val password: String) : ResetPasswordUiEvent
    data object Reset : ResetPasswordUiEvent
    data object ResendCode : ResetPasswordUiEvent
    data object DismissInfo : ResetPasswordUiEvent
}
