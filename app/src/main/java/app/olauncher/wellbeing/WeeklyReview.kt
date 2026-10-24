package app.olauncher.wellbeing

import java.time.LocalDate
import kotlin.math.roundToInt

/** The reason a person gave on the pause screen, or the raw stored key when it is not a known one. */
sealed class WhyChoice {
    object Bored : WhyChoice()
    object Reply : WhyChoice()
    object Lookup : WhyChoice()
    data class Other(val rawKey: String) : WhyChoice()
}

data class DurationParts(val hours: Int, val minutes: Int)

data class AppMinutes(val label: String, val minutes: Int)

/** One line of the weekly review, as data. Turning it into text is done on the Android side. */
sealed class ReviewLine {
    data class Distracting(val minutes: Int, val opens: Int) : ReviewLine()
    data class LessThanWeekBefore(val percent: Int) : ReviewLine()
    data class MoreThanWeekBefore(val percent: Int) : ReviewLine()
    data class TopApps(val apps: List<AppMinutes>) : ReviewLine()
    data class OverAllowance(val days: Int, val windowDays: Int) : ReviewLine()
    data class Pause(val notNow: Int, val opened: Int, val topWhy: WhyChoice?) : ReviewLine()
    data class Nudges(val water: Int, val walk: Int) : ReviewLine()
}

data class Review(
    val distractingAppsLine: ReviewLine.Distracting? = null,
    val weekComparisonLine: ReviewLine? = null,
    val topAppsLine: ReviewLine.TopApps? = null,
    val overAllowanceLine: ReviewLine.OverAllowance? = null,
    val pauseLine: ReviewLine.Pause? = null,
    val nudgesLine: ReviewLine.Nudges? = null,
    val distractingMinutes: Int? = null,
    val distractingOpens: Int? = null,
    val weekChangePercent: Int? = null,
    val topApps: List<Pair<String, Int>> = emptyList(),
    val overAllowanceDays: Int = 0,
    val pauseNotNow: Int = 0,
    val pauseOpened: Int = 0,
    val topWhy: WhyChoice? = null,
    val water: Int = 0,
    val walk: Int = 0,
) {
    val lines: List<ReviewLine>
        get() = listOfNotNull(
            distractingAppsLine,
            weekComparisonLine,
            topAppsLine,
            overAllowanceLine,
            pauseLine,
            nudgesLine,
        )
}

object WeeklyReview {

    const val WINDOW_DAYS = 7
    const val DEFAULT_TOP_APPS = 2

    fun durationParts(minutes: Int): DurationParts {
        val nonNegative = maxOf(0, minutes)
        return DurationParts(hours = nonNegative / 60, minutes = nonNegative % 60)
    }

    fun resolveWhy(key: String): WhyChoice = when (key.trim().lowercase()) {
        "bored", "just bored" -> WhyChoice.Bored
        "reply", "reply to someone" -> WhyChoice.Reply
        "lookup", "look something up" -> WhyChoice.Lookup
        else -> WhyChoice.Other(key)
    }

    fun summarise(snapshots: List<DaySnapshot>, today: LocalDate, topAppsCount: Int = DEFAULT_TOP_APPS): Review =
        summarise(snapshots, today.toEpochDay(), topAppsCount)

