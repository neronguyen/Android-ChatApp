package io.github.neronguyen.chat.feature.auth.presentation.forgot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.neronguyen.chat.core.ui.rememberMinLoading
import io.github.neronguyen.chat.feature.auth.R
import io.github.neronguyen.chat.feature.auth.presentation.util.asUiText

@Composable
fun ForgotPasswordRoute(
    viewModel: ForgotPasswordViewModel,
    onNavigateToResetPassword: (email: String) -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            val email = uiState.submittedEmail.orEmpty()
            onNavigateToResetPassword(email)
            viewModel.onForgotPasswordSuccessHandled()
        }
    }

    ForgotPasswordScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateToLogin = onNavigateToLogin,
        modifier = modifier,
    )
}

@Composable
fun ForgotPasswordScreen(
    uiState: ForgotPasswordUiState,
    onEvent: (ForgotPasswordUiEvent) -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showLoading by rememberMinLoading(isLoading = uiState.isLoading)

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("forgot_password_screen")
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.forgot_password_title),
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.forgot_password_instructions),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = uiState.email,
            onValueChange = { onEvent(ForgotPasswordUiEvent.EmailChanged(it)) },
            label = { Text(stringResource(R.string.email)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("email_field"),
        )

        Spacer(modifier = Modifier.height(12.dp))

        val activeErrorText = uiState.validationError?.asUiText() ?: uiState.authError?.asUiText()
        Text(
            text = activeErrorText.orEmpty(),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.alpha(if (activeErrorText != null) 1f else 0f),
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { onEvent(ForgotPasswordUiEvent.Submit) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("submit_button"),
            enabled = !uiState.isLoading,
        ) {
            if (showLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(stringResource(R.string.send_reset_code))
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
