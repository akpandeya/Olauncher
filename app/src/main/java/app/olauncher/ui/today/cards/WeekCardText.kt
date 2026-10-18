package app.olauncher.ui.today.cards

import android.content.Context
import app.olauncher.R
import app.olauncher.wellbeing.AppMinutes
import app.olauncher.wellbeing.ReviewLine
import app.olauncher.wellbeing.WeeklyReview
import app.olauncher.wellbeing.WhyChoice

/** Turns weekly review lines into text using Android string resources. */
object WeekCardText {

    val usedStringResIds: List<Int> = listOf(
        R.string.week_review_duration_hours_minutes,
        R.string.week_review_duration_hours,
        R.string.week_review_duration_minutes,
        R.string.week_review_distracting,
        R.string.week_review_less_than_week_before,
        R.string.week_review_more_than_week_before,
        R.string.week_review_top_apps,
        R.string.week_review_top_app_item,
        R.string.week_review_list_separator,
        R.string.week_review_over_allowance,
        R.string.week_review_pause,
        R.string.week_review_pause_mostly,
        R.string.week_review_nudges_water_walk,
        R.string.week_review_nudges_water,
        R.string.week_review_nudges_walk,
        R.string.pause_why_bored,
        R.string.pause_why_reply,
        R.string.pause_why_lookup,
    )

    val usedPluralsResIds: List<Int> = listOf(R.plurals.week_review_opens)

    fun format(context: Context, line: ReviewLine): String = when (line) {
        is ReviewLine.Distracting -> context.getString(
            R.string.week_review_distracting,
            formatDuration(context, line.minutes),
            formatOpens(context, line.opens),
        )
        is ReviewLine.LessThanWeekBefore ->
            context.getString(R.string.week_review_less_than_week_before, line.percent)
        is ReviewLine.MoreThanWeekBefore ->
            context.getString(R.string.week_review_more_than_week_before, line.percent)
        is ReviewLine.TopApps -> context.getString(
            R.string.week_review_top_apps,
            line.apps.joinToString(context.getString(R.string.week_review_list_separator)) { formatTopApp(context, it) },
        )
        is ReviewLine.OverAllowance ->
            context.getString(R.string.week_review_over_allowance, line.days, line.windowDays)
        is ReviewLine.Pause -> {
            val base = context.getString(R.string.week_review_pause, line.notNow, line.opened)
            val topWhy = line.topWhy
            if (topWhy != null) {
                context.getString(R.string.week_review_pause_mostly, base, formatWhy(context, topWhy))
            } else {
                base
            }
        }
        is ReviewLine.Nudges -> when {
            line.water > 0 && line.walk > 0 ->
                context.getString(R.string.week_review_nudges_water_walk, line.water, line.walk)
            line.water > 0 -> context.getString(R.string.week_review_nudges_water, line.water)
            else -> context.getString(R.string.week_review_nudges_walk, line.walk)
        }
    }

    fun formatDuration(context: Context, minutes: Int): String {
        val parts = WeeklyReview.durationParts(minutes)
        return when {
            parts.hours > 0 && parts.minutes > 0 ->
                context.getString(R.string.week_review_duration_hours_minutes, parts.hours, parts.minutes)
            parts.hours > 0 -> context.getString(R.string.week_review_duration_hours, parts.hours)
            else -> context.getString(R.string.week_review_duration_minutes, parts.minutes)
        }
    }

    fun formatOpens(context: Context, count: Int): String =
        context.resources.getQuantityString(R.plurals.week_review_opens, count, count)

    fun formatWhy(context: Context, why: WhyChoice): String = when (why) {
        WhyChoice.Bored -> context.getString(R.string.pause_why_bored)
        WhyChoice.Reply -> context.getString(R.string.pause_why_reply)
        WhyChoice.Lookup -> context.getString(R.string.pause_why_lookup)
        is WhyChoice.Other -> why.rawKey
    }

    private fun formatTopApp(context: Context, app: AppMinutes): String =
        context.getString(R.string.week_review_top_app_item, app.label, formatDuration(context, app.minutes))
}
