package li.gkd.app.authoring

import li.gkd.selector.Selector
import li.gkd.selector.SelectorCompileResult

sealed class GuidedDuplicateResult {
    data object None : GuidedDuplicateResult()
    data class Found(
        val candidate: GuidedRuleCandidate,
        val canReplace: Boolean,
    ) : GuidedDuplicateResult()
}

object GuidedDuplicateCheck {
    fun find(
        candidates: List<GuidedRuleCandidate>,
        canonicalSelector: String,
    ): GuidedDuplicateResult {
        val matches = candidates.filter { candidate ->
            candidate.selectors.any { canonicalOrNull(it) == canonicalSelector }
        }
        if (matches.isEmpty()) return GuidedDuplicateResult.None
        val candidate = matches.firstOrNull {
            it is GuidedRuleCandidate.App && it.inAuthoringGroup
        } ?: matches.first()
        val canReplace = candidate is GuidedRuleCandidate.App &&
                candidate.inAuthoringGroup &&
                candidate.rule.key != null &&
                GuidedRuleReader.read(candidate.rule) is GuidedRuleReadResult.Success
        return GuidedDuplicateResult.Found(candidate, canReplace)
    }

    private fun canonicalOrNull(source: String): String? =
        when (val result = Selector.compile(source)) {
            is SelectorCompileResult.Success -> result.value.toString()
            is SelectorCompileResult.Failure -> null
        }
}
