package io.github.neronguyen.chat.feature.chat.presentation.account

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest

class AccountScreenScreenshotTest {

    @PreviewTest
    @Preview(name = "VerifiedConnected", showBackground = true, widthDp = 390, heightDp = 844)
    @Composable
    fun accountScreenVerifiedConnected() = AccountScreenVerifiedConnectedPreview()

    @PreviewTest
    @Preview(name = "UnverifiedOffline", showBackground = true, widthDp = 390, heightDp = 844)
    @Composable
    fun accountScreenUnverifiedOffline() = AccountScreenUnverifiedOfflinePreview()
}
