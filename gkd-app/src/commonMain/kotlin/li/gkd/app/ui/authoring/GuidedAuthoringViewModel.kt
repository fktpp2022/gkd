package li.gkd.app.ui.authoring

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.app.launcherAppIdFlow
import li.gkd.app.authoring.GuidedCapture
import li.gkd.app.authoring.GuidedCaptureStore
import li.gkd.app.authoring.GuidedCondition
import li.gkd.app.authoring.GuidedConditionProperty
import li.gkd.app.authoring.GuidedContext
import li.gkd.app.authoring.GuidedContextBuildFailure
import li.gkd.app.authoring.GuidedContextBuildResult
import li.gkd.app.authoring.GuidedContextBuilder
import li.gkd.app.authoring.GuidedContextMode
import li.gkd.app.authoring.GuidedDuplicateCheck
import li.gkd.app.authoring.GuidedDuplicateResult
import li.gkd.app.authoring.GuidedExistingRuleDetector
import li.gkd.app.authoring.GuidedExistingRuleHit
import li.gkd.app.authoring.GuidedMatchScope
import li.gkd.app.authoring.GuidedNodeLabel
import li.gkd.app.authoring.GuidedPick
import li.gkd.app.authoring.GuidedPickResolver
import li.gkd.app.authoring.GuidedPrefillBuilder
import li.gkd.app.authoring.GuidedPrefillResult
import li.gkd.app.authoring.GuidedPreviewBuilder
import li.gkd.app.authoring.GuidedPreviewResult
import li.gkd.app.authoring.GuidedRecordingContext
import li.gkd.app.authoring.GuidedRecordingPhase
import li.gkd.app.authoring.GuidedRecordingResolution
import li.gkd.app.authoring.GuidedRecordingResolver
import li.gkd.app.authoring.GuidedRecordingSession
import li.gkd.app.authoring.GuidedRecordingSignal
import li.gkd.app.authoring.GuidedRuleAction
import li.gkd.app.authoring.GuidedRuleCandidate
import li.gkd.app.authoring.GuidedRuleData
import li.gkd.app.authoring.GuidedRuleDraft
import li.gkd.app.authoring.GuidedRuleEligibility
import li.gkd.app.authoring.GuidedRuleReadResult
import li.gkd.app.authoring.GuidedRuleReader
import li.gkd.app.authoring.GuidedRuleScope
import li.gkd.app.authoring.GuidedRuleScan
import li.gkd.app.authoring.GuidedRuleWriteFailure
import li.gkd.app.authoring.GuidedRuleWriteRequest
import li.gkd.app.authoring.GuidedRuleWriteResult
import li.gkd.app.authoring.GuidedRuleWriter
import li.gkd.app.authoring.GuidedSaveNotice
import li.gkd.app.authoring.GuidedSaveNotices
import li.gkd.app.authoring.GuidedSavePlan
import li.gkd.app.authoring.GuidedSavePlanResult
import li.gkd.app.authoring.GuidedSavePlanner
import li.gkd.app.authoring.GuidedSelectorCompileResult
import li.gkd.app.authoring.GuidedSelectorCompiler
import li.gkd.app.authoring.GuidedTapEvent
import li.gkd.app.authoring.GuidedTextOperator
import li.gkd.app.authoring.scanGuidedRules
import li.gkd.app.authoring.toSelectorDraft
import li.gkd.app.model.NodeInfo
import li.gkd.app.resources.*
import li.gkd.app.snapshot.SnapshotNodeAdapter
import li.gkd.app.state.Loadable
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.app.subscription.SubscriptionJson
import li.gkd.app.ui.state.BaseViewModel
import li.gkd.app.ui.subscription.RequiredSubscription
import li.gkd.db.SubscriptionConfigSnapshot
import li.gkd.db.SubscriptionConfigStore
import li.gkd.db.SubscriptionIds
import org.jetbrains.compose.resources.getString

enum class GuidedAuthoringPhase { Picking, Editing, DeadEnd }

enum class GuidedRecordingFailure { NoTap, NoNode, ScreenChanged }

sealed interface GuidedPreviewState {
    data object NoPreview : GuidedPreviewState
    data object Loading : GuidedPreviewState
    data class Ready(val result: GuidedPreviewResult.Ready) : GuidedPreviewState
    data object Defect : GuidedPreviewState
}

