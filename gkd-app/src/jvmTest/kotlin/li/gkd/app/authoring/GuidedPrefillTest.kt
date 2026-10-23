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
    )
    private val adapter = SnapshotNodeAdapter(nodes)

    @Test
    fun startsWithAUniqueTextIdentifier() {
        val prefill = prefill(6)

        assertEquals(
            listOf(GuidedCondition.Text(GuidedTextOperator.Is, "Plain")),
            prefill.target,
        )
        assertEquals("[text=\"Plain\"]", prefill.selector)
        assertEquals(1, prefill.matchCount)
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
        assertEquals("[text=\"Delete\"][vid=\"delete_primary\"]", prefill.selector)
        assertEquals(1, prefill.matchCount)
    }

    @Test
    fun usesContainsForDynamicText() {
        val prefill = prefill(3)

        assertEquals(
            listOf(GuidedCondition.Text(GuidedTextOperator.Contains, "Battery")),
            prefill.target,
        )
        assertEquals("[text*=\"Battery\"]", prefill.selector)
        assertEquals(1, prefill.matchCount)
        assertEquals(true, prefill.dynamicText)
    }

    @Test
    fun skipsDynamicTextWithoutAStablePrefix() {
        val prefill = prefill(4)

        assertEquals(listOf(GuidedCondition.ViewId("clock")), prefill.target)
        assertEquals("[vid=\"clock\"]", prefill.selector)
        assertEquals(false, prefill.dynamicText)
    }

    @Test
    fun prefersDescriptionForIconOnlyNodes() {
        val prefill = prefill(5)

        assertEquals(
            listOf(GuidedCondition.Description(GuidedTextOperator.Is, "Search")),
            prefill.target,
        )
        assertEquals("[desc=\"Search\"]", prefill.selector)
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
            prefill.selector,
        )
        assertEquals(1, prefill.matchCount)
    }

    @Test
    fun usesTheClassNameWhenNoOtherIdentifierExists() {
        val prefill = prefill(7)

        assertEquals(
            listOf(GuidedCondition.TypeName("com.example.UniqueButton")),
            prefill.target,
        )
        assertEquals(1, prefill.matchCount)
    }

    @Test
    fun reportsNodesWithoutIdentifiers() {
        val result = assertIs<GuidedPrefillResult.Failure>(
            GuidedPrefillBuilder.build(nodes[8], adapter),
        )

        assertEquals(GuidedPrefillFailure.NoIdentifier, result.reason)
    }

    private fun prefill(id: Int): GuidedPrefill =
        when (val result = GuidedPrefillBuilder.build(nodes[id], adapter)) {
            is GuidedPrefillResult.Success -> result.prefill
            is GuidedPrefillResult.Failure -> error("Prefill failed: ${result.reason}")
        }

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
