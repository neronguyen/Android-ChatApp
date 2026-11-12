package io.github.neronguyen.chat.feature.auth.presentation.verify

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.neronguyen.chat.core.data.repository.AuthRepository
import io.github.neronguyen.chat.core.model.error.AuthError
import io.github.neronguyenvn.nerochat.contract.validation.EmailValidator
import io.github.neronguyenvn.nerochat.contract.validation.auth.AuthValidator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.time.Duration.Companion.seconds

private const val KEY_COOLDOWN_END_EPOCH = "cooldown_end_epoch"
private const val KEY_CODE = "otp_code"

@HiltViewModel(assistedFactory = VerifyEmailViewModel.Factory::class)
class VerifyEmailViewModel @AssistedInject constructor(
    @Assisted val email: String,
    private val authRepository: AuthRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    internal constructor(
        email: String,
        authRepository: AuthRepository,
    ) : this(email, authRepository, SavedStateHandle())

    @AssistedFactory
    interface Factory {
        fun create(email: String): VerifyEmailViewModel
    }

    internal var timeProvider: () -> Long = { System.currentTimeMillis() }
        set(value) {
            field = value
            restoreCooldownFromSavedState()
        }

    private val initialCode: String = savedStateHandle.get<String>(KEY_CODE).orEmpty()
    private val _uiState = MutableStateFlow(
        VerifyEmailUiState(
            email = email,
            code = initialCode,
        ),
    )
    val uiState: StateFlow<VerifyEmailUiState> = _uiState.asStateFlow()

    private var cooldownJob: Job? = null

    init {
        restoreCooldownFromSavedState()
    }

    fun onEvent(event: VerifyEmailUiEvent) {
        when (event) {
            is VerifyEmailUiEvent.CodeChanged -> onCodeChanged(event.code)
            VerifyEmailUiEvent.Verify -> verify()
            VerifyEmailUiEvent.ResendCode -> resendCode()
            VerifyEmailUiEvent.DismissInfo -> dismissInfo()
        }
    }

    fun onVerifySuccessHandled() {
        _uiState.update { it.copy(isSuccess = false) }
    }

    private fun onCodeChanged(newCode: String) {
        val sanitized = newCode.filter { it.isDigit() }.take(6)
        savedStateHandle[KEY_CODE] = sanitized
        _uiState.update {
            it.copy(
                code = sanitized,
                validationError = null,
                authError = null,
            )
        }
        if (sanitized.length == 6 && !_uiState.value.isLoading) {
            verify()
        }
    }

    private fun verify() {
        if (_uiState.value.isLoading) return

        val normalizedEmail = EmailValidator.normalize(email)
        val codeText = _uiState.value.code.trim()

        val validationError = AuthValidator.validateVerifyEmail(
            email = normalizedEmail,
            code = codeText,
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
            authRepository.verifyEmail(
                email = normalizedEmail,
                code = codeText,
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

    private fun resendCode() {
        if (_uiState.value.resendCooldownSeconds > 0 || _uiState.value.isResending) return

        _uiState.update { it.copy(isResending = true, authError = null, validationError = null) }

        viewModelScope.launch {
            val normalizedEmail = EmailValidator.normalize(email)
            authRepository.resendVerification(normalizedEmail)
                .onLeft { error ->
                    _uiState.update {
                        it.copy(
                            isResending = false,
                            authError = AuthError.Data(error),
                        )
                    }
                }
                .onRight {
                    _uiState.update {
                        it.copy(
                            isResending = false,
                            infoMessage = "Verification code resent",
                        )
                    }
                    startCooldown(60)
                }
        }
    }

    fun startCooldown(seconds: Int) {
        val targetEpoch = timeProvider() + seconds * 1000L
        savedStateHandle[KEY_COOLDOWN_END_EPOCH] = targetEpoch
        _uiState.update { it.copy(resendCooldownSeconds = seconds) }
        launchCooldownTicker()
    }

    private fun restoreCooldownFromSavedState() {
        val targetEpoch = savedStateHandle.get<Long>(KEY_COOLDOWN_END_EPOCH) ?: 0L
        val remaining = max(0L, (targetEpoch - timeProvider() + 999L) / 1000L).toInt()
        if (remaining > 0) {
            _uiState.update { it.copy(resendCooldownSeconds = remaining) }
            launchCooldownTicker()
        }
    }

    private fun launchCooldownTicker() {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            while (true) {
                val targetEpoch = savedStateHandle.get<Long>(KEY_COOLDOWN_END_EPOCH) ?: 0L
                val remaining = max(0L, (targetEpoch - timeProvider() + 999L) / 1000L).toInt()
                _uiState.update { it.copy(resendCooldownSeconds = remaining) }
                if (remaining <= 0) break
                delay(1.seconds)
            }
        }
    }

    private fun dismissInfo() {
        _uiState.update { it.copy(infoMessage = null) }
    }
}
