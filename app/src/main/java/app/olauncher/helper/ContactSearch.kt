package app.olauncher.helper

import app.olauncher.data.AppModel

data class ContactEntry(val name: String, val lookupUri: String)

/** Source of contacts. The Android implementation reads ContactsContract; tests use a fake. */
interface ContactSource {
    fun contacts(): List<ContactEntry>

    /** Main phone number of the contact, or null when it has none. */
    fun primaryNumber(lookupUri: String): String?
}

object ContactSearch {
    const val MIN_QUERY_LENGTH = 2
    const val MAX_ROWS = 3
    const val MIN_SCORE = AppMatcher.SCORE_SUBSTRING

    /** Names that score >= [MIN_SCORE] for [query], best first, at most [max]. */
    fun rank(names: List<String>, query: String, max: Int = MAX_ROWS): List<String> =
        rankIndices(names, query, max).map { names[it] }

    fun search(source: ContactSource, query: String, max: Int = MAX_ROWS): List<ContactEntry> {
        val entries = source.contacts()
        return rankIndices(entries.map { it.name }, query, max).map { entries[it] }
    }

    fun toResult(entry: ContactEntry): AppModel.Result =
        AppModel.Result(
            text = "👤 ${entry.name}",
            action = AppModel.Result.Action.Contact(entry.lookupUri),
        )

    private fun rankIndices(names: List<String>, query: String, max: Int): List<Int> {
        val q = query.trim()
        if (q.length < MIN_QUERY_LENGTH || max <= 0) return emptyList()
        return names.withIndex()
            .mapNotNull { (i, name) ->
                if (name.isBlank()) return@mapNotNull null
                val score = AppMatcher.score(name, q) ?: return@mapNotNull null
                if (score < MIN_SCORE) null else Triple(i, name, score)
            }
            .sortedWith(
                compareByDescending<Triple<Int, String, Int>> { it.third }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.second }
                    .thenBy { it.first }
            )
            .take(max)
            .map { it.first }
    }
}
