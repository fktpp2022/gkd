package li.gkd.app.ui.authoring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import li.gkd.app.authoring.GuidedExistingRuleHit
import li.gkd.app.authoring.GuidedRuleCandidate
import li.gkd.app.authoring.GuidedRuleReadResult
import li.gkd.app.authoring.GuidedRuleReader
import li.gkd.app.authoring.GuidedSaveNotice
import li.gkd.app.resources.*
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import org.jetbrains.compose.resources.stringResource

@Composable
fun GuidedNoticeBanner(notice: GuidedSaveNotice.Disabled) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Text(
            text = stringResource(Res.string.guided_disabled_notice, notice.name),
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun GuidedScanErrorBanner(onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.guided_scan_error),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) {
                Text(stringResource(Res.string.guided_retry))
            }
        }
    }
}

@Composable
fun GuidedSaveErrorBanner(onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.guided_save_error),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) {
                Text(stringResource(Res.string.guided_retry))
            }
        }
    }
}

@Composable
fun GuidedExistingBanner(
    hits: List<GuidedExistingRuleHit>,
    expanded: Boolean,
    onToggleMore: () -> Unit,
    onEdit: (GuidedExistingRuleHit) -> Unit,
    onRaw: () -> Unit,
    onDismiss: () -> Unit,
) {
    val first = hits.first()
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GkIcon(
                    imageVector = GkIcons.Info,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        Res.string.guided_existing_rule,
                        first.candidate.groupName.orEmpty(),
                    ),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                GkIconButton(
                    imageVector = GkIcons.Close,
                    contentDescription = stringResource(Res.string.guided_existing_dismiss),
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp),
                )
            }
            if (hits.size > 1) {
                TextButton(onClick = onToggleMore) {
                    Text(
                        text = if (expanded) {
                            stringResource(Res.string.action_collapse)
                        } else {
                            stringResource(Res.string.guided_existing_more, (hits.size - 1).toString())
                        },
                    )
                }
            }
            if (expanded) {
                hits.forEach { hit ->
                    GuidedExistingRow(hit = hit, onEdit = { onEdit(hit) }, onRaw = onRaw)
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                }
            } else {
                GuidedExistingRow(hit = first, onEdit = { onEdit(first) }, onRaw = onRaw)
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(Res.string.guided_existing_create_anyway))
            }
        }
    }
}

@Composable
fun GuidedExistingRow(
    hit: GuidedExistingRuleHit,
    onEdit: () -> Unit,
    onRaw: () -> Unit,
) {
    val candidate = hit.candidate
    val representable = candidate is GuidedRuleCandidate.App &&
            candidate.inAuthoringGroup &&
            GuidedRuleReader.read(candidate.rule) is GuidedRuleReadResult.Success
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = candidate.ruleName?.takeIf { it.isNotBlank() }
                ?: candidate.groupName.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!representable) {
            Text(
                text = stringResource(Res.string.guided_existing_advanced),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (representable) {
                TextButton(onClick = onEdit) {
                    Text(stringResource(Res.string.guided_existing_edit_rule))
                }
            }
            if (!representable || !hit.candidate.inAuthoringGroup) {
                TextButton(onClick = onRaw) {
                    Text(stringResource(Res.string.guided_existing_edit_json))
                }
            }
        }
    }
}
