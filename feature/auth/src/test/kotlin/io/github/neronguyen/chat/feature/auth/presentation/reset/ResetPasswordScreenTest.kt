package io.github.neronguyen.chat.feature.auth.presentation.reset

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.neronguyen.chat.core.model.error.AuthError
import io.github.neronguyen.chat.core.ui.theme.ChatTheme
import io.github.neronguyenvn.nerochat.contract.validation.auth.AuthValidationError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ResetPasswordScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun resetPasswordScreen_enablesSubmitWhenCodeAndMatchingPasswordsProvided_andDispatchesReset() {
        val events = mutableListOf<ResetPasswordUiEvent>()
        var backToLoginClicked = false

        composeTestRule.setContent {
            ChatTheme {
                ResetPasswordScreen(
                    uiState = ResetPasswordUiState(
                        email = "elena@example.com",
                        code = "482910",
                        newPassword = "NewPassword123",
                        confirmPassword = "NewPassword123",
                    ),
                    onEvent = { events.add(it) },
                    onNavigateToLogin = { backToLoginClicked = true },
                )
            }
        }

        composeTestRule.onNodeWithTag("reset_password_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("otp_input_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("new_password_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirm_password_field").assertIsDisplayed()

        composeTestRule.onNodeWithTag("reset_password_button")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        assertEquals(listOf(ResetPasswordUiEvent.Reset), events)

        composeTestRule.onNodeWithTag("resend_button")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        assertEquals(listOf(ResetPasswordUiEvent.Reset, ResetPasswordUiEvent.ResendCode), events)

        composeTestRule.onNodeWithTag("back_to_login_button").performScrollTo().performClick()
        assertTrue(backToLoginClicked)
    }

    @Test
    fun resetPasswordScreen_disablesSubmitAndShowsMismatchError_whenPasswordsDiffer() {
        composeTestRule.setContent {
            ChatTheme {
                ResetPasswordScreen(
                    uiState = ResetPasswordUiState(
                        email = "elena@example.com",
                        code = "482910",
                        newPassword = "NewPassword123",
                        confirmPassword = "DifferentPassword123",
                        passwordsMismatch = true,
                    ),
                    onEvent = {},
                    onNavigateToLogin = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("reset_password_error_text")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextEquals("Passwords do not match")
        composeTestRule.onNodeWithTag("reset_password_button")
            .performScrollTo()
            .assertIsNotEnabled()
    }

    @Test
    fun resetPasswordScreen_displaysValidationAndLockoutErrors_andCooldown() {
        composeTestRule.setContent {
            ChatTheme {
                ResetPasswordScreen(
                    uiState = ResetPasswordUiState(
                        email = "elena@example.com",
                        code = "482910",
                        newPassword = "short",
                        confirmPassword = "short",
                        validationError = AuthValidationError.InvalidNewPassword,
                        resendCooldownSeconds = 25,
                    ),
                    onEvent = {},
                    onNavigateToLogin = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("reset_password_error_text")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextEquals("New password must be at least 10 characters and contain at least 1 digit and 1 letter")
        composeTestRule.onNodeWithTag("resend_button")
            .performScrollTo()
            .assertIsNotEnabled()
            .assertTextEquals("Resend Code (25s)")
    }

    @Test
    fun resetPasswordScreen_displaysLockoutAuthError() {
        composeTestRule.setContent {
            ChatTheme {
                ResetPasswordScreen(
                    uiState = ResetPasswordUiState(
                        email = "elena@example.com",
                        authError = AuthError.MaxAttemptsExceeded(retryAfterSeconds = 30),
                    ),
                    onEvent = {},
                    onNavigateToLogin = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("reset_password_error_text")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextEquals("Too many failed attempts. Please try again in 30 seconds")
    }
}
