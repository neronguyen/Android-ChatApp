package io.github.neronguyen.chat.feature.auth.presentation.verify

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.neronguyen.chat.core.ui.component.OtpInputField
import io.github.neronguyen.chat.core.ui.rememberMinLoading
import io.github.neronguyen.chat.feature.auth.R
import io.github.neronguyen.chat.feature.auth.presentation.util.asUiText

@Composable
fun VerifyEmailRoute(
    viewModel: VerifyEmailViewModel,
    onVerifySuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            onVerifySuccess()
            viewModel.onVerifySuccessHandled()
        }
    }

    VerifyEmailScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateToLogin = onNavigateToLogin,
        modifier = modifier,
    )
}

@Composable
fun VerifyEmailScreen(
    uiState: VerifyEmailUiState,
    onEvent: (VerifyEmailUiEvent) -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showLoading by rememberMinLoading(isLoading = uiState.isLoading)
    val hasActiveError = uiState.validationError != null || uiState.authError != null

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("verify_email_screen")
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.verify_email),
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.enter_verification_code, uiState.email),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(28.dp))

        OtpInputField(
            value = uiState.code,
            onValueChange = { onEvent(VerifyEmailUiEvent.CodeChanged(it)) },
            isError = hasActiveError,
            enabled = !uiState.isLoading,
            onComplete = { onEvent(VerifyEmailUiEvent.Verify) },
        )

        Spacer(modifier = Modifier.height(16.dp))

        val activeErrorText = uiState.validationError?.asUiText() ?: uiState.authError?.asUiText()
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

        if (hasActiveError && !uiState.isLoading) {
            Button(
                onClick = { onEvent(VerifyEmailUiEvent.Verify) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("retry_button"),
            ) {
                Text(stringResource(R.string.retry))
            }
        } else {
            Button(
                onClick = { onEvent(VerifyEmailUiEvent.Verify) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("verify_button"),
                enabled = !uiState.isLoading,
            ) {
                if (showLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.verify))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val resendButtonText = if (uiState.resendCooldownSeconds > 0) {
            stringResource(R.string.resend_cooldown, uiState.resendCooldownSeconds)
        } else {
            stringResource(R.string.resend_code)
        }

        TextButton(
            onClick = { onEvent(VerifyEmailUiEvent.ResendCode) },
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
