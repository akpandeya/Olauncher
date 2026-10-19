package app.olauncher.glance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherFormattingTest {

    private fun sampleForecast(
        nowTemp: Double = 12.0,
        nowCode: Int = 61,
        isDay: Boolean = true,
    ) = Forecast(
        nowTemp = nowTemp,
        nowCode = nowCode,
        isDay = isDay,
        todayMax = 15.0,
        todayMin = 8.0,
        hours = emptyList(),
    )

    @Test
    fun `rounding of negative temperatures rounds -0_4 to 0 degree without unit letter`() {
        val now = 10_000_000L
        val fetchedAt = now - 1000L

        val line = WeatherFormatting.homeLine(
            forecast = sampleForecast(nowTemp = -0.4, nowCode = 61, isDay = true),
            fetchedAt = fetchedAt,
            now = now,
        )
        assertEquals("🌧 0°", line)
    }

    @Test
    fun `rounding works for edge values and omits unit letter`() {
        val now = 10_000_000L
        val fetchedAt = now - 1000L

        assertEquals(
            "🌧 12°",
            WeatherFormatting.homeLine(sampleForecast(nowTemp = 12.0, nowCode = 61), fetchedAt, now),
        )
        assertEquals(
            "🌧 12°",
            WeatherFormatting.homeLine(sampleForecast(nowTemp = 12.3, nowCode = 61), fetchedAt, now),
        )
        assertEquals(
            "🌧 13°",
            WeatherFormatting.homeLine(sampleForecast(nowTemp = 12.6, nowCode = 61), fetchedAt, now),
        )
        assertEquals(
            "⛅ 12°",
            WeatherFormatting.homeLine(sampleForecast(nowTemp = 12.0, nowCode = 2), fetchedAt, now),
        )
        assertEquals(
            "🌧 0°",
            WeatherFormatting.homeLine(sampleForecast(nowTemp = 0.0, nowCode = 61), fetchedAt, now),
        )
        assertEquals(
            "🌧 0°",
            WeatherFormatting.homeLine(sampleForecast(nowTemp = -0.0, nowCode = 61), fetchedAt, now),
        )
        assertEquals(
            "🌧 0°",
            WeatherFormatting.homeLine(sampleForecast(nowTemp = -0.1, nowCode = 61), fetchedAt, now),
        )
        assertEquals(
            "🌧 -1°",
            WeatherFormatting.homeLine(sampleForecast(nowTemp = -0.6, nowCode = 61), fetchedAt, now),
        )
        assertEquals(
            "🌧 -5°",
            WeatherFormatting.homeLine(sampleForecast(nowTemp = -5.4, nowCode = 61), fetchedAt, now),
        )
        assertEquals(
            "🌧 -6°",
            WeatherFormatting.homeLine(sampleForecast(nowTemp = -5.6, nowCode = 61), fetchedAt, now),
        )
        assertEquals(
            "🌙 15°",
            WeatherFormatting.homeLine(sampleForecast(nowTemp = 15.0, nowCode = 0, isDay = false), fetchedAt, now),
        )
    }

    @Test
    fun `unknown code drops the emoji and keeps the temperature`() {
        val now = 10_000_000L
        assertEquals(
            "7°",
            WeatherFormatting.homeLine(sampleForecast(nowTemp = 7.0, nowCode = 4), now - 1000L, now),
        )
    }

    @Test
    fun `stale data older than 6 hours returns null`() {
        val fetchedAt = 10_000_000L
        val sixHoursMillis = 6 * 60 * 60 * 1000L

        // 6 hours + 1 ms
        assertNull(
            WeatherFormatting.homeLine(
                forecast = sampleForecast(),
                fetchedAt = fetchedAt,
                now = fetchedAt + sixHoursMillis + 1L,
            )
        )

        // 7 hours
        assertNull(
            WeatherFormatting.homeLine(
                forecast = sampleForecast(),
                fetchedAt = fetchedAt,
                now = fetchedAt + 7 * 60 * 60 * 1000L,
            )
        )

        // 24 hours
        assertNull(
            WeatherFormatting.homeLine(
                forecast = sampleForecast(),
                fetchedAt = fetchedAt,
                now = fetchedAt + 24 * 60 * 60 * 1000L,
            )
        )
    }

    @Test
    fun `fresh data within 6 hours returns formatted line`() {
        val fetchedAt = 10_000_000L
        val sixHoursMillis = 6 * 60 * 60 * 1000L

        // immediately after fetch
        assertEquals(
            "🌧 12°",
            WeatherFormatting.homeLine(sampleForecast(), fetchedAt, now = fetchedAt),
        )

        // 3 hours later
        assertEquals(
            "🌧 12°",
            WeatherFormatting.homeLine(sampleForecast(), fetchedAt, now = fetchedAt + 3 * 60 * 60 * 1000L),
        )

        // exactly 6 hours later
        assertEquals(
            "🌧 12°",
            WeatherFormatting.homeLine(sampleForecast(), fetchedAt, now = fetchedAt + sixHoursMillis),
        )
    }

    @Test
    fun `missing data returns null`() {
        val now = 10_000_000L

        // null forecast
        assertNull(WeatherFormatting.homeLine(null, fetchedAt = now - 1000L, now = now))

        // zero fetchedAt
        assertNull(WeatherFormatting.homeLine(sampleForecast(), fetchedAt = 0L, now = now))

        // negative fetchedAt
        assertNull(WeatherFormatting.homeLine(sampleForecast(), fetchedAt = -1L, now = now))

        // null forecast and zero fetchedAt
        assertNull(WeatherFormatting.homeLine(null, fetchedAt = 0L, now = now))

        // NaN temperature
        assertNull(WeatherFormatting.homeLine(sampleForecast(nowTemp = Double.NaN), fetchedAt = now - 1000L, now = now))
    }
}
