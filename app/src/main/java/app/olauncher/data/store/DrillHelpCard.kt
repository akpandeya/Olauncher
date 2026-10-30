package app.olauncher.data.store

/**
 * Context-free help-card states (#651 design, docs/design/drill-help-card.md §2–3):
 * what the help slot above the prompt shows in each moment of a question, and how the
 * pre-answer text is kept from naming the item's answer. The drill screen only binds
 * these states to views; everything decidable without a Context lives here.
 */
object DrillHelpCard {

    /** One help-slot rendering, ready to bind. Null means the slot stays hidden. */
    data class State(
        val rule: DrillHelpRule,
        /** True in the flashcard and after-a-miss states: highlight and bold the answer's cell. */
        val showAnswer: Boolean,
        /**
         * The example sentence with the changing form wrapped in `**…**`. Shown only in
         * the flashcard and after-a-miss states, never before a tap answer (design §3).
         */
        val example: String?,
        /**
         * The miss line after a wrong tap: the wrong choice, then why it is wrong and
         * which form fits. Null outside the after-a-miss state.
         */
        val miss: MissParts?,
    )

    /**
     * The help state for [question]:
     *
     * - Flashcard (rung 1): open, full card, the answer shown — it is a reading step.
     * - "Why?" tapped before answering: open, full card, no answer named.
     * - After a wrong answer: open, the ✗ miss line last, directly above the prompt.
     * - Any other moment: null — the slot stays hidden behind the collapsed cue line.
     *
     * A recap card keeps its own compact presentation and never shows this card.
     */
    fun state(
        question: DrillQuizQuestion,
        whyOpen: Boolean,
        answeredWrong: Boolean,
        wrongChoice: String? = null,
    ): State? {
        val rule = DrillHelpContent.rule(question.item.ruleId) ?: return null
        return when {
            question.isRecap -> null
            question.isFlashcard -> State(
                rule = rule,
                showAnswer = true,
                example = filledExample(question),
                miss = null,
            )
            answeredWrong -> State(
                rule = rule,
                showAnswer = true,
                example = filledExample(question),
                miss = missParts(question, wrongChoice),
            )
            whyOpen -> State(rule = rule, showAnswer = false, example = null, miss = null)
            else -> null
        }
    }

    /**
     * The help state of a word-order item (#699). A word-order round has no
     * [DrillQuizQuestion], so this takes the item itself. Before an answer only "Why?"
     * opens the card, with the rule and no example. After a wrong order the card adds
     * the item's own example. There is no miss line: the screen's inline correction
     * already shows the accepted order.
     */
    fun wordOrderState(item: WordOrderItem, whyOpen: Boolean, answeredWrong: Boolean): State? {
        val rule = DrillHelpContent.rule(item.ruleId) ?: return null
        return when {
            answeredWrong -> State(
                rule = rule,
                showAnswer = true,
                example = wordOrderExample(item),
                miss = null,
            )
            whyOpen -> State(rule = rule, showAnswer = false, example = null, miss = null)
            else -> null
        }
    }

    /**
     * The word-order item's own example from [ITEM_HELP_EXAMPLES], or null when none is
     * authored. Word-order prompts have no gap, so the rule's example is never used here:
     * it would show the same sentence for every item of the rule.
     */
    fun wordOrderExample(item: WordOrderItem): String? = ITEM_HELP_EXAMPLES[item.id.key]

    /**
     * The item's own example: the prompt with its gap filled by the answer, the answer
     * wrapped in `**…**` for bold+underline. Items whose prompt has no gap marker —
     * the which-case prepositions, the verb + preposition case questions and the
     * word-order items — carry their own per-item example in [ITEM_HELP_EXAMPLES]
     * (prepositions.md, verb-prepositions.md and word-order.md author the example per
     * item in docs/design/drill-content/); the rule's example stays as the fallback for
     * any other gapless item.
     */
    fun filledExample(question: DrillQuizQuestion): String {
        ITEM_HELP_EXAMPLES[question.id.key]?.let { return it }
        val prompt = question.prompt
        return if (prompt.contains(DrillItem.GAP_MARKER)) {
            prompt.replaceFirst(DrillItem.GAP_MARKER, "**${question.answer}**")
        } else {
            question.item.ruleId?.let { DrillHelpContent.rule(it)?.example } ?: question.answer
        }
    }

    /**
     * The miss line's parts after a wrong tap: the wrong choice, then why that choice
     * is wrong and which form fits — the item's reason for that exact wrong choice
     * ([DrillItem.missByChoice]), falling back to the item's general [DrillItem.missReason]
     * and then to the plain answer. The screen renders it with the ✗ mark string resource.
     */
    fun missParts(question: DrillQuizQuestion, wrongChoice: String?): MissParts? {
        val wrong = wrongChoice?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        val reason = question.item.missByChoice[wrong]?.takeUnless { it.isBlank() }
            ?: question.item.missReason?.takeUnless { it.isBlank() }
            ?: question.answer
        return MissParts(wrong = wrong, reason = reason)
    }

    data class MissParts(val wrong: String, val reason: String)

    /**
     * Splits `**…**` marked text into segments; [Segment.bold] marks the changing form
     * (bold + underline when rendered). Unmarked segments render plain.
     */
    fun markedSegments(text: String): List<Segment> {
        val segments = mutableListOf<Segment>()
        var index = 0
        while (index < text.length) {
            val start = text.indexOf("**", index)
            if (start < 0) {
                segments += Segment(text.substring(index), bold = false)
                break
            }
            if (start > index) segments += Segment(text.substring(index, start), bold = false)
            val end = text.indexOf("**", start + 2)
            if (end < 0) {
                segments += Segment(text.substring(start + 2), bold = true)
                break
            }
            segments += Segment(text.substring(start + 2, end), bold = true)
            index = end + 2
        }
        return segments.filter { it.text.isNotEmpty() }
    }

    /**
     * Splits `*…*` marked text into segments; [Segment.italic] marks the emphasized
     * span (italic when rendered). The verb + preposition miss lines mark their
     * preposition pairs this way; without this split the marks would show as literal
     * asterisks on the drill screen. An unmatched `*` stays literal.
     */
    fun italicSegments(text: String): List<Segment> {
        val segments = mutableListOf<Segment>()
        var index = 0
        while (index < text.length) {
            val start = text.indexOf('*', index)
            if (start < 0) {
                segments += Segment(text.substring(index), bold = false)
                break
            }
            if (start > index) segments += Segment(text.substring(index, start), bold = false)
            val end = text.indexOf('*', start + 1)
            if (end < 0) {
                segments += Segment(text.substring(start), bold = false)
                break
            }
            segments += Segment(text.substring(start + 1, end), bold = false, italic = true)
            index = end + 1
        }
        return segments.filter { it.text.isNotEmpty() }
    }

    data class Segment(val text: String, val bold: Boolean, val italic: Boolean = false)
}
