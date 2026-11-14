package io.github.neronguyen.chat.feature.chat.presentation.conversation

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest

class ConversationScreenScreenshotTest {

    @PreviewTest
    @Preview(name = "Direct", showBackground = true, widthDp = 390, heightDp = 844)
    @Composable
    fun conversationScreenDirect() = ConversationScreenDirectPreview()

    @PreviewTest
    @Preview(name = "DeliveryStatesOffline", showBackground = true, widthDp = 390, heightDp = 844)
    @Composable
    fun conversationScreenDeliveryStatesOffline() =
        ConversationScreenDeliveryStatesOfflinePreview()
}
