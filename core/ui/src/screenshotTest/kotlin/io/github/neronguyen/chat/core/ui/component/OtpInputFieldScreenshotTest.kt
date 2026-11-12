package io.github.neronguyen.chat.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.github.neronguyen.chat.core.ui.theme.ChatTheme

class OtpInputFieldScreenshotTest {

    @PreviewTest
    @Preview(name = "Empty", showBackground = true, widthDp = 390)
    @Composable
    fun OtpInputFieldEmpty() = ChatTheme {
        OtpInputField(
            value = "",
            onValueChange = {},
        )
    }

    @PreviewTest
    @Preview(name = "Partial", showBackground = true, widthDp = 390)
    @Composable
    fun OtpInputFieldPartial() = ChatTheme {
        OtpInputField(
            value = "123",
            onValueChange = {},
        )
    }

    @PreviewTest
    @Preview(name = "Full", showBackground = true, widthDp = 390)
    @Composable
    fun OtpInputFieldFull() = ChatTheme {
        OtpInputField(
            value = "123456",
            onValueChange = {},
        )
    }

    @PreviewTest
    @Preview(name = "Error", showBackground = true, widthDp = 390)
    @Composable
    fun OtpInputFieldError() = ChatTheme {
        OtpInputField(
            value = "123456",
            onValueChange = {},
            isError = true,
        )
    }
}
