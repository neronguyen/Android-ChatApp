package io.github.neronguyen.chat.feature.auth.presentation.forgot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.neronguyen.chat.core.data.repository.AuthRepository
import io.github.neronguyen.chat.core.model.error.AuthError
import io.github.neronguyenvn.nerochat.contract.validation.EmailValidator
import io.github.neronguyenvn.nerochat.contract.validation.auth.AuthValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    fun onEvent(event: ForgotPasswordUiEvent) {
        when (event) {
            is ForgotPasswordUiEvent.EmailChanged -> onEmailChanged(event.email)
            ForgotPasswordUiEvent.Submit -> submit()
        }
    }

    fun onForgotPasswordSuccessHandled() {
        _uiState.update { it.copy(isSuccess = false) }
    }

    private fun onEmailChanged(email: String) {
        _uiState.update {
            it.copy(
                email = email,
                validationError = null,
                authError = null,
            )
        }
    }

    private fun submit() {
        if (_uiState.value.isLoading) return

        val rawEmail = _uiState.value.email.trim()
        val validationError = AuthValidator.validateEmail(rawEmail)

        if (validationError != null) {
            _uiState.update {
                it.copy(
                    validationError = validationError,
                    authError = null,
                )
            }
            return
        }

        val normalizedEmail = EmailValidator.normalize(rawEmail)

        _uiState.update {
            it.copy(
                isLoading = true,
                validationError = null,
                authError = null,
            )
        }

        viewModelScope.launch {
            authRepository.forgotPassword(normalizedEmail)
                .onLeft { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            authError = AuthError.Data(error),
                        )
                    }
                }
                .onRight {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isSuccess = true,
                            submittedEmail = normalizedEmail,
                        )
                    }
                }
        }
    }
}
