package app.olauncher.wellbeing

import app.olauncher.helper.usageStats.ComponentForegroundStat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Single usage event for bucketing.
 * [eventType] maps to [android.app.usage.UsageEvents.Event] constants:
 * 1 = ACTIVITY_RESUMED, 2 = ACTIVITY_PAUSED, 3 = END_OF_DAY, 4 = CONTINUE_PREVIOUS_DAY, 23 = ACTIVITY_STOPPED.
 */
data class UsageEventRecord(
    val packageName: String,
    val timestamp: Long,
    val eventType: Int,
) {
    val isResume: Boolean
        get() = eventType == 1 || eventType == 4

    val isPause: Boolean
        get() = eventType == 2 || eventType == 23 || eventType == 3
}

/**
 * A continuous foreground usage session for an app.
 */
data class UsageSession(
    val packageName: String,
    val startMillis: Long,
    val endMillis: Long,
)

/**
 * Context-free helper for bucketing usage events/sessions into a 24-hour heat row.
 */
object HourlyUsage {

    const val HOURS_IN_DAY = 24

    /**
     * Extracts foreground usage sessions from a list of usage events for [distractingPkgs].
     * Handles:
     * - Open sessions at [now] (resumed without subsequent pause).
     * - Sessions started before [startOfDayMillis] (paused without preceding resume today).
     */
    fun extractSessions(
        events: List<UsageEventRecord>,
        distractingPkgs: Set<String>,
        startOfDayMillis: Long,
        now: Long = System.currentTimeMillis(),
    ): List<UsageSession> {
        if (distractingPkgs.isEmpty() || events.isEmpty()) return emptyList()

        val matchingEvents = events.filter { it.packageName in distractingPkgs }
        if (matchingEvents.isEmpty()) return emptyList()

        val sessions = mutableListOf<UsageSession>()
        val byPkg = matchingEvents.groupBy { it.packageName }

        for ((pkg, pkgEvents) in byPkg) {
            val sorted = pkgEvents.sortedBy { it.timestamp }
            var sessionStart: Long? = null

            for ((index, event) in sorted.withIndex()) {
                if (event.isResume) {
                    if (sessionStart == null) {
                        sessionStart = maxOf(event.timestamp, startOfDayMillis)
                    }
                } else if (event.isPause) {
                    // An unmatched close only means "open since midnight" when it is the
                    // first event of the day for this package. Otherwise it is a duplicate
                    // close (PAUSED followed by STOPPED) and must be ignored.
                    val start = sessionStart ?: if (index == 0) startOfDayMillis else null
                    if (start != null && event.timestamp > start) {
                        val sessionEnd = minOf(event.timestamp, now)
                        if (sessionEnd > start) {
                            sessions.add(UsageSession(pkg, start, sessionEnd))
                        }
                    }
                    sessionStart = null
                }
            }

            // Open session at now
            if (sessionStart != null && now > sessionStart) {
                sessions.add(UsageSession(pkg, sessionStart, now))
            }
        }

        return sessions
    }

    /**
     * Merges overlapping or adjacent sessions to avoid double-counting foreground time.
     */
    fun mergeSessions(sessions: List<UsageSession>): List<Pair<Long, Long>> {
        if (sessions.isEmpty()) return emptyList()
        val intervals = sessions
            .map { it.startMillis to it.endMillis }
            .filter { it.second > it.first }
            .sortedBy { it.first }

        if (intervals.isEmpty()) return emptyList()

        val merged = mutableListOf<Pair<Long, Long>>()
        var current = intervals[0]

        for (i in 1 until intervals.size) {
            val next = intervals[i]
            if (next.first <= current.second) {
                current = current.first to maxOf(current.second, next.second)
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)
        return merged
    }

    /**
     * Buckets usage sessions into 24 hourly buckets (0..23) for [date] in [zone].
     * Sessions that span an hour boundary are split between hours.
     * Future hours (hours after [now] in [zone]) remain empty (0 minutes).
     * Correctly handles DST transition days (spring forward / fall back).
     */
    fun bucketSessions(
        sessions: List<UsageSession>,
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
        now: Long = System.currentTimeMillis(),
    ): List<Int> {
        val startOfDayZdt = date.atStartOfDay(zone)
        val startOfDayMs = startOfDayZdt.toInstant().toEpochMilli()
        val endOfDayMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val effectiveNow = minOf(now, endOfDayMs)
        if (effectiveNow <= startOfDayMs || sessions.isEmpty()) {
            return List(HOURS_IN_DAY) { 0 }
        }

        val mergedIntervals = mergeSessions(sessions)
        val hourlyMillis = LongArray(HOURS_IN_DAY)

        for ((rawStart, rawEnd) in mergedIntervals) {
            val clampedStart = maxOf(rawStart, startOfDayMs)
            val clampedEnd = minOf(rawEnd, effectiveNow)
            if (clampedEnd <= clampedStart) continue

            var curr = clampedStart
            while (curr < clampedEnd) {
                val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(curr), zone)
                val hour = zdt.hour
                val nextHourZdt = zdt.truncatedTo(ChronoUnit.HOURS).plusHours(1)
                val nextHourMs = nextHourZdt.toInstant().toEpochMilli()

                val boundary = if (nextHourMs <= curr) {
                    curr + 3600_000L
                } else {
                    nextHourMs
                }

                val sliceEnd = minOf(clampedEnd, boundary)
                val duration = sliceEnd - curr

                if (duration > 0 && hour in 0 until HOURS_IN_DAY) {
                    hourlyMillis[hour] += duration
                }
                curr = sliceEnd
            }
        }

        val result = mutableListOf<Int>()
        for (h in 0 until HOURS_IN_DAY) {
            val mins = (hourlyMillis[h] / 60_000L).toInt()
            result.add(mins)
        }
        return result
    }

    /**
     * Buckets raw usage events for [distractingPkgs] into 24 hourly buckets.
     */
    fun bucketEvents(
        events: List<UsageEventRecord>,
        distractingPkgs: Set<String>,
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
        now: Long = System.currentTimeMillis(),
    ): List<Int> {
        val startOfDayMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val sessions = extractSessions(events, distractingPkgs, startOfDayMs, now)
        return bucketSessions(sessions, date, zone, now)
    }

    /**
     * Convenience method to bucket [ComponentForegroundStat] from [EventLogWrapper].
     */
    fun bucketForegroundStats(
        stats: List<ComponentForegroundStat>,
        distractingPkgs: Set<String>,
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
        now: Long = System.currentTimeMillis(),
    ): List<Int> {
        val matchingStats = if (distractingPkgs.isEmpty()) stats else stats.filter { it.packageName in distractingPkgs }
        val sessions = matchingStats.map { UsageSession(it.packageName, it.beginTime, it.endTime) }
        return bucketSessions(sessions, date, zone, now)
    }
}
