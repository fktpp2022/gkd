package li.gkd.app.authoring

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GuidedRecordingSessionTest {
    private val appId = "com.example.app"
    private val context = GuidedRecordingContext(
        appId = appId,
        appName = "Example",
        activityId = "com.example.app.MainActivity",
    )

    @BeforeTest
    @AfterTest
    fun reset() {
        GuidedRecordingSession.finish()
    }

    @Test
    fun foregroundWindowBurstKeepsRecording() = runTest {
        val signals = collectSignals()
        GuidedRecordingSession.start(context)
        GuidedRecordingSession.onWindowState(appId, null)
        GuidedRecordingSession.onWindowState(appId, ".MainActivity")
        GuidedRecordingSession.onWindowState(appId, "com.example.app.MainActivity")
        assertEquals(GuidedRecordingPhase.Recording, GuidedRecordingSession.phase)
        assertTrue(signals.isEmpty())
    }

    @Test
    fun activityChangeEndsWithScreenChanged() = runTest {
        val signals = collectSignals()
        GuidedRecordingSession.start(context)
        GuidedRecordingSession.onWindowState(appId, ".MainActivity")
        GuidedRecordingSession.onWindowState(appId, ".DetailActivity")
        assertEquals(GuidedRecordingPhase.Idle, GuidedRecordingSession.phase)
        assertEquals(listOf(GuidedRecordingSignal.ScreenChanged), signals)
    }

    @Test
    fun staleCaptureAtBaselineEndsWithScreenChanged() = runTest {
        val signals = collectSignals()
        GuidedRecordingSession.start(context)
        GuidedRecordingSession.onWindowState(appId, ".OtherActivity")
        assertEquals(GuidedRecordingPhase.Idle, GuidedRecordingSession.phase)
        assertEquals(listOf(GuidedRecordingSignal.ScreenChanged), signals)
    }

    @Test
    fun unnamedWindowStateIsIgnored() = runTest {
        GuidedRecordingSession.start(context)
        GuidedRecordingSession.onWindowState(appId, null)
        GuidedRecordingSession.onWindowState(appId, "  ")
        assertEquals(GuidedRecordingPhase.Recording, GuidedRecordingSession.phase)
        assertNull(GuidedRecordingSession.status.value.baselineActivityId)
    }

    @Test
    fun otherAppWindowStateIsIgnored() = runTest {
        val signals = collectSignals()
        GuidedRecordingSession.start(context)
        GuidedRecordingSession.onWindowState("com.example.other", ".DetailActivity")
        assertEquals(GuidedRecordingPhase.Recording, GuidedRecordingSession.phase)
        assertTrue(signals.isEmpty())
    }

    @Test
    fun tapFromOtherAppIsIgnored() = runTest {
        val signals = collectSignals()
        GuidedRecordingSession.start(context)
        GuidedRecordingSession.onTap(tap(packageName = "com.example.other"))
        assertEquals(GuidedRecordingPhase.Recording, GuidedRecordingSession.phase)
        assertTrue(signals.isEmpty())
    }

    @Test
    fun tapEntersResolvingAndEmitsTap() = runTest {
        val signals = collectSignals()
        val event = tap()
        GuidedRecordingSession.start(context)
        GuidedRecordingSession.onTap(event)
        assertEquals(GuidedRecordingPhase.Resolving, GuidedRecordingSession.phase)
        assertEquals(listOf(GuidedRecordingSignal.Tap(event)), signals)
    }

    @Test
    fun resolvingOnSameActivityIsNotAScreenChange() = runTest {
        val signals = collectSignals()
        GuidedRecordingSession.start(context)
        GuidedRecordingSession.onWindowState(appId, ".MainActivity")
        GuidedRecordingSession.onTap(tap())
        GuidedRecordingSession.onWindowState(appId, "com.example.app.MainActivity")
        assertEquals(GuidedRecordingPhase.Resolving, GuidedRecordingSession.phase)
        assertTrue(signals.none { it == GuidedRecordingSignal.ScreenChanged })
    }

    @Test
    fun resolvingOnChangedActivityReportsScreenChanged() = runTest {
        val signals = collectSignals()
        GuidedRecordingSession.start(context)
        GuidedRecordingSession.onWindowState(appId, ".MainActivity")
        GuidedRecordingSession.onTap(tap())
        GuidedRecordingSession.onWindowState(appId, ".DetailActivity")
        assertEquals(GuidedRecordingPhase.Resolving, GuidedRecordingSession.phase)
        assertEquals(listOf(GuidedRecordingSignal.ScreenChanged), signals.filterIsInstance<GuidedRecordingSignal.ScreenChanged>())
    }

    @Test
    fun stopWhileRecordingEmitsStopped() = runTest {
        val signals = collectSignals()
        GuidedRecordingSession.start(context)
        GuidedRecordingSession.stop()
        assertEquals(GuidedRecordingPhase.Idle, GuidedRecordingSession.phase)
        assertEquals(listOf(GuidedRecordingSignal.Stopped), signals)
    }

    private fun TestScope.collectSignals(): List<GuidedRecordingSignal> {
        val signals = mutableListOf<GuidedRecordingSignal>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            GuidedRecordingSession.signals.collect { signals.add(it) }
        }
        return signals
    }

    private fun tap(packageName: String = appId) = GuidedTapEvent(
        packageName = packageName,
        longClick = false,
        sourceVid = "com.example.app:id/action",
        sourceText = "Action",
        sourceDesc = null,
        sourceBounds = GuidedRecordingBounds(0, 0, 100, 100),
        eventTexts = emptyList(),
    )
}
