package io.github.neronguyen.chat.feature.chat.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import arrow.core.Either
import io.github.neronguyen.chat.core.model.ChatParticipant
import io.github.neronguyen.chat.core.model.ChatRoom
import io.github.neronguyen.chat.core.model.ChatRoomType
import io.github.neronguyen.chat.core.model.ChatSyncState
import io.github.neronguyen.chat.core.model.error.ChatError
import io.github.neronguyen.chat.core.ui.theme.ChatTheme
import io.github.neronguyen.chat.feature.chat.R
import io.github.neronguyen.chat.feature.chat.presentation.account.AccountRoute
import io.github.neronguyen.chat.feature.chat.presentation.account.AccountViewModel
import io.github.neronguyen.chat.feature.chat.presentation.conversation.ConversationRoute
import io.github.neronguyen.chat.feature.chat.presentation.conversation.ConversationViewModel
import io.github.neronguyen.chat.feature.chat.presentation.newchat.NewChatRoute
import io.github.neronguyen.chat.feature.chat.presentation.newchat.NewChatViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.ZoneId
import kotlin.time.Instant

/**
 * Tier 1/2 Feature Tests integrating Compose `*Route` screens, real ViewModels, and Fake chat
 * repositories / sync manager on the JVM under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h844dp")
class ChatFeatureTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun str(resId: Int, vararg args: Any): String =
        RuntimeEnvironment.getApplication().getString(resId, *args)

    @Test
    fun newChatRoute_displaysSearchError_thenFindsPeerAndCreatesDirectRoom() {
        val fakeAuthRepo = FakeChatAuthRepository()
        val fakeRoomRepo = FakeChatRoomRepository()
        val bobId = "00000000-0000-0000-0000-000000000002"
        val bob = ChatParticipant(
            userId = bobId,
            email = "bob@example.com",
            displayName = "Bob",
        )
        fakeRoomRepo.findParticipantHandler = { email ->
            if (email == "bob@example.com") {
                Either.Right(bob)
            } else {
                Either.Left(ChatError.ParticipantNotFound)
            }
        }
        val createdRooms = mutableListOf<String>()
        val viewModel = NewChatViewModel(fakeRoomRepo, fakeAuthRepo)

        composeTestRule.setContent {
            ChatTheme {
                NewChatRoute(
                    viewModel = viewModel,
                    onBackClick = {},
                    onChatCreated = { createdRooms.add(it) },
                )
            }
        }

        composeTestRule.onNodeWithTag("new_chat_email_field")
            .performTextReplacement("unknown@example.com")
        composeTestRule.onNodeWithTag("new_chat_search_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("new_chat_error_banner").assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.chat_error_participant_not_found))
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("new_chat_email_field")
            .performTextReplacement("bob@example.com")
        composeTestRule.onNodeWithTag("new_chat_search_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("new_chat_searched_participant_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("new_chat_first_message_field")
            .performScrollTo()
            .performTextInput("Hello Bob")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("new_chat_submit_direct_button")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf("room-$bobId"), createdRooms)
    }

    @Test
    fun conversationRoute_sendsMessage_andReactsToOfflineSyncRecovery() {
        val room = ChatRoom(
            id = "room-1",
            type = ChatRoomType.DIRECT,
            creator = ChatParticipant("user-me", "alice@example.com", "Alice"),
            participants = listOf(
                ChatParticipant("user-me", "alice@example.com", "Alice"),
                ChatParticipant("user-bob", "bob@example.com", "Bob"),
            ),
        )
        val fakeAuthRepo = FakeChatAuthRepository()
        val fakeRoomRepo = FakeChatRoomRepository(initialRooms = listOf(room))
        val fakeMessageRepo = FakeChatMessageRepository()
        val fakeSyncManager = FakeChatSyncManager(initialState = ChatSyncState.Synced)

        val viewModel = ConversationViewModel(
            roomId = "room-1",
            chatRoomRepository = fakeRoomRepo,
            chatMessageRepository = fakeMessageRepo,
            chatSyncManager = fakeSyncManager,
            authRepository = fakeAuthRepo,
        ).apply {
            nowProvider = { Instant.parse("2026-11-16T12:00:00Z") }
            zoneIdProvider = { ZoneId.of("UTC") }
        }

        composeTestRule.setContent {
            ChatTheme {
                ConversationRoute(
                    viewModel = viewModel,
                    onBackClick = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("conversation_message_input")
            .performTextReplacement("Hello from JVM")
        composeTestRule.onNodeWithTag("conversation_send_button")
            .assertIsEnabled()
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf("room-1" to "Hello from JVM"), fakeMessageRepo.sentMessages)
        composeTestRule.onNodeWithText("Hello from JVM").assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.conversation_status_sent)).assertIsDisplayed()

        // Offline transition surfaces sync banner and disables send while preserving draft
        composeTestRule.onNodeWithTag("conversation_message_input")
            .performTextReplacement("Draft while offline")
        fakeSyncManager.syncStateFlow.value = ChatSyncState.Offline
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("sync_status_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("conversation_send_button").assertIsNotEnabled()

        // Recovering to Synced hides sync banner and re-enables send button
        fakeSyncManager.syncStateFlow.value = ChatSyncState.Synced
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("sync_status_bar").assertDoesNotExist()
        composeTestRule.onNodeWithTag("conversation_send_button").assertIsEnabled()
    }

    @Test
    fun accountRoute_displaysSyncState_andConfirmsSignOut() {
        val fakeAuthRepo = FakeChatAuthRepository()
        val fakeSyncManager = FakeChatSyncManager(initialState = ChatSyncState.Synced)
        val viewModel = AccountViewModel(fakeAuthRepo, fakeSyncManager)

        composeTestRule.setContent {
            ChatTheme {
                AccountRoute(
                    viewModel = viewModel,
                    onBackClick = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("account_sign_out_button")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("account_sign_out_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("account_sign_out_confirm_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, fakeAuthRepo.logoutCallCount)
    }
}