data class GuidedAuthoringState(
    val phase: GuidedAuthoringPhase,
    val appId: String?,
    val appName: String?,
    val draft: GuidedRuleDraft = GuidedRuleDraft(),
    val pick: GuidedPick? = null,
    val preview: GuidedPreviewState = GuidedPreviewState.NoPreview,
    val scan: GuidedRuleScan = GuidedRuleScan.Pending,
    val existingHits: List<GuidedExistingRuleHit> = emptyList(),
    val existingDismissed: Boolean = true,
    val editing: GuidedRuleCandidate.App? = null,
    val notice: GuidedSaveNotice = GuidedSaveNotice.None,
    val contextError: Boolean = false,
    val dynamicText: Boolean = false,
    val nameEdited: Boolean = false,
    val manual: Boolean = false,
    val saving: Boolean = false,
    val saveError: Boolean = false,
    val contextMode: GuidedContextMode? = null,
    val overlap: List<NodeInfo>? = null,
    val recordingFailure: GuidedRecordingFailure? = null,
    val recordSpent: Boolean = false,
) {
    val canSave: Boolean
        get() {
            if (saving || draft.target.isEmpty() || appId == null) return false
            if (preview is GuidedPreviewState.Defect) return false
            if (scan !is GuidedRuleScan.Ready) return false
            return draft.target.all { condition ->
                when (condition) {
                    is GuidedCondition.Text -> condition.value.isNotBlank()
                    is GuidedCondition.Description -> condition.value.isNotBlank()
                    is GuidedCondition.ViewId -> condition.value.isNotBlank()
                    is GuidedCondition.FullId -> condition.value.isNotBlank()
                    is GuidedCondition.TypeName -> condition.value.isNotBlank()
                    GuidedCondition.Clickable, GuidedCondition.VisibleToUser -> true
                }
            }
        }
}

enum class GuidedDuplicateKind { Replaceable, AuthoringAdvanced, Foreign }

data class GuidedDuplicateState(
    val candidate: GuidedRuleCandidate,
    val kind: GuidedDuplicateKind,
)

data class GuidedBroadState(
    val plan: GuidedSavePlan,
    val confirming: Boolean = false,
)

data class GuidedRawTarget(
    val groupKey: Int?,
    val global: Boolean,
)

