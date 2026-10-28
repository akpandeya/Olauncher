package app.olauncher.data.store

/**
 * Adapter from the AI-generated practice items of Epic 9 (#561, `PracticeItem`) to a
 * planner [DrillItem] (#621): mixed drill rounds draw up to
 * [DrillRoundPlanner.MAX_AI_PER_ROUND] of them per round, and the planner renders each
 * one as the existing self-graded flashcard (prompt + hint + answer on reveal +
 * Continue) — never a tap or typed question, so [DrillQuiz] scores them exactly like
 * built-in flashcards: no first attempts, no typed evidence, no maturity.
 *
 * The item's practice type becomes the [DrillItemId] type, so the round's per-question
 * label shows the practice type (e.g. "Cloze"); the item key is prefixed to keep AI
 * ids out of the built-in id space.
 *
 * [PracticeItem.full] (the model's filled sentence, in memory only) is dropped on
 * purpose: the flashcard reveal derives the filled sentence from the prompt's gap
 * marker and the answer ([DrillFlashcard.filledPrompt]), the same way built-in
 * flashcards do.
 */
object PracticeDrillItems {

    /** Prefix of every AI item's [DrillItemId] key, so AI steps are recognisable in tests. */
    const val KEY_PREFIX = "ai:"

    fun toDrillItem(item: PracticeItem): DrillItem = DrillItem(
        id = DrillItemId(item.type, key(item)),
        prompt = item.prompt,
        answer = item.answer,
        choices = item.choices,
        hint = item.hint,
    )

    fun toDrillItems(items: List<PracticeItem>): List<DrillItem> = items.map(::toDrillItem)

    /** True when a planned step's id belongs to an AI practice item. */
    fun isAiItem(id: DrillItemId): Boolean = id.key.startsWith(KEY_PREFIX)

    private fun key(item: PracticeItem): String = "$KEY_PREFIX${item.wordId}:${item.id}"
}
