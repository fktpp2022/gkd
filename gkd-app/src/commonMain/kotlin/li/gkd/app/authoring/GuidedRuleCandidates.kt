package li.gkd.app.authoring

import kotlinx.coroutines.CancellationException
import li.gkd.app.subscription.RawSubscription

sealed interface GuidedRuleCandidate {
    val group: RawSubscription.RawGroupProps
    val rule: RawSubscription.RawRuleProps
    val inAuthoringGroup: Boolean
    val groupName get() = group.name
    val groupKey get() = group.key
    val ruleKey get() = rule.key
    val ruleName get() = rule.name
    val selectors get() = rule.matches.orEmpty() + rule.anyMatches.orEmpty()

    data class App(
        override val group: RawSubscription.RawAppGroup,
        override val rule: RawSubscription.RawAppRule,
        override val inAuthoringGroup: Boolean,
    ) : GuidedRuleCandidate

    data class Global(
        override val group: RawSubscription.RawGlobalGroup,
        override val rule: RawSubscription.RawGlobalRule,
    ) : GuidedRuleCandidate {
        override val inAuthoringGroup get() = false
    }
}

sealed interface GuidedRuleScan {
    data object Pending : GuidedRuleScan
    data class Ready(val candidates: List<GuidedRuleCandidate>) : GuidedRuleScan
    data object Failed : GuidedRuleScan
}

fun scanGuidedRules(scan: () -> List<GuidedRuleCandidate>): GuidedRuleScan {
    return try {
        GuidedRuleScan.Ready(scan())
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        GuidedRuleScan.Failed
    }
}
