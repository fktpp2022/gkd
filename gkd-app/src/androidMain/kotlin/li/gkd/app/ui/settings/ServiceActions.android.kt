package li.gkd.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.a11y.A11yRuntime
import li.gkd.app.permission.PermissionStates
import li.gkd.app.platform.service.ServiceController
import li.gkd.app.resources.*
import li.gkd.app.service.ActivityService
import li.gkd.app.service.ButtonService
import li.gkd.app.service.EventService
import li.gkd.app.service.GuidedRuleButtonService
import li.gkd.app.service.HttpService
import li.gkd.app.service.TrackService
import li.gkd.app.ui.androidState
import li.gkd.app.ui.platform.UiHost
import li.gkd.app.ui.share.launchUi
import li.gkd.app.util.IntentUtils
import li.gkd.app.util.ToastUtils

actual fun UiHost.setSnapshotButtonEnabled(enabled: Boolean) {
    mainVm.scope.launchUi {
        if (!enabled || mainVm.androidState.permissionRequests.ensurePermissions(
                PermissionStates.foregroundServiceSpecialUse,
                PermissionStates.notification,
                PermissionStates.drawOverlays,
            )
        ) {
            ServiceController.setSnapshotButtonEnabled(enabled)
        }
    }
}

actual fun UiHost.setGuidedRuleButtonEnabled(enabled: Boolean) {
    if (!enabled) {
        ServiceController.setGuidedRuleButtonEnabled(false)
        return
    }
    mainVm.scope.launchUi {
        if (!mainVm.androidState.permissionRequests.ensurePermissions(
                PermissionStates.foregroundServiceSpecialUse,
                PermissionStates.notification,
                PermissionStates.drawOverlays,
            )
        ) return@launchUi
        if (A11yRuntime.service == null) {
            ToastUtils.show(Res.string.guided_a11y_required.getSync())
            IntentUtils.openA11ySettings()
            return@launchUi
        }
        ServiceController.setGuidedRuleButtonEnabled(true)
    }
}

actual fun UiHost.setHttpServiceEnabled(enabled: Boolean) {
    mainVm.scope.launchUi {
        if (!enabled || mainVm.androidState.permissionRequests.ensurePermissions(
                PermissionStates.foregroundServiceSpecialUse,
                PermissionStates.notification,
                PermissionStates.localNetwork,
            )
        ) {
            ServiceController.setHttpEnabled(enabled)
        }
    }
}

actual fun UiHost.setActivityMonitorEnabled(enabled: Boolean) {
    mainVm.scope.launchUi {
        if (!enabled || mainVm.androidState.permissionRequests.ensurePermissions(
                PermissionStates.foregroundServiceSpecialUse,
                PermissionStates.notification,
                PermissionStates.drawOverlays,
            )
        ) {
            ServiceController.setActivityMonitorEnabled(enabled)
        }
    }
}

actual fun UiHost.setEventMonitorEnabled(enabled: Boolean) {
    mainVm.scope.launchUi {
        if (!enabled || mainVm.androidState.permissionRequests.ensurePermissions(
                PermissionStates.foregroundServiceSpecialUse,
                PermissionStates.notification,
                PermissionStates.drawOverlays,
            )
        ) {
            ServiceController.setEventMonitorEnabled(enabled)
        }
    }
}

actual fun UiHost.setTrackServiceEnabled(enabled: Boolean) {
    mainVm.scope.launchUi {
        if (!enabled || mainVm.androidState.permissionRequests.ensurePermissions(
                PermissionStates.foregroundServiceSpecialUse,
                PermissionStates.notification,
                PermissionStates.drawOverlays,
            )
        ) {
            if (enabled) TrackService.start() else TrackService.stop()
        }
    }
}

@Composable
actual fun httpRunning() =
    HttpService.httpServerFlow.collectAsStateWithLifecycle().value != null

@Composable
actual fun httpServerAddresses() =
    listOf(li.gkd.app.util.Constants.loopbackHost) + HttpService.localNetworkIpsFlow.collectAsStateWithLifecycle().value

@Composable
actual fun snapshotButtonRunning() =
    ButtonService.isRunning.collectAsStateWithLifecycle().value

@Composable
actual fun guidedButtonRunning() =
    GuidedRuleButtonService.isRunning.collectAsStateWithLifecycle().value

@Composable
actual fun activityMonitorRunning() =
    ActivityService.isRunning.collectAsStateWithLifecycle().value

@Composable
actual fun eventMonitorRunning() =
    EventService.isRunning.collectAsStateWithLifecycle().value

@Composable
actual fun trackServiceRunning() =
    TrackService.isRunning.collectAsStateWithLifecycle().value

// The Android service already applies port changes in its lifecycle-owned collector.
actual fun UiHost.httpServerPortChanged() = Unit
