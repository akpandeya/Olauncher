package app.olauncher.helper

import app.olauncher.data.Constants

/** The three home app sets. DAY is the original set (existing pref keys). */
enum class HomeSet { MORNING, DAY, EVENING }

/** Home slot fields stored per set. */
enum class HomeSlotField { NAME, PACKAGE, ACTIVITY, USER, IS_SHORTCUT, SHORTCUT_ID }

/** Start of each set, in minutes since local midnight (0..1439). */
data class HomeSetStarts(
    val morning: Int = HomeSets.DEFAULT_MORNING_START,
    val day: Int = HomeSets.DEFAULT_DAY_START,
    val evening: Int = HomeSets.DEFAULT_EVENING_START,
) {
    fun startOf(set: HomeSet): Int = when (set) {
        HomeSet.MORNING -> morning
        HomeSet.DAY -> day
        HomeSet.EVENING -> evening
    }
}

/** A home slot flag resolved to its set and 1-based slot. */
data class HomeSlotRef(val set: HomeSet, val slot: Int)

object HomeSets {
    const val MINUTES_PER_DAY = 24 * 60
    const val DEFAULT_MORNING_START = 6 * 60
    const val DEFAULT_DAY_START = 9 * 60
    const val DEFAULT_EVENING_START = 18 * 60
    const val MAX_SLOTS = 8

    /**
     * Picks the set for [nowMinutes] (local wall-clock minutes since midnight).
     * The active set is the one whose start was most recent, wrapping past midnight.
     * Off, or a set with no apps ([hasApps] false), gives DAY.
     */
    fun active(
        nowMinutes: Int,
        starts: HomeSetStarts,
        enabled: Boolean,
        hasApps: (HomeSet) -> Boolean = { true },
    ): HomeSet {
        if (!enabled) return HomeSet.DAY
        val now = normalize(nowMinutes)
        // Ties resolve in this order: DAY first, so equal starts stay on the original set.
        val candidate = listOf(HomeSet.DAY, HomeSet.MORNING, HomeSet.EVENING)
            .minBy { set -> normalize(now - starts.startOf(set)) }
        return if (candidate != HomeSet.DAY && !hasApps(candidate)) HomeSet.DAY else candidate
    }

    fun normalize(minutes: Int): Int = ((minutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY

    fun minutesOfDay(hour: Int, minute: Int): Int = normalize(hour * 60 + minute)

    /** Pref key for a home slot. DAY keeps the original key names. */
    fun slotKey(set: HomeSet, field: HomeSlotField, slot: Int): String {
        val base = when (field) {
            HomeSlotField.NAME -> "APP_NAME_"
            HomeSlotField.PACKAGE -> "APP_PACKAGE_"
            HomeSlotField.ACTIVITY -> "APP_ACTIVITY_CLASS_NAME_"
            HomeSlotField.USER -> "APP_USER_"
            HomeSlotField.IS_SHORTCUT -> "IS_SHORTCUT_"
            HomeSlotField.SHORTCUT_ID -> "SHORTCUT_ID_"
        }
        return when (set) {
            HomeSet.DAY -> "$base$slot"
            HomeSet.MORNING -> "HOME_SET_MORNING_$base$slot"
            HomeSet.EVENING -> "HOME_SET_EVENING_$base$slot"
        }
    }

    fun flagFor(set: HomeSet, slot: Int): Int = when (set) {
        HomeSet.DAY -> Constants.FLAG_SET_HOME_APP_1 + slot - 1
        HomeSet.MORNING -> Constants.FLAG_SET_MORNING_HOME_APP_1 + slot - 1
        HomeSet.EVENING -> Constants.FLAG_SET_EVENING_HOME_APP_1 + slot - 1
    }

    fun fromFlag(flag: Int): HomeSlotRef? = when (flag) {
        in Constants.FLAG_SET_HOME_APP_1..Constants.FLAG_SET_HOME_APP_8 ->
            HomeSlotRef(HomeSet.DAY, flag - Constants.FLAG_SET_HOME_APP_1 + 1)
        in Constants.FLAG_SET_MORNING_HOME_APP_1..Constants.FLAG_SET_MORNING_HOME_APP_8 ->
            HomeSlotRef(HomeSet.MORNING, flag - Constants.FLAG_SET_MORNING_HOME_APP_1 + 1)
        in Constants.FLAG_SET_EVENING_HOME_APP_1..Constants.FLAG_SET_EVENING_HOME_APP_8 ->
            HomeSlotRef(HomeSet.EVENING, flag - Constants.FLAG_SET_EVENING_HOME_APP_1 + 1)
        else -> null
    }
}
