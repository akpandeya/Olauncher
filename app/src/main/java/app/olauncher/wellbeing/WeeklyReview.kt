package app.olauncher.wellbeing

import java.time.LocalDate
import kotlin.math.roundToInt

data class Review(
    val distractingAppsLine: String? = null,
    val weekComparisonLine: String? = null,
    val topAppsLine: String? = null,
    val overAllowanceLine: String? = null,
    val pauseLine: String? = null,
    val nudgesLine: String? = null,
    val distractingMinutes: Int? = null,
    val distractingOpens: Int? = null,
    val weekChangePercent: Int? = null,
    val topApps: List<Pair<String, Int>> = emptyList(),
    val overAllowanceDays: Int = 0,
    val pauseNotNow: Int = 0,
    val pauseOpened: Int = 0,
    val topWhy: String? = null,
    val water: Int = 0,
    val walk: Int = 0,
) {
    val lines: List<String>
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

    fun formatDuration(minutes: Int): String {
        val nonNegative = maxOf(0, minutes)
        val hours = nonNegative / 60
        val remainingMinutes = nonNegative % 60
        return when {
            hours > 0 && remainingMinutes > 0 -> "$hours h $remainingMinutes min"
            hours > 0 -> "$hours h"
            else -> "$remainingMinutes min"
        }
    }

    fun formatOpens(count: Int): String {
        return if (count == 1) "1 open" else "$count opens"
    }

    fun resolveWhyLabel(key: String): String = when (key.trim().lowercase()) {
        "bored", "just bored" -> "Just bored"
        "reply", "reply to someone" -> "Reply to someone"
        "lookup", "look something up" -> "Look something up"
        else -> key
    }

    fun summarise(snapshots: List<DaySnapshot>, today: LocalDate): Review =
        summarise(snapshots, today.toEpochDay())

    fun summarise(snapshots: List<DaySnapshot>, today: Long = LocalDate.now().toEpochDay()): Review {
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
            "Distracting apps ${formatDuration(totalDistractingMinutes)} · ${formatOpens(totalDistractingOpens)}"
        } else {
            null
        }

        // Line 2: Week comparison
        val earlierUsageSnapshots = earlierSnapshots.filter { it.distractingMinutes != null }
        var weekChangePercent: Int? = null
        val weekComparisonLine = if (hasUsageData && totalDistractingMinutes != null && earlierUsageSnapshots.size >= 3) {
            val earlierMinutes = earlierUsageSnapshots.sumOf { it.distractingMinutes ?: 0 }
            val currentDailyAvg = totalDistractingMinutes.toDouble() / currentUsageSnapshots.size
            val earlierDailyAvg = earlierMinutes.toDouble() / earlierUsageSnapshots.size

            if (earlierDailyAvg > 0.0) {
                if (currentDailyAvg < earlierDailyAvg) {
                    val percent = ((earlierDailyAvg - currentDailyAvg) / earlierDailyAvg * 100.0).roundToInt()
                    if (percent > 0) {
                        weekChangePercent = -percent
                        "$percent% less than the week before"
                    } else null
                } else if (currentDailyAvg > earlierDailyAvg) {
                    val percent = ((currentDailyAvg - earlierDailyAvg) / earlierDailyAvg * 100.0).roundToInt()
                    if (percent > 0) {
                        weekChangePercent = percent
                        "$percent% more than the week before"
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
            .take(2)
            .map { it.key to it.value }

        val topAppsLine = if (topAppsList.isNotEmpty()) {
            val parts = topAppsList.map { (label, minutes) -> "$label ${formatDuration(minutes)}" }
            "Most: ${parts.joinToString(", ")}"
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
            "Over allowance on $overAllowanceDays of 7 days"
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
        val topWhyLabel = topWhyKey?.let { resolveWhyLabel(it) }

        val pauseLine = if (totalNotNow > 0 || totalOpened > 0) {
            val base = "Pause: $totalNotNow × not now · $totalOpened × opened"
            if (topWhyLabel != null) {
                "$base (mostly: $topWhyLabel)"
            } else {
                base
            }
        } else {
            null
        }

        // Line 6: Water & Walk
        val totalWater = currentSnapshots.sumOf { it.water }
        val totalWalk = currentSnapshots.sumOf { it.walk }

        val nudgesLine = when {
            totalWater > 0 && totalWalk > 0 -> "Water $totalWater · Walk $totalWalk"
            totalWater > 0 -> "Water $totalWater"
            totalWalk > 0 -> "Walk $totalWalk"
            else -> null
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
            topWhy = topWhyLabel,
            water = totalWater,
            walk = totalWalk,
        )
    }
}
