package li.gkd.app.authoring

import li.gkd.app.model.NodeInfo
import li.gkd.app.rule.RuleGroupPolicy
import li.gkd.app.snapshot.SnapshotNodeAdapter
import li.gkd.db.SubsItem
import li.gkd.db.SubscriptionIds
import li.gkd.selector.Selector
import li.gkd.selector.SelectorCompileResult

data class GuidedRuleDraft(
    val target: List<GuidedCondition> = emptyList(),
    val context: GuidedContext? = null,
    val action: GuidedRuleAction = GuidedRuleAction.Click,
    val nameOverride: String? = null,
    val limitToAppOpening: Boolean = false,
) {
    val usedProperties: Set<GuidedConditionProperty>
        get() = target.mapTo(mutableSetOf()) { it.property }

    fun addCondition(condition: GuidedCondition): GuidedRuleDraft {
        val index = target.indexOfFirst { it.property == condition.property }
        if (index < 0) return copy(target = target + condition)
        val target = target.toMutableList()
        target[index] = condition
        return copy(target = target)
    }

    fun removeCondition(property: GuidedConditionProperty): GuidedRuleDraft =
        copy(target = target.filterNot { it.property == property })

    fun updateCondition(
        property: GuidedConditionProperty,
        transform: (GuidedCondition) -> GuidedCondition,
    ): GuidedRuleDraft {
        val index = target.indexOfFirst { it.property == property }
        if (index < 0) return this
        val target = target.toMutableList()
        target[index] = transform(target[index])
        return copy(target = target)
    }
}

fun GuidedRuleDraft.toSelectorDraft(): GuidedSelectorDraft =
    GuidedSelectorDraft(target = target, context = context)

data class GuidedPick(
    val picked: NodeInfo,
    val actable: NodeInfo,
) {
    val climbed: Boolean get() = picked.id != actable.id
}

object GuidedPickResolver {
    fun resolve(picked: NodeInfo, adapter: SnapshotNodeAdapter): GuidedPick {
        if (picked.attr.clickable || picked.hasUsableText()) {
            return GuidedPick(picked, picked)
        }
        var current = picked
        while (true) {
            current = adapter.getParent(current) ?: return GuidedPick(picked, picked)
            if (current.attr.clickable) return GuidedPick(picked, current)
        }
    }
}

object GuidedNodeLabel {
    fun of(node: NodeInfo): String? = node.attr.text?.takeIf { it.isNotBlank() }
        ?: node.attr.desc?.takeIf { it.isNotBlank() }
        ?: node.attr.name?.takeIf { it.isNotBlank() }
}

private fun NodeInfo.hasUsableText(): Boolean =
    !attr.text.isNullOrBlank() || !attr.desc.isNullOrBlank()

enum class GuidedContextMode { Inside, NextTo }

sealed class GuidedContextBuildResult {
    data class Success(val context: GuidedContext) : GuidedContextBuildResult()
    data class Failure(val reason: GuidedContextBuildFailure) : GuidedContextBuildResult()
}

enum class GuidedContextBuildFailure {
    NoStableLabel,
}

object GuidedContextBuilder {
    fun build(
        mode: GuidedContextMode,
        picked: NodeInfo,
        anchor: NodeInfo,
        nodes: List<NodeInfo>,
        adapter: SnapshotNodeAdapter,
    ): GuidedContextBuildResult {
        val identifier = GuidedPrefillBuilder.identifiers(anchor, adapter).firstOrNull()
            ?: return GuidedContextBuildResult.Failure(GuidedContextBuildFailure.NoStableLabel)
        val relation = if (mode == GuidedContextMode.Inside) {
            GuidedContextRelation.Inside
        } else if (orderOf(anchor, nodes) < orderOf(picked, nodes)) {
            GuidedContextRelation.AnchorBeforeTarget
        } else {
            GuidedContextRelation.AnchorAfterTarget
        }
        return GuidedContextBuildResult.Success(
            GuidedContext(relation = relation, anchor = listOf(identifier)),
        )
    }

    private fun orderOf(node: NodeInfo, nodes: List<NodeInfo>): Int =
        nodes.indexOfFirst { it.id == node.id }
}

sealed class GuidedPreviewResult {
    data class Ready(
        val selector: String,
        val matches: List<NodeInfo>,
        val actable: NodeInfo,
    ) : GuidedPreviewResult()

    data object Incomplete : GuidedPreviewResult()
    data object Defect : GuidedPreviewResult()
}

