package app.olauncher.ui.askai

/**
 * The display-time inline-markdown subset for assistant reply text (bold,
 * italic, inline code — no headings, lists or fences), parsed Context-free so
 * it stays JVM-testable (docs/design/ask-ai-chat.md §Chat screen "Reply
 * formatting").
 *
 * Display only: the stored transcript keeps the reply verbatim — the compaction
 * summary and the "original asked / original replied" lines quote it and the
 * token estimate counts characters — so this parser is never in the store or
 * the request builder.
 *
 * Safety rules:
 * - An UNCLOSED marker stays literal text: a truncated reply must not eat the
 *   rest of the paragraph, so scanning resumes after the stray delimiter and
 *   later pairs still render.
 * - Content must be non-empty and not whitespace-flanked (a `* b * c` stays
 *   literal, matching CommonMark's flanking rule).
 * - Intraword `_` (letter/digit on the outside of the pair) stays literal,
 *   CommonMark's intraword-underscore rule — `_` never breaks up a word.
 * - German quotation marks `„ … "` contain no delimiter, so every quoted
 *   example passes through untouched.
 *
 * Runs are flat: content inside a styled run is not re-parsed, and a code run's
 * content is verbatim. Adjacent plain characters merge into one run.
 */
object AiChatInlineMarkdown {

    /**
     * One rendered run: [text] with at most one of [bold], [italic], [code]
     * set (all false = plain). The view layer maps them to spans that layer on
     * top of the panel's style, so the body weight and the muted/danger colour
     * variants hold for emphasised runs too.
     */
    data class Run(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val code: Boolean = false,
    )

    /** The single-run fast path: plain text is handed back without restructuring. */
    fun parse(text: String): List<Run> {
        if (!text.contains('*') && !text.contains('_') && !text.contains('`')) {
            return listOf(Run(text))
        }
        val runs = mutableListOf<Run>()
        val plain = StringBuilder()
        fun flushPlain() {
            if (plain.isNotEmpty()) {
                runs.add(Run(plain.toString()))
                plain.clear()
            }
        }
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '`' -> {
                    val close = text.indexOf('`', i + 1)
                    if (close == i + 1 || close == -1) {
                        // Empty code span or unclosed backtick: literal.
                        plain.append(c)
                        i++
                    } else {
                        flushPlain()
                        runs.add(Run(text.substring(i + 1, close), code = true))
                        i = close + 1
                    }
                }
                c == '*' && text.startsWith("**", i) -> {
                    val close = text.indexOf("**", i + 2)
                    if (close == -1 || close == i + 2 || !isFlanked(text, i + 2, close)) {
                        // Unclosed, empty or whitespace-flanked: literal, scan on.
                        plain.append("**")
                        i += 2
                    } else {
                        flushPlain()
                        runs.add(Run(text.substring(i + 2, close), bold = true))
                        i = close + 2
                    }
                }
                c == '*' || c == '_' -> {
                    val close = text.indexOf(c, i + 1)
                    val intraword = c == '_' && (
                        (i > 0 && text[i - 1].isLetterOrDigit()) ||
                            (close + 1 < text.length && text[close + 1].isLetterOrDigit())
                        )
                    if (close == -1 || close == i + 1 || !isFlanked(text, i + 1, close) || intraword) {
                        plain.append(c)
                        i++
                    } else {
                        flushPlain()
                        runs.add(Run(text.substring(i + 1, close), italic = true))
                        i = close + 1
                    }
                }
                else -> {
                    plain.append(c)
                    i++
                }
            }
        }
        flushPlain()
        return runs
    }

    /** The run between [open] and [close] must be non-empty and not space-flanked. */
    private fun isFlanked(text: String, open: Int, close: Int): Boolean {
        if (open >= close) return false
        return !text[open].isWhitespace() && !text[close - 1].isWhitespace()
    }
}
