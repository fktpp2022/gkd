package li.gkd.app.service

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import li.gkd.app.META
import li.gkd.app.a11y.A11yRuntime
import li.gkd.app.a11y.currentTopActivity
import li.gkd.app.a11y.topActivityFlow
import li.gkd.app.authoring.GuidedRecordingContext
import li.gkd.app.authoring.GuidedRecordingPhase
import li.gkd.app.authoring.GuidedRecordingSession
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
import org.jetbrains.compose.resources.stringResource

class GuidedRuleButtonService : OverlayWindowService(
    positionKey = "guided_rule_button"
) {
    override fun onClickView() {
        if (GuidedRecordingSession.phase != GuidedRecordingPhase.Idle) return
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

    override fun isViewClickEnabled(): Boolean =
        GuidedRecordingSession.phase == GuidedRecordingPhase.Idle

    override fun onLongClickView() {
        if (GuidedRecordingSession.phase != GuidedRecordingPhase.Idle) return
        stopSelf()
    }

    @Composable
    override fun ComposeContent() {
        val status by GuidedRecordingSession.status.collectAsState()
        val topActivity by topActivityFlow.collectAsState(initial = currentTopActivity)
        val context = status.context
        // GKD's own UI (authoring included) sits above the target app, so the floating
        // capture entry is useless there; draw nothing to also avoid intercepting taps.
        // The chip is a separate surface and stays visible for the live recording phase.
        if (status.phase == GuidedRecordingPhase.Idle || context == null) {
            if (topActivity.appId != META.appId) {
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
        } else {
            GuidedRecordingChip(context)
        }
    }

    @Composable
    private fun GuidedRecordingChip(context: GuidedRecordingContext) {
        val appName = context.appName ?: context.appId
        val description = stringResource(Res.string.guided_recording_announcement, appName)
        Surface(
            modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            },
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
        ) {
            Column(Modifier.padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 0.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Spacer(
                        Modifier
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.error, CircleShape),
                    )
                    Text(
                        text = stringResource(Res.string.guided_recording_title, appName),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Text(
                    text = stringResource(Res.string.guided_recording_paused),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { GuidedRecordingSession.stop() }) {
                    Text(stringResource(Res.string.guided_recording_stop))
                }
            }
        }
    }

    init {
        useServicePresence(
            stateFlow = isRunning,
            name = Res.string.guided_button_service.getSync(),
        )
        onCreated {
            NotificationCatalog.guidedRule().startForeground()
        }
        lifecycleScope.launch {
            var active = GuidedRecordingSession.phase != GuidedRecordingPhase.Idle
            GuidedRecordingSession.status.collect { status ->
                val nowActive = status.phase != GuidedRecordingPhase.Idle
                if (active && !nowActive) {
                    GuidedAuthoringLaunch.returnToAuthoring()
                    if (stopAfterRecording) {
                        stopAfterRecording = false
                        stopSelf()
                    }
                }
                active = nowActive
            }
        }
    }

    companion object {
        val isRunning: StateFlow<Boolean>
            field = MutableStateFlow(false)

        private var stopAfterRecording = false

        fun start() {
            if (!PermissionStates.drawOverlays.checkOrToast()) return
            IntentUtils.startForegroundService(GuidedRuleButtonService::class)
        }

        fun startForRecording(): Boolean {
            if (isRunning.value) return true
            if (!PermissionStates.drawOverlays.checkOrToast()) return false
            stopAfterRecording = true
            IntentUtils.startForegroundService(GuidedRuleButtonService::class)
            return true
        }

        fun stop() = IntentUtils.stopService(GuidedRuleButtonService::class)
    }
}
