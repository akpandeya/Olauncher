package app.olauncher.ui.today.cards

import app.olauncher.glance.WeatherCardReadability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherKeepOutTest {

    @Test
    fun `KeepOutRect calculates width and height correctly`() {
        val rect = KeepOutRect(left = 10f, top = 20f, right = 110f, bottom = 70f)
        assertEquals(100f, rect.width, 0.001f)
        assertEquals(50f, rect.height, 0.001f)
    }

    @Test
    fun `KeepOutRect pad expands all edges by padding amount`() {
        val rect = KeepOutRect(left = 20f, top = 30f, right = 80f, bottom = 90f)
        val padded = rect.pad(WeatherKeepOut.PAD)

        assertEquals(20f - 14f, padded.left, 0.001f)
        assertEquals(30f - 14f, padded.top, 0.001f)
        assertEquals(80f + 14f, padded.right, 0.001f)
        assertEquals(90f + 14f, padded.bottom, 0.001f)
        assertEquals(rect.width + 28f, padded.width, 0.001f)
        assertEquals(rect.height + 28f, padded.height, 0.001f)
    }

    @Test
    fun `fromBounds builds expected rect with and without padding`() {
        val unpadded = WeatherKeepOut.fromBounds(left = 15f, top = 25f, width = 50f, height = 30f)
        assertEquals(15f, unpadded.left, 0.001f)
        assertEquals(25f, unpadded.top, 0.001f)
        assertEquals(65f, unpadded.right, 0.001f)
        assertEquals(55f, unpadded.bottom, 0.001f)
        assertEquals(50f, unpadded.width, 0.001f)
        assertEquals(30f, unpadded.height, 0.001f)

        val padded = WeatherKeepOut.fromBounds(left = 15f, top = 25f, width = 50f, height = 30f, pad = 10f)
        assertEquals(5f, padded.left, 0.001f)
        assertEquals(15f, padded.top, 0.001f)
        assertEquals(75f, padded.right, 0.001f)
        assertEquals(65f, padded.bottom, 0.001f)
        assertEquals(70f, padded.width, 0.001f)
        assertEquals(50f, padded.height, 0.001f)
    }

    @Test
    fun `padRects pads all elements in collection`() {
        val r1 = KeepOutRect(0f, 0f, 10f, 10f)
        val r2 = KeepOutRect(20f, 20f, 30f, 30f)
        val padded = WeatherKeepOut.padRects(listOf(r1, r2), pad = 5f)

        assertEquals(2, padded.size)
        assertEquals(-5f, padded[0].left, 0.001f)
        assertEquals(15f, padded[0].right, 0.001f)
        assertEquals(15f, padded[1].left, 0.001f)
        assertEquals(35f, padded[1].right, 0.001f)
    }

    @Test
    fun `constants match task-05 readability specification`() {
        assertEquals(WeatherCardReadability.KEEP_OUT_PAD.toFloat(), WeatherKeepOut.PAD, 0.001f)
        assertEquals(WeatherCardReadability.KEEP_OUT_SIGMA.toFloat(), WeatherKeepOut.SIGMA, 0.001f)
        assertEquals(WeatherCardReadability.KEEP_OUT_PEAK.toFloat(), WeatherKeepOut.PEAK_ALPHA, 0.001f)
        assertEquals(14f, WeatherKeepOut.PAD, 0.001f)
        assertEquals(6f, WeatherKeepOut.SIGMA, 0.001f)
        assertEquals(0.87f, WeatherKeepOut.PEAK_ALPHA, 0.001f)
    }
}
