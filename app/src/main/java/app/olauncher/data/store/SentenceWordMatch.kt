package app.olauncher.data.store

import java.util.Locale

object SentenceWordMatch {
    const val MIN_WORD_CHARS = 3

    private val TOKEN_REGEX = Regex("""\p{L}+(?:-\p{L}+)*""")

    fun tokens(sentence: String): List<String> =
        TOKEN_REGEX.findAll(sentence).map { it.value }.toList()
}

class SentenceWordMatcher(private val lemmaOf: (String) -> String?) {

    private val memo = HashMap<String, String?>()

    private val articleRegex = Regex("""^(?i)(?:der|die|das|ein|eine)\s+""")

    private fun stripLeadingArticle(wordText: String): String {
        val trimmed = wordText.trim()
        val stripped = articleRegex.replaceFirst(trimmed, "").trim()
        return if (stripped.isNotEmpty()) stripped else trimmed
    }

    fun keyOf(form: String): String {
        val resolved = if (memo.containsKey(form)) {
            memo[form]
        } else {
            val lemma = lemmaOf(form)
            memo[form] = lemma
            lemma
        }
        return (resolved ?: form).lowercase(Locale.GERMAN)
    }

    fun matches(wordText: String, sentence: String): Boolean {
        val phrase = stripLeadingArticle(wordText)
        if (phrase.length < SentenceWordMatch.MIN_WORD_CHARS) return false
        if (phrase.any { it.isWhitespace() }) {
            val regex = Regex("(?<!\\p{L})" + Regex.escape(phrase) + "(?!\\p{L})", RegexOption.IGNORE_CASE)
            return regex.containsMatchIn(sentence)
        }
        val target = keyOf(phrase)
        return SentenceWordMatch.tokens(sentence).any { t ->
            t.equals(phrase, ignoreCase = true) || keyOf(t) == target
        }
    }

    private class PreparedSentence(
        val sentence: RecentSentence,
        val tokens: List<String>,
        val tokenKeys: Set<String>
    )

    fun candidates(
        words: List<Word>,
        sentences: List<RecentSentence>,
        maxPerWord: Int = 5
    ): Map<Long, List<RecentSentence>> {
        if (words.isEmpty() || sentences.isEmpty() || maxPerWord <= 0) return emptyMap()

        val sentenceComparator = compareByDescending<RecentSentence> { it.publishedAt }
            .thenBy { it.text.length }
        val sortedSentences = sentences.sortedWith(sentenceComparator)

        val prepared = sortedSentences.map { sentence ->
            val tokens = SentenceWordMatch.tokens(sentence.text)
            val tokenKeys = tokens.map { keyOf(it) }.toSet()
            PreparedSentence(sentence, tokens, tokenKeys)
        }

        val result = LinkedHashMap<Long, List<RecentSentence>>()
        for (word in words) {
            val phrase = stripLeadingArticle(word.text)
            if (phrase.length < SentenceWordMatch.MIN_WORD_CHARS) continue

            val matching = if (phrase.any { it.isWhitespace() }) {
                val regex = Regex("(?<!\\p{L})" + Regex.escape(phrase) + "(?!\\p{L})", RegexOption.IGNORE_CASE)
                prepared.filter { regex.containsMatchIn(it.sentence.text) }
            } else {
                val target = keyOf(phrase)
                prepared.filter { p ->
                    p.tokens.any { it.equals(phrase, ignoreCase = true) } || p.tokenKeys.contains(target)
                }
            }

            val selected = matching.take(maxPerWord).map { it.sentence }
            if (selected.isNotEmpty()) {
                result[word.id] = selected
            }
        }
        return result
    }
}
