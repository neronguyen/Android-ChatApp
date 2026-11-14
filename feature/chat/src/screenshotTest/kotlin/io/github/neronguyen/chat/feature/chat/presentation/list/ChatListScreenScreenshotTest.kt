package io.github.neronguyen.chat.feature.chat.presentation.list

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest

class ChatListScreenScreenshotTest {

    @PreviewTest
    @Preview(name = "Populated", showBackground = true, widthDp = 390, heightDp = 844)
    @Composable
    fun chatListScreenPopulated() = ChatListScreenPopulatedPreview()

    @PreviewTest
    @Preview(name = "Offline", showBackground = true, widthDp = 390, heightDp = 844)
    @Composable
    fun chatListScreenOffline() = ChatListScreenOfflinePreview()
}
