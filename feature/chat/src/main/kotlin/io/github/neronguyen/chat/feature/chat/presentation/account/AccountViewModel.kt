package io.github.neronguyen.chat.feature.chat.presentation.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.neronguyen.chat.core.data.realtime.RealtimeConnectionManager
import io.github.neronguyen.chat.core.data.repository.AuthRepository
import io.github.neronguyen.chat.core.model.AuthState
import io.github.neronguyen.chat.core.model.error.DataError
import io.github.neronguyen.chat.feature.chat.presentation.list.deriveInitials
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val realtimeConnectionManager: RealtimeConnectionManager,
) : ViewModel() {

    private val isSigningOutFlow = MutableStateFlow(false)
    private val showConfirmationFlow = MutableStateFlow(false)
    private val signOutErrorFlow = MutableStateFlow<DataError?>(null)

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    init {
        observeAccountState()
    }

    fun onEvent(event: AccountUiEvent) {
        when (event) {
            AccountUiEvent.RequestSignOut -> showSignOutConfirmation()
            AccountUiEvent.ConfirmSignOut -> performSignOut()
            AccountUiEvent.DismissSignOutConfirmation -> dismissSignOutConfirmation()
            AccountUiEvent.DismissError -> dismissError()
        }
    }

    private fun observeAccountState() {
        viewModelScope.launch {
            combine(
                authRepository.getAuthState().distinctUntilChanged(),
                realtimeConnectionManager.connectionState.distinctUntilChanged(),
                isSigningOutFlow,
                showConfirmationFlow,
                signOutErrorFlow,
            ) { authState, connectionState, isSigningOut, showConfirmation, signOutError ->
                val authenticatedUser = (authState as? AuthState.Authenticated)?.user
                val displayName = authenticatedUser?.displayName.orEmpty()
                val email = authenticatedUser?.email.orEmpty()
                val titleForInitials = displayName.ifBlank { email }
                val initials = deriveInitials(titleForInitials)

                AccountUiState(
                    displayName = displayName,
                    email = email,
                    initials = initials,
                    isEmailVerified = authenticatedUser?.isEmailVerified ?: false,
                    connectionState = connectionState,
                    isSigningOut = isSigningOut,
                    showSignOutConfirmation = showConfirmation,
                    signOutError = signOutError,
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    private fun showSignOutConfirmation() {
        showConfirmationFlow.value = true
    }

    private fun dismissSignOutConfirmation() {
        showConfirmationFlow.value = false
    }

    private fun dismissError() {
        signOutErrorFlow.value = null
    }

    private fun performSignOut() {
        if (isSigningOutFlow.value) return

        showConfirmationFlow.value = false
        signOutErrorFlow.value = null
        isSigningOutFlow.value = true

        viewModelScope.launch {
            authRepository.logout()
                .onLeft { error ->
                    isSigningOutFlow.value = false
                    signOutErrorFlow.value = error
                }
                .onRight {
                    isSigningOutFlow.value = false
                }
        }
    }
}
