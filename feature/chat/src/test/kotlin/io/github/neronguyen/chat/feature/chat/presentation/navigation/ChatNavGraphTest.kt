package io.github.neronguyen.chat.feature.chat.presentation.navigation

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import arrow.core.Either
import io.github.neronguyen.chat.core.model.AuthState
import io.github.neronguyen.chat.core.model.ChatSyncState
import io.github.neronguyen.chat.core.model.error.ChatError
import io.github.neronguyen.chat.core.testing.FakeAuthRepository
import io.github.neronguyen.chat.core.testing.FakeChatMessageRepository
import io.github.neronguyen.chat.core.testing.FakeChatRoomRepository
import io.github.neronguyen.chat.core.testing.FakeChatSyncManager
import io.github.neronguyen.chat.core.testing.TestFixtures
import io.github.neronguyen.chat.core.ui.theme.ChatTheme
import io.github.neronguyen.chat.feature.chat.R
import io.github.neronguyen.chat.feature.chat.presentation.account.AccountViewModel
import io.github.neronguyen.chat.feature.chat.presentation.conversation.ConversationViewModel
import io.github.neronguyen.chat.feature.chat.presentation.list.ChatListViewModel
import io.github.neronguyen.chat.feature.chat.presentation.newchat.NewChatViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h844dp")
class ChatNavGraphTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun str(resId: Int): String = RuntimeEnvironment.getApplication().getString(resId)

    @Test
    fun chatList_toNewChat_toConversation_popsNewChatFromBackStack() {
        val fakeAuthRepo = FakeAuthRepository(
            initialAuthState = AuthState.Authenticated(TestFixtures.user(), "access-token"),
        )
        val fakeRoomRepo = FakeChatRoomRepository()
        val bob = TestFixtures.participant(
            userId = "00000000-0000-0000-0000-000000000002",
            email = "bob@example.com",
            displayName = "Bob",
        )
        fakeRoomRepo.findParticipantHandler = { email ->
            if (email == "bob@example.com") Either.Right(bob) else Either.Left(ChatError.ParticipantNotFound)
        }
        val fakeMessageRepo = FakeChatMessageRepository()
        val fakeSyncManager = FakeChatSyncManager(initialState = ChatSyncState.Synced)

        val providers = ChatNavViewModelProviders(
            chatListViewModel = { remember { ChatListViewModel(fakeRoomRepo, fakeAuthRepo, fakeSyncManager) } },
            newChatViewModel = { remember { NewChatViewModel(fakeRoomRepo, fakeAuthRepo) } },
            conversationViewModel = { roomId ->
                remember(roomId) {
                    ConversationViewModel(
                        roomId = roomId,
                        chatRoomRepository = fakeRoomRepo,
                        chatMessageRepository = fakeMessageRepo,
                        chatSyncManager = fakeSyncManager,
                        authRepository = fakeAuthRepo,
                    )
                }
            },
            accountViewModel = { remember { AccountViewModel(fakeAuthRepo, fakeSyncManager) } },
        )

        val openedRooms = mutableListOf<String>()

        composeTestRule.setContent {
            ChatTheme {
                ChatNavGraph(
                    viewModelProviders = providers,
                    onOpenConversation = { openedRooms.add(it) },
                )
            }
        }

        // ChatList -> NewChat
        composeTestRule.onNodeWithTag("chat_list_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chat_list_new_chat_fab").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("new_chat_screen").assertIsDisplayed()

        // Create direct room -> navigates to Conversation and pops NewChat
        composeTestRule.onNodeWithTag("new_chat_email_field").performTextReplacement("bob@example.com")
        composeTestRule.onNodeWithTag("new_chat_search_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("new_chat_searched_participant_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("new_chat_first_message_field")
            .performScrollTo()
            .performTextInput("Hi Bob")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("new_chat_submit_direct_button")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf("room-${bob.userId}"), openedRooms)
        composeTestRule.onNodeWithTag("conversation_message_input").assertIsDisplayed()

        // Pressing Back from Conversation returns directly to ChatList (NewChat was popped)
        composeTestRule.onNodeWithContentDescription(str(R.string.navigate_back)).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("chat_list_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("new_chat_screen").assertDoesNotExist()
    }
}
