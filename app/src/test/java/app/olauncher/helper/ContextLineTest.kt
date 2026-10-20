package app.olauncher.helper

import app.olauncher.glance.CalendarEvent
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContextLineTest {

    private val now = Instant.parse("2026-10-19T10:00:00Z")
    private fun at(minutes: Long) = now.plusSeconds(minutes * 60)
    private fun event(startMin: Long, endMin: Long, title: String = "Standup", allDay: Boolean = false, id: Long = 7L) =
        CalendarEvent(id, title, at(startMin), at(endMin), allDay)

    private val playing = MediaInfo("Song", "Artist", "com.music", true)

    @Test
    fun `playing media wins over an event`() {
        val item = ContextLine.pick(playing, event(5, 35), now)
        assertEquals(ContextItem.Media("▶ Song — Artist", "com.music", "Song", true), item)
    }

    @Test
    fun `media without artist shows only title`() {
        val item = ContextLine.pick(playing.copy(artist = null), null, now)
        assertEquals("▶ Song", item?.text)
    }

    @Test
    fun `paused media shows pause marker and wins over an event`() {
        val item = ContextLine.pick(playing.copy(playing = false), event(8, 40), now)
        assertEquals(ContextItem.Media("❚❚ Song — Artist", "com.music", "Song", false), item)
    }

    @Test
    fun `no media and no event hides the line`() {
        assertNull(ContextLine.pick(null, null, now))
    }

    @Test
    fun `event within 30 minutes shows minutes, rounded up`() {
        val e = CalendarEvent(1L, "Standup", now.plusSeconds(8 * 60 + 10), at(60))
        assertEquals("Standup in 9 min", ContextLine.pick(null, e, now)?.text)
    }

    @Test
    fun `event exactly 30 minutes away shows, 31 does not`() {
        assertEquals("Standup in 30 min", ContextLine.pick(null, event(30, 60), now)?.text)
        assertNull(ContextLine.pick(null, event(31, 60), now))
    }

    @Test
    fun `running event shows now`() {
        assertEquals("Standup now", ContextLine.pick(null, event(-10, 20), now)?.text)
        assertEquals("Standup now", ContextLine.pick(null, event(0, 20), now)?.text)
    }

    @Test
    fun `ended event and all-day event are ignored`() {
        assertNull(ContextLine.pick(null, event(-30, 0), now))
        assertNull(ContextLine.pick(null, event(-30, 600, allDay = true), now))
    }

    @Test
    fun `custom labels are used`() {
        val labels = ContextLabels(inMinutes = "%2\$d min: %1\$s", now = "Jetzt %1\$s")
        assertEquals("5 min: Standup", ContextLine.pick(null, event(5, 20), now, labels)?.text)
        assertEquals("Jetzt Standup", ContextLine.pick(null, event(-5, 20), now, labels)?.text)
    }

    @Test
    fun `relevantEvent skips all-day and far events`() {
        val list = listOf(event(-60, 600, "Holiday", allDay = true, id = 1), event(90, 120, "Later", id = 2), event(10, 40, "Soon", id = 3))
        assertEquals(3L, ContextLine.relevantEvent(list, now)?.id)
        assertNull(ContextLine.relevantEvent(list.take(2), now))
    }
}
