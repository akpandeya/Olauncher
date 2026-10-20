package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicPulseTest {

    @Test
    fun positionAdvancesWithClockWhilePlaying() {
        // Reported 10 s in at t=1000 ms; 4 s later at 1x speed it should be 14 s.
        val position = MusicPulse.positionMs(10_000L, 1_000L, 5_000L, 1f, true, 200_000L)
        assertEquals(14_000L, position)
    }

    @Test
    fun positionFollowsPlaybackSpeed() {
        val position = MusicPulse.positionMs(10_000L, 1_000L, 5_000L, 2f, true, 200_000L)
        assertEquals(18_000L, position)
    }

    @Test
    fun pausedPositionDoesNotMove() {
        val position = MusicPulse.positionMs(10_000L, 1_000L, 5_000L, 1f, false, 200_000L)
        assertEquals(10_000L, position)
    }

    @Test
    fun unreportedPositionIsUsedAsIs() {
        val position = MusicPulse.positionMs(7_000L, 0L, 9_000_000L, 1f, true, 200_000L)
        assertEquals(7_000L, position)
    }

    @Test
    fun positionIsClampedToDuration() {
        val position = MusicPulse.positionMs(195_000L, 1_000L, 60_000L, 1f, true, 200_000L)
        assertEquals(200_000L, position)
    }

    @Test
    fun clockGoingBackwardsDoesNotRewind() {
        val position = MusicPulse.positionMs(10_000L, 9_000L, 5_000L, 1f, true, 200_000L)
        assertEquals(10_000L, position)
    }

    @Test
    fun progressIsFractionOfDuration() {
        val fraction = MusicPulse.progress(50_000L, 0L, 0L, 1f, false, 200_000L)
        assertEquals(0.25f, fraction!!, 0.0001f)
    }

    @Test
    fun progressIsNullWhenDurationUnknown() {
        assertNull(MusicPulse.progress(50_000L, 0L, 0L, 1f, true, 0L))
        assertNull(MusicPulse.progress(50_000L, 0L, 0L, 1f, true, -1L))
    }

    @Test
    fun progressStaysWithinZeroAndOne() {
        val fraction = MusicPulse.progress(0L, 0L, 0L, 1f, false, 1_000L)
        assertEquals(0f, fraction!!, 0.0001f)
        val end = MusicPulse.progress(5_000L, 1_000L, 900_000L, 1f, true, 1_000L)
        assertEquals(1f, end!!, 0.0001f)
    }

    @Test
    fun glowPrefersMostPopulousSaturatedSwatch() {
        val muted = SwatchColor(0xFF888888.toInt(), population = 900, saturation = 0.02f, lightness = 0.5f)
        val teal = SwatchColor(0xFF1A9E8F.toInt(), population = 300, saturation = 0.6f, lightness = 0.4f)
        val pink = SwatchColor(0xFFE05A9A.toInt(), population = 120, saturation = 0.7f, lightness = 0.6f)
        assertEquals(0xFF1A9E8F.toInt(), MusicPulse.glowColor(listOf(muted, teal, pink)))
    }

    @Test
    fun glowSkipsNearBlackAndNearWhite() {
        val black = SwatchColor(0xFF050510.toInt(), population = 1000, saturation = 0.8f, lightness = 0.03f)
        val white = SwatchColor(0xFFFFF8F0.toInt(), population = 800, saturation = 0.5f, lightness = 0.97f)
        val blue = SwatchColor(0xFF2E5BBA.toInt(), population = 50, saturation = 0.5f, lightness = 0.45f)
        assertEquals(0xFF2E5BBA.toInt(), MusicPulse.glowColor(listOf(black, white, blue)))
    }

    @Test
    fun glowFallsBackToGreyWhenNothingSaturated() {
        val grey = SwatchColor(0xFF777777.toInt(), population = 400, saturation = 0.05f, lightness = 0.46f)
        assertEquals(0xFF777777.toInt(), MusicPulse.glowColor(listOf(grey)))
    }

    @Test
    fun glowIsNullWithNoUsableSwatch() {
        assertNull(MusicPulse.glowColor(emptyList()))
        val black = SwatchColor(0xFF000000.toInt(), population = 10, saturation = 0f, lightness = 0f)
        assertNull(MusicPulse.glowColor(listOf(black)))
    }

    @Test
    fun androidArtworkSchemesAreLoadable() {
        assertTrue(MusicPulse.isLoadableArtwork("https://lh3.googleusercontent.com/art=w544"))
        assertTrue(MusicPulse.isLoadableArtwork("HTTP://example.com/a.jpg"))
        assertTrue(MusicPulse.isLoadableArtwork("content://media/external/images/1"))
        assertTrue(MusicPulse.isLoadableArtwork("android.resource://app.olauncher/1"))
        assertTrue(MusicPulse.isLoadableArtwork("file:///sdcard/a.jpg"))
        assertFalse(MusicPulse.isLoadableArtwork("ftp://example.com/a.jpg"))
        assertFalse(MusicPulse.isLoadableArtwork(""))
        assertFalse(MusicPulse.isLoadableArtwork(null))
    }

    @Test
    fun artworkUriTakesFirstFetchableCandidate() {
        // Album art is a non-loadable scheme here, so the web art URI after it is used.
        val uri = MusicPulse.loadableArtworkUri(
            "ftp://media/1", "  https://yt.example/art.jpg  ", "https://yt.example/icon.jpg",
        )
        assertEquals("https://yt.example/art.jpg", uri)
        // A content:// link is loadable and wins over later ones.
        assertEquals("content://media/1", MusicPulse.loadableArtworkUri("content://media/1", "https://yt.example/a.jpg"))
    }

    @Test
    fun artworkUriIsNullWhenNoCandidateIsWeb() {
        assertNull(MusicPulse.loadableArtworkUri(null, "", "ftp://media/2"))
        assertNull(MusicPulse.loadableArtworkUri())
    }

    @Test
    fun glowStrengthIsClampedToZeroToHundred() {
        assertEquals(0, MusicPulse.clampGlowStrength(-5))
        assertEquals(100, MusicPulse.clampGlowStrength(250))
        assertEquals(42, MusicPulse.clampGlowStrength(42))
    }

    @Test
    fun defaultGlowStrengthKeepsTheOriginalCentreAlpha() {
        assertEquals(0xAA, MusicPulse.glowCentreAlpha(MusicPulse.GLOW_STRENGTH_DEFAULT))
    }

    @Test
    fun glowCentreAlphaMapsPercentToByte() {
        assertEquals(0, MusicPulse.glowCentreAlpha(0))
        assertEquals(127, MusicPulse.glowCentreAlpha(50))
        assertEquals(0xFF, MusicPulse.glowCentreAlpha(100))
        assertEquals(0xFF, MusicPulse.glowCentreAlpha(140))
        assertEquals(0, MusicPulse.glowCentreAlpha(-20))
    }

    @Test
    fun zeroGlowStrengthHidesTheGlow() {
        assertFalse(MusicPulse.isGlowVisible(0))
        assertFalse(MusicPulse.isGlowVisible(-3))
        assertTrue(MusicPulse.isGlowVisible(1))
    }

    @Test
    fun defaultGlowPulseRangeIsTheOriginalBreathing() {
        val range = MusicPulse.glowPulseRange(MusicPulse.GLOW_STRENGTH_DEFAULT)
        assertEquals(0.35f, range.low, 0.0001f)
        assertEquals(0.8f, range.high, 0.0001f)
    }

    @Test
    fun weakerGlowPulseKeepsTheSameRatio() {
        val range = MusicPulse.glowPulseRange(33)
        assertTrue(range.high < 0.8f)
        assertEquals(0.35f / 0.8f, range.low / range.high, 0.0001f)
    }

    @Test
    fun strongestGlowPulseIsCappedAtFullAlpha() {
        val range = MusicPulse.glowPulseRange(100)
        assertEquals(1f, range.high, 0.0001f)
        assertEquals(0.35f * 100f / 67f, range.low, 0.0001f)
        assertTrue(range.low < range.high)
    }

    @Test
    fun glowCacheKeyIsStableForTheSameTrackAndArt() {
        val first = MusicPulse.glowCacheKey("com.yt", "Core", "Dhadkan", "https://art/1", 544, 544)
        val again = MusicPulse.glowCacheKey("com.yt", "Core", "Dhadkan", "https://art/1", 544, 544)
        assertEquals(first, again)
    }

    @Test
    fun glowCacheKeyChangesWhenArtArrives() {
        val before = MusicPulse.glowCacheKey("com.yt", "Core", "Dhadkan", "https://art/1", 0, 0)
        val after = MusicPulse.glowCacheKey("com.yt", "Core", "Dhadkan", "https://art/1", 544, 544)
        assertNotEquals(before, after)
    }

    @Test
    fun glowCacheKeyChangesForEachField() {
        val base = MusicPulse.glowCacheKey("com.yt", "Core", "Dhadkan", "https://art/1", 544, 544)
        assertNotEquals(base, MusicPulse.glowCacheKey("com.sp", "Core", "Dhadkan", "https://art/1", 544, 544))
        assertNotEquals(base, MusicPulse.glowCacheKey("com.yt", "Other", "Dhadkan", "https://art/1", 544, 544))
        assertNotEquals(base, MusicPulse.glowCacheKey("com.yt", "Core", "Aditya", "https://art/1", 544, 544))
        assertNotEquals(base, MusicPulse.glowCacheKey("com.yt", "Core", "Dhadkan", "https://art/2", 544, 544))
        assertNotEquals(base, MusicPulse.glowCacheKey("com.yt", "Core", "Dhadkan", "https://art/1", 300, 300))
    }
}
