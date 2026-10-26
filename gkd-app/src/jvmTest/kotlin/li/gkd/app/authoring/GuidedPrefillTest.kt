package li.gkd.app.authoring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import li.gkd.app.model.AttrInfo
import li.gkd.app.model.NodeInfo
import li.gkd.app.snapshot.SnapshotNodeAdapter

class GuidedPrefillTest {
    private val nodes = listOf(
        node(id = 0, pid = -1, name = "android.widget.FrameLayout", childCount = 10),
        node(id = 1, pid = 0, name = "android.widget.TextView", vid = "delete_primary", text = "Delete"),
        node(id = 2, pid = 0, name = "android.widget.TextView", vid = "delete_secondary", text = "Delete"),
        node(id = 3, pid = 0, name = "android.widget.TextView", text = "Battery 87%"),
        node(id = 4, pid = 0, name = "android.widget.TextView", vid = "clock", text = "12:34"),
        node(id = 5, pid = 0, name = "android.widget.ImageButton", desc = "Search"),
        node(id = 6, pid = 0, name = "android.widget.TextView", text = "Plain"),
        node(id = 7, pid = 0, name = "com.example.UniqueButton"),
        node(id = 8, pid = 0),
        node(
            id = 9,
            pid = 0,
            name = "android.widget.Button",
            viewId = "pkg:id/dup",
            vid = "dup",
            text = "Dup",
            desc = "DupDesc",
        ),
        node(
            id = 10,
            pid = 0,
            name = "android.widget.ImageButton",
            viewId = "pkg:id/dup",
            vid = "dup",
            text = "Dup",
            desc = "DupDesc",
        ),
        node(id = 20, pid = 0, name = "android.widget.LinearLayout", vid = "row_a"),
        node(id = 21, pid = 20, name = "android.widget.Button", text = "Delete"),
        node(id = 22, pid = 0, name = "android.widget.LinearLayout", vid = "row_b"),
        node(id = 23, pid = 22, name = "android.widget.Button", text = "Delete"),
        node(id = 30, pid = 0, name = "android.widget.LinearLayout"),
        node(id = 31, pid = 30, name = "android.widget.TextView", text = "Same"),
        node(id = 32, pid = 0, name = "android.widget.LinearLayout"),
        node(id = 33, pid = 32, name = "android.widget.TextView", text = "Same"),
    )
    private val adapter = SnapshotNodeAdapter(nodes)

    @Test
    fun startsWithAUniqueTextIdentifier() {
        val prefill = prefill(6)

        assertEquals(
            listOf(GuidedCondition.Text(GuidedTextOperator.Is, "Plain")),
            prefill.target,
        )
        assertEquals(null, prefill.context)
        assertEquals("[text=\"Plain\"]", selectorOf(prefill))
        assertEquals(1, matchCountOf(6, prefill))
        assertEquals(false, prefill.dynamicText)
    }

    @Test
    fun addsTheNextIdentifierWhileAmbiguous() {
        val prefill = prefill(1)

        assertEquals(
            listOf(
                GuidedCondition.Text(GuidedTextOperator.Is, "Delete"),
                GuidedCondition.ViewId("delete_primary"),
            ),
            prefill.target,
        )
        assertEquals(null, prefill.context)
        assertEquals("[text=\"Delete\"][vid=\"delete_primary\"]", selectorOf(prefill))
        assertEquals(1, matchCountOf(1, prefill))
    }

    @Test
    fun usesContainsForDynamicText() {
        val prefill = prefill(3)

        assertEquals(
            listOf(GuidedCondition.Text(GuidedTextOperator.Contains, "Battery")),
            prefill.target,
        )
        assertEquals("[text*=\"Battery\"]", selectorOf(prefill))
        assertEquals(1, matchCountOf(3, prefill))
        assertEquals(true, prefill.dynamicText)
    }

    @Test
    fun skipsDynamicTextWithoutAStablePrefix() {
        val prefill = prefill(4)

        assertEquals(listOf(GuidedCondition.ViewId("clock")), prefill.target)
        assertEquals("[vid=\"clock\"]", selectorOf(prefill))
        assertEquals(false, prefill.dynamicText)
    }

