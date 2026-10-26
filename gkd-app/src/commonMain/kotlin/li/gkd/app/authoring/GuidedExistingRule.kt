package li.gkd.app.authoring

import li.gkd.app.model.AppInfo
import li.gkd.app.model.NodeInfo
import li.gkd.app.rule.RuleExecutionParameters
import li.gkd.app.rule.RuleMatchEnvironment
import li.gkd.app.rule.RuleRuntime
import li.gkd.app.rule.RuleSummaryBuilder
import li.gkd.app.snapshot.SnapshotNodeAdapter
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.UsedSubsEntry
import li.gkd.db.SubscriptionConfigSnapshot
import li.gkd.db.SubscriptionIds
import li.gkd.db.SubsItem
import li.gkd.selector.Selector

data class GuidedRuleScope(
    val local: RawSubscription,
    val configs: SubscriptionConfigSnapshot,
    val appId: String,
    val activityId: String?,
    val appInfo: AppInfo?,
    val launcherAppId: String,
    val systemAppIds: Set<String>,
)

object GuidedRuleEligibility {
    /** Resolves enabled local rules for the captured screen, authoring group first. */
    fun candidates(
        scope: GuidedRuleScope,
        authoringGroupName: String,
    ): List<GuidedRuleCandidate> {
        val item = scope.configs.subsItems.find { it.id == SubscriptionIds.LOCAL }
            ?: SubsItem(id = SubscriptionIds.LOCAL, order = 0)
        if (!item.enable) return emptyList()
        val appInfoById = scope.appInfo?.let { mapOf(scope.appId to it) }.orEmpty()
        val summary = RuleSummaryBuilder.build(
            runtime = RuleRuntime(
                environment = {
                    RuleMatchEnvironment(scope.launcherAppId, scope.systemAppIds)
                },
            ),
            subscriptions = listOf(UsedSubsEntry(item, scope.local)),
            appInfoById = appInfoById,
            appConfigs = scope.configs.appConfigs,
            groupConfigs = scope.configs.appGroupConfigs + scope.configs.globalGroupConfigs,
            categoryConfigs = scope.configs.categoryConfigs,
            launcherAppId = scope.launcherAppId,
        )
        val appCandidates = summary.appIdToRules[scope.appId].orEmpty()
            .filter { it.matchActivity(scope.appId, scope.activityId) }
            .map { rule ->
                GuidedRuleCandidate.App(
                    group = rule.group,
                    rule = rule.rule as RawSubscription.RawAppRule,
                    inAuthoringGroup = rule.group.name == authoringGroupName,
                )
            }
        val globalCandidates = summary.globalRules
            .filter { it.matchActivity(scope.appId, scope.activityId) }
            .map { rule ->
                GuidedRuleCandidate.Global(
                    group = rule.group,
                    rule = rule.rule as RawSubscription.RawGlobalRule,
                )
            }
        val (authoring, other) = appCandidates.partition { it.inAuthoringGroup }
        return authoring + other + globalCandidates
    }
}

data class GuidedExistingRuleHit(
    val candidate: GuidedRuleCandidate,
    val target: NodeInfo,
)

object GuidedExistingRuleDetector {
    fun detect(
        candidates: List<GuidedRuleCandidate>,
        picked: NodeInfo,
        nodes: List<NodeInfo>,
        adapter: SnapshotNodeAdapter,
    ): List<GuidedExistingRuleHit> {
        if (candidates.isEmpty() || nodes.isEmpty()) return emptyList()
        val related = relatedNodeIds(picked, adapter)
        return candidates.mapNotNull { candidate ->
            val parameters = runCatching {
                RuleExecutionParameters(candidate.rule, candidate.group)
            }.getOrNull() ?: return@mapNotNull null
            val target = matchRule(parameters, nodes, adapter) ?: return@mapNotNull null
            if (target.id !in related) return@mapNotNull null
            GuidedExistingRuleHit(candidate, target)
        }
    }

    private fun matchRule(
        parameters: RuleExecutionParameters,
        nodes: List<NodeInfo>,
        adapter: SnapshotNodeAdapter,
    ): NodeInfo? {
        val root = adapter.root
        if (parameters.matchRoot) {
            return root?.let { matchFrom(parameters, it, it, adapter) }
        }
        return nodes.firstNotNullOfOrNull { node ->
            matchFrom(parameters, node, root, adapter)
        }
    }

    private fun matchFrom(
        parameters: RuleExecutionParameters,
        queryNode: NodeInfo,
        root: NodeInfo?,
        adapter: SnapshotNodeAdapter,
    ): NodeInfo? {
        var result: NodeInfo? = null
        if (parameters.anyMatches.isNotEmpty()) {
            result = parameters.anyMatches.firstNotNullOfOrNull { selector ->
                querySelfOrSelector(parameters, queryNode, selector, root, adapter)
            } ?: return null
        }
        for (selector in parameters.matches) {
            result = querySelfOrSelector(parameters, queryNode, selector, root, adapter)
                ?: return null
        }
        for (selector in parameters.excludeMatches) {
            if (querySelfOrSelector(parameters, queryNode, selector, root, adapter) != null) {
                return null
            }
        }
        if (parameters.excludeAllMatches.isNotEmpty() &&
            !parameters.excludeAllMatches.all { selector ->
                querySelfOrSelector(parameters, queryNode, selector, root, adapter) == null
            }
        ) {
            return null
        }
        return result
    }

    private fun querySelfOrSelector(
        parameters: RuleExecutionParameters,
        node: NodeInfo,
        selector: Selector,
        root: NodeInfo?,
        adapter: SnapshotNodeAdapter,
    ): NodeInfo? {
        if (selector.isMatchRoot) {
            return root?.let { selector.match(it, adapter, parameters.matchOptions) }
        }
        selector.match(node, adapter, parameters.matchOptions)?.let { return it }
        return adapter.querySelector(node, selector, parameters.matchOptions)
    }

    private fun relatedNodeIds(
        picked: NodeInfo,
        adapter: SnapshotNodeAdapter,
    ): Set<Int> {
        val ids = mutableSetOf(picked.id)
        var parent = adapter.getParent(picked)
        while (parent != null && ids.add(parent.id)) {
            parent = adapter.getParent(parent)
        }
        adapter.getDescendants(picked).forEach { ids.add(it.id) }
        return ids
    }
}
