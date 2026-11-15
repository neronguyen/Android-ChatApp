package io.github.neronguyen.chat.e2e

import android.util.Log
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.github.neronguyen.chat.MainActivity
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import java.io.File

abstract class BaseE2EJourneyTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    protected open val journeyId: String
        get() = this::class.java.simpleName
            .removeSuffix("E2ETest")
            .replace(CAMEL_BOUNDARY_REGEX, "$1-$2")
            .lowercase()

    protected lateinit var device: UiDevice
    protected lateinit var artifactDir: File
    protected lateinit var persistentJourneyDir: String
    internal lateinit var peerClient: E2EPeerClient

    private val stepRecords = mutableListOf<String>()
    private var captureSteps: Boolean = false
    private var currentCheckpoint: String = "00_init"
    private var currentState: String = "INIT"
    private var currentStepStartedAtMs: Long = 0L

    @Before
    fun baseSetUp() {
        val args = InstrumentationRegistry.getArguments()
        Assume.assumeTrue(
            "Skipping ${this::class.simpleName} unless -Pandroid.testInstrumentationRunnerArguments.e2e=true is set",
            args.getString("e2e") == "true",
        )
        captureSteps = args.getString("e2eCaptureSteps") == "true"
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        persistentJourneyDir = "$PERSISTENT_ROOT_DIR/$journeyId"
        artifactDir = prepareArtifactDir()
        peerClient = E2EPeerClient()
    }

    @After
    fun baseTearDown() {
        if (::device.isInitialized) {
            restoreDeviceConnectivity(logEvent = false)
        }
        if (::peerClient.isInitialized) {
            peerClient.close()
        }
        if (::artifactDir.isInitialized && stepRecords.isNotEmpty()) {
            runCatching {
                val stepsFile = File(artifactDir, "steps.csv")
                val lines = listOf(CSV_HEADER) + stepRecords
                stepsFile.writeText(lines.joinToString("\n") + "\n")
                mirrorFileToPersistentDir(stepsFile)
            }
        }
    }

    protected fun runJourney(block: () -> Unit) {
        Log.i(TAG, "[FSM] Journey=$journeyId START artifacts=$persistentJourneyDir")
        try {
            block()
            Log.i(TAG, "[FSM] Journey=$journeyId DONE (${stepRecords.size} states passed)")
        } catch (t: Throwable) {
            val elapsedMs = if (currentStepStartedAtMs > 0L) {
                System.currentTimeMillis() - currentStepStartedAtMs
            } else {
                0L
            }
            Log.e(
                TAG,
                "[FSM] FAIL journey=$journeyId state=$currentState checkpoint=$currentCheckpoint reason=${t.message}",
                t,
            )
            captureScreenshot("failure")
            val message = (t.message ?: t::class.simpleName ?: "Unknown failure")
                .replace('\r', ' ')
                .replace('\n', ' ')
                .trim()
                .take(240)
            stepRecords.add(csvRow(currentCheckpoint, currentState, "FAIL", elapsedMs, message))
            throw t
        }
    }

    protected fun step(checkpoint: String, state: String, block: () -> Unit) {
        currentCheckpoint = checkpoint
        currentState = state
        currentStepStartedAtMs = System.currentTimeMillis()
        Log.i(TAG, "[FSM] State transition -> $state ($checkpoint)")
        block()
        recordCheckpoint(checkpoint, state, currentStepStartedAtMs)
    }

    protected fun disableDeviceConnectivity() {
        Log.i(TAG, "[FSM] Device network -> OFFLINE (svc wifi/data disabled)")
        device.executeShellCommand("svc wifi disable")
        device.executeShellCommand("svc data disable")
    }

    protected fun restoreDeviceConnectivity(logEvent: Boolean = true) {
        if (logEvent) {
            Log.i(TAG, "[FSM] Device network -> ONLINE (svc wifi/data enabled)")
        }
        runCatching { device.executeShellCommand("svc wifi enable") }
        runCatching { device.executeShellCommand("svc data enable") }
    }

    protected fun waitForTag(tag: String, timeoutMs: Long) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMs) {
            composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    protected fun waitForTagAbsent(tag: String, timeoutMs: Long) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMs) {
            composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
        }
    }

    protected fun waitForText(text: String, timeoutMs: Long) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMs) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun prepareArtifactDir(): File {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val externalRoot = targetContext.getExternalFilesDir(null) ?: targetContext.filesDir
        val dir = File(File(externalRoot, "e2e"), journeyId)
        if (dir.exists()) {
            dir.listFiles()?.forEach { it.deleteRecursively() }
        } else {
            dir.mkdirs()
        }
        runCatching {
            device.executeShellCommand("rm -rf $persistentJourneyDir && mkdir -p $persistentJourneyDir")
        }
        return dir
    }

    private fun recordCheckpoint(name: String, state: String, startedAtMs: Long) {
        composeTestRule.waitForIdle()
        if (captureSteps) {
            captureScreenshot(name)
        }
        val elapsedMs = System.currentTimeMillis() - startedAtMs
        Log.i(TAG, "[FSM] State $state PASS (${elapsedMs}ms) checkpoint=$name")
        stepRecords.add(csvRow(name, state, "PASS", elapsedMs, ""))
    }

    private fun captureScreenshot(name: String) {
        val screenshotFile = File(artifactDir, "$name.png")
        runCatching {
            device.takeScreenshot(screenshotFile)
            mirrorFileToPersistentDir(screenshotFile)
        }
    }

    private fun mirrorFileToPersistentDir(file: File) {
        if (!file.exists()) return
        runCatching {
            device.executeShellCommand(
                "mkdir -p $persistentJourneyDir && cp ${file.absolutePath} $persistentJourneyDir/${file.name}",
            )
        }
    }

    private fun csvRow(
        checkpoint: String,
        state: String,
        status: String,
        durationMs: Long,
        error: String,
    ): String = listOf(
        csvEscape(checkpoint),
        csvEscape(state),
        csvEscape(status),
        durationMs.toString(),
        csvEscape(error),
    ).joinToString(",")

    private fun csvEscape(value: String): String {
        val needsQuoting = value.indexOfAny(charArrayOf(',', '"', '\n', '\r')) >= 0
        return if (needsQuoting) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    companion object {
        const val TAG = "E2E_TEST"
        const val PERSISTENT_ROOT_DIR = "/sdcard/Download/e2e"
        private const val CSV_HEADER = "checkpoint,state,status,duration_ms,error"
        private val CAMEL_BOUNDARY_REGEX = Regex("([a-z0-9])([A-Z])")
    }
}
