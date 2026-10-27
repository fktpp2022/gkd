package li.gkd.app.authoring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class GuidedPickingTest {
    @Test
    fun labeledUsableNodesAreListedInScreenOrder() {
        val nodes = listOf(
            testNode(0, -1, name = "android.widget.FrameLayout"),
            testNode(1, 0, text = "Wi-Fi"),
            testNode(2, 0, desc = "Search"),
            testNode(3, 0, clickable = true),
            testNode(4, 0, text = "Hidden", bounds = GuidedRecordingBounds(10, 10, 10, 10)),
        )

        val content = assertIs<GuidedNodeListContent.Nodes>(guidedNodeListContent(nodes))

        assertEquals(listOf(0, 1, 2), content.nodes.map { it.id })
    }

    @Test
    fun noLabeledUsableNodeChoosesTheManualRoute() {
        val nodes = listOf(
            testNode(0, -1, clickable = true),
            testNode(1, 0),
            testNode(2, 0, bounds = GuidedRecordingBounds(10, 10, 10, 10)),
        )

        assertEquals(GuidedNodeListContent.Manual, guidedNodeListContent(nodes))
        assertEquals(GuidedNodeListContent.Manual, guidedNodeListContent(emptyList()))
    }

    @Test
    fun preselectsTheSmallestClickableNodeWithTextEvenWhenASmallerClickableNodeHasNoText() {
        val smallerWithoutText = testNode(
            1, 0,
            clickable = true,
            bounds = GuidedRecordingBounds(0, 0, 40, 40),
        )
        val blankText = testNode(
            2, 0,
            text = "   ",
            clickable = true,
            bounds = GuidedRecordingBounds(0, 0, 60, 60),
        )
        val labeled = testNode(
            3, 0,
            text = "Delete",
            clickable = true,
            bounds = GuidedRecordingBounds(0, 0, 100, 100),
        )
        val larger = testNode(
            4, 0,
            text = "More",
            clickable = true,
            bounds = GuidedRecordingBounds(0, 0, 200, 200),
        )

        val selected = preselectedOverlap(listOf(smallerWithoutText, blankText, labeled, larger))

        assertEquals(3, selected?.id)
    }

    @Test
    fun noCandidateHasTextFallsBackToTheSmallestMeaningfulCandidate() {
        val smallest = testNode(
            1, 0,
            desc = "Search",
            bounds = GuidedRecordingBounds(0, 0, 40, 40),
        )
        val clickable = testNode(
            2, 0,
            clickable = true,
            bounds = GuidedRecordingBounds(0, 0, 80, 80),
        )

        assertEquals(1, preselectedOverlap(listOf(smallest, clickable))?.id)
        assertNull(preselectedOverlap(emptyList()))
    }

    @Test
    fun preselectionFollowsTheGivenOrderWithoutReSorting() {
        val ordered = listOf(
            testNode(1, 0, text = "First", clickable = true, bounds = GuidedRecordingBounds(0, 0, 90, 90)),
            testNode(2, 0, text = "Second", clickable = true, bounds = GuidedRecordingBounds(0, 0, 100, 100)),
            testNode(3, 0, text = "Third", clickable = true, bounds = GuidedRecordingBounds(0, 0, 110, 110)),
        )
        assertEquals(1, preselectedOverlap(ordered)?.id)

        val outOfAreaOrder = listOf(
            testNode(4, 0, text = "Listed first", clickable = true, bounds = GuidedRecordingBounds(0, 0, 200, 200)),
            testNode(5, 0, text = "Smaller but later", clickable = true, bounds = GuidedRecordingBounds(0, 0, 10, 10)),
        )
        assertEquals(4, preselectedOverlap(outOfAreaOrder)?.id)
    }
}
