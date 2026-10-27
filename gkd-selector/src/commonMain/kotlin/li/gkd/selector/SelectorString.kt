package li.gkd.selector

import li.gkd.selector.syntax.escapeString

/** Quotes and escapes [value] exactly as the selector printer renders a string literal. */
public fun escapeSelectorString(value: String): String = escapeString(value)
