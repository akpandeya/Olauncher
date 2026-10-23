package app.olauncher.data.store

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Comeback detection over the review event log: a study day preceded by days
 * without study celebrates returning instead of punishing the pause.
 *
 * Boundary (per the stats ticket's acceptance): a study day 2 or more days
 * after the previous one is a comeback; the day right after is not.
 */
object ReviewComebacks {

    /** Days with at least one review, as local dates (never UTC-millis division, which drifts at DST). */
    fun studyDays(events: List<ReviewEvent>, zone: ZoneId): List<LocalDate> =
        events.map { Instant.ofEpochMilli(it.reviewedAt).atZone(zone).toLocalDate() }
            .distinct()
            .sorted()

    /**
     * Whether studying today is a comeback: [events] must not already contain
     * today, and the most recent study day must be at least two days back.
     */
    fun isComeback(events: List<ReviewEvent>, today: LocalDate, zone: ZoneId): Boolean {
        val days = studyDays(events, zone)
        if (days.isEmpty() || days.last() == today) return false
        return today.toEpochDay() - days.last().toEpochDay() >= 2
    }

    /**
     * Comeback count over the logged study days alone — for panels that describe
     * what already happened, with no hypothetical today injected.
     */
    fun count(events: List<ReviewEvent>, zone: ZoneId): Int {
        val days = studyDays(events, zone)
        var comebacks = 0
        for (i in 1 until days.size) {
            if (days[i].toEpochDay() - days[i - 1].toEpochDay() >= 2) comebacks++
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
            today.toEpochDay() - days.last().toEpochDay() >= 2
        return count(events, zone) + if (todayIsComeback) 1 else 0
    }
}
