package li.gkd.app.authoring

import kotlinx.coroutines.runBlocking
import li.gkd.app.resources.*
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals

class GuidedRuleNameTest {
    @Test
    fun suggestedNameFollowsTheActionAndFallsBackWithoutALabel() = runBlocking {
        assertEquals(
            getString(Res.string.guided_rule_name_tap, "Skip 5s"),
            GuidedRuleAction.Click.suggestedName("Skip \n5s"),
        )
        assertEquals(
            getString(Res.string.guided_rule_name_long_press, "Later"),
            GuidedRuleAction.LongPress.suggestedName("Later"),
        )
        assertEquals(
            getString(Res.string.guided_rule_name_tap_fallback),
            GuidedRuleAction.Click.suggestedName(null),
        )
        assertEquals(
            getString(Res.string.guided_rule_name_long_press_fallback),
            GuidedRuleAction.LongPress.suggestedName("  "),
        )
    }
}
