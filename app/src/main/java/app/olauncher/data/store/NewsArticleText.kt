package app.olauncher.data.store

/**
 * Extracts and cleans usable German sentences from news article HTML.
 * Context-free and JVM-testable.
 */
object NewsArticleText {

    const val MAX_HTML_CHARS = 2_000_000
    const val MAX_SENTENCES_PER_ARTICLE = 40

    private val COMMENT_REGEX = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)
    private val BLOCKS_REGEX = Regex(
        """<(script|style|noscript|svg|template|nav|header|footer|aside|figure|figcaption|form|button)\b[^>]*>.*?</\1\s*>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val ARTICLE_OPEN_REGEX = Regex("""<article\b""", RegexOption.IGNORE_CASE)
    private val ARTICLE_CLOSE_REGEX = Regex("""</article\s*>""", RegexOption.IGNORE_CASE)
    private val BODY_OPEN_REGEX = Regex("""<body\b""", RegexOption.IGNORE_CASE)
    private val BODY_CLOSE_REGEX = Regex("""</body\s*>""", RegexOption.IGNORE_CASE)

    private val P_TAG_REGEX = Regex("""<p\b([^>]*)>(.*?)</p>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val CLASS_ATTR_REGEX = Regex("""\bclass\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)
    private val BR_REGEX = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
    private val TAG_REGEX = Regex("""<[^>]+>""")
    private val WHITESPACE_REGEX = Regex("""\s+""")

    private val SKIPPED_CLASSES = listOf(
        "caption",
        "copyright",
        "credit",
        "byline",
        "author",
        "date",
        "meta",
        "teaser__topline"
    )

    private val OPENING_QUOTES = setOf('„', '"', '»', '«')
    private val FORBIDDEN_SUBSTRINGS = listOf("http", "www.", "@", "©", "|", "#")
    private val FORBIDDEN_PREFIXES = listOf(
        "Foto:",
        "Bild:",
        "Quelle:",
        "Video:",
        "Audio:",
        "Mehr zum Thema",
        "Lesen Sie",
        "Hören Sie",
        "Anzeige"
    )

    fun paragraphs(html: String): List<String> {
        if (html.length > MAX_HTML_CHARS || !html.contains("</p>", ignoreCase = true)) {
            return emptyList()
        }

        var text = COMMENT_REGEX.replace(html, "")
        text = BLOCKS_REGEX.replace(text, "")

        val articleOpen = ARTICLE_OPEN_REGEX.find(text)
        val region = if (articleOpen != null) {
            val articleClose = ARTICLE_CLOSE_REGEX.findAll(text).lastOrNull()
            if (articleClose != null && articleClose.range.last >= articleOpen.range.first) {
                text.substring(articleOpen.range.first, articleClose.range.last + 1)
            } else {
                text.substring(articleOpen.range.first)
            }
        } else {
            val bodyOpen = BODY_OPEN_REGEX.find(text)
            val bodyClose = BODY_CLOSE_REGEX.findAll(text).lastOrNull()
            if (bodyOpen != null && bodyClose != null && bodyClose.range.last >= bodyOpen.range.first) {
                text.substring(bodyOpen.range.first, bodyClose.range.last + 1)
            } else {
                text
            }
        }

        val result = mutableListOf<String>()
        for (match in P_TAG_REGEX.findAll(region)) {
            val attributes = match.groupValues[1]
            val innerHtml = match.groupValues[2]

            val hasSkippedClass = CLASS_ATTR_REGEX.findAll(attributes).any { classMatch ->
                val classValue = classMatch.groupValues[1].ifEmpty {
                    classMatch.groupValues[2].ifEmpty {
                        classMatch.groupValues[3]
                    }
                }
                SKIPPED_CLASSES.any { classValue.contains(it, ignoreCase = true) }
            }
            if (hasSkippedClass) continue

            val cleaned = cleanParagraph(innerHtml)
            if (cleaned.isNotEmpty()) {
                result.add(cleaned)
            }
        }
        return result
    }

    fun isUsableSentence(s: String): Boolean {
        if (s.length !in 30..300) return false

        val words = s.trim().split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
        if (words.size !in 5..40) return false

        val firstChar = s.first()
        if (!firstChar.isUpperCase() && firstChar !in OPENING_QUOTES) return false

        val stripped = s.trimEnd('“', '”', '"', '«', '»', '\'')
        val lastChar = stripped.lastOrNull() ?: return false
        if (lastChar != '.' && lastChar != '!' && lastChar != '?') return false

        if (FORBIDDEN_SUBSTRINGS.any { s.contains(it, ignoreCase = true) }) return false

        if (FORBIDDEN_PREFIXES.any { s.startsWith(it, ignoreCase = true) }) return false

        val nonWhitespaceCount = s.count { !it.isWhitespace() }
        if (nonWhitespaceCount == 0) return false
        val letterCount = s.count { it.isLetter() }
        if (letterCount * 100 < nonWhitespaceCount * 60) return false

        return true
    }

    fun sentences(html: String): List<String> =
        paragraphs(html)
            .flatMap { ContextSentence.split(it) }
            .filter { isUsableSentence(it) }
            .distinct()
            .take(MAX_SENTENCES_PER_ARTICLE)

    private fun cleanParagraph(raw: String): String {
        var text = BR_REGEX.replace(raw, " ")
        text = TAG_REGEX.replace(text, "")
        text = WiktionaryParser.decodeHtmlEntities(text)
        text = text.replace('\u00A0', ' ')
        text = WHITESPACE_REGEX.replace(text, " ")
        return text.trim()
    }
}
