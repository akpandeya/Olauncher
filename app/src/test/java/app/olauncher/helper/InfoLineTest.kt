package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Test

class InfoLineTest {

    @Test
    fun allParts() {
        assertEquals(
            "Mon 19 Oct · 🌦 12° · 82% · 2h 11m",
            InfoLine.compose("Mon 19 Oct", "🌦 12°", "82%", "2h 11m")
        )
    }

    @Test
    fun dateOnly() {
        assertEquals("Mon 19 Oct", InfoLine.compose("Mon 19 Oct"))
    }

    @Test
    fun skipsMissingMiddlePart() {
        assertEquals("Mon 19 Oct · 82%", InfoLine.compose("Mon 19 Oct", null, "82%", null))
        assertEquals("Mon 19 Oct · 2h", InfoLine.compose("Mon 19 Oct", null, null, "2h"))
        assertEquals("Mon 19 Oct · 12° · 2h", InfoLine.compose("Mon 19 Oct", "12°", null, "2h"))
    }

    @Test
    fun blankPartsAreSkipped() {
        assertEquals("Mon 19 Oct · 82%", InfoLine.compose("Mon 19 Oct", "  ", "82%", ""))
    }

    @Test
    fun blankDateHasNoLeadingSeparator() {
        assertEquals("12° · 82%", InfoLine.compose("", "12°", "82%", null))
    }
}
