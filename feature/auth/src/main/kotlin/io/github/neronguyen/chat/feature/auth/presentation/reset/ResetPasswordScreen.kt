package io.github.neronguyen.chat.feature.auth.presentation.reset

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.neronguyen.chat.core.ui.component.OtpInputField
import io.github.neronguyen.chat.core.ui.rememberMinLoading
import io.github.neronguyen.chat.core.ui.theme.ChatTheme
import io.github.neronguyen.chat.feature.auth.R
import io.github.neronguyen.chat.feature.auth.presentation.util.asUiText

@Composable
fun ResetPasswordRoute(
    viewModel: ResetPasswordViewModel,
    onResetSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            onResetSuccess()
            viewModel.onResetSuccessHandled()
        }
    }

    ResetPasswordScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateToLogin = onNavigateToLogin,
        modifier = modifier,
    )
}

@Composable
fun ResetPasswordScreen(
    uiState: ResetPasswordUiState,
    onEvent: (ResetPasswordUiEvent) -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showLoading by rememberMinLoading(isLoading = uiState.isLoading)
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    val hasActiveError = uiState.validationError != null ||
        uiState.passwordsMismatch ||
        uiState.authError != null

    val isCtaEnabled = !uiState.isLoading &&
        uiState.code.length == 6 &&
        uiState.newPassword.isNotBlank() &&
        uiState.confirmPassword.isNotBlank() &&
        uiState.newPassword == uiState.confirmPassword

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("reset_password_screen")
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.reset_password),
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.enter_reset_code, uiState.email),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(24.dp))

        OtpInputField(
            value = uiState.code,
            onValueChange = { onEvent(ResetPasswordUiEvent.CodeChanged(it)) },
            isError = hasActiveError,
            enabled = !uiState.isLoading,
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = uiState.newPassword,
            onValueChange = { onEvent(ResetPasswordUiEvent.NewPasswordChanged(it)) },
            label = { Text(stringResource(R.string.new_password)) },
            singleLine = true,
            visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                    Icon(
                        imageVector = if (newPasswordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = null,
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("new_password_field"),
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = uiState.confirmPassword,
            onValueChange = { onEvent(ResetPasswordUiEvent.ConfirmPasswordChanged(it)) },
            label = { Text(stringResource(R.string.confirm_new_password)) },
            singleLine = true,
            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                    Icon(
                        imageVector = if (confirmPasswordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = null,
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("confirm_password_field"),
        )

        Spacer(modifier = Modifier.height(12.dp))

        val activeErrorText = when {
            uiState.passwordsMismatch -> stringResource(R.string.error_passwords_do_not_match)
            uiState.validationError != null -> uiState.validationError.asUiText()
            uiState.authError != null -> uiState.authError.asUiText()
            else -> null
        }
        Text(
            text = activeErrorText.orEmpty(),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(if (activeErrorText != null) 1f else 0f),
        )

        if (uiState.infoMessage != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = uiState.infoMessage,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onEvent(ResetPasswordUiEvent.Reset) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("reset_password_button"),
            enabled = isCtaEnabled,
        ) {
            if (showLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(stringResource(R.string.reset_password))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val resendButtonText = if (uiState.resendCooldownSeconds > 0) {
            stringResource(R.string.resend_cooldown, uiState.resendCooldownSeconds)
        } else {
            stringResource(R.string.resend_code)
        }

        TextButton(
            onClick = { onEvent(ResetPasswordUiEvent.ResendCode) },
            enabled = uiState.resendCooldownSeconds == 0 && !uiState.isResending,
            modifier = Modifier.testTag("resend_button"),
        ) {
            if (uiState.isResending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(resendButtonText)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onNavigateToLogin,
            modifier = Modifier.testTag("back_to_login_button"),
        ) {
            Text(stringResource(R.string.already_have_account))
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
internal fun ResetPasswordScreenDefaultPreview() {
    ChatTheme {
        ResetPasswordScreen(
            uiState = ResetPasswordUiState(
                email = "elena@example.com",
                code = "482910",
                newPassword = "NewPassword123",
                confirmPassword = "NewPassword123",
            ),
            onEvent = {},
            onNavigateToLogin = {},
        )
    }
}
