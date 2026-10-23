package li.gkd.app.authoring

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import li.gkd.app.model.ComplexSnapshot
import li.gkd.app.snapshot.SnapshotScreenshotStatus

/** One transient capture for one authoring session; never added to the snapshot gallery. */
data class GuidedCapture(
    val snapshot: ComplexSnapshot,
    val screenshot: ByteArray?,
    val screenshotStatus: SnapshotScreenshotStatus,
    val capturedAt: Long,
    val fromSnapshot: Boolean = false,
)

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
