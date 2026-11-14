package io.github.neronguyen.chat.feature.auth.presentation.login

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.neronguyen.chat.core.model.error.AuthError
import io.github.neronguyen.chat.core.ui.theme.ChatTheme
import io.github.neronguyenvn.nerochat.contract.validation.auth.AuthValidationError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h844dp")
class LoginScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun loginScreen_displaysInputsAndActionButtons_andDispatchesLoginEvent() {
        val events = mutableListOf<LoginUiEvent>()
        val emailState = TextFieldState()
        val passwordState = TextFieldState()

        composeTestRule.setContent {
            ChatTheme {
                LoginScreen(
                    emailState = emailState,
                    passwordState = passwordState,
                    uiState = LoginUiState(),
                    onEvent = { events.add(it) },
                    onNavigateToRegister = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("login_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("email_field").performTextInput("elena@example.com")
        composeTestRule.onNodeWithTag("password_field").performTextInput("Secret1234")
        composeTestRule.onNodeWithTag("login_button").assertIsEnabled().performClick()

        assertEquals("elena@example.com", emailState.text.toString())
        assertEquals("Secret1234", passwordState.text.toString())
        assertEquals(listOf(LoginUiEvent.Login), events)
    }

    @Test
    fun loginScreen_disablesButtonAndShowsSpinner_whenLoading() {
        composeTestRule.setContent {
            ChatTheme {
                LoginScreen(
                    emailState = TextFieldState(),
                    passwordState = TextFieldState(),
                    uiState = LoginUiState(isLoading = true),
                    onEvent = {},
                    onNavigateToRegister = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("login_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("login_loading_indicator").assertIsDisplayed()
    }

    @Test
    fun loginScreen_displaysValidationAndAuthErrors() {
        composeTestRule.setContent {
            ChatTheme {
                LoginScreen(
                    emailState = TextFieldState(),
                    passwordState = TextFieldState(),
                    uiState = LoginUiState(validationError = AuthValidationError.InvalidEmail),
                    onEvent = {},
                    onNavigateToRegister = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("login_error_text")
            .assertIsDisplayed()
            .assertTextEquals("Please enter a valid email address")
    }

    @Test
    fun loginScreen_displaysBannerAndEmailNotVerifiedPrompt_andInvokesNavigationCallbacks() {
        var verifyEmailArg: String? = null
        var forgotClicked = false
        var registerClicked = false
        val emailState = TextFieldState().apply {
            setTextAndPlaceCursorAtEnd("  elena@example.com  ")
        }

        composeTestRule.setContent {
            ChatTheme {
                LoginScreen(
                    emailState = emailState,
                    passwordState = TextFieldState(),
                    uiState = LoginUiState(
                        authError = AuthError.EmailNotVerified,
                        bannerMessage = "Password reset successfully. Please log in.",
                    ),
                    onEvent = {},
                    onNavigateToRegister = { registerClicked = true },
                    onNavigateToVerifyEmail = { verifyEmailArg = it },
                    onNavigateToForgotPassword = { forgotClicked = true },
                )
            }
        }

        composeTestRule.onNodeWithTag("confirmation_banner").assertIsDisplayed()
        composeTestRule.onNodeWithText("Password reset successfully. Please log in.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("login_error_text")
            .assertTextEquals("Please verify your email address before logging in")

        composeTestRule.onNodeWithTag("verify_email_prompt_button").assertIsDisplayed()
            .performClick()
        assertEquals("elena@example.com", verifyEmailArg)

        composeTestRule.onNodeWithTag("forgot_password_button").performClick()
        assertTrue(forgotClicked)

        composeTestRule.onNodeWithTag("register_button").performClick()
        assertTrue(registerClicked)
    }
}
