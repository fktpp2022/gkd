package li.gkd.app.ui.platform

import androidx.compose.runtime.Composable
import li.gkd.app.authoring.GuidedRecordingContext
import li.gkd.app.platform.PlatformResult
import li.gkd.app.settings.AutomatorMode

expect fun UiHost.setStatusServiceEnabled(enabled: Boolean)
expect fun UiHost.changeAutomatorMode(mode: AutomatorMode)
expect fun UiHost.finishGuidedAuthoring()

@Composable
expect fun guidedRecordingSupported(): Boolean

/** `Success(Unit)` means the host supports recording and accepted the request, not that a gate is running. */
expect fun UiHost.startGuidedRecording(context: GuidedRecordingContext): PlatformResult<Unit>
expect suspend fun UiHost.captureAndStartGuidedRecording(
    appId: String,
    appName: String?,
): PlatformResult<Boolean>
expect suspend fun UiHost.recaptureGuidedAuthoring(appId: String): PlatformResult<Boolean>
