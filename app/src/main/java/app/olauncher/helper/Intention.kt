package app.olauncher.helper

import java.time.LocalDateTime

/** The one-line "Today I want to..." intention. It lapses when the (rolled-over) day changes. */
object Intention {

    const val MAX_LENGTH = 60
    const val DEFAULT_ROLLOVER_HOUR = 4

    /** The day an instant belongs to, as an epoch day. Before [rolloverHour] still counts as the previous day. */
    fun dayOf(now: LocalDateTime, rolloverHour: Int = DEFAULT_ROLLOVER_HOUR): Long =
        now.minusHours(rolloverHour.toLong()).toLocalDate().toEpochDay()

    /** Trims and caps what was typed; blank becomes empty. */
    fun clean(raw: String?): String = raw.orEmpty().trim().take(MAX_LENGTH).trim()

    /** The intention to show, or null when none is set or it was written on an earlier day. */
    fun current(
        text: String?,
        day: Long,
        today: LocalDateTime,
        rolloverHour: Int = DEFAULT_ROLLOVER_HOUR,
    ): String? {
        val cleaned = clean(text)
        if (cleaned.isEmpty()) return null
        return if (day == dayOf(today, rolloverHour)) cleaned else null
    }

    /** True when the field is empty, so it should look editable (outline and pencil). Filled text stays clean. */
    fun showsFieldHint(text: CharSequence?): Boolean = text.isNullOrBlank()
}
