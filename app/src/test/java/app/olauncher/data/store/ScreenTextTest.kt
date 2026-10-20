package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTextTest {

    @Test
    fun `splits lines into words with the whole line as context`() {
        val result = ScreenText.choices(listOf("Ich liebe Fernweh.", "Guten Morgen!"))
        assertEquals(
            listOf(
                ScreenText.Choice("Ich", "Ich liebe Fernweh."),
                ScreenText.Choice("liebe", "Ich liebe Fernweh."),
                ScreenText.Choice("Fernweh", "Ich liebe Fernweh."),
                ScreenText.Choice("Guten", "Guten Morgen!"),
                ScreenText.Choice("Morgen", "Guten Morgen!"),
            ),
            result
        )
    }

    @Test
    fun `drops duplicates case-insensitively keeping the first`() {
        val result = ScreenText.choices(listOf("Haus haus", "HAUS Garten"))
        assertEquals(listOf("Haus", "Garten"), result.map { it.text })
        assertEquals("Haus haus", result[0].context)
        assertEquals("HAUS Garten", result[1].context)
    }

    @Test
    fun `drops numbers and one-letter tokens`() {
        val result = ScreenText.choices(listOf("a 12 3,5 10:30 x Tag 2024 B2"))
        assertEquals(listOf("Tag", "B2"), result.map { it.text })
    }

    @Test
    fun `context is capped at 400 chars`() {
        val line = "Wort " + "x".repeat(500)
        val result = ScreenText.choices(listOf(line))
        assertEquals(400, result[0].context.length)
    }

    @Test
    fun `caps at 300 choices`() {
        val line = (1..400).joinToString(" ") { "wort${"a".repeat(it % 5)}$it" }
        assertEquals(300, ScreenText.choices(listOf(line)).size)
    }

    @Test
    fun `blank input gives no choices`() {
        assertTrue(ScreenText.choices(emptyList()).isEmpty())
        assertTrue(ScreenText.choices(listOf("  ", "")).isEmpty())
    }
}
