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

    protected abstract val journeyId: String

    protected lateinit var device: UiDevice
    protected lateinit var artifactDir: File
    protected lateinit var persistentJourneyDir: String
    internal lateinit var peerClient: E2EPeerClient

    private val stepRecords = mutableListOf<String>()
    private var currentCheckpoint: String = "00_init"
    private var currentState: String = "INIT"
    private var currentStepStartedAtMs: Long = 0L

    @Before
    fun baseSetUp() {
        val args = InstrumentationRegistry.getArguments()
        Assume.assumeTrue(
            "Skipping ${this::class.simpleName} unless -Pandroid.testInstrumentationRunnerArguments.e2eSmoke=true is set",
            args.getString("e2eSmoke") == "true",
        )
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
                val stepsFile = File(artifactDir, "steps.tsv")
                stepsFile.writeText(stepRecords.joinToString("\n") + "\n")
            }
        }
        if (::device.isInitialized && ::artifactDir.isInitialized) {
            mirrorArtifactsToPersistentDir()
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
            captureCheckpoint("failure")
            val message = (t.message ?: t::class.simpleName ?: "Unknown failure")
                .replace('\t', ' ')
                .replace('\n', ' ')
                .take(240)
            stepRecords.add("$currentCheckpoint\t$currentState\tFAIL\t$elapsedMs\t$message")
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
        val dir = File(File(externalRoot, "e2e-smoke"), journeyId)
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
        captureCheckpoint(name)
        val elapsedMs = System.currentTimeMillis() - startedAtMs
        Log.i(TAG, "[FSM] State $state PASS (${elapsedMs}ms) checkpoint=$name")
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
                "cp ${file.absolutePath} $persistentJourneyDir/${file.name}",
            )
        }
    }

    private fun mirrorArtifactsToPersistentDir() {
        runCatching {
            device.executeShellCommand(
                "mkdir -p $persistentJourneyDir && cp -r ${artifactDir.absolutePath}/. $persistentJourneyDir/",
            )
        }
    }

    companion object {
        const val TAG = "E2E_SMOKE"
        const val PERSISTENT_ROOT_DIR = "/sdcard/Download/e2e-smoke"
    }
}
