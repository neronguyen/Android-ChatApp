package io.github.neronguyen.chat.core.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.neronguyen.chat.core.model.ChatSyncState
import io.github.neronguyen.chat.core.model.error.DataError
import io.github.neronguyen.chat.core.ui.theme.ChatTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncStatusBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun syncStatusBar_hiddenWhenIdle() {
        composeTestRule.setContent {
            ChatTheme {
                SyncStatusBar(
                    syncState = ChatSyncState.Idle,
                    onRetry = {},
                    onDismiss = {},
                    animate = false,
                )
            }
        }

        composeTestRule.onNodeWithTag("sync_status_bar").assertDoesNotExist()
    }

    @Test
    fun syncStatusBar_hiddenWhenSynced() {
        composeTestRule.setContent {
            ChatTheme {
                SyncStatusBar(
                    syncState = ChatSyncState.Synced,
                    onRetry = {},
                    onDismiss = {},
                    animate = false,
                )
            }
        }

        composeTestRule.onNodeWithTag("sync_status_bar").assertDoesNotExist()
    }

    @Test
    fun syncStatusBar_visibleWhenOffline_andNoRetryButton() {
        var dismissed = false

        composeTestRule.setContent {
            ChatTheme {
                SyncStatusBar(
                    syncState = ChatSyncState.Offline,
                    onRetry = {},
                    onDismiss = { dismissed = true },
                    animate = false,
                )
            }
        }

        composeTestRule.onNodeWithTag("sync_status_bar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Waiting for network…").assertIsDisplayed()
        composeTestRule.onNodeWithTag("sync_status_retry_button").assertDoesNotExist()

        composeTestRule.onNodeWithTag("sync_status_dismiss_button").performClick()
        assertEquals(true, dismissed)
    }

    @Test
    fun syncStatusBar_visibleWhenConnecting_andNoRetryButton() {
        composeTestRule.setContent {
            ChatTheme {
                SyncStatusBar(
                    syncState = ChatSyncState.Connecting,
                    onRetry = {},
                    onDismiss = {},
                    animate = false,
                )
            }
        }

        composeTestRule.onNodeWithTag("sync_status_bar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Connecting…").assertIsDisplayed()
        composeTestRule.onNodeWithTag("sync_status_retry_button").assertDoesNotExist()
    }

    @Test
    fun syncStatusBar_visibleWhenSyncing_andNoRetryButton() {
        composeTestRule.setContent {
            ChatTheme {
                SyncStatusBar(
                    syncState = ChatSyncState.Syncing,
                    onRetry = {},
                    onDismiss = {},
                    animate = false,
                )
            }
        }

        composeTestRule.onNodeWithTag("sync_status_bar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Syncing messages…").assertIsDisplayed()
        composeTestRule.onNodeWithTag("sync_status_retry_button").assertDoesNotExist()
    }

    @Test
    fun syncStatusBar_visibleWhenFailed_showsRetryButton_andHandlesClicks() {
        var retried = false
        var dismissed = false

        composeTestRule.setContent {
            ChatTheme {
                SyncStatusBar(
                    syncState = ChatSyncState.Failed(DataError.Remote.Unavailable),
                    onRetry = { retried = true },
                    onDismiss = { dismissed = true },
                    animate = false,
                )
            }
        }

        composeTestRule.onNodeWithTag("sync_status_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("sync_status_retry_button").assertIsDisplayed()

        composeTestRule.onNodeWithTag("sync_status_retry_button").performClick()
        assertEquals(true, retried)

        composeTestRule.onNodeWithTag("sync_status_dismiss_button").performClick()
        assertEquals(true, dismissed)
    }
}
