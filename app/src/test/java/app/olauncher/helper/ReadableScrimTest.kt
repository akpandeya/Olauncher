package app.olauncher.helper

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadableScrimTest {
    @Test
    fun contrastOfBlackOnWhiteIs21() {
        assertEquals(21.0, ReadableScrim.contrast(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
    }

    @Test
    fun cardMeetsAaForHintTextOnWorstPhotoInBothThemes() {
        for (dark in listOf(true, false)) {
            val c = ReadableScrim.hintContrastOnCard(ReadableScrim.CARD_ALPHA, dark)
            assertTrue("dark=$dark contrast=$c", c >= ReadableScrim.MIN_CONTRAST)
        }
    }

    @Test
    fun cardAlphaIsNotWastefullyStrong() {
        for (dark in listOf(true, false)) {
            val min = ReadableScrim.minCardAlpha(dark)
            assertNotNull(min)
            assertTrue(ReadableScrim.CARD_ALPHA >= min!!)
            assertTrue("dark=$dark min=$min", ReadableScrim.CARD_ALPHA - min <= 0x40)
        }
    }

    @Test
    fun oldCardAlphaFailedAaOnWhitePhoto() {
        assertTrue(ReadableScrim.hintContrastOnCard(0x40, true) < ReadableScrim.MIN_CONTRAST)
    }

    @Test
    fun blendMixesByAlpha() {
        assertEquals(0xFF808080.toInt(), ReadableScrim.blend(0x80FFFFFF.toInt(), 0xFF000000.toInt()))
    }

    @Test
    fun colorResourcesMatchConstants() {
        for ((dir, rgb) in listOf("values-night" to "000000", "values" to "FFFFFF")) {
            val xml = File("src/main/res/$dir/colors.xml").readText()
            assertTrue(xml.contains("\"readableCard\">#%02X%s<".format(ReadableScrim.CARD_ALPHA, rgb)))
            assertTrue(xml.contains("\"drawerScrimBottom\">#%02X%s<".format(ReadableScrim.DRAWER_BOTTOM_ALPHA, rgb)))
        }
    }
}
