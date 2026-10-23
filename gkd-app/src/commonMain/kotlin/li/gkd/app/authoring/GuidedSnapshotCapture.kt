package li.gkd.app.authoring

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import li.gkd.app.model.ComplexSnapshot
import li.gkd.app.snapshot.SnapshotScreenshotStatus
import li.gkd.app.snapshot.SnapshotStore
import li.gkd.db.Snapshot

object GuidedSnapshotCapture {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    suspend fun isUsable(snapshot: Snapshot): Boolean = load(snapshot) != null

    suspend fun open(snapshot: Snapshot): Boolean {
        val complex = load(snapshot) ?: return false
        val screenshot = withContext(Dispatchers.IO) {
            SnapshotStore.screenshotFile(snapshot.id).takeIf { it.isFile }?.readBytes()
        }
        GuidedCaptureStore.put(
            GuidedCapture(
                snapshot = complex,
                screenshot = screenshot,
                screenshotStatus = if (screenshot != null) {
                    SnapshotScreenshotStatus.Captured
                } else {
                    SnapshotScreenshotStatus.Unavailable
                },
                capturedAt = snapshot.id,
                fromSnapshot = true,
            )
        )
        return true
    }

    private suspend fun load(snapshot: Snapshot): ComplexSnapshot? {
        if (snapshot.appId.isBlank()) return null
        val text = withContext(Dispatchers.IO) {
            SnapshotStore.snapshotFile(snapshot.id).takeIf { it.isFile }?.readText()
        } ?: return null
        val complex = withContext(Dispatchers.Default) {
            runCatching { json.decodeFromString<ComplexSnapshot>(text) }.getOrNull()
        } ?: return null
        val usable = complex.nodes.any {
            it.attr.visibleToUser && it.attr.width > 0 && it.attr.height > 0
        }
        return complex.takeIf { usable }
    }
}
