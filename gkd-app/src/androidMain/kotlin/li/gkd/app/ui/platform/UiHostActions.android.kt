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

actual fun UiHost.startGuidedRecording(context: GuidedRecordingContext): Boolean {
    if (!GuidedRuleButtonService.startForRecording()) return false
    GuidedRecordingGate.start(context)
    moveTaskToBack(true)
    return true
}

actual suspend fun UiHost.captureAndStartGuidedRecording(
    appId: String,
    appName: String?,
): Boolean {
    val capture = captureTargetApp(appId)
    if (capture == null) {
        GuidedAuthoringLaunch.returnToAuthoring()
        return false
    }
    if (!GuidedRuleButtonService.startForRecording()) {
        GuidedAuthoringLaunch.returnToAuthoring()
        return false
    }
    GuidedRecordingGate.start(
        GuidedRecordingContext(
            appId = appId,
            appName = appName ?: appId,
            activityId = capture.snapshot.activityId,
        )
    )
    return true
}

actual suspend fun UiHost.recaptureGuidedAuthoring(appId: String): Boolean {
    val captured = captureTargetApp(appId) != null
    GuidedAuthoringLaunch.returnToAuthoring()
    return captured
}

private suspend fun UiHost.captureTargetApp(appId: String): GuidedCapture? {
    moveTaskToBack(true)
    withTimeoutOrNull(1500) {
        while (currentTopActivity.appId == META.appId) {
            delay(50)
        }
    }
    val capture = try {
        GuidedCaptureHost.capture()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        LogUtils.d("引导规则重新截图失败", e)
        null
    }
    return capture?.takeIf {
        it.snapshot.appId == appId && it.snapshot.nodes.any { node ->
            node.attr.visibleToUser && node.attr.width > 0 && node.attr.height > 0
        }
    }
}
