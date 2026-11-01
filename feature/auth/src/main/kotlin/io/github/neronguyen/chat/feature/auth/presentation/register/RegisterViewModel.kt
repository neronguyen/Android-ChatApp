package io.github.neronguyen.chat.feature.auth.presentation.register

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.neronguyen.chat.core.data.repository.AuthRepository
import io.github.neronguyenvn.nerochat.contract.validation.EmailValidator
import io.github.neronguyenvn.nerochat.contract.validation.auth.AuthValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val emailState = TextFieldState()
    val displayNameState = TextFieldState()
    val passwordState = TextFieldState()

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onEvent(event: RegisterUiEvent) {
        when (event) {
            RegisterUiEvent.Register -> register()
        }
    }

    fun onRegisterSuccessHandled() {
        _uiState.update { it.copy(isSuccess = false) }
    }

    private fun register() {
        if (_uiState.value.isLoading) return

        val emailText = EmailValidator.normalize(emailState.text.toString())
        val displayNameText = displayNameState.text.toString().trim()
        val passwordText = passwordState.text.toString()

        val validationError = AuthValidator.validateRegister(
            email = emailText,
            displayName = displayNameText,
            password = passwordText,
        ).firstOrNull()

        if (validationError != null) {
            _uiState.update {
                it.copy(
                    validationError = validationError,
                    authError = null,
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                validationError = null,
                authError = null,
            )
        }

        viewModelScope.launch {
            authRepository.register(
                email = emailText,
                displayName = displayNameText,
                password = passwordText,
            )
                .onLeft { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            authError = error,
                        )
                    }
                }
                .onRight {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isSuccess = true,
                        )
                    }
                }
        }
    }
}
