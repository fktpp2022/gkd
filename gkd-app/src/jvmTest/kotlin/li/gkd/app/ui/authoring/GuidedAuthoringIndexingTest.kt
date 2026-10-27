package li.gkd.app.ui.authoring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuidedAuthoringIndexingTest {
    private fun indexing() = GuidedAuthoringState(
        phase = GuidedAuthoringPhase.Indexing,
        appId = "com.example",
        appName = "Example",
    )

    @Test
    fun thePickerIgnoresPicksUntilIndexingCompletes() {
        assertFalse(indexing().canPick)
        assertTrue(indexing().afterIndexing(usable = true).canPick)
    }

    @Test
    fun aUsableTreeRoutesToPick() {
        val next = indexing().afterIndexing(usable = true)

        assertEquals(GuidedAuthoringPhase.Picking, next.phase)
        assertEquals("com.example", next.appId)
        assertEquals("Example", next.appName)
    }

    @Test
    fun anUnusableTreeRoutesToTheDeadEnd() {
        val next = indexing().afterIndexing(usable = false)

        assertEquals(GuidedAuthoringPhase.DeadEnd, next.phase)
    }

    @Test
    fun indexingCompletesOnce() {
        val picked = indexing().afterIndexing(usable = true)

        assertEquals(picked, picked.afterIndexing(usable = false))
    }
}
