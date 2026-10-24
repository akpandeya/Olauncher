package app.olauncher.glance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import app.olauncher.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

class TodayHeroTest {

    private fun hour(h: Int, temp: Double = 10.0, code: Int = 0) =
        Hour(LocalDateTime.of(2026, 10, 19, h, 0), temp, 0, code)

    private val day = (10..23).map { hour(it) }

    @Test
    fun `strip starts at the current hour and takes count`() {
        val result = HourlyStrip.next(day, LocalDateTime.of(2026, 10, 19, 15, 40), 6)
        assertEquals((15..20).toList(), result.map { it.time.hour })
    }

    @Test
    fun `strip skips hours before now`() {
        val result = HourlyStrip.next(day, LocalDateTime.of(2026, 10, 19, 12, 0), 3)
        assertEquals(listOf(12, 13, 14), result.map { it.time.hour })
    }

    @Test
    fun `strip returns fewer when not enough hours remain`() {
        val result = HourlyStrip.next(day, LocalDateTime.of(2026, 10, 19, 22, 30), 6)
        assertEquals(listOf(22, 23), result.map { it.time.hour })
    }

    @Test
    fun `strip is empty for empty list, stale cache or zero count`() {
        val now = LocalDateTime.of(2026, 10, 19, 15, 0)
        assertTrue(HourlyStrip.next(emptyList(), now, 6).isEmpty())
        assertTrue(HourlyStrip.next(day, LocalDateTime.of(2026, 10, 20, 9, 0), 6).isEmpty())
        assertTrue(HourlyStrip.next(day, now, 0).isEmpty())
    }

    @Test
    fun `english day and date`() {
        val date = LocalDate.of(2026, 10, 19)
        assertEquals("Monday", HeroFormatting.dayName(date, Locale.ENGLISH))
        assertEquals("19 October", HeroFormatting.dateLine(date, Locale.ENGLISH))
    }

    @Test
    fun `german day and date`() {
        val date = LocalDate.of(2026, 10, 19)
        assertEquals("Montag", HeroFormatting.dayName(date, Locale.GERMAN))
        assertEquals("19. Oktober", HeroFormatting.dateLine(date, Locale.GERMAN))
    }

    @Test
    fun `capitalise handles empty and already capital`() {
        assertEquals("Light rain", HeroFormatting.capitalise("light rain"))
        assertEquals("Fog", HeroFormatting.capitalise("Fog"))
        assertEquals("", HeroFormatting.capitalise(""))
    }

    @Test
    fun `now icon, temperature and condition line`() {
        val forecast = Forecast(11.6, 51, true, 15.0, 8.4, emptyList())
        assertEquals("🌦", HeroFormatting.nowIcon(forecast))
        assertEquals("12°", HeroFormatting.nowTemperature(forecast))
        assertEquals("Drizzle · 15° / 8°", HeroFormatting.conditionLine(forecast))
    }

    @Test
    fun `no icon for an unknown code`() {
        val forecast = Forecast(-0.6, 4, true, 1.0, -3.0, emptyList())
        assertEquals("-1°", HeroFormatting.nowTemperature(forecast))
        assertEquals("", HeroFormatting.nowIcon(forecast))
    }

    @Test
    fun `hour column parts`() {
        val h = hour(9, temp = 12.5, code = 61)
        assertEquals("09", HeroFormatting.hourLabel(h))
        assertEquals("13°", HeroFormatting.hourTemperature(h))
        assertEquals("🌧", HeroFormatting.hourEmoji(h))
    }

    @Test
    fun `hour emoji uses moon at night and sun by day`() {
        assertEquals("☀️", HeroFormatting.hourEmoji(hour(12, code = 0)))
        assertEquals("🌙", HeroFormatting.hourEmoji(hour(23, code = 0)))
        assertEquals("", HeroFormatting.hourEmoji(hour(12, code = 4)))
    }

    @Test
    fun `nowIconRes returns vector drawable resource`() {
        val forecast = Forecast(11.6, 51, true, 15.0, 8.4, emptyList())
        assertEquals(R.drawable.ic_weather_light_rain, HeroFormatting.nowIconRes(forecast))

        val unknown = Forecast(11.6, 4, true, 15.0, 8.4, emptyList())
        assertNull(HeroFormatting.nowIconRes(unknown))
    }

    @Test
    fun `hourIconRes uses moon at night and sun by day`() {
        assertEquals(R.drawable.ic_weather_clear_day, HeroFormatting.hourIconRes(hour(12, code = 0)))
        assertEquals(R.drawable.ic_weather_clear_night, HeroFormatting.hourIconRes(hour(23, code = 0)))
        assertEquals(R.drawable.ic_weather_light_rain, HeroFormatting.hourIconRes(hour(9, code = 61)))
        assertNull(HeroFormatting.hourIconRes(hour(12, code = 4)))
    }
}
