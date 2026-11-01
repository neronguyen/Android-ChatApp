package io.github.neronguyen.chat.feature.auth.presentation.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.neronguyen.chat.feature.auth.R
import io.github.neronguyenvn.nerochat.contract.validation.auth.AuthValidationError

@Composable
fun AuthValidationError.asUiText(): String = when (this) {
    AuthValidationError.EmptyEmail -> stringResource(R.string.error_empty_email)
    AuthValidationError.InvalidEmail -> stringResource(R.string.error_invalid_email)
    AuthValidationError.EmptyDisplayName -> stringResource(R.string.error_empty_display_name)
    AuthValidationError.EmptyPassword -> stringResource(R.string.error_empty_password)
    AuthValidationError.InvalidPassword -> stringResource(R.string.error_invalid_password)
    AuthValidationError.EmptyRefreshToken -> stringResource(R.string.error_empty_refresh_token)
    AuthValidationError.EmptyResetToken -> stringResource(R.string.error_empty_reset_token)
    AuthValidationError.EmptyOldPassword -> stringResource(R.string.error_empty_old_password)
    AuthValidationError.EmptyNewPassword -> stringResource(R.string.error_empty_new_password)
    AuthValidationError.InvalidNewPassword -> stringResource(R.string.error_invalid_new_password)
}
