package io.github.neronguyen.chat.feature.chat.presentation.account

import io.github.neronguyen.chat.core.model.RealtimeConnectionState
import io.github.neronguyen.chat.core.model.error.DataError

data class AccountUiState(
    val displayName: String = "",
    val email: String = "",
    val initials: String = "?",
    val isEmailVerified: Boolean = false,
    val connectionState: RealtimeConnectionState = RealtimeConnectionState.DISCONNECTED,
    val isSigningOut: Boolean = false,
    val showSignOutConfirmation: Boolean = false,
    val signOutError: DataError? = null,
)
