package li.gkd.app.authoring

import li.gkd.selector.LiteralSelector
import li.gkd.selector.LiteralSelectorParseResult
import li.gkd.selector.LiteralSelectorParser
import li.gkd.selector.LiteralUnit
import li.gkd.selector.LiteralValue
import li.gkd.selector.Selector
import li.gkd.selector.SelectorCompileResult
import li.gkd.selector.escapeSelectorString

sealed class GuidedCondition {
    sealed class Identifier : GuidedCondition()
    sealed class Flag : GuidedCondition()

    data class Text(
        val operator: GuidedTextOperator,
        val value: String,
    ) : Identifier()

    data class Description(
        val operator: GuidedTextOperator,
        val value: String,
    ) : Identifier()

    data class ViewId(val value: String) : Identifier()
    data class FullId(val value: String) : Identifier()
    data class TypeName(val value: String) : Identifier()

    data object Clickable : Flag()
    data object VisibleToUser : Flag()
}

enum class GuidedConditionProperty(val key: String) {
    Text("text"),
    Description("desc"),
    ViewId("vid"),
    FullId("id"),
    TypeName("type"),
    Clickable("clickable"),
    VisibleToUser("visibleToUser"),
}

val GuidedCondition.property: GuidedConditionProperty
    get() = when (this) {
        is GuidedCondition.Text -> GuidedConditionProperty.Text
        is GuidedCondition.Description -> GuidedConditionProperty.Description
        is GuidedCondition.ViewId -> GuidedConditionProperty.ViewId
        is GuidedCondition.FullId -> GuidedConditionProperty.FullId
        is GuidedCondition.TypeName -> GuidedConditionProperty.TypeName
        GuidedCondition.Clickable -> GuidedConditionProperty.Clickable
        GuidedCondition.VisibleToUser -> GuidedConditionProperty.VisibleToUser
    }

enum class GuidedTextOperator(val key: String) {
    Is("="),
    Contains("*="),
}

enum class GuidedContextRelation { Inside, AnchorBeforeTarget, AnchorAfterTarget }

data class GuidedContext(
    val relation: GuidedContextRelation,
    val anchor: List<GuidedCondition.Identifier>,
)

data class GuidedSelectorDraft(
    val target: List<GuidedCondition>,
    val context: GuidedContext? = null,
)

sealed class GuidedSelectorCompileResult {
    data class Success(val selector: String) : GuidedSelectorCompileResult()
    data class Failure(val reason: GuidedSelectorCompileFailure) : GuidedSelectorCompileResult()
}

enum class GuidedSelectorCompileFailure {
    EmptyTarget,
    EmptyAnchor,
    EmptyConditionValue,
    DuplicateProperty,
    CompileError,
}

object GuidedSelectorCompiler {
    fun compile(draft: GuidedSelectorDraft): GuidedSelectorCompileResult {
        if (draft.target.isEmpty()) return failure(GuidedSelectorCompileFailure.EmptyTarget)
        val target = when (val result = compileUnit(draft.target, at = draft.context != null)) {
            is CompiledUnit.Success -> result.selector
            is CompiledUnit.Failure -> return failure(result.reason)
        }
        val context = draft.context
        val raw = if (context == null) {
            target
        } else {
            if (context.anchor.isEmpty()) return failure(GuidedSelectorCompileFailure.EmptyAnchor)
            val anchor = when (val result = compileUnit(context.anchor, at = false)) {
                is CompiledUnit.Success -> result.selector
                is CompiledUnit.Failure -> return failure(result.reason)
            }
            val operator = when (context.relation) {
                GuidedContextRelation.Inside -> ">"
                GuidedContextRelation.AnchorBeforeTarget -> "+"
                GuidedContextRelation.AnchorAfterTarget -> "-"
            }
            "$anchor $operator $target"
        }
        return when (val result = Selector.compile(raw)) {
            is SelectorCompileResult.Success -> {
                GuidedSelectorCompileResult.Success(result.value.toString())
            }

            is SelectorCompileResult.Failure -> failure(GuidedSelectorCompileFailure.CompileError)
        }
    }

