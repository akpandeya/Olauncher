package app.olauncher.ui.today.cards

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import app.olauncher.R
import app.olauncher.databinding.CardWeekBinding
import app.olauncher.ui.today.TodayCard
import app.olauncher.ui.today.TodayOrder
import app.olauncher.wellbeing.DaySnapshot
import app.olauncher.wellbeing.Review
import app.olauncher.wellbeing.UsageSnapshotStore
import app.olauncher.wellbeing.WeeklyReview
import java.time.LocalDate
import java.time.ZonedDateTime

object WeekCard : TodayCard {

    const val KEY = "week"

    override val key: String = KEY
    override val titleRes: Int = R.string.last_7_days
    override val emoji: String = "📊"
    override val defaultEnabled: Boolean = true

    var storeProvider: ((Context) -> UsageSnapshotStore)? = null
    var todaySupplier: () -> Long = { LocalDate.now().toEpochDay() }

    fun getUsageSnapshotStore(context: Context): UsageSnapshotStore {
        return storeProvider?.invoke(context) ?: UsageSnapshotStore(context)
    }

    override fun isAvailable(context: Context): Boolean {
        val store = getUsageSnapshotStore(context)
        val today = todaySupplier()
        return isAvailable(store.getAll(), today)
    }

    fun isAvailable(snapshots: List<DaySnapshot>, today: Long = todaySupplier()): Boolean {
        val last7Days = (today - 7)..(today - 1)
        val inWindow = snapshots.filter { it.epochDay in last7Days }
        return inWindow.size >= 2 && inWindow.any { hasRealData(it) }
    }

    /** True when the day holds usage or a non-zero pause/nudge counter, not just an empty row. */
    internal fun hasRealData(snapshot: DaySnapshot): Boolean =
        (snapshot.distractingMinutes ?: 0) > 0 ||
            (snapshot.distractingOpens ?: 0) > 0 ||
            snapshot.notNow > 0 ||
            snapshot.opened > 0 ||
            snapshot.water > 0 ||
            snapshot.walk > 0

    fun isAvailable(snapshots: List<DaySnapshot>, today: LocalDate): Boolean =
        isAvailable(snapshots, today.toEpochDay())

    override fun priority(context: Context, now: ZonedDateTime): Int =
        TodayOrder.weekPriority(now.dayOfWeek, super.priority(context, now))

    override fun createView(inflater: LayoutInflater, parent: ViewGroup, fragment: Fragment): View {
        val viewBinding = CardWeekBinding.inflate(inflater, parent, false)
        val cardView = WeekCardView(viewBinding)
        viewBinding.root.tag = cardView
        cardView.render()
        return viewBinding.root
    }

    override fun onResume(view: View) {
        (view.tag as? WeekCardView)?.render()
    }
}

/** Wrapped lines of a review line start indented, so a long line reads as one item. */
internal fun hangingIndent(view: View, text: CharSequence): CharSequence {
    val indent = (HANGING_INDENT_DP * view.resources.displayMetrics.density).toInt()
    return SpannableString(text).apply {
        setSpan(LeadingMarginSpan.Standard(0, indent), 0, length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
    }
}

private const val HANGING_INDENT_DP = 16

/** Per-view state of the Week card; lives and dies with the card's root view. */
internal class WeekCardView(private val binding: CardWeekBinding) {

    var currentReview: Review? = null
        private set

    fun render(review: Review) {
        currentReview = review
        val b = binding
        val context = b.root.context
        b.tvDistractingApps.isVisible = review.distractingAppsLine != null
        b.tvDistractingApps.text = review.distractingAppsLine?.let { hangingIndent(b.root, WeekCardText.formatLine(context, it)) }

        b.tvWeekComparison.isVisible = review.weekComparisonLine != null
        b.tvWeekComparison.text = review.weekComparisonLine?.let { hangingIndent(b.root, WeekCardText.formatLine(context, it)) }

        b.tvTopApps.isVisible = review.topAppsLine != null
        b.tvTopApps.text = review.topAppsLine?.let { hangingIndent(b.root, WeekCardText.formatLine(context, it)) }

        b.tvOverAllowance.isVisible = review.overAllowanceLine != null
        b.tvOverAllowance.text = review.overAllowanceLine?.let { hangingIndent(b.root, WeekCardText.formatLine(context, it)) }

        b.tvPause.isVisible = review.pauseLine != null
        b.tvPause.text = review.pauseLine?.let { hangingIndent(b.root, WeekCardText.formatLine(context, it)) }

        b.tvNudges.isVisible = review.nudgesLine != null
        b.tvNudges.text = review.nudgesLine?.let { hangingIndent(b.root, WeekCardText.formatLine(context, it)) }
    }

    fun render() {
        val context = binding.root.context ?: return
        val store = WeekCard.getUsageSnapshotStore(context)
        val today = WeekCard.todaySupplier()
        render(WeeklyReview.summarise(store.getAll(), today))
    }
}
