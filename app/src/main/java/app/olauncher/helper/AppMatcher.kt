package app.olauncher.helper

import java.text.Normalizer

object AppMatcher {

    const val SCORE_EXACT = 100
    const val SCORE_PREFIX = 90
    const val SCORE_WORD_START = 80
    const val SCORE_INITIALS = 70
    const val SCORE_SUBSTRING = 60
    const val SCORE_FUZZY = 40

    private val diacriticsRegex = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val separatorsRegex = Regex("[-_+,.`'\\s\\p{Z}]")

    fun normalizeForSearch(text: CharSequence): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(diacriticsRegex, "")
            .replace(separatorsRegex, "")

    private fun stripDiacritics(text: CharSequence): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(diacriticsRegex, "")

    fun score(label: String, query: String): Int? {
        val normQuery = normalizeForSearch(query).lowercase()
        if (normQuery.isEmpty()) return null

        val cleanLabel = stripDiacritics(label)
        val normLabel = normalizeForSearch(cleanLabel).lowercase()
        if (normLabel.isEmpty()) return null

        // 1. Exact match (100)
        if (normLabel == normQuery) return SCORE_EXACT

        // 2. Prefix match (90)
        if (normLabel.startsWith(normQuery)) return SCORE_PREFIX

        // 3. Word-start prefix (80)
        val wordStarts = getWordStartIndices(cleanLabel)
        for (i in wordStarts) {
            if (i > 0) {
                val remainder = normalizeForSearch(cleanLabel.substring(i)).lowercase()
                if (remainder.startsWith(normQuery)) {
                    return SCORE_WORD_START
                }
            }
        }

        // 4. Initials (70)
        if (normQuery.length >= 2) {
            val initialsSet = getCandidateInitials(cleanLabel, wordStarts)
            if (initialsSet.any { it == normQuery || it.startsWith(normQuery) }) {
                return SCORE_INITIALS
            }
        }

        // 5. Substring match (60)
        if (normLabel.contains(normQuery)) return SCORE_SUBSTRING

        // 6. Fuzzy match: one edit (Damerau-Levenshtein <= 1) against label prefix for queries >= 4 chars (40)
        if (normQuery.length >= 4) {
            val minLen = maxOf(1, normQuery.length - 1)
            val maxLen = minOf(normLabel.length, normQuery.length + 1)
            for (len in minLen..maxLen) {
                val prefix = normLabel.substring(0, len)
                if (damerauLevenshtein(normQuery, prefix) <= 1) {
                    return SCORE_FUZZY
                }
            }
        }

        return null
    }

    fun matchRanges(label: String, query: String): List<IntRange> {
        val normQuery = normalizeForSearch(query).lowercase()
        if (normQuery.isEmpty()) return emptyList()

        val (cleanLabel, cleanToLabelMap) = buildCleanLabelAndMap(label)
        val (normLabel, normToCleanMap) = getNormMapping(cleanLabel)
        if (normLabel.isEmpty()) return emptyList()

        // 1 & 2. Exact match (100) and Prefix match (90)
        if (normLabel.startsWith(normQuery)) {
            val matchedCleanIndices = (0 until normQuery.length).map { normToCleanMap[it] }
            val matchedLabelIndices = matchedCleanIndices.map { cleanToLabelMap[it] }
            return collapseIndices(matchedLabelIndices)
        }

        // 3. Word-start prefix (80)
        val wordStarts = getWordStartIndices(cleanLabel)
        for (i in wordStarts) {
            if (i > 0) {
                val cleanSub = cleanLabel.substring(i)
                val (subNorm, subNormToCleanMap) = getNormMapping(cleanSub)
                if (subNorm.startsWith(normQuery)) {
                    val matchedCleanIndices = (0 until normQuery.length).map { i + subNormToCleanMap[it] }
                    val matchedLabelIndices = matchedCleanIndices.map { cleanToLabelMap[it] }
                    return collapseIndices(matchedLabelIndices)
                }
            }
        }

        // 4. Initials (70)
        if (normQuery.length >= 2) {
            if (wordStarts.size >= 2) {
                val wordInitials = wordStarts.map { cleanLabel[it].lowercaseChar() }.joinToString("")
                if (wordInitials == normQuery || wordInitials.startsWith(normQuery)) {
                    val matchedCleanIndices = wordStarts.take(normQuery.length)
                    val matchedLabelIndices = matchedCleanIndices.map { cleanToLabelMap[it] }
                    return collapseIndices(matchedLabelIndices)
                }
            }

            if (cleanLabel.any { it.isLowerCase() }) {
                val capitalIndices = cleanLabel.indices.filter { cleanLabel[it].isUpperCase() }
                if (capitalIndices.size >= 2) {
                    val capitals = capitalIndices.map { cleanLabel[it].lowercaseChar() }.joinToString("")
                    if (capitals == normQuery || capitals.startsWith(normQuery)) {
                        val matchedCleanIndices = capitalIndices.take(normQuery.length)
                        val matchedLabelIndices = matchedCleanIndices.map { cleanToLabelMap[it] }
                        return collapseIndices(matchedLabelIndices)
                    }
                }
            }

            val tokenIndices = getTokenFirstCharIndices(cleanLabel)
            if (tokenIndices.size >= 2) {
                val tokenInitials = tokenIndices.map { cleanLabel[it].lowercaseChar() }.joinToString("")
                if (tokenInitials == normQuery || tokenInitials.startsWith(normQuery)) {
                    val matchedCleanIndices = tokenIndices.take(normQuery.length)
                    val matchedLabelIndices = matchedCleanIndices.map { cleanToLabelMap[it] }
                    return collapseIndices(matchedLabelIndices)
                }
            }
        }

        // 5. Substring match (60)
        val subIndex = normLabel.indexOf(normQuery)
        if (subIndex >= 0) {
            val matchedCleanIndices = (subIndex until (subIndex + normQuery.length)).map { normToCleanMap[it] }
            val matchedLabelIndices = matchedCleanIndices.map { cleanToLabelMap[it] }
            return collapseIndices(matchedLabelIndices)
        }

        // 6. Fuzzy match (40) or no match -> none for fuzzy
        return emptyList()
    }

