package app.olauncher.ui.today.cards

import app.olauncher.quiet.HeldItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class HeldCardLogicTest {

    private val zoneUtc: ZoneId = ZoneId.of("UTC")
    private val zoneBerlin: ZoneId = ZoneId.of("Europe/Berlin")

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
        title: String = "",
        text: String = "",
        postTime: Long = 1000L,
        key: String = "key_${pkg}_${postTime}",
    ): HeldItem {
        return HeldItem(
            key = key,
            pkg = pkg,
            label = label,
            title = title,
            text = text,
            postTime = postTime,
        )
    }

    // --- isAvailable tests ---

    @Test
    fun `isAvailable returns true only when quiet is enabled and access is granted`() {
        assertTrue(HeldCardLogic.isAvailable(quietEnabled = true, accessGranted = true))
        assertFalse(HeldCardLogic.isAvailable(quietEnabled = false, accessGranted = true))
        assertFalse(HeldCardLogic.isAvailable(quietEnabled = true, accessGranted = false))
        assertFalse(HeldCardLogic.isAvailable(quietEnabled = false, accessGranted = false))
    }

    // --- groupAndSort tests ---

    @Test
    fun `groupAndSort returns empty list when input is empty`() {
        val result = HeldCardLogic.groupAndSort(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `groupAndSort prunes items older than 48 hours`() {
        val now = epochMillis(2026, 10, 19, 12, 0, zoneUtc)
        val fortyEightHoursMs = 48L * 60 * 60 * 1000
        val cutoff = now - fortyEightHoursMs

        val expiredItem = heldItem(
            pkg = "com.old.app",
            label = "OldApp",
            postTime = cutoff - 1000L,
        )
        val validItem = heldItem(
            pkg = "com.fresh.app",
            label = "FreshApp",
            postTime = cutoff + 1000L,
        )

        val result = HeldCardLogic.groupAndSort(
            items = listOf(expiredItem, validItem),
            nowMillis = now,
            cutoffMillis = cutoff,
        )

        assertEquals(1, result.size)
        assertEquals("com.fresh.app", result[0].pkg)
    }

    @Test
    fun `groupAndSort aggregates multiple items per package with latest title and text`() {
        val now = epochMillis(2026, 10, 19, 15, 0, zoneUtc)
        val time1 = epochMillis(2026, 10, 19, 10, 0, zoneUtc)
        val time2 = epochMillis(2026, 10, 19, 12, 0, zoneUtc)
        val time3 = epochMillis(2026, 10, 19, 14, 2, zoneUtc)

        val items = listOf(
            heldItem(pkg = "com.instagram.android", label = "Instagram", title = "Bob", text = "sent a photo", postTime = time1),
            heldItem(pkg = "com.instagram.android", label = "Instagram", title = "Charlie", text = "commented", postTime = time2),
            heldItem(pkg = "com.instagram.android", label = "Instagram", title = "Anna", text = "liked your photo", postTime = time3),
            heldItem(pkg = "com.instagram.android", label = "Instagram", title = "Old message", text = "hey", postTime = time1 - 1000L),
        )

        val result = HeldCardLogic.groupAndSort(items, nowMillis = now)
        assertEquals(1, result.size)
        val summary = result[0]
        assertEquals("com.instagram.android", summary.pkg)
        assertEquals("Instagram", summary.label)
        assertEquals(4, summary.count)
        assertEquals(time3, summary.latestPostTime)
        assertEquals("Anna", summary.latestTitle)
        assertEquals("liked your photo", summary.latestText)
    }

    @Test
    fun `groupAndSort orders most recent app first`() {
        val now = epochMillis(2026, 10, 19, 16, 0, zoneUtc)
        val timeIg = epochMillis(2026, 10, 19, 14, 2, zoneUtc)
        val timeYt = epochMillis(2026, 10, 19, 13, 30, zoneUtc)
        val timeWa = epochMillis(2026, 10, 19, 15, 10, zoneUtc)

        val items = listOf(
            heldItem(pkg = "com.instagram.android", label = "Instagram", postTime = timeIg),
            heldItem(pkg = "com.google.android.youtube", label = "YouTube", postTime = timeYt),
            heldItem(pkg = "com.whatsapp", label = "WhatsApp", postTime = timeWa),
        )

        val result = HeldCardLogic.groupAndSort(items, nowMillis = now)
        assertEquals(3, result.size)
        assertEquals("WhatsApp", result[0].label)
        assertEquals("Instagram", result[1].label)
        assertEquals("YouTube", result[2].label)
    }

    @Test
    fun `groupAndSort falls back to package name when label is blank`() {
        val now = epochMillis(2026, 10, 19, 12, 0, zoneUtc)
        val items = listOf(
            heldItem(pkg = "com.unknown.app", label = "", postTime = now - 1000L)
        )
        val result = HeldCardLogic.groupAndSort(items, nowMillis = now)
        assertEquals("com.unknown.app", result[0].label)
    }

    // --- formatTime tests ---

    @Test
    fun `formatTime formats postTime as HH mm in specified zone`() {
        val millis = epochMillis(2026, 10, 19, 14, 2, zoneUtc)
        assertEquals("14:02", HeldCardLogic.formatTime(millis, zoneUtc))
        assertEquals("16:02", HeldCardLogic.formatTime(millis, zoneBerlin))
    }

    // --- formatContent tests ---

    @Test
    fun `formatContent combines title and text or handles blanks`() {
        assertEquals("Anna liked your photo", HeldCardLogic.formatContent("Anna", "liked your photo"))
        assertEquals("Anna liked your photo", HeldCardLogic.formatContent("Anna liked your photo", ""))
        assertEquals("Anna liked your photo", HeldCardLogic.formatContent("", "Anna liked your photo"))
        assertEquals("", HeldCardLogic.formatContent("", ""))
        assertEquals("Title text on one line", HeldCardLogic.formatContent("Title\n", "text\ron one line"))
    }

    // --- formatAppLine tests ---

    @Test
    fun `formatAppLine matches acceptance criteria example`() {
        val postTime = epochMillis(2026, 10, 19, 14, 2, zoneUtc)
        val summary = HeldAppSummary(
            pkg = "com.instagram.android",
            label = "Instagram",
            count = 4,
            latestPostTime = postTime,
            latestTitle = "Anna",
            latestText = "liked your photo",
        )

        val line = HeldCardLogic.formatAppLine(summary, zoneUtc)
        assertEquals("Instagram · 4 · 14:02 Anna liked your photo", line)
    }

    @Test
    fun `formatAppLine formats without title or text`() {
        val postTime = epochMillis(2026, 10, 19, 14, 2, zoneUtc)
        val summary = HeldAppSummary(
            pkg = "com.instagram.android",
            label = "Instagram",
            count = 1,
            latestPostTime = postTime,
            latestTitle = "",
            latestText = "",
        )

        val line = HeldCardLogic.formatAppLine(summary, zoneUtc)
        assertEquals("Instagram · 1 · 14:02", line)
    }
}
