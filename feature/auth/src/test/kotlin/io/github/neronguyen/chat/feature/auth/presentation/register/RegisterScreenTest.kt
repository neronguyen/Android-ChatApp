package io.github.neronguyen.chat.feature.auth.presentation.register

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
class RegisterScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun registerScreen_acceptsInputsAndDispatchesRegisterAndLoginNavigation() {
        val events = mutableListOf<RegisterUiEvent>()
        var navigatedToLogin = false
        val emailState = TextFieldState()
        val displayNameState = TextFieldState()
        val passwordState = TextFieldState()

        composeTestRule.setContent {
            ChatTheme {
                RegisterScreen(
                    emailState = emailState,
                    displayNameState = displayNameState,
                    passwordState = passwordState,
                    uiState = RegisterUiState(),
                    onEvent = { events.add(it) },
                    onNavigateToLogin = { navigatedToLogin = true },
                )
            }
        }

        composeTestRule.onNodeWithTag("register_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("email_field").performTextInput("elena@example.com")
        composeTestRule.onNodeWithTag("display_name_field").performTextInput("Elena Rostova")
        composeTestRule.onNodeWithTag("password_field").performTextInput("ValidPass123")
        composeTestRule.onNodeWithTag("register_button").assertIsEnabled().performClick()

        assertEquals("elena@example.com", emailState.text.toString())
        assertEquals("Elena Rostova", displayNameState.text.toString())
        assertEquals("ValidPass123", passwordState.text.toString())
        assertEquals(listOf(RegisterUiEvent.Register), events)

        composeTestRule.onNodeWithTag("login_button").performClick()
        assertTrue(navigatedToLogin)
    }

    @Test
    fun registerScreen_disablesButtonAndShowsLoadingIndicator_whenLoading() {
        composeTestRule.setContent {
            ChatTheme {
                RegisterScreen(
                    emailState = TextFieldState(),
                    displayNameState = TextFieldState(),
                    passwordState = TextFieldState(),
                    uiState = RegisterUiState(isLoading = true),
                    onEvent = {},
                    onNavigateToLogin = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("register_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("register_loading_indicator").assertIsDisplayed()
    }

    @Test
    fun registerScreen_displaysValidationErrorMessage() {
        composeTestRule.setContent {
            ChatTheme {
                RegisterScreen(
                    emailState = TextFieldState(),
                    displayNameState = TextFieldState(),
                    passwordState = TextFieldState(),
                    uiState = RegisterUiState(validationError = AuthValidationError.InvalidPassword),
                    onEvent = {},
                    onNavigateToLogin = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("register_error_text")
            .assertIsDisplayed()
            .assertTextEquals("Password must be at least 10 characters and contain at least 1 digit and 1 letter")
    }

    @Test
    fun registerScreen_displaysAuthErrorMessage() {
        composeTestRule.setContent {
            ChatTheme {
                RegisterScreen(
                    emailState = TextFieldState(),
                    displayNameState = TextFieldState(),
                    passwordState = TextFieldState(),
                    uiState = RegisterUiState(authError = AuthError.EmailAlreadyInUse),
                    onEvent = {},
                    onNavigateToLogin = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("register_error_text")
            .assertIsDisplayed()
            .assertTextEquals("An account with this email already exists")
    }
}
