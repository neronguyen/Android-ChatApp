package io.github.neronguyen.chat.feature.chat.presentation.conversation

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.neronguyen.chat.core.model.ChatRoomType
import io.github.neronguyen.chat.core.model.ChatSyncState
import io.github.neronguyen.chat.core.model.error.DataError
import io.github.neronguyen.chat.core.ui.theme.ChatTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConversationScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun sendButton_disabledWhenOffline_evenWithText_preservesComposerInput() {
        val composerState = TextFieldState()
        composerState.setTextAndPlaceCursorAtEnd("Offline staged draft")

        val uiState = ConversationUiState(
            roomId = "r1",
            roomType = ChatRoomType.DIRECT,
            title = "Elena Rostova",
            syncState = ChatSyncState.Offline,
        )

        assertEquals(false, uiState.canSendMessage)

        composeTestRule.setContent {
            ChatTheme {
                ConversationScreen(
                    uiState = uiState,
                    composerState = composerState,
                    addParticipantEmailState = TextFieldState(),
                    onEvent = {},
                    onBackClick = {},
                )
            }
        }

        // Send button is disabled
        composeTestRule.onNodeWithTag("conversation_send_button").assertIsNotEnabled()

        // Composer text is intact and preserved
        assertEquals("Offline staged draft", composerState.text.toString())
    }

    @Test
    fun sendButton_disabledWhenConnecting_evenWithText() {
        val composerState = TextFieldState()
        composerState.setTextAndPlaceCursorAtEnd("Connecting draft")

        val uiState = ConversationUiState(
            roomId = "r1",
            roomType = ChatRoomType.DIRECT,
            title = "Elena Rostova",
            syncState = ChatSyncState.Connecting,
        )

        assertEquals(false, uiState.canSendMessage)

        composeTestRule.setContent {
            ChatTheme {
                ConversationScreen(
                    uiState = uiState,
                    composerState = composerState,
                    addParticipantEmailState = TextFieldState(),
                    onEvent = {},
                    onBackClick = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("conversation_send_button").assertIsNotEnabled()
    }

    @Test
    fun sendButton_enabledWhenSynced_andDispatchesSendMessage() {
        val events = mutableListOf<ConversationUiEvent>()
        val composerState = TextFieldState()
        composerState.setTextAndPlaceCursorAtEnd("Ready to send")

        val uiState = ConversationUiState(
            roomId = "r1",
            roomType = ChatRoomType.DIRECT,
            title = "Elena Rostova",
            syncState = ChatSyncState.Synced,
        )

        assertEquals(true, uiState.canSendMessage)

        composeTestRule.setContent {
            ChatTheme {
                ConversationScreen(
                    uiState = uiState,
                    composerState = composerState,
                    addParticipantEmailState = TextFieldState(),
                    onEvent = { events.add(it) },
                    onBackClick = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("conversation_send_button").performClick()
        assertEquals(listOf(ConversationUiEvent.SendMessage), events)
    }

    @Test
    fun syncStatusBar_visibleOnFailure_andDispatchesRetryAndDismiss() {
        val events = mutableListOf<ConversationUiEvent>()
        val uiState = ConversationUiState(
            roomId = "r1",
            roomType = ChatRoomType.DIRECT,
            title = "Elena Rostova",
            syncState = ChatSyncState.Failed(DataError.Remote.Unavailable),
            isBannerDismissed = false,
        )

        assertEquals(true, uiState.showSyncBanner)

        composeTestRule.setContent {
            ChatTheme {
                ConversationScreen(
                    uiState = uiState,
                    composerState = TextFieldState(),
                    addParticipantEmailState = TextFieldState(),
                    onEvent = { events.add(it) },
                    onBackClick = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("sync_status_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("sync_status_retry_button").assertIsDisplayed()

        composeTestRule.onNodeWithTag("sync_status_retry_button").performClick()
        assertEquals(listOf(ConversationUiEvent.RetrySync), events)

        composeTestRule.onNodeWithTag("sync_status_dismiss_button").performClick()
        assertEquals(
            listOf(ConversationUiEvent.RetrySync, ConversationUiEvent.DismissBanner),
            events,
        )
    }

    @Test
    fun syncStatusBar_hiddenWhenDismissed() {
        val uiState = ConversationUiState(
            roomId = "r1",
            roomType = ChatRoomType.DIRECT,
            title = "Elena Rostova",
            syncState = ChatSyncState.Offline,
            isBannerDismissed = true,
        )

        assertEquals(false, uiState.showSyncBanner)

        composeTestRule.setContent {
            ChatTheme {
                ConversationScreen(
                    uiState = uiState,
                    composerState = TextFieldState(),
                    addParticipantEmailState = TextFieldState(),
                    onEvent = {},
                    onBackClick = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("sync_status_bar").assertDoesNotExist()
    }
}
