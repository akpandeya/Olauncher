package app.olauncher.ui.words

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import app.olauncher.R
import app.olauncher.data.store.DrillResult
import app.olauncher.data.store.ReviewEvent

/**
 * The result→mark mapping behind the one shared summary-row skin (#623): tick on
 * sage for a pass, dot on sand for a near-miss, cross on rose for a miss. Context-free
 * so both the drill and the review thin adapters call the same mapping and cannot
 * drift apart; resolve [Mark.tint] with `ContextCompat.getColor` where a Context is
 * available.
 */
object SummaryMarks {

    /** The icon and tint resource for one summary row's mark slot. */
    data class Mark(@DrawableRes val icon: Int, @ColorRes val tint: Int)

    fun forDrillResult(result: DrillResult): Mark = when (result) {
        DrillResult.RIGHT -> PASS
        DrillResult.ALMOST -> NEAR
        DrillResult.WRONG -> MISS
    }

    fun forReviewResult(result: String): Mark = when (result) {
        ReviewEvent.Result.KNEW -> PASS
        ReviewEvent.Result.ALMOST -> NEAR
        else -> MISS
    }

    private val PASS = Mark(R.drawable.ic_check, R.color.accentSage)
    private val NEAR = Mark(R.drawable.ic_dot, R.color.accentSand)
    private val MISS = Mark(R.drawable.ic_close, R.color.accentRose)
}
