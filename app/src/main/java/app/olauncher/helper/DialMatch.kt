package app.olauncher.helper

object DialMatch {
    private val REGEX = Regex("""^\+?[0-9][0-9 ()\-]{5,}$""")

    fun number(query: String): String? {
        val trimmed = query.trim()
        return if (REGEX.matches(trimmed)) trimmed else null
    }

    private val PHONE_CHARS = Regex("""^[0-9+ \-]+$""")
    private val HYPHEN_GROUP = Regex("""[0-9]-[0-9]""")
    private val SPACED_OPERATORS = listOf(" - ", " + ", " * ", " / ", " ^ ")

    /** True when the query reads as a phone number, so the calculator row should be skipped. */
    fun looksLikePhone(query: String): Boolean {
        val q = query.trim()
        if (!PHONE_CHARS.matches(q) || number(q) == null) return false
        if (q.startsWith("0") || q.startsWith("+")) return true
        return HYPHEN_GROUP.containsMatchIn(q) && q.count { it.isDigit() } >= 7
    }

    /** True when the query has spaced arithmetic operators, so the dial row should be skipped. */
    fun hasSpacedOperator(query: String): Boolean = SPACED_OPERATORS.any { query.contains(it) }

    fun showCalcRow(query: String): Boolean = !looksLikePhone(query)

    fun showDialRow(query: String): Boolean = !hasSpacedOperator(query)
}
