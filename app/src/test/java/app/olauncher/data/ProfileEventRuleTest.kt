package app.olauncher.data

import app.olauncher.glance.CalendarEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProfileEventRuleTest {

    private val now = 1_000_000L

    private fun event(title: String = "Standup", cal: Long = 7L, start: Long = now - 10, end: Long = now + 10, allDay: Boolean = false) =
        CalendarEvent(1L, title, start, end, allDay, cal)

    @Test
    fun calendarIdMatchesOnlyThatCalendar() {
        val rule = ProfileEventRule("focus", calendarId = 7L)
        assertTrue(rule.matches(event(cal = 7L), now))
        assertFalse(rule.matches(event(cal = 8L), now))
    }

    @Test
    fun keywordIsCaseInsensitiveSubstring() {
        val rule = ProfileEventRule("focus", keyword = "stand")
        assertTrue(rule.matches(event("Daily STANDUP"), now))
        assertFalse(rule.matches(event("Lunch"), now))
    }

    @Test
    fun calendarAndKeywordMustBothMatch() {
        val rule = ProfileEventRule("focus", calendarId = 7L, keyword = "deep")
        assertTrue(rule.matches(event("Deep work", cal = 7L), now))
        assertFalse(rule.matches(event("Deep work", cal = 8L), now))
        assertFalse(rule.matches(event("Lunch", cal = 7L), now))
    }

    @Test
    fun allDayEventsIgnoredByDefault() {
        val allDay = event(allDay = true)
        assertFalse(ProfileEventRule("focus", calendarId = 7L).matches(allDay, now))
        assertTrue(ProfileEventRule("focus", calendarId = 7L, includeAllDay = true).matches(allDay, now))
    }

    @Test
    fun onlyRunningEventsMatch() {
        val rule = ProfileEventRule("focus", calendarId = 7L)
        assertFalse(rule.matches(event(start = now + 1, end = now + 50), now))
        assertFalse(rule.matches(event(start = now - 50, end = now), now))
        assertTrue(rule.matches(event(start = now, end = now + 1), now))
    }

    @Test
    fun ruleNeedsCalendarOrKeyword() {
        try {
            ProfileEventRule("focus", keyword = "  ")
            fail()
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun missingCalendarAccessSkipsEventRules() {
        val schedule = ProfileSchedule("day", emptyList(), listOf(ProfileEventRule("focus", calendarId = 7L)))
        assertNull(schedule.resolveEvent(null, now))
        assertEquals("focus", schedule.resolveEvent(listOf(event()), now))
    }

    @Test
    fun laterEventRuleWins() {
        val schedule = ProfileSchedule(
            "day", emptyList(),
            listOf(ProfileEventRule("a", calendarId = 7L), ProfileEventRule("b", keyword = "stand")),
        )
        assertEquals("b", schedule.resolveEvent(listOf(event()), now))
    }

    @Test
    fun eventRuleBeatsTimeRuleAndNullFallsBackToTime() {
        val time = ProfileRule("evening", 0, 0, ProfileSchedule.EVERY_DAY)
        val schedule = ProfileSchedule("day", listOf(time), listOf(ProfileEventRule("focus", calendarId = 7L)))
        fun active(events: List<CalendarEvent>?) = ProfileScheduleResolver.activeProfileId(
            schedule, 600, 1, false, null, runningEvents = events, nowMillis = now,
        )
        assertEquals("focus", active(listOf(event())))
        assertEquals("evening", active(listOf(event(cal = 9L))))
        assertEquals("evening", active(null))
    }

    @Test
    fun jsonRoundTripAndOldJsonStillLoads() {
        val schedule = ProfileSchedule(
            "day", listOf(ProfileRule("focus", 540, 1020, setOf(1, 2))),
            listOf(ProfileEventRule("focus", calendarId = 7L, keyword = "Work", includeAllDay = true), ProfileEventRule("rich", keyword = "x")),
        )
        assertEquals(schedule, ProfileSchedule.fromJson(schedule.toJson()))
        val old = ProfileSchedule("day", listOf(ProfileRule("focus", 540, 1020, setOf(1))))
        assertFalse(old.toJson().contains("eventRules"))
        assertEquals(old, ProfileSchedule.fromJson(old.toJson()))
    }

    @Test
    fun badEventRuleJsonIsRejected() {
        val bad = """{"default":"day","rules":[],"eventRules":[{"profile":"a","keyword":"","includeAllDay":false}]}"""
        try {
            ProfileSchedule.fromJson(bad)
            fail()
        } catch (_: ProfileFormatException) {
        }
    }
}
