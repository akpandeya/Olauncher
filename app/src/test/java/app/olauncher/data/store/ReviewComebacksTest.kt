package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReviewComebacksTest {

    // A fixed non-DST zone keeps the day arithmetic readable; the DST test below
    // proves the bucketing itself is zone-correct.
    private val zone = ZoneId.of("UTC")

    private fun event(reviewedAt: Long, id: Long = reviewedAt) =
        ReviewEvent(id = id, wordId = 1, reviewedAt = reviewedAt, result = "knew", boxBefore = 1, boxAfter = 2)

    /** Epoch millis for a local date at noon UTC — safely inside the day in any zone. */
    private fun millis(date: LocalDate): Long =
        date.atTime(12, 0).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()

    @Test
    fun `studying the day after is not a comeback`() {
        val events = listOf(event(millis(LocalDate.of(2026, 1, 1))))
        val today = LocalDate.of(2026, 1, 2)
        assertFalse(ReviewComebacks.isComeback(events, today, zone))
    }

    @Test
    fun `a two-day jump is a comeback`() {
        val events = listOf(event(millis(LocalDate.of(2026, 1, 1))))
        val today = LocalDate.of(2026, 1, 3)
        assertTrue(ReviewComebacks.isComeback(events, today, zone))
    }

    @Test
    fun `first-ever study day is not a comeback`() {
        assertFalse(ReviewComebacks.isComeback(emptyList(), LocalDate.of(2026, 1, 5), zone))
    }

    @Test
    fun `studying again the same day is not a comeback`() {
        val today = LocalDate.of(2026, 1, 5)
        val events = listOf(event(millis(today)))
        assertFalse(ReviewComebacks.isComeback(events, today, zone))
    }

    @Test
    fun `count tallies every qualifying return`() {
        // Study days: 1st, 2nd (no gap), 5th (+3, comeback), 6th, 9th (+3, comeback).
        val days = listOf(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 2),
            LocalDate.of(2026, 1, 5),
            LocalDate.of(2026, 1, 6),
            LocalDate.of(2026, 1, 9),
        )
        val events = days.mapIndexed { index, day -> event(millis(day), id = index.toLong() + 1) }
        assertEquals(2, ReviewComebacks.count(events, LocalDate.of(2026, 1, 9), zone))
    }

    @Test
    fun `count includes today as a study day`() {
        // 1st studied; returning on the 4th is comeback #1.
        val events = listOf(event(millis(LocalDate.of(2026, 1, 1))))
        assertEquals(1, ReviewComebacks.count(events, LocalDate.of(2026, 1, 4), zone))
    }

    @Test
    fun `count on an empty log is zero no matter the gap`() {
        assertEquals(0, ReviewComebacks.count(emptyList(), LocalDate.of(2026, 1, 4), zone))
    }

    @Test
    fun `logged-days count skips the not-yet-studied today the session count includes`() {
        // Last study day 3 days back, today unstudied: the panel counts only what
        // happened; the post-session summary counts today's hypothetical return.
        val events = listOf(event(millis(LocalDate.of(2026, 1, 1))))
        val today = LocalDate.of(2026, 1, 4)
        assertEquals(0, ReviewComebacks.count(events, zone))
        assertEquals(1, ReviewComebacks.count(events, today, zone))
    }

    @Test
    fun `events on the same local day count once for presence`() {
        // Morning and evening of one day: still a single study day.
        val zone = ZoneId.of("Europe/Berlin")
        val day = LocalDate.of(2026, 1, 1)
        val morning = day.atTime(6, 0).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()
        val evening = day.atTime(22, 0).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()
        val events = listOf(event(morning), event(evening))
        assertEquals(listOf(day), ReviewComebacks.studyDays(events, zone))
    }

    @Test
    fun `bucketing survives a DST boundary`() {
        // Europe/Berlin, 2026: DST ends Oct 25 (clocks 03:00 -> 02:00). Reviews at
        // fixed UTC instants on both sides must still land on their local dates.
        val zone = ZoneId.of("Europe/Berlin")
        val before = LocalDate.of(2026, 10, 24)
        val after = LocalDate.of(2026, 10, 26)
        val events = listOf(
            event(before.atTime(12, 0).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()),
            event(after.atTime(12, 0).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()),
        )
        // A 2-day jump across the DST change: still a comeback. isComeback takes the
        // pre-session log, so the event on `after` itself is excluded there.
        assertEquals(listOf(before, after), ReviewComebacks.studyDays(events, zone))
        assertTrue(ReviewComebacks.isComeback(events.take(1), after, zone))
        assertEquals(1, ReviewComebacks.count(events, after, zone))
    }
}
