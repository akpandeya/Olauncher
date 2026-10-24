package app.olauncher.ui.today.cards

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.databinding.CardWeekBinding
import app.olauncher.databinding.ItemWeekAppBinding
import app.olauncher.ui.today.TodayCard
import app.olauncher.ui.today.TodayOrder
import app.olauncher.wellbeing.DaySnapshot
import app.olauncher.wellbeing.UsageSnapshotStore
import app.olauncher.wellbeing.WeekBento
import app.olauncher.wellbeing.WellbeingBento
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

/** Per-view state of the 7 days side; lives and dies with the card's root view. */
internal class WeekCardView(private val binding: CardWeekBinding) {

    var current: WeekBento? = null
        private set

    fun render(bento: WeekBento) {
        current = bento
        val b = binding
        val context = b.root.context
        b.tvWeekTotal.text = WeekCardText.formatDuration(context, bento.totalMinutes)

        val change = bento.changePercent
        b.tvWeekChange.text = when {
            change == null -> context.getString(R.string.wellbeing_week_no_change)
            change < 0 -> context.getString(R.string.week_review_less_than_week_before, -change)
            else -> context.getString(R.string.week_review_more_than_week_before, change)
        }

        b.dayBars.setBars(bento.bars, bento.scaleMinutes, bento.allowanceMin)
        val allowanceText = when {
            bento.allowanceMin <= 0 -> null
            bento.daysOverAllowance > 0 ->
                context.getString(R.string.week_review_over_allowance, bento.daysOverAllowance, WellbeingBento.DAYS)
            else -> context.getString(R.string.wellbeing_week_allowance_line, WeekCardText.formatDuration(context, bento.allowanceMin))
        }
        b.tvWeekAllowance.isVisible = allowanceText != null
        b.tvWeekAllowance.text = allowanceText
        b.dayBars.contentDescription = context.getString(
            R.string.wellbeing_week_bars_description,
            allowanceText.orEmpty(),
        )

        b.llTopApps.removeAllViews()
        val inflater = LayoutInflater.from(context)
        if (bento.topApps.isEmpty()) {
            val row = ItemWeekAppBinding.inflate(inflater, b.llTopApps, false)
            row.tvAppName.setText(R.string.wellbeing_week_no_apps)
            row.tvAppTime.isVisible = false
            b.llTopApps.addView(row.root)
        }
        for (app in bento.topApps) {
            val row = ItemWeekAppBinding.inflate(inflater, b.llTopApps, false)
            row.tvAppName.text = app.label
            row.tvAppTime.text = WeekCardText.formatDuration(context, app.minutes)
            b.llTopApps.addView(row.root)
        }

        b.tvWeekNotNow.text = bento.notNow.toString()
        val topWhy = bento.topWhy
        b.tvWeekNotNowSub.text = if (topWhy != null) {
            context.getString(R.string.wellbeing_opened_anyway_mostly, bento.opened, WeekCardText.formatWhy(context, topWhy))
        } else {
            context.getString(R.string.wellbeing_week_not_now_sub, bento.opened)
        }
        b.tvWeekWaterWalk.text = "${bento.water} · ${bento.walk}"
    }

    /** Stored days only, for a card shown on its own; the Wellbeing card adds today's live numbers. */
    fun render() {
        val context = binding.root.context ?: return
        val store = WeekCard.getUsageSnapshotStore(context)
        val today = WeekCard.todaySupplier()
        render(WellbeingBento.week(store.getAll(), today, DaySnapshot(epochDay = today), Prefs(context).pauseAllowanceMin))
    }
}
