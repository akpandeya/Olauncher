package app.olauncher.wellbeing

import org.junit.Assert.assertEquals
import org.junit.Test

class WalkDetectorTest {
    private val min = 60_000L
    private val hour = 60 * min
    private val dayStart = 1_700_000_000_000L - (1_700_000_000_000L % (24 * hour))
    private val dayEnd = dayStart + 24 * hour

    private fun steps(startMin: Long, lenMin: Long, count: Long, origin: String = "app.a") =
        StepSpan(dayStart + 10 * hour + startMin * min, dayStart + 10 * hour + (startMin + lenMin) * min, count, origin)

    private fun detect(s: List<StepSpan>, w: List<WalkSpan> = emptyList()) =
        WalkDetector.detect(s, w, dayStart, dayEnd)

    @Test fun emptyInputIsZero() {
        assertEquals(WalkDetection(0, 0, 0), detect(emptyList()))
    }

    @Test fun boutOfExactly200StepsCounts() {
        assertEquals(WalkDetection(1, 0, 1), detect(listOf(steps(0, 3, 200))))
    }

    @Test fun boutOf199StepsDoesNot() {
        assertEquals(WalkDetection(0, 0, 0), detect(listOf(steps(0, 3, 199))))
    }

    @Test fun gapOfFiveMinutesMerges() {
        // active minutes 0-1, then 5 empty minutes (2..6), then 7-8: 120 + 120 = 240
        val r = detect(listOf(steps(0, 2, 120), steps(7, 2, 120)))
        assertEquals(WalkDetection(1, 0, 1), r)
    }

    @Test fun gapOfSixMinutesDoesNotMerge() {
        val r = detect(listOf(steps(0, 2, 120), steps(8, 2, 120)))
        assertEquals(WalkDetection(0, 0, 0), r)
    }

    @Test fun twoOriginsOnSameMinutesTakeMaxNotSum() {
        // Sum would be 300 (a walk); max is 150 (not a walk).
        val r = detect(listOf(steps(0, 3, 150, "app.a"), steps(0, 3, 120, "app.b")))
        assertEquals(WalkDetection(0, 0, 0), r)
        // Max of 210 vs 120 gives 210, one walk.
        val r2 = detect(listOf(steps(0, 3, 210, "app.a"), steps(0, 3, 120, "app.b")))
        assertEquals(WalkDetection(1, 0, 1), r2)
    }

    @Test fun boutOverlappingSessionIsDroppedAndSessionCountsOnce() {
        val start = dayStart + 10 * hour
        val session = WalkSpan(start - 2 * min, start + 5 * min)
        val r = detect(listOf(steps(0, 4, 400), steps(60, 4, 300)), listOf(session))
        assertEquals(WalkDetection(2, 1, 1), r)
    }

    @Test fun dailyAggregateRecordIsIgnored() {
        val aggregate = StepSpan(dayStart, dayEnd, 9_674, "com.samsung.shealth")
        val r = detect(listOf(aggregate, steps(0, 3, 90)))
        assertEquals(WalkDetection(0, 0, 0), r)
    }

    @Test fun recordOverTwelveHoursIsIgnoredButTwelveHoursIsKept() {
        val over = StepSpan(dayStart, dayStart + 12 * hour + min, 5_000, "x")
        assertEquals(WalkDetection(0, 0, 0), detect(listOf(over)))
        val exact = StepSpan(dayStart, dayStart + 12 * hour, 43_200, "x")
        assertEquals(1, detect(listOf(exact)).boutWalks)
    }

    @Test fun slowSpreadUnderPaceIsNotAWalk() {
        // 12 h at 10 steps/min is not walking.
        val slow = StepSpan(dayStart, dayStart + 12 * hour, 7_200, "x")
        assertEquals(WalkDetection(0, 0, 0), detect(listOf(slow)))
        // 300 steps over 10 min = 30/min: not a walk; 600 over 10 min = 60/min: a walk.
        assertEquals(WalkDetection(0, 0, 0), detect(listOf(steps(0, 10, 300))))
        assertEquals(WalkDetection(1, 0, 1), detect(listOf(steps(0, 10, 600))))
    }

    @Test fun shownWalksAreAutoPlusManualTaps() {
        assertEquals(4, WalkCount.shown(3, 1))
        assertEquals(1, WalkCount.shown(null, 1))
        assertEquals(3, WalkCount.shown(3, 0))
    }

    @Test fun stepsCrossingMidnightAreClippedToDay() {
        // 10 minutes straddling the start of the day, 20 steps per minute: only 5 minutes (100 steps) in the day.
        val r = detect(listOf(StepSpan(dayStart - 5 * min, dayStart + 5 * min, 200, "a")))
        assertEquals(WalkDetection(0, 0, 0), r)
        // 10 minutes, 60 per minute: 5 minutes inside = 300 steps.
        val r2 = detect(listOf(StepSpan(dayStart - 5 * min, dayStart + 5 * min, 600, "a")))
        assertEquals(WalkDetection(1, 0, 1), r2)
    }

    @Test fun sessionCrossingMidnightCountsOnTheDayItOverlaps() {
        val s = WalkSpan(dayStart - 10 * min, dayStart + 10 * min)
        assertEquals(WalkDetection(1, 1, 0), detect(emptyList(), listOf(s)))
        val before = WalkSpan(dayStart - 20 * min, dayStart)
        assertEquals(WalkDetection(0, 0, 0), detect(emptyList(), listOf(before)))
    }

    @Test fun allPagesAreReadAndBoutsFromBothPagesCount() = kotlinx.coroutines.runBlocking {
        val pages = mapOf<String?, Pair<List<StepSpan>, String?>>(
            null to (listOf(steps(0, 3, 300)) to "p2"),
            "p2" to (listOf(steps(60, 3, 300)) to null),
        )
        val all = readAllPages { token -> pages.getValue(token) }
        assertEquals(2, all.size)
        assertEquals(WalkDetection(2, 0, 2), detect(all))
    }
}
