package li.gkd.app.authoring

import li.gkd.app.model.NodeInfo

data class GuidedRecordingBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val hasArea: Boolean get() = right > left && bottom > top

    fun overlaps(other: GuidedRecordingBounds): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    fun contains(other: GuidedRecordingBounds): Boolean =
        other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom
}

data class GuidedTapEvent(
    val packageName: String,
    val longClick: Boolean,
    val sourceVid: String?,
    val sourceText: String?,
    val sourceDesc: String?,
    val sourceBounds: GuidedRecordingBounds?,
    val eventTexts: List<String>,
)

sealed interface GuidedRecordingResolution {
    data class Found(val nodes: List<NodeInfo>) : GuidedRecordingResolution
    data object NotFound : GuidedRecordingResolution
}

/** Resolves a demonstrated tap to nodes of the pre-tap capture (spec section 8.8). */
object GuidedRecordingResolver {
    fun resolve(event: GuidedTapEvent, nodes: List<NodeInfo>): GuidedRecordingResolution {
        val sourceBounds = event.sourceBounds?.takeIf { it.hasArea }
        val vid = event.sourceVid
        val candidates = when {
            vid != null -> {
                val matching = nodes.filter { it.attr.vid == vid }
                val overlapping = if (sourceBounds != null) {
                    matching.filter { boundsOf(it)?.overlaps(sourceBounds) == true }
                } else {
                    emptyList()
                }
                when {
                    overlapping.isNotEmpty() -> overlapping
                    matching.size == 1 -> matching
                    sourceBounds == null -> matching
                    else -> emptyList()
                }
            }

            !event.sourceText.isNullOrBlank() || !event.sourceDesc.isNullOrBlank() -> {
                if (sourceBounds == null) return GuidedRecordingResolution.NotFound
                nodes.filter { node ->
                    val attr = node.attr
                    (matches(attr.text, event.sourceText) || matches(attr.desc, event.sourceDesc)) &&
                            boundsOf(node)?.overlaps(sourceBounds) == true
                }
            }

            else -> {
                if (sourceBounds == null) return GuidedRecordingResolution.NotFound
                var ambiguous: List<NodeInfo>? = null
                for (text in event.eventTexts) {
                    if (text.isBlank()) continue
                    val inside = nodes.filter { node ->
                        val attr = node.attr
                        (attr.text == text || attr.desc == text) &&
                                boundsOf(node)?.let(sourceBounds::contains) == true
                    }
                    if (inside.size == 1) return GuidedRecordingResolution.Found(inside)
                    if (inside.size > 1 && ambiguous == null) {
                        ambiguous = inside
                    }
                }
                ambiguous.orEmpty()
            }
        }.distinctBy { it.id }
        return if (candidates.isEmpty()) {
            GuidedRecordingResolution.NotFound
        } else {
            GuidedRecordingResolution.Found(candidates)
        }
    }

    private fun matches(value: String?, expected: String?): Boolean =
        !expected.isNullOrBlank() && value == expected

    private fun boundsOf(node: NodeInfo): GuidedRecordingBounds? {
        val attr = node.attr
        if (attr.width <= 0 || attr.height <= 0) return null
        return GuidedRecordingBounds(attr.left, attr.top, attr.right, attr.bottom)
    }
}
