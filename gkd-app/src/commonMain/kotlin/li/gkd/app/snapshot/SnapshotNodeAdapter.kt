package li.gkd.app.snapshot

import li.gkd.app.model.NodeInfo
import li.gkd.selector.NodeAdapter

class SnapshotNodeAdapter(nodes: List<NodeInfo>) : NodeAdapter<NodeInfo>() {
    private val nodesById = nodes.associateBy(NodeInfo::id)
    private val childrenByParent = nodes.groupBy(NodeInfo::pid)

    val root: NodeInfo? = nodes.firstOrNull { nodesById[it.pid] == null }

    override fun getAttr(target: Any, name: String): Any? {
        val node = target as? NodeInfo ?: return null
        val attr = node.attr
        return when (name) {
            "id" -> attr.id
            "vid" -> attr.vid
            "name" -> attr.name
            "text" -> attr.text
            "desc" -> attr.desc

            "clickable" -> attr.clickable
            "focusable" -> attr.focusable
            "checkable" -> attr.checkable
            "checked" -> attr.checked
            "editable" -> attr.editable
            "longClickable" -> attr.longClickable
            "visibleToUser" -> attr.visibleToUser

            "left" -> attr.left
            "top" -> attr.top
            "right" -> attr.right
            "bottom" -> attr.bottom
            "width" -> attr.width
            "height" -> attr.height

            "index" -> attr.index
            "depth" -> attr.depth
            "childCount" -> attr.childCount

            "parent" -> getParent(node)
            else -> null
        }
    }

    override fun getInvoke(target: Any, name: String, args: List<Any>): Any? {
        val node = target as? NodeInfo ?: return null
        return when (name) {
            "getChild" -> (args.firstOrNull() as? Int)?.let { getChild(node, it) }
            else -> null
        }
    }

    override fun getName(node: NodeInfo): String? = node.attr.name

    override fun getChildCount(node: NodeInfo): Int = children(node).size

    override fun getChild(node: NodeInfo, index: Int): NodeInfo? = children(node).getOrNull(index)

    override fun getParent(node: NodeInfo): NodeInfo? = nodesById[node.pid]

    override fun getNodeKey(node: NodeInfo): Any = node.id

    private fun children(node: NodeInfo): List<NodeInfo> =
        childrenByParent[node.id].orEmpty()
}