    fun summarise(
        snapshots: List<DaySnapshot>,
        today: Long = LocalDate.now().toEpochDay(),
        topAppsCount: Int = DEFAULT_TOP_APPS,
    ): Review {
        val currentDaysRange = (today - 7)..(today - 1)
        val earlierDaysRange = (today - 14)..(today - 8)

        val currentSnapshots = snapshots.filter { it.epochDay in currentDaysRange }
            .groupBy { it.epochDay }
            .map { (_, list) -> list.reduce { acc, s -> mergeSnapshots(acc, s) } }

        val earlierSnapshots = snapshots.filter { it.epochDay in earlierDaysRange }
            .groupBy { it.epochDay }
            .map { (_, list) -> list.reduce { acc, s -> mergeSnapshots(acc, s) } }

        // Line 1: Distracting apps usage
        val currentUsageSnapshots = currentSnapshots.filter { it.distractingMinutes != null }
        val hasUsageData = currentUsageSnapshots.isNotEmpty()

        val totalDistractingMinutes = if (hasUsageData) {
            currentUsageSnapshots.sumOf { it.distractingMinutes ?: 0 }
        } else {
            null
        }

        val totalDistractingOpens = if (hasUsageData) {
            currentUsageSnapshots.sumOf { it.distractingOpens ?: 0 }
        } else {
            null
        }

        val distractingAppsLine = if (hasUsageData && totalDistractingMinutes != null && totalDistractingOpens != null) {
            ReviewLine.Distracting(totalDistractingMinutes, totalDistractingOpens)
        } else {
            null
        }

        // Line 2: Week comparison
        val earlierUsageSnapshots = earlierSnapshots.filter { it.distractingMinutes != null }
        var weekChangePercent: Int? = null
        val weekComparisonLine: ReviewLine? = if (hasUsageData && totalDistractingMinutes != null && earlierUsageSnapshots.size >= 3) {
            val earlierMinutes = earlierUsageSnapshots.sumOf { it.distractingMinutes ?: 0 }
            val currentDailyAvg = totalDistractingMinutes.toDouble() / currentUsageSnapshots.size
            val earlierDailyAvg = earlierMinutes.toDouble() / earlierUsageSnapshots.size

            if (earlierDailyAvg > 0.0) {
                if (currentDailyAvg < earlierDailyAvg) {
                    val percent = ((earlierDailyAvg - currentDailyAvg) / earlierDailyAvg * 100.0).roundToInt()
                    if (percent > 0) {
                        weekChangePercent = -percent
                        ReviewLine.LessThanWeekBefore(percent)
                    } else null
                } else if (currentDailyAvg > earlierDailyAvg) {
                    val percent = ((currentDailyAvg - earlierDailyAvg) / earlierDailyAvg * 100.0).roundToInt()
                    if (percent > 0) {
                        weekChangePercent = percent
                        ReviewLine.MoreThanWeekBefore(percent)
                    } else null
                } else {
                    null
                }
            } else {
                null
            }
        } else {
            null
        }

        // Line 3: Top apps
        val appMinutesMap = mutableMapOf<String, Int>()
        if (hasUsageData) {
            for (snapshot in currentSnapshots) {
                for ((label, minutes) in snapshot.topApps) {
                    if (minutes > 0) {
                        appMinutesMap[label] = (appMinutesMap[label] ?: 0) + minutes
                    }
                }
            }
        }
        val topAppsList = appMinutesMap.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(topAppsCount)
            .map { it.key to it.value }

        val topAppsLine = if (topAppsList.isNotEmpty()) {
            ReviewLine.TopApps(topAppsList.map { (label, minutes) -> AppMinutes(label, minutes) })
        } else {
            null
        }

        // Line 4: Over allowance
        val overAllowanceDays = if (hasUsageData) {
            currentSnapshots.count { it.overAllowance }
        } else {
            0
        }
        val overAllowanceLine = if (hasUsageData && overAllowanceDays > 0) {
            ReviewLine.OverAllowance(overAllowanceDays, WINDOW_DAYS)
        } else {
            null
        }

        // Line 5: Pause stats
        val totalNotNow = currentSnapshots.sumOf { it.notNow }
        val totalOpened = currentSnapshots.sumOf { it.opened }

        val whyCounts = mutableMapOf<String, Int>()
        for (snapshot in currentSnapshots) {
            for ((key, count) in snapshot.why) {
                if (count > 0) {
                    whyCounts[key] = (whyCounts[key] ?: 0) + count
                }
            }
        }
        val topWhyEntry = whyCounts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .firstOrNull()
        val topWhyKey = topWhyEntry?.key
        val topWhy = topWhyKey?.let { resolveWhy(it) }

        val pauseLine = if (totalNotNow > 0 || totalOpened > 0) {
            ReviewLine.Pause(totalNotNow, totalOpened, topWhy)
        } else {
            null
        }

        // Line 6: Water & Walk
        val totalWater = currentSnapshots.sumOf { it.water }
        val totalWalk = currentSnapshots.sumOf { it.walk }

        val nudgesLine = if (totalWater > 0 || totalWalk > 0) {
            ReviewLine.Nudges(water = maxOf(0, totalWater), walk = maxOf(0, totalWalk))
        } else {
            null
        }

        return Review(
            distractingAppsLine = distractingAppsLine,
            weekComparisonLine = weekComparisonLine,
            topAppsLine = topAppsLine,
            overAllowanceLine = overAllowanceLine,
            pauseLine = pauseLine,
            nudgesLine = nudgesLine,
            distractingMinutes = totalDistractingMinutes,
            distractingOpens = totalDistractingOpens,
            weekChangePercent = weekChangePercent,
            topApps = topAppsList,
            overAllowanceDays = overAllowanceDays,
            pauseNotNow = totalNotNow,
            pauseOpened = totalOpened,
            topWhy = topWhy,
            water = totalWater,
            walk = totalWalk,
        )
    }
}
