package li.gkd.app.ui.authoring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import li.gkd.app.authoring.GuidedCondition
import li.gkd.app.authoring.GuidedConditionProperty
import li.gkd.app.authoring.GuidedContext
import li.gkd.app.authoring.GuidedContextRelation
import li.gkd.app.authoring.GuidedExistingRuleHit
import li.gkd.app.authoring.GuidedRuleAction
import li.gkd.app.authoring.GuidedRuleCandidate
import li.gkd.app.authoring.GuidedSaveNotice
import li.gkd.app.authoring.GuidedTextOperator
import li.gkd.app.authoring.property
import li.gkd.app.model.ComplexSnapshot
import li.gkd.app.resources.*
import li.gkd.app.ui.component.GkExpandableSection
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkOutlinedTextField
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkTextSwitch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun GuidedEditingContent(
    state: GuidedAuthoringState,
    screenshot: ByteArray?,
    snapshot: ComplexSnapshot?,
    sourceLabel: String?,
    suggestions: (GuidedConditionProperty) -> List<String>,
    showOptions: Boolean,
    onToggleOptions: () -> Unit,
    showExistingMore: Boolean,
    onToggleExistingMore: () -> Unit,
    onEditExisting: (GuidedExistingRuleHit) -> Unit,
    onDismissExisting: () -> Unit,
    onRaw: (GuidedRuleCandidate?) -> Unit,
    onPreview: () -> Unit,
    onAction: (GuidedRuleAction) -> Unit,
    onLimit: (Boolean) -> Unit,
    onName: (String) -> Unit,
    onTextOperator: (GuidedConditionProperty, GuidedTextOperator) -> Unit,
    onConditionValue: (GuidedConditionProperty, String) -> Unit,
    onRemoveCondition: (GuidedConditionProperty) -> Unit,
    onAddCondition: () -> Unit,
    onStartContext: () -> Unit,
    onRemoveContext: () -> Unit,
    onRetry: () -> Unit,
    onRetryScan: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        if (sourceLabel != null) {
            Text(
                text = sourceLabel,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.notice is GuidedSaveNotice.Disabled) {
            GuidedNoticeBanner(state.notice)
        }
        val previewReady = state.preview as? GuidedPreviewState.Ready
        if (snapshot != null && !state.manual) {
            GuidedPreviewCard(
                snapshot = snapshot,
                screenshot = screenshot,
                preview = state.preview,
                zeroMatch = previewReady?.result?.matches?.isEmpty() == true,
                onPreview = onPreview,
            )
        } else {
            Text(
                text = stringResource(Res.string.guided_preview_unavailable),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.existingHits.isNotEmpty() && !state.existingDismissed) {
            GuidedExistingBanner(
                hits = state.existingHits,
                expanded = showExistingMore,
                onToggleMore = onToggleExistingMore,
                onEdit = onEditExisting,
                onRaw = { onRaw(state.existingHits.first().candidate) },
                onDismiss = onDismissExisting,
            )
        }

        if (state.scanFailed) {
            GuidedScanErrorBanner(onRetryScan)
        }

        if (state.saveError) {
            GuidedSaveErrorBanner(onRetry)
        }

        GuidedActionSection(action = state.draft.action, onAction = onAction)

        Text(
            text = stringResource(Res.string.guided_conditions_heading),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        state.draft.target.forEach { condition ->
            GuidedConditionRow(
                condition = condition,
                dynamicText = state.dynamicText &&
                        condition.property == GuidedConditionProperty.Text,
                suggestions = if (condition.valueOrNull().isNullOrBlank()) {
                    suggestions(condition.property)
                } else {
                    emptyList()
                },
                onOperator = { operator -> onTextOperator(condition.property, operator) },
                onValue = { value -> onConditionValue(condition.property, value) },
                onRemove = { onRemoveCondition(condition.property) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onAddCondition) {
                GkIcon(GkIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.guided_add_condition))
            }
            Spacer(Modifier.weight(1f))
            if (state.draft.context == null && !state.manual) {
                TextButton(onClick = { onStartContext() }) {
                    Text(stringResource(Res.string.guided_add_context))
                }
            }
        }
        state.draft.context?.let { context ->
            GuidedContextChip(
                context = context,
                onReplace = { onStartContext() },
                onRemove = onRemoveContext,
            )
        }

        GkExpandableSection(
            expanded = showOptions,
            onToggle = onToggleOptions,
            modifier = Modifier.padding(top = 8.dp),
            header = { indicator ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.guided_options),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.weight(1f))
                    indicator()
                }
            },
        ) {
            GkTextSwitch(
                title = stringResource(Res.string.guided_limits),
                subtitle = stringResource(Res.string.guided_limits_helper),
                checked = state.draft.limitToAppOpening,
                onCheckedChange = onLimit,
            )
            Text(
                text = stringResource(Res.string.guided_limits_recommendation),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                GkOutlinedTextField(
                    value = state.draft.nameOverride.orEmpty(),
                    onValueChange = onName,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.guided_rule_name)) },
                    placeholder = { Text(state.suggestedName) },
                    singleLine = true,
                )
            }
            val canonical = previewReady?.result?.selector
            if (canonical != null) {
                Text(
                    text = stringResource(Res.string.guided_advanced_selector),
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SelectionContainer {
                    Text(
                        text = canonical,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        GkPageBottomSpace()
    }
}

@Composable
private fun GuidedPreviewCard(
    snapshot: ComplexSnapshot,
    screenshot: ByteArray?,
    preview: GuidedPreviewState,
    zeroMatch: Boolean,
    onPreview: () -> Unit,
) {
    val ready = preview as? GuidedPreviewState.Ready
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp)
                .clip(MaterialTheme.shapes.medium)
                .clickable(
                    onClickLabel = stringResource(Res.string.guided_preview_full),
                    onClick = onPreview,
                ),
        ) {
            GuidedRuleCanvas(
                snapshot = snapshot,
                screenshot = screenshot,
                matches = ready?.result?.matches.orEmpty(),
                actableId = ready?.result?.actable?.id,
                selectedId = null,
                description = if (ready != null) {
                    pluralStringResource(
                        Res.plurals.guided_match_count,
                        ready.result.matches.size,
                        ready.result.matches.size.toString(),
                    )
                } else {
                    stringResource(Res.string.guided_canvas_none_description)
                },
                modifier = Modifier.fillMaxWidth().height(180.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (preview) {
                GuidedPreviewState.Loading -> {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.guided_match_count_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is GuidedPreviewState.Ready -> {
                    Text(
                        text = pluralStringResource(
                            Res.plurals.guided_match_count,
                            preview.result.matches.size,
                            preview.result.matches.size.toString(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                GuidedPreviewState.Defect,
                GuidedPreviewState.NoPreview -> {
                    Text(
                        text = stringResource(Res.string.guided_preview_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (zeroMatch) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Text(
                    text = stringResource(Res.string.guided_zero_match),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun GuidedActionSection(
    action: GuidedRuleAction,
    onAction: (GuidedRuleAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.guided_action_label),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(end = 8.dp),
        )
        FilterChip(
            selected = action == GuidedRuleAction.Click,
            onClick = { onAction(GuidedRuleAction.Click) },
            label = { Text(stringResource(Res.string.guided_action_tap)) },
        )
        FilterChip(
            selected = action == GuidedRuleAction.LongPress,
            onClick = { onAction(GuidedRuleAction.LongPress) },
            label = { Text(stringResource(Res.string.guided_action_long_press)) },
        )
    }
}

@Composable
private fun GuidedConditionRow(
    condition: GuidedCondition,
    dynamicText: Boolean,
    suggestions: List<String>,
    onOperator: (GuidedTextOperator) -> Unit,
    onValue: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val property = condition.property
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = guidedConditionPropertyLabel(property),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                GkIconButton(
                    imageVector = GkIcons.Close,
                    contentDescription = stringResource(
                        Res.string.guided_remove_condition,
                        guidedConditionPropertyLabel(property),
                    ),
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp),
                )
            }
            when (condition) {
                is GuidedCondition.Text -> {
                    GuidedTextOperatorChips(condition.operator, onOperator)
                    GuidedValueField(condition.value, onValue)
                }

                is GuidedCondition.Description -> {
                    GuidedTextOperatorChips(condition.operator, onOperator)
                    GuidedValueField(condition.value, onValue)
                }

                is GuidedCondition.ViewId -> GuidedValueField(condition.value, onValue)
                is GuidedCondition.FullId -> GuidedValueField(condition.value, onValue)
                is GuidedCondition.TypeName -> GuidedValueField(condition.value, onValue)
                GuidedCondition.Clickable,
                GuidedCondition.VisibleToUser -> Unit
            }
            if (dynamicText) {
                Text(
                    text = stringResource(Res.string.guided_dynamic_text_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (suggestions.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.guided_condition_suggestions),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    suggestions.forEach { value ->
                        AssistChip(
                            onClick = { onValue(value) },
                            label = {
                                Text(
                                    text = displayLabel(value).orEmpty(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuidedValueField(
    value: String,
    onValue: (String) -> Unit,
) {
    GkOutlinedTextField(
        value = value,
        onValueChange = onValue,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        placeholder = { Text(stringResource(Res.string.guided_condition_value_hint)) },
        singleLine = true,
    )
}

@Composable
private fun GuidedTextOperatorChips(
    operator: GuidedTextOperator,
    onOperator: (GuidedTextOperator) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        FilterChip(
            selected = operator == GuidedTextOperator.Is,
            onClick = { onOperator(GuidedTextOperator.Is) },
            label = { Text(stringResource(Res.string.guided_operator_is)) },
        )
        FilterChip(
            selected = operator == GuidedTextOperator.Contains,
            onClick = { onOperator(GuidedTextOperator.Contains) },
            label = { Text(stringResource(Res.string.guided_operator_contains)) },
        )
    }
}

@Composable
private fun GuidedContextChip(
    context: GuidedContext,
    onReplace: () -> Unit,
    onRemove: () -> Unit,
) {
    val anchor = context.anchor.firstOrNull()?.valueOrNull().orEmpty()
    val label = when (context.relation) {
        GuidedContextRelation.Inside ->
            stringResource(Res.string.guided_context_inside_chip, anchor)

        GuidedContextRelation.AnchorBeforeTarget,
        GuidedContextRelation.AnchorAfterTarget ->
            stringResource(Res.string.guided_context_next_to_chip, anchor)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AssistChip(
            onClick = onReplace,
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        )
        GkIconButton(
            imageVector = GkIcons.Close,
            contentDescription = stringResource(Res.string.action_delete),
            onClick = onRemove,
            modifier = Modifier.size(32.dp),
        )
    }
}

private fun GuidedCondition.valueOrNull(): String? = when (this) {
    is GuidedCondition.Text -> value
    is GuidedCondition.Description -> value
    is GuidedCondition.ViewId -> value
    is GuidedCondition.FullId -> value
    is GuidedCondition.TypeName -> value
    GuidedCondition.Clickable,
    GuidedCondition.VisibleToUser -> null
}

@Composable
fun guidedConditionPropertyLabel(property: GuidedConditionProperty): String =
    when (property) {
        GuidedConditionProperty.Text -> stringResource(Res.string.guided_condition_text)
        GuidedConditionProperty.Description ->
            stringResource(Res.string.guided_condition_description)

        GuidedConditionProperty.ViewId -> stringResource(Res.string.guided_condition_view_id)
        GuidedConditionProperty.FullId -> stringResource(Res.string.guided_condition_full_id)
        GuidedConditionProperty.TypeName -> stringResource(Res.string.guided_condition_type)
        GuidedConditionProperty.Clickable ->
            stringResource(Res.string.guided_condition_clickable)

        GuidedConditionProperty.VisibleToUser ->
            stringResource(Res.string.guided_condition_visible_to_user)
    }
