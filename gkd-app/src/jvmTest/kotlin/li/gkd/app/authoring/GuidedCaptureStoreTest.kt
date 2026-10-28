package li.gkd.app.authoring

import li.gkd.app.model.ComplexSnapshot
import li.gkd.app.model.DeviceInfo
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GuidedCaptureStoreTest {
    fun runAll() {
        putThenClearReflectsContract()
    }

    private fun putThenClearReflectsContract() {
        val first = capture(1L)
        val second = capture(2L)

        GuidedCaptureStore.clear()
        assertNull(GuidedCaptureStore.current.value)

        GuidedCaptureStore.put(first)
        assertEquals(first, GuidedCaptureStore.current.value)

        // Second put replaces, never accumulates.
        GuidedCaptureStore.put(second)
        assertEquals(second, GuidedCaptureStore.current.value)

        GuidedCaptureStore.clear()
        assertNull(GuidedCaptureStore.current.value)
    }

    private fun capture(id: Long): GuidedCapture = GuidedCapture(
        snapshot = ComplexSnapshot(
            id = id,
            appId = "test.authoring",
            activityId = null,
            screenHeight = 100,
            screenWidth = 100,
            isLandscape = false,
            appInfo = null,
            gkdAppInfo = null,
            device = DeviceInfo("test", "test", "test", "test", 0, "test"),
            nodes = emptyList(),
        ),
        screenshot = null,
        capturedAt = id,
    )
}