object GuidedPreviewBuilder {
    fun build(
        draft: GuidedSelectorDraft,
        actable: NodeInfo,
        adapter: SnapshotNodeAdapter,
    ): GuidedPreviewResult {
        val canonical = when (val result = GuidedSelectorCompiler.compile(draft)) {
            is GuidedSelectorCompileResult.Success -> result.selector
            is GuidedSelectorCompileResult.Failure -> return when (result.reason) {
                GuidedSelectorCompileFailure.CompileError -> GuidedPreviewResult.Defect
                else -> GuidedPreviewResult.Incomplete
            }
        }
        val selector = when (val result = Selector.compile(canonical)) {
            is SelectorCompileResult.Success -> result.value
            is SelectorCompileResult.Failure -> return GuidedPreviewResult.Defect
        }
        val root = adapter.root ?: return GuidedPreviewResult.Incomplete
        return GuidedPreviewResult.Ready(
            selector = canonical,
            matches = matches(selector, root, adapter),
            actable = actable,
        )
    }

    fun matches(
        selector: Selector,
        root: NodeInfo,
        adapter: SnapshotNodeAdapter,
    ): List<NodeInfo> {
        val matches = mutableListOf<NodeInfo>()
        selector.match(root, adapter)?.let { matches += it }
        matches += adapter.querySelectorAll(root, selector)
        return matches.distinctBy { it.id }
    }
}

enum class GuidedMatchScope { Zero, One, Many }

data class GuidedSavePlan(
    val selector: String,
    val matches: List<NodeInfo>,
    val duplicate: GuidedDuplicateResult,
) {
    val matchCount: Int get() = matches.size

    val scope: GuidedMatchScope
        get() = when (matches.size) {
            0 -> GuidedMatchScope.Zero
            1 -> GuidedMatchScope.One
            else -> GuidedMatchScope.Many
        }
}

sealed class GuidedSavePlanResult {
    data class Ready(val plan: GuidedSavePlan) : GuidedSavePlanResult()
    data object Incomplete : GuidedSavePlanResult()
    data object Defect : GuidedSavePlanResult()
}

object GuidedSavePlanner {
    fun plan(
        draft: GuidedSelectorDraft,
        actable: NodeInfo,
        adapter: SnapshotNodeAdapter,
        candidates: List<GuidedRuleCandidate>,
    ): GuidedSavePlanResult {
        return when (val preview = GuidedPreviewBuilder.build(draft, actable, adapter)) {
            GuidedPreviewResult.Incomplete -> GuidedSavePlanResult.Incomplete
            GuidedPreviewResult.Defect -> GuidedSavePlanResult.Defect
            is GuidedPreviewResult.Ready -> GuidedSavePlanResult.Ready(
                GuidedSavePlan(
                    selector = preview.selector,
                    matches = preview.matches,
                    duplicate = GuidedDuplicateCheck.find(candidates, preview.selector),
                ),
            )
        }
    }
}

fun GuidedSavePlanResult.broadPlan(): GuidedSavePlan? =
    (this as? GuidedSavePlanResult.Ready)?.plan?.takeIf { it.scope == GuidedMatchScope.Many }

enum class GuidedDisabledScope { Subscription, App, Group }

sealed class GuidedSaveNotice {
    data object None : GuidedSaveNotice()
    data class Disabled(val scope: GuidedDisabledScope, val name: String) : GuidedSaveNotice()
}

object GuidedSaveNotices {
    fun resolve(scope: GuidedRuleScope, authoringGroupName: String): GuidedSaveNotice {
        val item = scope.configs.subsItems.find { it.id == SubscriptionIds.LOCAL }
            ?: SubsItem(id = SubscriptionIds.LOCAL, order = 0)
        if (!item.enable) {
            return GuidedSaveNotice.Disabled(GuidedDisabledScope.Subscription, scope.local.name)
        }
        val app = scope.local.apps.find { it.id == scope.appId }
        val appEnabled = scope.configs.appConfigs
            .find { it.subsId == SubscriptionIds.LOCAL && it.appId == scope.appId }
            ?.enable ?: true
        if (!appEnabled) {
            return GuidedSaveNotice.Disabled(
                GuidedDisabledScope.App,
                app?.name?.takeIf { it.isNotBlank() } ?: scope.appId,
            )
        }
        val group = app?.groups?.find { it.name == authoringGroupName } ?: return GuidedSaveNotice.None
        val groupConfig = scope.configs.appGroupConfigs.find {
            it.subsId == SubscriptionIds.LOCAL &&
                    it.appId == scope.appId &&
                    it.groupKey == group.key
        }
        val category = scope.local.getCategory(group.name)
        val categoryConfig = category?.let { category ->
            scope.configs.categoryConfigs.find {
                it.subsId == SubscriptionIds.LOCAL && it.categoryKey == category.key
            }
        }
        if (!RuleGroupPolicy.getGroupEnabled(group, groupConfig, category, categoryConfig)) {
            return GuidedSaveNotice.Disabled(GuidedDisabledScope.Group, group.name)
        }
        return GuidedSaveNotice.None
    }
}
