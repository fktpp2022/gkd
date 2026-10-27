package li.gkd.app.authoring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GuidedRecordingResolverTest {
    private fun tap(
        vid: String? = null,
        text: String? = null,
        desc: String? = null,
        bounds: GuidedRecordingBounds? = GuidedRecordingBounds(0, 0, 200, 200),
        eventTexts: List<String> = emptyList(),
    ) = GuidedTapEvent(
        packageName = "com.example",
        longClick = false,
        sourceVid = vid,
        sourceText = text,
        sourceDesc = desc,
        sourceBounds = bounds,
        eventTexts = eventTexts,
    )

    private fun foundIds(result: GuidedRecordingResolution): List<Int> =
        assertIs<GuidedRecordingResolution.Found>(result).nodes.map { it.id }

    @Test
    fun prefersTheVidNodeOverlappingTheSourceBounds() {
        val nodes = listOf(
            testNode(id = 1, pid = 0, viewId = "pkg:id/row", bounds = GuidedRecordingBounds(0, 0, 100, 100)),
            testNode(id = 2, pid = 0, viewId = "pkg:id/row", bounds = GuidedRecordingBounds(0, 300, 100, 400)),
        )

        assertEquals(listOf(1), foundIds(GuidedRecordingResolver.resolve(tap(vid = "row"), nodes)))
    }

    @Test
    fun fallsBackToTheUniqueVidNodeWithoutBounds() {
        val nodes = listOf(
            testNode(id = 1, pid = 0, viewId = "pkg:id/row", bounds = GuidedRecordingBounds(0, 300, 100, 400)),
        )

        assertEquals(listOf(1), foundIds(GuidedRecordingResolver.resolve(tap(vid = "row"), nodes)))
    }

    @Test
    fun rejectsSeveralVidNodesWhenNoneOverlaps() {
        val nodes = listOf(
            testNode(id = 1, pid = 0, viewId = "pkg:id/row", bounds = GuidedRecordingBounds(0, 300, 100, 400)),
            testNode(id = 2, pid = 0, viewId = "pkg:id/row", bounds = GuidedRecordingBounds(0, 500, 100, 600)),
        )

        assertIs<GuidedRecordingResolution.NotFound>(
            GuidedRecordingResolver.resolve(tap(vid = "row"), nodes),
        )
    }

    @Test
    fun usesAllVidNodesWhenTheSourceHasNoBounds() {
        val nodes = listOf(
            testNode(id = 1, pid = 0, viewId = "pkg:id/row"),
            testNode(id = 2, pid = 0, viewId = "pkg:id/row"),
        )

        assertEquals(
            listOf(1, 2),
            foundIds(GuidedRecordingResolver.resolve(tap(vid = "row", bounds = null), nodes)),
        )
    }

    @Test
    fun matchesTheSourceTextOverlappingTheSourceBounds() {
        val nodes = listOf(
            testNode(id = 1, pid = 0, text = "Delete", bounds = GuidedRecordingBounds(0, 0, 100, 100)),
            testNode(id = 2, pid = 0, text = "Delete", bounds = GuidedRecordingBounds(0, 300, 100, 400)),
        )

        assertEquals(listOf(1), foundIds(GuidedRecordingResolver.resolve(tap(text = "Delete"), nodes)))
    }

    @Test
    fun matchesTheSourceDescriptionOverlappingTheSourceBounds() {
        val nodes = listOf(
            testNode(id = 1, pid = 0, desc = "Search", bounds = GuidedRecordingBounds(0, 0, 100, 100)),
        )

        assertEquals(listOf(1), foundIds(GuidedRecordingResolver.resolve(tap(desc = "Search"), nodes)))
    }

    @Test
    fun labelWithoutOverlapDoesNotFallBackToBounds() {
        val nodes = listOf(
            testNode(id = 1, pid = 0, text = "Delete", bounds = GuidedRecordingBounds(0, 300, 100, 400)),
        )

        assertIs<GuidedRecordingResolution.NotFound>(
            GuidedRecordingResolver.resolve(tap(text = "Delete"), nodes),
        )
    }

    @Test
    fun labelWithoutSourceBoundsIsNotResolved() {
        val nodes = listOf(testNode(id = 1, pid = 0, text = "Delete"))

        assertIs<GuidedRecordingResolution.NotFound>(
            GuidedRecordingResolver.resolve(tap(text = "Delete", bounds = null), nodes),
        )
    }

    @Test
    fun recoversALabeledChildFromABareContainer() {
        val nodes = listOf(
            testNode(id = 1, pid = 0, clickable = true, childCount = 1, bounds = GuidedRecordingBounds(0, 0, 200, 200)),
            testNode(id = 2, pid = 1, text = "Open", bounds = GuidedRecordingBounds(20, 20, 80, 60)),
            testNode(id = 3, pid = 0, text = "Open", bounds = GuidedRecordingBounds(0, 300, 80, 340)),
        )

        assertEquals(
            listOf(2),
            foundIds(GuidedRecordingResolver.resolve(tap(eventTexts = listOf("Open")), nodes)),
        )
    }

    @Test
    fun prefersALaterUniqueEventTextOverAnEarlierAmbiguousOne() {
        val nodes = listOf(
            testNode(id = 1, pid = 0, text = "Open", bounds = GuidedRecordingBounds(0, 0, 50, 50)),
            testNode(id = 2, pid = 0, text = "Open", bounds = GuidedRecordingBounds(100, 100, 150, 150)),
            testNode(id = 3, pid = 0, text = "Details", bounds = GuidedRecordingBounds(60, 60, 90, 90)),
        )

        assertEquals(
            listOf(3),
            foundIds(GuidedRecordingResolver.resolve(tap(eventTexts = listOf("Open", "Details")), nodes)),
        )
    }

    @Test
    fun keepsSeveralEventTextMatchesForTheOverlapStep() {
        val nodes = listOf(
            testNode(id = 1, pid = 0, text = "Open", bounds = GuidedRecordingBounds(0, 0, 50, 50)),
            testNode(id = 2, pid = 0, text = "Open", bounds = GuidedRecordingBounds(100, 100, 150, 150)),
        )

        assertEquals(
            listOf(1, 2),
            foundIds(GuidedRecordingResolver.resolve(tap(eventTexts = listOf("Open")), nodes)),
        )
    }

    @Test
    fun rejectsEventTextOutsideTheSourceBounds() {
        val nodes = listOf(
            testNode(id = 1, pid = 0, text = "Open", bounds = GuidedRecordingBounds(0, 300, 80, 340)),
            testNode(id = 2, pid = 0, text = "Partial", bounds = GuidedRecordingBounds(150, 150, 260, 260)),
        )

        assertIs<GuidedRecordingResolution.NotFound>(
            GuidedRecordingResolver.resolve(tap(eventTexts = listOf("Open", "Partial")), nodes),
        )
    }

    @Test
    fun neverFallsBackToAnUnlabeledNodeAtTheSameBounds() {
        val nodes = listOf(
            testNode(id = 1, pid = 0, clickable = true, bounds = GuidedRecordingBounds(0, 0, 200, 200)),
        )

        assertIs<GuidedRecordingResolution.NotFound>(
            GuidedRecordingResolver.resolve(tap(eventTexts = listOf("Open")), nodes),
        )
    }
}
