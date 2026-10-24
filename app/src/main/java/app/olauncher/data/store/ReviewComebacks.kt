package app.olauncher.data.store

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Comeback detection over the review event log: a study day preceded by days
 * without study celebrates returning instead of punishing the pause.
 *
 * Boundary (per the stats ticket's acceptance): a comeback is a study day
 * preceded by at least [MIN_FREE_DAYS] consecutive study-free days. One free
 * day is not a comeback; two are.
 */
object ReviewComebacks {

    const val MIN_FREE_DAYS = 2

    /** True when [later] follows [earlier] after at least [MIN_FREE_DAYS] study-free days. */
    private fun isGapComeback(earlier: LocalDate, later: LocalDate): Boolean =
        later.toEpochDay() - earlier.toEpochDay() - 1 >= MIN_FREE_DAYS

    /** Days with at least one review, as local dates (never UTC-millis division, which drifts at DST). */
    fun studyDays(events: List<ReviewEvent>, zone: ZoneId): List<LocalDate> =
        events.map { Instant.ofEpochMilli(it.reviewedAt).atZone(zone).toLocalDate() }
            .distinct()
            .sorted()

    /**
     * Whether studying today is a comeback: [events] must not already contain
     * today, and the most recent study day must be followed by at least two study-free days.
     */
    fun isComeback(events: List<ReviewEvent>, today: LocalDate, zone: ZoneId): Boolean {
        val days = studyDays(events, zone)
        if (days.isEmpty() || days.last() == today) return false
        return isGapComeback(days.last(), today)
    }

    /**
     * Comeback count over the logged study days alone — for panels that describe
     * what already happened, with no hypothetical today injected.
     */
    fun count(events: List<ReviewEvent>, zone: ZoneId): Int {
        val days = studyDays(events, zone)
        var comebacks = 0
        for (i in 1 until days.size) {
            if (isGapComeback(days[i - 1], days[i])) comebacks++
        }
        return comebacks
    }

    /**
     * Lifetime comeback count through [today], which counts as a study day
     * (pass the events logged before the current session).
     */
    fun count(events: List<ReviewEvent>, today: LocalDate, zone: ZoneId): Int {
        val days = studyDays(events, zone)
        val todayIsComeback = days.isNotEmpty() && today !in days &&
            isGapComeback(days.last(), today)
        return count(events, zone) + if (todayIsComeback) 1 else 0
    }
}
