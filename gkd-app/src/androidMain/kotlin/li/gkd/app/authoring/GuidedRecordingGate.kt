package li.gkd.app.authoring

import android.os.SystemClock

/**
 * Keeps the engine from starting an action once recording is requested, and marks
 * when recording began so a click already in flight is not taken as the demonstration.
 */
object GuidedRecordingGate {
    private val lock = Any()
    private var startedAtMillis = 0L

    fun start(context: GuidedRecordingContext) {
        synchronized(lock) {
            startedAtMillis = SystemClock.uptimeMillis()
            GuidedRecordingSession.start(context)
        }
    }

    fun isBeforeStart(eventTime: Long): Boolean = synchronized(lock) {
        startedAtMillis != 0L && eventTime <= startedAtMillis
    }

    suspend fun <T> performAction(block: suspend () -> T): T? =
        if (GuidedRecordingSession.isPaused) null else block()
}
