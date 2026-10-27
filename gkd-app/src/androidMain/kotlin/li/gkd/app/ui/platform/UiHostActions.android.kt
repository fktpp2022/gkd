package li.gkd.app.ui.platform

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import li.gkd.app.META
import li.gkd.app.a11y.currentTopActivity
import li.gkd.app.authoring.GuidedCapture
import li.gkd.app.authoring.GuidedRecordingContext
import li.gkd.app.authoring.GuidedRecordingGate
import li.gkd.app.authoring.hasUsableNode
import li.gkd.app.platform.PlatformResult
import li.gkd.app.platform.service.ServiceController
import li.gkd.app.service.A11yService
import li.gkd.app.service.GuidedRuleButtonService
import li.gkd.app.snapshot.GuidedCaptureHost
import li.gkd.app.ui.GuidedAuthoringLaunch
import li.gkd.app.ui.androidState
import li.gkd.app.settings.AutomatorMode
import li.gkd.app.ui.share.launchUi
import li.gkd.app.util.LogUtils

actual fun UiHost.setStatusServiceEnabled(enabled: Boolean) {
    if (enabled) mainVm.scope.launchUi { mainVm.androidState.enableStatusService() }
    else ServiceController.setStatusEnabled(false)
}

actual fun UiHost.changeAutomatorMode(mode: AutomatorMode) {
    mainVm.androidState.updateAutomatorMode(mode)
}

actual fun UiHost.finishGuidedAuthoring() {
    finish()
}

@Composable
actual fun guidedRecordingSupported(): Boolean =
    A11yService.isRunning.collectAsStateWithLifecycle().value

actual fun UiHost.startGuidedRecording(context: GuidedRecordingContext): PlatformResult<Unit> {
    // A missing overlay permission is surfaced by the service's own toast; this result
    // only distinguishes hosts that cannot record at all.
    if (GuidedRuleButtonService.startForRecording()) {
        GuidedRecordingGate.start(context)
        moveTaskToBack(true)
    }
    return PlatformResult.Success(Unit)
}

actual suspend fun UiHost.captureAndStartGuidedRecording(
    appId: String,
    appName: String?,
): PlatformResult<Boolean> {
    val capture = captureTargetApp(appId)
    if (capture == null) {
        GuidedAuthoringLaunch.returnToAuthoring()
        return PlatformResult.Success(false)
    }
    if (!GuidedRuleButtonService.startForRecording()) {
        GuidedAuthoringLaunch.returnToAuthoring()
        return PlatformResult.Success(false)
    }
    GuidedRecordingGate.start(
        GuidedRecordingContext(
            appId = appId,
            appName = appName ?: appId,
            activityId = capture.snapshot.activityId,
        )
    )
    return PlatformResult.Success(true)
}

actual suspend fun UiHost.recaptureGuidedAuthoring(appId: String): PlatformResult<Boolean> {
    val captured = captureTargetApp(appId) != null
    GuidedAuthoringLaunch.returnToAuthoring()
    return PlatformResult.Success(captured)
}

private suspend fun UiHost.captureTargetApp(appId: String): GuidedCapture? {
    moveTaskToBack(true)
    withTimeoutOrNull(1500) {
        while (currentTopActivity.appId == META.appId) {
            delay(50)
        }
    }
    val capture = try {
        GuidedRuleButtonService.withSurfacesHidden {
            GuidedCaptureHost.capture()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        LogUtils.d("引导规则重新截图失败", e)
        null
    }
    return capture?.takeIf {
        it.snapshot.appId == appId && it.snapshot.nodes.hasUsableNode()
    }
}
