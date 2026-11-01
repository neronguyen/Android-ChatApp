package io.github.neronguyen.chat.feature.auth.presentation.register

import io.github.neronguyen.chat.core.model.error.AuthError
import io.github.neronguyenvn.nerochat.contract.validation.auth.AuthValidationError

data class RegisterUiState(
    val isLoading: Boolean = false,
    val validationError: AuthValidationError.Register? = null,
    val authError: AuthError.Register? = null,
    val isSuccess: Boolean = false,
)
