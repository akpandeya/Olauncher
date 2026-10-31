package app.olauncher.ui.askai

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [AiChatInlineMarkdown] — the display-time inline subset
 * (bold, italic, code) assistant replies render with
 * (docs/design/ask-ai-chat.md §Chat screen "Reply formatting"). Pure JVM, no
 * Android.
 */
class AiChatInlineMarkdownTest {

    private fun run(text: String, bold: Boolean = false, italic: Boolean = false, code: Boolean = false) =
        AiChatInlineMarkdown.Run(text, bold = bold, italic = italic, code = code)

    @Test
    fun `plain text without delimiters is one plain run`() {
        assertEquals(
            listOf(run("Movement into a place takes the accusative.")),
            AiChatInlineMarkdown.parse("Movement into a place takes the accusative."),
        )
    }

    @Test
    fun `bold pair renders one bold run`() {
        assertEquals(
            listOf(run("Besides "), run("gegenüber", bold = true), run(", dative prepositions include mit.")),
            AiChatInlineMarkdown.parse("Besides **gegenüber**, dative prepositions include mit."),
        )
    }

    @Test
    fun `italic with stars and with underscores renders italic runs`() {
        assertEquals(
            listOf(run("the verb "), run("stellen", italic = true), run(" takes the accusative")),
            AiChatInlineMarkdown.parse("the verb *stellen* takes the accusative"),
        )
        assertEquals(
            listOf(run("the verb "), run("stellen", italic = true), run(" takes the accusative")),
            AiChatInlineMarkdown.parse("the verb _stellen_ takes the accusative"),
        )
    }

    @Test
    fun `code span renders a code run`() {
        assertEquals(
            listOf(run("the prefix "), run("ge-", code = true), run(" marks the participle")),
            AiChatInlineMarkdown.parse("the prefix `ge-` marks the participle"),
        )
    }

    @Test
    fun `multiple bold pairs in one reply all render`() {
        assertEquals(
            listOf(
                run("Ich fahre "),
                run("mit", bold = true),
                run(" dem Bus — with "),
                run("nach", bold = true),
                run(" you say "),
                run("nach Berlin", bold = true),
                run("."),
            ),
            AiChatInlineMarkdown.parse("Ich fahre **mit** dem Bus — with **nach** you say **nach Berlin**."),
        )
    }

    @Test
    fun `unclosed bold marker stays literal and does not eat the rest of the paragraph`() {
        // A truncated reply: the marker never closes, everything after it stays plain.
        val text = "Yes! Besides **gegenüber, common dative prepositions include mit."
        assertEquals(listOf(run(text)), AiChatInlineMarkdown.parse(text))
    }

    @Test
    fun `unclosed bold marker leaves a later pair working`() {
        assertEquals(
            listOf(
                run("Yes! Besides **gegenüber, also "),
                run("mit", bold = true),
                run(" and "),
                run("nach", bold = true),
                run("."),
            ),
            AiChatInlineMarkdown.parse("Yes! Besides **gegenüber, also **mit** and **nach**."),
        )
    }

    @Test
    fun `unclosed backtick stays literal`() {
        val text = "the prefix `ge marks the participle"
        assertEquals(listOf(run(text)), AiChatInlineMarkdown.parse(text))
    }

    @Test
    fun `single stray star stays literal`() {
        val text = "that costs 5 * 3 credits"
        assertEquals(listOf(run(text)), AiChatInlineMarkdown.parse(text))
    }

    @Test
    fun `space flanked pair stays literal`() {
        val text = "compare a * b * c in the formula"
        assertEquals(listOf(run(text)), AiChatInlineMarkdown.parse(text))
    }

    @Test
    fun `german quotation marks pass through untouched`() {
        val text = "\u201EIch fahre mit dem Bus\u201C means \"I take the bus\"."
        assertEquals(listOf(run(text)), AiChatInlineMarkdown.parse(text))
    }

    @Test
    fun `german quotation marks around a bold word still render`() {
        assertEquals(
            listOf(run("the article "), run("die", bold = true), run(" in \u201Edie Stra\u00DFe\u201C names a feminine noun.")),
            AiChatInlineMarkdown.parse("the article **die** in \u201Edie Stra\u00DFe\u201C names a feminine noun."),
        )
    }

    @Test
    fun `intraword underscore stays literal`() {
        val text = "the id prep_two_way names the rule"
        assertEquals(listOf(run(text)), AiChatInlineMarkdown.parse(text))
    }

    @Test
    fun `empty and whitespace-only pairs stay literal`() {
        assertEquals(listOf(run("a **** b")), AiChatInlineMarkdown.parse("a **** b"))
        assertEquals(listOf(run("a ** ** b")), AiChatInlineMarkdown.parse("a ** ** b"))
    }

    @Test
    fun `code span content is verbatim and not reparsed`() {
        assertEquals(
            listOf(run("write "), run("**not bold**", code = true), run(" here")),
            AiChatInlineMarkdown.parse("write `**not bold**` here"),
        )
    }

    @Test
    fun `adjacent plain characters merge into one run`() {
        assertEquals(
            listOf(run("a"), run("b", bold = true), run("c")),
            AiChatInlineMarkdown.parse("a**b**c"),
        )
        val parsed = AiChatInlineMarkdown.parse("plain text, then **bold** and done")
        assertEquals(listOf(run("plain text, then "), run("bold", bold = true), run(" and done"), ), parsed)
    }
}