    @Test
    fun prefersDescriptionForIconOnlyNodes() {
        val prefill = prefill(5)

        assertEquals(
            listOf(GuidedCondition.Description(GuidedTextOperator.Is, "Search")),
            prefill.target,
        )
        assertEquals("[desc=\"Search\"]", selectorOf(prefill))
    }

    @Test
    fun triesOneInsideContextOnTheNearestStableAncestor() {
        val prefill = prefill(21)

        assertEquals(
            GuidedContext(
                GuidedContextRelation.Inside,
                listOf(GuidedCondition.ViewId("row_a")),
            ),
            prefill.context,
        )
        assertEquals(
            "[vid=\"row_a\"] > @android.widget.Button[text=\"Delete\"]",
            selectorOf(prefill),
        )
        assertEquals(1, matchCountOf(21, prefill))
    }

    @Test
    fun keepsTheDraftBroadWhenNoAncestorNarrowsIt() {
        val prefill = prefill(31)

        assertEquals(null, prefill.context)
        assertEquals(2, matchCountOf(31, prefill))
    }

    @Test
    fun fallsBackToTheClassNameAfterThreeIdentifiers() {
        val prefill = prefill(9)

        assertEquals(
            listOf(
                GuidedCondition.Text(GuidedTextOperator.Is, "Dup"),
                GuidedCondition.ViewId("dup"),
                GuidedCondition.FullId("pkg:id/dup"),
                GuidedCondition.TypeName("android.widget.Button"),
            ),
            prefill.target,
        )
        assertEquals(
            "android.widget.Button[text=\"Dup\"][vid=\"dup\"][id=\"pkg:id/dup\"]",
            selectorOf(prefill),
        )
        assertEquals(1, matchCountOf(9, prefill))
    }

    @Test
    fun usesTheClassNameWhenNoOtherIdentifierExists() {
        val prefill = prefill(7)

        assertEquals(
            listOf(GuidedCondition.TypeName("com.example.UniqueButton")),
            prefill.target,
        )
        assertEquals(1, matchCountOf(7, prefill))
    }

    @Test
    fun reportsNodesWithoutIdentifiers() {
        val result = assertIs<GuidedPrefillResult.Failure>(
            GuidedPrefillBuilder.build(nodeOf(8), adapter),
        )

        assertEquals(GuidedPrefillFailure.NoIdentifier, result.reason)
    }

    private fun prefill(id: Int): GuidedPrefill =
        when (val result = GuidedPrefillBuilder.build(nodeOf(id), adapter)) {
            is GuidedPrefillResult.Success -> result.prefill
            is GuidedPrefillResult.Failure -> error("Prefill failed: ${result.reason}")
        }

    private fun selectorOf(prefill: GuidedPrefill): String =
        when (val result = GuidedSelectorCompiler.compile(prefill.toDraft())) {
            is GuidedSelectorCompileResult.Success -> result.selector
            is GuidedSelectorCompileResult.Failure -> error("Compile failed: ${result.reason}")
        }

    private fun matchCountOf(id: Int, prefill: GuidedPrefill): Int =
        when (val result = GuidedPreviewBuilder.build(prefill.toDraft(), nodeOf(id), adapter)) {
            is GuidedPreviewResult.Ready -> result.matches.size
            else -> error("Preview not ready")
        }

    private fun nodeOf(id: Int): NodeInfo = nodes.first { it.id == id }

    private fun GuidedPrefill.toDraft() = GuidedSelectorDraft(target, context)

    private fun node(
        id: Int,
        pid: Int,
        name: String? = null,
        viewId: String? = null,
        vid: String? = null,
        text: String? = null,
        desc: String? = null,
        childCount: Int = 0,
    ) = NodeInfo(
        id = id,
        pid = pid,
        idQf = null,
        textQf = null,
        attr = AttrInfo(
            id = viewId ?: vid?.let { "pkg:id/$it" },
            vid = vid,
            name = name,
            text = text,
            desc = desc,
            clickable = false,
            focusable = false,
            checkable = false,
            checked = null,
            editable = false,
            longClickable = false,
            visibleToUser = true,
            left = 0,
            top = 0,
            right = 100,
            bottom = 20,
            width = 100,
            height = 20,
            childCount = childCount,
            index = id,
            depth = if (pid == -1) 0 else 1,
        ),
    )
}
