package io.github.neronguyen.chat.feature.auth.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import arrow.core.Either
import io.github.neronguyen.chat.core.model.error.AuthError
import io.github.neronguyen.chat.core.model.error.DataError
import io.github.neronguyen.chat.core.testing.FakeAuthRepository
import io.github.neronguyen.chat.core.ui.theme.ChatTheme
import io.github.neronguyen.chat.feature.auth.R
import io.github.neronguyen.chat.feature.auth.presentation.login.LoginRoute
import io.github.neronguyen.chat.feature.auth.presentation.login.LoginViewModel
import io.github.neronguyen.chat.feature.auth.presentation.register.RegisterRoute
import io.github.neronguyen.chat.feature.auth.presentation.register.RegisterViewModel
import io.github.neronguyen.chat.feature.auth.presentation.verify.VerifyEmailRoute
import io.github.neronguyen.chat.feature.auth.presentation.verify.VerifyEmailViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import io.github.neronguyen.chat.core.ui.R as CoreUiR

/**
 * Tier 1/2 Feature Tests integrating Compose `*Route` screens, real ViewModels, and [FakeAuthRepository]
 * on the JVM under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h844dp")
class AuthFeatureTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun str(resId: Int, vararg args: Any): String =
        RuntimeEnvironment.getApplication().getString(resId, *args)

    @Test
    fun loginRoute_sendsCredentialsToAuthRepository_andRendersDifferentErrors() {
        val fakeRepo = FakeAuthRepository()
        val viewModel = LoginViewModel(fakeRepo)
        var verifyEmailTarget: String? = null
        var loginSuccessCount = 0

        var nextResponse: Either<AuthError.Login, io.github.neronguyen.chat.core.model.User> =
            Either.Left(AuthError.InvalidCredentials)
        fakeRepo.loginHandler = { _, _ -> nextResponse }

        composeTestRule.setContent {
            ChatTheme {
                LoginRoute(
                    viewModel = viewModel,
                    onNavigateToRegister = {},
                    onLoginSuccess = { loginSuccessCount++ },
                    onNavigateToVerifyEmail = { verifyEmailTarget = it },
                )
            }
        }

        composeTestRule.onNodeWithTag("email_field").performTextInput("  Elena@Example.com ")
        composeTestRule.onNodeWithTag("password_field").performTextInput("WrongPass123")
        composeTestRule.onNodeWithTag("login_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf("elena@example.com" to "WrongPass123"), fakeRepo.loginCalls)
        composeTestRule.onNodeWithTag("login_error_text")
            .assertIsDisplayed()
            .assertTextEquals(str(R.string.error_invalid_credentials))
        assertEquals(0, loginSuccessCount)

        // Network connectivity error from auth manager
        nextResponse = Either.Left(AuthError.Data(DataError.Connectivity.NoInternet))
        composeTestRule.onNodeWithTag("login_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("login_error_text")
            .assertIsDisplayed()
            .assertTextEquals(str(CoreUiR.string.error_no_internet))

        // Unverified email surfaces verification CTA and passes normalized lowercase email
        nextResponse = Either.Left(AuthError.EmailNotVerified)
        composeTestRule.onNodeWithTag("login_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("login_error_text")
            .assertIsDisplayed()
            .assertTextEquals(str(R.string.error_email_not_verified))
        composeTestRule.onNodeWithTag("verify_email_prompt_button")
            .assertIsDisplayed()
            .performClick()
        assertEquals("elena@example.com", verifyEmailTarget)
    }

    @Test
    fun loginRoute_prefillsEmailAndBanner_andInvokesOnLoginSuccess() {
        val fakeRepo = FakeAuthRepository()
        val viewModel = LoginViewModel(fakeRepo)
        var loginSuccessCount = 0
        val verifiedBanner = str(R.string.email_verified_banner)

        composeTestRule.setContent {
            ChatTheme {
                LoginRoute(
                    viewModel = viewModel,
                    onNavigateToRegister = {},
                    onLoginSuccess = { loginSuccessCount++ },
                    prefilledEmail = "elena@example.com",
                    bannerMessage = verifiedBanner,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("confirmation_banner").assertIsDisplayed()
        composeTestRule.onNodeWithText(verifiedBanner).assertIsDisplayed()

        composeTestRule.onNodeWithTag("password_field").performTextInput("StrongPass123!")
        composeTestRule.onNodeWithTag("login_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf("elena@example.com" to "StrongPass123!"), fakeRepo.loginCalls)
        assertEquals(1, loginSuccessCount)
    }

    @Test
    fun registerRoute_displaysConflictError_thenNavigatesToVerifyEmailOnSuccess() {
        val fakeRepo = FakeAuthRepository()
        val viewModel = RegisterViewModel(fakeRepo)
        val navigatedEmails = mutableListOf<String>()

        var failWithConflict = true
        fakeRepo.registerHandler = { email, displayName, _ ->
            if (failWithConflict) {
                Either.Left(AuthError.EmailAlreadyInUse)
            } else {
                Either.Right(
                    io.github.neronguyen.chat.core.model.User(
                        id = "user-new",
                        email = email,
                        displayName = displayName,
                        isEmailVerified = false,
                    ),
                )
            }
        }

        composeTestRule.setContent {
            ChatTheme {
                RegisterRoute(
                    viewModel = viewModel,
                    onNavigateToLogin = {},
                    onRegisterSuccess = { navigatedEmails.add(it) },
                )
            }
        }

        composeTestRule.onNodeWithTag("email_field").performTextReplacement("taken@example.com")
        composeTestRule.onNodeWithTag("display_name_field").performTextReplacement("Elena")
        composeTestRule.onNodeWithTag("password_field").performTextReplacement("StrongPass123!")
        composeTestRule.onNodeWithTag("register_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("register_error_text")
            .assertIsDisplayed()
            .assertTextEquals(str(R.string.error_email_already_in_use))
        assertTrue(navigatedEmails.isEmpty())

        failWithConflict = false
        composeTestRule.onNodeWithTag("email_field").performTextReplacement("fresh@example.com")
        composeTestRule.onNodeWithTag("register_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf("fresh@example.com"), navigatedEmails)
    }

    @Test
    fun verifyEmailRoute_rejectsInvalidOtp_resendsCode_andRecoversWithValidOtp() {
        val fakeRepo = FakeAuthRepository()
        val viewModel = VerifyEmailViewModel("elena@example.com", fakeRepo)
        var verifySuccessCount = 0

        fakeRepo.verifyEmailHandler = { _, code ->
            if (code == "654321") Either.Right(Unit) else Either.Left(AuthError.InvalidCode)
        }

        composeTestRule.setContent {
            ChatTheme {
                VerifyEmailRoute(
                    viewModel = viewModel,
                    onVerifySuccess = { verifySuccessCount++ },
                    onNavigateToLogin = {},
                )
            }
        }

        val otpInput = composeTestRule.onNode(
            hasSetTextAction() and hasAnyAncestor(hasTestTag("otp_input_field")),
        )

        // Wrong 6-digit OTP auto-submits exactly once and surfaces retry + invalid code error
        otpInput.performTextReplacement("000000")
        composeTestRule.waitForIdle()

        assertEquals(listOf("elena@example.com" to "000000"), fakeRepo.verifyEmailCalls)
        composeTestRule.onNodeWithTag("verify_email_error_text")
            .assertIsDisplayed()
            .assertTextEquals(str(R.string.error_invalid_otp_code))
        composeTestRule.onNodeWithTag("retry_button").assertIsDisplayed()
        assertEquals(0, verifySuccessCount)

        // Resend verification code surfaces confirmation info text
        composeTestRule.onNodeWithTag("resend_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf("elena@example.com"), fakeRepo.resendVerificationCalls)
        composeTestRule.onNodeWithTag("verify_email_info_text")
            .assertIsDisplayed()
            .assertTextEquals(str(R.string.verification_code_resent))

        // Replacing with valid 6-digit OTP submits once more and triggers onVerifySuccess
        otpInput.performTextReplacement("")
        composeTestRule.waitForIdle()
        otpInput.performTextReplacement("654321")
        composeTestRule.waitForIdle()

        assertEquals(
            listOf(
                "elena@example.com" to "000000",
                "elena@example.com" to "654321",
            ),
            fakeRepo.verifyEmailCalls,
        )
        assertEquals(1, verifySuccessCount)
    }
}
