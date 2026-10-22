package app.olauncher.helper

/**
 * Parses the text after `:t`, e.g. `10`, `1h30`, `1h`, `45m`, `90s`, with optional label words
 * (`25 tea`). A bare number is minutes. Pure Kotlin: no Android types.
 */
object TimerCommand {
    const val MAX_SECONDS = 24 * 60 * 60

    data class Timer(val seconds: Int, val label: String)

    private val DURATION = Regex("""^(?:(\d{1,6})h)?(?:(\d{1,6})(?:m|$))?(?:(\d{1,6})s)?$""", RegexOption.IGNORE_CASE)
    private val WHITESPACE = Regex("""\s+""")

    /** Null when the duration is missing, unreadable, zero or longer than 24 h. */
    fun parse(args: String): Timer? {
        val words = args.trim().split(WHITESPACE).filter { it.isNotEmpty() }
        val first = words.firstOrNull() ?: return null
        val match = DURATION.matchEntire(first) ?: return null
        val (h, m, s) = match.destructured
        if (h.isEmpty() && m.isEmpty() && s.isEmpty()) return null
        val total = (h.toLongOrNull() ?: 0L) * 3600 +
            (m.toLongOrNull() ?: 0L) * 60 +
            (s.toLongOrNull() ?: 0L)
        if (total <= 0L || total > MAX_SECONDS) return null
        return Timer(total.toInt(), words.drop(1).joinToString(" "))
    }

    /** `5400` -> `1 h 30 min`; `90` -> `1 min 30 s`. */
    fun formatDuration(seconds: Int): String {
        val h = seconds / 3600
        val m = seconds % 3600 / 60
        val s = seconds % 60
        return buildList {
            if (h > 0) add("$h h")
            if (m > 0) add("$m min")
            if (s > 0) add("$s s")
        }.joinToString(" ")
    }

    /** Preview row text: `⏱ 1 h 30 min · tea`. */
    fun previewText(timer: Timer): String =
        "⏱ " + formatDuration(timer.seconds) + if (timer.label.isEmpty()) "" else " · ${timer.label}"
}
