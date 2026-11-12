package io.github.neronguyen.chat.feature.auth.presentation.reset

import io.github.neronguyen.chat.core.model.error.AuthError
import io.github.neronguyenvn.nerochat.contract.validation.auth.AuthValidationError

data class ResetPasswordUiState(
    val email: String,
    val code: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val isResending: Boolean = false,
    val resendCooldownSeconds: Int = 0,
    val validationError: AuthValidationError.ResetPassword? = null,
    val passwordsMismatch: Boolean = false,
    val authError: AuthError.ResetPassword? = null,
    val infoMessage: String? = null,
    val isSuccess: Boolean = false,
)
