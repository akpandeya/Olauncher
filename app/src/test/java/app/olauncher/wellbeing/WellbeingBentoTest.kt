package app.olauncher.wellbeing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WellbeingBentoTest {

    private val today = 20_000L

    private fun day(
        offset: Int,
        minutes: Int? = null,
        apps: List<Pair<String, Int>> = emptyList(),
        notNow: Int = 0,
        opened: Int = 0,
        water: Int = 0,
        walk: Int = 0,
    ) = DaySnapshot(
        epochDay = today + offset,
        distractingMinutes = minutes,
        distractingOpens = minutes?.let { 1 },
        topApps = apps,
        notNow = notNow,
        opened = opened,
        water = water,
        walk = walk,
    )

    @Test
    fun `bars cover the seven days ending today, oldest first, with today flagged`() {
        val snapshots = (-6..-1).map { day(it, minutes = 10 * (it + 7)) }
        val week = WellbeingBento.week(snapshots, today, day(0, minutes = 70), allowanceMin = 0)

        assertEquals((today - 6)..today, week.bars.first().epochDay..week.bars.last().epochDay)
        assertEquals(listOf(10, 20, 30, 40, 50, 60, 70), week.bars.map { it.minutes })
        assertEquals(listOf(false, false, false, false, false, false, true), week.bars.map { it.isToday })
        assertEquals(280, week.totalMinutes)
    }

    @Test
    fun `live numbers replace a stored row for today`() {
        val stale = day(0, minutes = 5)
        val week = WellbeingBento.week(listOf(stale), today, day(0, minutes = 90), allowanceMin = 0)
        assertEquals(90, week.bars.last().minutes)
        assertEquals(90, week.totalMinutes)
    }

    @Test
    fun `days at or over the allowance are marked and counted`() {
        val snapshots = listOf(day(-3, minutes = 120), day(-2, minutes = 59), day(-1, minutes = 60))
        val week = WellbeingBento.week(snapshots, today, day(0, minutes = 30), allowanceMin = 60)

        assertEquals(2, week.daysOverAllowance)
        assertEquals(60, week.allowanceMin)
        assertTrue(week.bars.single { it.epochDay == today - 3 }.overAllowance)
        assertFalse(week.bars.single { it.epochDay == today - 2 }.overAllowance)
    }

    @Test
    fun `no allowance means nothing is over it`() {
        val week = WellbeingBento.week(listOf(day(-1, minutes = 600)), today, day(0, minutes = 900), allowanceMin = 0)
        assertEquals(0, week.daysOverAllowance)
        assertTrue(week.bars.none { it.overAllowance })
    }

    @Test
    fun `top three apps add up across days`() {
        val snapshots = listOf(
            day(-2, minutes = 100, apps = listOf("YouTube" to 60, "Insta" to 30, "Reddit" to 10)),
            day(-1, minutes = 100, apps = listOf("Insta" to 50, "YouTube" to 40, "Mail" to 5)),
        )
        val week = WellbeingBento.week(snapshots, today, day(0, minutes = 0), allowanceMin = 0)

        assertEquals(
            listOf(AppMinutes("YouTube", 100), AppMinutes("Insta", 80), AppMinutes("Reddit", 10)),
            week.topApps,
        )
    }

    @Test
    fun `an empty week gives zeros, not blanks`() {
        val week = WellbeingBento.week(emptyList(), today, DaySnapshot(epochDay = today), allowanceMin = 0)

        assertEquals(7, week.bars.size)
        assertEquals(0, week.totalMinutes)
        assertNull(week.changePercent)
        assertTrue(week.topApps.isEmpty())
        assertEquals(0, week.notNow)
        assertEquals(0, week.opened)
        assertEquals(0, week.water)
        assertEquals(0, week.walk)
        assertEquals(1, week.scaleMinutes)
    }

    @Test
    fun `pause and nudge totals include today's live counters`() {
        val snapshots = listOf(day(-1, notNow = 4, opened = 2, water = 6, walk = 1))
        val live = day(0, notNow = 5, opened = 18, water = 29, walk = 10)
        val week = WellbeingBento.week(snapshots, today, live, allowanceMin = 0)

        assertEquals(9, week.notNow)
        assertEquals(20, week.opened)
        assertEquals(35, week.water)
        assertEquals(11, week.walk)
    }

    @Test
    fun `change against the week before is negative when the week was lighter`() {
        val earlier = (-13..-7).map { day(it, minutes = 100) }
        val current = (-6..-1).map { day(it, minutes = 80) }
        val week = WellbeingBento.week(earlier + current, today, day(0, minutes = 80), allowanceMin = 0)
        assertEquals(-20, week.changePercent)
    }

    @Test
    fun `scale is the tallest bar or the allowance line`() {
        val low = WellbeingBento.week(listOf(day(-1, minutes = 30)), today, day(0, minutes = 10), allowanceMin = 90)
        assertEquals(90, low.scaleMinutes)
        val high = WellbeingBento.week(listOf(day(-1, minutes = 200)), today, day(0, minutes = 10), allowanceMin = 90)
        assertEquals(200, high.scaleMinutes)
    }

    @Test
    fun `top reason is the most chosen, ties go to the first key`() {
        assertEquals(WhyChoice.Lookup, WellbeingBento.topWhy(mapOf("reply" to 2, "lookup" to 5)))
        assertEquals(WhyChoice.Bored, WellbeingBento.topWhy(mapOf("reply" to 3, "bored" to 3)))
        assertNull(WellbeingBento.topWhy(emptyMap()))
        assertNull(WellbeingBento.topWhy(mapOf("bored" to 0)))
    }

    @Test
    fun `apps under a minute are left out and the list is capped at three`() {
        val millis = mapOf(
            "a" to 600_000L, "b" to 500_000L, "c" to 400_000L, "d" to 300_000L, "tiny" to 59_000L,
        )
        val top = WellbeingBento.topAppsOf(millis) { it.uppercase() }
        assertEquals(listOf("A" to 10, "B" to 8, "C" to 6), top)
    }
}
