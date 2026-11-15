package io.github.neronguyen.chat.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import io.github.neronguyen.chat.feature.chat.R as ChatR

@RunWith(AndroidJUnit4::class)
class FullStackSmokeE2ETest : BaseE2EJourneyTest() {

    override val journeyId: String = "happy-path"

    @Test
    fun fullStackSmokeJourney_completesAllEightStates() = runJourney {
        val epoch = System.currentTimeMillis() / 1000L
        val aliceEmail = "alice.$epoch@example.com"
        val aliceDisplayName = "Alice E2E"
        val alicePassword = "AlicePassword123!"
        val bobEmail = E2EPeerClient.DEFAULT_BOB_EMAIL

        peerClient.ensureVerifiedPeerBob()

        step("00_register_form", "S0_REGISTER_FORM") {
            waitForTag("login_screen", timeoutMs = 15_000L)
            composeTestRule.onNodeWithTag("register_button").performClick()

            waitForTag("register_screen", timeoutMs = 10_000L)
            composeTestRule.onNodeWithTag("email_field").performTextReplacement(aliceEmail)
            composeTestRule.onNodeWithTag("display_name_field")
                .performTextReplacement(aliceDisplayName)
            composeTestRule.onNodeWithTag("password_field").performTextReplacement(alicePassword)
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("register_error_text").assertTextEquals("")
        }

        step("01_otp_challenge", "S1_OTP_CHALLENGE") {
            composeTestRule.onNodeWithTag("register_button").performClick()
            waitForTag("verify_email_screen", timeoutMs = 20_000L)
            composeTestRule.onNodeWithTag("otp_input_field").assertIsDisplayed()
        }

        step("02_login_verified", "S2_LOGIN_VERIFIED") {
            val aliceOtp = peerClient.fetchLatestOtp(aliceEmail)
            composeTestRule.onNode(
                hasSetTextAction() and hasAnyAncestor(hasTestTag("otp_input_field")),
            ).performTextInput(aliceOtp)
            waitForTag("login_screen", timeoutMs = 20_000L)
            waitForTag("confirmation_banner", timeoutMs = 10_000L)
            composeTestRule.onNodeWithTag("confirmation_banner").assertIsDisplayed()
        }

        step("03_chat_list_empty", "S3_CHAT_LIST_EMPTY") {
            composeTestRule.onNodeWithTag("email_field").performTextReplacement(aliceEmail)
            composeTestRule.onNodeWithTag("password_field").performTextReplacement(alicePassword)
            composeTestRule.onNodeWithTag("login_button").performClick()

            waitForTag("chat_list_screen", timeoutMs = 20_000L)
            waitForTagAbsent("sync_status_bar", timeoutMs = 15_000L)
            composeTestRule.onNodeWithTag("chat_list_new_chat_fab").assertIsDisplayed()
        }

        step("04_new_chat_found_peer", "S4_NEW_CHAT_FOUND_PEER") {
            composeTestRule.onNodeWithTag("chat_list_new_chat_fab").performClick()
            waitForTag("new_chat_screen", timeoutMs = 10_000L)
            composeTestRule.onNodeWithTag("new_chat_email_field").performTextReplacement(bobEmail)
            composeTestRule.onNodeWithTag("new_chat_search_button").performClick()
            waitForTag("new_chat_searched_participant_card", timeoutMs = 20_000L)
            composeTestRule.onNodeWithTag("new_chat_searched_participant_card").assertIsDisplayed()
        }

        step("05_outbound_message_sent", "S5_OUTBOUND_MESSAGE_SENT") {
            val firstOutbound = "Hello Bob from AVD"
            val secondOutbound = "Follow-up from AVD"
            val sentLabel =
                composeTestRule.activity.getString(ChatR.string.conversation_status_sent)
            val sendingLabel =
                composeTestRule.activity.getString(ChatR.string.conversation_status_sending)

            // Direct mode auto-selects the searched participant in NewChatViewModel; do not tap the card.
            composeTestRule.onNodeWithTag("new_chat_first_message_field")
                .performScrollTo()
                .performTextReplacement(firstOutbound)
            composeTestRule.onNodeWithTag("new_chat_submit_direct_button")
                .performScrollTo()
                .performClick()

            waitForTag("conversation_message_input", timeoutMs = 20_000L)
            waitForText(firstOutbound, timeoutMs = 15_000L)
            composeTestRule.onNodeWithTag("conversation_message_input")
                .performTextReplacement(secondOutbound)
            composeTestRule.waitUntil(timeoutMillis = 15_000L) {
                composeTestRule.onAllNodes(hasTestTag("conversation_send_button") and isEnabled())
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("conversation_send_button").performClick()

            composeTestRule.waitUntil(timeoutMillis = 20_000L) {
                val firstVisible = composeTestRule.onAllNodesWithText(firstOutbound)
                    .fetchSemanticsNodes().isNotEmpty()
                val secondVisible = composeTestRule.onAllNodesWithText(secondOutbound)
                    .fetchSemanticsNodes().isNotEmpty()
                val sentCount = composeTestRule.onAllNodesWithText(sentLabel)
                    .fetchSemanticsNodes().size
                val sendingCount = composeTestRule.onAllNodesWithText(sendingLabel)
                    .fetchSemanticsNodes().size
                firstVisible && secondVisible && sentCount >= 2 && sendingCount == 0
            }
            composeTestRule.onNodeWithText(firstOutbound).assertIsDisplayed()
            composeTestRule.onNodeWithText(secondOutbound).assertIsDisplayed()
        }

        lateinit var roomId: String
        step("06_inbound_message_received", "S6_INBOUND_MESSAGE_RECEIVED") {
            roomId = peerClient.resolveDirectRoomId(aliceEmail)
            peerClient.connectWebSocket()
            val inboundReply = "Reply from Bob over WS"
            peerClient.sendDirectTextMessage(roomId, inboundReply)
            waitForText(inboundReply, timeoutMs = 20_000L)
            composeTestRule.onNodeWithText(inboundReply).assertIsDisplayed()
        }

        step("07_reconnect_catchup_sync", "S7_RECONNECT_CATCHUP_SYNC") {
            val offlineMissedMsg = "Offline missed msg"

            try {
                disableDeviceConnectivity()

                waitForTag("sync_status_bar", timeoutMs = 20_000L)
                composeTestRule.onNodeWithTag("sync_status_bar").assertIsDisplayed()

                peerClient.sendDirectTextMessage(roomId, offlineMissedMsg)
            } finally {
                restoreDeviceConnectivity(logEvent = true)
            }

            composeTestRule.waitUntil(timeoutMillis = 30_000L) {
                val bannerGone = composeTestRule.onAllNodesWithTag("sync_status_bar")
                    .fetchSemanticsNodes().isEmpty()
                val missedMsgVisible = composeTestRule.onAllNodesWithText(offlineMissedMsg)
                    .fetchSemanticsNodes().isNotEmpty()
                bannerGone && missedMsgVisible
            }
            composeTestRule.onNodeWithText(offlineMissedMsg).assertIsDisplayed()
        }
    }
}
