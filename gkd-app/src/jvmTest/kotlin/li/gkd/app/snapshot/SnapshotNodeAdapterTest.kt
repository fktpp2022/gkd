package li.gkd.app.snapshot

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import li.gkd.app.model.AttrInfo
import li.gkd.app.model.NodeInfo
import li.gkd.selector.Selector

class SnapshotNodeAdapterTest {
    private val adapter = SnapshotNodeAdapter(
        listOf(
            node(
                id = 0,
                pid = -1,
                name = "android.widget.FrameLayout",
                childCount = 2,
                index = 0,
                depth = 0,
            ),
            node(
                id = 1,
                pid = 0,
                name = "androidx.recyclerview.widget.RecyclerView",
                vid = "settings_list",
                childCount = 2,
                index = 0,
                depth = 1,
            ),
            node(
                id = 2,
                pid = 1,
                name = "android.widget.TextView",
                viewId = "com.example.settings:id/title_wifi",
                vid = "title_wifi",
                text = "Wi-Fi",
                index = 0,
                depth = 2,
            ),
            node(
                id = 3,
                pid = 1,
                name = "android.widget.TextView",
                viewId = "com.example.settings:id/title_bluetooth",
                vid = "title_bluetooth",
                text = "Bluetooth",
                index = 1,
                depth = 2,
            ),
            node(
                id = 4,
                pid = 0,
                name = "android.widget.Button",
                viewId = "com.example.settings:id/btn_ok",
                vid = "btn_ok",
                text = "OK",
                clickable = true,
                longClickable = true,
                index = 1,
                depth = 1,
            ),
        ),
    )

    @Test
    fun navigatesCapturedTreeInSnapshotOrder() {
        val root = assertNotNull(adapter.root)
        assertEquals(0, root.id)
        assertEquals(listOf(1, 4), adapter.getChildren(root).map(NodeInfo::id).toList())

        val list = assertNotNull(adapter.getChild(root, 0))
        assertEquals(listOf(2, 3), adapter.getChildren(list).map(NodeInfo::id).toList())
        assertSame(root, adapter.getParent(list))
        assertSame(root, adapter.getRoot(assertNotNull(adapter.getChild(list, 1))))
        assertEquals(
            listOf(1, 2, 3, 4),
            adapter.getDescendants(root).map(NodeInfo::id).toList(),
        )
        assertEquals(2, adapter.getChildCount(root))
        assertEquals(0, adapter.getChildCount(assertNotNull(adapter.getChild(list, 0))))
    }

    @Test
    fun readsTheSameAttributesAsTheAccessibilityAdapter() {
        val root = assertNotNull(adapter.root)
        val button = assertNotNull(adapter.getChild(root, 1))

        assertEquals("com.example.settings:id/btn_ok", adapter.getAttr(button, "id"))
        assertEquals("btn_ok", adapter.getAttr(button, "vid"))
        assertEquals("android.widget.Button", adapter.getAttr(button, "name"))
        assertEquals("OK", adapter.getAttr(button, "text"))
        assertEquals(true, adapter.getAttr(button, "clickable"))
        assertEquals(true, adapter.getAttr(button, "longClickable"))
        assertEquals(1, adapter.getAttr(button, "index"))
        assertEquals(1, adapter.getAttr(button, "depth"))
        assertEquals(0, adapter.getAttr(button, "childCount"))
        assertEquals(10, adapter.getAttr(button, "left"))
        assertEquals(20, adapter.getAttr(button, "top"))
        assertEquals(90, adapter.getAttr(button, "right"))
        assertEquals(40, adapter.getAttr(button, "bottom"))
        assertSame(root, adapter.getAttr(button, "parent"))
        assertSame(button, adapter.getInvoke(root, "getChild", listOf(1)))
        assertEquals(null, adapter.getAttr(button, "unknown"))
    }

    @Test
    fun matchesSelectorsAgainstTheCapturedTree() {
        val root = assertNotNull(adapter.root)

        assertEquals(3, query(root, "[text='Bluetooth']"))
        assertEquals(4, query(root, "[clickable=true][vid='btn_ok']"))
        assertEquals(
            listOf(2, 3),
            adapter.querySelectorAll(
                root,
                Selector.compile("android.widget.TextView").value,
            ).map(NodeInfo::id),
        )
        assertEquals(3, query(root, "[vid='settings_list'] > @[text='Bluetooth']"))
        assertEquals(2, query(root, "@[text='Wi-Fi'] + [text='Bluetooth']"))
        assertEquals(null, query(root, "[text='Missing']"))
    }

    private fun query(root: NodeInfo, source: String): Int? =
        adapter.querySelector(root, Selector.compile(source).value)?.id

    private fun node(
        id: Int,
        pid: Int,
        name: String? = null,
        viewId: String? = null,
        vid: String? = null,
        text: String? = null,
        clickable: Boolean = false,
        longClickable: Boolean = false,
        childCount: Int = 0,
        index: Int = 0,
        depth: Int = 0,
    ) = NodeInfo(
        id = id,
        pid = pid,
        idQf = false,
        textQf = false,
        attr = AttrInfo(
            id = viewId ?: vid?.let { "com.example.settings:id/$it" },
            vid = vid,
            name = name,
            text = text,
            desc = null,
            clickable = clickable,
            focusable = clickable,
            checkable = false,
            checked = null,
            editable = false,
            longClickable = longClickable,
            visibleToUser = true,
            left = 10,
            top = 20,
            right = 90,
            bottom = 40,
            width = 80,
            height = 20,
            childCount = childCount,
            index = index,
            depth = depth,
        ),
    )
}
