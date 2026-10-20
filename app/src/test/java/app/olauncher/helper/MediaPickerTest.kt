package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPickerTest {

    private val now = 10_000_000_000L
    private val min = 60_000L

    private fun session(pkg: String, transport: Transport, title: String? = "Song $pkg") =
        MediaSession(pkg, transport, title, "Artist")

    @Test
    fun `playing beats paused`() {
        val sessions = listOf(session("a", Transport.PAUSED), session("b", Transport.PLAYING))
        val info = MediaPicker.pick(sessions, mapOf("a" to now - min, "b" to now), now)
        assertEquals("b", info?.packageName)
        assertTrue(info!!.playing)
    }

    @Test
    fun `paused within 30 minutes is shown`() {
        val info = MediaPicker.pick(listOf(session("a", Transport.PAUSED)), mapOf("a" to now - 30 * min), now)
        assertEquals("a", info?.packageName)
        assertFalse(info!!.playing)
    }

    @Test
    fun `paused 31 minutes ago is hidden`() {
        assertNull(MediaPicker.pick(listOf(session("a", Transport.PAUSED)), mapOf("a" to now - 31 * min), now))
    }

    @Test
    fun `paused never seen playing is hidden`() {
        assertNull(MediaPicker.pick(listOf(session("a", Transport.PAUSED)), emptyMap(), now))
    }

    @Test
    fun `stopped is hidden`() {
        assertNull(MediaPicker.pick(listOf(session("a", Transport.OTHER)), mapOf("a" to now), now))
    }

    @Test
    fun `session without title is hidden`() {
        assertNull(MediaPicker.pick(listOf(session("a", Transport.PLAYING, title = null)), emptyMap(), now))
    }

    @Test
    fun `text for playing and paused`() {
        val playing = MediaInfo("Song", "Artist", "p", true)
        assertEquals("▶ Song — Artist", ContextLine.pick(playing, null, java.time.Instant.EPOCH)?.text)
        assertEquals("❚❚ Song — Artist", ContextLine.pick(playing.copy(playing = false), null, java.time.Instant.EPOCH)?.text)
    }
}
