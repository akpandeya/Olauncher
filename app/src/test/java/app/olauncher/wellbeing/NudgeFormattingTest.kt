package app.olauncher.wellbeing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NudgeFormattingTest {

    @Test
    fun `water notification text matches specification format with done count`() {
        assertEquals("Time for some water · 0 today", NudgeFormatting.notificationText(Nudges.WATER, 0))
        assertEquals("Time for some water · 1 today", NudgeFormatting.notificationText(Nudges.WATER, 1))
        assertEquals("Time for some water · 3 today", NudgeFormatting.notificationText(Nudges.WATER, 3))
        assertEquals("Time for some water · 12 today", NudgeFormatting.notificationText(Nudges.WATER, 12))
    }

    @Test
    fun `walk notification text matches specification format with done count`() {
        assertEquals("Stand up and walk for a few minutes · 0 today", NudgeFormatting.notificationText(Nudges.WALK, 0))
        assertEquals("Stand up and walk for a few minutes · 1 today", NudgeFormatting.notificationText(Nudges.WALK, 1))
        assertEquals("Stand up and walk for a few minutes · 5 today", NudgeFormatting.notificationText(Nudges.WALK, 5))
    }

    @Test
    fun `notification text returns empty string for unknown type`() {
        assertEquals("", NudgeFormatting.notificationText("unknown", 1))
    }

    @Test
    fun `formatActiveHours formats default 0900 to 2100 with en-dash`() {
        // 540 = 09:00, 1260 = 21:00
        val formatted = NudgeFormatting.formatActiveHours(540, 1260)
        assertEquals("09:00–21:00", formatted)
    }

    @Test
    fun `formatActiveHours formats arbitrary times with zero padding`() {
        assertEquals("08:30–17:45", NudgeFormatting.formatActiveHours(8 * 60 + 30, 17 * 60 + 45))
        assertEquals("00:00–23:59", NudgeFormatting.formatActiveHours(0, 23 * 60 + 59))
        assertEquals("07:05–19:09", NudgeFormatting.formatActiveHours(7 * 60 + 5, 19 * 60 + 9))
    }

    @Test
    fun `formatWaterInterval formats all choices`() {
        assertEquals("Off", NudgeFormatting.formatWaterInterval(0))
        assertEquals("every 1 h", NudgeFormatting.formatWaterInterval(60))
        assertEquals("every 1.5 h", NudgeFormatting.formatWaterInterval(90))
        assertEquals("every 2 h", NudgeFormatting.formatWaterInterval(120))
        assertEquals("every 3 h", NudgeFormatting.formatWaterInterval(180))
        assertEquals("Off", NudgeFormatting.formatWaterInterval(-1))
    }

    @Test
    fun `formatWalkInterval formats all choices`() {
        assertEquals("Off", NudgeFormatting.formatWalkInterval(0))
        assertEquals("every 2 h", NudgeFormatting.formatWalkInterval(120))
        assertEquals("every 3 h", NudgeFormatting.formatWalkInterval(180))
        assertEquals("every 4 h", NudgeFormatting.formatWalkInterval(240))
        assertEquals("Off", NudgeFormatting.formatWalkInterval(-1))
    }

    @Test
    fun `interval options contain the specified values`() {
        assertEquals(listOf(0, 60, 90, 120, 180), NudgeFormatting.WATER_INTERVAL_OPTIONS_MIN)
        assertEquals(listOf(0, 120, 180, 240), NudgeFormatting.WALK_INTERVAL_OPTIONS_MIN)
    }

    @Test
    fun `isValidActiveHours enforces start strictly less than end`() {
        assertTrue(NudgeFormatting.isValidActiveHours(540, 1260))
        assertTrue(NudgeFormatting.isValidActiveHours(0, 1440))
        assertFalse(NudgeFormatting.isValidActiveHours(540, 540))
        assertFalse(NudgeFormatting.isValidActiveHours(1260, 540))
        assertFalse(NudgeFormatting.isValidActiveHours(-1, 540))
        assertFalse(NudgeFormatting.isValidActiveHours(540, 1441))
    }
}
