package io.github.neronguyen.chat.feature.chat.presentation.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.neronguyen.chat.core.model.ChatSyncState
import io.github.neronguyen.chat.core.model.error.DataError
import io.github.neronguyen.chat.core.ui.theme.ChatTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AccountScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun accountScreen_displaysVerifiedProfileAndSyncedStatus_andDispatchesSignOutRequest() {
        val events = mutableListOf<AccountUiEvent>()
        var backClicked = false

        composeTestRule.setContent {
            ChatTheme {
                AccountScreen(
                    uiState = AccountUiState(
                        displayName = "Alex Lin",
                        email = "alex.lin@example.com",
                        initials = "AL",
                        isEmailVerified = true,
                        syncState = ChatSyncState.Synced,
                    ),
                    onEvent = { events.add(it) },
                    onBackClick = { backClicked = true },
                )
            }
        }

        composeTestRule.onNodeWithTag("account_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alex Lin").assertIsDisplayed()
        composeTestRule.onNodeWithText("Verified").assertIsDisplayed()
        composeTestRule.onNodeWithTag("account_sync_banner").assertIsDisplayed()
        composeTestRule.onNodeWithText("Realtime stream operational").assertIsDisplayed()

        composeTestRule.onNodeWithTag("account_sign_out_button")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        assertEquals(listOf(AccountUiEvent.RequestSignOut), events)

        composeTestRule.onNodeWithTag("account_back_button").performClick()
        assertTrue(backClicked)
    }

    @Test
    fun accountScreen_displaysUnverifiedAndOfflineState() {
        composeTestRule.setContent {
            ChatTheme {
                AccountScreen(
                    uiState = AccountUiState(
                        displayName = "Elena Rostova",
                        email = "elena@example.com",
                        initials = "ER",
                        isEmailVerified = false,
                        syncState = ChatSyncState.Offline,
                    ),
                    onEvent = {},
                    onBackClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Unverified").assertIsDisplayed()
        composeTestRule.onNodeWithText("Offline — waiting for connection").assertIsDisplayed()
    }

    @Test
    fun accountScreen_confirmationDialogDispatchesConfirmAndCancel() {
        val events = mutableListOf<AccountUiEvent>()

        composeTestRule.setContent {
            ChatTheme {
                AccountScreen(
                    uiState = AccountUiState(
                        displayName = "Alex Lin",
                        email = "alex.lin@example.com",
                        initials = "AL",
                        showSignOutConfirmation = true,
                    ),
                    onEvent = { events.add(it) },
                    onBackClick = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("account_sign_out_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("account_sign_out_confirm_button").performClick()
        composeTestRule.onNodeWithTag("account_sign_out_cancel_button").performClick()

        assertEquals(
            listOf(AccountUiEvent.ConfirmSignOut, AccountUiEvent.DismissSignOutConfirmation),
            events,
        )
    }

    @Test
    fun accountScreen_disablesSignOutWhenSigningOut_andDisplaysSignOutError() {
        composeTestRule.setContent {
            ChatTheme {
                AccountScreen(
                    uiState = AccountUiState(
                        displayName = "Alex Lin",
                        email = "alex.lin@example.com",
                        initials = "AL",
                        isSigningOut = true,
                        signOutError = DataError.Connectivity.NoInternet,
                    ),
                    onEvent = {},
                    onBackClick = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("account_sign_out_error")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("account_sign_out_button")
            .performScrollTo()
            .assertIsNotEnabled()
        composeTestRule.onNodeWithText("Signing out…").assertIsDisplayed()
    }
}
