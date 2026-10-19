package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class HomeSetsTest {

    private val starts = HomeSetStarts(morning = 6 * 60, day = 9 * 60, evening = 18 * 60)

    private fun at(h: Int, m: Int = 0, enabled: Boolean = true, hasApps: (HomeSet) -> Boolean = { true }) =
        HomeSets.active(h * 60 + m, starts, enabled, hasApps)

    @Test
    fun offAlwaysGivesDay() {
        listOf(0, 6, 9, 18, 23).forEach { assertEquals(HomeSet.DAY, at(it, enabled = false)) }
    }

    @Test
    fun boundariesSwitchExactlyAtStart() {
        assertEquals(HomeSet.MORNING, at(6, 0))
        assertEquals(HomeSet.MORNING, at(8, 59))
        assertEquals(HomeSet.DAY, at(9, 0))
        assertEquals(HomeSet.DAY, at(17, 59))
        assertEquals(HomeSet.EVENING, at(18, 0))
        assertEquals(HomeSet.EVENING, at(23, 59))
    }

    @Test
    fun eveningWrapsPastMidnightUntilMorning() {
        assertEquals(HomeSet.EVENING, at(0, 0))
        assertEquals(HomeSet.EVENING, at(3, 30))
        assertEquals(HomeSet.EVENING, at(5, 59))
        assertEquals(HomeSet.MORNING, at(6, 0))
    }

    @Test
    fun customStartsWrapWhenEveningStartsAfterMidnight() {
        val s = HomeSetStarts(morning = 7 * 60, day = 10 * 60, evening = 30)
        assertEquals(HomeSet.EVENING, HomeSets.active(30, s, true))
        assertEquals(HomeSet.EVENING, HomeSets.active(6 * 60 + 59, s, true))
        assertEquals(HomeSet.MORNING, HomeSets.active(7 * 60, s, true))
        assertEquals(HomeSet.DAY, HomeSets.active(23 * 60, s, true))
        // before 00:30 the day set is still running (it began the previous morning)
        assertEquals(HomeSet.DAY, HomeSets.active(10, s, true))
    }

    @Test
    fun emptySetFallsBackToDay() {
        val onlyDay: (HomeSet) -> Boolean = { it == HomeSet.DAY }
        assertEquals(HomeSet.DAY, at(7, hasApps = onlyDay))
        assertEquals(HomeSet.DAY, at(20, hasApps = onlyDay))
        val noMorning: (HomeSet) -> Boolean = { it != HomeSet.MORNING }
        assertEquals(HomeSet.DAY, at(7, hasApps = noMorning))
        assertEquals(HomeSet.EVENING, at(20, hasApps = noMorning))
    }

    @Test
    fun equalStartsPreferDay() {
        val s = HomeSetStarts(morning = 9 * 60, day = 9 * 60, evening = 18 * 60)
        assertEquals(HomeSet.DAY, HomeSets.active(10 * 60, s, true))
    }

    @Test
    fun outOfRangeMinutesAreNormalized() {
        assertEquals(HomeSet.EVENING, HomeSets.active(-60, starts, true))
        assertEquals(HomeSet.MORNING, HomeSets.active(30 * 60, starts, true))
        assertEquals(1439, HomeSets.normalize(-1))
    }

    private fun wallMinutes(z: ZonedDateTime) = HomeSets.minutesOfDay(z.hour, z.minute)

    @Test
    fun springForwardDayFollowsWallClock() {
        val ny = ZoneId.of("America/New_York")
        // 2026-03-08: 02:00 jumps to 03:00
        val before = ZonedDateTime.of(2026, 3, 8, 1, 59, 0, 0, ny)
        val after = before.plusMinutes(1)
        assertEquals(3, after.hour)
        assertEquals(HomeSet.EVENING, HomeSets.active(wallMinutes(before), starts, true))
        assertEquals(HomeSet.EVENING, HomeSets.active(wallMinutes(after), starts, true))
        val six = ZonedDateTime.of(2026, 3, 8, 6, 0, 0, 0, ny)
        assertEquals(HomeSet.MORNING, HomeSets.active(wallMinutes(six), starts, true))
        // a 03:00 start still triggers on the skipped-hour day
        val s = HomeSetStarts(morning = 2 * 60 + 30, day = 9 * 60, evening = 18 * 60)
        assertEquals(HomeSet.MORNING, HomeSets.active(wallMinutes(after), s, true))
    }

    @Test
    fun fallBackDayRepeatedHourKeepsSameSet() {
        val ny = ZoneId.of("America/New_York")
        // 2026-11-01: 01:30 happens twice
        val first = ZonedDateTime.of(2026, 11, 1, 1, 30, 0, 0, ny).withEarlierOffsetAtOverlap()
        val second = first.withLaterOffsetAtOverlap()
        assertEquals(first.hour, second.hour)
        assertTrue(second.toInstant().isAfter(first.toInstant()))
        assertEquals(HomeSets.active(wallMinutes(first), starts, true), HomeSets.active(wallMinutes(second), starts, true))
        assertEquals(HomeSet.EVENING, HomeSets.active(wallMinutes(second), starts, true))
        val nine = ZonedDateTime.of(2026, 11, 1, 9, 0, 0, 0, ny)
        assertEquals(HomeSet.DAY, HomeSets.active(wallMinutes(nine), starts, true))
    }

    @Test
    fun slotKeysKeepDayNamesAndPrefixOthers() {
        assertEquals("APP_NAME_3", HomeSets.slotKey(HomeSet.DAY, HomeSlotField.NAME, 3))
        assertEquals("APP_ACTIVITY_CLASS_NAME_1", HomeSets.slotKey(HomeSet.DAY, HomeSlotField.ACTIVITY, 1))
        assertEquals("IS_SHORTCUT_8", HomeSets.slotKey(HomeSet.DAY, HomeSlotField.IS_SHORTCUT, 8))
        assertEquals("HOME_SET_MORNING_APP_PACKAGE_2", HomeSets.slotKey(HomeSet.MORNING, HomeSlotField.PACKAGE, 2))
        assertEquals("HOME_SET_EVENING_APP_USER_5", HomeSets.slotKey(HomeSet.EVENING, HomeSlotField.USER, 5))
        assertEquals("HOME_SET_EVENING_SHORTCUT_ID_4", HomeSets.slotKey(HomeSet.EVENING, HomeSlotField.SHORTCUT_ID, 4))
    }

    @Test
    fun flagsRoundTrip() {
        for (set in HomeSet.values()) for (slot in 1..8) {
            assertEquals(HomeSlotRef(set, slot), HomeSets.fromFlag(HomeSets.flagFor(set, slot)))
        }
        assertNull(HomeSets.fromFlag(100))
        assertNull(HomeSets.fromFlag(11))
    }
}
