package li.gkd.app.authoring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import li.gkd.selector.Selector
import li.gkd.selector.SelectorCompileResult

class GuidedSelectorTest {
    @Test
    fun compilesIdentifiersBeforeFlagsInRowOrder() {
        assertEquals(
            "[text*=\"OK\"][vid=\"btn_ok\"][clickable=true][visibleToUser=true]",
            compiled(
                GuidedSelectorDraft(
                    target = listOf(
                        GuidedCondition.Clickable,
                        GuidedCondition.Text(GuidedTextOperator.Contains, "OK"),
                        GuidedCondition.VisibleToUser,
                        GuidedCondition.ViewId("btn_ok"),
                    ),
                ),
            ),
        )
    }

    @Test
    fun compilesTypeTokenAsTheUnitName() {
        assertEquals(
            "android.widget.Button[text=\"OK\"]",
            compiled(
                GuidedSelectorDraft(
                    target = listOf(
                        GuidedCondition.Text(GuidedTextOperator.Is, "OK"),
                        GuidedCondition.TypeName("android.widget.Button"),
                    ),
                ),
            ),
        )
        assertEquals(
            "Button",
            compiled(
                GuidedSelectorDraft(listOf(GuidedCondition.TypeName("Button"))),
            ),
        )
    }

    @Test
    fun compilesInsideAndSiblingContexts() {
        val target = listOf<GuidedCondition>(
            GuidedCondition.Text(GuidedTextOperator.Is, "Bluetooth"),
        )
        val anchor = listOf(
            GuidedCondition.ViewId("settings_list"),
            GuidedCondition.Text(GuidedTextOperator.Is, "Settings"),
        )

        assertEquals(
            "[vid=\"settings_list\"][text=\"Settings\"] > @[text=\"Bluetooth\"]",
            compiled(
                GuidedSelectorDraft(
                    target,
                    GuidedContext(GuidedContextRelation.Inside, anchor),
                ),
            ),
        )
        assertEquals(
            "[vid=\"settings_list\"][text=\"Settings\"] + @[text=\"Bluetooth\"]",
            compiled(
                GuidedSelectorDraft(
                    target,
                    GuidedContext(GuidedContextRelation.AnchorBeforeTarget, anchor),
                ),
            ),
        )
        assertEquals(
            "[vid=\"settings_list\"][text=\"Settings\"] - @[text=\"Bluetooth\"]",
            compiled(
                GuidedSelectorDraft(
                    target,
                    GuidedContext(GuidedContextRelation.AnchorAfterTarget, anchor),
                ),
            ),
        )
    }

