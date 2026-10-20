package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PosterClockTest {

    @Test
    fun keepsDesiredSizeWhenItFits() {
        val size = PosterClock.fitTextSizePx(
            desiredPx = 450f, sampleWidthAtDesiredPx = 900f, availableWidthPx = 1100f,
            maxLineHeightPx = 700f, lineHeightRatio = 1.2f,
        )
        assertEquals(450f, size, 0.001f)
    }

    @Test
    fun shrinksToFitWidestSampleInTheRow() {
        // At 2x font scale the 450px dimen would draw "23:59" 1800px wide in a 1000px row.
        val size = PosterClock.fitTextSizePx(
            desiredPx = 900f, sampleWidthAtDesiredPx = 1800f, availableWidthPx = 1000f,
            maxLineHeightPx = 0f, lineHeightRatio = 1.2f,
        )
        val drawnWidth = 1800f * size / 900f
        assertTrue("drawn $drawnWidth must fit 1000", drawnWidth <= 1000f)
        assertEquals(900f * 1000f * PosterClock.WIDTH_SAFETY / 1800f, size, 0.001f)
    }

    @Test
    fun heightCapWinsWhenTighterThanWidth() {
        val size = PosterClock.fitTextSizePx(
            desiredPx = 600f, sampleWidthAtDesiredPx = 1200f, availableWidthPx = 1000f,
            maxLineHeightPx = 360f, lineHeightRatio = 1.2f,
        )
        assertEquals(300f, size, 0.001f)
    }

    @Test
    fun unknownWidthSkipsWidthCap() {
        val size = PosterClock.fitTextSizePx(
            desiredPx = 420f, sampleWidthAtDesiredPx = 5000f, availableWidthPx = 0f,
            maxLineHeightPx = 0f, lineHeightRatio = 1.1f,
        )
        assertEquals(420f, size, 0.001f)
    }

    @Test
    fun lineRatioBelowOneIsTreatedAsOne() {
        val size = PosterClock.fitTextSizePx(
            desiredPx = 500f, sampleWidthAtDesiredPx = 100f, availableWidthPx = 1000f,
            maxLineHeightPx = 333f, lineHeightRatio = 0.5f,
        )
        assertEquals(333f, size, 0.001f)
    }

    @Test
    fun widestSampleIsTwentyFourHourMax() {
        assertEquals("23:59", PosterClock.WIDEST_SAMPLE)
    }
}
