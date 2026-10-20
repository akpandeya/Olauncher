package app.olauncher.helper

import android.media.session.PlaybackState
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

    @Test
    fun `buffering connecting and skipping count as playing`() {
        listOf(
            PlaybackState.STATE_PLAYING,
            PlaybackState.STATE_BUFFERING,
            PlaybackState.STATE_CONNECTING,
            PlaybackState.STATE_SKIPPING_TO_NEXT,
            PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
            PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM,
        ).forEach { assertEquals("state $it", Transport.PLAYING, MediaPicker.transportOf(it)) }
    }

    @Test
    fun `paused stays paused and stopped or unknown is other`() {
        assertEquals(Transport.PAUSED, MediaPicker.transportOf(PlaybackState.STATE_PAUSED))
        assertEquals(Transport.OTHER, MediaPicker.transportOf(PlaybackState.STATE_STOPPED))
        assertEquals(Transport.OTHER, MediaPicker.transportOf(PlaybackState.STATE_NONE))
        assertEquals(Transport.OTHER, MediaPicker.transportOf(PlaybackState.STATE_ERROR))
        assertEquals(Transport.OTHER, MediaPicker.transportOf(null))
    }

    @Test
    fun `buffering session is shown as playing`() {
        val buffering = MediaPicker.transportOf(PlaybackState.STATE_BUFFERING)
        val info = MediaPicker.pick(listOf(session("yt", buffering)), emptyMap(), now)
        assertEquals("yt", info?.packageName)
        assertTrue(info!!.playing)
    }
}