    @Test
    fun escapesValuesWithTheSharedSelectorEscaper() {
        assertEquals(
            "[text=\"quote\\\" slash\\\\ line\\n\"]",
            compiled(
                GuidedSelectorDraft(
                    listOf(
                        GuidedCondition.Text(
                            GuidedTextOperator.Is,
                            "quote\" slash\\ line\n",
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun reportsCompileFailures() {
        assertFailure(
            GuidedSelectorCompileFailure.EmptyTarget,
            GuidedSelectorDraft(emptyList()),
        )
        assertFailure(
            GuidedSelectorCompileFailure.EmptyConditionValue,
            GuidedSelectorDraft(
                listOf(GuidedCondition.Text(GuidedTextOperator.Is, "   ")),
            ),
        )
        assertFailure(
            GuidedSelectorCompileFailure.DuplicateProperty,
            GuidedSelectorDraft(
                listOf(
                    GuidedCondition.Text(GuidedTextOperator.Is, "a"),
                    GuidedCondition.Text(GuidedTextOperator.Contains, "b"),
                ),
            ),
        )
        assertFailure(
            GuidedSelectorCompileFailure.EmptyAnchor,
            GuidedSelectorDraft(
                listOf(GuidedCondition.Clickable),
                GuidedContext(GuidedContextRelation.Inside, emptyList()),
            ),
        )
        assertFailure(
            GuidedSelectorCompileFailure.CompileError,
            GuidedSelectorDraft(listOf(GuidedCondition.TypeName("Bad["))),
        )
    }

    @Test
    fun readsBackEveryRepresentableShape() {
        listOf(
            "[text=\"OK\"][clickable=true]",
            "[text*=\"OK\"]",
            "[desc=\"Search\"]",
            "[desc*=\"Search\"]",
            "[vid=\"btn_ok\"]",
            "[id=\"com.example:id/btn_ok\"]",
            "Button[text=\"OK\"]",
            "[text=\"OK\"][visibleToUser=true]",
            "[vid=\"settings_list\"] > @[text=\"Bluetooth\"]",
            "[text=\"Wi-Fi\"] + @[text=\"Bluetooth\"]",
            "[text=\"Wi-Fi\"] - @[text=\"Bluetooth\"]",
        ).forEach(::assertRoundTrip)
    }

    @Test
    fun addsTheTargetMarkerWhenAContextLacksOne() {
        assertEquals(
            "[text=\"Wi-Fi\"] + @[text=\"Bluetooth\"]",
            compiled(read("[text=\"Wi-Fi\"] + [text=\"Bluetooth\"]")),
        )
    }

    @Test
    fun readsAMarkedSingleUnitAsATarget() {
        val draft = read("@[text=\"OK\"]")

        assertEquals(
            listOf(GuidedCondition.Text(GuidedTextOperator.Is, "OK")),
            draft.target,
        )
        assertEquals(null, draft.context)
    }

    @Test
    fun rejectsShapesOutsideTheGuidedSubset() {
        assertReadFailure(GuidedSelectorReadFailure.InvalidSyntax, "[text=")
        assertReadFailure(
            GuidedSelectorReadFailure.UnsupportedSyntax,
            "([text=\"a\"]) && ([text=\"b\"])",
        )
        assertReadFailure(
            GuidedSelectorReadFailure.InvalidShape,
            "[text=\"a\"] > [text=\"b\"] > [text=\"c\"]",
        )
        assertReadFailure(
            GuidedSelectorReadFailure.InvalidShape,
            "@[text=\"a\"] > [text=\"b\"]",
        )
        assertReadFailure(
            GuidedSelectorReadFailure.UnsupportedRelation,
            "[text=\"a\"] < @[text=\"b\"]",
        )
        assertReadFailure(
            GuidedSelectorReadFailure.NonDefaultOffset,
            "[text=\"a\"] >2 @[text=\"b\"]",
        )
        assertReadFailure(
            GuidedSelectorReadFailure.UnsupportedProperty,
            "[name=\"Button\"]",
        )
        assertReadFailure(
            GuidedSelectorReadFailure.UnsupportedOperator,
            "[text^=\"a\"]",
        )
        assertReadFailure(
            GuidedSelectorReadFailure.UnsupportedValue,
            "[text=42]",
        )
        assertReadFailure(
            GuidedSelectorReadFailure.UnsupportedValue,
            "[clickable=false]",
        )
        assertReadFailure(
            GuidedSelectorReadFailure.FlagInAnchor,
            "[clickable=true] > @[text=\"x\"]",
        )
        assertReadFailure(
            GuidedSelectorReadFailure.DuplicateProperty,
            "[text=\"a\"][text=\"b\"]",
        )
    }

    private fun assertRoundTrip(source: String) {
        val canonical = when (val result = Selector.compile(source)) {
            is SelectorCompileResult.Success -> result.value.toString()
            is SelectorCompileResult.Failure -> throw result.error
        }
        assertEquals(canonical, compiled(read(canonical)), source)
    }

    private fun compiled(draft: GuidedSelectorDraft): String =
        when (val result = GuidedSelectorCompiler.compile(draft)) {
            is GuidedSelectorCompileResult.Success -> result.selector
            is GuidedSelectorCompileResult.Failure -> error("Compile failed: ${result.reason}")
        }

    private fun read(source: String): GuidedSelectorDraft =
        when (val result = GuidedSelectorReader.read(source)) {
            is GuidedSelectorReadResult.Success -> result.draft
            is GuidedSelectorReadResult.Unsupported -> error("Read failed: ${result.reason}")
        }

    private fun assertFailure(
        reason: GuidedSelectorCompileFailure,
        draft: GuidedSelectorDraft,
    ) {
        val result = assertIs<GuidedSelectorCompileResult.Failure>(
            GuidedSelectorCompiler.compile(draft),
        )
        assertEquals(reason, result.reason)
    }

    private fun assertReadFailure(
        reason: GuidedSelectorReadFailure,
        source: String,
    ) {
        val result = assertIs<GuidedSelectorReadResult.Unsupported>(
            GuidedSelectorReader.read(source),
            source,
        )
        assertEquals(reason, result.reason, source)
    }
}
