package io.github.neronguyen.chat.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OtpRecoveryE2ETest : BaseE2EJourneyTest() {

    @Test
    fun otpRecoveryJourney_rejectsInvalidOtpAndRecoversWithValidCode() = runJourney {
        val epoch = System.currentTimeMillis() / 1000L
        val aliceEmail = "alice.otp.$epoch@example.com"
        val aliceDisplayName = "Alice OTP Recovery"
        val alicePassword = "AlicePassword123!"

        val otpInputMatcher = hasSetTextAction() and hasAnyAncestor(hasTestTag("otp_input_field"))

        step("00_register_and_challenge", "N0_REGISTER_AND_CHALLENGE") {
            waitForTag("login_screen", timeoutMs = 15_000L)
            composeTestRule.onNodeWithTag("register_button").performClick()

            waitForTag("register_screen", timeoutMs = 10_000L)
            composeTestRule.onNodeWithTag("email_field").performTextReplacement(aliceEmail)
            composeTestRule.onNodeWithTag("display_name_field")
                .performTextReplacement(aliceDisplayName)
            composeTestRule.onNodeWithTag("password_field").performTextReplacement(alicePassword)
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("register_error_text").assertTextEquals("")

            composeTestRule.onNodeWithTag("register_button").performClick()
            waitForTag("verify_email_screen", timeoutMs = 20_000L)
            composeTestRule.onNodeWithTag("otp_input_field").assertIsDisplayed()
        }

        lateinit var realOtp: String
        step("01_invalid_otp_rejected", "N1_INVALID_OTP_REJECTED") {
            realOtp = peerClient.fetchLatestOtp(aliceEmail)
            val invalidOtp = if (realOtp == "000000") "999999" else "000000"

            composeTestRule.onNode(otpInputMatcher).performTextReplacement(invalidOtp)

            // Wait for backend Redis rejection to surface retry_button and error text on VerifyEmailScreen
            waitForTag("retry_button", timeoutMs = 20_000L)
            composeTestRule.onNodeWithTag("verify_email_screen").assertIsDisplayed()
            composeTestRule.onNodeWithTag("retry_button").assertIsDisplayed()
            composeTestRule.onNodeWithTag("verify_email_error_text")
                .assertIsDisplayed()
                .assertTextEquals("Invalid verification code")
        }

        step("02_valid_otp_recovery_to_login", "N2_VALID_OTP_RECOVERY_TO_LOGIN") {
            composeTestRule.onNode(otpInputMatcher).performTextReplacement("")
            composeTestRule.waitForIdle()
            composeTestRule.onNode(otpInputMatcher).performTextReplacement(realOtp)

            waitForTagAbsent("verify_email_screen", timeoutMs = 20_000L)
            waitForTag("login_screen", timeoutMs = 20_000L)
            waitForTag("confirmation_banner", timeoutMs = 10_000L)
            composeTestRule.onNodeWithTag("confirmation_banner").assertIsDisplayed()
        }
    }
}
