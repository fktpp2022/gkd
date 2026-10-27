package li.gkd.app.authoring

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import li.gkd.app.model.ComplexSnapshot
import li.gkd.app.snapshot.SnapshotStore
import li.gkd.db.Snapshot

object GuidedSnapshotCapture {
    /** Decodes a stored snapshot once and returns its tree when it can open an authoring session. */
    suspend fun load(snapshot: Snapshot): ComplexSnapshot? {
        if (snapshot.appId.isBlank()) return null
        return SnapshotStore.loadSnapshot(snapshot.id)?.takeIf { it.nodes.hasUsableNode() }
    }

    suspend fun open(snapshot: Snapshot): Boolean =
        load(snapshot)?.let { open(snapshot, it) } ?: false

    /** Opens a session from a tree already returned by [load]; nothing is decoded again. */
    suspend fun open(snapshot: Snapshot, complex: ComplexSnapshot): Boolean {
        val screenshot = withContext(Dispatchers.IO) {
            SnapshotStore.screenshotFile(snapshot.id).takeIf { it.isFile }?.readBytes()
        }
        GuidedCaptureStore.put(
            GuidedCapture(
                snapshot = complex,
                screenshot = screenshot,
                capturedAt = snapshot.id,
                fromSnapshot = true,
            )
        )
        return true
    }
}
