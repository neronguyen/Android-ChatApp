package io.github.neronguyen.chat.core.ui.component

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import io.github.neronguyen.chat.core.ui.theme.ChatTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OtpInputFieldTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun otpInputField_rendersAllSixCells() {
        composeTestRule.setContent {
            ChatTheme {
                OtpInputField(
                    value = "",
                    onValueChange = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("otp_input_field").assertIsDisplayed()
        for (i in 0 until 6) {
            composeTestRule.onNodeWithTag("otp_cell_$i", useUnmergedTree = true).assertExists()
        }
    }

    @Test
    fun otpInputField_displaysEnteredDigits() {
        composeTestRule.setContent {
            ChatTheme {
                OtpInputField(
                    value = "123",
                    onValueChange = {},
                )
            }
        }

        composeTestRule.onNodeWithText("1").assertIsDisplayed()
        composeTestRule.onNodeWithText("2").assertIsDisplayed()
        composeTestRule.onNodeWithText("3").assertIsDisplayed()
        composeTestRule.onNodeWithTag("otp_input_field")
            .assertContentDescriptionEquals("Verification code, 3 of 6 digits entered")
    }

    @Test
    fun otpInputField_invokesOnCompleteWhenSixDigitsEntered() {
        var completedCode = ""

        composeTestRule.setContent {
            ChatTheme {
                OtpInputField(
                    value = "123456",
                    onValueChange = {},
                    onComplete = { completedCode = it },
                )
            }
        }

        assertEquals("123456", completedCode)
    }
}
