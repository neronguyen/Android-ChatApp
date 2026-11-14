package io.github.neronguyen.chat.feature.chat.presentation.newchat

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest

class NewChatScreenScreenshotTest {

    @PreviewTest
    @Preview(name = "Direct", showBackground = true, widthDp = 390, heightDp = 844)
    @Composable
    fun newChatDirect() = NewChatDirectPreview()

    @PreviewTest
    @Preview(name = "Group", showBackground = true, widthDp = 390, heightDp = 844)
    @Composable
    fun newChatGroup() = NewChatGroupPreview()
}
