package app.olauncher.ui.today.cards

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import app.olauncher.R
import app.olauncher.databinding.CardNextUpBinding
import app.olauncher.databinding.ItemNextUpBinding
import app.olauncher.glance.CalendarEvent
import app.olauncher.glance.CalendarRepository
import app.olauncher.glance.NextUpFormatting
import app.olauncher.glance.NextUpLabels
import app.olauncher.glance.NextUpPlanner
import app.olauncher.helper.OpenTargets
import app.olauncher.helper.getColorFromAttr
import app.olauncher.ui.today.TodayCard
import app.olauncher.ui.today.TodayOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

object NextUpCard : TodayCard {

    const val KEY = "next_up"

    override val key: String = KEY
    override val titleRes: Int = R.string.next_up
    override val emoji: String = "📅"
    override val defaultEnabled: Boolean = false

    var repositoryProvider: ((Context) -> CalendarRepository)? = null
    var nowSupplier: () -> Instant = { Instant.now() }
    var zoneSupplier: () -> ZoneId = { ZoneId.systemDefault() }
    var is24HourSupplier: (Context) -> Boolean = { DateFormat.is24HourFormat(it) }
    var scopeProvider: (() -> CoroutineScope)? = null
    var eventOpener: ((Context, CalendarEvent) -> Unit)? = null

    fun getCalendarRepository(context: Context): CalendarRepository {
        return repositoryProvider?.invoke(context) ?: CalendarRepository(context)
    }

    override fun isAvailable(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun hasContent(context: Context): Boolean {
        val now = nowSupplier().toEpochMilli()
        return getCalendarRepository(context).events(now, now + 48 * 3_600_000L).any { it.endMillis > now }
    }

    override fun priority(context: Context, now: ZonedDateTime): Int {
        val nowMillis = now.toInstant().toEpochMilli()
        val soonest = getCalendarRepository(context)
            .events(nowMillis, nowMillis + TodayOrder.NEXT_UP_WINDOW_MILLIS)
            .filter { !it.allDay && it.startMillis >= nowMillis }
            .minOfOrNull { it.startMillis }
        return TodayOrder.nextUpPriority(soonest, nowMillis, super.priority(context, now))
    }

    override fun createView(inflater: LayoutInflater, parent: ViewGroup, fragment: Fragment): View {
        val binding = CardNextUpBinding.inflate(inflater, parent, false)
        val cardView = NextUpCardView(binding, fragment)
        binding.root.tag = cardView
        cardView.render()
        return binding.root
    }

    override fun onResume(view: View) {
        (view.tag as? NextUpCardView)?.render()
    }

    fun openEvent(context: Context, event: CalendarEvent) {
        val opener = eventOpener
        if (opener != null) opener(context, event) else OpenTargets.openEvent(context, event.id)
    }
}

internal class NextUpCardView(
    val binding: CardNextUpBinding,
    private val fragment: Fragment,
) {
    private var job: Job? = null
    private var expanded = false

    fun render() {
        val context = binding.root.context ?: return
        val scope = NextUpCard.scopeProvider?.invoke()
            ?: (try { fragment.viewLifecycleOwner.lifecycleScope } catch (_: Exception) { null }
                ?: fragment.lifecycleScope
                ?: CoroutineScope(Dispatchers.Main))

        job?.cancel()
        job = scope.launch {
            val repo = NextUpCard.getCalendarRepository(context)
            val now = NextUpCard.nowSupplier()
            val events = repo.next(now, hours = 48, limit = NextUpPlanner.FETCH_LIMIT)

            val currentBinding = binding
            if (events.isEmpty()) {
                currentBinding.tvEmpty.isVisible = true
                currentBinding.eventsContainer.removeAllViews()
            } else {
                currentBinding.tvEmpty.isVisible = false
                currentBinding.eventsContainer.removeAllViews()

                val zone = NextUpCard.zoneSupplier()
                val is24Hour = NextUpCard.is24HourSupplier(context)
                val inflater = LayoutInflater.from(context)
                val labels = NextUpLabels(
                    today = context.getString(R.string.today_section),
                    tomorrow = context.getString(R.string.next_up_tomorrow),
                    nowTemplate = context.getString(R.string.next_up_now),
                    tomorrowEventTemplate = context.getString(R.string.next_up_tomorrow_event),
                )

                val plan = NextUpPlanner.plan(events, now, zone, expanded)
                for ((index, event) in plan.rows.withIndex()) {
                    val itemBinding = ItemNextUpBinding.inflate(inflater, currentBinding.eventsContainer, false)
                    val row = NextUpFormatting.row(event, now, zone, is24Hour, labels)
                    itemBinding.tvEventTime.text = row.lead
                    itemBinding.tvEventTime.isVisible = row.lead.isNotEmpty()
                    itemBinding.tvEventLine.text = row.title
                    if (index == 0) {
                        // Current or next event: follows the accent (same as the text colour when accent is off).
                        val accent = context.getColorFromAttr(R.attr.accentColor)
                        itemBinding.tvEventTime.setTextColor(accent)
                        itemBinding.tvEventLine.setTextColor(accent)
                    }
                    if (OpenTargets.canOpenEvent(event.id)) {
                        itemBinding.root.setOnClickListener {
                            NextUpCard.openEvent(context, event)
                        }
                    } else {
                        itemBinding.root.isClickable = false
                        itemBinding.root.isFocusable = false
                    }
                    currentBinding.eventsContainer.addView(itemBinding.root)
                }

                if (plan.hiddenCount > 0) {
                    val toggle = ItemNextUpBinding.inflate(inflater, currentBinding.eventsContainer, false)
                    toggle.tvEventTime.isVisible = false
                    toggle.tvEventLine.text = if (expanded) {
                        context.getString(R.string.next_up_less)
                    } else {
                        context.getString(R.string.next_up_more, plan.hiddenCount)
                    }
                    toggle.root.isClickable = true
                    toggle.root.isFocusable = true
                    toggle.root.setOnClickListener {
                        expanded = !expanded
                        render()
                    }
                    currentBinding.eventsContainer.addView(toggle.root)
                }
            }
        }
    }
}
