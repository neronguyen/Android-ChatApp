package io.github.neronguyen.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.github.neronguyen.chat.core.model.AuthState
import io.github.neronguyen.chat.core.testing.FakeAuthRepository
import io.github.neronguyen.chat.core.testing.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import javax.inject.Inject
import io.github.neronguyen.chat.feature.chat.R as ChatR

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(
    application = HiltTestApplication::class,
    qualifiers = "w390dp-h844dp",
)
class MainActivityNavigationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var fakeAuthRepository: FakeAuthRepository

    @Test
    fun mainActivity_switchesBetweenAuthNavGraphAndChatNavGraph_onAuthStateChanges() {
        hiltRule.inject()
        composeTestRule.waitForIdle()

        // Starts unauthenticated -> AuthNavGraph (LoginScreen)
        composeTestRule.onNodeWithTag("login_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chat_list_screen").assertDoesNotExist()

        // Transition to Authenticated -> ChatNavGraph (ChatListScreen)
        fakeAuthRepository.authStateFlow.value = AuthState.Authenticated(
            user = TestFixtures.user(),
            accessToken = "access-token",
        )
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("chat_list_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("login_screen").assertDoesNotExist()

        // Transition back to Unauthenticated (e.g. token refresh failure) -> AuthNavGraph
        fakeAuthRepository.authStateFlow.value = AuthState.Unauthenticated
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("login_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chat_list_screen").assertDoesNotExist()
    }

    @Test
    fun mainActivity_signingOutFromAccountRoute_returnsToAuthNavGraph() {
        hiltRule.inject()
        fakeAuthRepository.authStateFlow.value = AuthState.Authenticated(
            user = TestFixtures.user(),
            accessToken = "access-token",
        )
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("chat_list_screen").assertIsDisplayed()

        // Navigate ChatList -> Account -> Sign Out -> Confirm
        val accountCd = RuntimeEnvironment.getApplication().getString(ChatR.string.account)
        composeTestRule.onNodeWithContentDescription(accountCd).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("account_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("account_sign_out_button")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("account_sign_out_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("account_sign_out_confirm_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, fakeAuthRepository.logoutCallCount)
        composeTestRule.onNodeWithTag("login_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chat_list_screen").assertDoesNotExist()
    }
}
