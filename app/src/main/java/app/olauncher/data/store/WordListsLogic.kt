package app.olauncher.data.store

/** Pure rules for word lists, kept free of Android so they can be unit tested. */
object WordListsLogic {

    enum class NameError { BLANK, DUPLICATE }

    /** Trims and collapses internal whitespace runs so near-identical names compare equal. */
    fun normalizeName(raw: String): String {
        return raw.trim().replace(Regex("\\s+"), " ")
    }

    /**
     * Null when [name] can be used. A duplicate is a case-insensitive name match against
     * another list; [exceptId] excludes the list being renamed from that check.
     */
    fun validateName(name: String, lists: List<WordList>, exceptId: String? = null): NameError? {
        if (name.isBlank()) return NameError.BLANK
        val clash = lists.any { it.id != exceptId && it.name.equals(name, ignoreCase = true) }
        return if (clash) NameError.DUPLICATE else null
    }

    /**
     * Lowercases, folds every run of non-letter/digit characters into a single dash and
     * trims dashes at the edges. Unicode letters are kept (Küche → küche). A name that
     * leaves nothing behind falls back to "list".
     */
    fun slugify(name: String): String {
        val slug = StringBuilder()
        var pendingDash = false
        for (c in name.lowercase()) {
            if (c.isLetterOrDigit()) {
                if (pendingDash && slug.isNotEmpty()) slug.append('-')
                slug.append(c)
                pendingDash = false
            } else {
                pendingDash = true
            }
        }
        return slug.toString().ifEmpty { "list" }
    }

    /** Slug for [name], suffixed with -2, -3, … until it is free within [existingIds]. */
    fun uniqueListId(name: String, existingIds: Set<String>): String {
        val base = slugify(name)
        if (base !in existingIds) return base
        var suffix = 2
        while ("$base-$suffix" in existingIds) suffix++
        return "$base-$suffix"
    }

    /**
     * Adds or removes [listId] on exactly the words whose id is in [wordIds].
     * Idempotent: a word already in the target state is returned unchanged.
     */
    fun withAssignment(words: List<Word>, wordIds: Set<Long>, listId: String, assign: Boolean): List<Word> {
        return words.map { word ->
            if (word.id !in wordIds) return@map word
            if (assign) {
                if (listId in word.listIds) word else word.copy(listIds = word.listIds + listId)
            } else {
                if (listId !in word.listIds) word else word.copy(listIds = word.listIds - listId)
            }
        }
    }

    /** Drops [listId] from every word that carries it (used when the list is deleted). */
    fun pruneListId(words: List<Word>, listId: String): List<Word> {
        return words.map { word ->
            if (listId in word.listIds) word.copy(listIds = word.listIds - listId) else word
        }
    }

    enum class SourceBucket { WIKTIONARY, USER, NONE }

    /** Buckets words by meaning source in fixed WIKTIONARY, USER, NONE order; empty buckets included. */
    fun groupBySource(words: List<Word>): List<Pair<SourceBucket, List<Word>>> {
        val byBucket = words.groupBy { word ->
            when (word.meaningSource) {
                "wiktionary" -> SourceBucket.WIKTIONARY
                "user" -> SourceBucket.USER
                else -> SourceBucket.NONE
            }
        }
        return SourceBucket.entries.map { bucket -> bucket to (byBucket[bucket] ?: emptyList()) }
    }
}
