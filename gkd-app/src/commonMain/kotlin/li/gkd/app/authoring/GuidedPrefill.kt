package li.gkd.app.authoring

import li.gkd.app.model.NodeInfo
import li.gkd.app.snapshot.SnapshotNodeAdapter
import li.gkd.selector.Selector
import li.gkd.selector.SelectorCompileResult

data class GuidedPrefill(
    val target: List<GuidedCondition.Identifier>,
    val selector: String,
    val matchCount: Int,
    val dynamicText: Boolean,
)

sealed class GuidedPrefillResult {
    data class Success(val prefill: GuidedPrefill) : GuidedPrefillResult()
    data class Failure(val reason: GuidedPrefillFailure) : GuidedPrefillResult()
}

enum class GuidedPrefillFailure {
    NoTree,
    NoIdentifier,
}

object GuidedPrefillBuilder {
    fun build(node: NodeInfo, adapter: SnapshotNodeAdapter): GuidedPrefillResult {
        val root = adapter.root ?: return failure(GuidedPrefillFailure.NoTree)
        val identifiers = mutableListOf<GuidedCondition.Identifier>()
        var identifierRows = 0
        var selector: String? = null
        var matchCount = 0
        var dynamicText = false

        for (candidate in candidates(node)) {
            if (candidate.condition !is GuidedCondition.TypeName && identifierRows >= MAX_IDENTIFIERS) {
                continue
            }
            val compiled = GuidedSelectorCompiler.compile(
                GuidedSelectorDraft(identifiers + candidate.condition),
            ) as? GuidedSelectorCompileResult.Success ?: continue
            val count = countMatches(root, adapter, compiled.selector)
            identifiers += candidate.condition
            if (candidate.condition !is GuidedCondition.TypeName) identifierRows++
            selector = compiled.selector
            matchCount = count
            dynamicText = dynamicText || candidate.dynamic
            if (count == 1) break
        }
        if (selector == null) return failure(GuidedPrefillFailure.NoIdentifier)
        return GuidedPrefillResult.Success(
            GuidedPrefill(
                target = identifiers,
                selector = selector,
                matchCount = matchCount,
                dynamicText = dynamicText,
            ),
        )
    }

    private fun countMatches(
        root: NodeInfo,
        adapter: SnapshotNodeAdapter,
        source: String,
    ): Int {
        val selector = when (val result = Selector.compile(source)) {
            is SelectorCompileResult.Success -> result.value
            is SelectorCompileResult.Failure -> return 0
        }
        return GuidedPreviewBuilder.matches(selector, root, adapter).size
    }

    private fun candidates(node: NodeInfo): List<Candidate> {
        val attr = node.attr
        val text = attr.text?.takeIf { it.isNotBlank() }
        val desc = attr.desc?.takeIf { it.isNotBlank() }
        val iconOnly = text == null && desc != null
        val result = mutableListOf<Candidate>()
        for (kind in if (iconOnly) iconOnlyOrder else defaultOrder) {
            when (kind) {
                Kind.Text -> if (text != null) {
                    textCandidate(text)?.let {
                        result += Candidate(
                            GuidedCondition.Text(it.operator, it.value),
                            it.dynamic,
                        )
                    }
                }

                Kind.Description -> if (desc != null) {
                    textCandidate(desc)?.let {
                        result += Candidate(
                            GuidedCondition.Description(it.operator, it.value),
                            it.dynamic,
                        )
                    }
                }

                Kind.ViewId -> attr.vid?.takeIf { it.isNotBlank() }?.let {
                    result += Candidate(GuidedCondition.ViewId(it), dynamic = false)
                }

                Kind.FullId -> attr.id?.takeIf { it.isNotBlank() }?.let {
                    result += Candidate(GuidedCondition.FullId(it), dynamic = false)
                }

                Kind.Type -> attr.name?.takeIf { it.isNotBlank() }?.let {
                    result += Candidate(GuidedCondition.TypeName(it), dynamic = false)
                }
            }
        }
        return result
    }

    private fun textCandidate(value: String): TextCandidate? {
        val prefix = dynamicTextPrefix(value)
        return if (prefix == null) {
            TextCandidate(GuidedTextOperator.Is, value, dynamic = false)
        } else if (prefix.length >= 2) {
            TextCandidate(GuidedTextOperator.Contains, prefix, dynamic = true)
        } else {
            null
        }
    }

    private fun dynamicTextPrefix(value: String): String? {
        val firstDigit = value.indexOfFirst(Char::isDigit)
        if (firstDigit < 0) return null
        return value.substring(0, firstDigit).trimEnd()
    }

    private fun failure(reason: GuidedPrefillFailure) = GuidedPrefillResult.Failure(reason)

    private data class TextCandidate(
        val operator: GuidedTextOperator,
        val value: String,
        val dynamic: Boolean,
    )

    private data class Candidate(
        val condition: GuidedCondition.Identifier,
        val dynamic: Boolean,
    )

    private enum class Kind { Text, ViewId, FullId, Description, Type }

    private val defaultOrder = listOf(
        Kind.Text,
        Kind.ViewId,
        Kind.FullId,
        Kind.Description,
        Kind.Type,
    )
    private val iconOnlyOrder = listOf(
        Kind.Description,
        Kind.ViewId,
        Kind.FullId,
        Kind.Text,
        Kind.Type,
    )

    private const val MAX_IDENTIFIERS = 3
}
