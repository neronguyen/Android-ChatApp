package io.github.neronguyen.chat.feature.auth.presentation.navigation

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import io.github.neronguyen.chat.core.testing.FakeAuthRepository
import io.github.neronguyen.chat.core.ui.theme.ChatTheme
import io.github.neronguyen.chat.feature.auth.R
import io.github.neronguyen.chat.feature.auth.presentation.forgot.ForgotPasswordViewModel
import io.github.neronguyen.chat.feature.auth.presentation.login.LoginViewModel
import io.github.neronguyen.chat.feature.auth.presentation.register.RegisterViewModel
import io.github.neronguyen.chat.feature.auth.presentation.reset.ResetPasswordViewModel
import io.github.neronguyen.chat.feature.auth.presentation.verify.VerifyEmailViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h844dp")
class AuthNavGraphTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun str(resId: Int, vararg args: Any): String =
        RuntimeEnvironment.getApplication().getString(resId, *args)

    private fun providersFor(fakeRepo: FakeAuthRepository) = AuthNavViewModelProviders(
        loginViewModel = { remember { LoginViewModel(fakeRepo) } },
        registerViewModel = { remember { RegisterViewModel(fakeRepo) } },
        verifyEmailViewModel = { email -> remember(email) { VerifyEmailViewModel(email, fakeRepo) } },
        forgotPasswordViewModel = { remember { ForgotPasswordViewModel(fakeRepo) } },
        resetPasswordViewModel = { email -> remember(email) { ResetPasswordViewModel(email, fakeRepo) } },
    )

    @Test
    fun registerFlow_navigatesToVerifyEmail_thenReplacesBackStackWithLoginBanner() {
        val fakeRepo = FakeAuthRepository()
        var authSuccessCount = 0

        composeTestRule.setContent {
            ChatTheme {
                AuthNavGraph(
                    onAuthSuccess = { authSuccessCount++ },
                    viewModelProviders = providersFor(fakeRepo),
                )
            }
        }

        // Start on Login -> tap Register
        composeTestRule.onNodeWithTag("login_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("register_button").performClick()
        composeTestRule.waitForIdle()

        // Submit registration -> navigates to VerifyEmail(email)
        composeTestRule.onNodeWithTag("register_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("email_field").performTextInput("elena@example.com")
        composeTestRule.onNodeWithTag("display_name_field").performTextInput("Elena")
        composeTestRule.onNodeWithTag("password_field").performTextInput("StrongPass123!")
        composeTestRule.onNodeWithTag("register_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("verify_email_screen").assertIsDisplayed()

        // Complete 6-digit OTP -> replaces back stack with Login(email, verifiedBanner)
        composeTestRule.onNode(
            hasSetTextAction() and hasAnyAncestor(hasTestTag("otp_input_field")),
        ).performTextReplacement("123456")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("login_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirmation_banner").assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.email_verified_banner)).assertIsDisplayed()
        composeTestRule.onNodeWithTag("email_field")
            .assertTextContains("elena@example.com")
        assertEquals(0, authSuccessCount)
    }

    @Test
    fun recoveryFlow_navigatesForgotPasswordToResetPassword_thenReturnsToLoginWithBanner() {
        val fakeRepo = FakeAuthRepository()

        composeTestRule.setContent {
            ChatTheme {
                AuthNavGraph(
                    onAuthSuccess = {},
                    viewModelProviders = providersFor(fakeRepo),
                )
            }
        }

        // Login -> ForgotPassword
        composeTestRule.onNodeWithTag("forgot_password_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("forgot_password_screen").assertIsDisplayed()

        composeTestRule.onNodeWithTag("email_field").performTextInput("elena@example.com")
        composeTestRule.onNodeWithTag("submit_button").performClick()
        composeTestRule.waitForIdle()

        // ResetPassword -> complete reset -> Login with password_reset_banner
        composeTestRule.onNodeWithTag("reset_password_screen").assertIsDisplayed()
        composeTestRule.onNode(
            hasSetTextAction() and hasAnyAncestor(hasTestTag("otp_input_field")),
        ).performTextReplacement("654321")
        composeTestRule.onNodeWithTag("new_password_field").performTextInput("NewPass123!")
        composeTestRule.onNodeWithTag("confirm_password_field").performTextInput("NewPass123!")
        composeTestRule.onNodeWithTag("reset_password_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("login_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirmation_banner").assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.password_reset_banner)).assertIsDisplayed()
    }

    @Test
    fun backToLoginButton_popsBackStackToLoginViaPopToLoginOrAdd() {
        val fakeRepo = FakeAuthRepository()

        composeTestRule.setContent {
            ChatTheme {
                AuthNavGraph(
                    onAuthSuccess = {},
                    viewModelProviders = providersFor(fakeRepo),
                )
            }
        }

        // Login -> ForgotPassword -> Back to Login
        composeTestRule.onNodeWithTag("login_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("forgot_password_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("forgot_password_screen").assertIsDisplayed()

        composeTestRule.onNodeWithTag("back_to_login_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("login_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("forgot_password_screen").assertDoesNotExist()
        composeTestRule.onNodeWithTag("confirmation_banner").assertDoesNotExist()
    }
}
