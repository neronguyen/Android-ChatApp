package io.github.neronguyen.chat.feature.auth.presentation.forgot

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.neronguyen.chat.core.ui.theme.ChatTheme
import io.github.neronguyenvn.nerochat.contract.validation.auth.AuthValidationError
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ForgotPasswordScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun forgotPasswordScreen_dispatchesEmailChangedSubmitAndBackToLogin() {
        val events = mutableListOf<ForgotPasswordUiEvent>()
        var backToLoginClicked = false

        composeTestRule.setContent {
            ChatTheme {
                ForgotPasswordScreen(
                    uiState = ForgotPasswordUiState(email = "elena@example.com"),
                    onEvent = { events.add(it) },
                    onNavigateToLogin = { backToLoginClicked = true },
                )
            }
        }

        composeTestRule.onNodeWithTag("forgot_password_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("email_field").performTextInput("x")
        composeTestRule.onNodeWithTag("submit_button").assertIsEnabled().performClick()

        assertTrue(events.any { it is ForgotPasswordUiEvent.EmailChanged })
        assertTrue(events.contains(ForgotPasswordUiEvent.Submit))

        composeTestRule.onNodeWithTag("back_to_login_button").performClick()
        assertTrue(backToLoginClicked)
    }

    @Test
    fun forgotPasswordScreen_displaysValidationError() {
        composeTestRule.setContent {
            ChatTheme {
                ForgotPasswordScreen(
                    uiState = ForgotPasswordUiState(
                        email = "bad",
                        validationError = AuthValidationError.InvalidEmail,
                    ),
                    onEvent = {},
                    onNavigateToLogin = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("forgot_password_error_text")
            .assertIsDisplayed()
            .assertTextEquals("Please enter a valid email address")
    }

    @Test
    fun forgotPasswordScreen_disablesSubmitAndShowsLoadingIndicator_whenLoading() {
        composeTestRule.setContent {
            ChatTheme {
                ForgotPasswordScreen(
                    uiState = ForgotPasswordUiState(
                        email = "elena@example.com",
                        isLoading = true,
                    ),
                    onEvent = {},
                    onNavigateToLogin = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("submit_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("forgot_password_loading_indicator").assertIsDisplayed()
    }
}
