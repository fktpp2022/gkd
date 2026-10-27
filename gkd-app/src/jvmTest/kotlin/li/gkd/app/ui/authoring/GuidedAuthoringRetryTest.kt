package li.gkd.app.ui.authoring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import li.gkd.app.state.Loadable

class GuidedAuthoringRetryTest {
    private fun deadEnd(
        appId: String? = "com.example",
        manual: Boolean = false,
    ) = GuidedAuthoringState(
        phase = GuidedAuthoringPhase.DeadEnd,
        appId = appId,
        appName = appId,
        manual = manual,
    )

    @Test
    fun liveCaptureDeadEndOffersRetry() {
        assertTrue(deadEnd().canRetryCapture(liveCapture = true))
    }

    @Test
    fun storedSnapshotAndManualDeadEndsDoNotOfferRetry() {
        assertFalse(deadEnd().canRetryCapture(liveCapture = false))
        assertFalse(deadEnd(manual = true).canRetryCapture(liveCapture = true))
    }

    @Test
    fun deadEndWithoutATargetAppCannotRetry() {
        assertFalse(deadEnd(appId = null).canRetryCapture(liveCapture = true))
    }

    @Test
    fun retryWithAUsableCaptureLandsOnPick() {
        val next = deadEnd().afterRetryCapture(usable = true)
        assertEquals(GuidedAuthoringPhase.Picking, next.phase)
        assertEquals("com.example", next.appId)
        assertEquals("com.example", next.appName)
        assertFalse(next.manual)
        assertEquals(Loadable.Loading, next.scan)
    }

    @Test
    fun retryWithoutAUsableCaptureStaysOnTheDeadEnd() {
        val deadEnd = deadEnd()
        assertEquals(deadEnd, deadEnd.afterRetryCapture(usable = false))
    }
}
