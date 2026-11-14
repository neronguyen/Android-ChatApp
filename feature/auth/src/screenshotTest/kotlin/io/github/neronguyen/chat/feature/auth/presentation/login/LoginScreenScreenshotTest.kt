package io.github.neronguyen.chat.feature.auth.presentation.login

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest

class LoginScreenScreenshotTest {

    @PreviewTest
    @Preview(name = "Default", showBackground = true, widthDp = 390, heightDp = 844)
    @Composable
    fun loginScreenDefault() = LoginScreenDefaultPreview()

    @PreviewTest
    @Preview(name = "ErrorAndBanner", showBackground = true, widthDp = 390, heightDp = 844)
    @Composable
    fun loginScreenErrorAndBanner() = LoginScreenErrorAndBannerPreview()
}
