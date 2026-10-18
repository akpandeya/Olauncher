package app.olauncher.helper

object DialMatch {
    private val REGEX = Regex("""^\+?[0-9][0-9 ()\-]{5,}$""")

    fun number(query: String): String? {
        val trimmed = query.trim()
        return if (REGEX.matches(trimmed)) trimmed else null
    }
}
