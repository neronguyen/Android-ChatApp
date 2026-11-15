package io.github.neronguyen.chat.e2e

import android.util.Log
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.github.neronguyen.chat.MainActivity
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import io.github.neronguyen.chat.feature.chat.R as ChatR

@RunWith(AndroidJUnit4::class)
class FullStackSmokeE2ETest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var device: UiDevice
    private lateinit var artifactDir: File
    private lateinit var peerClient: E2EPeerClient
    private val stepRecords = mutableListOf<String>()

    @Before
    fun setUp() {
        val args = InstrumentationRegistry.getArguments()
        Assume.assumeTrue(
            "Skipping FullStackSmokeE2ETest unless -Pandroid.testInstrumentationRunnerArguments.e2eSmoke=true is set",
            args.getString("e2eSmoke") == "true",
        )
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        artifactDir = prepareArtifactDir()
        peerClient = E2EPeerClient()
    }

    @After
    fun tearDown() {
        if (::device.isInitialized) {
            restoreDeviceConnectivity()
        }
        if (::peerClient.isInitialized) {
            peerClient.close()
        }
        if (::artifactDir.isInitialized && stepRecords.isNotEmpty()) {
            runCatching {
                val stepsFile = File(artifactDir, "steps.tsv")
                stepsFile.writeText(stepRecords.joinToString("\n") + "\n")
            }
        }
        if (::device.isInitialized && ::artifactDir.isInitialized) {
            mirrorArtifactsToPersistentDir()
        }
    }

    @Test
    fun fullStackSmokeJourney_completesAllEightStates() {
        val epoch = System.currentTimeMillis() / 1000L
        val aliceEmail = "alice.$epoch@example.com"
        val aliceDisplayName = "Alice E2E"
        val alicePassword = "AlicePassword123!"
        val bobEmail = E2EPeerClient.DEFAULT_BOB_EMAIL

        var currentCheckpoint = "00_register_form"
        var currentState = "S0_REGISTER_FORM"
        Log.i(TAG, "[START] alice=$aliceEmail bob=$bobEmail artifacts=${artifactDir.absolutePath}")

        try {
            Log.i(TAG, "[SETUP] Ensuring verified peer Bob...")
            peerClient.ensureVerifiedPeerBob()
            Log.i(TAG, "[SETUP] Peer Bob ready.")

            // S0_REGISTER_FORM
            Log.i(TAG, "[STATE $currentState] START")
            val s0Start = System.currentTimeMillis()
            waitForTag("login_screen", timeoutMs = 15_000L)
            composeTestRule.onNodeWithTag("register_button").performClick()

            waitForTag("register_screen", timeoutMs = 10_000L)
            composeTestRule.onNodeWithTag("email_field").performTextReplacement(aliceEmail)
            composeTestRule.onNodeWithTag("display_name_field")
                .performTextReplacement(aliceDisplayName)
            composeTestRule.onNodeWithTag("password_field").performTextReplacement(alicePassword)
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("register_error_text").assertTextEquals("")
            recordCheckpoint(currentCheckpoint, currentState, s0Start)

            // S1_OTP_CHALLENGE
            currentCheckpoint = "01_otp_challenge"
            currentState = "S1_OTP_CHALLENGE"
            Log.i(TAG, "[STATE $currentState] START")
            val s1Start = System.currentTimeMillis()
            composeTestRule.onNodeWithTag("register_button").performClick()
            waitForTag("verify_email_screen", timeoutMs = 20_000L)
            composeTestRule.onNodeWithTag("otp_input_field").assertIsDisplayed()
            recordCheckpoint(currentCheckpoint, currentState, s1Start)

            // S2_LOGIN_VERIFIED
            currentCheckpoint = "02_login_verified"
            currentState = "S2_LOGIN_VERIFIED"
            Log.i(TAG, "[STATE $currentState] START")
            val s2Start = System.currentTimeMillis()
            val aliceOtp = peerClient.fetchLatestOtp(aliceEmail)
            composeTestRule.onNode(
                hasSetTextAction() and hasAnyAncestor(hasTestTag("otp_input_field")),
            ).performTextInput(aliceOtp)
            waitForTag("login_screen", timeoutMs = 20_000L)
            waitForTag("confirmation_banner", timeoutMs = 10_000L)
            composeTestRule.onNodeWithTag("confirmation_banner").assertIsDisplayed()
            recordCheckpoint(currentCheckpoint, currentState, s2Start)

            // S3_CHAT_LIST_EMPTY
            currentCheckpoint = "03_chat_list_empty"
            currentState = "S3_CHAT_LIST_EMPTY"
            Log.i(TAG, "[STATE $currentState] START")
            val s3Start = System.currentTimeMillis()
            composeTestRule.onNodeWithTag("email_field").performTextReplacement(aliceEmail)
            composeTestRule.onNodeWithTag("password_field").performTextReplacement(alicePassword)
            composeTestRule.onNodeWithTag("login_button").performClick()

            waitForTag("chat_list_screen", timeoutMs = 20_000L)
            waitForTagAbsent("sync_status_bar", timeoutMs = 15_000L)
            composeTestRule.onNodeWithTag("chat_list_new_chat_fab").assertIsDisplayed()
            recordCheckpoint(currentCheckpoint, currentState, s3Start)

            // S4_NEW_CHAT_FOUND_PEER
            currentCheckpoint = "04_new_chat_found_peer"
            currentState = "S4_NEW_CHAT_FOUND_PEER"
            Log.i(TAG, "[STATE $currentState] START")
            val s4Start = System.currentTimeMillis()
            composeTestRule.onNodeWithTag("chat_list_new_chat_fab").performClick()
            waitForTag("new_chat_screen", timeoutMs = 10_000L)
            composeTestRule.onNodeWithTag("new_chat_email_field").performTextReplacement(bobEmail)
            composeTestRule.onNodeWithTag("new_chat_search_button").performClick()
            waitForTag("new_chat_searched_participant_card", timeoutMs = 20_000L)
            composeTestRule.onNodeWithTag("new_chat_searched_participant_card").assertIsDisplayed()
            recordCheckpoint(currentCheckpoint, currentState, s4Start)

            // S5_OUTBOUND_MESSAGE_SENT
            currentCheckpoint = "05_outbound_message_sent"
            currentState = "S5_OUTBOUND_MESSAGE_SENT"
            Log.i(TAG, "[STATE $currentState] START")
            val s5Start = System.currentTimeMillis()
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
            recordCheckpoint(currentCheckpoint, currentState, s5Start)

            // S6_INBOUND_MESSAGE_RECEIVED
            currentCheckpoint = "06_inbound_message_received"
            currentState = "S6_INBOUND_MESSAGE_RECEIVED"
            Log.i(TAG, "[STATE $currentState] START")
            val s6Start = System.currentTimeMillis()
            val roomId = peerClient.resolveDirectRoomId(aliceEmail)
            peerClient.connectWebSocket()
            val inboundReply = "Reply from Bob over WS"
            peerClient.sendDirectTextMessage(roomId, inboundReply)
            waitForText(inboundReply, timeoutMs = 20_000L)
            composeTestRule.onNodeWithText(inboundReply).assertIsDisplayed()
            recordCheckpoint(currentCheckpoint, currentState, s6Start)

            // S7_RECONNECT_CATCHUP_SYNC
            currentCheckpoint = "07_reconnect_catchup_sync"
            currentState = "S7_RECONNECT_CATCHUP_SYNC"
            Log.i(TAG, "[STATE $currentState] START")
            val s7Start = System.currentTimeMillis()
            val offlineMissedMsg = "Offline missed msg"

            try {
                device.executeShellCommand("svc wifi disable")
                device.executeShellCommand("svc data disable")

                waitForTag("sync_status_bar", timeoutMs = 20_000L)
                composeTestRule.onNodeWithTag("sync_status_bar").assertIsDisplayed()

                peerClient.sendDirectTextMessage(roomId, offlineMissedMsg)
            } finally {
                restoreDeviceConnectivity()
            }

            composeTestRule.waitUntil(timeoutMillis = 30_000L) {
                val bannerGone = composeTestRule.onAllNodesWithTag("sync_status_bar")
                    .fetchSemanticsNodes().isEmpty()
                val missedMsgVisible = composeTestRule.onAllNodesWithText(offlineMissedMsg)
                    .fetchSemanticsNodes().isNotEmpty()
                bannerGone && missedMsgVisible
            }
            composeTestRule.onNodeWithText(offlineMissedMsg).assertIsDisplayed()
            recordCheckpoint(currentCheckpoint, currentState, s7Start)
            Log.i(TAG, "[DONE] All 8 E2E states passed.")
        } catch (t: Throwable) {
            Log.e(
                TAG,
                "[FAIL] state=$currentState checkpoint=$currentCheckpoint reason=${t.message}",
                t
            )
            captureCheckpoint("failure")
            val message = (t.message ?: t::class.simpleName ?: "Unknown failure")
                .replace('\t', ' ')
                .replace('\n', ' ')
                .take(240)
            stepRecords.add("$currentCheckpoint\t$currentState\tFAIL\t0\t$message")
            throw t
        }
    }

    private fun prepareArtifactDir(): File {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val externalRoot = targetContext.getExternalFilesDir(null) ?: targetContext.filesDir
        val dir = File(externalRoot, "e2e-smoke")
        if (dir.exists()) {
            dir.listFiles()?.forEach { it.deleteRecursively() }
        } else {
            dir.mkdirs()
        }
        runCatching {
            device.executeShellCommand("mkdir -p $PERSISTENT_ARTIFACT_DIR")
        }
        return dir
    }

    private fun recordCheckpoint(name: String, state: String, startedAtMs: Long) {
        composeTestRule.waitForIdle()
        captureCheckpoint(name)
        val elapsedMs = System.currentTimeMillis() - startedAtMs
        Log.i(TAG, "[STATE $state] PASS (${elapsedMs}ms) checkpoint=$name")
        stepRecords.add("$name\t$state\tPASS\t$elapsedMs\t")
    }

    private fun captureCheckpoint(name: String) {
        val screenshotFile = File(artifactDir, "$name.png")
        runCatching {
            device.takeScreenshot(screenshotFile)
            mirrorFileToPersistentDir(screenshotFile)
        }
        val hierarchyFile = File(artifactDir, "$name.xml")
        runCatching {
            device.dumpWindowHierarchy(hierarchyFile)
            mirrorFileToPersistentDir(hierarchyFile)
        }
    }

    private fun mirrorFileToPersistentDir(file: File) {
        if (!file.exists()) return
        runCatching {
            device.executeShellCommand(
                "cp ${file.absolutePath} $PERSISTENT_ARTIFACT_DIR/${file.name}",
            )
        }
    }

    private fun mirrorArtifactsToPersistentDir() {
        runCatching {
            device.executeShellCommand(
                "mkdir -p $PERSISTENT_ARTIFACT_DIR && cp -r ${artifactDir.absolutePath}/. $PERSISTENT_ARTIFACT_DIR/",
            )
        }
    }

    private fun restoreDeviceConnectivity() {
        runCatching { device.executeShellCommand("svc wifi enable") }
        runCatching { device.executeShellCommand("svc data enable") }
    }

    private fun waitForTag(tag: String, timeoutMs: Long) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMs) {
            composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForTagAbsent(tag: String, timeoutMs: Long) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMs) {
            composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun waitForText(text: String, timeoutMs: Long) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMs) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    companion object {
        private const val TAG = "E2E_SMOKE"
        private const val PERSISTENT_ARTIFACT_DIR = "/sdcard/Download/e2e-smoke"
    }
}
