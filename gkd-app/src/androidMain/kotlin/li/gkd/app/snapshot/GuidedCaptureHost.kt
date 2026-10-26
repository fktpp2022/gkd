package li.gkd.app.snapshot

import li.gkd.app.authoring.GuidedCapture
import li.gkd.app.authoring.GuidedCaptureStore
import li.gkd.app.model.ComplexSnapshot

object GuidedCaptureHost {
    /** Captures the active window for an authoring session without touching the gallery. */
    suspend fun capture(): GuidedCapture {
        val input = SnapshotCaptureHost.captureInput()
        val snapshot = ComplexSnapshot(
            id = System.currentTimeMillis(),
            appId = input.appId,
            activityId = input.activityId,
            screenHeight = input.screenHeight,
            screenWidth = input.screenWidth,
            isLandscape = input.isLandscape,
            appInfo = input.appInfo,
            gkdAppInfo = input.gkdAppInfo,
            device = input.device,
            nodes = input.nodes,
        )
        val capture = GuidedCapture(
            snapshot = snapshot,
            screenshot = input.screenshot.takeIf {
                input.screenshotStatus == SnapshotScreenshotStatus.Captured
            },
            capturedAt = snapshot.id,
        )
        GuidedCaptureStore.put(capture)
        return capture
    }
}
