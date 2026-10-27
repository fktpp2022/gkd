package li.gkd.selector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LiteralSelectorParserTest {
    @Test
    fun parsesTargetConditionsInSourceOrder() {
        val selector = parse("[text=\"OK\"][clickable=true]")

        assertEquals(
            LiteralSelector(
                units = listOf(
                    LiteralUnit(
                        at = false,
                        typeName = null,
                        conditions = listOf(
                            LiteralCondition(
                                "text",
                                "=",
                                LiteralValue.StringValue("OK"),
                            ),
                            LiteralCondition(
                                "clickable",
                                "=",
                                LiteralValue.BooleanValue(true),
                            ),
                        ),
                    ),
                ),
                relations = emptyList(),
            ),
            selector,
        )
    }

    @Test
    fun parsesTypeTokenInsideAndSiblingContexts() {
        assertEquals(
            listOf("android.widget.Button"),
            parse("android.widget.Button[text=\"OK\"]").units.map { it.typeName },
        )

        val inside = parse("[vid=\"settings_list\"] > @[text=\"Bluetooth\"]")
        assertEquals(listOf(0), inside.relations.map { it.offset })
        assertEquals(listOf(">"), inside.relations.map { it.operator })
        assertEquals(listOf(false, true), inside.units.map { it.at })

        assertEquals("+", parse("[text=\"Wi-Fi\"] + @[text=\"Bluetooth\"]").relations.single().operator)
        assertEquals("-", parse("[text=\"Wi-Fi\"] - @[text=\"Bluetooth\"]").relations.single().operator)
    }

    @Test
    fun canonicalizesSingleQuotedAndEscapedSources() {
        val source = "[text='a\\'b'][desc*=\"c\"]"
        val canonical = Selector.compile(source).value.toString()

        assertEquals(parse(canonical), parse(source))
    }

    @Test
    fun acceptsMultipleUnitsForCallersToRestrict() {
        val selector = parse("[text=\"a\"] > [text=\"b\"] > [text=\"c\"]")

        assertEquals(3, selector.units.size)
        assertEquals(listOf(">", ">"), selector.relations.map { it.operator })
    }

    @Test
    fun rejectsExpressionsOutsideTheLiteralSubset() {
        assertUnsupported(
            "([text=\"a\"]) && ([text=\"b\"])",
            LiteralSelectorUnsupportedReason.LogicalExpression,
        )
        assertUnsupported(
            "!([text=\"a\"])",
            LiteralSelectorUnsupportedReason.Negation,
        )
        assertUnsupported(
            "[text=\"a\" && desc=\"b\"]",
            LiteralSelectorUnsupportedReason.LogicalExpression,
        )
        assertUnsupported(
            "[text=desc]",
            LiteralSelectorUnsupportedReason.NonLiteralComparison,
        )
        assertUnsupported(
            "[text~=\"a\"]",
            LiteralSelectorUnsupportedReason.NonValueOperator,
        )
        assertUnsupported(
            "[text=\"a\"] >n [text=\"b\"]",
            LiteralSelectorUnsupportedReason.NonConstantRelation,
        )
        assertUnsupported(
            "[text=\"a\"] [text=\"b\"]",
            LiteralSelectorUnsupportedReason.NonConstantRelation,
        )
    }

    @Test
    fun reportsSyntaxErrorsSeparately() {
        assertIs<LiteralSelectorParseResult.Invalid>(LiteralSelectorParser.parse("[text="))
    }

    private fun parse(source: String): LiteralSelector =
        when (val result = LiteralSelectorParser.parse(source)) {
            is LiteralSelectorParseResult.Success -> result.selector
            is LiteralSelectorParseResult.Invalid -> throw result.error
            is LiteralSelectorParseResult.Unsupported -> error("Unsupported: ${result.reason}")
        }

    private fun assertUnsupported(
        source: String,
        reason: LiteralSelectorUnsupportedReason,
    ) {
        val result = assertIs<LiteralSelectorParseResult.Unsupported>(
            LiteralSelectorParser.parse(source),
            source,
        )
        assertEquals(reason, result.reason, source)
    }
}
