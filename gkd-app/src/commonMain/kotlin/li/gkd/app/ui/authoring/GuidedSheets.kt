package li.gkd.app.ui.authoring

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import li.gkd.app.authoring.GuidedCondition
import li.gkd.app.authoring.GuidedConditionProperty
import li.gkd.app.authoring.GuidedNodeListContent
import li.gkd.app.authoring.GuidedTextOperator
import li.gkd.app.authoring.guidedNodeListContent
import li.gkd.app.model.ComplexSnapshot
import li.gkd.app.model.NodeInfo
import li.gkd.app.resources.*
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkModalBottomSheet
import li.gkd.app.ui.platform.GkFullscreenDialog
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun GuidedNodeListSheet(
    snapshot: ComplexSnapshot,
    onDismiss: () -> Unit,
    onSelect: (NodeInfo) -> Unit,
    onDescribeManually: () -> Unit,
) {
    val content = remember(snapshot) { guidedNodeListContent(snapshot.nodes) }
    GkModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Text(
            text = stringResource(Res.string.guided_node_list_title),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        when (content) {
            is GuidedNodeListContent.Nodes -> LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
            ) {
                items(content.nodes, key = { it.id }) { node ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(node) }
                            .padding(
                                start = (16 + node.attr.depth.coerceIn(0, 6) * 12).dp,
                                end = 16.dp,
                                top = 12.dp,
                                bottom = 12.dp,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = guidedNodeLabel(node),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = guidedNodeMeta(node),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            GuidedNodeListContent.Manual -> Column(Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(Res.string.guided_node_list_empty),
                    modifier = Modifier.padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onDescribeManually,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Text(stringResource(Res.string.guided_describe_manually))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun GuidedOverlapSheet(
    candidates: List<NodeInfo>,
    selected: NodeInfo?,
    pickLabel: li.gkd.app.authoring.GuidedPick?,
    onSelect: (NodeInfo) -> Unit,
    onUse: () -> Unit,
    onDismiss: () -> Unit,
) {
    GkModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Text(
            text = stringResource(Res.string.guided_overlap_title),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            candidates.forEach { node ->
                val isSelected = node.id == selected?.id
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(node) }
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                            else Color.Transparent,
                        )
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = guidedNodeLabel(node),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = guidedNodeMeta(node),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (pickLabel?.climbed == true) {
            Text(
                text = stringResource(Res.string.guided_actable_climb),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
            Button(onClick = onUse, enabled = selected != null) {
                Text(stringResource(Res.string.guided_use_this))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun GuidedPreviewDialog(
    snapshot: ComplexSnapshot,
    screenshot: ByteArray?,
    preview: GuidedPreviewState,
    onDismiss: () -> Unit,
) {
    val ready = preview as? GuidedPreviewState.Ready
    GkFullscreenDialog(onDismiss = onDismiss) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            Box(Modifier.fillMaxSize()) {
                GuidedRuleCanvas(
                    snapshot = snapshot,
                    screenshot = screenshot,
                    matches = ready?.result?.matches.orEmpty(),
                    actableId = ready?.result?.actable?.id,
                    selectedId = null,
                    description = stringResource(Res.string.guided_preview_title),
                    modifier = Modifier.fillMaxSize(),
                )
                GkIconButton(
                    imageVector = GkIcons.Close,
                    contentDescription = stringResource(Res.string.action_close),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                )
            }
        }
    }
}

@Composable
fun GuidedBroadSheet(
    plan: li.gkd.app.authoring.GuidedSavePlan,
    pickedId: Int?,
    onNarrow: () -> Unit,
    onKeep: () -> Unit,
    onDismiss: () -> Unit,
) {
    GkModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Text(
            text = pluralStringResource(
                Res.plurals.guided_match_count,
                plan.matchCount,
                plan.matchCount.toString(),
            ),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(Res.string.guided_broad_body),
            modifier = Modifier.padding(horizontal = 24.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).padding(top = 8.dp),
        ) {
            items(plan.matches, key = { it.id }) { node ->
                val picked = node.id == pickedId
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = guidedNodeLabel(node),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (picked) {
                            androidx.compose.ui.text.font.FontWeight.SemiBold
                        } else {
                            null
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (picked) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(Res.string.guided_current_pick),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onNarrow, modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.guided_broad_narrow))
            }
            Button(onClick = onKeep, modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.guided_broad_keep))
            }
        }
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        ) {
            Text(stringResource(Res.string.action_cancel))
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun GuidedDuplicateSheet(
    state: GuidedDuplicateState,
    onReplace: () -> Unit,
    onKeepBoth: () -> Unit,
    onEditJson: () -> Unit,
    onDismiss: () -> Unit,
) {
    val candidate = state.candidate
    GkModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Text(
            text = stringResource(Res.string.guided_duplicate_title),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = when (state.kind) {
                GuidedDuplicateKind.Replaceable,
                GuidedDuplicateKind.AuthoringAdvanced ->
                    stringResource(
                        Res.string.guided_duplicate_same_group,
                        candidate.groupName.orEmpty(),
                    )

                GuidedDuplicateKind.Foreign ->
                    stringResource(Res.string.guided_duplicate_other_group)
            },
            modifier = Modifier.padding(horizontal = 24.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        val ruleName = candidate.ruleName?.takeIf { it.isNotBlank() }
        val selector = candidate.selectors.firstOrNull()
        if (ruleName != null || selector != null) {
            Text(
                text = ruleName ?: selector.orEmpty(),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (state.kind) {
                GuidedDuplicateKind.Replaceable -> {
                    Button(onClick = onReplace, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.guided_duplicate_replace))
                    }
                    OutlinedButton(onClick = onKeepBoth, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.guided_duplicate_keep_both))
                    }
                }

                GuidedDuplicateKind.AuthoringAdvanced -> {
                    Button(onClick = onKeepBoth, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.guided_duplicate_keep_both))
                    }
                    OutlinedButton(onClick = onEditJson, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.guided_existing_edit_json))
                    }
                }

                GuidedDuplicateKind.Foreign -> {
                    Button(onClick = onKeepBoth, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.guided_duplicate_keep_both))
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun GuidedAddConditionSheet(
    used: Set<GuidedConditionProperty>,
    onDismiss: () -> Unit,
    onAdd: (GuidedCondition) -> Unit,
) {
    var showMore by remember { mutableStateOf(false) }
    val visible = buildList {
        for (property in GuidedConditionProperty.entries) {
            if (property in used) continue
            if (property == GuidedConditionProperty.FullId && !showMore) continue
            add(property)
        }
    }
    GkModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Text(
            text = stringResource(Res.string.guided_add_condition_title),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
            items(visible, key = { it.key }) { property ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAdd(defaultCondition(property)) }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = guidedConditionPropertyLabel(property),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            if (!showMore && GuidedConditionProperty.FullId !in used) {
                item(key = "more") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showMore = true }
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(Res.string.guided_condition_more),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun defaultCondition(property: GuidedConditionProperty): GuidedCondition = when (property) {
    GuidedConditionProperty.Text -> GuidedCondition.Text(GuidedTextOperator.Is, "")
    GuidedConditionProperty.Description -> GuidedCondition.Description(GuidedTextOperator.Is, "")
    GuidedConditionProperty.ViewId -> GuidedCondition.ViewId("")
    GuidedConditionProperty.FullId -> GuidedCondition.FullId("")
    GuidedConditionProperty.TypeName -> GuidedCondition.TypeName("")
    GuidedConditionProperty.Clickable -> GuidedCondition.Clickable
    GuidedConditionProperty.VisibleToUser -> GuidedCondition.VisibleToUser
}
