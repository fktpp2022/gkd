package li.gkd.app.ui.authoring

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.min
import kotlinx.coroutines.launch
import li.gkd.app.authoring.GuidedCondition
import li.gkd.app.authoring.GuidedConditionProperty
import li.gkd.app.authoring.GuidedContext
import li.gkd.app.authoring.GuidedContextMode
import li.gkd.app.authoring.GuidedContextRelation
import li.gkd.app.authoring.GuidedDuplicateResult
import li.gkd.app.authoring.GuidedExistingRuleHit
import li.gkd.app.authoring.GuidedNodeLabel
import li.gkd.app.authoring.GuidedPreviewResult
import li.gkd.app.authoring.GuidedRuleAction
import li.gkd.app.authoring.GuidedRuleCandidate
import li.gkd.app.authoring.GuidedRuleReadResult
import li.gkd.app.authoring.GuidedRuleReader
import li.gkd.app.authoring.GuidedSaveNotice
import li.gkd.app.authoring.GuidedTextOperator
import li.gkd.app.authoring.isUsableNode
import li.gkd.app.authoring.property
import li.gkd.app.model.ComplexSnapshot
import li.gkd.app.model.NodeInfo
import li.gkd.app.platform.PlatformResult
import li.gkd.app.resources.*
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.component.GkAlertDialog
import li.gkd.app.ui.component.GkExpandableSection
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkModalBottomSheet
import li.gkd.app.ui.component.GkOutlinedTextField
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkScaffold
import li.gkd.app.ui.component.GkTextSwitch
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.navigation.GuidedAuthoringRoute
import li.gkd.app.ui.navigation.SubsAppListRoute
import li.gkd.app.ui.navigation.UpsertRuleGroupRoute
import li.gkd.app.ui.platform.GkBackHandler
import li.gkd.app.ui.platform.GkFullscreenDialog
import li.gkd.app.ui.platform.UiHost
import li.gkd.app.ui.platform.captureAndStartGuidedRecording
import li.gkd.app.ui.platform.finishGuidedAuthoring
import li.gkd.app.ui.platform.guidedRecordingSupported
import li.gkd.app.ui.platform.recaptureGuidedAuthoring
import li.gkd.app.ui.platform.startGuidedRecording
import li.gkd.app.ui.text.formatTimeAgo
import li.gkd.app.util.ToastUtils
import li.gkd.db.SubscriptionIds
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun GuidedAuthoringPage(
    host: UiHost,
    route: GuidedAuthoringRoute,
) {
    val mainVm = MainViewModel.requireCurrent()
    val vm = viewModel { GuidedAuthoringViewModel(route.startManualAppId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val broad by vm.broad.collectAsStateWithLifecycle()
    val duplicate by vm.duplicate.collectAsStateWithLifecycle()
    val discardPrompt by vm.discardPrompt.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val finished by vm.finished.collectAsStateWithLifecycle()
    val rawHandoff by vm.rawHandoff.collectAsStateWithLifecycle()

    var pending by remember { mutableStateOf<NodeInfo?>(null) }
    var showNodeList by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }
    var showAddCondition by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var showExistingMore by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val recordingSupported = guidedRecordingSupported()
    val canRecord = state.phase == GuidedAuthoringPhase.Picking &&
            !vm.fromSnapshot &&
            vm.hasCapture &&
            !state.recordSpent &&
            state.recordingFailure == null &&
            recordingSupported

    val startRecording: () -> Unit = {
        val context = vm.recordingContext()
        if (context != null) {
            pending = null
            if (host.startGuidedRecording(context) is PlatformResult.Success) {
                vm.prepareRecording()
            }
        }
    }
    val captureAndRecord: () -> Unit = {
        val appId = state.appId
        if (appId != null) {
            pending = null
            vm.prepareRecording()
            scope.launch {
                vm.adoptRecapturedCapture(host.captureAndStartGuidedRecording(appId, state.appName))
            }
        }
    }
    val retryCapture: () -> Unit = {
        val appId = state.appId
        if (appId != null) {
            scope.launch {
                vm.adoptRetryCapture(host.recaptureGuidedAuthoring(appId))
            }
        }
    }

    val close: () -> Unit = {
        if (route.fromOverlay) host.finishGuidedAuthoring() else mainVm.navigator.pop()
    }
    val editAsJson: (GuidedRuleCandidate?) -> Unit = vm::requestRawHandoff

    LaunchedEffect(state.phase) {
        if (state.phase != GuidedAuthoringPhase.Picking) {
            pending = null
            showNodeList = false
        }
    }
    LaunchedEffect(state.overlap) {
        if (state.overlap != null && pending == null) pending = state.overlap?.firstOrNull()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.clearRawHandoffIntent()
    }
    LaunchedEffect(saved) {
        if (saved) {
            vm.consumeSaved()
            ToastUtils.show(Res.string.guided_saved.getSync())
            close()
        }
    }
    LaunchedEffect(finished) {
        if (finished) {
            vm.consumeFinished()
            close()
        }
    }
    LaunchedEffect(rawHandoff) {
        val target = rawHandoff ?: return@LaunchedEffect
        vm.consumeRawHandoff()
        val appId = state.appId
        if (!target.global && appId == null) return@LaunchedEffect
        mainVm.navigator.navigate(
            UpsertRuleGroupRoute(
                subsId = SubscriptionIds.LOCAL,
                groupKey = target.groupKey,
                appId = if (target.global) null else appId,
                initialText = if (target.groupKey == null) vm.rawDraftText() else null,
                guided = true,
            ),
        )
    }

    val handleBack: () -> Unit = {
        when {
            state.contextMode != null -> vm.cancelContext()
            state.phase == GuidedAuthoringPhase.Picking && pending != null -> pending = null
            else -> vm.requestBack()
        }
    }
    GkBackHandler { handleBack() }

    val editingName = state.editing?.ruleName?.takeIf { it.isNotBlank() }
        ?: state.appName
    val snapshotSource = if (vm.fromSnapshot) {
        stringResource(Res.string.guided_snapshot_preview, formatTimeAgo(vm.capturedAt))
    } else {
        null
    }
    val title = when {
        state.phase == GuidedAuthoringPhase.DeadEnd ->
            stringResource(Res.string.guided_create_rule)

        state.editing != null ->
            stringResource(Res.string.guided_edit_title, editingName ?: state.appId.orEmpty())

        state.phase == GuidedAuthoringPhase.Editing ->
            stringResource(
                Res.string.guided_conditions_title,
                state.appName ?: state.appId.orEmpty(),
            )

        else -> stringResource(Res.string.guided_create_rule)
    }

    GkScaffold(
        topBar = {
            GkTopAppBar(
                navigationIcon = {
                    GkIconButton(
                        imageVector = GkIcons.ArrowBack,
                        onClick = handleBack,
                    )
                },
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    if (state.phase == GuidedAuthoringPhase.Editing) {
                        GuidedOverflowAction(onEditJson = { editAsJson(null) })
                    }
                    if (canRecord) {
                        GkIconButton(
                            imageVector = GkIcons.Record,
                            onClick = startRecording,
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (
                state.phase == GuidedAuthoringPhase.Editing &&
                state.contextMode == null
            ) {
                GuidedSaveBar(
                    saveAnyway = (state.preview as? GuidedPreviewState.Ready)
                        ?.result?.matches?.isEmpty() == true,
                    saving = state.saving,
                    enabled = vm.canSave(),
                    onSave = vm::requestSave,
                )
            }
        },
    ) { contentPadding ->
        Box(Modifier.fillMaxSize().padding(contentPadding)) {
            when (state.phase) {
                GuidedAuthoringPhase.DeadEnd -> GuidedDeadEndContent(
                    appId = state.appId,
                    onRetry = if (state.canRetryCapture(liveCapture = !vm.fromSnapshot)) {
                        retryCapture
                    } else {
                        null
                    },
                    onDescribeManually = vm::startManual,
                    onAdvanced = {
                        if (state.appId != null) {
                            mainVm.navigator.navigate(
                                UpsertRuleGroupRoute(
                                    subsId = SubscriptionIds.LOCAL,
                                    appId = state.appId,
                                    guided = true,
                                ),
                            )
                        } else {
                            mainVm.navigator.navigate(SubsAppListRoute(SubscriptionIds.LOCAL))
                        }
                    },
                )

                GuidedAuthoringPhase.Indexing,
                GuidedAuthoringPhase.Picking -> {
                    val snapshot = vm.snapshot
                    if (snapshot == null) {
                        GuidedDeadEndContent(
                            appId = state.appId,
                            onRetry = null,
                            onDescribeManually = vm::startManual,
                            onAdvanced = {
                                if (state.appId != null) {
                                    mainVm.navigator.navigate(
                                        UpsertRuleGroupRoute(
                                            subsId = SubscriptionIds.LOCAL,
                                            appId = state.appId,
                                            guided = true,
                                        ),
                                    )
                                } else {
                                    mainVm.navigator.navigate(SubsAppListRoute(SubscriptionIds.LOCAL))
                                }
                            },
                        )
                    } else {
                        GuidedPickingContent(
                            snapshot = snapshot,
                            screenshot = vm.screenshot,
                            sourceLabel = snapshotSource,
                            indexing = !state.canPick,
                            pending = pending,
                            pickLabel = pending?.let(vm::resolvePick),
                            failure = state.recordingFailure,
                            onCandidates = { candidates ->
                                if (candidates.size > 1) {
                                    pending = candidates.first()
                                    vm.showOverlap(candidates)
                                } else {
                                    pending = candidates.firstOrNull()
                                }
                            },
                            onUse = {
                                pending?.let(vm::select)
                            },
                            onChooseAgain = { pending = null },
                            onChooseFromList = { showNodeList = true },
                            onPickItInstead = vm::dismissRecordingFailure,
                            onRecordAgain = startRecording,
                            onCaptureAndRecord = captureAndRecord,
                            onCancelRecording = vm::closeSession,
                        )
                    }
                }

                GuidedAuthoringPhase.Editing -> {
                    val contextMode = state.contextMode
                    if (contextMode != null) {
                        val snapshot = vm.snapshot
                        if (snapshot != null) {
                            GuidedContextContent(
                                snapshot = snapshot,
                                screenshot = vm.screenshot,
                                preview = state.preview,
                                mode = contextMode,
                                error = state.contextError,
                                onMode = vm::setContextMode,
                                onCandidates = { candidates ->
                                    candidates.firstOrNull()?.let(vm::pickContextAnchor)
                                },
                                onCancel = vm::cancelContext,
                            )
                        }
                    } else {
                        GuidedEditingContent(
                            state = state,
                            screenshot = vm.screenshot,
                            snapshot = vm.snapshot,
                            sourceLabel = snapshotSource,
                            suggestions = vm::labelSuggestions,
                            showOptions = showOptions,
                            onToggleOptions = { showOptions = !showOptions },
                            showExistingMore = showExistingMore,
                            onToggleExistingMore = { showExistingMore = !showExistingMore },
                            onEditExisting = vm::editExisting,
                            onDismissExisting = vm::dismissExisting,
                            onRaw = editAsJson,
                            onPreview = { showPreview = true },
                            onAction = vm::setAction,
                            onLimit = vm::setLimitToAppOpening,
                            onName = vm::setName,
                            onTextOperator = vm::setTextOperator,
                            onConditionValue = { property, value ->
                                vm.updateCondition(property) { condition ->
                                    when (condition) {
                                        is GuidedCondition.Text -> condition.copy(value = value)
                                        is GuidedCondition.Description -> condition.copy(value = value)
                                        is GuidedCondition.ViewId -> condition.copy(value = value)
                                        is GuidedCondition.FullId -> condition.copy(value = value)
                                        is GuidedCondition.TypeName -> condition.copy(value = value)
                                        GuidedCondition.Clickable,
                                        GuidedCondition.VisibleToUser -> condition
                                    }
                                }
                            },
                            onRemoveCondition = vm::removeCondition,
                            onAddCondition = { showAddCondition = true },
                            onStartContext = vm::startContext,
                            onRemoveContext = vm::removeContext,
                            onRetry = vm::retrySave,
                        )
                    }
                }
            }
        }
    }

    if (showNodeList) {
        val snapshot = vm.snapshot
        if (snapshot != null) {
            GuidedNodeListSheet(
                snapshot = snapshot,
                onDismiss = { showNodeList = false },
                onSelect = { node ->
                    pending = node
                    showNodeList = false
                },
            )
        }
    }

    state.overlap?.let { candidates ->
        GuidedOverlapSheet(
            candidates = candidates,
            selected = pending,
            pickLabel = pending?.let(vm::resolvePick),
            onSelect = { pending = it },
            onUse = {
                pending?.let(vm::select)
                vm.dismissOverlap()
            },
            onDismiss = {
                pending = null
                vm.dismissOverlap()
            },
        )
    }

    if (showPreview) {
        val snapshot = vm.snapshot
        val preview = state.preview
        if (snapshot != null) {
            GuidedPreviewDialog(
                snapshot = snapshot,
                screenshot = vm.screenshot,
                preview = preview,
                onDismiss = { showPreview = false },
            )
        }
    }

    if (showAddCondition && state.phase == GuidedAuthoringPhase.Editing) {
        GuidedAddConditionSheet(
            used = state.draft.usedProperties,
            onDismiss = { showAddCondition = false },
            onAdd = { condition ->
                vm.addCondition(condition)
                showAddCondition = false
            },
        )
    }

    broad?.let { broadState ->
        GuidedBroadSheet(
            plan = broadState.plan,
            pickedId = state.pick?.actable?.id,
            onNarrow = {
                vm.narrowBroad()
                showOptions = false
            },
            onKeep = vm::confirmBroad,
            onDismiss = vm::cancelBroad,
        )
        if (broadState.confirming) {
            GkAlertDialog(
                onDismissRequest = vm::cancelBroadConfirm,
                title = {
                    Text(
                        pluralStringResource(
                            Res.plurals.guided_match_count,
                            broadState.plan.matchCount,
                            broadState.plan.matchCount.toString(),
                        ),
                    )
                },
                text = {
                    Text(
                        stringResource(
                            Res.string.guided_broad_confirm,
                            (broadState.plan.matchCount - 1).coerceAtLeast(0).toString(),
                        ),
                    )
                },
                confirmButton = {
                    TextButton(onClick = vm::keepBroad) {
                        Text(stringResource(Res.string.guided_broad_confirm_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = vm::cancelBroadConfirm) {
                        Text(stringResource(Res.string.action_cancel))
                    }
                },
            )
        }
    }

    duplicate?.let { duplicateState ->
        GuidedDuplicateSheet(
            state = duplicateState,
            onReplace = vm::replaceDuplicate,
            onKeepBoth = vm::keepBothDuplicate,
            onEditJson = vm::editDuplicateInJson,
            onDismiss = vm::cancelDuplicate,
        )
    }

    if (discardPrompt) {
        GkAlertDialog(
            onDismissRequest = vm::dismissDiscard,
            title = {
                Text(stringResource(Res.string.guided_discard_title))
            },
            confirmButton = {
                TextButton(onClick = vm::confirmDiscard) {
                    Text(stringResource(Res.string.guided_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissDiscard) {
                    Text(stringResource(Res.string.guided_keep_editing))
                }
            },
        )
    }
}

@Composable
private fun GuidedOverflowAction(onEditJson: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        GkIconButton(
            imageVector = GkIcons.MoreVert,
            contentDescription = stringResource(Res.string.more_actions),
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.guided_edit_as_json)) },
                onClick = {
                    expanded = false
                    onEditJson()
                },
            )
        }
    }
}

@Composable
private fun GuidedSaveBar(
    saveAnyway: Boolean,
    saving: Boolean,
    enabled: Boolean,
    onSave: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (saving) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f),
                enabled = enabled && !saving,
            ) {
                Text(
                    text = stringResource(
                        if (saveAnyway) Res.string.guided_save_anyway else Res.string.guided_save,
                    ),
                )
            }
        }
    }
}

