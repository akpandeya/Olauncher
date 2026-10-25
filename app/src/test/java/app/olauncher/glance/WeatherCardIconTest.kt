package app.olauncher.glance

import app.olauncher.R
import app.olauncher.helper.WeatherLook
import app.olauncher.helper.WeatherLooks
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherCardIconTest {

    private fun forecast(code: Int, isDay: Boolean) =
        Forecast(
            nowTemp = 15.0,
            nowCode = code,
            isDay = isDay,
            todayMax = 20.0,
            todayMin = 10.0,
            hours = emptyList(),
        )

    private fun hour(hourOfDay: Int, code: Int) =
        Hour(
            time = LocalDateTime.of(2026, 10, 25, hourOfDay, 0),
            temp = 15.0,
            precipProb = 0,
            code = code,
        )

    private val handledCodes = listOf(
        0, 1, 2, 3,
        45, 48,
        51, 53, 55, 56, 57,
        61, 63, 65, 66, 67,
        71, 73, 75, 77,
        80, 81, 82,
        85, 86,
        95, 96, 99,
    )

    @Test
    fun `for every code the shared mapping handles, day and night icons equal iconFor look`() {
        for (code in handledCodes) {
            // Day
            val dayLook = WeatherLooks.forForecast(code, isDay = true)
            assertNotNull("Handled code $code must produce a day look", dayLook)
            val expectedDayIcon = WeatherCodes.iconFor(dayLook!!)

            assertEquals(
                "WeatherCodes.icon by day for code $code",
                expectedDayIcon,
                WeatherCodes.icon(code, isDay = true),
            )
            assertEquals(
                "HeroFormatting.nowIconRes by day for code $code",
                expectedDayIcon,
                HeroFormatting.nowIconRes(forecast(code, isDay = true)),
            )
            assertEquals(
                "HeroFormatting.hourIconRes hour 12 for code $code",
                expectedDayIcon,
                HeroFormatting.hourIconRes(hour(12, code)),
            )

            // Night
            val nightLook = WeatherLooks.forForecast(code, isDay = false)
            assertNotNull("Handled code $code must produce a night look", nightLook)
            val expectedNightIcon = WeatherCodes.iconFor(nightLook!!)

            assertEquals(
                "WeatherCodes.icon by night for code $code",
                expectedNightIcon,
                WeatherCodes.icon(code, isDay = false),
            )
            assertEquals(
                "HeroFormatting.nowIconRes by night for code $code",
                expectedNightIcon,
                HeroFormatting.nowIconRes(forecast(code, isDay = false)),
            )
            assertEquals(
                "HeroFormatting.hourIconRes hour 23 for code $code",
                expectedNightIcon,
                HeroFormatting.hourIconRes(hour(23, code)),
            )
        }
    }

    @Test
    fun `unknown codes give null for all icon functions`() {
        val unknownCodes = listOf(-1, 4, 10, 100)
        for (code in unknownCodes) {
            for (isDay in listOf(true, false)) {
                assertNull(
                    "WeatherCodes.icon for unknown code $code isDay=$isDay",
                    WeatherCodes.icon(code, isDay = isDay),
                )
                assertNull(
                    "HeroFormatting.nowIconRes for unknown code $code isDay=$isDay",
                    HeroFormatting.nowIconRes(forecast(code, isDay = isDay)),
                )
            }
            assertNull(
                "HeroFormatting.hourIconRes hour 12 for unknown code $code",
                HeroFormatting.hourIconRes(hour(12, code)),
            )
            assertNull(
                "HeroFormatting.hourIconRes hour 23 for unknown code $code",
                HeroFormatting.hourIconRes(hour(23, code)),
            )
        }
    }

    @Test
    fun `all nine icons are reachable`() {
        val expectedDrawables = setOf(
            R.drawable.ic_weather_clear_day,
            R.drawable.ic_weather_clear_night,
            R.drawable.ic_weather_partly_cloudy,
            R.drawable.ic_weather_overcast,
            R.drawable.ic_weather_light_rain,
            R.drawable.ic_weather_heavy_rain,
            R.drawable.ic_weather_thunderstorm,
            R.drawable.ic_weather_snow,
            R.drawable.ic_weather_fog,
        )
        assertEquals(9, expectedDrawables.size)

        // Reachable via iconFor(WeatherLook)
        val fromLookEnum = WeatherLook.values().map { WeatherCodes.iconFor(it) }.toSet()
        assertEquals(expectedDrawables, fromLookEnum)

        // Reachable via WeatherCodes.icon(code, isDay)
        val reachableFromCodes = mutableSetOf<Int>()
        for (code in handledCodes) {
            WeatherCodes.icon(code, isDay = true)?.let { reachableFromCodes.add(it) }
            WeatherCodes.icon(code, isDay = false)?.let { reachableFromCodes.add(it) }
        }
        assertEquals(expectedDrawables, reachableFromCodes)

        // Reachable via HeroFormatting.nowIconRes
        val reachableFromHeroNow = mutableSetOf<Int>()
        for (code in handledCodes) {
            HeroFormatting.nowIconRes(forecast(code, isDay = true))?.let { reachableFromHeroNow.add(it) }
            HeroFormatting.nowIconRes(forecast(code, isDay = false))?.let { reachableFromHeroNow.add(it) }
        }
        assertEquals(expectedDrawables, reachableFromHeroNow)

        // Reachable via HeroFormatting.hourIconRes
        val reachableFromHeroHour = mutableSetOf<Int>()
        for (code in handledCodes) {
            HeroFormatting.hourIconRes(hour(12, code))?.let { reachableFromHeroHour.add(it) }
            HeroFormatting.hourIconRes(hour(23, code))?.let { reachableFromHeroHour.add(it) }
        }
        assertEquals(expectedDrawables, reachableFromHeroHour)
    }
}
