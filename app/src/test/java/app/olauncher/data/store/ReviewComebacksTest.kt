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
    fun `one study-free day is not a comeback`() {
        // Studied the 1st, skipped the 2nd, back on the 3rd: gap of 1.
        val events = listOf(event(millis(LocalDate.of(2026, 1, 1))))
        assertFalse(ReviewComebacks.isComeback(events, LocalDate.of(2026, 1, 3), zone))
    }

    @Test
    fun `two study-free days make a comeback`() {
        // Studied the 1st, skipped the 2nd and 3rd, back on the 4th: gap of 2.
        val events = listOf(event(millis(LocalDate.of(2026, 1, 1))))
        assertTrue(ReviewComebacks.isComeback(events, LocalDate.of(2026, 1, 4), zone))
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

    private fun local(zone: ZoneId, date: LocalDate, hour: Int, minute: Int): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `local midnight events on either side of a DST switch bucket by local day`() {
        // Europe/Berlin, DST ends Oct 25 2026. 00:30 local on the 24th is 22:30 UTC
        // on the 23rd (CEST, +2); 00:30 local on the 26th is 23:30 UTC on the 25th
        // (CET, +1). UTC bucketing would yield the 23rd and 25th.
        val zone = ZoneId.of("Europe/Berlin")
        val events = listOf(
            event(local(zone, LocalDate.of(2026, 10, 24), 0, 30)),
            event(local(zone, LocalDate.of(2026, 10, 26), 0, 30)),
        )
        assertEquals(
            listOf(LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 26)),
            ReviewComebacks.studyDays(events, zone),
        )
        // Local gap is 1 free day (25th): not a comeback. A UTC count (23rd -> 25th)
        // is also 1, so also check the today-based path below.
        assertEquals(0, ReviewComebacks.count(events, zone))
    }

    @Test
    fun `comeback decision uses local days across a DST switch`() {
        // Last study: 00:30 local Oct 24 = Oct 23 22:30 UTC. Today (local) Oct 26.
        // Local: 25th is the only free day -> no comeback. UTC days would be the 23rd
        // to the 26th = 2 free days -> a wrong comeback.
        val zone = ZoneId.of("Europe/Berlin")
        val events = listOf(event(local(zone, LocalDate.of(2026, 10, 24), 0, 30)))
        assertFalse(ReviewComebacks.isComeback(events, LocalDate.of(2026, 10, 26), zone))
        assertTrue(ReviewComebacks.isComeback(events, LocalDate.of(2026, 10, 27), zone))
    }

    @Test
    fun `23_30 and 00_30 around local midnight are two study days`() {
        // After the switch (CET): 23:30 on Oct 25 and 00:30 on Oct 26 share a UTC day
        // (Oct 25) but are two local days.
        val zone = ZoneId.of("Europe/Berlin")
        val events = listOf(
            event(local(zone, LocalDate.of(2026, 10, 25), 23, 30)),
            event(local(zone, LocalDate.of(2026, 10, 26), 0, 30)),
        )
        assertEquals(2, ReviewComebacks.studyDays(events, zone).size)
    }
}
