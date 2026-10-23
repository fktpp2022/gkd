package li.gkd.app.authoring

import li.gkd.app.subscription.RawSubscription

data class GuidedRuleData(
    val draft: GuidedSelectorDraft,
    val action: GuidedRuleAction,
    val limitToAppOpening: Boolean,
    val name: String?,
)

sealed class GuidedRuleReadResult {
    data class Success(val data: GuidedRuleData) : GuidedRuleReadResult()
    data class Unsupported(val reason: GuidedRuleReadFailure) : GuidedRuleReadResult()
}

enum class GuidedRuleReadFailure {
    AdvancedFields,
    UnsupportedAction,
    UnsupportedSelector,
    UnsupportedLimits,
}

object GuidedRuleReader {
    fun read(rule: RawSubscription.RawAppRule): GuidedRuleReadResult {
        if (hasAdvancedFields(rule)) {
            return unsupported(GuidedRuleReadFailure.AdvancedFields)
        }
        val action = GuidedRuleAction.fromKey(rule.action)
            ?: return unsupported(GuidedRuleReadFailure.UnsupportedAction)
        if (rule.anyMatches != null) {
            return unsupported(GuidedRuleReadFailure.UnsupportedSelector)
        }
        val matches = rule.matches
        if (matches == null || matches.size != 1) {
            return unsupported(GuidedRuleReadFailure.UnsupportedSelector)
        }
        val limitToAppOpening = when {
            rule.matchTime == null && rule.actionMaximum == null && rule.resetMatch == null -> false
            rule.matchTime == GuidedRuleLimits.MATCH_TIME_MS &&
                    rule.actionMaximum == GuidedRuleLimits.ACTION_MAXIMUM &&
                    rule.resetMatch == GuidedRuleLimits.RESET_MATCH -> true

            else -> return unsupported(GuidedRuleReadFailure.UnsupportedLimits)
        }
        val draft = when (val result = GuidedSelectorReader.read(matches.single())) {
            is GuidedSelectorReadResult.Success -> result.draft
            is GuidedSelectorReadResult.Unsupported -> {
                return unsupported(GuidedRuleReadFailure.UnsupportedSelector)
            }
        }
        return GuidedRuleReadResult.Success(
            GuidedRuleData(
                draft = draft,
                action = action,
                limitToAppOpening = limitToAppOpening,
                name = rule.name,
            ),
        )
    }

    private fun hasAdvancedFields(rule: RawSubscription.RawAppRule): Boolean =
        rule.preKeys != null ||
                rule.position != null ||
                rule.swipeArg != null ||
                rule.excludeMatches != null ||
                rule.excludeAllMatches != null ||
                rule.actionCd != null ||
                rule.actionDelay != null ||
                rule.matchDelay != null ||
                rule.fastQuery != null ||
                rule.matchRoot != null ||
                rule.forcedTime != null ||
                rule.order != null ||
                rule.priorityTime != null ||
                rule.priorityActionMaximum != null ||
                rule.actionCdKey != null ||
                rule.actionMaximumKey != null ||
                rule.snapshotUrls != null ||
                rule.excludeSnapshotUrls != null ||
                rule.exampleUrls != null ||
                rule.activityIds != null ||
                rule.excludeActivityIds != null ||
                rule.versionCode != null ||
                rule.versionName != null

    private fun unsupported(reason: GuidedRuleReadFailure) =
        GuidedRuleReadResult.Unsupported(reason)
}