class GuidedAuthoringViewModel(
    private val startManualAppId: String? = null,
) : BaseViewModel() {
    private var capture: GuidedCapture? = GuidedCaptureStore.current.value
    private var adapter: SnapshotNodeAdapter? =
        capture?.snapshot?.nodes?.takeIf { it.isNotEmpty() }?.let(::SnapshotNodeAdapter)
    private var nodes: List<NodeInfo> = capture?.snapshot?.nodes.orEmpty()
    private var usable = nodes.any { it.attr.visibleToUser && it.attr.width > 0 && it.attr.height > 0 }

    private var ruleScope: GuidedRuleScope? = null
    private var authoringGroupName: String = ""
    private var baseline: GuidedRuleDraft = GuidedRuleDraft()
    private var preEditDraft: GuidedRuleDraft? = null
    private var preEditNameEdited: Boolean = false
    private var previewJob: Job? = null
    private var detectionJob: Job? = null
    private var persistJob: Job? = null
    private var recordingWaitJob: Job? = null
    private var pendingRecordedAction: GuidedRuleAction? = null
    private var screenChangedWhileResolving = false

    private val initialPhase = when {
        startManualAppId != null -> GuidedAuthoringPhase.Editing
        capture == null || !usable -> GuidedAuthoringPhase.DeadEnd
        else -> GuidedAuthoringPhase.Picking
    }

    private val initialState = GuidedAuthoringState(
        phase = initialPhase,
        appId = startManualAppId ?: capture?.snapshot?.appId,
        appName = capture?.snapshot?.appInfo?.name ?: capture?.snapshot?.appId,
        manual = startManualAppId != null,
    )

    val state: StateFlow<GuidedAuthoringState>
        field = MutableStateFlow(initialState)

    val broad: StateFlow<GuidedBroadState?>
        field = MutableStateFlow(null)

    val duplicate: StateFlow<GuidedDuplicateState?>
        field = MutableStateFlow(null)

    val discardPrompt: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val saved: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val finished: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val rawHandoff: StateFlow<GuidedRawTarget?>
        field = MutableStateFlow(null)

    val hasCapture: Boolean get() = capture != null
    val hasPreview: Boolean get() = adapter != null
    val fromSnapshot: Boolean get() = capture?.fromSnapshot == true
    val capturedAt: Long get() = capture?.capturedAt ?: 0L
    val screenshot: ByteArray? get() = capture?.screenshot
    val snapshot: li.gkd.app.model.ComplexSnapshot? get() = capture?.snapshot

    fun resolvePick(node: NodeInfo): GuidedPick? =
        adapter?.let { GuidedPickResolver.resolve(node, it) }

    init {
        viewModelScope.launch {
            authoringGroupName = getString(Res.string.guided_authoring_group_name)
            loadRuleScope()
        }
        viewModelScope.launch {
            GuidedRecordingSession.signals.collect { signal ->
                when (signal) {
                    is GuidedRecordingSignal.Tap -> onRecordedTap(signal.event)
                    GuidedRecordingSignal.Stopped -> setRecordingFailure(GuidedRecordingFailure.NoTap)
                    GuidedRecordingSignal.ScreenChanged -> onScreenChanged()
                }
            }
        }
    }

    fun recordingContext(): GuidedRecordingContext? {
        val capture = capture ?: return null
        if (capture.fromSnapshot || !usable) return null
        if (state.value.phase != GuidedAuthoringPhase.Picking) return null
        return GuidedRecordingContext(
            appId = capture.snapshot.appId,
            appName = state.value.appName ?: capture.snapshot.appId,
            activityId = capture.snapshot.activityId,
        )
    }

    fun prepareRecording() {
        recordingWaitJob?.cancel()
        pendingRecordedAction = null
        screenChangedWhileResolving = false
        state.update { it.copy(recordingFailure = null, overlap = null) }
    }

    fun dismissRecordingFailure() {
        state.update { it.copy(recordingFailure = null) }
    }

    fun adoptRecapturedCapture() {
        val capture = GuidedCaptureStore.current.value ?: return
        val adapter = capture.snapshot.nodes
            .takeIf { it.isNotEmpty() }
            ?.let(::SnapshotNodeAdapter)
            ?: return
        this.capture = capture
        this.adapter = adapter
        nodes = capture.snapshot.nodes
        usable = nodes.any { it.attr.visibleToUser && it.attr.width > 0 && it.attr.height > 0 }
        state.update { it.copy(recordingFailure = null) }
    }

    fun recaptureFailed() {
        recordingWaitJob?.cancel()
        state.update {
            it.copy(
                phase = GuidedAuthoringPhase.DeadEnd,
                recordingFailure = null,
                overlap = null,
            )
        }
    }

    private fun onRecordedTap(event: GuidedTapEvent) {
        if (capture == null) return
        screenChangedWhileResolving = false
        when (val result = GuidedRecordingResolver.resolve(event, nodes)) {
            GuidedRecordingResolution.NotFound -> {
                recordingWaitJob?.cancel()
                recordingWaitJob = viewModelScope.launch {
                    delay(500)
                    val changed = screenChangedWhileResolving
                    screenChangedWhileResolving = false
                    GuidedRecordingSession.finish()
                    setRecordingFailure(
                        if (changed) GuidedRecordingFailure.ScreenChanged
                        else GuidedRecordingFailure.NoNode
                    )
                }
            }

            is GuidedRecordingResolution.Found -> {
                val action = if (event.longClick) {
                    GuidedRuleAction.LongPress
                } else {
                    GuidedRuleAction.Click
                }
                if (result.nodes.size > 1) {
                    pendingRecordedAction = action
                    GuidedRecordingSession.finish()
                    state.update {
                        it.copy(recordingFailure = null, overlap = result.nodes)
                    }
                } else {
                    GuidedRecordingSession.finish()
                    selectNode(result.nodes.first(), action, recorded = true)
                }
            }
        }
    }

    private fun onScreenChanged() {
        if (GuidedRecordingSession.phase == GuidedRecordingPhase.Resolving) {
            screenChangedWhileResolving = true
        } else {
            setRecordingFailure(GuidedRecordingFailure.ScreenChanged)
        }
    }

    private fun setRecordingFailure(failure: GuidedRecordingFailure) {
        pendingRecordedAction = null
        state.update {
            if (it.phase != GuidedAuthoringPhase.Picking) it
            else it.copy(recordingFailure = failure, overlap = null)
        }
    }

    private suspend fun loadRuleScope() {
        combine(
            SubscriptionRepository.snapshotFlow,
            SubscriptionConfigStore.observe(),
            AppInfoRepository.appInfoMapFlow,
            launcherAppIdFlow,
        ) { loaded, configs, apps, launcher ->
            RuleScopeInput(loaded, configs, apps, launcher)
        }.collect { input ->
            val loaded = input.loaded
            if (loaded !is Loadable.Ready) return@collect
            val appId = state.value.appId ?: return@collect
            val local = loaded.value.subscriptions[SubscriptionIds.LOCAL] ?: return@collect
            val scope = GuidedRuleScope(
                local = local,
                configs = input.configs,
                appId = appId,
                activityId = capture?.snapshot?.activityId,
                appInfo = input.apps[appId],
                launcherAppId = input.launcher,
                systemAppIds = input.apps.values.filter { it.isSystem }
                    .mapTo(mutableSetOf()) { it.id },
            )
            ruleScope = scope
            val scan = withContext(Dispatchers.Default) {
                scanGuidedRules { GuidedRuleEligibility.candidates(scope, authoringGroupName) }
            }
            val appName = state.value.appName?.takeIf { it != appId }
                ?: scope.appInfo?.name ?: appId
            state.update {
                it.copy(
                    appName = appName,
                    scan = scan,
                    notice = GuidedSaveNotices.resolve(scope, authoringGroupName),
                )
            }
            if (scan is GuidedRuleScan.Ready) {
                val pick = state.value.pick
                if (pick != null && !state.value.manual && state.value.editing == null) {
                    detectExisting(pick, scan.candidates)
                }
            }
        }
    }

    private data class RuleScopeInput(
        val loaded: Loadable<li.gkd.app.subscription.SubscriptionSnapshot>,
        val configs: SubscriptionConfigSnapshot,
        val apps: Map<String, li.gkd.app.model.AppInfo>,
        val launcher: String,
    )

    fun select(node: NodeInfo) {
        val action = pendingRecordedAction
        pendingRecordedAction = null
        selectNode(node, action ?: GuidedRuleAction.Click, recorded = action != null)
    }

    private fun selectNode(node: NodeInfo, action: GuidedRuleAction, recorded: Boolean) {
        val adapter = adapter ?: return
        val pick = GuidedPickResolver.resolve(node, adapter)
        val prefill = GuidedPrefillBuilder.build(pick.actable, adapter)
        val target = when (prefill) {
            is GuidedPrefillResult.Success -> prefill.prefill.target
            is GuidedPrefillResult.Failure -> emptyList()
        }
        val dynamicText = (prefill as? GuidedPrefillResult.Success)?.prefill?.dynamicText == true
        val draft = GuidedRuleDraft(target = target, action = action)
        baseline = draft
        preEditDraft = null
        state.update {
            it.copy(
                phase = GuidedAuthoringPhase.Editing,
                draft = draft,
                pick = pick,
                preview = GuidedPreviewState.NoPreview,
                existingHits = emptyList(),
                existingDismissed = true,
                editing = null,
                contextError = false,
                dynamicText = dynamicText,
                nameEdited = false,
                manual = false,
                saveError = false,
                contextMode = null,
                overlap = null,
                recordingFailure = null,
                recordSpent = it.recordSpent || recorded,
            )
        }
        refreshPreview()
        val scan = state.value.scan
        if (scan is GuidedRuleScan.Ready) {
            detectExisting(pick, scan.candidates)
        }
    }

    private fun detectExisting(pick: GuidedPick, candidates: List<GuidedRuleCandidate>) {
        val adapter = adapter ?: return
        detectionJob?.cancel()
        detectionJob = viewModelScope.launch(Dispatchers.Default) {
            val hits = GuidedExistingRuleDetector.detect(
                candidates, pick.picked, nodes, adapter,
            )
            state.update {
                if (it.pick != pick) it
                else it.copy(existingHits = hits, existingDismissed = hits.isEmpty())
            }
        }
    }

    fun refreshPreview() {
        val adapter = adapter
        val pick = state.value.pick
        previewJob?.cancel()
        if (adapter == null || pick == null) {
            state.update { it.copy(preview = GuidedPreviewState.NoPreview) }
            return
        }
        state.update { it.copy(preview = GuidedPreviewState.Loading) }
        val draft = state.value.draft
        previewJob = viewModelScope.launch(Dispatchers.Default) {
            val result = GuidedPreviewBuilder.build(draft.toSelectorDraft(), pick.actable, adapter)
            state.update {
                if (it.draft != draft || it.pick != pick) it
                else it.copy(
                    preview = when (result) {
                        GuidedPreviewResult.Incomplete -> GuidedPreviewState.NoPreview
                        GuidedPreviewResult.Defect -> GuidedPreviewState.Defect
                        is GuidedPreviewResult.Ready -> GuidedPreviewState.Ready(result)
                    },
                )
            }
        }
    }

    fun setAction(action: GuidedRuleAction) = updateDraft { it.copy(action = action) }

    fun setLimitToAppOpening(enabled: Boolean) =
        updateDraft { it.copy(limitToAppOpening = enabled) }

    fun setName(value: String) {
        state.update {
            it.copy(draft = it.draft.copy(nameOverride = value), nameEdited = true, saveError = false)
        }
    }

    fun addCondition(condition: GuidedCondition) = updateDraft { it.addCondition(condition) }

    fun removeCondition(property: GuidedConditionProperty) =
        updateDraft { it.removeCondition(property) }

    fun updateCondition(
        property: GuidedConditionProperty,
        transform: (GuidedCondition) -> GuidedCondition,
    ) = updateDraft { it.updateCondition(property, transform) }

    fun setTextOperator(property: GuidedConditionProperty, operator: GuidedTextOperator) =
        updateCondition(property) { condition ->
            when (condition) {
                is GuidedCondition.Text -> condition.copy(operator = operator)
                is GuidedCondition.Description -> condition.copy(operator = operator)
                else -> condition
            }
        }

    private fun updateDraft(transform: (GuidedRuleDraft) -> GuidedRuleDraft) {
        state.update { it.copy(draft = transform(it.draft), saveError = false) }
        refreshPreview()
    }

    fun showOverlap(candidates: List<NodeInfo>) {
        if (candidates.size <= 1) {
            candidates.firstOrNull()?.let(::select)
        } else {
            state.update { it.copy(overlap = candidates) }
        }
    }

    fun dismissOverlap() {
        pendingRecordedAction = null
        state.update { it.copy(overlap = null) }
    }

    fun startContext(mode: GuidedContextMode = GuidedContextMode.Inside) =
        state.update { it.copy(contextMode = mode, contextError = false) }

    fun setContextMode(mode: GuidedContextMode) =
        state.update { it.copy(contextMode = mode, contextError = false) }

    fun cancelContext() = state.update { it.copy(contextMode = null) }

    fun pickContextAnchor(anchor: NodeInfo) {
        val adapter = adapter ?: return
        val pick = state.value.pick ?: return
        val mode = state.value.contextMode ?: return
        when (val result = GuidedContextBuilder.build(mode, pick.actable, anchor, nodes, adapter)) {
            is GuidedContextBuildResult.Success -> {
                state.update { it.copy(contextMode = null, contextError = false) }
                updateDraft { it.copy(context = result.context) }
            }

            is GuidedContextBuildResult.Failure -> {
                state.update {
                    it.copy(contextError = result.reason == GuidedContextBuildFailure.NoStableLabel)
                }
            }
        }
    }

    fun removeContext() = updateDraft { it.copy(context = null) }

    fun dismissExisting() = state.update { it.copy(existingDismissed = true) }

    fun editExisting(hit: GuidedExistingRuleHit) {
        val candidate = hit.candidate as? GuidedRuleCandidate.App
            ?: return requestRawHandoff(hit.candidate)
        if (!candidate.inAuthoringGroup) return requestRawHandoff(candidate)
        val data = when (val result = GuidedRuleReader.read(candidate.rule)) {
            is GuidedRuleReadResult.Success -> result.data
            is GuidedRuleReadResult.Unsupported -> return requestRawHandoff(candidate)
        }
        preEditDraft = state.value.draft
        preEditNameEdited = state.value.nameEdited
        val draft = data.toDraft()
        baseline = draft
        state.update {
            it.copy(
                phase = GuidedAuthoringPhase.Editing,
                draft = draft,
                nameEdited = true,
                editing = candidate,
                existingDismissed = true,
                saveError = false,
                contextMode = null,
                overlap = null,
            )
        }
        refreshPreview()
    }

    private fun GuidedRuleData.toDraft() = GuidedRuleDraft(
        target = draft.target,
        context = draft.context,
        action = action,
        nameOverride = name,
        limitToAppOpening = limitToAppOpening,
    )

    private fun exitEdit() {
        val draft = preEditDraft ?: return
        preEditDraft = null
        baseline = draft
        state.update {
            it.copy(
                draft = draft,
                nameEdited = preEditNameEdited,
                editing = null,
                existingDismissed = true,
                saveError = false,
            )
        }
        refreshPreview()
    }

    fun requestRawHandoff(preferred: GuidedRuleCandidate? = null) {
        val appId = state.value.appId ?: return
        val local = ruleScope?.local
        val authoringGroup = local?.apps?.find { it.id == appId }
            ?.groups?.find { it.name == authoringGroupName }
        val target = when (preferred) {
            is GuidedRuleCandidate.App -> GuidedRawTarget(preferred.groupKey, global = false)
            is GuidedRuleCandidate.Global -> GuidedRawTarget(preferred.groupKey, global = true)
            null -> GuidedRawTarget(authoringGroup?.key, global = false)
        }
        rawHandoff.value = target
    }

    fun consumeRawHandoff() {
        rawHandoff.value = null
    }

    fun startManual() {
        val appId = state.value.appId ?: return
        val draft = GuidedRuleDraft()
        baseline = draft
        preEditDraft = null
        state.update {
            it.copy(
                phase = GuidedAuthoringPhase.Editing,
                appId = appId,
                draft = draft,
                pick = null,
                preview = GuidedPreviewState.NoPreview,
                existingHits = emptyList(),
                existingDismissed = true,
                editing = null,
                contextError = false,
                dynamicText = false,
                nameEdited = false,
                manual = true,
                saveError = false,
                contextMode = null,
                overlap = null,
            )
        }
    }

    fun requestSave() {
        val current = state.value
        if (!current.canSave || persistJob?.isActive == true) return
        val scan = current.scan as? GuidedRuleScan.Ready ?: return
        state.update { it.copy(saving = true, saveError = false) }
        val adapter = adapter
        if (current.manual || adapter == null || current.pick == null) {
            saveManual(scan.candidates)
            return
        }
        val draft = current.draft
        val actable = current.pick.actable
        val candidates = duplicateCandidates(scan.candidates)
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                GuidedSavePlanner.plan(draft.toSelectorDraft(), actable, adapter, candidates)
            }
            state.update { it.copy(saving = false) }
            when (result) {
                GuidedSavePlanResult.Incomplete -> state.update { it.copy(saveError = true) }
                GuidedSavePlanResult.Defect -> state.update { it.copy(saveError = true) }
                is GuidedSavePlanResult.Ready -> when (result.plan.scope) {
                    GuidedMatchScope.Many -> broad.value = GuidedBroadState(result.plan)
                    GuidedMatchScope.Zero, GuidedMatchScope.One -> handleDuplicate(result.plan)
                }
            }
        }
    }

    private fun saveManual(candidates: List<GuidedRuleCandidate>) {
        val draft = state.value.draft
        val compiled = GuidedSelectorCompiler.compile(draft.toSelectorDraft())
        val selector = when (compiled) {
            is GuidedSelectorCompileResult.Success -> compiled.selector
            is GuidedSelectorCompileResult.Failure -> {
                state.update { it.copy(saving = false, saveError = true) }
                return
            }
        }
        state.update { it.copy(saving = false) }
        when (val duplicate = GuidedDuplicateCheck.find(duplicateCandidates(candidates), selector)) {
            GuidedDuplicateResult.None -> persist(replaceRuleKey = null)
            is GuidedDuplicateResult.Found -> showDuplicate(duplicate)
        }
    }

    fun narrowBroad() {
        broad.value = null
        startContext(GuidedContextMode.Inside)
    }

    fun confirmBroad() {
        broad.update { it?.copy(confirming = true) }
    }

    fun cancelBroadConfirm() {
        broad.update { it?.copy(confirming = false) }
    }

    fun keepBroad() {
        val plan = broad.value?.plan ?: return
        broad.value = null
        handleDuplicate(plan)
    }

    fun cancelBroad() {
        broad.value = null
    }

    private fun handleDuplicate(plan: GuidedSavePlan) {
        when (val duplicate = plan.duplicate) {
            GuidedDuplicateResult.None -> persist(replaceRuleKey = null)
            is GuidedDuplicateResult.Found -> showDuplicate(duplicate)
        }
    }

    private fun showDuplicate(duplicate: GuidedDuplicateResult.Found) {
        val candidate = duplicate.candidate
        val kind = when {
            candidate is GuidedRuleCandidate.App && candidate.inAuthoringGroup && duplicate.canReplace ->
                GuidedDuplicateKind.Replaceable

            candidate is GuidedRuleCandidate.App && candidate.inAuthoringGroup ->
                GuidedDuplicateKind.AuthoringAdvanced

            else -> GuidedDuplicateKind.Foreign
        }
        this.duplicate.value = GuidedDuplicateState(candidate, kind)
    }

    fun replaceDuplicate() {
        val candidate = duplicate.value?.candidate as? GuidedRuleCandidate.App ?: return
        val key = candidate.ruleKey ?: return
        duplicate.value = null
        persist(replaceRuleKey = key)
    }

    fun keepBothDuplicate() {
        duplicate.value = null
        persist(replaceRuleKey = null)
    }

    fun editDuplicateInJson() {
        val candidate = duplicate.value?.candidate ?: return
        duplicate.value = null
        requestRawHandoff(candidate)
    }

    fun cancelDuplicate() {
        duplicate.value = null
    }

    fun retrySave() {
        state.update { it.copy(saveError = false) }
        requestSave()
    }

    private fun persist(replaceRuleKey: Int?) {
        val scope = ruleScope ?: run {
            state.update { it.copy(saveError = true) }
            return
        }
        val current = state.value
        val appId = current.appId ?: run {
            state.update { it.copy(saveError = true) }
            return
        }
        val draft = current.draft
        val editingKey = current.editing?.ruleKey
        val label = current.pick?.actable?.let(GuidedNodeLabel::of)
        state.update { it.copy(saving = true, saveError = false) }
        val required = RequiredSubscription(SubscriptionIds.LOCAL, viewModelScope)
        persistJob = viewModelScope.launch {
            val ruleName = if (current.nameEdited) {
                draft.nameOverride?.takeIf { it.isNotBlank() }
            } else {
                autoRuleName(draft.action, label)
            }
            val request = GuidedRuleWriteRequest(
                appId = appId,
                appName = current.appName,
                authoringGroupName = authoringGroupName,
                ruleName = ruleName,
                action = draft.action,
                draft = draft.toSelectorDraft(),
                limitToAppOpening = draft.limitToAppOpening,
                replaceRuleKey = replaceRuleKey ?: editingKey,
            )
            var failure: GuidedRuleWriteFailure? = null
            val completed = runCatching {
                required.update { subscription ->
                    when (val result = GuidedRuleWriter.write(subscription, request)) {
                        is GuidedRuleWriteResult.Success -> result.subscription
                        is GuidedRuleWriteResult.Failure -> {
                            failure = result.reason
                            subscription
                        }
                    }
                }
            }.onFailure { cause ->
                if (cause is CancellationException) throw cause
            }
            if (completed.isSuccess && failure == null) {
                GuidedCaptureStore.clear()
                saved.value = true
            } else {
                state.update { it.copy(saving = false, saveError = true) }
            }
        }
    }

    private suspend fun autoRuleName(action: GuidedRuleAction, label: String?): String {
        return when (action) {
            GuidedRuleAction.Click -> if (label != null) {
                getString(Res.string.guided_rule_name_tap, label)
            } else {
                getString(Res.string.guided_rule_name_tap_fallback)
            }

            GuidedRuleAction.LongPress -> if (label != null) {
                getString(Res.string.guided_rule_name_long_press, label)
            } else {
                getString(Res.string.guided_rule_name_long_press_fallback)
            }
        }
    }

    fun rawDraftText(): String? {
        val draft = state.value.draft
        val selector = when (val result = GuidedSelectorCompiler.compile(draft.toSelectorDraft())) {
            is GuidedSelectorCompileResult.Success -> result.selector
            is GuidedSelectorCompileResult.Failure -> return null
        }
        val name = draft.nameOverride?.takeIf { it.isNotBlank() }
        return buildString {
            append("{\n")
            append("  name: ").append(SubscriptionJson.toJson5String(authoringGroupName)).append(",\n")
            append("  rules: [\n    {\n")
            if (name != null) {
                append("      name: ").append(SubscriptionJson.toJson5String(name)).append(",\n")
            }
            append("      action: ").append(SubscriptionJson.toJson5String(draft.action.key)).append(",\n")
            append("      matches: [").append(SubscriptionJson.toJson5String(selector)).append("]\n")
            append("    }\n  ]\n}")
        }
    }

    fun labelSuggestions(property: GuidedConditionProperty): List<String> {
        val values = nodes.mapNotNull { node ->
            when (property) {
                GuidedConditionProperty.Text -> node.attr.text
                GuidedConditionProperty.Description -> node.attr.desc
                GuidedConditionProperty.ViewId -> node.attr.vid
                GuidedConditionProperty.FullId -> node.attr.id
                GuidedConditionProperty.TypeName -> node.attr.name
                GuidedConditionProperty.Clickable,
                GuidedConditionProperty.VisibleToUser -> null
            }
        }
        return values.map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(4)
    }

    private fun duplicateCandidates(all: List<GuidedRuleCandidate>): List<GuidedRuleCandidate> {
        val editingKey = state.value.editing?.ruleKey
        val editingGroup = state.value.editing?.groupKey
        return all.filterNot { candidate ->
            candidate is GuidedRuleCandidate.App &&
                    candidate.ruleKey == editingKey &&
                    candidate.groupKey == editingGroup
        }
    }

    fun canSave(): Boolean = state.value.canSave

    fun draftChanged(): Boolean = state.value.draft != baseline

    fun requestBack() {
        val current = state.value
        if (current.editing != null) {
            exitEdit()
            return
        }
        if (current.phase == GuidedAuthoringPhase.Editing && !draftChanged()) {
            if (current.manual) closeSession() else backToPick()
            return
        }
        if (current.phase == GuidedAuthoringPhase.Picking) {
            closeSession()
            return
        }
        if (draftChanged()) {
            discardPrompt.value = true
        } else {
            closeSession()
        }
    }

    fun backToPick() {
        previewJob?.cancel()
        detectionJob?.cancel()
        recordingWaitJob?.cancel()
        pendingRecordedAction = null
        screenChangedWhileResolving = false
        state.update {
            it.copy(
                phase = GuidedAuthoringPhase.Picking,
                draft = GuidedRuleDraft(),
                pick = null,
                preview = GuidedPreviewState.NoPreview,
                existingHits = emptyList(),
                existingDismissed = true,
                editing = null,
                contextError = false,
                dynamicText = false,
                nameEdited = false,
                manual = false,
                saveError = false,
                contextMode = null,
                overlap = null,
                recordingFailure = null,
            )
        }
        baseline = GuidedRuleDraft()
    }

    fun dismissDiscard() {
        discardPrompt.value = false
    }

    fun confirmDiscard() {
        discardPrompt.value = false
        closeSession()
    }

    fun closeSession() {
        previewJob?.cancel()
        detectionJob?.cancel()
        persistJob?.cancel()
        recordingWaitJob?.cancel()
        GuidedRecordingSession.finish()
        GuidedCaptureStore.clear()
        finished.value = true
    }

    fun consumeSaved() {
        saved.value = false
    }

    fun consumeFinished() {
        finished.value = false
    }

    override fun onCleared() {
        GuidedRecordingSession.finish()
        GuidedCaptureStore.clear()
    }
}
