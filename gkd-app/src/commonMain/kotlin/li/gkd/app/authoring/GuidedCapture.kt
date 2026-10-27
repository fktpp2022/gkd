package li.gkd.app.authoring

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import li.gkd.app.model.ComplexSnapshot
import li.gkd.app.model.NodeInfo

/** One transient capture for one authoring session; never added to the snapshot gallery. */
data class GuidedCapture(
    val snapshot: ComplexSnapshot,
    val screenshot: ByteArray?,
    val capturedAt: Long,
    val fromSnapshot: Boolean = false,
)

/** A capture node that can be picked: visible to the user with a non-zero area. */
fun NodeInfo.isUsableNode(): Boolean =
    attr.visibleToUser && attr.width > 0 && attr.height > 0

fun List<NodeInfo>.hasUsableNode(): Boolean = any { it.isUsableNode() }

object GuidedCaptureStore {
    val current: StateFlow<GuidedCapture?>
        field = MutableStateFlow(null)

    fun put(capture: GuidedCapture) {
        current.value = capture
    }

    fun clear() {
        current.value = null
    }
}
