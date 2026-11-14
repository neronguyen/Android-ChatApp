package io.github.neronguyen.chat.feature.auth.presentation.verify

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest

class VerifyEmailScreenScreenshotTest {

    @PreviewTest
    @Preview(name = "Default", showBackground = true, widthDp = 390, heightDp = 844)
    @Composable
    fun verifyEmailScreenDefault() = VerifyEmailScreenDefaultPreview()

    @PreviewTest
    @Preview(name = "Cooldown", showBackground = true, widthDp = 390, heightDp = 844)
    @Composable
    fun verifyEmailScreenCooldown() = VerifyEmailScreenCooldownPreview()
}
