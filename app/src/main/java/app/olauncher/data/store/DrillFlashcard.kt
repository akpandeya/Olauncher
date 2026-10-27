package app.olauncher.data.store

/**
 * Context-free presentation of a flashcard step (rung 1, tap-to-reveal): the prompt
 * before and after the reveal, and the hint line once revealed. The screen only adds
 * the localised "tap to reveal" placeholder.
 */
object DrillFlashcard {

    /**
     * The prompt with its gap filled by the answer — the sentence a revealed flashcard
     * and a rule recap both teach.
     */
    fun filledPrompt(question: DrillQuizQuestion): String =
        if (question.prompt.contains(DrillItem.GAP_MARKER)) {
            question.prompt.replaceFirst(DrillItem.GAP_MARKER, question.answer)
        } else {
            question.prompt
        }

    /** The card's prompt: the raw gap until the card is tapped, the answer filled once revealed. */
    fun prompt(question: DrillQuizQuestion, revealed: Boolean): String =
        if (revealed) filledPrompt(question) else question.prompt

    /**
     * The hint line once the card is revealed: with a gap the answer is already in the
     * filled sentence, so the line is the rule alone; without one the answer itself is
     * the content the card teaches, joined with the rule.
     */
    fun revealedHint(question: DrillQuizQuestion): String? =
        if (question.prompt.contains(DrillItem.GAP_MARKER)) {
            question.hint
        } else {
            listOfNotNull(question.answer, question.hint).joinToString(" · ")
        }
}
