package io.github.neronguyen.chat.feature.chat.presentation.list

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
class ChatListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun syncStatusBar_visibleWhenShowSyncBannerIsTrue() {
        val uiState = ChatListUiState(
            chats = emptyList(),
            syncState = ChatSyncState.Offline,
            isInitialLoadComplete = true,
            isBannerDismissed = false,
        )

        composeTestRule.setContent {
            ChatTheme {
                ChatListScreen(
                    searchState = TextFieldState(),
                    uiState = uiState,
                    onEvent = {},
                    onOpenConversation = {},
                    onNewChatClick = {},
                    onAccountClick = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("sync_status_bar").assertIsDisplayed()
    }

    @Test
    fun syncStatusBar_hiddenWhenDismissed() {
        val uiState = ChatListUiState(
            chats = emptyList(),
            syncState = ChatSyncState.Offline,
            isInitialLoadComplete = true,
            isBannerDismissed = true,
        )

        composeTestRule.setContent {
            ChatTheme {
                ChatListScreen(
                    searchState = TextFieldState(),
                    uiState = uiState,
                    onEvent = {},
                    onOpenConversation = {},
                    onNewChatClick = {},
                    onAccountClick = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("sync_status_bar").assertDoesNotExist()
    }

    @Test
    fun syncStatusBar_suppressedDuringInitialLoading() {
        val uiState = ChatListUiState(
            chats = emptyList(),
            syncState = ChatSyncState.Connecting,
            isInitialLoadComplete = false,
            isBannerDismissed = false,
        )

        assertEquals(true, uiState.isInitialLoading)
        assertEquals(false, uiState.showSyncBanner)

        composeTestRule.setContent {
            ChatTheme {
                ChatListScreen(
                    searchState = TextFieldState(),
                    uiState = uiState,
                    onEvent = {},
                    onOpenConversation = {},
                    onNewChatClick = {},
                    onAccountClick = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("sync_status_bar").assertDoesNotExist()
    }

    @Test
    fun syncStatusBar_dispatchesRetryAndDismissEvents() {
        val events = mutableListOf<ChatListUiEvent>()
        val uiState = ChatListUiState(
            chats = emptyList(),
            syncState = ChatSyncState.Failed(DataError.Remote.Unavailable),
            isInitialLoadComplete = true,
            isBannerDismissed = false,
        )

        composeTestRule.setContent {
            ChatTheme {
                ChatListScreen(
                    searchState = TextFieldState(),
                    uiState = uiState,
                    onEvent = { events.add(it) },
                    onOpenConversation = {},
                    onNewChatClick = {},
                    onAccountClick = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("sync_status_retry_button").performClick()
        assertEquals(listOf(ChatListUiEvent.RefreshChats), events)

        composeTestRule.onNodeWithTag("sync_status_dismiss_button").performClick()
        assertEquals(
            listOf(ChatListUiEvent.RefreshChats, ChatListUiEvent.DismissSyncBanner),
            events,
        )
    }
}
