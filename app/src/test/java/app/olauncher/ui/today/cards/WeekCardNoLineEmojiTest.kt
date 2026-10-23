package app.olauncher.ui.today.cards

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WeekCardNoLineEmojiTest {

    private fun source(name: String): String =
        listOf("app/src/main/java/app/olauncher/ui/today/cards/$name", "src/main/java/app/olauncher/ui/today/cards/$name")
            .map(::File).first { it.exists() }.readText()

    @Test
    fun `week card text has no per-line emoji`() {
        val text = source("WeekCardText.kt")
        assertFalse(text.contains("formatWithEmoji"))
        assertFalse(text.contains("fun emoji("))
        for (e in listOf("📱", "📉", "📈", "🔝", "⏱", "⏸", "💧")) assertFalse(text.contains(e))
    }

    @Test
    fun `wellbeing card has no per-line emoji`() {
        val strings = listOf("app/src/main/res/values/strings.xml", "src/main/res/values/strings.xml")
            .map(::File).first { it.exists() }.readText()
        val lines = strings.lines().filter { it.contains("wellbeing_") }
        assertTrue(lines.isNotEmpty())
        for (line in lines) for (e in listOf("💧", "🚶", "🌿")) assertFalse(line, line.contains(e))
        val card = source("WellbeingCard.kt")
        for (e in listOf("💧", "🚶", "🌿")) assertFalse(card.contains(e))
    }

    @Test
    fun `week card keeps one heading icon and indents wrapped lines`() {
        val card = source("WeekCard.kt")
        assertTrue(card.contains("override val emoji: String = \"📊\""))
        assertTrue(card.contains("LeadingMarginSpan.Standard(0, indent)"))
    }
}
