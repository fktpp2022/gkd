package li.gkd.selector

import kotlin.test.Test
import kotlin.test.assertEquals

class SelectorStringTest {
    @Test
    fun quotesAndEscapesLikeTheSelectorPrinter() {
        assertEquals("\"plain\"", escapeSelectorString("plain"))
        assertEquals("\"a\\\"b\"", escapeSelectorString("a\"b"))
        assertEquals("\"a\\\\b\"", escapeSelectorString("a\\b"))
        assertEquals("\"a\\nb\\tc\"", escapeSelectorString("a\nb\tc"))
        assertEquals("\"\\x01\"", escapeSelectorString("\u0001"))
        assertEquals("\"\uD83D\uDE00\"", escapeSelectorString("\uD83D\uDE00"))
    }

    @Test
    fun escapedTextIsAcceptedByTheParser() {
        val source = "[text=${escapeSelectorString("quote\" slash\\ line\n")}]"
        val canonical = Selector.compile(source).value.toString()

        assertEquals("[text=\"quote\\\" slash\\\\ line\\n\"]", canonical)
    }
}
