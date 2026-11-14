package io.github.neronguyen.chat.feature.auth.presentation.verify

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.neronguyen.chat.core.model.error.AuthError
import io.github.neronguyen.chat.core.ui.theme.ChatTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VerifyEmailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun verifyEmailScreen_displaysTargetEmailAndOtpField_andDispatchesVerifyAndResend() {
        val events = mutableListOf<VerifyEmailUiEvent>()
        var backToLoginClicked = false

        composeTestRule.setContent {
            ChatTheme {
                VerifyEmailScreen(
                    uiState = VerifyEmailUiState(
                        email = "elena@example.com",
                        code = "12345",
                        resendCooldownSeconds = 0,
                    ),
                    onEvent = { events.add(it) },
                    onNavigateToLogin = { backToLoginClicked = true },
                )
            }
        }

        composeTestRule.onNodeWithTag("verify_email_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("We've sent a 6-digit verification code to elena@example.com")
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("otp_input_field").assertIsDisplayed()

        composeTestRule.onNodeWithTag("verify_button").assertIsEnabled().performClick()
        assertEquals(listOf(VerifyEmailUiEvent.Verify), events)

        composeTestRule.onNodeWithTag("resend_button").assertIsEnabled().performClick()
        assertEquals(listOf(VerifyEmailUiEvent.Verify, VerifyEmailUiEvent.ResendCode), events)

        composeTestRule.onNodeWithTag("back_to_login_button").performClick()
        assertTrue(backToLoginClicked)
    }

    @Test
    fun verifyEmailScreen_showsCooldownCountdownAndDisablesResendButton() {
        composeTestRule.setContent {
            ChatTheme {
                VerifyEmailScreen(
                    uiState = VerifyEmailUiState(
                        email = "elena@example.com",
                        resendCooldownSeconds = 42,
                        infoMessage = "Verification code resent",
                    ),
                    onEvent = {},
                    onNavigateToLogin = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("resend_button")
            .assertIsNotEnabled()
            .assertTextEquals("Resend Code (42s)")
        composeTestRule.onNodeWithTag("verify_email_info_text")
            .assertIsDisplayed()
            .assertTextEquals("Verification code resent")
    }

    @Test
    fun verifyEmailScreen_displaysInvalidCodeErrorAndRetryButton() {
        val events = mutableListOf<VerifyEmailUiEvent>()

        composeTestRule.setContent {
            ChatTheme {
                VerifyEmailScreen(
                    uiState = VerifyEmailUiState(
                        email = "elena@example.com",
                        code = "12345",
                        authError = AuthError.InvalidCode,
                    ),
                    onEvent = { events.add(it) },
                    onNavigateToLogin = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("verify_email_error_text")
            .assertIsDisplayed()
            .assertTextEquals("Invalid verification code")
        composeTestRule.onNodeWithTag("verify_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("retry_button").assertIsDisplayed().performClick()
        assertEquals(listOf(VerifyEmailUiEvent.Verify), events)
    }

    @Test
    fun verifyEmailScreen_displaysMaxAttemptsExceededLockoutMessage() {
        composeTestRule.setContent {
            ChatTheme {
                VerifyEmailScreen(
                    uiState = VerifyEmailUiState(
                        email = "elena@example.com",
                        authError = AuthError.MaxAttemptsExceeded(retryAfterSeconds = 60),
                    ),
                    onEvent = {},
                    onNavigateToLogin = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("verify_email_error_text")
            .assertIsDisplayed()
            .assertTextEquals("Too many failed attempts. Please try again in 60 seconds")
    }

    @Test
    fun verifyEmailScreen_disablesVerifyButtonAndShowsSpinner_whenLoading() {
        composeTestRule.setContent {
            ChatTheme {
                VerifyEmailScreen(
                    uiState = VerifyEmailUiState(
                        email = "elena@example.com",
                        isLoading = true,
                    ),
                    onEvent = {},
                    onNavigateToLogin = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("verify_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("verify_loading_indicator").assertIsDisplayed()
    }
}
