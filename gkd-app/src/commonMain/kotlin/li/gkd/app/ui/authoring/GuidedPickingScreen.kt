package li.gkd.app.ui.authoring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import li.gkd.app.authoring.GuidedContextMode
import li.gkd.app.model.ComplexSnapshot
import li.gkd.app.model.NodeInfo
import li.gkd.app.resources.*
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIcons
import org.jetbrains.compose.resources.stringResource

@Composable
fun GuidedPickingContent(
    snapshot: ComplexSnapshot,
    screenshot: ByteArray?,
    sourceLabel: String?,
    indexing: Boolean,
    pending: NodeInfo?,
    pickLabel: li.gkd.app.authoring.GuidedPick?,
    failure: GuidedRecordingFailure?,
    onCandidates: (List<NodeInfo>) -> Unit,
    onUse: () -> Unit,
    onChooseAgain: () -> Unit,
    onChooseFromList: () -> Unit,
    onPickItInstead: () -> Unit,
    onRecordAgain: () -> Unit,
    onCaptureAndRecord: () -> Unit,
    onCancelRecording: () -> Unit,
) {
    val pickAt = if (indexing || failure != null) null else onCandidates
    Column(modifier = Modifier.fillMaxSize()) {
        if (failure == null) {
            Text(
                text = stringResource(
                    if (indexing) {
                        Res.string.guided_finding_elements
                    } else {
                        Res.string.guided_pick_hint
                    },
                ),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            GuidedRecordingFailureContent(
                failure = failure,
                onPickItInstead = onPickItInstead,
                onRecordAgain = onRecordAgain,
                onCaptureAndRecord = onCaptureAndRecord,
                onCancel = onCancelRecording,
            )
        }
        if (sourceLabel != null) {
            Text(
                text = sourceLabel,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        GuidedRuleCanvas(
            snapshot = snapshot,
            screenshot = screenshot,
            matches = emptyList(),
            actableId = pickLabel?.actable?.id,
            selectedId = pending?.id,
            description = stringResource(Res.string.guided_canvas_pick_description),
            onCandidates = pickAt,
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp),
        )
        if (screenshot == null) {
            Text(
                text = stringResource(Res.string.guided_protected_screenshot),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (failure == null && !indexing) {
            if (pending != null) {
                GuidedSelectedSummary(node = pending, pick = pickLabel)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onChooseFromList) {
                    Text(stringResource(Res.string.guided_choose_from_list))
                }
                Spacer(Modifier.weight(1f))
                if (pending != null) {
                    OutlinedButton(onClick = onChooseAgain) {
                        Text(stringResource(Res.string.guided_choose_again))
                    }
                    Button(onClick = onUse) {
                        Text(stringResource(Res.string.guided_use_element))
                    }
                }
            }
        }
    }
}

@Composable
private fun GuidedRecordingFailureContent(
    failure: GuidedRecordingFailure,
    onPickItInstead: () -> Unit,
    onRecordAgain: () -> Unit,
    onCaptureAndRecord: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when (failure) {
                GuidedRecordingFailure.NoTap -> {
                    Text(
                        text = stringResource(Res.string.guided_nothing_recorded),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(Res.string.guided_nothing_recorded_stopped),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(Res.string.guided_nothing_recorded_helper),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                GuidedRecordingFailure.NoNode -> {
                    Text(
                        text = stringResource(Res.string.guided_nothing_recorded),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(Res.string.guided_nothing_recorded_no_match),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                GuidedRecordingFailure.ScreenChanged -> {
                    Text(
                        text = stringResource(Res.string.guided_screen_changed),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(Res.string.guided_screen_changed_nothing),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (failure == GuidedRecordingFailure.ScreenChanged) {
                    Button(onClick = onCaptureAndRecord) {
                        Text(stringResource(Res.string.guided_capture_this_screen))
                    }
                    TextButton(onClick = onPickItInstead) {
                        Text(stringResource(Res.string.guided_pick_on_captured_screen))
                    }
                } else {
                    Button(onClick = onPickItInstead) {
                        Text(stringResource(Res.string.guided_pick_it_instead))
                    }
                    TextButton(onClick = onRecordAgain) {
                        Text(stringResource(Res.string.guided_record_again))
                    }
                }
                TextButton(onClick = onCancel) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        }
    }
}

@Composable
private fun GuidedSelectedSummary(
    node: NodeInfo,
    pick: li.gkd.app.authoring.GuidedPick?,
) {
    val label = guidedNodeLabel(node)
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                text = stringResource(Res.string.guided_selected, label),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (pick?.climbed == true) {
                Text(
                    text = stringResource(Res.string.guided_actable_climb),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
fun GuidedContextContent(
    snapshot: ComplexSnapshot,
    screenshot: ByteArray?,
    preview: GuidedPreviewState,
    mode: GuidedContextMode,
    error: Boolean,
    onMode: (GuidedContextMode) -> Unit,
    onCandidates: (List<NodeInfo>) -> Unit,
    onCancel: () -> Unit,
) {
    val matches = (preview as? GuidedPreviewState.Ready)?.result?.matches.orEmpty()
    val actableId = (preview as? GuidedPreviewState.Ready)?.result?.actable?.id
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(Res.string.guided_narrow_hint),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = mode == GuidedContextMode.Inside,
                onClick = { onMode(GuidedContextMode.Inside) },
                label = { Text(stringResource(Res.string.guided_context_inside)) },
            )
            FilterChip(
                selected = mode == GuidedContextMode.NextTo,
                onClick = { onMode(GuidedContextMode.NextTo) },
                label = { Text(stringResource(Res.string.guided_context_next_to)) },
            )
        }
        GuidedRuleCanvas(
            snapshot = snapshot,
            screenshot = screenshot,
            matches = matches,
            actableId = actableId,
            selectedId = null,
            description = stringResource(Res.string.guided_canvas_context_description),
            onCandidates = onCandidates,
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp),
        )
        if (error) {
            Text(
                text = stringResource(Res.string.guided_context_invalid),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    }
}

@Composable
fun GuidedDeadEndContent(
    appId: String?,
    onRetry: (() -> Unit)?,
    onDescribeManually: () -> Unit,
    onAdvanced: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GkIcon(
            imageVector = GkIcons.WarningAmber,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.guided_dead_end),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        if (onRetry != null) {
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.guided_try_again))
            }
        }
        OutlinedButton(
            onClick = onDescribeManually,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            enabled = appId != null,
        ) {
            Text(stringResource(Res.string.guided_describe_manually))
        }
        TextButton(onClick = onAdvanced, modifier = Modifier.padding(top = 4.dp)) {
            Text(stringResource(Res.string.guided_open_advanced))
        }
    }
}
