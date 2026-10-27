package li.gkd.app.authoring

import li.gkd.app.model.NodeInfo

/** Body of A2's "Choose from a list" sheet. */
sealed interface GuidedNodeListContent {
    data class Nodes(val nodes: List<NodeInfo>) : GuidedNodeListContent

    /** The capture has no labeled node to list, so the sheet offers the manual editor instead. */
    data object Manual : GuidedNodeListContent
}

fun guidedNodeListContent(nodes: List<NodeInfo>): GuidedNodeListContent {
    val labeled = nodes.filter { it.isUsableNode() && GuidedNodeLabel.of(it) != null }
    return if (labeled.isEmpty()) GuidedNodeListContent.Manual else GuidedNodeListContent.Nodes(labeled)
}

/**
 * A2b preselection over candidates that the hit test already ordered smallest-first:
 * the smallest clickable node with text, otherwise the smallest candidate.
 */
fun preselectedOverlap(candidates: List<NodeInfo>): NodeInfo? =
    candidates.firstOrNull { it.attr.clickable && !it.attr.text.isNullOrBlank() }
        ?: candidates.firstOrNull()
