package io.github.neronguyen.chat.feature.auth.presentation.login

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
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val emailState = TextFieldState()
    val passwordState = TextFieldState()

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEvent(event: LoginUiEvent) {
        when (event) {
            LoginUiEvent.Login -> login()
        }
    }

    fun onLoginSuccessHandled() {
        _uiState.update { it.copy(isSuccess = false) }
    }

    private fun login() {
        if (_uiState.value.isLoading) return

        val emailText = EmailValidator.normalize(emailState.text.toString())
        val passwordText = passwordState.text.toString()

        val validationError = AuthValidator.validateLogin(
            email = emailText,
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
            authRepository.login(
                email = emailText,
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
