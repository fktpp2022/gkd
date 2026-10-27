package li.gkd.app.ui.snapshot

import li.gkd.app.authoring.hasUsableNode
import li.gkd.app.model.ComplexSnapshot

/** Whether the snapshot shown in the actions sheet can open a guided authoring session. */
sealed interface SnapshotRuleReadiness {
    data object Loading : SnapshotRuleReadiness

    data class Available(val tree: ComplexSnapshot) : SnapshotRuleReadiness

    data object Unavailable : SnapshotRuleReadiness

    val canCreateRule: Boolean get() = this is Available
}

/** Maps a decoded stored tree to the action's readiness; a null tree could not be read. */
fun snapshotRuleReadiness(appId: String, tree: ComplexSnapshot?): SnapshotRuleReadiness =
    tree?.takeIf { appId.isNotBlank() && it.nodes.hasUsableNode() }
        ?.let(SnapshotRuleReadiness::Available)
        ?: SnapshotRuleReadiness.Unavailable
