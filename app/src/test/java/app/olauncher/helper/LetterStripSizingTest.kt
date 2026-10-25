package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LetterStripSizingTest {
    @Test fun keepsMaxWhenTall() = assertEquals(40f, LetterStripSizing.textSizePx(3000, 27, 40f), 0f)
    @Test fun shrinksWhenShort() {
        val s = LetterStripSizing.textSizePx(300, 27, 40f)
        assertTrue(s < 10f)
        assertTrue(s * 1.2f * 27 <= 300f + 0.01f)
    }
    @Test fun unknownHeightKeepsMax() = assertEquals(40f, LetterStripSizing.textSizePx(0, 27, 40f), 0f)

    @Test
    fun `scaleForDistance active slot has highest scale`() {
        assertEquals(LetterStripSizing.SCALE_ACTIVE, LetterStripSizing.scaleForDistance(0), 0.001f)
    }

    @Test
    fun `scaleForDistance falls off over two neighbours each side`() {
        val s0 = LetterStripSizing.scaleForDistance(0)
        val s1 = LetterStripSizing.scaleForDistance(1)
        val s2 = LetterStripSizing.scaleForDistance(2)
        val s3 = LetterStripSizing.scaleForDistance(3)

        assertTrue("Active letter must be larger than first neighbour", s0 > s1)
        assertTrue("First neighbour must be larger than second neighbour", s1 > s2)
        assertTrue("Second neighbour must be larger than rest scale", s2 > s3)
        assertEquals(LetterStripSizing.SCALE_REST, s3, 0.001f)
    }

    @Test
    fun `scaleForDistance is symmetric for negative distances`() {
        assertEquals(LetterStripSizing.scaleForDistance(1), LetterStripSizing.scaleForDistance(-1), 0.001f)
        assertEquals(LetterStripSizing.scaleForDistance(2), LetterStripSizing.scaleForDistance(-2), 0.001f)
        assertEquals(LetterStripSizing.scaleForDistance(3), LetterStripSizing.scaleForDistance(-3), 0.001f)
        assertEquals(LetterStripSizing.scaleForDistance(7), LetterStripSizing.scaleForDistance(-7), 0.001f)
    }

    @Test
    fun `scaleForDistance returns rest scale for distance 3 or greater`() {
        assertEquals(1.0f, LetterStripSizing.scaleForDistance(3), 0.001f)
        assertEquals(1.0f, LetterStripSizing.scaleForDistance(4), 0.001f)
        assertEquals(1.0f, LetterStripSizing.scaleForDistance(25), 0.001f)
        assertEquals(1.0f, LetterStripSizing.SCALE_REST, 0.001f)
    }

    @Test
    fun `fisheyeScale alias matches scaleForDistance`() {
        for (d in -5..5) {
            assertEquals(LetterStripSizing.scaleForDistance(d), LetterStripSizing.fisheyeScale(d), 0.001f)
        }
    }

    @Test
    fun `LetterStripSizing stays Context free`() {
        val file = listOf(
            java.io.File("src/main/java/app/olauncher/helper/LetterStripSizing.kt"),
            java.io.File("app/src/main/java/app/olauncher/helper/LetterStripSizing.kt"),
        ).first { it.isFile }
        val imports = file.readLines().filter { it.startsWith("import ") }
        assertTrue("LetterStripSizing must not import android: $imports", imports.none { it.startsWith("import android.") })
    }
}
