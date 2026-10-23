package li.gkd.app.service

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import li.gkd.app.a11y.A11yRuntime
import li.gkd.app.notif.NotificationCatalog
import li.gkd.app.permission.PermissionStates
import li.gkd.app.resources.*
import li.gkd.app.snapshot.GuidedCaptureHost
import li.gkd.app.ui.GuidedAuthoringLaunch
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.share.launchUi
import li.gkd.app.util.IntentUtils
import li.gkd.app.util.LogUtils

class GuidedRuleButtonService : OverlayWindowService(
    positionKey = "guided_rule_button"
) {
    override fun onClickView() {
        if (isOverlayContentHidden) return
        lifecycleScope.launchUi {
            try {
                withAllOverlaysHidden {
                    GuidedCaptureHost.capture()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                LogUtils.d("引导规则截图失败", e)
            }
            GuidedAuthoringLaunch.open()
        }
    }

    override fun onLongClickView() = stopSelf()

    @Composable
    override fun ComposeContent() {
        val alpha = 0.75f
        GkIcon(
            imageVector = GkIcons.Rule,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = alpha))
                .size(40.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
        )
    }

    init {
        useServicePresence(
            stateFlow = isRunning,
            name = Res.string.guided_button_service.getSync(),
        )
        onCreated {
            NotificationCatalog.guidedRule().startForeground()
        }
    }

    companion object {
        val isRunning: StateFlow<Boolean>
            field = MutableStateFlow(false)

        fun start() {
            if (!PermissionStates.drawOverlays.checkOrToast()) return
            IntentUtils.startForegroundService(GuidedRuleButtonService::class)
        }

        fun stop() = IntentUtils.stopService(GuidedRuleButtonService::class)
    }
}
