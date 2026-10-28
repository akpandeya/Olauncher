package app.olauncher.ui.words

import app.olauncher.R
import app.olauncher.data.store.DrillResult
import app.olauncher.data.store.ReviewEvent
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the one result→mark mapping shared by the drill and review summary adapters
 * (#623): knew/right tick on sage, almost dot on sand, again/wrong cross on rose.
 */
class SummaryMarksTest {

    @Test
    fun `drill results map right to sage tick, almost to sand dot, wrong to rose cross`() {
        assertEquals(
            SummaryMarks.Mark(R.drawable.ic_check, R.color.accentSage),
            SummaryMarks.forDrillResult(DrillResult.RIGHT),
        )
        assertEquals(
            SummaryMarks.Mark(R.drawable.ic_dot, R.color.accentSand),
            SummaryMarks.forDrillResult(DrillResult.ALMOST),
        )
        assertEquals(
            SummaryMarks.Mark(R.drawable.ic_close, R.color.accentRose),
            SummaryMarks.forDrillResult(DrillResult.WRONG),
        )
    }

    @Test
    fun `review results map knew to sage tick, almost to sand dot, again to rose cross`() {
        assertEquals(
            SummaryMarks.Mark(R.drawable.ic_check, R.color.accentSage),
            SummaryMarks.forReviewResult(ReviewEvent.Result.KNEW),
        )
        assertEquals(
            SummaryMarks.Mark(R.drawable.ic_dot, R.color.accentSand),
            SummaryMarks.forReviewResult(ReviewEvent.Result.ALMOST),
        )
        assertEquals(
            SummaryMarks.Mark(R.drawable.ic_close, R.color.accentRose),
            SummaryMarks.forReviewResult(ReviewEvent.Result.AGAIN),
        )
    }

    @Test
    fun `both summaries resolve to the same mark for the same outcome`() {
        for ((drill, review) in listOf(
            DrillResult.RIGHT to ReviewEvent.Result.KNEW,
            DrillResult.ALMOST to ReviewEvent.Result.ALMOST,
            DrillResult.WRONG to ReviewEvent.Result.AGAIN,
        )) {
            assertEquals(SummaryMarks.forDrillResult(drill), SummaryMarks.forReviewResult(review))
        }
    }
}
