package app.olauncher.glance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class WeatherChartGeometryTest {

    private fun hour(hourOfDay: Int, temp: Double, precipProb: Int, code: Int = 0): Hour =
        Hour(
            time = LocalDateTime.of(2026, 10, 24, hourOfDay, 0),
            temp = temp,
            precipProb = precipProb,
            code = code,
        )

    @Test
    fun `equal temps produce a flat horizontal line`() {
        val hours = (0 until 6).map { hour(it, temp = 14.0, precipProb = 0) }
        val width = 300f
        val height = 40f
        val curveTop = 4f
        val curveBottom = 20f

        val geometry = WeatherChartGeometry.compute(
            hours = hours,
            width = width,
            height = height,
            slotCount = 6,
            curveTop = curveTop,
            curveBottom = curveBottom,
        )

        val expectedY = (curveTop + curveBottom) / 2f
        assertEquals(6, geometry.points.size)
        for (point in geometry.points) {
            assertEquals("Point Y must be vertically centered", expectedY, point.y, 0.001f)
        }

        // Segments should also be completely flat
        assertEquals(5, geometry.segments.size)
        for (segment in geometry.segments) {
            assertEquals(expectedY, segment.start.y, 0.001f)
            assertEquals(expectedY, segment.cp1.y, 0.001f)
            assertEquals(expectedY, segment.cp2.y, 0.001f)
            assertEquals(expectedY, segment.end.y, 0.001f)
        }
    }

    @Test
    fun `missing hours - empty list produces empty geometry`() {
        val geometry = WeatherChartGeometry.compute(
            hours = emptyList(),
            width = 300f,
            height = 40f,
        )
        assertTrue(geometry.points.isEmpty())
        assertTrue(geometry.bars.isEmpty())
        assertTrue(geometry.segments.isEmpty())
    }

    @Test
    fun `missing hours - single hour produces one point and one bar`() {
        val hours = listOf(hour(10, temp = 18.0, precipProb = 20))
        val geometry = WeatherChartGeometry.compute(
            hours = hours,
            width = 300f,
            height = 40f,
            slotCount = 6,
            curveTop = 4f,
            curveBottom = 20f,
        )

        assertEquals(1, geometry.points.size)
        assertEquals(1, geometry.bars.size)
        assertTrue("No curve segment for a single point", geometry.segments.isEmpty())

        // Point is centered in slot 0 (0 to 50, center 25)
        assertEquals(25f, geometry.points[0].x, 0.001f)
        assertEquals(12f, geometry.points[0].y, 0.001f) // Centered because range = 0
        assertEquals(25f, (geometry.bars[0].left + geometry.bars[0].right) / 2f, 0.001f)
    }

    @Test
    fun `missing hours - fewer than slot count fills corresponding left slots`() {
        val hours = listOf(
            hour(10, temp = 10.0, precipProb = 10),
            hour(11, temp = 15.0, precipProb = 20),
            hour(12, temp = 20.0, precipProb = 30),
        )
        val geometry = WeatherChartGeometry.compute(
            hours = hours,
            width = 300f,
            height = 40f,
            slotCount = 6,
            curveTop = 4f,
            curveBottom = 24f,
        )

        assertEquals(3, geometry.points.size)
        assertEquals(3, geometry.bars.size)
        assertEquals(2, geometry.segments.size)

        // Slots 0, 1, 2 have widths of 50, centers at 25, 75, 125
        assertEquals(25f, geometry.points[0].x, 0.001f)
        assertEquals(75f, geometry.points[1].x, 0.001f)
        assertEquals(125f, geometry.points[2].x, 0.001f)

        // Temperatures scaled: 10 is min (curveBottom = 24), 20 is max (curveTop = 4)
        assertEquals(24f, geometry.points[0].y, 0.001f)
        assertEquals(14f, geometry.points[1].y, 0.001f) // midpoint
        assertEquals(4f, geometry.points[2].y, 0.001f)
    }

    @Test
    fun `more than slot count is truncated to slot count`() {
        val hours = (0 until 12).map { hour(it, temp = it.toDouble(), precipProb = 0) }
        val geometry = WeatherChartGeometry.compute(
            hours = hours,
            width = 300f,
            height = 40f,
            slotCount = 6,
        )
        assertEquals(6, geometry.points.size)
        assertEquals(6, geometry.bars.size)
    }

    @Test
    fun `precipitation probability 0 percent draws nothing`() {
        val hours = listOf(hour(10, temp = 15.0, precipProb = 0))
        val geometry = WeatherChartGeometry.compute(
            hours = hours,
            width = 300f,
            height = 40f,
            barBottom = 38f,
            maxBarHeight = 12f,
        )

        val bar = geometry.bars[0]
        assertEquals(0f, bar.height, 0.001f)
        assertEquals(38f, bar.top, 0.001f)
        assertEquals(38f, bar.bottom, 0.001f)
        assertFalse("0% precip must not be visible", bar.isVisible)
    }

    @Test
    fun `precipitation probability 100 percent produces full bar`() {
        val hours = listOf(hour(10, temp = 15.0, precipProb = 100))
        val maxBarHeight = 12f
        val barBottom = 38f
        val geometry = WeatherChartGeometry.compute(
            hours = hours,
            width = 300f,
            height = 40f,
            barBottom = barBottom,
            maxBarHeight = maxBarHeight,
        )

        val bar = geometry.bars[0]
        assertEquals(maxBarHeight, bar.height, 0.001f)
        assertEquals(barBottom - maxBarHeight, bar.top, 0.001f)
        assertEquals(barBottom, bar.bottom, 0.001f)
        assertTrue(bar.isVisible)
    }

    @Test
    fun `precipitation probability 30 percent or more produces full bar`() {
        val hours = listOf(
            hour(10, temp = 15.0, precipProb = 30),
            hour(11, temp = 15.0, precipProb = 60),
            hour(12, temp = 15.0, precipProb = 100),
        )
        val maxBarHeight = 10f
        val geometry = WeatherChartGeometry.compute(
            hours = hours,
            width = 300f,
            height = 40f,
            maxBarHeight = maxBarHeight,
        )

        for (bar in geometry.bars) {
            assertEquals("30% or more must be a full bar", maxBarHeight, bar.height, 0.001f)
            assertTrue(bar.isVisible)
        }
    }

    @Test
    fun `precipitation probability scales linearly between 0 and 30 percent`() {
        val hours = listOf(
            hour(10, temp = 15.0, precipProb = 15), // half
            hour(11, temp = 15.0, precipProb = 6),  // 20%
        )
        val maxBarHeight = 10f
        val geometry = WeatherChartGeometry.compute(
            hours = hours,
            width = 300f,
            height = 40f,
            maxBarHeight = maxBarHeight,
        )

        assertEquals(5f, geometry.bars[0].height, 0.001f)
        assertEquals(2f, geometry.bars[1].height, 0.001f)
    }

    @Test
    fun `negative precipitation probability clamps to 0 percent`() {
        val hours = listOf(hour(10, temp = 15.0, precipProb = -10))
        val geometry = WeatherChartGeometry.compute(
            hours = hours,
            width = 300f,
            height = 40f,
        )
        assertEquals(0f, geometry.bars[0].height, 0.001f)
        assertFalse(geometry.bars[0].isVisible)
    }

    @Test
    fun `temperature curve scales correctly across arbitrary ranges`() {
        val hours = listOf(
            hour(0, temp = -5.0, precipProb = 0),
            hour(1, temp = 5.0, precipProb = 0),
            hour(2, temp = 0.0, precipProb = 0),
        )
        val curveTop = 10f
        val curveBottom = 30f
        val geometry = WeatherChartGeometry.compute(
            hours = hours,
            width = 180f,
            height = 50f,
            slotCount = 3,
            curveTop = curveTop,
            curveBottom = curveBottom,
        )

        // -5 is min -> curveBottom (30f)
        // 5 is max -> curveTop (10f)
        // 0 is mid -> 20f
        assertEquals(30f, geometry.points[0].y, 0.001f)
        assertEquals(10f, geometry.points[1].y, 0.001f)
        assertEquals(20f, geometry.points[2].y, 0.001f)
    }

    @Test
    fun `zero or negative dimensions return empty geometry without error`() {
        val hours = listOf(hour(0, temp = 10.0, precipProb = 10))
        assertTrue(WeatherChartGeometry.compute(hours, width = 0f, height = 40f).points.isEmpty())
        assertTrue(WeatherChartGeometry.compute(hours, width = -10f, height = 40f).points.isEmpty())
        assertTrue(WeatherChartGeometry.compute(hours, width = 100f, height = 0f).points.isEmpty())
        assertTrue(WeatherChartGeometry.compute(hours, width = 100f, height = -10f).points.isEmpty())
        assertTrue(WeatherChartGeometry.compute(hours, width = 100f, height = 40f, slotCount = 0).points.isEmpty())
    }

    @Test
    fun `convenience chartGeometry on WeatherFormatting produces identical results`() {
        val hours = listOf(hour(0, temp = 10.0, precipProb = 15))
        val fromObject = WeatherChartGeometry.compute(hours, 300f, 40f)
        val fromFormatting = WeatherFormatting.chartGeometry(hours, 300f, 40f)
        assertEquals(fromObject, fromFormatting)
    }
}
