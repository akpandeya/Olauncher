package app.olauncher.wellbeing

import app.olauncher.ui.today.cards.HeatRowGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class HourlyUsageTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val date = LocalDate.of(2026, 10, 24)
    private val distracting = setOf("com.example.distracting")

    private fun timeMs(hour: Int, minute: Int, d: LocalDate = date, z: ZoneId = zone): Long =
        d.atTime(hour, minute).atZone(z).toInstant().toEpochMilli()

    @Test
    fun `boundary split splits session across an hour boundary`() {
        // Session from 10:45 to 11:20 (35 minutes total: 15 min in hour 10, 20 min in hour 11)
        val events = listOf(
            UsageEventRecord("com.example.distracting", timeMs(10, 45), eventType = 1), // RESUMED
            UsageEventRecord("com.example.distracting", timeMs(11, 20), eventType = 2), // PAUSED
        )
        val now = timeMs(12, 0)
        val buckets = HourlyUsage.bucketEvents(events, distracting, date, zone, now)

        assertEquals(24, buckets.size)
        assertEquals(15, buckets[10])
        assertEquals(20, buckets[11])
        assertEquals(0, buckets[9])
        assertEquals(0, buckets[12])
    }

    @Test
    fun `boundary split splits session across multiple hour boundaries`() {
        // Session from 09:30 to 12:15 (30m in h9, 60m in h10, 60m in h11, 15m in h12)
        val events = listOf(
            UsageEventRecord("com.example.distracting", timeMs(9, 30), eventType = 1),
            UsageEventRecord("com.example.distracting", timeMs(12, 15), eventType = 2),
        )
        val now = timeMs(13, 0)
        val buckets = HourlyUsage.bucketEvents(events, distracting, date, zone, now)

        assertEquals(30, buckets[9])
        assertEquals(60, buckets[10])
        assertEquals(60, buckets[11])
        assertEquals(15, buckets[12])
        assertEquals(0, buckets[8])
        assertEquals(0, buckets[13])
    }

    @Test
    fun `open session at now is counted up to now and future hours remain empty`() {
        // Resumed at 14:10, no subsequent pause event. Current time 'now' is 14:35.
        val events = listOf(
            UsageEventRecord("com.example.distracting", timeMs(14, 10), eventType = 1),
        )
        val now = timeMs(14, 35)
        val buckets = HourlyUsage.bucketEvents(events, distracting, date, zone, now)

        assertEquals(25, buckets[14])
        for (h in 15..23) {
            assertEquals("Hour $h must be empty for open session at 14:35", 0, buckets[h])
        }
    }

    @Test
    fun `open session at now spanning hour boundary splits between hours`() {
        // Resumed at 13:45, no subsequent pause event. Current time 'now' is 14:20.
        val events = listOf(
            UsageEventRecord("com.example.distracting", timeMs(13, 45), eventType = 1),
        )
        val now = timeMs(14, 20)
        val buckets = HourlyUsage.bucketEvents(events, distracting, date, zone, now)

        assertEquals(15, buckets[13])
        assertEquals(20, buckets[14])
        for (h in 15..23) {
            assertEquals("Hour $h must be empty", 0, buckets[h])
        }
    }

    @Test
    fun `DST fall back day handles 25-hour day and session during transition`() {
        // In Europe/Berlin on 2026-10-25:
        // Clocks fall back 1 hour from 03:00 CEST (+02:00) to 02:00 CET (+01:00).
        val dstDate = LocalDate.of(2026, 10, 25)

        // Session 1: 02:40 CEST to 02:20 CET (40 minutes duration across transition)
        // 02:40 CEST is +02:00:
        val startCest = ZonedDateTime.of(2026, 10, 25, 2, 40, 0, 0, zone).withEarlierOffsetAtOverlap().toInstant().toEpochMilli()
        // 02:20 CET is +01:00 (after transition):
        val endCet = ZonedDateTime.of(2026, 10, 25, 2, 20, 0, 0, zone).withLaterOffsetAtOverlap().toInstant().toEpochMilli()

        val events = listOf(
            UsageEventRecord("com.example.distracting", startCest, eventType = 1),
            UsageEventRecord("com.example.distracting", endCet, eventType = 2),
        )
        val now = ZonedDateTime.of(2026, 10, 25, 5, 0, 0, 0, zone).toInstant().toEpochMilli()
        val buckets = HourlyUsage.bucketEvents(events, distracting, dstDate, zone, now)

        // Both parts of the session occurred at clock hour 2 (20m CEST + 20m CET = 40m)
        assertEquals(40, buckets[2])

        // Session 2: late evening at 23:10 to 23:45 on fall back day
        val lateStart = ZonedDateTime.of(2026, 10, 25, 23, 10, 0, 0, zone).toInstant().toEpochMilli()
        val lateEnd = ZonedDateTime.of(2026, 10, 25, 23, 45, 0, 0, zone).toInstant().toEpochMilli()
        val lateEvents = listOf(
            UsageEventRecord("com.example.distracting", lateStart, eventType = 1),
            UsageEventRecord("com.example.distracting", lateEnd, eventType = 2),
        )
        val lateNow = ZonedDateTime.of(2026, 10, 25, 23, 59, 0, 0, zone).toInstant().toEpochMilli()
        val lateBuckets = HourlyUsage.bucketEvents(lateEvents, distracting, dstDate, zone, lateNow)

        assertEquals("Hour 23 must be index 23 without out-of-bounds error on 25h day", 35, lateBuckets[23])
    }

    @Test
    fun `DST spring forward day handles 23-hour day and skipped hour`() {
        // In Europe/Berlin on 2026-03-29:
        // Clocks skip from 02:00 CET (+01:00) to 03:00 CEST (+02:00). Hour 2 does not exist on clock.
        val dstSpringDate = LocalDate.of(2026, 3, 29)

        // Session from 01:50 CET to 03:20 CEST (30 minutes duration total)
        val startMs = ZonedDateTime.of(2026, 3, 29, 1, 50, 0, 0, zone).toInstant().toEpochMilli()
        val endMs = ZonedDateTime.of(2026, 3, 29, 3, 20, 0, 0, zone).toInstant().toEpochMilli()

        val events = listOf(
            UsageEventRecord("com.example.distracting", startMs, eventType = 1),
            UsageEventRecord("com.example.distracting", endMs, eventType = 2),
        )
        val now = ZonedDateTime.of(2026, 3, 29, 4, 0, 0, 0, zone).toInstant().toEpochMilli()
        val buckets = HourlyUsage.bucketEvents(events, distracting, dstSpringDate, zone, now)

        assertEquals(10, buckets[1])
        assertEquals("Skipped DST hour 2 must be 0", 0, buckets[2])
        assertEquals(20, buckets[3])
    }

    @Test
    fun `ignores non-distracting packages and merges overlapping distracting sessions`() {
        val nonDistracting = "com.example.productive"
        val distractingA = "com.distracting.a"
        val distractingB = "com.distracting.b"
        val distractingSet = setOf(distractingA, distractingB)

        val events = listOf(
            // Productive app from 10:00 to 11:00 (ignored)
            UsageEventRecord(nonDistracting, timeMs(10, 0), eventType = 1),
            UsageEventRecord(nonDistracting, timeMs(11, 0), eventType = 2),
            // Distracting app A from 10:10 to 10:30 (20 min)
            UsageEventRecord(distractingA, timeMs(10, 10), eventType = 1),
            UsageEventRecord(distractingA, timeMs(10, 30), eventType = 2),
            // Distracting app B from 10:20 to 10:45 (overlapping by 10 min, merged span is 10:10 to 10:45 = 35 min)
            UsageEventRecord(distractingB, timeMs(10, 20), eventType = 1),
            UsageEventRecord(distractingB, timeMs(10, 45), eventType = 2),
        )
        val now = timeMs(12, 0)
        val buckets = HourlyUsage.bucketEvents(events, distractingSet, date, zone, now)

        assertEquals(35, buckets[10])
    }

    @Test
    fun `session started before midnight only counts from start of today`() {
        // App paused at 00:20 without previous resume today (was open before midnight)
        val events = listOf(
            UsageEventRecord("com.example.distracting", timeMs(0, 20), eventType = 2),
        )
        val now = timeMs(1, 0)
        val buckets = HourlyUsage.bucketEvents(events, distracting, date, zone, now)

        assertEquals(20, buckets[0])
    }

    @Test
    fun `paused then stopped does not create a phantom session from midnight`() {
        val pkg = "com.example.distracting"
        val events = listOf(
            UsageEventRecord(pkg, timeMs(21, 0), eventType = 1),
            UsageEventRecord(pkg, timeMs(21, 15), eventType = 2),
            UsageEventRecord(pkg, timeMs(21, 15), eventType = 23),
        )
        val buckets = HourlyUsage.bucketEvents(events, distracting, date, zone, timeMs(22, 0))
        for (h in 0..23) {
            assertEquals("Hour $h", if (h == 21) 15 else 0, buckets[h])
        }
    }

    @Test
    fun `first event of day being stopped counts from midnight`() {
        val events = listOf(
            UsageEventRecord("com.example.distracting", timeMs(8, 0), eventType = 23),
        )
        val buckets = HourlyUsage.bucketEvents(events, distracting, date, zone, timeMs(9, 0))
        for (h in 0..23) {
            assertEquals("Hour $h", if (h in 0..7) 60 else 0, buckets[h])
        }
    }

    @Test
    fun `stopped after paused with earlier resume counts only one session`() {
        val pkg = "com.example.distracting"
        val events = listOf(
            UsageEventRecord(pkg, timeMs(10, 0), eventType = 1),
            UsageEventRecord(pkg, timeMs(10, 30), eventType = 2),
            UsageEventRecord(pkg, timeMs(10, 30), eventType = 23),
            UsageEventRecord(pkg, timeMs(14, 0), eventType = 1),
            UsageEventRecord(pkg, timeMs(14, 10), eventType = 2),
            UsageEventRecord(pkg, timeMs(14, 10), eventType = 23),
        )
        val sessions = HourlyUsage.extractSessions(events, distracting, timeMs(0, 0), timeMs(15, 0))
        assertEquals(2, sessions.size)
        val buckets = HourlyUsage.bucketEvents(events, distracting, date, zone, timeMs(15, 0))
        for (h in 0..23) {
            val expected = when (h) { 10 -> 30; 14 -> 10; else -> 0 }
            assertEquals("Hour $h", expected, buckets[h])
        }
    }

    @Test
    fun `empty events or empty distracting set returns 24 zero buckets`() {
        val emptyBuckets = HourlyUsage.bucketEvents(emptyList(), distracting, date, zone, timeMs(12, 0))
        assertEquals(24, emptyBuckets.size)
        assertTrue(emptyBuckets.all { it == 0 })

        val events = listOf(UsageEventRecord("com.example.distracting", timeMs(10, 0), eventType = 1))
        val noDistractingBuckets = HourlyUsage.bucketEvents(events, emptySet(), date, zone, timeMs(12, 0))
        assertEquals(24, noDistractingBuckets.size)
        assertTrue(noDistractingBuckets.all { it == 0 })
    }

    @Test
    fun `HeatRowGeometry computes 24 cells marking current hour and future hours`() {
        val hours = (0 until 24).map { if (it in 8..14) 15 else 0 }
        val currentHour = 14
        val cells = HeatRowGeometry.compute(
            hours = hours,
            currentHour = currentHour,
            width = 240f,
            density = 1f,
        )

        assertEquals(24, cells.size)

        // Check hour 10
        val cell10 = cells[10]
        assertEquals(10, cell10.hour)
        assertEquals(15, cell10.minutes)
        assertFalse(cell10.isCurrent)
        assertFalse(cell10.isFuture)

        // Check current hour (14)
        val cell14 = cells[14]
        assertEquals(14, cell14.hour)
        assertEquals(15, cell14.minutes)
        assertTrue(cell14.isCurrent)
        assertFalse(cell14.isFuture)

        // Check future hour (15)
        val cell15 = cells[15]
        assertEquals(15, cell15.hour)
        assertFalse(cell15.isCurrent)
        assertTrue(cell15.isFuture)
    }
}
