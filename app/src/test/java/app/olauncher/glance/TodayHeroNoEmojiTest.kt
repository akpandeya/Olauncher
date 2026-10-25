package app.olauncher.glance

import app.olauncher.ui.today.cards.WeatherCardLogic
import java.time.LocalDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class TodayHeroNoEmojiTest {

    private val documentedCodes = listOf(
        0, 1, 2, 3,
        45, 48,
        51, 53, 55, 56, 57,
        61, 63, 65, 66, 67,
        71, 73, 75, 77,
        80, 81, 82,
        85, 86,
        95, 96, 99,
    )

    private val unknownCodes = listOf(-100, -1, 4, 10, 50, 60, 70, 100, 999)

    private val testCodes = documentedCodes + unknownCodes

    private fun assertNoEmoji(text: String?, context: String) {
        assertNotNull("Expected non-null string for $context", text)
        text?.let { s ->
            s.codePoints().forEach { cp ->
                val isEmoji = (cp in 0x1F300..0x1FAFF) || (cp in 0x2600..0x27BF) || (cp == 0xFE0F)
                assertFalse(
                    "Found emoji code point U+${Integer.toHexString(cp).uppercase()} in '$s' ($context)",
                    isEmoji,
                )
            }
        }
    }

    @Test
    fun `no emoji code points for all WMO codes and unknown codes, day and night`() {
        val nowMillis = 10_000_000L

        for (code in testCodes) {
            for (isDay in listOf(true, false)) {
                val hourTime = LocalDateTime.of(2026, 10, 25, if (isDay) 14 else 22, 0)
                val hour = Hour(time = hourTime, temp = 14.2, precipProb = 10, code = code)
                val forecast = Forecast(
                    nowTemp = 14.2,
                    nowCode = code,
                    isDay = isDay,
                    todayMax = 18.0,
                    todayMin = 7.0,
                    hours = listOf(hour),
                )

                val ctx = "code=$code, isDay=$isDay"

                val nowTemp = HeroFormatting.nowTemperature(forecast)
                assertNoEmoji(nowTemp, "nowTemperature $ctx")

                val condLine = HeroFormatting.conditionLine(forecast)
                assertNoEmoji(condLine, "conditionLine $ctx")

                val hLabel = HeroFormatting.hourLabel(hour)
                assertNoEmoji(hLabel, "hourLabel $ctx")

                val hTemp = HeroFormatting.hourTemperature(hour)
                assertNoEmoji(hTemp, "hourTemperature $ctx")

                val cardLine1 = WeatherCardLogic.formatLine1(forecast)
                assertNoEmoji(cardLine1, "WeatherCardLogic.formatLine1(forecast) $ctx")

                val cardLine1Params = WeatherCardLogic.formatLine1(
                    nowTemp = forecast.nowTemp,
                    nowCode = forecast.nowCode,
                    isDay = forecast.isDay,
                    todayMax = forecast.todayMax,
                    todayMin = forecast.todayMin,
                )
                assertNoEmoji(cardLine1Params, "WeatherCardLogic.formatLine1(params) $ctx")

                val homeLine = WeatherFormatting.homeLine(
                    forecast = forecast,
                    fetchedAt = nowMillis - 1000L,
                    now = nowMillis,
                )
                assertNoEmoji(homeLine, "homeLine $ctx")
            }
        }
    }

    @Test
    fun `no emoji code points for negative and zero temperatures`() {
        val nowMillis = 10_000_000L
        val edgeTemps = listOf(-15.6, -5.0, -0.6, -0.4, 0.0, 0.4)

        for (code in testCodes) {
            for (isDay in listOf(true, false)) {
                for (temp in edgeTemps) {
                    val hour = Hour(
                        time = LocalDateTime.of(2026, 10, 25, if (isDay) 12 else 23, 0),
                        temp = temp,
                        precipProb = 0,
                        code = code,
                    )
                    val forecast = Forecast(
                        nowTemp = temp,
                        nowCode = code,
                        isDay = isDay,
                        todayMax = temp + 5.0,
                        todayMin = temp - 5.0,
                        hours = listOf(hour),
                    )

                    val ctx = "code=$code, isDay=$isDay, temp=$temp"

                    assertNoEmoji(HeroFormatting.nowTemperature(forecast), "nowTemperature $ctx")
                    assertNoEmoji(HeroFormatting.conditionLine(forecast), "conditionLine $ctx")
                    assertNoEmoji(HeroFormatting.hourTemperature(hour), "hourTemperature $ctx")
                    assertNoEmoji(WeatherCardLogic.formatLine1(forecast), "formatLine1 $ctx")
                    assertNoEmoji(
                        WeatherFormatting.homeLine(forecast, fetchedAt = nowMillis - 1000L, now = nowMillis),
                        "homeLine $ctx",
                    )
                }
            }
        }
    }
}
