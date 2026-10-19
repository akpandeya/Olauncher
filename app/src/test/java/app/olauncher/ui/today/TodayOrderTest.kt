package app.olauncher.ui.today

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
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
}
