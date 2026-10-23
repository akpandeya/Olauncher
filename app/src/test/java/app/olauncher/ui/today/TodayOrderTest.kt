package app.olauncher.ui.today

import app.olauncher.glance.CalendarEvent
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayOrderTest {

    @Test
    fun ordersByPriorityDescending() {
        val out = TodayOrder.order(listOf(Ranked("a", 10), Ranked("b", 99), Ranked("c", 50)))
        assertEquals(listOf("b", "c", "a"), out.map { it.key })
    }

    @Test
    fun tiesKeepIncomingOrder() {
        val out = TodayOrder.order(listOf(Ranked("x", 5), Ranked("y", 5), Ranked("z", 7), Ranked("w", 5)))
        assertEquals(listOf("z", "x", "y", "w"), out.map { it.key })
    }

    @Test
    fun emptyStaysEmpty() {
        assertEquals(emptyList<Ranked>(), TodayOrder.order(emptyList()))
    }

    @Test
    fun defaultPriorityFallsWithIndex() {
        assertEquals(50, TodayOrder.defaultPriority(0))
        assertEquals(47, TodayOrder.defaultPriority(3))
    }

    @Test
    fun nextUpWithinTwoHoursWins() {
        val now = 1_000_000L
        assertEquals(100, TodayOrder.nextUpPriority(now + 30 * 60_000L, now, 48))
        assertEquals(100, TodayOrder.nextUpPriority(now + 2 * 3_600_000L, now, 48))
        assertEquals(48, TodayOrder.nextUpPriority(now + 2 * 3_600_000L + 1, now, 48))
        assertEquals(48, TodayOrder.nextUpPriority(now - 1, now, 48))
        assertEquals(48, TodayOrder.nextUpPriority(null, now, 48))
    }

    @Test
    fun heldWinsOnlyWhenSomethingIsHeld() {
        assertEquals(95, TodayOrder.heldPriority(true, 47))
        assertEquals(47, TodayOrder.heldPriority(false, 47))
    }

    @Test
    fun weekRisesOnSundayAndMondayOnly() {
        assertEquals(90, TodayOrder.weekPriority(DayOfWeek.SUNDAY, 44))
        assertEquals(90, TodayOrder.weekPriority(DayOfWeek.MONDAY, 44))
        assertEquals(44, TodayOrder.weekPriority(DayOfWeek.TUESDAY, 44))
        assertEquals(44, TodayOrder.weekPriority(DayOfWeek.SATURDAY, 44))
    }

    @Test
    fun wellbeingRisesAfterSix() {
        assertEquals(46, TodayOrder.wellbeingPriority(17, 46))
        assertEquals(70, TodayOrder.wellbeingPriority(18, 46))
        assertEquals(70, TodayOrder.wellbeingPriority(23, 46))
    }

    @Test
    fun heldAndNextUpBeatEverythingElse() {
        val cards = listOf(
            Ranked("saved", TodayOrder.defaultPriority(2)),
            Ranked("held", TodayOrder.heldPriority(true, 47)),
            Ranked("wellbeing", TodayOrder.wellbeingPriority(19, 46)),
            Ranked("next_up", TodayOrder.NEXT_UP_PRIORITY),
        )
        assertEquals(listOf("next_up", "held", "wellbeing", "saved"), TodayOrder.order(cards).map { it.key })
    }

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private fun at(hour: Int, minute: Int = 0, day: Int = 20): Instant =
        ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, zone).toInstant()

    private fun event(title: String, start: Instant, end: Instant, allDay: Boolean = false) =
        CalendarEvent(id = title.hashCode().toLong(), title = title, start = start, end = end, allDay = allDay)

    @Test
    fun nextUpPinnedWhileAMeetingIsRunning() {
        val standup = event("Standup", at(9, 30), at(10, 0))
        assertTrue(TodayOrder.nextUpPinned(listOf(standup), at(9, 45), zone))
    }

    @Test
    fun nextUpPinnedForAMeetingLaterToday() {
        val review = event("Review", at(16), at(17))
        assertTrue(TodayOrder.nextUpPinned(listOf(review), at(8), zone))
    }

    @Test
    fun nextUpNotPinnedOnceTodaysMeetingsAreOver() {
        val standup = event("Standup", at(9, 30), at(10, 0))
        assertFalse(TodayOrder.nextUpPinned(listOf(standup), at(10, 1), zone))
    }

    @Test
    fun nextUpNotPinnedForAllDayOrTomorrowOnly() {
        val holiday = event("Holiday", at(0), at(0, day = 21), allDay = true)
        val tomorrow = event("Dentist", at(9, day = 21), at(10, day = 21))
        assertFalse(TodayOrder.nextUpPinned(listOf(holiday, tomorrow), at(12), zone))
        assertFalse(TodayOrder.nextUpPinned(emptyList(), at(12), zone))
    }

    @Test
    fun splitPullsPinnedOutAndRanksTheRest() {
        val cards = listOf(
            Ranked("next_up", 49),
            Ranked("saved", 48),
            Ranked("wellbeing", 46),
            Ranked("week", 90),
        )
        val split = TodayOrder.split(cards, setOf("next_up"))
        assertEquals(listOf("next_up"), split.pinned)
        assertEquals(listOf("week", "saved", "wellbeing"), split.stacked)
    }

    @Test
    fun splitWithNothingPinnedIsThePlainOrder() {
        val cards = listOf(Ranked("a", 10), Ranked("b", 99), Ranked("c", 50))
        val split = TodayOrder.split(cards, emptySet())
        assertEquals(emptyList<String>(), split.pinned)
        assertEquals(listOf("b", "c", "a"), split.stacked)
    }

    @Test
    fun oldWeekKeyMapsToTheMergedWellbeingCard() {
        assertEquals("wellbeing", TodayOrder.canonicalKey("week"))
        assertEquals("wellbeing", TodayOrder.canonicalKey("wellbeing"))
        assertEquals("saved", TodayOrder.canonicalKey("saved"))
    }

    @Test
    fun oldWeekPinPinsTheMergedCard() {
        val cards = listOf(Ranked("saved", 48), Ranked("wellbeing", 46))
        val split = TodayOrder.split(cards, setOf("week"))
        assertEquals(listOf("wellbeing"), split.pinned)
        assertEquals(listOf("saved"), split.stacked)
    }

    @Test
    fun mergedEnabledUsesAnySavedChoiceElseDefault() {
        assertTrue(TodayOrder.mergedEnabled(null, null, true))
        assertFalse(TodayOrder.mergedEnabled(null, null, false))
        assertTrue(TodayOrder.mergedEnabled(false, true, false))
        assertTrue(TodayOrder.mergedEnabled(null, true, false))
        assertFalse(TodayOrder.mergedEnabled(false, null, true))
        assertFalse(TodayOrder.mergedEnabled(false, false, true))
    }

    @Test
    fun mergedPriorityTakesTheHigherOfEvening70AndSundayMonday90() {
        assertEquals(90, TodayOrder.mergedPriority(9, DayOfWeek.MONDAY, 46))
        assertEquals(90, TodayOrder.mergedPriority(20, DayOfWeek.SUNDAY, 46))
        assertEquals(70, TodayOrder.mergedPriority(20, DayOfWeek.WEDNESDAY, 46))
        assertEquals(46, TodayOrder.mergedPriority(9, DayOfWeek.WEDNESDAY, 46))
    }

    @Test
    fun rangeFollowsSavedChoiceWhenItHasContent() {
        assertEquals(WellbeingRange.WEEK, WellbeingRanges.resolve(true, true, true))
        assertEquals(WellbeingRange.TODAY, WellbeingRanges.resolve(false, true, true))
    }

    @Test
    fun rangeFallsBackToTheSideWithContent() {
        assertEquals(WellbeingRange.TODAY, WellbeingRanges.resolve(true, true, false))
        assertEquals(WellbeingRange.WEEK, WellbeingRanges.resolve(false, false, true))
        assertNull(WellbeingRanges.resolve(false, false, false))
    }

    @Test
    fun switchShowsOnlyWhenBothSidesHaveContent() {
        assertTrue(WellbeingRanges.showSwitch(true, true))
        assertFalse(WellbeingRanges.showSwitch(true, false))
        assertFalse(WellbeingRanges.showSwitch(false, true))
    }
}
