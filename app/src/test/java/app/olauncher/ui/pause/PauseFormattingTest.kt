package app.olauncher.ui.pause

import org.junit.Assert.assertEquals
import org.junit.Test

class PauseFormattingTest {

    @Test
    fun `formatDuration formats zero and negative values as 0 min`() {
        assertEquals("0 min", PauseFormatting.formatDuration(0L))
        assertEquals("0 min", PauseFormatting.formatDuration(-5000L))
    }

    @Test
    fun `formatDuration formats less than one minute as less than 1 min`() {
        assertEquals("<1 min", PauseFormatting.formatDuration(10_000L))
        assertEquals("<1 min", PauseFormatting.formatDuration(59_000L))
    }

    @Test
    fun `formatDuration formats minutes under one hour`() {
        assertEquals("1 min", PauseFormatting.formatDuration(60_000L))
        assertEquals("38 min", PauseFormatting.formatDuration(38 * 60 * 1000L))
        assertEquals("59 min", PauseFormatting.formatDuration(59 * 60 * 1000L))
    }

    @Test
    fun `formatDuration formats exact hours`() {
        assertEquals("1h", PauseFormatting.formatDuration(60 * 60 * 1000L))
        assertEquals("2h", PauseFormatting.formatDuration(120 * 60 * 1000L))
    }

    @Test
    fun `formatDuration formats hours and remaining minutes`() {
        assertEquals("1h 15m", PauseFormatting.formatDuration(75 * 60 * 1000L))
        assertEquals("2h 5m", PauseFormatting.formatDuration(125 * 60 * 1000L))
    }

    @Test
    fun `formatTimes formats singular and plural counts`() {
        assertEquals("0 times", PauseFormatting.formatTimes(0))
        assertEquals("1 time", PauseFormatting.formatTimes(1))
        assertEquals("2 times", PauseFormatting.formatTimes(2))
        assertEquals("4 times", PauseFormatting.formatTimes(4))
    }

    @Test
    fun `formatUsageStatsLine matches expected acceptance string with usage access`() {
        val result = PauseFormatting.formatUsageStatsLine(4, 38 * 60 * 1000L)
        assertEquals("Opened 4 times today · 38 min", result)
    }

    @Test
    fun `formatUsageStatsLine formats singular and edge durations`() {
        assertEquals(
            "Opened 1 time today · 38 min",
            PauseFormatting.formatUsageStatsLine(1, 38 * 60 * 1000L)
        )
        assertEquals(
            "Opened 0 times today · 0 min",
            PauseFormatting.formatUsageStatsLine(0, 0L)
        )
        assertEquals(
            "Opened 2 times today · <1 min",
            PauseFormatting.formatUsageStatsLine(2, 30_000L)
        )
        assertEquals(
            "Opened 5 times today · 1h 20m",
            PauseFormatting.formatUsageStatsLine(5, 80 * 60 * 1000L)
        )
    }

    @Test
    fun `formatThumblineOpensLine matches expected acceptance string without usage access`() {
        val result = PauseFormatting.formatThumblineOpensLine(2)
        assertEquals("Opened 2 times from Thumbline today", result)
    }

    @Test
    fun `formatThumblineOpensLine formats singular and zero counts`() {
        assertEquals(
            "Opened 1 time from Thumbline today",
            PauseFormatting.formatThumblineOpensLine(1)
        )
        assertEquals(
            "Opened 0 times from Thumbline today",
            PauseFormatting.formatThumblineOpensLine(0)
        )
    }
}
