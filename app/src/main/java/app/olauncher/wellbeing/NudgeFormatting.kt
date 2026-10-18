package app.olauncher.wellbeing

import java.util.Locale

/**
 * Pure formatting and option helpers for gentle nudges.
 */
object NudgeFormatting {

    val WATER_INTERVAL_OPTIONS_MIN = listOf(0, 60, 90, 120, 180)
    val WALK_INTERVAL_OPTIONS_MIN = listOf(0, 120, 180, 240)
    val SOCIAL_THRESHOLD_OPTIONS_MIN = listOf(0, 15, 30, 45, 60, 90)

    fun formatWaterInterval(intervalMin: Int): String = when (intervalMin) {
        60 -> "every 1 h"
        90 -> "every 1.5 h"
        120 -> "every 2 h"
        180 -> "every 3 h"
        else -> "Off"
    }

    fun formatWalkInterval(intervalMin: Int): String = when (intervalMin) {
        120 -> "every 2 h"
        180 -> "every 3 h"
        240 -> "every 4 h"
        else -> "Off"
    }

    fun formatSocialThreshold(thresholdMin: Int): String = when (thresholdMin) {
        15, 30, 45, 60, 90 -> "after $thresholdMin min of distracting apps"
        else -> "Off"
    }

    fun formatActiveHours(startMin: Int, endMin: Int): String {
        val startHour = startMin / 60
        val startMinute = startMin % 60
        val endHour = endMin / 60
        val endMinute = endMin % 60
        return String.format(Locale.ROOT, "%02d:%02d\u2013%02d:%02d", startHour, startMinute, endHour, endMinute)
    }

    fun isValidActiveHours(startMin: Int, endMin: Int): Boolean {
        return startMin < endMin && startMin >= 0 && endMin <= 24 * 60
    }
}