    private fun buildCleanLabelAndMap(label: String): Pair<String, IntArray> {
        val nfc = Normalizer.normalize(label, Normalizer.Form.NFC)
        val clean = StringBuilder()
        val map = ArrayList<Int>()
        for (i in nfc.indices) {
            val c = nfc[i]
            val stripped = Normalizer.normalize(c.toString(), Normalizer.Form.NFD)
                .replace(diacriticsRegex, "")
            if (stripped.isNotEmpty()) {
                clean.append(stripped)
                for (k in stripped.indices) {
                    map.add(i)
                }
            }
        }
        return clean.toString() to map.toIntArray()
    }

    private fun getNormMapping(cleanLabel: String): Pair<String, IntArray> {
        val norm = StringBuilder()
        val map = ArrayList<Int>()
        for (i in cleanLabel.indices) {
            val c = cleanLabel[i]
            if (!separatorsRegex.matches(c.toString())) {
                norm.append(c.lowercaseChar())
                map.add(i)
            }
        }
        return norm.toString() to map.toIntArray()
    }

    private fun getTokenFirstCharIndices(cleanLabel: String): List<Int> {
        val indices = mutableListOf<Int>()
        var inToken = false
        var tokenStart = -1
        for (i in cleanLabel.indices) {
            val c = cleanLabel[i]
            val isSep = separatorsRegex.matches(c.toString())
            if (!isSep) {
                if (!inToken) {
                    inToken = true
                    tokenStart = i
                }
            } else {
                if (inToken) {
                    val first = (tokenStart until i).firstOrNull { cleanLabel[it].isLetterOrDigit() }
                    if (first != null) indices.add(first)
                    inToken = false
                }
            }
        }
        if (inToken) {
            val first = (tokenStart until cleanLabel.length).firstOrNull { cleanLabel[it].isLetterOrDigit() }
            if (first != null) indices.add(first)
        }
        return indices
    }

    private fun collapseIndices(indices: List<Int>): List<IntRange> {
        if (indices.isEmpty()) return emptyList()
        val sorted = indices.distinct().sorted()
        val ranges = mutableListOf<IntRange>()
        var start = sorted[0]
        var end = sorted[0]
        for (i in 1 until sorted.size) {
            val curr = sorted[i]
            if (curr == end + 1) {
                end = curr
            } else {
                ranges.add(start..end)
                start = curr
                end = curr
            }
        }
        ranges.add(start..end)
        return ranges
    }

    private fun getWordStartIndices(text: String): List<Int> {
        val indices = mutableListOf<Int>()
        for (i in text.indices) {
            val c = text[i]
            if (!c.isLetterOrDigit()) continue

            if (i == 0) {
                indices.add(i)
            } else {
                val prev = text[i - 1]
                if (!prev.isLetterOrDigit() || separatorsRegex.matches(prev.toString())) {
                    if (prev == '\'' && i >= 2 && text[i - 2].isLetter()) {
                        continue
                    }
                    indices.add(i)
                } else if (c.isUpperCase() && prev.isLowerCase()) {
                    indices.add(i)
                } else if (c.isUpperCase() && prev.isUpperCase() && i + 1 < text.length && text[i + 1].isLowerCase()) {
                    indices.add(i)
                }
            }
        }
        return indices
    }

    private fun getCandidateInitials(cleanLabel: String, wordStarts: List<Int>): Set<String> {
        val initialsSet = mutableSetOf<String>()

        if (wordStarts.size >= 2) {
            val wordInitials = wordStarts.map { cleanLabel[it].lowercaseChar() }.joinToString("")
            initialsSet.add(wordInitials)
        }

        val tokens = cleanLabel.split(separatorsRegex).filter { it.isNotEmpty() }
        if (tokens.size >= 2) {
            val tokenInitials = tokens.mapNotNull { token ->
                token.firstOrNull { it.isLetterOrDigit() }?.lowercaseChar()
            }.joinToString("")
            if (tokenInitials.length >= 2) {
                initialsSet.add(tokenInitials)
            }
        }

        if (cleanLabel.any { it.isLowerCase() }) {
            val capitals = cleanLabel.filter { it.isUpperCase() }.lowercase()
            if (capitals.length >= 2) {
                initialsSet.add(capitals)
            }
        }

        return initialsSet
    }

    internal fun damerauLevenshtein(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        if (m == 0) return n
        if (n == 0) return m

        val d = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) d[i][0] = i
        for (j in 0..n) d[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                d[i][j] = minOf(
                    d[i - 1][j] + 1,       // deletion from s1
                    d[i][j - 1] + 1,       // insertion into s1
                    d[i - 1][j - 1] + cost // substitution
                )
                if (i > 1 && j > 1 && s1[i - 1] == s2[j - 2] && s1[i - 2] == s2[j - 1]) {
                    d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1) // transposition
                }
            }
        }
        return d[m][n]
    }
}
