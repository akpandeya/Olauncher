package app.olauncher.glance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class RainOutlookTest {

    private val labels = RainOutlookLabels(
        dryNext12h = "Dry for the next 12 h",
        rainAllDay = "Rain all day",
        rainUntil = "Rain until %1\$s",
        rainFrom = "Rain from %1\$s",
    )

    private fun hour(
        time: LocalDateTime,
        precipProb: Int = 0,
        code: Int = 0,
        temp: Double = 15.0,
    ) = Hour(
        time = time,
        temp = temp,
        precipProb = precipProb,
        code = code,
    )

    @Test
    fun `isRainy returns true when code is greater than or equal to 51 or precipProb is greater than or equal to 60`() {
        val baseTime = LocalDateTime.of(2024, 5, 18, 14, 0)

        // Code >= 51, precipProb < 60
        assertTrue(RainOutlook.isRainy(hour(baseTime, code = 51, precipProb = 0)))
        assertTrue(RainOutlook.isRainy(hour(baseTime, code = 61, precipProb = 10)))
        assertTrue(RainOutlook.isRainy(hour(baseTime, code = 80, precipProb = 50)))

        // Code < 51, precipProb >= 60
        assertTrue(RainOutlook.isRainy(hour(baseTime, code = 0, precipProb = 60)))
        assertTrue(RainOutlook.isRainy(hour(baseTime, code = 2, precipProb = 80)))
        assertTrue(RainOutlook.isRainy(hour(baseTime, code = 3, precipProb = 100)))

        // Both true
        assertTrue(RainOutlook.isRainy(hour(baseTime, code = 61, precipProb = 75)))

        // Neither true (dry)
        assertFalse(RainOutlook.isRainy(hour(baseTime, code = 50, precipProb = 59)))
        assertFalse(RainOutlook.isRainy(hour(baseTime, code = 3, precipProb = 59)))
        assertFalse(RainOutlook.isRainy(hour(baseTime, code = 0, precipProb = 0)))
    }

    @Test
    fun `rain now and stopping later returns Rain until time`() {
        val now = LocalDateTime.of(2024, 5, 18, 14, 0)
        val hours = listOf(
            hour(now, code = 61, precipProb = 70), // 14:00 rain
            hour(now.plusHours(1), code = 61, precipProb = 50), // 15:00 rain (code 61)
            hour(now.plusHours(2), code = 2, precipProb = 10), // 16:00 dry -> stops at 16:00
            hour(now.plusHours(3), code = 2, precipProb = 10), // 17:00 dry
        )

        val summary = RainOutlook.summary(hours, labels, now)
        assertEquals("Rain until 16:00", summary)
    }

    @Test
    fun `rain now due to high precip probability and stopping later returns Rain until time`() {
        val now = LocalDateTime.of(2024, 5, 18, 14, 0)
        val hours = listOf(
            hour(now, code = 3, precipProb = 65), // 14:00 rain by precip prob
            hour(now.plusHours(1), code = 1, precipProb = 20), // 15:00 dry
            hour(now.plusHours(2), code = 0, precipProb = 0), // 16:00 dry
        )

        val summary = RainOutlook.summary(hours, labels, now)
        assertEquals("Rain until 15:00", summary)
    }

    @Test
    fun `dry now and rain later returns Rain from time`() {
        val now = LocalDateTime.of(2024, 5, 18, 14, 0)
        val hours = listOf(
            hour(now, code = 1, precipProb = 10), // 14:00 dry
            hour(now.plusHours(1), code = 2, precipProb = 20), // 15:00 dry
            hour(now.plusHours(2), code = 61, precipProb = 80), // 16:00 rain
            hour(now.plusHours(3), code = 61, precipProb = 80), // 17:00 rain
        )

        val summary = RainOutlook.summary(hours, labels, now)
        assertEquals("Rain from 16:00", summary)
    }

    @Test
    fun `dry now and rain later by precip probability returns Rain from time`() {
        val now = LocalDateTime.of(2024, 5, 18, 14, 0)
        val hours = listOf(
            hour(now, code = 1, precipProb = 0), // 14:00 dry
            hour(now.plusHours(1), code = 2, precipProb = 10), // 15:00 dry
            hour(now.plusHours(2), code = 3, precipProb = 60), // 16:00 rain (precip >= 60%)
        )

        val summary = RainOutlook.summary(hours, labels, now)
        assertEquals("Rain from 16:00", summary)
    }

    @Test
    fun `all dry hours returns Dry for the next 12 h`() {
        val now = LocalDateTime.of(2024, 5, 18, 14, 0)
        val hours = (0 until 12).map { i ->
            hour(now.plusHours(i.toLong()), code = 1, precipProb = 10)
        }

        val summary = RainOutlook.summary(hours, labels, now)
        assertEquals("Dry for the next 12 h", summary)
    }

    @Test
    fun `rain all day returns Rain all day`() {
        val now = LocalDateTime.of(2024, 5, 18, 14, 0)
        val hours = (0 until 12).map { i ->
            hour(now.plusHours(i.toLong()), code = 61, precipProb = 80)
        }

        val summary = RainOutlook.summary(hours, labels, now)
        assertEquals("Rain all day", summary)
    }

    @Test
    fun `empty hours list returns Dry for the next 12 h`() {
        val now = LocalDateTime.of(2024, 5, 18, 14, 0)
        val summary = RainOutlook.summary(emptyList(), labels, now)
        assertEquals("Dry for the next 12 h", summary)
    }

    @Test
    fun `filters past hours prior to current hour`() {
        val now = LocalDateTime.of(2024, 5, 18, 14, 30) // 14:30
        val past1 = hour(LocalDateTime.of(2024, 5, 18, 12, 0), code = 61, precipProb = 90) // past rain
        val past2 = hour(LocalDateTime.of(2024, 5, 18, 13, 0), code = 61, precipProb = 90) // past rain
        val current = hour(LocalDateTime.of(2024, 5, 18, 14, 0), code = 1, precipProb = 10) // 14:00 dry
        val future1 = hour(LocalDateTime.of(2024, 5, 18, 15, 0), code = 1, precipProb = 10) // 15:00 dry
        val future2 = hour(LocalDateTime.of(2024, 5, 18, 16, 0), code = 61, precipProb = 80) // 16:00 rain

        val hours = listOf(past1, past2, current, future1, future2)
        val summary = RainOutlook.summary(hours, labels, now)
        // At 14:30, current hour is 14:00 (dry), next rain is at 16:00
        assertEquals("Rain from 16:00", summary)
    }

    @Test
    fun `all cached hours in the past returns null`() {
        val now = LocalDateTime.of(2024, 5, 18, 23, 30)
        val hours = (0 until 12).map { i ->
            hour(LocalDateTime.of(2024, 5, 18, 4, 0).plusHours(i.toLong()), code = 61, precipProb = 90)
        }

        assertNull(RainOutlook.summary(hours, labels, now))
    }
}
