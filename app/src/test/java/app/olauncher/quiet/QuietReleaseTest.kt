package app.olauncher.quiet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class QuietReleaseTest {

    private val zoneUtc = ZoneId.of("UTC")
    private val zoneBerlin = ZoneId.of("Europe/Berlin")
    private val zoneNewYork = ZoneId.of("America/New_York")

    private fun epochMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        zone: ZoneId = zoneUtc,
    ): Long {
        return LocalDateTime.of(year, month, day, hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }

    private fun heldItem(
        pkg: String,
        label: String = "",
        key: String = "key_${pkg}_${System.nanoTime()}",
        postTime: Long = 1000L,
    ): HeldItem {
        return HeldItem(
            key = key,
            pkg = pkg,
            label = label,
            title = "Title",
            text = "Text",
            postTime = postTime,
        )
    }

    // --- nextRelease tests ---

    @Test
    fun `nextRelease throws when times list is empty`() {
        assertThrows(IllegalArgumentException::class.java) {
            QuietRelease.nextRelease(System.currentTimeMillis(), zoneUtc, emptyList())
        }
    }

    @Test
    fun `nextRelease returns earlier slot today when now is before first slot`() {
        // 12:30 (750) and 18:30 (1110)
        val times = listOf(750, 1110)
        val now = epochMillis(2026, 10, 19, 10, 0, zoneUtc)
        val expected = epochMillis(2026, 10, 19, 12, 30, zoneUtc)

        val next = QuietRelease.nextRelease(now, zoneUtc, times)
        assertEquals(expected, next)
    }

    @Test
    fun `nextRelease returns second slot today when now is strictly at first slot`() {
        val times = listOf(750, 1110)
        val now = epochMillis(2026, 10, 19, 12, 30, zoneUtc)
        val expected = epochMillis(2026, 10, 19, 18, 30, zoneUtc)

        val next = QuietRelease.nextRelease(now, zoneUtc, times)
        assertEquals(expected, next)
    }

    @Test
    fun `nextRelease returns second slot today when now is between slots`() {
        val times = listOf(750, 1110)
        val now = epochMillis(2026, 10, 19, 15, 45, zoneUtc)
        val expected = epochMillis(2026, 10, 19, 18, 30, zoneUtc)

        val next = QuietRelease.nextRelease(now, zoneUtc, times)
        assertEquals(expected, next)
    }

    @Test
    fun `nextRelease rolls to tomorrow's first slot when now is strictly at last slot`() {
        val times = listOf(750, 1110)
        val now = epochMillis(2026, 10, 19, 18, 30, zoneUtc)
        val expected = epochMillis(2026, 10, 20, 12, 30, zoneUtc)

        val next = QuietRelease.nextRelease(now, zoneUtc, times)
        assertEquals(expected, next)
    }

    @Test
    fun `nextRelease rolls to tomorrow's first slot when now is after last slot today`() {
        val times = listOf(750, 1110)
        val now = epochMillis(2026, 10, 19, 21, 0, zoneUtc)
        val expected = epochMillis(2026, 10, 20, 12, 30, zoneUtc)

        val next = QuietRelease.nextRelease(now, zoneUtc, times)
        assertEquals(expected, next)
    }

    @Test
    fun `nextRelease sorts input times regardless of parameter order`() {
        // Passed unsorted: 18:30 (1110) before 12:30 (750)
        val times = listOf(1110, 750)
        val now = epochMillis(2026, 10, 19, 9, 30, zoneUtc)
        val expected = epochMillis(2026, 10, 19, 12, 30, zoneUtc)

        val next = QuietRelease.nextRelease(now, zoneUtc, times)
        assertEquals(expected, next)
    }

    @Test
    fun `nextRelease works with a single time entry`() {
        val times = listOf(1110) // 18:30 only
        val nowMorning = epochMillis(2026, 10, 19, 9, 0, zoneUtc)
        val expectedToday = epochMillis(2026, 10, 19, 18, 30, zoneUtc)
        assertEquals(expectedToday, QuietRelease.nextRelease(nowMorning, zoneUtc, times))

        val nowEvening = epochMillis(2026, 10, 19, 19, 0, zoneUtc)
        val expectedTomorrow = epochMillis(2026, 10, 20, 18, 30, zoneUtc)
        assertEquals(expectedTomorrow, QuietRelease.nextRelease(nowEvening, zoneUtc, times))
    }

    @Test
    fun `nextRelease works with three time entries`() {
        val times = listOf(540, 750, 1110) // 09:00, 12:30, 18:30
        val nowBeforeFirst = epochMillis(2026, 10, 19, 7, 0, zoneUtc)
        assertEquals(epochMillis(2026, 10, 19, 9, 0, zoneUtc), QuietRelease.nextRelease(nowBeforeFirst, zoneUtc, times))

        val nowMidDay = epochMillis(2026, 10, 19, 10, 0, zoneUtc)
        assertEquals(epochMillis(2026, 10, 19, 12, 30, zoneUtc), QuietRelease.nextRelease(nowMidDay, zoneUtc, times))

        val nowAfternoon = epochMillis(2026, 10, 19, 14, 0, zoneUtc)
        assertEquals(epochMillis(2026, 10, 19, 18, 30, zoneUtc), QuietRelease.nextRelease(nowAfternoon, zoneUtc, times))

        val nowNight = epochMillis(2026, 10, 19, 23, 0, zoneUtc)
        assertEquals(epochMillis(2026, 10, 20, 9, 0, zoneUtc), QuietRelease.nextRelease(nowNight, zoneUtc, times))
    }

    @Test
    fun `nextRelease is DST-safe across daylight saving spring forward and fall back`() {
        // Berlin springs forward on 2026-03-29 (02:00 -> 03:00)
        val times = listOf(750, 1110) // 12:30, 18:30
        val nowBeforeDst = epochMillis(2026, 3, 28, 20, 0, zoneBerlin)
        val nextDst = QuietRelease.nextRelease(nowBeforeDst, zoneBerlin, times)
        val expectedDst = epochMillis(2026, 3, 29, 12, 30, zoneBerlin)
        assertEquals(expectedDst, nextDst)
        assertTrue(nextDst > nowBeforeDst)

        // Berlin falls back on 2026-10-25 (03:00 -> 02:00)
        val nowBeforeFall = epochMillis(2026, 10, 24, 20, 0, zoneBerlin)
        val nextFall = QuietRelease.nextRelease(nowBeforeFall, zoneBerlin, times)
        val expectedFall = epochMillis(2026, 10, 25, 12, 30, zoneBerlin)
        assertEquals(expectedFall, nextFall)
        assertTrue(nextFall > nowBeforeFall)
    }

    // --- summary tests ---

    @Test
    fun `summary returns empty string when items list is empty`() {
        assertEquals("", QuietRelease.summary(emptyList()))
    }

    @Test
    fun `summary formats single held item`() {
        val items = listOf(
            heldItem(pkg = "com.instagram.android", label = "Instagram")
        )
        assertEquals("1 held · Instagram 1", QuietRelease.summary(items))
    }

    @Test
    fun `summary formats multiple items from single app`() {
        val items = listOf(
            heldItem(pkg = "com.instagram.android", label = "Instagram"),
            heldItem(pkg = "com.instagram.android", label = "Instagram"),
            heldItem(pkg = "com.instagram.android", label = "Instagram"),
        )
        assertEquals("3 held · Instagram 3", QuietRelease.summary(items))
    }

    @Test
    fun `summary formats two apps matching acceptance criteria example`() {
        // "6 held · Instagram 4, YouTube 2"
        val items = listOf(
            heldItem(pkg = "com.instagram.android", label = "Instagram"),
            heldItem(pkg = "com.instagram.android", label = "Instagram"),
            heldItem(pkg = "com.instagram.android", label = "Instagram"),
            heldItem(pkg = "com.instagram.android", label = "Instagram"),
            heldItem(pkg = "com.google.android.youtube", label = "YouTube"),
            heldItem(pkg = "com.google.android.youtube", label = "YouTube"),
        )
        assertEquals("6 held · Instagram 4, YouTube 2", QuietRelease.summary(items))
    }

    @Test
    fun `summary formats three apps without remaining suffix`() {
        val items = listOf(
            heldItem(pkg = "com.instagram.android", label = "Instagram"),
            heldItem(pkg = "com.instagram.android", label = "Instagram"),
            heldItem(pkg = "com.instagram.android", label = "Instagram"),
            heldItem(pkg = "com.instagram.android", label = "Instagram"),
            heldItem(pkg = "com.google.android.youtube", label = "YouTube"),
            heldItem(pkg = "com.google.android.youtube", label = "YouTube"),
            heldItem(pkg = "com.reddit.frontpage", label = "Reddit"),
        )
        assertEquals("7 held · Instagram 4, YouTube 2, Reddit 1", QuietRelease.summary(items))
    }

    @Test
    fun `summary formats top 3 apps and plus 1 more when 4 apps present`() {
        val items = listOf(
            heldItem(pkg = "com.instagram.android", label = "Instagram"),
            heldItem(pkg = "com.instagram.android", label = "Instagram"),
            heldItem(pkg = "com.instagram.android", label = "Instagram"),
            heldItem(pkg = "com.instagram.android", label = "Instagram"),
            heldItem(pkg = "com.google.android.youtube", label = "YouTube"),
            heldItem(pkg = "com.google.android.youtube", label = "YouTube"),
            heldItem(pkg = "com.google.android.youtube", label = "YouTube"),
            heldItem(pkg = "com.reddit.frontpage", label = "Reddit"),
            heldItem(pkg = "com.reddit.frontpage", label = "Reddit"),
            heldItem(pkg = "com.twitter.android", label = "Twitter"),
        )
        // Instagram 4, YouTube 3, Reddit 2 -> top 3, 1 remaining
        assertEquals("10 held · Instagram 4, YouTube 3, Reddit 2, +1 more", QuietRelease.summary(items))
    }

    @Test
    fun `summary formats top 3 apps and plus 2 more when 5 apps present`() {
        val items = listOf(
            heldItem(pkg = "com.instagram.android", label = "Instagram"),
            heldItem(pkg = "com.instagram.android", label = "Instagram"),
            heldItem(pkg = "com.instagram.android", label = "Instagram"),
            heldItem(pkg = "com.instagram.android", label = "Instagram"),
            heldItem(pkg = "com.instagram.android", label = "Instagram"),
            heldItem(pkg = "com.google.android.youtube", label = "YouTube"),
            heldItem(pkg = "com.google.android.youtube", label = "YouTube"),
            heldItem(pkg = "com.google.android.youtube", label = "YouTube"),
            heldItem(pkg = "com.reddit.frontpage", label = "Reddit"),
            heldItem(pkg = "com.reddit.frontpage", label = "Reddit"),
            heldItem(pkg = "com.twitter.android", label = "Twitter"),
            heldItem(pkg = "com.slack", label = "Slack"),
        )
        // Instagram 5, YouTube 3, Reddit 2 -> top 3, 2 remaining (+2 more)
        assertEquals("12 held · Instagram 5, YouTube 3, Reddit 2, +2 more", QuietRelease.summary(items))
    }

    @Test
    fun `summary breaks ties between apps by alphabetical name`() {
        val items = listOf(
            heldItem(pkg = "com.google.android.youtube", label = "YouTube"),
            heldItem(pkg = "com.google.android.youtube", label = "YouTube"),
            heldItem(pkg = "com.instagram.android", label = "Instagram"),
            heldItem(pkg = "com.instagram.android", label = "Instagram"),
        )
        // Both have count 2; Instagram comes before YouTube alphabetically
        assertEquals("4 held · Instagram 2, YouTube 2", QuietRelease.summary(items))
    }

    @Test
    fun `summary falls back to package name when label is empty or blank`() {
        val items = listOf(
            heldItem(pkg = "com.example.app", label = ""),
            heldItem(pkg = "com.example.app", label = "   "),
        )
        assertEquals("2 held · com.example.app 2", QuietRelease.summary(items))
    }

    // --- parsing and formatting tests ---

    @Test
    fun `parseTimes parses valid comma-separated minutes`() {
        assertEquals(listOf(750, 1110), QuietRelease.parseTimes("750,1110"))
        assertEquals(listOf(540, 750, 1110), QuietRelease.parseTimes("540, 750, 1110"))
        assertEquals(listOf(750), QuietRelease.parseTimes("750"))
    }

    @Test
    fun `parseTimes falls back to default on empty or invalid input`() {
        assertEquals(listOf(750, 1110), QuietRelease.parseTimes(""))
        assertEquals(listOf(750, 1110), QuietRelease.parseTimes(null))
        assertEquals(listOf(750, 1110), QuietRelease.parseTimes("   "))
        assertEquals(listOf(750, 1110), QuietRelease.parseTimes("invalid,abc"))
        assertEquals(listOf(750, 1110), QuietRelease.parseTimes("-5, 2000"))
    }

    @Test
    fun `parseTimes sorts, deduplicates, and caps at 3 entries`() {
        assertEquals(listOf(750, 1110), QuietRelease.parseTimes("1110,750"))
        assertEquals(listOf(750, 1110), QuietRelease.parseTimes("750,750,1110"))
        assertEquals(listOf(100, 200, 300), QuietRelease.parseTimes("300, 100, 400, 200"))
    }

    @Test
    fun `formatTimes formats valid list to comma-separated string`() {
        assertEquals("750,1110", QuietRelease.formatTimes(listOf(750, 1110)))
        assertEquals("540,750,1110", QuietRelease.formatTimes(listOf(1110, 540, 750)))
        assertEquals("750", QuietRelease.formatTimes(listOf(750)))
        assertEquals("750,1110", QuietRelease.formatTimes(emptyList()))
    }

    @Test
    fun `formatTime formats minute of day to HH mm`() {
        assertEquals("00:00", QuietRelease.formatTime(0))
        assertEquals("09:05", QuietRelease.formatTime(9 * 60 + 5))
        assertEquals("12:30", QuietRelease.formatTime(750))
        assertEquals("18:30", QuietRelease.formatTime(1110))
        assertEquals("23:59", QuietRelease.formatTime(1439))
    }

    @Test
    fun `formatReleaseTimesRow joins formatted times with middle dot`() {
        assertEquals("12:30 · 18:30", QuietRelease.formatReleaseTimesRow(listOf(750, 1110)))
        assertEquals("09:00 · 12:30 · 18:30", QuietRelease.formatReleaseTimesRow(listOf(540, 750, 1110)))
        assertEquals("18:30", QuietRelease.formatReleaseTimesRow(listOf(1110)))
    }
}
