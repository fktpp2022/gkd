package li.gkd.app.ui.snapshot

import li.gkd.app.ui.component.SheetRequest
import li.gkd.app.ui.component.MultiSelectionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.gkd.app.authoring.GuidedSnapshotCapture
import li.gkd.app.platform.PlatformResult
import li.gkd.app.resources.*
import li.gkd.app.snapshot.SnapshotStore
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.state.BaseViewModel
import li.gkd.app.util.LogUtils
import li.gkd.app.util.ToastUtils
import li.gkd.db.Db
import li.gkd.db.Snapshot
import org.jetbrains.compose.resources.getString
import java.io.File

data class SnapshotDeleteResult(
    val deletedIds: Set<Long>,
    val failedCount: Int,
)

data class SnapshotShareProgress(val completed: Int, val total: Int)

class SnapshotViewModel : BaseViewModel() {
    val actionRequest: StateFlow<SheetRequest<Long>?>
        field = MutableStateFlow(null)

    val ruleReadiness: StateFlow<SnapshotRuleReadiness>
        field = MutableStateFlow<SnapshotRuleReadiness>(SnapshotRuleReadiness.Loading)

    private var readinessJob: Job? = null
    private var shownSnapshot: Snapshot? = null

    fun showActions(snapshot: Snapshot) {
        shownSnapshot = snapshot
        actionRequest.value = SheetRequest(snapshot.id)
        readinessJob?.cancel()
        ruleReadiness.value = SnapshotRuleReadiness.Loading
        readinessJob = scope.launch {
            val tree = SnapshotStore.loadSnapshot(snapshot.id)
            if (actionRequest.value?.key == snapshot.id) {
                ruleReadiness.value = snapshotRuleReadiness(snapshot.appId, tree)
            }
        }
    }

    fun dismissActions(request: SheetRequest<Long>) {
        actionRequest.compareAndSet(request, null)
    }

    /** Opens a guided session for the shown snapshot from the tree asynchronously loaded for it. */
    suspend fun openShownRuleAuthoring(): Boolean {
        val snapshot = shownSnapshot ?: return false
        val tree = (ruleReadiness.value as? SnapshotRuleReadiness.Available)?.tree ?: return false
        if (tree.id != snapshot.id) return false
        return GuidedSnapshotCapture.open(snapshot, tree)
    }

    val selection = MultiSelectionState<Long>()

    val uiState = Db.snapshotDao.query().stateLoadable()

    val shareProgress: StateFlow<SnapshotShareProgress?>
        field = MutableStateFlow(null)

    private var shareJob: Job? = null

    fun shareSnapshots(snapshots: List<Snapshot>, platform: SnapshotPlatformActions) {
        if (snapshots.isEmpty() || shareProgress.value != null) return
        val targets = snapshots.toList()
        shareProgress.value = SnapshotShareProgress(0, targets.size)
        shareJob = scope.launchUi {
            val archives = mutableListOf<File>()
            var handedOff = false
            try {
                for (snapshot in targets) {
                    currentCoroutineContext().ensureActive()
                    // Finish this ZIP and record ownership before cancellation can discard its result.
                    withContext(NonCancellable) {
                        archives += SnapshotStore.createArchive(
                            snapshot.id, snapshot.appId, snapshot.activityId
                        )
                    }
                    currentCoroutineContext().ensureActive()
                    shareProgress.value = SnapshotShareProgress(archives.size, targets.size)
                }
                when (platform.share(archives)) {
                    is PlatformResult.Success -> handedOff = true
                    PlatformResult.Unsupported ->
                        ToastUtils.show(getString(Res.string.platform_action_unsupported))
                }
            } finally {
                // Receivers may read files after the chooser returns; cache expiry owns those files.
                withContext(NonCancellable) {
                    if (!handedOff) archives.forEach { SnapshotStore.deleteArchive(it) }
                    shareProgress.value = null
                }
            }
        }
    }

    fun cancelShare() {
        shareJob?.cancel()
    }

    suspend fun deleteSnapshots(snapshots: List<Snapshot>): SnapshotDeleteResult {
        val deletedIds = mutableSetOf<Long>()
        var failedCount = 0
        snapshots.forEach { snapshot ->
            try {
                SnapshotStore.delete(snapshot)
                deletedIds.add(snapshot.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtils.d(e)
                failedCount++
            }
        }
        return SnapshotDeleteResult(deletedIds, failedCount)
    }

}
