package app.olauncher.wellbeing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class PauseSnoozeTest {
    private val zone = ZoneId.of("Europe/Berlin")

    private fun at(text: String): Long = ZonedDateTime.parse("$text[Europe/Berlin]").toInstant().toEpochMilli()

    @Test
    fun `one hour adds exactly an hour`() {
        assertEquals(5_000L + 3_600_000L, PauseSnooze.until(PauseSnooze.Choice.HOUR, 5_000L, zone))
    }

    @Test
    fun `tomorrow before 04 00 is the same day 04 00`() {
        val now = at("2026-10-19T02:30:00+02:00")
        assertEquals(at("2026-10-19T04:00:00+02:00"), PauseSnooze.until(PauseSnooze.Choice.TOMORROW, now, zone))
    }

    @Test
    fun `tomorrow after 04 00 is the next day 04 00`() {
        val now = at("2026-10-19T15:40:00+02:00")
        assertEquals(at("2026-10-20T04:00:00+02:00"), PauseSnooze.until(PauseSnooze.Choice.TOMORROW, now, zone))
    }

    @Test
    fun `tomorrow at exactly 04 00 moves to the next day`() {
        val now = at("2026-10-19T04:00:00+02:00")
        assertEquals(at("2026-10-20T04:00:00+02:00"), PauseSnooze.until(PauseSnooze.Choice.TOMORROW, now, zone))
    }

    @Test
    fun `tomorrow across the clock change still lands on 04 00 local`() {
        val now = at("2026-10-24T20:00:00+02:00")
        assertEquals(at("2026-10-25T04:00:00+01:00"), PauseSnooze.until(PauseSnooze.Choice.TOMORROW, now, zone))
    }

    @Test
    fun `isActive only before the end time`() {
        assertTrue(PauseSnooze.isActive(100L, 99L))
        assertFalse(PauseSnooze.isActive(100L, 100L))
        assertFalse(PauseSnooze.isActive(100L, 101L))
        assertFalse(PauseSnooze.isActive(0L, 0L))
    }

    @Test
    fun `formatUntil is HH mm local`() {
        assertEquals("15:40", PauseSnooze.formatUntil(at("2026-10-19T15:40:00+02:00"), zone))
    }

    @Test
    fun `encoding round trips`() {
        val map = mapOf("com.a.b" to 123L, "com.c.d" to 456L)
        assertEquals(map, PauseSnooze.decode(PauseSnooze.encode(map)))
    }

    @Test
    fun `decode drops malformed entries`() {
        val decoded = PauseSnooze.decode(
            setOf(
                "com.ok|100", "nopipe", "|100", "com.x|", "com.x|abc", "com.x|-5", "com.x|0",
                "com.x|1|2", "com.x|1.5", "com.x| 5", "com y|5",
            )
        )
        assertEquals(mapOf("com.ok" to 100L), decoded)
    }

    @Test
    fun `encode skips invalid entries`() {
        assertEquals(setOf("com.ok|7"), PauseSnooze.encode(mapOf("com.ok" to 7L, "" to 1L, "a|b" to 1L, "com.z" to 0L)))
    }

    @Test
    fun `launch skips pause while global snooze is active then resumes`() {
        val apps = setOf("com.a")
        assertFalse(DistractingApps.shouldPauseLaunch("com.a", true, apps, snoozeUntil = 200L, now = 100L))
        assertTrue(DistractingApps.shouldPauseLaunch("com.a", true, apps, snoozeUntil = 200L, now = 200L))
    }

    @Test
    fun `launch skips pause only for the snoozed app`() {
        val apps = setOf("com.a", "com.b")
        val snoozes = mapOf("com.a" to 200L)
        assertFalse(DistractingApps.shouldPauseLaunch("com.a", true, apps, appSnoozes = snoozes, now = 100L))
        assertTrue(DistractingApps.shouldPauseLaunch("com.b", true, apps, appSnoozes = snoozes, now = 100L))
        assertTrue(DistractingApps.shouldPauseLaunch("com.a", true, apps, appSnoozes = snoozes, now = 300L))
    }
}
