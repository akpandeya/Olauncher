package app.olauncher.wellbeing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HealthSummaryTest {

    private val labels = HealthLabels("%1\$s steps", "Slept %1\$s", "Resting HR %1\$s", "Exercise %1\$s min")

    private val hour = 3_600_000L
    private val min = 60_000L
    private val dayStart = 100 * 24 * hour
    private val dayEnd = dayStart + 24 * hour

    @Test
    fun allParts() {
        val s = HealthSummary(steps = 6240, sleepMinutes = 430, restingHeartRate = 58, exerciseMinutes = 30)
        assertEquals("6,240 steps · Slept 7 h 10 · Resting HR 58 · Exercise 30 min", HealthSummaryFormatting.formatLine(s, labels))
    }

    @Test
    fun someMissing() {
        val s = HealthSummary(steps = 1200, restingHeartRate = 61)
        assertEquals("1,200 steps · Resting HR 61", HealthSummaryFormatting.formatLine(s, labels))
        assertEquals("Slept 45 min", HealthSummaryFormatting.formatLine(HealthSummary(sleepMinutes = 45), labels))
        assertEquals("Slept 8 h", HealthSummaryFormatting.formatLine(HealthSummary(sleepMinutes = 480), labels))
    }

    @Test
    fun noPartsGivesNull() {
        assertNull(HealthSummaryFormatting.formatLine(HealthSummary(), labels))
        assertNull(HealthSummaryFormatting.formatLine(HealthSummary(steps = 0, exerciseMinutes = 0), labels))
    }

    @Test
    fun sleepSpanningMidnightCountsForSessionEndingToday() {
        val lastNight = SleepSpan(dayStart - 1 * hour, dayStart + 6 * hour + 10 * min)
        assertEquals(430L, HealthSummaryFormatting.sleepMinutesEndingToday(listOf(lastNight), dayStart, dayEnd))
    }

    @Test
    fun sleepEndedYesterdayOrStartsTomorrowIsIgnored() {
        val old = SleepSpan(dayStart - 9 * hour, dayStart - 1 * min)
        val next = SleepSpan(dayEnd - hour, dayEnd + 7 * hour)
        assertNull(HealthSummaryFormatting.sleepMinutesEndingToday(listOf(old, next), dayStart, dayEnd))
    }

    @Test
    fun exerciseIsTotalOfSessions() {
        assertEquals(50L, HealthSummaryFormatting.exerciseMinutesTotal(listOf(30, 20)))
        assertNull(HealthSummaryFormatting.exerciseMinutesTotal(emptyList()))
    }
}
