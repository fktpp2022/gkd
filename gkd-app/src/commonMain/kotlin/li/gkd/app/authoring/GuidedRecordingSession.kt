package li.gkd.app.authoring

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class GuidedRecordingContext(
    val appId: String,
    val appName: String?,
    val activityId: String?,
)

enum class GuidedRecordingPhase { Idle, Recording, Resolving }

data class GuidedRecordingStatus(
    val phase: GuidedRecordingPhase = GuidedRecordingPhase.Idle,
    val context: GuidedRecordingContext? = null,
    /** Activity the captured app showed when the phase came forward, once a named window state arrives. */
    val baselineActivityId: String? = null,
) {
    /** Stop ends the session only while taps are still being accepted. */
    val canStop: Boolean get() = phase == GuidedRecordingPhase.Recording
}

sealed interface GuidedRecordingSignal {
    data class Tap(val event: GuidedTapEvent) : GuidedRecordingSignal
    data object Stopped : GuidedRecordingSignal
    data object ScreenChanged : GuidedRecordingSignal
}

/**
 * The one live recording phase. Platform code feeds taps and window changes;
 * the authoring view model resolves the tap against the frozen capture.
 */
object GuidedRecordingSession {
    val status: StateFlow<GuidedRecordingStatus>
        field = MutableStateFlow(GuidedRecordingStatus())

    val phase: GuidedRecordingPhase get() = status.value.phase

    /** Matching stays paused from [start] until [finish], including the tap's screen-change wait. */
    val isPaused: Boolean get() = status.value.phase != GuidedRecordingPhase.Idle

    val signals: SharedFlow<GuidedRecordingSignal>
        field = MutableSharedFlow(extraBufferCapacity = 8)

    fun start(context: GuidedRecordingContext) {
        status.value = GuidedRecordingStatus(
            phase = GuidedRecordingPhase.Recording,
            context = context,
        )
    }

    fun stop() {
        if (!status.value.canStop) return
        status.value = GuidedRecordingStatus()
        signals.tryEmit(GuidedRecordingSignal.Stopped)
    }

    fun onTap(event: GuidedTapEvent) {
        val current = status.value
        val context = current.context ?: return
        if (current.phase != GuidedRecordingPhase.Recording) return
        if (event.packageName != context.appId) return
        status.value = current.copy(phase = GuidedRecordingPhase.Resolving)
        signals.tryEmit(GuidedRecordingSignal.Tap(event))
    }

    fun onWindowState(packageName: String, className: String?) {
        val current = status.value
        val context = current.context ?: return
        if (packageName != context.appId) return
        val activityId = normalizeActivityId(packageName, className) ?: return
        when (current.phase) {
            GuidedRecordingPhase.Idle -> Unit

            GuidedRecordingPhase.Resolving -> {
                val expected = current.baselineActivityId
                    ?: normalizeActivityId(packageName, context.activityId)
                if (expected != null && activityId != expected) {
                    signals.tryEmit(GuidedRecordingSignal.ScreenChanged)
                }
            }

            GuidedRecordingPhase.Recording -> {
                val baseline = current.baselineActivityId
                if (baseline == null) {
                    status.update { it.copy(baselineActivityId = activityId) }
                    val expected = normalizeActivityId(packageName, context.activityId)
                    if (expected != null && activityId != expected) {
                        finishWith(GuidedRecordingSignal.ScreenChanged)
                    }
                } else if (activityId != baseline) {
                    finishWith(GuidedRecordingSignal.ScreenChanged)
                }
            }
        }
    }

    private fun normalizeActivityId(packageName: String, className: String?): String? {
        val name = className?.takeIf { it.isNotBlank() } ?: return null
        return if (name.startsWith('.')) packageName + name else name
    }

    fun finish() {
        status.value = GuidedRecordingStatus()
    }

    private fun finishWith(signal: GuidedRecordingSignal) {
        status.value = GuidedRecordingStatus()
        signals.tryEmit(signal)
    }
}
