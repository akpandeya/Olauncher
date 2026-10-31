package app.olauncher.data.store

/**
 * Context-free help-card states (#651 design, docs/design/drill-help-card.md §2–3):
 * what the help slot above the prompt shows in each moment of a question, and how the
 * pre-answer text is kept from naming the item's answer. The drill screen only binds
 * these states to views; everything decidable without a Context lives here.
 */
object DrillHelpCard {

    /**
     * One cell of a rule's forms table by position: the row's label and the data
     * column's header, as authored in the table (for example Cell("Gen", "n")).
     */
    data class Cell(val row: String, val column: String)

    /** One help-slot rendering, ready to bind. Null means the slot stays hidden. */
    data class State(
        val rule: DrillHelpRule,
        /** True in the flashcard and after-a-miss states: highlight and bold the answer's cell. */
        val showAnswer: Boolean,
        /**
         * The item's own cell in the rule's table, when the item's position is known
         * ([DrillItem.helpCell]). Null keeps the text-match fallback of
         * [answerCellPosition].
         */
        val answerCell: Cell? = null,
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
                answerCell = question.item.helpCell,
                example = filledExample(question),
                miss = null,
            )
            answeredWrong -> State(
                rule = rule,
                showAnswer = true,
                answerCell = question.item.helpCell,
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

    /** The bare gender/number column labels: m, f, n, pl. */
    private val BARE_GENDER_HEADERS = setOf("m", "f", "n", "pl")

    /**
     * The chips of the card's pattern row: the table's data-column headers — except
     * the bare gender labels (`m · f · n · pl`), which only repeat the table's own
     * header row rendered right below and, detached from their cells, carry no
     * pattern of their own (#723). Question-word headers (`Wer? → Nom`) name the
     * pattern and stay. An empty result hides the chips row.
     */
    fun patternChips(table: DrillHelpTable): List<String> {
        val chips = table.headers.drop(1).filter { it.isNotBlank() }
        return if (chips.all { it.trim().lowercase() in BARE_GENDER_HEADERS }) {
            emptyList()
        } else {
            chips
        }
    }

    /**
     * The table position to bold+underline for the item's answer, as `(row, data
     * column)` indexes into [table], or null for no cell.
     *
     * The item's own [cell] wins when the item carries one ([DrillItem.helpCell]):
     * the position is the fact the design authors (docs/design/drill-content/:
     * "highlight the row and the column of the item's noun, and bold its article"),
     * and a repeated form in another column is a different noun's form.
     *
     * Without an authored cell the answer's text decides — but only when exactly one
     * cell in the whole table equals it. When several do (the genitive *des* covers
     * masculine and neuter), no single cell is this item's own and nothing is
     * highlighted: lighting them all answers "where does this form appear?" instead
     * of "this noun's form", and quietly approves the answer for either column.
     */
    fun answerCellPosition(table: DrillHelpTable, answer: String?, cell: Cell?): Pair<Int, Int>? {
        if (answer.isNullOrBlank()) return null
        cell?.let { authored ->
            val row = table.rows.indexOfFirst {
                it.label.trim().equals(authored.row.trim(), ignoreCase = true)
            }
            val column = table.headers.drop(1).indexOfFirst {
                it.trim().equals(authored.column.trim(), ignoreCase = true)
            }
            if (row >= 0 && column >= 0) return row to column
        }
        return table.rows.flatMapIndexed { row, candidate ->
            candidate.cells.mapIndexedNotNull { column, text ->
                if (text.trim().equals(answer.trim(), ignoreCase = true)) row to column else null
            }
        }.singleOrNull()
    }

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
