package io.github.neronguyen.chat.feature.auth.presentation.login

import io.github.neronguyen.chat.core.model.error.AuthError
import io.github.neronguyenvn.nerochat.contract.validation.auth.AuthValidationError

data class LoginUiState(
    val isLoading: Boolean = false,
    val validationError: AuthValidationError.Login? = null,
    val authError: AuthError.Login? = null,
    val isSuccess: Boolean = false,
)