@Composable
private fun GuidedPickingContent(
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
private fun GuidedContextContent(
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
private fun GuidedEditingContent(
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

@Composable
private fun GuidedNoticeBanner(notice: GuidedSaveNotice.Disabled) {
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
private fun GuidedSaveErrorBanner(onRetry: () -> Unit) {
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
private fun GuidedExistingBanner(
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
private fun GuidedExistingRow(
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

@Composable
private fun GuidedDeadEndContent(
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

@Composable
private fun GuidedNodeListSheet(
    snapshot: ComplexSnapshot,
    onDismiss: () -> Unit,
    onSelect: (NodeInfo) -> Unit,
) {
    val nodes = remember(snapshot) {
        snapshot.nodes.filter { it.isUsableNode() && GuidedNodeLabel.of(it) != null }
    }
    GkModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Text(
            text = stringResource(Res.string.guided_node_list_title),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
            items(nodes, key = { it.id }) { node ->
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
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun GuidedOverlapSheet(
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
private fun GuidedPreviewDialog(
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
private fun GuidedBroadSheet(
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
private fun GuidedDuplicateSheet(
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
private fun GuidedAddConditionSheet(
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

@Composable
private fun GuidedRuleCanvas(
    snapshot: ComplexSnapshot,
    screenshot: ByteArray?,
    matches: List<NodeInfo>,
    actableId: Int?,
    selectedId: Int?,
    description: String,
    modifier: Modifier = Modifier,
    onCandidates: ((List<NodeInfo>) -> Unit)? = null,
) {
    val image = remember(screenshot) {
        screenshot?.let { bytes ->
            runCatching { bytes.decodeToImageBitmap() }.getOrNull()
        }
    }
    val outlineNodes = remember(snapshot) {
        snapshot.nodes.filter {
            it.isUsableNode() && (it.attr.clickable || GuidedNodeLabel.of(it) != null)
        }
    }
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val background = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val matchColor = MaterialTheme.colorScheme.primary
    val actableColor = MaterialTheme.colorScheme.tertiary
    val selectColor = MaterialTheme.colorScheme.secondary
    val contrast = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)

    Canvas(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(background)
            .onSizeChanged { canvasSize = it }
            .semantics { contentDescription = description }
            .pointerInput(snapshot, onCandidates != null) {
                if (onCandidates == null) return@pointerInput
                detectTapGestures { position ->
                    val size = Size(
                        canvasSize.width.toFloat(),
                        canvasSize.height.toFloat(),
                    )
                    val geometry = canvasGeometry(size, snapshot, zoom, pan)
                    val point = (position - geometry.translate) / geometry.scale
                    if (
                        point.x < 0f || point.y < 0f ||
                        point.x > snapshot.screenWidth || point.y > snapshot.screenHeight
                    ) {
                        return@detectTapGestures
                    }
                    val candidates = candidatesAt(snapshot.nodes, point)
                    if (candidates.isNotEmpty()) onCandidates(candidates)
                }
            }
            .pointerInput(snapshot) {
                detectTransformGestures { centroid, panChange, zoomChange, _ ->
                    val size = Size(
                        canvasSize.width.toFloat(),
                        canvasSize.height.toFloat(),
                    )
                    if (size.width <= 0f || size.height <= 0f) return@detectTransformGestures
                    val newZoom = (zoom * zoomChange).coerceIn(1f, 5f)
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val ratio = newZoom / zoom
                    val nextPan =
                        centroid - center - (centroid - center - pan) * ratio + panChange
                    zoom = newZoom
                    pan = if (newZoom <= 1f) Offset.Zero else nextPan
                }
            },
    ) {
        val size = Size(this.size.width.toFloat(), this.size.height.toFloat())
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val geometry = canvasGeometry(size, snapshot, zoom, pan)
        val scale = geometry.scale
        withTransform({
            translate(geometry.translate.x, geometry.translate.y)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            if (image != null) {
                drawImage(
                    image = image,
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(snapshot.screenWidth, snapshot.screenHeight),
                    filterQuality = FilterQuality.Medium,
                )
            } else {
                drawRect(background)
                val step = 64f
                var x = 0f
                while (x <= snapshot.screenWidth) {
                    drawLine(
                        color = grid,
                        start = Offset(x, 0f),
                        end = Offset(x, snapshot.screenHeight.toFloat()),
                        strokeWidth = 1.dp.toPx() / scale,
                    )
                    x += step
                }
                var y = 0f
                while (y <= snapshot.screenHeight) {
                    drawLine(
                        color = grid,
                        start = Offset(0f, y),
                        end = Offset(snapshot.screenWidth.toFloat(), y),
                        strokeWidth = 1.dp.toPx() / scale,
                    )
                    y += step
                }
                outlineNodes.forEach { node ->
                    drawRect(
                        color = outline,
                        topLeft = node.rect().topLeft,
                        size = node.rect().size,
                        style = Stroke(width = 1.dp.toPx() / scale),
                    )
                }
            }
            val matchStroke = 3.dp.toPx() / scale
            matches.forEach { node ->
                val rect = node.rect()
                drawRect(
                    color = contrast,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    style = Stroke(width = matchStroke + 2.dp.toPx() / scale),
                )
                drawRect(
                    color = matchColor,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    style = Stroke(width = matchStroke),
                )
            }
            if (selectedId != null) {
                snapshot.nodes.firstOrNull { it.id == selectedId }?.let { node ->
                    drawRect(
                        color = selectColor,
                        topLeft = node.rect().topLeft,
                        size = node.rect().size,
                        style = Stroke(
                            width = 2.dp.toPx() / scale,
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(10.dp.toPx() / scale, 6.dp.toPx() / scale),
                            ),
                        ),
                    )
                }
            }
            if (actableId != null) {
                snapshot.nodes.firstOrNull { it.id == actableId }?.let { node ->
                    val rect = node.rect()
                    drawRect(
                        color = contrast,
                        topLeft = rect.topLeft,
                        size = rect.size,
                        style = Stroke(width = 6.dp.toPx() / scale),
                    )
                    drawRect(
                        color = actableColor,
                        topLeft = rect.topLeft,
                        size = rect.size,
                        style = Stroke(width = 3.dp.toPx() / scale),
                    )
                    val radius = 4.dp.toPx() / scale
                    listOf(
                        rect.topLeft,
                        Offset(rect.right, rect.top),
                        Offset(rect.left, rect.bottom),
                        rect.bottomRight,
                    ).forEach { corner ->
                        drawCircle(color = actableColor, radius = radius, center = corner)
                    }
                }
            }
        }
    }
}

private data class CanvasGeometry(val scale: Float, val translate: Offset)

private fun canvasGeometry(
    size: Size,
    snapshot: ComplexSnapshot,
    zoom: Float,
    pan: Offset,
): CanvasGeometry {
    if (snapshot.screenWidth <= 0 || snapshot.screenHeight <= 0) {
        return CanvasGeometry(1f, Offset.Zero)
    }
    val baseScale = min(
        size.width / snapshot.screenWidth,
        size.height / snapshot.screenHeight,
    )
    val baseOffset = Offset(
        (size.width - snapshot.screenWidth * baseScale) / 2f,
        (size.height - snapshot.screenHeight * baseScale) / 2f,
    )
    val scale = baseScale * zoom
    val center = Offset(size.width / 2f, size.height / 2f)
    val translate = baseOffset * zoom + center * (1f - zoom) + pan
    return CanvasGeometry(scale, translate)
}

private fun candidatesAt(nodes: List<NodeInfo>, point: Offset): List<NodeInfo> {
    val hits = nodes.filter { node ->
        val attr = node.attr
        node.isUsableNode() &&
                point.x >= attr.left &&
                point.x <= attr.right &&
                point.y >= attr.top &&
                point.y <= attr.bottom
    }
    if (hits.isEmpty()) return emptyList()
    val meaningful = hits.filter { it.attr.clickable || GuidedNodeLabel.of(it) != null }
    val base = meaningful.ifEmpty { hits }
    return base.filter { candidate ->
        base.none { other ->
            other.id != candidate.id &&
                    other.area() < candidate.area() &&
                    other.contains(candidate)
        }
    }.sortedWith(compareBy<NodeInfo>({ it.area() }, { -it.attr.depth }))
}

private fun NodeInfo.area(): Long = attr.width.toLong() * attr.height.toLong()

private fun NodeInfo.contains(other: NodeInfo): Boolean {
    val a = attr
    val b = other.attr
    return a.left <= b.left && a.top <= b.top && a.right >= b.right && a.bottom >= b.bottom
}

private fun NodeInfo.rect(): Rect = Rect(
    left = attr.left.toFloat(),
    top = attr.top.toFloat(),
    right = attr.right.toFloat(),
    bottom = attr.bottom.toFloat(),
)

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
private fun guidedConditionPropertyLabel(property: GuidedConditionProperty): String =
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

@Composable
private fun guidedNodeLabel(node: NodeInfo): String {
    val label = displayLabel(node)
    return label ?: stringResource(Res.string.guided_node_unnamed)
}

private fun displayLabel(node: NodeInfo): String? =
    GuidedNodeLabel.of(node)?.let(::displayLabel)

private fun displayLabel(value: String): String? =
    value.replace(Regex("\\s+"), " ").trim().takeIf { it.isNotEmpty() }

@Composable
private fun guidedNodeMeta(node: NodeInfo): String {
    val type = node.attr.name?.substringAfterLast('.')?.takeIf { it.isNotBlank() }
        ?: stringResource(Res.string.guided_node_unnamed)
    return stringResource(
        Res.string.guided_node_meta,
        type,
        node.attr.width.toString(),
        node.attr.height.toString(),
    )
}