    private fun compileUnit(
        conditions: List<GuidedCondition>,
        at: Boolean,
    ): CompiledUnit {
        val seen = mutableSetOf<GuidedConditionProperty>()
        val typeName = conditions.filterIsInstance<GuidedCondition.TypeName>().firstOrNull()
        for (condition in conditions) {
            if (!seen.add(condition.property)) {
                return CompiledUnit.Failure(GuidedSelectorCompileFailure.DuplicateProperty)
            }
            val value = when (condition) {
                is GuidedCondition.Text -> condition.value
                is GuidedCondition.Description -> condition.value
                is GuidedCondition.ViewId -> condition.value
                is GuidedCondition.FullId -> condition.value
                is GuidedCondition.TypeName -> condition.value
                GuidedCondition.Clickable, GuidedCondition.VisibleToUser -> null
            }
            if (value != null && value.isBlank()) {
                return CompiledUnit.Failure(GuidedSelectorCompileFailure.EmptyConditionValue)
            }
        }

        val builder = StringBuilder()
        if (at) builder.append('@')
        if (typeName != null) builder.append(typeName.value)
        for (condition in conditions.filterNot { it is GuidedCondition.TypeName }
            .sortedBy { if (it is GuidedCondition.Flag) 1 else 0 }) {
            when (condition) {
                is GuidedCondition.Text -> builder.append(
                    "[text${condition.operator.key}${escapeSelectorString(condition.value)}]",
                )

                is GuidedCondition.Description -> builder.append(
                    "[desc${condition.operator.key}${escapeSelectorString(condition.value)}]",
                )

                is GuidedCondition.ViewId -> builder.append(
                    "[vid=${escapeSelectorString(condition.value)}]",
                )

                is GuidedCondition.FullId -> builder.append(
                    "[id=${escapeSelectorString(condition.value)}]",
                )

                is GuidedCondition.TypeName -> Unit
                GuidedCondition.Clickable -> builder.append("[clickable=true]")
                GuidedCondition.VisibleToUser -> builder.append("[visibleToUser=true]")
            }
        }
        return CompiledUnit.Success(builder.toString())
    }

    private fun failure(reason: GuidedSelectorCompileFailure) = GuidedSelectorCompileResult.Failure(reason)

    private sealed interface CompiledUnit {
        data class Success(val selector: String) : CompiledUnit
        data class Failure(val reason: GuidedSelectorCompileFailure) : CompiledUnit
    }
}

sealed class GuidedSelectorReadResult {
    data class Success(val draft: GuidedSelectorDraft) : GuidedSelectorReadResult()
    data class Unsupported(val reason: GuidedSelectorReadFailure) : GuidedSelectorReadResult()
}

enum class GuidedSelectorReadFailure {
    InvalidSyntax,
    UnsupportedSyntax,
    InvalidShape,
    UnsupportedRelation,
    NonDefaultOffset,
    UnsupportedProperty,
    UnsupportedOperator,
    UnsupportedValue,
    FlagInAnchor,
    DuplicateProperty,
}

object GuidedSelectorReader {
    fun read(source: String): GuidedSelectorReadResult =
        when (val result = LiteralSelectorParser.parse(source)) {
            is LiteralSelectorParseResult.Success -> read(result.selector)
            is LiteralSelectorParseResult.Invalid -> unsupported(
                GuidedSelectorReadFailure.InvalidSyntax,
            )

            is LiteralSelectorParseResult.Unsupported -> unsupported(
                GuidedSelectorReadFailure.UnsupportedSyntax,
            )
        }

    fun read(selector: LiteralSelector): GuidedSelectorReadResult {
        val units = selector.units
        if (units.size !in 1..2 || selector.relations.size != units.size - 1) {
            return unsupported(GuidedSelectorReadFailure.InvalidShape)
        }
        if (units.size == 1) {
            return when (val target = readUnit(units[0], allowFlags = true)) {
                is ReadUnit.Success -> GuidedSelectorReadResult.Success(
                    GuidedSelectorDraft(target.conditions),
                )

                is ReadUnit.Failure -> unsupported(target.reason)
            }
        }

        val anchorUnit = units[0]
        val targetUnit = units[1]
        if (anchorUnit.at) {
            return unsupported(GuidedSelectorReadFailure.InvalidShape)
        }
        val relation = selector.relations.single()
        if (relation.offset != 0) {
            return unsupported(GuidedSelectorReadFailure.NonDefaultOffset)
        }
        val contextRelation = when (relation.operator) {
            ">" -> GuidedContextRelation.Inside
            "+" -> GuidedContextRelation.AnchorBeforeTarget
            "-" -> GuidedContextRelation.AnchorAfterTarget
            else -> return unsupported(GuidedSelectorReadFailure.UnsupportedRelation)
        }
        val anchor = when (val result = readUnit(anchorUnit, allowFlags = false)) {
            is ReadUnit.Success -> result.conditions
            is ReadUnit.Failure -> return unsupported(result.reason)
        }
        val target = when (val result = readUnit(targetUnit, allowFlags = true)) {
            is ReadUnit.Success -> result.conditions
            is ReadUnit.Failure -> return unsupported(result.reason)
        }
        return GuidedSelectorReadResult.Success(
            GuidedSelectorDraft(
                target = target,
                context = GuidedContext(
                    relation = contextRelation,
                    anchor = anchor.filterIsInstance<GuidedCondition.Identifier>(),
                ),
            ),
        )
    }

