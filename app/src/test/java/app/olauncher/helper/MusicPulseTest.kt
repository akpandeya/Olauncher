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

    @Test
    fun artworkThatNeverFailedIsFetched() {
        assertTrue(MusicPulse.shouldFetchArtwork(null, 5_000L))
    }

    @Test
    fun failedArtworkWaitsAMinuteBeforeRetry() {
        val failedAt = 1_000_000L
        assertFalse(MusicPulse.shouldFetchArtwork(failedAt, failedAt))
        assertFalse(MusicPulse.shouldFetchArtwork(failedAt, failedAt + MusicPulse.ARTWORK_RETRY_MS - 1))
        assertTrue(MusicPulse.shouldFetchArtwork(failedAt, failedAt + MusicPulse.ARTWORK_RETRY_MS))
        assertTrue(MusicPulse.shouldFetchArtwork(failedAt, failedAt + 10 * MusicPulse.ARTWORK_RETRY_MS))
    }

    private fun swatch(argb: Int, population: Int, saturation: Float = 0.6f, lightness: Float = 0.5f) =
        SwatchColor(argb, population, saturation, lightness)

    @Test
    fun glowColorsStartWithTheSingleGlowColourThenFollowPopulation() {
        val swatches = listOf(
            swatch(0xFF110000.toInt(), 50),
            swatch(0xFF220000.toInt(), 400),
            swatch(0xFF330000.toInt(), 200),
            swatch(0xFF440000.toInt(), 100),
        )
        val colors = MusicPulse.glowColors(swatches)
        assertEquals(MusicPulse.glowColor(swatches), colors.first())
        assertEquals(listOf(0xFF220000.toInt(), 0xFF330000.toInt(), 0xFF440000.toInt()), colors)
    }

    @Test
    fun glowColorsDropSwatchesThatFailTheFilters() {
        val swatches = listOf(
            swatch(0xFF220000.toInt(), 400),
            swatch(0xFF010101.toInt(), 900, lightness = 0.05f),
            swatch(0xFFFEFEFE.toInt(), 800, lightness = 0.95f),
            swatch(0xFF808080.toInt(), 700, saturation = 0.05f),
            swatch(0xFF330000.toInt(), 0),
            swatch(0xFF440000.toInt(), 100),
        )
        assertEquals(listOf(0xFF220000.toInt(), 0xFF440000.toInt()), MusicPulse.glowColors(swatches))
    }

    @Test
    fun glowColorsFallBackToOneGreyWhenNothingIsSaturated() {
        val swatches = listOf(
            swatch(0xFF808080.toInt(), 700, saturation = 0.05f),
            swatch(0xFF606060.toInt(), 300, saturation = 0.02f),
        )
        assertEquals(listOf(0xFF808080.toInt()), MusicPulse.glowColors(swatches))
    }

    @Test
    fun glowColorsAreEmptyWithoutUsableSwatches() {
        assertTrue(MusicPulse.glowColors(emptyList()).isEmpty())
        assertTrue(MusicPulse.glowColors(listOf(swatch(0xFF010101.toInt(), 900, lightness = 0.01f))).isEmpty())
        assertTrue(MusicPulse.glowColors(listOf(swatch(0xFF220000.toInt(), 400)), max = 0).isEmpty())
    }

    @Test
    fun glowColorsSkipDuplicates() {
        val swatches = listOf(swatch(0xFF220000.toInt(), 400), swatch(0xFF220000.toInt(), 300), swatch(0xFF330000.toInt(), 100))
        assertEquals(listOf(0xFF220000.toInt(), 0xFF330000.toInt()), MusicPulse.glowColors(swatches))
    }

    @Test
    fun nextGlowColorIndexWrapsAround() {
        assertEquals(1, MusicPulse.nextGlowColorIndex(0, 3))
        assertEquals(2, MusicPulse.nextGlowColorIndex(1, 3))
        assertEquals(0, MusicPulse.nextGlowColorIndex(2, 3))
        assertEquals(0, MusicPulse.nextGlowColorIndex(0, 1))
        assertEquals(0, MusicPulse.nextGlowColorIndex(5, 0))
        assertEquals(1, MusicPulse.nextGlowColorIndex(3, 3))
    }

    @Test
    fun wavePhaseWrapsEachPeriod() {
        assertEquals(0f, MusicPulse.wavePhase(0L, 3_000L), 1e-6f)
        assertEquals(0.25f, MusicPulse.wavePhase(750L, 3_000L), 1e-6f)
        assertEquals(0.5f, MusicPulse.wavePhase(4_500L, 3_000L), 1e-6f)
        assertEquals(0f, MusicPulse.wavePhase(6_000L, 3_000L), 1e-6f)
        assertEquals(0f, MusicPulse.wavePhase(1_234L, 0L), 1e-6f)
        assertTrue(MusicPulse.wavePhase(2_999L) < 1f)
    }

    @Test
    fun waveOffsetIsSineOfAmplitude() {
        // Wavelength 24, amplitude 3: zero at 0, peak at a quarter, zero at half, trough at three quarters.
        assertEquals(0f, MusicPulse.waveOffset(0f, 24f, 3f, 0f), 1e-4f)
        assertEquals(3f, MusicPulse.waveOffset(6f, 24f, 3f, 0f), 1e-4f)
        assertEquals(0f, MusicPulse.waveOffset(12f, 24f, 3f, 0f), 1e-4f)
        assertEquals(-3f, MusicPulse.waveOffset(18f, 24f, 3f, 0f), 1e-4f)
        assertEquals(0f, MusicPulse.waveOffset(6f, 0f, 3f, 0f), 1e-6f)
    }

    @Test
    fun waveOffsetMovesForwardWithPhase() {
        // A quarter phase shifts the peak from x=6 to x=12.
        assertEquals(3f, MusicPulse.waveOffset(12f, 24f, 3f, 0.25f), 1e-4f)
        assertEquals(MusicPulse.waveOffset(5f, 24f, 3f, 0f), MusicPulse.waveOffset(29f, 24f, 3f, 0f), 1e-4f)
    }

    @Test
    fun progressLineUsesTextColourWithoutAlbumColours() {
        assertEquals(0xFFEEEEEE.toInt(), MusicPulse.progressLineColor(emptyList(), 0xFFEEEEEE.toInt()))
    }

    @Test
    fun contrastRatioMatchesWcag() {
        assertEquals(21f, MusicPulse.contrastRatio(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01f)
        assertEquals(1f, MusicPulse.contrastRatio(0xFF336699.toInt(), 0xFF336699.toInt()), 0.001f)
        // Mid grey #777777 on white is about 4.48.
        assertEquals(4.48f, MusicPulse.contrastRatio(0xFF777777.toInt(), 0xFFFFFFFF.toInt()), 0.02f)
    }

    @Test
    fun lineBackgroundIsOppositeOfText() {
        assertEquals(0xFFFFFFFF.toInt(), MusicPulse.lineBackground(0xFF111111.toInt()))
        assertEquals(0xFF000000.toInt(), MusicPulse.lineBackground(0xFFF0F0F0.toInt()))
    }

    @Test
    fun progressLinePicksAlbumColourWithBestContrastOnLightWallpaper() {
        val paleYellow = 0xFFF2E27A.toInt()
        val darkBlue = 0xFF1A3A80.toInt()
        val midRed = 0xFFC04040.toInt()
        // Dark text means a light wallpaper, so the darkest album colour wins, whatever the order.
        assertEquals(darkBlue, MusicPulse.progressLineColor(listOf(paleYellow, midRed, darkBlue), 0xFF101010.toInt()))
    }

    @Test
    fun progressLinePicksAlbumColourWithBestContrastOnDarkWallpaper() {
        val paleYellow = 0xFFF2E27A.toInt()
        val darkBlue = 0xFF1A3A80.toInt()
        assertEquals(paleYellow, MusicPulse.progressLineColor(listOf(darkBlue, paleYellow), 0xFFFAFAFA.toInt()))
    }

    @Test
    fun faintAlbumColourIsMixedTowardsTextUntilReadable() {
        val paleYellow = 0xFFF2E27A.toInt()
        val text = 0xFF101010.toInt()
        val white = 0xFFFFFFFF.toInt()
        assertTrue(MusicPulse.contrastRatio(paleYellow, white) < MusicPulse.LINE_MIN_CONTRAST)
        val line = MusicPulse.progressLineColor(listOf(paleYellow), text)
        assertTrue(MusicPulse.contrastRatio(line, white) >= MusicPulse.LINE_MIN_CONTRAST)
        // Still a tint of the album colour, not the plain text colour.
        assertNotEquals(text, line)
        assertEquals(0xFF, line ushr 24)
    }

    @Test
    fun readableAlbumColourIsKeptAsItIs() {
        val darkBlue = 0xFF1A3A80.toInt()
        assertEquals(darkBlue, MusicPulse.progressLineColor(listOf(darkBlue), 0xFF101010.toInt()))
    }

    @Test
    fun mixColorBlendsChannels() {
        assertEquals(0xFF000000.toInt(), MusicPulse.mixColor(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0f))
        assertEquals(0xFFFFFFFF.toInt(), MusicPulse.mixColor(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 1f))
        assertEquals(0xFF804020.toInt(), MusicPulse.mixColor(0xFF000000.toInt(), 0xFFFF8040.toInt(), 0.5f))
    }

    @Test
    fun waveTrackColorKeepsConstantAlpha() {
        assertEquals(0x70336699, MusicPulse.waveTrackColor(0xFF336699.toInt()))
        assertEquals(0x38112233, MusicPulse.waveTrackColor(0x80112233.toInt()))
        assertEquals(0x00112233, MusicPulse.waveTrackColor(0x00112233))
    }

    @Test
    fun dividerShowsOnlyBetweenStartAndEnd() {
        assertFalse(MusicPulse.waveDividerVisible(0f))
        assertTrue(MusicPulse.waveDividerVisible(0.01f))
        assertTrue(MusicPulse.waveDividerVisible(0.5f))
        assertTrue(MusicPulse.waveDividerVisible(0.99f))
        assertFalse(MusicPulse.waveDividerVisible(1f))
    }

    @Test
    fun waveTrackIsFaintButVisible() {
        val alpha = MusicPulse.WAVE_TRACK_ALPHA / 255f
        assertTrue(alpha in 0.4f..0.5f)
    }

    @Test
    fun waveOutlineIsSubtleBlackScaledByStrokeAlpha() {
        assertEquals(0x59000000, MusicPulse.waveOutlineColor(0xFF336699.toInt()))
        assertEquals(0x2C000000, MusicPulse.waveOutlineColor(0x80FFFFFF.toInt()))
        assertEquals(0, MusicPulse.waveOutlineColor(0x00336699))
        // The track outline comes from the full line colour, so it keeps its edge on light wallpapers.
        assertEquals(0x59000000, MusicPulse.waveOutlineColor(0xFFFFFFFF.toInt()))
        assertTrue(MusicPulse.WAVE_OUTLINE_ALPHA < 0x80)
    }

    @Test
    fun waveIsThickAndFitsTheViewHeight() {
        assertTrue(MusicPulse.WAVE_STROKE_DP in 3f..4f)
        assertTrue(MusicPulse.WAVE_AMPLITUDE_DP >= 3f)
        assertEquals(11f, MusicPulse.waveDrawHeightDp(), 0.001f)
        assertTrue(MusicPulse.waveDrawHeightDp() <= MusicPulse.WAVE_VIEW_HEIGHT_DP)
        assertTrue(MusicPulse.dividerDrawHeightDp() <= MusicPulse.WAVE_VIEW_HEIGHT_DP)
        assertTrue(MusicPulse.WAVE_TRACK_STROKE_DP < MusicPulse.WAVE_STROKE_DP)
    }

    @Test
    fun glowColourCycleIsAboutNineSeconds() {
        val cycle = MusicPulse.GLOW_COLOR_HOLD_MS + MusicPulse.GLOW_COLOR_FADE_MS
        assertTrue(cycle in 8_000L..10_000L)
    }
}
