package app.olauncher.wellbeing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class HealthSummaryTest {

    private val labels = HealthLabels("%1\$s steps", "Slept %1\$s", "Resting HR %1\$s", "Exercise %1\$s min", "Heart rate %1\$s")

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

    @Test
    fun averageBpmRoundsHalfUp() {
        assertEquals(61L, HealthSummaryFormatting.averageBpm(listOf(60, 61)))
        assertEquals(61L, HealthSummaryFormatting.averageBpm(listOf(60, 61, 61)))
        assertEquals(3L, HealthSummaryFormatting.averageBpm(listOf(2, 3)))
        assertNull(HealthSummaryFormatting.averageBpm(emptyList()))
    }

    @Test
    fun heartRateComesAfterRestingHr() {
        val line = HealthSummaryFormatting.formatLine(
            HealthSummary(steps = 100, restingHeartRate = 58, exerciseMinutes = 30, heartRate = 72),
            labels,
        )
        assertEquals("100 steps · Resting HR 58 · Heart rate 72 · Exercise 30 min", line)
    }

    @Test
    fun heartRateAloneGivesOnlyThatPart() {
        assertEquals("Heart rate 72", HealthSummaryFormatting.formatLine(HealthSummary(heartRate = 72), labels))
    }

    @Test
    fun noHeartRateLeavesLineUnchanged() {
        val line = HealthSummaryFormatting.formatLine(HealthSummary(steps = 100, restingHeartRate = 58), labels)
        assertEquals("100 steps · Resting HR 58", line)
    }

    @Test
    fun sleepNightEndingTodayReturnsNightWithBounds() {
        val session = SleepSpan(dayStart - 1 * hour, dayStart + 7 * hour)
        val night = HealthSummaryFormatting.sleepNightEndingToday(listOf(session), dayStart, dayEnd)
        assertNotNull(night)
        assertEquals(480L, night?.minutes)
        assertEquals(dayStart - 1 * hour, night?.startMs)
        assertEquals(dayStart + 7 * hour, night?.endMs)
    }

    @Test
    fun sleepAcrossMidnightIn7DayBucketing() {
        val zone = ZoneOffset.UTC
        val startDay = LocalDate.of(2026, 10, 18)
        val days = (0L..6L).map { startDay.plusDays(it) }

        // Day 1 start is at 2026-10-19T00:00:00Z.
        // Sleep session starts 2026-10-18T23:00:00Z and ends 2026-10-19T07:00:00Z.
        val day1StartMs = days[1].atStartOfDay(zone).toInstant().toEpochMilli()
        val sleepSpan = SleepSpan(day1StartMs - hour, day1StartMs + 7 * hour)

        val summary = HealthSummaryFormatting.bucketAndAverageWeek(
            days = days,
            zone = zone,
            exerciseSessions = emptyList(),
            restingRecords = emptyList(),
            sleepRecords = listOf(sleepSpan),
        )

        // Day 0 has no sleep ending on it
        assertNull(summary.days[0].sleep)
        // Day 1 has the sleep that started before midnight
        val day1Sleep = summary.days[1].sleep
        assertNotNull(day1Sleep)
        assertEquals(480L, day1Sleep?.minutes)
        assertEquals(480L, summary.sleepMinutesAvg)
        assertEquals(LocalTime.of(23, 0), summary.sleepBedTime)
        assertEquals(LocalTime.of(7, 0), summary.sleepWakeTime)
    }

    @Test
    fun emptyDaysIn7DayBucketingReturnsNullAverages() {
        val zone = ZoneOffset.UTC
        val days = (0L..6L).map { LocalDate.of(2026, 10, 18).plusDays(it) }

        val summary = HealthSummaryFormatting.bucketAndAverageWeek(
            days = days,
            zone = zone,
            exerciseSessions = emptyList(),
            restingRecords = emptyList(),
            sleepRecords = emptyList(),
        )

        assertNull("Empty exercise records give null average", summary.exerciseMinutesAvg)
        assertNull("Empty resting HR records give null average", summary.restingHeartRateAvg)
        assertNull("Empty sleep records give null average", summary.sleepMinutesAvg)
        assertNull(summary.sleepBedTime)
        assertNull(summary.sleepWakeTime)
        assertEquals(7, summary.days.size)
        assertTrue(summary.days.all { it.exerciseMinutes == 0L })
        assertTrue(summary.days.all { it.restingHeartRate == null })
        assertTrue(summary.days.all { it.sleep == null })
    }

    @Test
    fun nullInputsWhenNotPermittedOrOffReturnsNullPerType() {
        val zone = ZoneOffset.UTC
        val days = (0L..6L).map { LocalDate.of(2026, 10, 18).plusDays(it) }

        val summary = HealthSummaryFormatting.bucketAndAverageWeek(
            days = days,
            zone = zone,
            exerciseSessions = null,
            restingRecords = null,
            sleepRecords = null,
        )

        assertNull(summary.exerciseMinutesAvg)
        assertNull(summary.exerciseMinutesTotal)
        assertNull(summary.restingHeartRateAvg)
        assertNull(summary.sleepMinutesAvg)
        assertTrue(summary.days.all { it.exerciseMinutes == null })
        assertTrue(summary.days.all { it.restingHeartRate == null })
        assertTrue(summary.days.all { it.sleep == null })
    }

    @Test
    fun partialDaysBucketingAndAverages() {
        val zone = ZoneOffset.UTC
        val startDay = LocalDate.of(2026, 10, 18)
        val days = (0L..6L).map { startDay.plusDays(it) }

        val d0Start = days[0].atStartOfDay(zone).toInstant().toEpochMilli()
        val d2Start = days[2].atStartOfDay(zone).toInstant().toEpochMilli()

        // 35 min on day 0, 70 min on day 2 = 105 min total across 7 days -> 105 / 7 = 15 min / day
        val exercises = listOf(
            ExerciseSpan(d0Start + 10 * hour, d0Start + 10 * hour + 35 * min),
            ExerciseSpan(d2Start + 14 * hour, d2Start + 15 * hour + 10 * min),
        )

        // Resting HR on day 0 (58) and day 1 (62) -> average 60
        val resting = listOf(
            RestingSample(d0Start + 8 * hour, 58),
            RestingSample(d0Start + 24 * hour + 8 * hour, 62),
        )

        // Sleep ending on day 0 (420 min) and day 1 (480 min) -> average 450 min
        val sleep = listOf(
            SleepSpan(d0Start - 1 * hour, d0Start + 6 * hour), // 7 h = 420 min (bed 23:00, wake 06:00)
            SleepSpan(d0Start + 23 * hour + 30 * min, d0Start + 31 * hour + 30 * min), // 8 h = 480 min (bed 23:30, wake 07:30)
        )

        val summary = HealthSummaryFormatting.bucketAndAverageWeek(
            days = days,
            zone = zone,
            exerciseSessions = exercises,
            restingRecords = resting,
            sleepRecords = sleep,
        )

        assertEquals(15L, summary.exerciseMinutesAvg)
        assertEquals(105L, summary.exerciseMinutesTotal)
        assertEquals(60L, summary.restingHeartRateAvg)
        assertEquals(450L, summary.sleepMinutesAvg)
        assertEquals(LocalTime.of(23, 15), summary.sleepBedTime)
        assertEquals(LocalTime.of(6, 45), summary.sleepWakeTime)
        assertEquals("23:15 – 06:45", HealthSummaryFormatting.formatBedWakeTime(summary.sleepBedTime!!, summary.sleepWakeTime!!))
    }

    @Test
    fun averageBedAndWakeTimesAcrossMidnight() {
        val zone = ZoneOffset.UTC
        val baseMs = 1_000_000_000_000L

        // Night 1: bed 23:30, wake 07:00
        val n1 = SleepNight(
            minutes = 450,
            startMs = LocalDate.of(2026, 1, 1).atTime(23, 30).atZone(zone).toInstant().toEpochMilli(),
            endMs = LocalDate.of(2026, 1, 2).atTime(7, 0).atZone(zone).toInstant().toEpochMilli(),
        )
        // Night 2: bed 00:30, wake 08:00
        val n2 = SleepNight(
            minutes = 450,
            startMs = LocalDate.of(2026, 1, 3).atTime(0, 30).atZone(zone).toInstant().toEpochMilli(),
            endMs = LocalDate.of(2026, 1, 3).atTime(8, 0).atZone(zone).toInstant().toEpochMilli(),
        )

        val (bed, wake) = HealthSummaryFormatting.averageBedAndWakeTimes(listOf(n1, n2), zone)
        assertEquals(LocalTime.of(0, 0), bed)
        assertEquals(LocalTime.of(7, 30), wake)
        assertEquals("00:00 – 07:30", HealthSummaryFormatting.formatBedWakeTime(bed, wake))
    }

    @Test
    fun dashConstantIsEmDash() {
        assertEquals("—", HealthSummaryFormatting.DASH)
    }
}
