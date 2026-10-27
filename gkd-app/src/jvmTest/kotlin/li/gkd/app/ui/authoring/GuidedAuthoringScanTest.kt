package li.gkd.app.ui.authoring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import li.gkd.app.authoring.GuidedCondition
import li.gkd.app.authoring.GuidedRuleCandidate
import li.gkd.app.authoring.GuidedRuleDraft
import li.gkd.app.authoring.GuidedTextOperator
import li.gkd.app.state.Loadable

class GuidedAuthoringScanTest {
    private fun editableState(
        scan: Loadable<List<GuidedRuleCandidate>>,
        manual: Boolean = true,
    ) = GuidedAuthoringState(
        phase = GuidedAuthoringPhase.Editing,
        appId = "com.example",
        appName = "Example",
        draft = GuidedRuleDraft().addCondition(GuidedCondition.Text(GuidedTextOperator.Is, "OK")),
        manual = manual,
        scan = scan,
    )

    @Test
    fun pendingScanDisablesSaveWithoutAFailureBanner() {
        val state = editableState(Loadable.Loading)

        assertFalse(state.canSave)
        assertFalse(state.scanFailed)
    }

    @Test
    fun failedScanDisablesSaveAndExposesRetry() {
        val state = editableState(Loadable.Failure(IllegalStateException("broken rule")))

        assertFalse(state.canSave)
        assertTrue(state.scanFailed)
    }

    @Test
    fun theFailureGateAlsoAppliesToThePickedConditionsScreen() {
        val state = editableState(
            scan = Loadable.Failure(IllegalStateException("broken rule")),
            manual = false,
        )

        assertFalse(state.canSave)
        assertTrue(state.scanFailed)
    }

    @Test
    fun retryReturnsTheScanToPendingAndACompletedRetryEnablesSave() {
        val failure = editableState(Loadable.Failure(IllegalStateException("broken rule")))

        val retrying = failure.afterRetryScan()
        assertEquals(Loadable.Loading, retrying.scan)
        assertFalse(retrying.scanFailed)
        assertFalse(retrying.canSave)

        val completed = retrying.copy(scan = Loadable.Ready(emptyList()))
        assertTrue(completed.canSave)
    }

    @Test
    fun retryIsIgnoredOnceTheScanCompleted() {
        val ready = editableState(Loadable.Ready(emptyList()))

        assertEquals(ready, ready.afterRetryScan())
    }

    @Test
    fun completedScanWithoutRulesKeepsTheZeroDuplicateSavePath() {
        assertTrue(editableState(Loadable.Ready(emptyList())).canSave)
    }
}
