package io.github.neronguyen.chat.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.github.neronguyen.chat.core.model.ChatSyncState
import io.github.neronguyen.chat.core.model.error.DataError
import io.github.neronguyen.chat.core.ui.theme.ChatTheme

class SyncStatusBarScreenshotTest {

    @PreviewTest
    @Preview(name = "Offline", showBackground = true, widthDp = 390)
    @Composable
    fun syncStatusBarOffline() = ChatTheme {
        SyncStatusBar(
            syncState = ChatSyncState.Offline,
            onRetry = {},
            onDismiss = {},
            animate = false,
        )
    }

    @PreviewTest
    @Preview(name = "Connecting", showBackground = true, widthDp = 390)
    @Composable
    fun syncStatusBarConnecting() = ChatTheme {
        SyncStatusBar(
            syncState = ChatSyncState.Connecting,
            onRetry = {},
            onDismiss = {},
            animate = false,
        )
    }

    @PreviewTest
    @Preview(name = "Syncing", showBackground = true, widthDp = 390)
    @Composable
    fun syncStatusBarSyncing() = ChatTheme {
        SyncStatusBar(
            syncState = ChatSyncState.Syncing,
            onRetry = {},
            onDismiss = {},
            animate = false,
        )
    }

    @PreviewTest
    @Preview(name = "Failed", showBackground = true, widthDp = 390)
    @Composable
    fun syncStatusBarFailed() = ChatTheme {
        SyncStatusBar(
            syncState = ChatSyncState.Failed(DataError.Remote.Unavailable),
            onRetry = {},
            onDismiss = {},
            animate = false,
        )
    }
}
