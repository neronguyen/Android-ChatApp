package io.github.neronguyen.chat.feature.auth.presentation.register

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest

class RegisterScreenScreenshotTest {

    @PreviewTest
    @Preview(name = "Default", showBackground = true, widthDp = 390, heightDp = 844)
    @Composable
    fun registerScreenDefault() = RegisterScreenDefaultPreview()

    @PreviewTest
    @Preview(name = "ValidationError", showBackground = true, widthDp = 390, heightDp = 844)
    @Composable
    fun registerScreenValidationError() = RegisterScreenValidationErrorPreview()
}
