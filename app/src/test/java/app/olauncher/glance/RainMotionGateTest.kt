package app.olauncher.glance

import app.olauncher.helper.WeatherLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RainMotionGateTest {

    @Test
    fun `animator scale 0 gives still frame`() {
        assertFalse(
            "Rain must hold a still frame when animators are disabled",
            RainMotionGate.shouldAnimate(
                isRainLook = true,
                cardVisible = true,
                animatorsEnabled = false,
            ),
        )

        for (look in listOf(WeatherLook.LIGHT_RAIN, WeatherLook.HEAVY_RAIN, WeatherLook.STORM)) {
            assertFalse(
                "Look $look must hold still when animators are disabled",
                RainMotionGate.shouldAnimate(
                    look = look,
                    cardVisible = true,
                    animatorsEnabled = false,
                ),
            )
        }
    }

    @Test
    fun `card hidden gives still frame`() {
        assertFalse(
            "Rain must hold a still frame when card is hidden",
            RainMotionGate.shouldAnimate(
                isRainLook = true,
                cardVisible = false,
                animatorsEnabled = true,
            ),
        )

        for (look in listOf(WeatherLook.LIGHT_RAIN, WeatherLook.HEAVY_RAIN, WeatherLook.STORM)) {
            assertFalse(
                "Look $look must hold still when card is hidden",
                RainMotionGate.shouldAnimate(
                    look = look,
                    cardVisible = false,
                    animatorsEnabled = true,
                ),
            )
        }
    }

    @Test
    fun `non-rain look gives still frame`() {
        assertFalse(
            "Non-rain look must not animate rain",
            RainMotionGate.shouldAnimate(
                isRainLook = false,
                cardVisible = true,
                animatorsEnabled = true,
            ),
        )

        val nonRainLooks = listOf(
            WeatherLook.CLEAR,
            WeatherLook.PARTLY,
            WeatherLook.OVERCAST,
            WeatherLook.SNOW,
            WeatherLook.FOG,
            WeatherLook.CLEAR_NIGHT,
            null,
        )

        for (look in nonRainLooks) {
            assertFalse(
                "Non-rain look $look must not animate",
                RainMotionGate.shouldAnimate(
                    look = look,
                    cardVisible = true,
                    animatorsEnabled = true,
                ),
            )
            assertFalse(
                "isRainLook for $look must be false",
                RainMotionGate.isRainLook(look),
            )
        }
    }

    @Test
    fun `all on gives animate`() {
        assertTrue(
            "Rain must animate when look is rain, card is visible and animators are enabled",
            RainMotionGate.shouldAnimate(
                isRainLook = true,
                cardVisible = true,
                animatorsEnabled = true,
            ),
        )

        val rainLooks = listOf(
            WeatherLook.LIGHT_RAIN,
            WeatherLook.HEAVY_RAIN,
            WeatherLook.STORM,
        )

        for (look in rainLooks) {
            assertTrue("isRainLook for $look must be true", RainMotionGate.isRainLook(look))
            assertTrue(
                "Look $look must animate when visible and animators are on",
                RainMotionGate.shouldAnimate(
                    look = look,
                    cardVisible = true,
                    animatorsEnabled = true,
                ),
            )
        }
    }

    @Test
    fun `seedFor produces deterministic hash matching mock`() {
        val s1 = RainMotionGate.seedFor("rain:heavy:360x200")
        val s2 = RainMotionGate.seedFor("rain:heavy:360x200")
        assertEquals("Same string must produce same seed", s1, s2)

        val seedA = RainMotionGate.seedFor(WeatherLook.HEAVY_RAIN, 360f, 200f)
        val seedB = RainMotionGate.seedFor(WeatherLook.HEAVY_RAIN, 360f, 200f)
        assertEquals(seedA, seedB)

        val seedDifferentLook = RainMotionGate.seedFor(WeatherLook.LIGHT_RAIN, 360f, 200f)
        assertNotEquals(seedA, seedDifferentLook)

        val seedDifferentSize = RainMotionGate.seedFor(WeatherLook.HEAVY_RAIN, 412f, 200f)
        assertNotEquals(seedA, seedDifferentSize)
    }
}
