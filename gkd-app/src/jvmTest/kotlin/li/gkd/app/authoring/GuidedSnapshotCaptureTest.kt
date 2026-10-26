package li.gkd.app.authoring

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import li.gkd.app.model.ComplexSnapshot
import li.gkd.app.model.DeviceInfo
import li.gkd.app.model.NodeInfo
import li.gkd.app.storage.AppStorageLayout
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuidedSnapshotCaptureTest(private val layout: AppStorageLayout) {
    fun runAll() = runBlocking {
        usableSnapshotLoadsOnceAndOpensSession()
        snapshotWithoutUsableNodeDoesNotOpen()
        unknownFieldsAndExplicitNullsStillParse()
    }

    private suspend fun usableSnapshotLoadsOnceAndOpensSession() {
        val node = testNode(1, 0, text = "OK", clickable = true)
        val stored = writeSnapshot(750001, listOf(node))
        GuidedCaptureStore.clear()
        try {
            val loaded = GuidedSnapshotCapture.load(stored.toSnapshot())
            assertNotNull(loaded)
            assertEquals(listOf(node), loaded.nodes)

            // open() reuses the tree loaded above instead of decoding the file again.
            jsonFile(stored.id).delete()
            assertTrue(GuidedSnapshotCapture.open(stored.toSnapshot(), loaded))
            val capture = assertNotNull(GuidedCaptureStore.current.value)
            assertEquals(listOf(node), capture.snapshot.nodes)
            assertEquals(stored.id, capture.capturedAt)
            assertTrue(capture.fromSnapshot)
        } finally {
            GuidedCaptureStore.clear()
            snapshotDirectory(stored.id).deleteRecursively()
        }
    }

    private suspend fun snapshotWithoutUsableNodeDoesNotOpen() {
        val invisible = testNode(1, 0, text = "Hidden")
            .copy(attr = testNode(1, 0).attr.copy(visibleToUser = false))
        val zeroArea = testNode(2, 0, text = "Collapsed", bounds = GuidedRecordingBounds(10, 10, 10, 10))
        val stored = writeSnapshot(750002, listOf(invisible, zeroArea))
        GuidedCaptureStore.clear()
        try {
            assertNull(GuidedSnapshotCapture.load(stored.toSnapshot()))
            assertFalse(GuidedSnapshotCapture.open(stored.toSnapshot()))
            assertNull(GuidedCaptureStore.current.value)
        } finally {
            GuidedCaptureStore.clear()
            snapshotDirectory(stored.id).deleteRecursively()
        }
    }

    private suspend fun unknownFieldsAndExplicitNullsStillParse() {
        val node = testNode(1, 0, text = "Continue", clickable = true)
        val expected = writeSnapshot(750003, listOf(node), unknownField = true)
        try {
            val loaded = GuidedSnapshotCapture.load(expected.toSnapshot())
            assertEquals(expected, loaded)
            assertEquals(null, loaded?.activityId)
            assertEquals(null, loaded?.appInfo)
        } finally {
            snapshotDirectory(expected.id).deleteRecursively()
        }
    }

    private fun writeSnapshot(
        id: Long,
        nodes: List<NodeInfo>,
        unknownField: Boolean = false,
    ): ComplexSnapshot {
        val snapshot = ComplexSnapshot(
            id = id,
            appId = "test.authoring",
            activityId = null,
            screenHeight = 100,
            screenWidth = 100,
            isLandscape = false,
            appInfo = null,
            gkdAppInfo = null,
            device = DeviceInfo("test", "test", "test", "test", 0, "test"),
            nodes = nodes,
        )
        var text = Json { explicitNulls = true }.encodeToString(snapshot)
        if (unknownField) {
            val fields = Json.parseToJsonElement(text).jsonObject.toMutableMap()
            fields["futureCaptureField"] = JsonPrimitive("ignored")
            text = JsonObject(fields).toString()
        }
        snapshotDirectory(id).mkdirs()
        jsonFile(id).writeText(text)
        return snapshot
    }

    private fun snapshotDirectory(id: Long) = layout.snapshot.resolve(id.toString())

    private fun jsonFile(id: Long) = snapshotDirectory(id).resolve("$id.json")
}