    private fun readUnit(unit: LiteralUnit, allowFlags: Boolean): ReadUnit {
        val conditions = mutableListOf<GuidedCondition>()
        val seen = mutableSetOf<GuidedConditionProperty>()
        val typeName = unit.typeName
        if (typeName != null) {
            if (typeName.isBlank() || typeName == "*") {
                return ReadUnit.Failure(GuidedSelectorReadFailure.UnsupportedValue)
            }
            conditions += GuidedCondition.TypeName(typeName)
            seen += GuidedConditionProperty.TypeName
        }
        for (condition in unit.conditions) {
            val guided = when (condition.property) {
                "text" -> {
                    val operator = textOperator(condition.operator)
                        ?: return ReadUnit.Failure(GuidedSelectorReadFailure.UnsupportedOperator)
                    val value = stringValue(condition.value)
                        ?: return ReadUnit.Failure(GuidedSelectorReadFailure.UnsupportedValue)
                    GuidedCondition.Text(operator, value)
                }

                "desc" -> {
                    val operator = textOperator(condition.operator)
                        ?: return ReadUnit.Failure(GuidedSelectorReadFailure.UnsupportedOperator)
                    val value = stringValue(condition.value)
                        ?: return ReadUnit.Failure(GuidedSelectorReadFailure.UnsupportedValue)
                    GuidedCondition.Description(operator, value)
                }

                "vid" -> {
                    if (condition.operator != "=") {
                        return ReadUnit.Failure(GuidedSelectorReadFailure.UnsupportedOperator)
                    }
                    val value = stringValue(condition.value)
                        ?: return ReadUnit.Failure(GuidedSelectorReadFailure.UnsupportedValue)
                    GuidedCondition.ViewId(value)
                }

                "id" -> {
                    if (condition.operator != "=") {
                        return ReadUnit.Failure(GuidedSelectorReadFailure.UnsupportedOperator)
                    }
                    val value = stringValue(condition.value)
                        ?: return ReadUnit.Failure(GuidedSelectorReadFailure.UnsupportedValue)
                    GuidedCondition.FullId(value)
                }

                "clickable" -> {
                    if (!allowFlags) {
                        return ReadUnit.Failure(GuidedSelectorReadFailure.FlagInAnchor)
                    }
                    if (condition.operator != "=" || !trueValue(condition.value)) {
                        return ReadUnit.Failure(GuidedSelectorReadFailure.UnsupportedValue)
                    }
                    GuidedCondition.Clickable
                }

                "visibleToUser" -> {
                    if (!allowFlags) {
                        return ReadUnit.Failure(GuidedSelectorReadFailure.FlagInAnchor)
                    }
                    if (condition.operator != "=" || !trueValue(condition.value)) {
                        return ReadUnit.Failure(GuidedSelectorReadFailure.UnsupportedValue)
                    }
                    GuidedCondition.VisibleToUser
                }

                else -> return ReadUnit.Failure(GuidedSelectorReadFailure.UnsupportedProperty)
            }
            if (!seen.add(guided.property)) {
                return ReadUnit.Failure(GuidedSelectorReadFailure.DuplicateProperty)
            }
            conditions += guided
        }
        if (conditions.isEmpty()) {
            return ReadUnit.Failure(GuidedSelectorReadFailure.UnsupportedValue)
        }
        return ReadUnit.Success(conditions)
    }

    private fun textOperator(key: String): GuidedTextOperator? = when (key) {
        "=" -> GuidedTextOperator.Is
        "*=" -> GuidedTextOperator.Contains
        else -> null
    }

    private fun stringValue(value: LiteralValue): String? =
        (value as? LiteralValue.StringValue)?.value?.takeIf { it.isNotBlank() }

    private fun trueValue(value: LiteralValue): Boolean =
        (value as? LiteralValue.BooleanValue)?.value == true

    private fun unsupported(reason: GuidedSelectorReadFailure) =
        GuidedSelectorReadResult.Unsupported(reason)
}

private sealed interface ReadUnit {
    data class Success(val conditions: List<GuidedCondition>) : ReadUnit
    data class Failure(val reason: GuidedSelectorReadFailure) : ReadUnit
}
