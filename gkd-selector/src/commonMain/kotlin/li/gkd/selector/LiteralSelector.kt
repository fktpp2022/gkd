package li.gkd.selector

import li.gkd.selector.engine.LogicalSelectorExpression
import li.gkd.selector.engine.NotSelectorExpression
import li.gkd.selector.engine.UnitSelectorExpression
import li.gkd.selector.property.CompareOperator
import li.gkd.selector.property.ComparisonExpression
import li.gkd.selector.property.LogicalExpression
import li.gkd.selector.property.NotExpression
import li.gkd.selector.property.PropertyExpression
import li.gkd.selector.property.ValueExpression
import li.gkd.selector.relation.PolynomialExpression
import li.gkd.selector.relation.RelationExpression
import li.gkd.selector.relation.TupleExpression
import li.gkd.selector.syntax.SelectorParser

/**
 * A read-only projection of selector syntax that contains literal comparisons only.
 *
 * Logical expressions, negation, computed values, regex operators, and non-constant relation
 * offsets are rejected. Properties and operators are not interpreted; callers decide which parts
 * of this model they support.
 */
public data class LiteralSelector(
    val units: List<LiteralUnit>,
    val relations: List<LiteralRelation>,
)

public data class LiteralUnit(
    val at: Boolean,
    val typeName: String?,
    val conditions: List<LiteralCondition>,
)

public data class LiteralCondition(
    val property: String,
    val operator: String,
    val value: LiteralValue,
)

public data class LiteralRelation(
    val operator: String,
    val offset: Int,
)

public sealed class LiteralValue {
    public data class StringValue(val value: String) : LiteralValue()
    public data class BooleanValue(val value: Boolean) : LiteralValue()
    public data class IntValue(val value: Int) : LiteralValue()
    public data object NullValue : LiteralValue()
}

public sealed class LiteralSelectorParseResult {
    public class Success(public val selector: LiteralSelector) : LiteralSelectorParseResult()
    public class Invalid(public val error: SelectorSyntaxException) : LiteralSelectorParseResult()
    public class Unsupported(
        public val reason: LiteralSelectorUnsupportedReason,
    ) : LiteralSelectorParseResult()
}

public enum class LiteralSelectorUnsupportedReason {
    LogicalExpression,
    Negation,
    NonLiteralComparison,
    NonValueOperator,
    NonConstantRelation,
}

public object LiteralSelectorParser {
    public fun parse(source: String): LiteralSelectorParseResult {
        val expression = try {
            SelectorParser(source).readSelector()
        } catch (error: SelectorSyntaxException) {
            return LiteralSelectorParseResult.Invalid(error)
        }
        val unit = when (expression) {
            is UnitSelectorExpression -> expression
            is LogicalSelectorExpression -> return unsupported(
                LiteralSelectorUnsupportedReason.LogicalExpression,
            )

            is NotSelectorExpression -> return unsupported(
                LiteralSelectorUnsupportedReason.Negation,
            )
        }

        val units = ArrayList<LiteralUnit>(unit.propertySelectors.size)
        for (selector in unit.propertySelectors) {
            val conditions = ArrayList<LiteralCondition>(selector.filters.size)
            for (filter in selector.filters) {
                val condition = filter.toLiteralCondition()
                    ?: return unsupported(filter.unsupportedReason())
                conditions += condition
            }
            units += LiteralUnit(
                at = selector.at,
                typeName = selector.name.takeIf { it.isNotEmpty() },
                conditions = conditions,
            )
        }

        val relations = ArrayList<LiteralRelation>(unit.relations.size)
        for (relation in unit.relations) {
            val offset = relation.relationExpression.constantOffset()
                ?: return unsupported(LiteralSelectorUnsupportedReason.NonConstantRelation)
            relations += LiteralRelation(relation.operator.key, offset)
        }
        return LiteralSelectorParseResult.Success(
            LiteralSelector(units = units, relations = relations),
        )
    }

    private fun unsupported(reason: LiteralSelectorUnsupportedReason) =
        LiteralSelectorParseResult.Unsupported(reason)

    private fun PropertyExpression.toLiteralCondition(): LiteralCondition? {
        val comparison = this as? ComparisonExpression ?: return null
        if (comparison.operator !is CompareOperator.ValueOperator) return null
        val property = comparison.left as? ValueExpression.Identifier ?: return null
        val value = comparison.right.toLiteralValue() ?: return null
        return LiteralCondition(property.name, comparison.operator.key, value)
    }

    private fun PropertyExpression.unsupportedReason(): LiteralSelectorUnsupportedReason =
        when (this) {
            is LogicalExpression -> LiteralSelectorUnsupportedReason.LogicalExpression
            is NotExpression -> LiteralSelectorUnsupportedReason.Negation
            is ComparisonExpression -> if (operator !is CompareOperator.ValueOperator) {
                LiteralSelectorUnsupportedReason.NonValueOperator
            } else {
                LiteralSelectorUnsupportedReason.NonLiteralComparison
            }
        }

    private fun ValueExpression.toLiteralValue(): LiteralValue? = when (this) {
        is ValueExpression.StringLiteral -> LiteralValue.StringValue(value)
        is ValueExpression.BooleanLiteral -> LiteralValue.BooleanValue(value)
        is ValueExpression.IntLiteral -> LiteralValue.IntValue(value)
        is ValueExpression.NullLiteral -> LiteralValue.NullValue
        else -> null
    }

    private fun RelationExpression.constantOffset(): Int? = when (this) {
        is TupleExpression -> numbers.singleOrNull()?.minus(1)
        is PolynomialExpression -> if (a == 0) b - 1 else null
    }
}
