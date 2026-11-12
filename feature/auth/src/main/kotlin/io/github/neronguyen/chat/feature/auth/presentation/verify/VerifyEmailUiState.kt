package io.github.neronguyen.chat.feature.auth.presentation.verify

import io.github.neronguyen.chat.core.model.error.AuthError
import io.github.neronguyenvn.nerochat.contract.validation.auth.AuthValidationError

data class VerifyEmailUiState(
    val email: String,
    val code: String = "",
    val isLoading: Boolean = false,
    val isResending: Boolean = false,
    val resendCooldownSeconds: Int = 0,
    val validationError: AuthValidationError.VerifyEmail? = null,
    val authError: AuthError.VerifyEmail? = null,
    val infoMessage: String? = null,
    val isSuccess: Boolean = false,
)
