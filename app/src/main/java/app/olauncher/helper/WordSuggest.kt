package app.olauncher.helper

/** Offline "did you mean" matching for German words; umlauts and ß fold, case is ignored. */
object WordSuggest {
    const val MAX_SUGGESTIONS = 3
    const val MAX_EDIT_DISTANCE = 2

    fun suggest(savedWords: List<String>, typed: String): List<String> {
        val query = fold(typed)
        if (query.isEmpty()) return emptyList()
        val candidates = savedWords
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { fold(it) }
            .filter { !it.equals(typed.trim(), ignoreCase = true) }
            .map { it to fold(it) }
        val prefix = candidates.filter { it.second.startsWith(query) }
        val substring = candidates.filter { !it.second.startsWith(query) && it.second.contains(query) }
        val taken = (prefix + substring).map { it.first }.toSet()
        val fuzzy = candidates
            .filter { it.first !in taken }
            .map { it to distance(query, it.second) }
            .filter { it.second <= MAX_EDIT_DISTANCE }
            .sortedBy { it.second }
            .map { it.first.first }
        return (prefix.map { it.first } + substring.map { it.first } + fuzzy).take(MAX_SUGGESTIONS)
    }

    /** True when [typed] equals a saved word, ignoring case only (so "Muell" still suggests "Müll"). */
    fun hasExact(savedWords: List<String>, typed: String): Boolean {
        val query = typed.trim()
        return query.isNotEmpty() && savedWords.any { it.trim().equals(query, ignoreCase = true) }
    }

    /** Items without an exact saved match mapped to their offline suggestions (may be empty). */
    fun suggestForItems(savedWords: List<String>, items: List<String>): Map<String, List<String>> {
        val result = LinkedHashMap<String, List<String>>()
        for (item in items) {
            if (hasExact(savedWords, item)) continue
            result[item] = suggest(savedWords, item)
        }
        return result
    }

    /** Offline [local] suggestions first, then [online] ones; drops [typed] and case-insensitive repeats, capped at [MAX_SUGGESTIONS]. */
    fun mergeOptions(typed: String, local: List<String>, online: List<String>): List<String> {
        val seen = HashSet<String>()
        seen.add(typed.trim().lowercase())
        val options = ArrayList<String>()
        for (candidate in local + online) {
            if (options.size >= MAX_SUGGESTIONS) break
            if (seen.add(candidate.trim().lowercase())) options.add(candidate)
        }
        return options
    }

    fun fold(text: String): String {
        val sb = StringBuilder()
        for (c in text.trim().lowercase()) {
            when (c) {
                'ä' -> sb.append("ae")
                'ö' -> sb.append("oe")
                'ü' -> sb.append("ue")
                'ß' -> sb.append("ss")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun distance(a: String, b: String): Int {
        if (kotlin.math.abs(a.length - b.length) > MAX_EDIT_DISTANCE) return Int.MAX_VALUE
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            prev = cur
        }
        return prev[b.length]
    }
}
