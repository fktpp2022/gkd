package li.gkd.app.a11y

import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import li.gkd.app.authoring.GuidedRecordingBounds
import li.gkd.app.authoring.GuidedRecordingGate
import li.gkd.app.authoring.GuidedRecordingSession
import li.gkd.app.authoring.GuidedTapEvent
import li.gkd.app.util.AndroidTarget

object GuidedRecordingBridge {
    fun onEvent(event: AccessibilityEvent) {
        if (!GuidedRecordingSession.isPaused) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                if (AndroidTarget.S && event.action == AccessibilityNodeInfo.ACTION_CLICK) return
                if (GuidedRecordingGate.isBeforeStart(event.eventTime)) return
                val tap = event.toGuidedTapEvent() ?: return
                GuidedRecordingSession.onTap(tap)
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val packageName = event.packageName?.toString() ?: return
                GuidedRecordingSession.onWindowState(
                    packageName,
                    event.className?.toString(),
                )
            }
        }
    }

    private fun AccessibilityEvent.toGuidedTapEvent(): GuidedTapEvent? {
        val packageName = packageName?.toString() ?: return null
        val source = try {
            source
        } catch (_: Throwable) {
            null
        }
        val bounds = source?.let { node ->
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() <= 0 || rect.height() <= 0) null else GuidedRecordingBounds(
                left = rect.left,
                top = rect.top,
                right = rect.right,
                bottom = rect.bottom,
            )
        }
        return GuidedTapEvent(
            packageName = packageName,
            longClick = eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
            sourceVid = source?.getVid()?.toString(),
            sourceText = source?.text?.toString()?.takeIf { it.isNotBlank() },
            sourceDesc = source?.contentDescription?.toString()?.takeIf { it.isNotBlank() },
            sourceBounds = bounds,
            eventTexts = text.orEmpty().mapNotNull { it?.toString()?.takeIf(String::isNotBlank) },
        )
    }
}
