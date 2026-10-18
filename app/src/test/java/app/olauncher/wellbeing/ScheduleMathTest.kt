package app.olauncher.wellbeing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class ScheduleMathTest {

    private val utc = ZoneOffset.UTC

    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0): Long {
        return LocalDateTime.of(year, month, day, hour, minute, second)
            .atZone(utc)
            .toInstant()
            .toEpochMilli()
    }

    @Test
    fun `before the window returns windowStart today`() {
        // Window 09:00 (540) to 21:00 (1260), interval 60 min
        val now = utcMillis(2026, 10, 18, 7, 30) // 07:30
        val next = ScheduleMath.nextFire(
            nowMillis = now,
            zone = utc,
            windowStartMin = 540,
            windowEndMin = 1260,
            intervalMin = 60,
        )
        val expected = utcMillis(2026, 10, 18, 9, 0) // 09:00 today
        assertEquals(expected, next)
    }

    @Test
    fun `inside the window returns next slot today`() {
        // Window 09:00 (540) to 21:00 (1260), interval 60 min
        // At 09:30, next slot is 10:00
        val now1 = utcMillis(2026, 10, 18, 9, 30)
        val next1 = ScheduleMath.nextFire(
            nowMillis = now1,
            zone = utc,
            windowStartMin = 540,
            windowEndMin = 1260,
            intervalMin = 60,
        )
        assertEquals(utcMillis(2026, 10, 18, 10, 0), next1)

        // At 14:15, next slot is 15:00
        val now2 = utcMillis(2026, 10, 18, 14, 15)
        val next2 = ScheduleMath.nextFire(
            nowMillis = now2,
            zone = utc,
            windowStartMin = 540,
            windowEndMin = 1260,
            intervalMin = 60,
        )
        assertEquals(utcMillis(2026, 10, 18, 15, 0), next2)
    }

    @Test
    fun `after the window rolls to tomorrow windowStart`() {
        // Window 09:00 (540) to 21:00 (1260), interval 60 min
        // At 21:30, all slots today have passed
        val now = utcMillis(2026, 10, 18, 21, 30)
        val next = ScheduleMath.nextFire(
            nowMillis = now,
            zone = utc,
            windowStartMin = 540,
            windowEndMin = 1260,
            intervalMin = 60,
        )
        val expectedTomorrow = utcMillis(2026, 10, 19, 9, 0) // 09:00 tomorrow
        assertEquals(expectedTomorrow, next)

        // Late at night (23:55)
        val lateNight = utcMillis(2026, 10, 18, 23, 55)
        val nextLate = ScheduleMath.nextFire(
            nowMillis = lateNight,
            zone = utc,
            windowStartMin = 540,
            windowEndMin = 1260,
            intervalMin = 60,
        )
        assertEquals(expectedTomorrow, nextLate)
    }

    @Test
    fun `exactly on a slot returns next slot today`() {
        // Window 09:00 to 21:00, interval 60 min
        // Exactly on 10:00:00.000 -> next slot is 11:00:00.000
        val now = utcMillis(2026, 10, 18, 10, 0, 0)
        val next = ScheduleMath.nextFire(
            nowMillis = now,
            zone = utc,
            windowStartMin = 540,
            windowEndMin = 1260,
            intervalMin = 60,
        )
        assertEquals(utcMillis(2026, 10, 18, 11, 0, 0), next)

        // Exactly on the first slot 09:00:00.000 -> next slot is 10:00:00.000
        val onFirstSlot = utcMillis(2026, 10, 18, 9, 0, 0)
        val nextFromFirst = ScheduleMath.nextFire(
            nowMillis = onFirstSlot,
            zone = utc,
            windowStartMin = 540,
            windowEndMin = 1260,
            intervalMin = 60,
        )
        assertEquals(utcMillis(2026, 10, 18, 10, 0, 0), nextFromFirst)
    }

    @Test
    fun `exactly on the last slot of the day rolls to tomorrow windowStart`() {
        // Window 09:00 to 21:00, interval 60 min
        // Last slot is 21:00. Exactly on 21:00:00.000 -> rolls to tomorrow 09:00
        val onLastSlot = utcMillis(2026, 10, 18, 21, 0, 0)
        val next = ScheduleMath.nextFire(
            nowMillis = onLastSlot,
            zone = utc,
            windowStartMin = 540,
            windowEndMin = 1260,
            intervalMin = 60,
        )
        assertEquals(utcMillis(2026, 10, 19, 9, 0, 0), next)
    }

    @Test
    fun `DST day in Europe Berlin spring forward transition`() {
        // Sunday, March 29, 2026: clocks jump from 02:00 CET (+01:00) to 03:00 CEST (+02:00)
        val berlin = ZoneId.of("Europe/Berlin")

        // 08:00 Berlin time on DST spring-forward day
        val nowZdt = LocalDateTime.of(2026, 3, 29, 8, 0).atZone(berlin)
        val nowMillis = nowZdt.toInstant().toEpochMilli()

        val nextMillis = ScheduleMath.nextFire(
            nowMillis = nowMillis,
            zone = berlin,
            windowStartMin = 540, // 09:00
            windowEndMin = 1260,  // 21:00
            intervalMin = 60,
        )

        val nextZdt = Instant.ofEpochMilli(nextMillis).atZone(berlin)
        assertEquals(LocalDate.of(2026, 3, 29), nextZdt.toLocalDate())
        assertEquals(LocalTime.of(9, 0), nextZdt.toLocalTime())
        assertEquals(ZoneOffset.ofHours(2), nextZdt.offset)

        // After the window on DST day: rolls to tomorrow at 09:00
        val afterWindowZdt = LocalDateTime.of(2026, 3, 29, 21, 30).atZone(berlin)
        val nextAfterMillis = ScheduleMath.nextFire(
            nowMillis = afterWindowZdt.toInstant().toEpochMilli(),
            zone = berlin,
            windowStartMin = 540,
            windowEndMin = 1260,
            intervalMin = 60,
        )
        val nextAfterZdt = Instant.ofEpochMilli(nextAfterMillis).atZone(berlin)
        assertEquals(LocalDate.of(2026, 3, 30), nextAfterZdt.toLocalDate())
        assertEquals(LocalTime.of(9, 0), nextAfterZdt.toLocalTime())
        assertEquals(ZoneOffset.ofHours(2), nextAfterZdt.offset)
    }

    @Test
    fun `DST day in Europe Berlin autumn fall back transition`() {
        // Sunday, October 25, 2026: clocks jump from 03:00 CEST (+02:00) to 02:00 CET (+01:00)
        val berlin = ZoneId.of("Europe/Berlin")

        val nowZdt = LocalDateTime.of(2026, 10, 25, 8, 0).atZone(berlin)
        val nowMillis = nowZdt.toInstant().toEpochMilli()

        val nextMillis = ScheduleMath.nextFire(
            nowMillis = nowMillis,
            zone = berlin,
            windowStartMin = 540,
            windowEndMin = 1260,
            intervalMin = 60,
        )

        val nextZdt = Instant.ofEpochMilli(nextMillis).atZone(berlin)
        assertEquals(LocalDate.of(2026, 10, 25), nextZdt.toLocalDate())
        assertEquals(LocalTime.of(9, 0), nextZdt.toLocalTime())
        assertEquals(ZoneOffset.ofHours(1), nextZdt.offset)
    }

    @Test
    fun `window crossing midnight is rejected`() {
        val now = utcMillis(2026, 10, 18, 12, 0)
        assertThrows(IllegalArgumentException::class.java) {
            ScheduleMath.nextFire(
                nowMillis = now,
                zone = utc,
                windowStartMin = 1300, // 21:40
                windowEndMin = 300,   // 05:00 next day
                intervalMin = 60,
            )
        }
    }

    @Test
    fun `window start equal to window end is rejected`() {
        val now = utcMillis(2026, 10, 18, 12, 0)
        assertThrows(IllegalArgumentException::class.java) {
            ScheduleMath.nextFire(
                nowMillis = now,
                zone = utc,
                windowStartMin = 600,
                windowEndMin = 600,
                intervalMin = 60,
            )
        }
    }

    @Test
    fun `invalid interval or bounds are rejected`() {
        val now = utcMillis(2026, 10, 18, 12, 0)
        assertThrows(IllegalArgumentException::class.java) {
            ScheduleMath.nextFire(now, utc, 540, 1260, intervalMin = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ScheduleMath.nextFire(now, utc, 540, 1260, intervalMin = -15)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ScheduleMath.nextFire(now, utc, windowStartMin = -1, windowEndMin = 1260, intervalMin = 60)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ScheduleMath.nextFire(now, utc, windowStartMin = 540, windowEndMin = 1500, intervalMin = 60)
        }
    }

    @Test
    fun `non-aligned window end respects latest slot within window`() {
        // Window 09:00 (540) to 12:30 (750), interval 60 min
        // Slots: 09:00 (540), 10:00 (600), 11:00 (660), 12:00 (720).
        // 13:00 (780) is > 750, so outside the window.
        val now = utcMillis(2026, 10, 18, 12, 5)
        val next = ScheduleMath.nextFire(
            nowMillis = now,
            zone = utc,
            windowStartMin = 540,
            windowEndMin = 750,
            intervalMin = 60,
        )
        // Since 12:05 is after the last valid slot (12:00), rolls to tomorrow 09:00
        assertEquals(utcMillis(2026, 10, 19, 9, 0), next)
    }

    @Test
    fun `custom interval spacing works as expected`() {
        // Window 09:00 (540) to 21:00 (1260), interval 90 min (1.5 h)
        // Slots: 09:00 (540), 10:30 (630), 12:00 (720), ...
        val now = utcMillis(2026, 10, 18, 9, 15)
        val next = ScheduleMath.nextFire(
            nowMillis = now,
            zone = utc,
            windowStartMin = 540,
            windowEndMin = 1260,
            intervalMin = 90,
        )
        assertEquals(utcMillis(2026, 10, 18, 10, 30), next)
    }
}
