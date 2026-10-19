package app.olauncher.ui.today.cards

import app.olauncher.wellbeing.AppMinutes
import app.olauncher.wellbeing.ReviewLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekCardEmojiTest {

    @Test
    fun `each review line has its own emoji`() {
        val lines = listOf(
            ReviewLine.Distracting(10, 2) to "📱",
            ReviewLine.LessThanWeekBefore(10) to "📉",
            ReviewLine.MoreThanWeekBefore(10) to "📈",
            ReviewLine.TopApps(listOf(AppMinutes("A", 5))) to "🔝",
            ReviewLine.OverAllowance(2, 7) to "⏱",
            ReviewLine.Pause(1, 1, null) to "⏸",
            ReviewLine.Nudges(1, 1) to "💧",
        )
        for ((line, emoji) in lines) assertEquals(emoji, WeekCardText.emoji(line))
        assertEquals(lines.size, lines.map { WeekCardText.emoji(it.first) }.toSet().size)
        assertTrue(WeekCardText.emoji(ReviewLine.Nudges(0, 1)).isNotEmpty())
    }
}
