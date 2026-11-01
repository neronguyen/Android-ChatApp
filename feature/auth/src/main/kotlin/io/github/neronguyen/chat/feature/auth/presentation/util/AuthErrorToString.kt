package io.github.neronguyen.chat.feature.auth.presentation.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.neronguyen.chat.core.model.error.AuthError
import io.github.neronguyen.chat.core.ui.asUiText
import io.github.neronguyen.chat.feature.auth.R

@Composable
fun AuthError.asUiText(): String = when (this) {
    AuthError.InvalidCredentials -> stringResource(R.string.error_invalid_credentials)
    AuthError.EmailNotVerified -> stringResource(R.string.error_email_not_verified)
    AuthError.EmailAlreadyInUse -> stringResource(R.string.error_email_already_in_use)
    is AuthError.Data -> error.asUiText()
}
