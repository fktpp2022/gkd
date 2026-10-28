package li.gkd.app.ui.authoring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import li.gkd.app.authoring.GuidedCondition
import li.gkd.app.authoring.GuidedRuleCandidate
import li.gkd.app.authoring.preselectedOverlap
import li.gkd.app.model.NodeInfo
import li.gkd.app.platform.PlatformResult
import li.gkd.app.resources.*
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.component.GkAlertDialog
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkScaffold
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.navigation.GuidedAuthoringRoute
import li.gkd.app.ui.navigation.SubsAppListRoute
import li.gkd.app.ui.navigation.UpsertRuleGroupRoute
import li.gkd.app.ui.platform.GkBackHandler
import li.gkd.app.ui.platform.UiHost
import li.gkd.app.ui.platform.captureAndStartGuidedRecording
import li.gkd.app.ui.platform.finishGuidedAuthoring
import li.gkd.app.ui.platform.guidedRecordingSupported
import li.gkd.app.ui.platform.recaptureGuidedAuthoring
import li.gkd.app.ui.platform.startGuidedRecording
import li.gkd.app.ui.text.formatTimeAgo
import li.gkd.app.util.ToastUtils
import li.gkd.db.SubscriptionIds
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
            scope.launch {
                val result = host.captureAndStartGuidedRecording(appId, state.appName)
                if (result is PlatformResult.Success) vm.prepareRecording()
                vm.adoptRecapturedCapture(result)
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
        if (state.overlap != null && pending == null) {
            pending = preselectedOverlap(state.overlap.orEmpty())
        }
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
                                    pending = preselectedOverlap(candidates)
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
                            onRetryScan = vm::retryScan,
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
                onDescribeManually = {
                    showNodeList = false
                    vm.startManual()
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
