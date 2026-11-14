package io.github.neronguyen.chat.feature.auth.presentation.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.neronguyen.chat.core.model.error.AuthError
import io.github.neronguyen.chat.core.ui.rememberMinLoading
import io.github.neronguyen.chat.core.ui.theme.ChatTheme
import io.github.neronguyen.chat.feature.auth.R
import io.github.neronguyen.chat.feature.auth.presentation.util.asUiText

@Composable
fun LoginRoute(
    viewModel: LoginViewModel,
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: () -> Unit,
    onNavigateToVerifyEmail: (email: String) -> Unit = {},
    onNavigateToForgotPassword: () -> Unit = {},
    prefilledEmail: String? = null,
    bannerMessage: String? = null,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(prefilledEmail, bannerMessage) {
        viewModel.setInitialState(prefilledEmail, bannerMessage)
    }

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            onLoginSuccess()
            viewModel.onLoginSuccessHandled()
        }
    }

    LoginScreen(
        emailState = viewModel.emailState,
        passwordState = viewModel.passwordState,
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateToRegister = onNavigateToRegister,
        onNavigateToVerifyEmail = onNavigateToVerifyEmail,
        onNavigateToForgotPassword = onNavigateToForgotPassword,
        modifier = modifier,
    )
}

@Composable
fun LoginScreen(
    emailState: TextFieldState,
    passwordState: TextFieldState,
    uiState: LoginUiState,
    onEvent: (LoginUiEvent) -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToVerifyEmail: (email: String) -> Unit = {},
    onNavigateToForgotPassword: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val showLoading by rememberMinLoading(isLoading = uiState.isLoading)

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("login_screen")
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.welcome_back),
            style = MaterialTheme.typography.headlineMedium,
        )

        if (uiState.bannerMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("confirmation_banner")
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(12.dp),
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = uiState.bannerMessage,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            state = emailState,
            label = { Text(stringResource(R.string.email)) },
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("email_field"),
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedSecureTextField(
            state = passwordState,
            label = { Text(stringResource(R.string.password)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("password_field"),
        )

        Spacer(modifier = Modifier.height(12.dp))

        val activeErrorText = uiState.validationError?.asUiText() ?: uiState.authError?.asUiText()
        Text(
            text = activeErrorText.orEmpty(),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .testTag("login_error_text")
                .alpha(if (activeErrorText != null) 1f else 0f),
        )

        if (uiState.authError == AuthError.EmailNotVerified) {
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                onClick = { onNavigateToVerifyEmail(emailState.text.toString().trim()) },
                modifier = Modifier.testTag("verify_email_prompt_button"),
            ) {
                Text(stringResource(R.string.verify_email_prompt))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { onEvent(LoginUiEvent.Login) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("login_button"),
            enabled = !uiState.isLoading,
        ) {
            if (showLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("login_loading_indicator"),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(text = stringResource(R.string.login))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
            onClick = onNavigateToForgotPassword,
            modifier = Modifier.testTag("forgot_password_button"),
        ) {
            Text(stringResource(R.string.forgot_password))
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onNavigateToRegister,
            modifier = Modifier.testTag("register_button"),
        ) {
            Text(stringResource(R.string.dont_have_account))
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
internal fun LoginScreenDefaultPreview() {
    ChatTheme {
        LoginScreen(
            emailState = remember { TextFieldState("elena@example.com") },
            passwordState = remember { TextFieldState() },
            uiState = LoginUiState(),
            onEvent = {},
            onNavigateToRegister = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
internal fun LoginScreenErrorAndBannerPreview() {
    ChatTheme {
        LoginScreen(
            emailState = remember { TextFieldState("elena@example.com") },
            passwordState = remember { TextFieldState("••••••••••••") },
            uiState = LoginUiState(
                authError = AuthError.EmailNotVerified,
                bannerMessage = "Password reset successful. Please sign in with your new password.",
            ),
            onEvent = {},
            onNavigateToRegister = {},
        )
    }
}
