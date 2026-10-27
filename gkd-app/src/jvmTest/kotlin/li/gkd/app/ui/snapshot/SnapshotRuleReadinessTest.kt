package li.gkd.app.ui.snapshot

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import li.gkd.app.authoring.GuidedRecordingBounds
import li.gkd.app.authoring.testNode
import li.gkd.app.model.ComplexSnapshot
import li.gkd.app.model.DeviceInfo
import li.gkd.app.model.NodeInfo

class SnapshotRuleReadinessTest {
    private val usable = testNode(1, 0, text = "OK", clickable = true)
    private val invisible = testNode(2, 0, text = "Hidden")
        .copy(attr = testNode(2, 0).attr.copy(visibleToUser = false))
    private val collapsed = testNode(
        3, 0, text = "Collapsed", bounds = GuidedRecordingBounds(10, 10, 10, 10)
    )

    @Test
    fun anAppIdAndAUsableNodeMakeTheSnapshotAvailable() {
        val tree = tree(listOf(usable))
        val readiness = assertIs<SnapshotRuleReadiness.Available>(
            snapshotRuleReadiness("com.example", tree),
        )
        assertSame(tree, readiness.tree)
        assertTrue(readiness.canCreateRule)
    }

    @Test
    fun aMissingAppIdIsUnavailableEvenWithAUsableTree() {
        assertEquals(
            SnapshotRuleReadiness.Unavailable,
            snapshotRuleReadiness("", tree(listOf(usable))),
        )
        assertEquals(
            SnapshotRuleReadiness.Unavailable,
            snapshotRuleReadiness("   ", tree(listOf(usable))),
        )
    }

    @Test
    fun anUnreadableTreeOrNoUsableNodeIsUnavailable() {
        val appId = "com.example"
        assertEquals(SnapshotRuleReadiness.Unavailable, snapshotRuleReadiness(appId, null))
        assertEquals(
            SnapshotRuleReadiness.Unavailable,
            snapshotRuleReadiness(appId, tree(emptyList())),
        )
        assertEquals(
            SnapshotRuleReadiness.Unavailable,
            snapshotRuleReadiness(appId, tree(listOf(invisible, collapsed))),
        )
    }

    @Test
    fun pendingOrUnavailableReadinessKeepsCreateRuleDisabled() {
        assertFalse(SnapshotRuleReadiness.Loading.canCreateRule)
        assertFalse(SnapshotRuleReadiness.Unavailable.canCreateRule)
    }

    private fun tree(nodes: List<NodeInfo>) = ComplexSnapshot(
        id = 1,
        appId = "com.example",
        activityId = null,
        screenHeight = 100,
        screenWidth = 100,
        isLandscape = false,
        appInfo = null,
        gkdAppInfo = null,
        device = DeviceInfo("test", "test", "test", "test", 0, "test"),
        nodes = nodes,
    )
}
