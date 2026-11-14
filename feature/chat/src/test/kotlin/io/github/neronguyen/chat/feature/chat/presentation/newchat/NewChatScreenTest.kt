package io.github.neronguyen.chat.feature.chat.presentation.newchat

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.neronguyen.chat.core.model.error.ChatError
import io.github.neronguyen.chat.core.ui.theme.ChatTheme
import io.github.neronguyenvn.nerochat.contract.validation.chat.ChatValidationError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NewChatScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val elena = ParticipantUiModel(
        userId = "user-elena",
        email = "elena@example.com",
        displayName = "Elena Rostova",
        initials = "ER",
        avatarUrl = null,
    )

    private val marcus = ParticipantUiModel(
        userId = "user-marcus",
        email = "marcus@example.com",
        displayName = "Marcus Vance",
        initials = "MV",
        avatarUrl = null,
    )

    @Test
    fun newChatScreen_switchesBetweenDirectAndGroupModes_andDispatchesSearchAndBack() {
        val events = mutableListOf<NewChatUiEvent>()
        var backClicked = false
        val emailState = TextFieldState().apply { setTextAndPlaceCursorAtEnd("elena@example.com") }

        composeTestRule.setContent {
            ChatTheme {
                NewChatScreen(
                    emailSearchState = emailState,
                    firstMessageState = TextFieldState(),
                    groupNameState = TextFieldState(),
                    uiState = NewChatUiState(mode = NewChatMode.DIRECT),
                    onEvent = { events.add(it) },
                    onBackClick = { backClicked = true },
                )
            }
        }

        composeTestRule.onNodeWithTag("new_chat_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("new_chat_submit_direct_button")
            .performScrollTo()
            .assertIsNotEnabled()

        composeTestRule.onNodeWithTag("new_chat_search_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("new_chat_mode_group").performScrollTo().performClick()
        assertEquals(
            listOf(NewChatUiEvent.SearchByEmail, NewChatUiEvent.SelectMode(NewChatMode.GROUP)),
            events,
        )

        composeTestRule.onNodeWithTag("new_chat_back_button").performClick()
        assertTrue(backClicked)
    }

    @Test
    fun newChatScreen_directMode_displaysSearchedParticipantAndSubmitsWhenReady() {
        val events = mutableListOf<NewChatUiEvent>()

        composeTestRule.setContent {
            ChatTheme {
                NewChatScreen(
                    emailSearchState = TextFieldState("elena@example.com"),
                    firstMessageState = TextFieldState("Hello Elena!"),
                    groupNameState = TextFieldState(),
                    uiState = NewChatUiState(
                        mode = NewChatMode.DIRECT,
                        searchedParticipant = elena,
                        selectedDirectParticipant = elena,
                        firstMessage = "Hello Elena!",
                    ),
                    onEvent = { events.add(it) },
                    onBackClick = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("new_chat_searched_participant_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Elena Rostova").assertIsDisplayed()

        composeTestRule.onNodeWithTag("new_chat_searched_participant_card").performClick()
        composeTestRule.onNodeWithTag("new_chat_submit_direct_button")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        assertEquals(
            listOf(NewChatUiEvent.ToggleSearchedParticipant, NewChatUiEvent.SubmitChat),
            events,
        )
    }

    @Test
    fun newChatScreen_groupMode_displaysSelectedChipsAndSubmitsGroup() {
        val events = mutableListOf<NewChatUiEvent>()

        composeTestRule.setContent {
            ChatTheme {
                NewChatScreen(
                    emailSearchState = TextFieldState(),
                    firstMessageState = TextFieldState(),
                    groupNameState = TextFieldState("Core Team"),
                    uiState = NewChatUiState(
                        mode = NewChatMode.GROUP,
                        selectedGroupParticipants = listOf(elena, marcus),
                        groupName = "Core Team",
                    ),
                    onEvent = { events.add(it) },
                    onBackClick = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("new_chat_participant_chip_user-elena").assertIsDisplayed()
        composeTestRule.onNodeWithTag("new_chat_participant_chip_user-marcus").assertIsDisplayed()

        composeTestRule.onNodeWithTag("new_chat_remove_participant_user-elena")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("new_chat_submit_group_button")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        assertEquals(
            listOf(
                NewChatUiEvent.RemoveGroupParticipant("user-elena"),
                NewChatUiEvent.SubmitChat,
            ),
            events,
        )
    }

    @Test
    fun newChatScreen_displaysValidationAndSearchErrors() {
        composeTestRule.setContent {
            ChatTheme {
                NewChatScreen(
                    emailSearchState = TextFieldState(),
                    firstMessageState = TextFieldState(),
                    groupNameState = TextFieldState(),
                    uiState = NewChatUiState(
                        validationError = ChatValidationError.EmptySearchEmail,
                    ),
                    onEvent = {},
                    onBackClick = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("new_chat_error_banner").assertIsDisplayed()
        composeTestRule.onNodeWithText("Please enter an email address.").assertIsDisplayed()
    }

    @Test
    fun newChatScreen_displaysParticipantNotFoundError() {
        composeTestRule.setContent {
            ChatTheme {
                NewChatScreen(
                    emailSearchState = TextFieldState("missing@example.com"),
                    firstMessageState = TextFieldState(),
                    groupNameState = TextFieldState(),
                    uiState = NewChatUiState(
                        searchError = ChatError.ParticipantNotFound,
                    ),
                    onEvent = {},
                    onBackClick = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("new_chat_error_banner").assertIsDisplayed()
        composeTestRule.onNodeWithText("Participant not found").assertIsDisplayed()
    }
}
