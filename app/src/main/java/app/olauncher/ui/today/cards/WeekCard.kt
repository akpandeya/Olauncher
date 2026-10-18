package app.olauncher.ui.today.cards

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import app.olauncher.R
import app.olauncher.databinding.CardWeekBinding
import app.olauncher.ui.today.TodayCard
import app.olauncher.wellbeing.DaySnapshot
import app.olauncher.wellbeing.Review
import app.olauncher.wellbeing.UsageSnapshotStore
import app.olauncher.wellbeing.WeeklyReview
import java.time.LocalDate

object WeekCard : TodayCard {

    const val KEY = "week"

    override val key: String = KEY
    override val titleRes: Int = R.string.last_7_days
    override val defaultEnabled: Boolean = true

    var storeProvider: ((Context) -> UsageSnapshotStore)? = null
    var todaySupplier: () -> Long = { LocalDate.now().toEpochDay() }

    internal var binding: CardWeekBinding? = null
    internal var currentReview: Review? = null

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

    override fun createView(inflater: LayoutInflater, parent: ViewGroup, fragment: Fragment): View {
        val viewBinding = CardWeekBinding.inflate(inflater, parent, false)
        binding = viewBinding

        viewBinding.root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                if (binding === viewBinding) {
                    binding = null
                }
            }
        })

        render()
        return viewBinding.root
    }

    override fun onResume() {
        render()
    }

    override fun onResume(view: View) {
        render()
    }

    fun render(review: Review) {
        currentReview = review
        val b = binding ?: return
        val context = b.root.context

        b.tvDistractingApps.isVisible = review.distractingAppsLine != null
        b.tvDistractingApps.text = review.distractingAppsLine?.let { WeekCardText.format(context, it) }

        b.tvWeekComparison.isVisible = review.weekComparisonLine != null
        b.tvWeekComparison.text = review.weekComparisonLine?.let { WeekCardText.format(context, it) }

        b.tvTopApps.isVisible = review.topAppsLine != null
        b.tvTopApps.text = review.topAppsLine?.let { WeekCardText.format(context, it) }

        b.tvOverAllowance.isVisible = review.overAllowanceLine != null
        b.tvOverAllowance.text = review.overAllowanceLine?.let { WeekCardText.format(context, it) }

        b.tvPause.isVisible = review.pauseLine != null
        b.tvPause.text = review.pauseLine?.let { WeekCardText.format(context, it) }

        b.tvNudges.isVisible = review.nudgesLine != null
        b.tvNudges.text = review.nudgesLine?.let { WeekCardText.format(context, it) }
    }

    fun render() {
        val b = binding ?: return
        val context = b.root.context ?: return
        val store = getUsageSnapshotStore(context)
        val today = todaySupplier()
        val snapshots = store.getAll()
        val review = WeeklyReview.summarise(snapshots, today)
        render(review)
    }
}
