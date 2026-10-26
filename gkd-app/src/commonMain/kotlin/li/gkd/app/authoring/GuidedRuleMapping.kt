package li.gkd.app.authoring

import li.gkd.app.subscription.RawSubscription
import li.gkd.db.SubscriptionIds

enum class GuidedRuleAction(val key: String) {
    Click("click"),
    LongPress("longClick"),
    ;

    companion object {
        fun fromKey(key: String?): GuidedRuleAction? = entries.find { it.key == key }
    }
}

object GuidedRuleLimits {
    const val MATCH_TIME_MS = 10_000L
    const val ACTION_MAXIMUM = 1
    const val RESET_MATCH = "app"
}

data class GuidedRuleWriteRequest(
    val appId: String,
    val appName: String?,
    val authoringGroupName: String,
    val ruleName: String?,
    val action: GuidedRuleAction,
    val draft: GuidedSelectorDraft,
    val limitToAppOpening: Boolean,
    val replaceRuleKey: Int? = null,
)

sealed class GuidedRuleWriteResult {
    data class Success(
        val subscription: RawSubscription,
    ) : GuidedRuleWriteResult()

    data class Failure(val reason: GuidedRuleWriteFailure) : GuidedRuleWriteResult()
}

enum class GuidedRuleWriteFailure {
    NotLocalSubscription,
    InvalidSelector,
    ReplaceTargetMissing,
}

object GuidedRuleWriter {
    fun write(
        current: RawSubscription,
        request: GuidedRuleWriteRequest,
    ): GuidedRuleWriteResult {
        if (!SubscriptionIds.isLocal(current.id)) {
            return failure(GuidedRuleWriteFailure.NotLocalSubscription)
        }
        val canonical = when (val result = GuidedSelectorCompiler.compile(request.draft)) {
            is GuidedSelectorCompileResult.Success -> result.selector
            is GuidedSelectorCompileResult.Failure -> {
                return failure(GuidedRuleWriteFailure.InvalidSelector)
            }
        }
        val appIndex = current.apps.indexOfFirst { it.id == request.appId }
        val app = current.apps.getOrNull(appIndex) ?: RawSubscription.RawApp(
            id = request.appId,
            name = request.appName,
            groups = emptyList(),
        )
        val groupIndex = app.groups.indexOfFirst { it.name == request.authoringGroupName }
        val group = app.groups.getOrNull(groupIndex)

        val replacementKey = request.replaceRuleKey
        val newGroup: RawSubscription.RawAppGroup
        if (replacementKey != null) {
            val ruleIndex = group?.rules?.indexOfFirst { it.key == replacementKey } ?: -1
            if (group == null || ruleIndex < 0) {
                return failure(GuidedRuleWriteFailure.ReplaceTargetMissing)
            }
            val replacedRule = rewriteRule(group.rules[ruleIndex], request, canonical)
            val rules = group.rules.toMutableList().apply { set(ruleIndex, replacedRule) }
            newGroup = group.copy(rules = rules)
        } else {
            val ruleKey = nextRuleKey(group?.rules.orEmpty())
            val rule = newRule(ruleKey, request, canonical)
            newGroup = if (group == null) {
                newGroup(
                    key = nextGroupKey(app.groups),
                    name = request.authoringGroupName,
                    rule = rule,
                )
            } else {
                group.copy(rules = group.rules + rule)
            }
        }

        val groups = if (group == null) {
            app.groups + newGroup
        } else {
            app.groups.toMutableList().apply { set(groupIndex, newGroup) }
        }
        val updatedApp = app.copy(groups = groups)
        val apps = if (appIndex < 0) {
            current.apps + updatedApp
        } else {
            current.apps.toMutableList().apply { set(appIndex, updatedApp) }
        }
        return GuidedRuleWriteResult.Success(
            subscription = current.copy(apps = apps),
        )
    }

    private fun rewriteRule(
        rule: RawSubscription.RawAppRule,
        request: GuidedRuleWriteRequest,
        canonical: String,
    ): RawSubscription.RawAppRule {
        val limits = limits(request)
        return rule.copy(
            name = request.ruleName?.takeIf { it.isNotBlank() },
            action = request.action.key,
            matches = listOf(canonical),
            matchTime = limits.matchTime,
            actionMaximum = limits.actionMaximum,
            resetMatch = limits.resetMatch,
        )
    }

    private fun newRule(
        key: Int,
        request: GuidedRuleWriteRequest,
        canonical: String,
    ): RawSubscription.RawAppRule {
        val limits = limits(request)
        return RawSubscription.RawAppRule(
            key = key,
            name = request.ruleName?.takeIf { it.isNotBlank() },
            preKeys = null,
            action = request.action.key,
            position = null,
            swipeArg = null,
            matches = listOf(canonical),
            excludeMatches = null,
            excludeAllMatches = null,
            anyMatches = null,
            actionCdKey = null,
            actionMaximumKey = null,
            actionCd = null,
            actionDelay = null,
            fastQuery = null,
            matchRoot = null,
            actionMaximum = limits.actionMaximum,
            priorityTime = null,
            priorityActionMaximum = null,
            order = null,
            forcedTime = null,
            matchDelay = null,
            matchTime = limits.matchTime,
            resetMatch = limits.resetMatch,
            snapshotUrls = null,
            excludeSnapshotUrls = null,
            exampleUrls = null,
            activityIds = null,
            excludeActivityIds = null,
            versionCode = null,
            versionName = null,
        )
    }

    private fun newGroup(
        key: Int,
        name: String,
        rule: RawSubscription.RawAppRule,
    ): RawSubscription.RawAppGroup = RawSubscription.RawAppGroup(
        key = key,
        name = name,
        desc = null,
        enable = true,
        scopeKeys = null,
        actionCdKey = null,
        actionMaximumKey = null,
        actionCd = null,
        actionDelay = null,
        fastQuery = null,
        matchRoot = null,
        actionMaximum = null,
        priorityTime = null,
        priorityActionMaximum = null,
        order = null,
        forcedTime = null,
        matchDelay = null,
        matchTime = null,
        resetMatch = null,
        snapshotUrls = null,
        excludeSnapshotUrls = null,
        exampleUrls = null,
        activityIds = null,
        excludeActivityIds = null,
        rules = listOf(rule),
        versionCode = null,
        versionName = null,
        ignoreGlobalGroupMatch = null,
    )

    private class Limits(
        val matchTime: Long?,
        val actionMaximum: Int?,
        val resetMatch: String?,
    )

    private fun limits(request: GuidedRuleWriteRequest) = if (request.limitToAppOpening) {
        Limits(
            matchTime = GuidedRuleLimits.MATCH_TIME_MS,
            actionMaximum = GuidedRuleLimits.ACTION_MAXIMUM,
            resetMatch = GuidedRuleLimits.RESET_MATCH,
        )
    } else {
        Limits(matchTime = null, actionMaximum = null, resetMatch = null)
    }

    private fun nextRuleKey(rules: List<RawSubscription.RawAppRule>): Int =
        (rules.mapNotNull { it.key }.maxOrNull() ?: 0) + 1

    private fun nextGroupKey(groups: List<RawSubscription.RawAppGroup>): Int =
        (groups.maxOfOrNull { it.key } ?: 0) + 1

    private fun failure(reason: GuidedRuleWriteFailure) = GuidedRuleWriteResult.Failure(reason)
}
