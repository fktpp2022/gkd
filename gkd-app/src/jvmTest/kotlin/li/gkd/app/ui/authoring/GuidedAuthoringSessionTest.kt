package li.gkd.app.ui.authoring

import kotlinx.coroutines.runBlocking
import li.gkd.app.authoring.GuidedCondition
import li.gkd.app.authoring.GuidedContextMode
import li.gkd.app.authoring.GuidedDisabledScope
import li.gkd.app.authoring.GuidedExistingRuleHit
import li.gkd.app.authoring.GuidedPick
import li.gkd.app.authoring.GuidedRuleAction
import li.gkd.app.authoring.GuidedRuleCandidate
import li.gkd.app.authoring.GuidedRuleDraft
import li.gkd.app.authoring.GuidedSaveNotice
import li.gkd.app.authoring.GuidedTextOperator
import li.gkd.app.authoring.testAppGroup
import li.gkd.app.authoring.testAppRule
import li.gkd.app.authoring.testNode
import li.gkd.app.resources.*
import li.gkd.app.state.Loadable
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuidedAuthoringSessionTest {
    private val picked = testNode(1, 0, text = "Later", clickable = true)
    private val pick = GuidedPick(picked, picked)
    private val notice = GuidedSaveNotice.Disabled(GuidedDisabledScope.App, "Example")

    private fun candidate() = GuidedRuleCandidate.App(
        group = testAppGroup(name = "My rules"),
        rule = testAppRule(),
        inAuthoringGroup = true,
    )

    private fun dirtyState() = GuidedAuthoringState(
        phase = GuidedAuthoringPhase.DeadEnd,
        appId = "com.example",
        appName = "Example",
        draft = GuidedRuleDraft(
            target = listOf(GuidedCondition.Text(GuidedTextOperator.Is, "Stale")),
            nameOverride = "Stale name",
        ),
        pick = pick,
        preview = GuidedPreviewState.Defect,
        scan = Loadable.Ready(emptyList()),
        existingHits = listOf(GuidedExistingRuleHit(candidate(), picked)),
        existingDismissed = false,
        editing = candidate(),
        notice = notice,
        contextError = true,
        dynamicText = true,
        nameEdited = true,
        manual = true,
        saveError = true,
        contextMode = GuidedContextMode.Inside,
        overlap = listOf(picked),
        recordingFailure = GuidedRecordingFailure.NoNode,
        recordSpent = true,
        suggestedName = "Stale suggestion",
    )

    @Test
    fun sessionResetClearsEveryTransientField() = runBlocking {
        val draft = GuidedRuleDraft(
            target = listOf(GuidedCondition.Text(GuidedTextOperator.Is, "Later")),
            action = GuidedRuleAction.LongPress,
        )
        val reset = dirtyState().resetSession(
            phase = GuidedAuthoringPhase.Editing,
            draft = draft,
            pick = pick,
        )

        assertEquals(GuidedAuthoringPhase.Editing, reset.phase)
        assertEquals(draft, reset.draft)
        assertEquals(pick, reset.pick)
        assertEquals(GuidedPreviewState.NoPreview, reset.preview)
        assertTrue(reset.existingHits.isEmpty())
        assertTrue(reset.existingDismissed)
        assertNull(reset.editing)
        assertFalse(reset.contextError)
        assertFalse(reset.dynamicText)
        assertFalse(reset.nameEdited)
        assertFalse(reset.manual)
        assertFalse(reset.saveError)
        assertNull(reset.contextMode)
        assertNull(reset.overlap)
        assertNull(reset.recordingFailure)
        assertTrue(reset.recordSpent)
        assertEquals("com.example", reset.appId)
        assertEquals(notice, reset.notice)
        assertEquals(Loadable.Ready(emptyList()), reset.scan)
        assertEquals(
            getString(Res.string.guided_rule_name_long_press, "Later"),
            reset.suggestedName,
        )
    }

    @Test
    fun sessionResetKeepsARecordedDemonstrationSpent() {
        val reset = dirtyState().copy(recordSpent = false).resetSession(
            phase = GuidedAuthoringPhase.Editing,
            draft = GuidedRuleDraft(),
            recorded = true,
        )

        assertTrue(reset.recordSpent)
    }
}
