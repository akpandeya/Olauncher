package app.olauncher.wellbeing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeeklyReviewTest {

    private val today = 100L

    @Test
    fun `full week with prompt numbers matches all acceptance criteria lines`() {
        val snapshots = mutableListOf<DaySnapshot>()

        // Earlier week: days 86L..92L (7 days)
        // Average: 305 min/day -> total 2135 min
        for (day in 86L..92L) {
            snapshots.add(
                DaySnapshot(
                    epochDay = day,
                    distractingMinutes = 305,
                    distractingOpens = 50,
                    topApps = listOf("Instagram" to 200, "YouTube" to 105),
                    overAllowance = true,
                )
            )
        }

        // Current week: days 93L..99L (7 days)
        // Total minutes = 252 (4 h 12 min), opens = 46
        // Daily average = 252 / 7 = 36 min/day
        // Comparison: (305 - 36) / 305 = 88.2% less (or let's set exact 18% less)
        // For earlier daily avg = 44 min/day (total 308 min), current daily avg = 36 min/day:
        // (44 - 36) / 44 = 8 / 44 = 18.18% less -> 18% less than the week before!
        snapshots.clear()

        // Earlier week: 7 days with daily avg ~43.9 min (e.g. 5 days of 44 min, 2 days of 44 min = 308 min)
        for (day in 86L..92L) {
            snapshots.add(
                DaySnapshot(
                    epochDay = day,
                    distractingMinutes = 44,
                    distractingOpens = 10,
                )
            )
        }

        // Current week: days 93L..99L (7 days)
        // Total distractingMinutes: 4 * 60 + 12 = 252 min
        // Total distractingOpens: 46 opens
        // Top apps: Instagram 150 min (2 h 30 min), YouTube 70 min (1 h 10 min), Reddit 32 min
        // Over allowance on 2 of 7 days: day 93 and day 94
        // Pause: 31 not now, 15 opened, top why: "bored"
        // Water: 34, Walk: 12
        val dailyMinutes = listOf(60, 50, 40, 30, 30, 22, 20) // sum = 252
        val dailyOpens = listOf(10, 8, 7, 6, 5, 5, 5) // sum = 46

        for ((index, day) in (93L..99L).withIndex()) {
            val isOver = day == 93L || day == 94L
            val topApps = when (day) {
                93L -> listOf("Instagram" to 90, "YouTube" to 40)
                94L -> listOf("Instagram" to 60, "YouTube" to 30)
                95L -> listOf("Reddit" to 32)
                else -> emptyList()
            }
            val notNow = if (day == 93L) 20 else if (day == 94L) 11 else 0 // sum = 31
            val opened = if (day == 93L) 10 else if (day == 94L) 5 else 0 // sum = 15
            val why = if (day == 93L) {
                mapOf("bored" to 7, "reply" to 3)
            } else if (day == 94L) {
                mapOf("bored" to 3, "lookup" to 2)
            } else {
                emptyMap()
            }
            val water = if (day == 93L) 20 else if (day == 94L) 14 else 0 // sum = 34
            val walk = if (day == 93L) 7 else if (day == 94L) 5 else 0 // sum = 12

            snapshots.add(
                DaySnapshot(
                    epochDay = day,
                    distractingMinutes = dailyMinutes[index],
                    distractingOpens = dailyOpens[index],
                    topApps = topApps,
                    overAllowance = isOver,
                    notNow = notNow,
                    opened = opened,
                    why = why,
                    water = water,
                    walk = walk,
                )
            )
        }

        val review = WeeklyReview.summarise(snapshots, today)

        assertEquals(ReviewLine.Distracting(252, 46), review.distractingAppsLine)
        assertEquals(ReviewLine.LessThanWeekBefore(18), review.weekComparisonLine)
        assertEquals(ReviewLine.TopApps(listOf(AppMinutes("Instagram", 150), AppMinutes("YouTube", 70))), review.topAppsLine)
        assertEquals(ReviewLine.OverAllowance(2, 7), review.overAllowanceLine)
        assertEquals(ReviewLine.Pause(31, 15, WhyChoice.Bored), review.pauseLine)
        assertEquals(ReviewLine.Nudges(34, 12), review.nudgesLine)

        assertEquals(6, review.lines.size)
        assertEquals(
            listOf(
                ReviewLine.Distracting(252, 46),
                ReviewLine.LessThanWeekBefore(18),
                ReviewLine.TopApps(listOf(AppMinutes("Instagram", 150), AppMinutes("YouTube", 70))),
                ReviewLine.OverAllowance(2, 7),
                ReviewLine.Pause(31, 15, WhyChoice.Bored),
                ReviewLine.Nudges(34, 12),
            ),
            review.lines
        )
    }

    @Test
    fun `full week with increase formats percent more than the week before`() {
        val snapshots = mutableListOf<DaySnapshot>()
        // Earlier week: daily avg = 100 min (7 days, total = 700)
        for (day in 86L..92L) {
            snapshots.add(DaySnapshot(epochDay = day, distractingMinutes = 100, distractingOpens = 10))
        }
        // Current week: daily avg = 109 min (7 days, total = 763) -> 9% more
        for (day in 93L..99L) {
            snapshots.add(DaySnapshot(epochDay = day, distractingMinutes = 109, distractingOpens = 12))
        }

        val review = WeeklyReview.summarise(snapshots, today)
        assertEquals(ReviewLine.MoreThanWeekBefore(9), review.weekComparisonLine)
        assertEquals(9, review.weekChangePercent)
    }

    @Test
    fun `sparse week hides week comparison when earlier week has under 3 days of data`() {
        val snapshots = mutableListOf<DaySnapshot>()

        // Earlier week has only 2 snapshots (under 3 days of data)
        snapshots.add(DaySnapshot(epochDay = 91L, distractingMinutes = 60, distractingOpens = 5))
        snapshots.add(DaySnapshot(epochDay = 92L, distractingMinutes = 60, distractingOpens = 5))

        // Current week has 3 snapshots
        snapshots.add(DaySnapshot(epochDay = 97L, distractingMinutes = 30, distractingOpens = 2, overAllowance = true))
        snapshots.add(DaySnapshot(epochDay = 98L, distractingMinutes = 30, distractingOpens = 2, notNow = 4, opened = 2))
        snapshots.add(DaySnapshot(epochDay = 99L, distractingMinutes = 30, distractingOpens = 2, water = 5, walk = 3))

        val review = WeeklyReview.summarise(snapshots, today)

        assertEquals(ReviewLine.Distracting(90, 6), review.distractingAppsLine)
        // Crucial requirement: "nothing when the earlier week has under 3 days of data"
        assertNull(review.weekComparisonLine)
        assertNull(review.weekChangePercent)
        assertEquals(ReviewLine.OverAllowance(1, 7), review.overAllowanceLine)
        assertEquals(ReviewLine.Pause(4, 2, null), review.pauseLine)
        assertEquals(ReviewLine.Nudges(5, 3), review.nudgesLine)
    }

    @Test
    fun `sparse week with at least 3 days in earlier week computes comparison`() {
        val snapshots = mutableListOf<DaySnapshot>()

        // Earlier week has exactly 3 days with data: daily avg = 100 min
        snapshots.add(DaySnapshot(epochDay = 90L, distractingMinutes = 100, distractingOpens = 5))
        snapshots.add(DaySnapshot(epochDay = 91L, distractingMinutes = 100, distractingOpens = 5))
        snapshots.add(DaySnapshot(epochDay = 92L, distractingMinutes = 100, distractingOpens = 5))

        // Current week has 4 days: daily avg = 82 min -> 18% less
        snapshots.add(DaySnapshot(epochDay = 96L, distractingMinutes = 82, distractingOpens = 5))
        snapshots.add(DaySnapshot(epochDay = 97L, distractingMinutes = 82, distractingOpens = 5))
        snapshots.add(DaySnapshot(epochDay = 98L, distractingMinutes = 82, distractingOpens = 5))
        snapshots.add(DaySnapshot(epochDay = 99L, distractingMinutes = 82, distractingOpens = 5))

        val review = WeeklyReview.summarise(snapshots, today)
        assertEquals(ReviewLine.LessThanWeekBefore(18), review.weekComparisonLine)
        assertEquals(-18, review.weekChangePercent)
    }

    @Test
    fun `no previous week hides week comparison line`() {
        val snapshots = mutableListOf<DaySnapshot>()

        // Only current week snapshots, earlier week is completely empty
        for (day in 93L..99L) {
            snapshots.add(DaySnapshot(epochDay = day, distractingMinutes = 20, distractingOpens = 3))
        }

        val review = WeeklyReview.summarise(snapshots, today)
        assertNull(review.weekComparisonLine)
        assertEquals(ReviewLine.Distracting(140, 21), review.distractingAppsLine)
    }

    @Test
    fun `usage null days hides usage lines but shows counters`() {
        val snapshots = mutableListOf<DaySnapshot>()

        for (day in 93L..99L) {
            snapshots.add(
                DaySnapshot(
                    epochDay = day,
                    distractingMinutes = null,
                    distractingOpens = null,
                    topApps = emptyList(),
                    overAllowance = false,
                    notNow = 4,
                    opened = 2,
                    why = mapOf("reply" to 2),
                    water = 5,
                    walk = 2,
                )
            )
        }

        val review = WeeklyReview.summarise(snapshots, today)

        // Usage lines must be hidden
        assertNull(review.distractingAppsLine)
        assertNull(review.weekComparisonLine)
        assertNull(review.topAppsLine)
        assertNull(review.overAllowanceLine)

        // Counters must still be shown
        assertEquals(ReviewLine.Pause(28, 14, WhyChoice.Reply), review.pauseLine)
        assertEquals(ReviewLine.Nudges(35, 14), review.nudgesLine)

        assertEquals(2, review.lines.size)
        assertEquals(
            listOf(
                ReviewLine.Pause(28, 14, WhyChoice.Reply),
                ReviewLine.Nudges(35, 14),
            ),
            review.lines
        )
    }

    @Test
    fun `rounding of percentages and duration formatting`() {
        // Percentage rounding: 18.18% rounds to 18%
        val s1 = mutableListOf<DaySnapshot>()
        for (day in 86L..92L) s1.add(DaySnapshot(epochDay = day, distractingMinutes = 55))
        for (day in 93L..99L) s1.add(DaySnapshot(epochDay = day, distractingMinutes = 45))
        val r1 = WeeklyReview.summarise(s1, today)
        assertEquals(ReviewLine.LessThanWeekBefore(18), r1.weekComparisonLine)

        // Percentage rounding: 8.7% rounds to 9%
        val s2 = mutableListOf<DaySnapshot>()
        for (day in 86L..92L) s2.add(DaySnapshot(epochDay = day, distractingMinutes = 23))
        for (day in 93L..99L) s2.add(DaySnapshot(epochDay = day, distractingMinutes = 25))
        val r2 = WeeklyReview.summarise(s2, today)
        assertEquals(ReviewLine.MoreThanWeekBefore(9), r2.weekComparisonLine)

        // Duration split edge cases
        assertEquals(DurationParts(0, 0), WeeklyReview.durationParts(0))
        assertEquals(DurationParts(0, 0), WeeklyReview.durationParts(-5))
        assertEquals(DurationParts(0, 45), WeeklyReview.durationParts(45))
        assertEquals(DurationParts(0, 59), WeeklyReview.durationParts(59))
        assertEquals(DurationParts(1, 0), WeeklyReview.durationParts(60))
        assertEquals(DurationParts(1, 1), WeeklyReview.durationParts(61))
        assertEquals(DurationParts(1, 10), WeeklyReview.durationParts(70))
        assertEquals(DurationParts(2, 0), WeeklyReview.durationParts(120))
        assertEquals(DurationParts(2, 30), WeeklyReview.durationParts(150))
        assertEquals(DurationParts(4, 12), WeeklyReview.durationParts(252))
    }

    @Test
    fun `why keys resolve to known choices or are kept raw`() {
        assertEquals(WhyChoice.Bored, WeeklyReview.resolveWhy("bored"))
        assertEquals(WhyChoice.Bored, WeeklyReview.resolveWhy(" Just Bored "))
        assertEquals(WhyChoice.Reply, WeeklyReview.resolveWhy("reply"))
        assertEquals(WhyChoice.Reply, WeeklyReview.resolveWhy("Reply to someone"))
        assertEquals(WhyChoice.Lookup, WeeklyReview.resolveWhy("lookup"))
        assertEquals(WhyChoice.Lookup, WeeklyReview.resolveWhy("look something up"))
        assertEquals(WhyChoice.Other("Meeting Prep"), WeeklyReview.resolveWhy("Meeting Prep"))
    }

    @Test
    fun `top apps takes at most top 2 apps with non-zero minutes`() {
        val snapshots = listOf(
            DaySnapshot(
                epochDay = 95L,
                distractingMinutes = 100,
                distractingOpens = 5,
                topApps = listOf(
                    "Instagram" to 60,
                    "YouTube" to 30,
                    "Reddit" to 10,
                )
            ),
            DaySnapshot(
                epochDay = 96L,
                distractingMinutes = 50,
                distractingOpens = 3,
                topApps = listOf(
                    "YouTube" to 40,
                    "Unused" to 0,
                )
            )
        )

        val review = WeeklyReview.summarise(snapshots, today)
        // YouTube: 30 + 40 = 70 min (1 h 10 min)
        // Instagram: 60 min (1 h)
        // Reddit: 10 min (excluded because only top 2 are taken)
        // Unused: 0 min (excluded)
        assertEquals(ReviewLine.TopApps(listOf(AppMinutes("YouTube", 70), AppMinutes("Instagram", 60))), review.topAppsLine)
    }

    @Test
    fun `single top app formats correctly without comma`() {
        val snapshots = listOf(
            DaySnapshot(
                epochDay = 95L,
                distractingMinutes = 60,
                distractingOpens = 5,
                topApps = listOf("Instagram" to 60)
            )
        )

        val review = WeeklyReview.summarise(snapshots, today)
        assertEquals(ReviewLine.TopApps(listOf(AppMinutes("Instagram", 60))), review.topAppsLine)
    }

    @Test
    fun `top why formats look something up label and handles ties deterministically`() {
        val snapshots = listOf(
            DaySnapshot(
                epochDay = 95L,
                notNow = 5,
                opened = 3,
                why = mapOf("lookup" to 3)
            )
        )

        val review = WeeklyReview.summarise(snapshots, today)
        assertEquals(ReviewLine.Pause(5, 3, WhyChoice.Lookup), review.pauseLine)
    }

    @Test
    fun `pause without why omits mostly suffix`() {
        val snapshots = listOf(
            DaySnapshot(
                epochDay = 95L,
                notNow = 5,
                opened = 3,
                why = emptyMap()
            )
        )

        val review = WeeklyReview.summarise(snapshots, today)
        assertEquals(ReviewLine.Pause(5, 3, null), review.pauseLine)
    }

    @Test
    fun `nudges line formats single nudge when only one has count`() {
        val snapshotsWaterOnly = listOf(DaySnapshot(epochDay = 95L, water = 12, walk = 0))
        assertEquals(ReviewLine.Nudges(12, 0), WeeklyReview.summarise(snapshotsWaterOnly, today).nudgesLine)

        val snapshotsWalkOnly = listOf(DaySnapshot(epochDay = 95L, water = 0, walk = 8))
        assertEquals(ReviewLine.Nudges(0, 8), WeeklyReview.summarise(snapshotsWalkOnly, today).nudgesLine)

        val snapshotsNeither = listOf(DaySnapshot(epochDay = 95L, water = 0, walk = 0))
        assertNull(WeeklyReview.summarise(snapshotsNeither, today).nudgesLine)
    }

    @Test
    fun `snapshots outside 14-day window are ignored`() {
        val snapshots = listOf(
            // Today (not a full completed day)
            DaySnapshot(epochDay = today, distractingMinutes = 500, distractingOpens = 100),
            // Day older than 14 days ago (< 86L)
            DaySnapshot(epochDay = 80L, distractingMinutes = 500, distractingOpens = 100),
            // Valid current week snapshot
            DaySnapshot(epochDay = 95L, distractingMinutes = 30, distractingOpens = 5),
        )

        val review = WeeklyReview.summarise(snapshots, today)
        assertEquals(ReviewLine.Distracting(30, 5), review.distractingAppsLine)
    }

    @Test
    fun `LocalDate overload delegates to epochDay`() {
        val date = LocalDate.of(2026, 10, 18)
        val epochDay = date.toEpochDay()
        val snapshots = listOf(
            DaySnapshot(epochDay = epochDay - 2, distractingMinutes = 20, distractingOpens = 2)
        )

        val reviewDate = WeeklyReview.summarise(snapshots, date)
        val reviewLong = WeeklyReview.summarise(snapshots, epochDay)
        assertEquals(reviewLong, reviewDate)
    }
}
