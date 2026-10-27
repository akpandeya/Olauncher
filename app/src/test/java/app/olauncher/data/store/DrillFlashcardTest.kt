package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Test

class DrillFlashcardTest {

    private val gapped = DrillItem(
        id = DrillItemId(DrillType.PREPOSITIONS, "mit"),
        prompt = "Ich fahre ___ dem Zug.",
        answer = "mit",
        choices = listOf("mit", "bei", "zu"),
        hint = "Dativ",
    )

    private val plain = DrillItem(
        id = DrillItemId(DrillType.ARTICLES, "buch"),
        prompt = "Wortart von „Buch“",
        answer = "das Buch",
        choices = listOf("das Buch", "der Buch", "die Buch"),
        hint = "Neutrum",
    )

    private fun flashcard(item: DrillItem): DrillQuizQuestion =
        DrillQuizQuestion(item, DrillFormat.FLASHCARD)

    @Test
    fun unrevealedGapPromptKeepsTheRawGap() {
        assertEquals(
            "Ich fahre ___ dem Zug.",
            DrillFlashcard.prompt(flashcard(gapped), revealed = false),
        )
    }

    @Test
    fun revealedGapPromptFillsTheAnswer() {
        assertEquals(
            "Ich fahre mit dem Zug.",
            DrillFlashcard.prompt(flashcard(gapped), revealed = true),
        )
    }

    @Test
    fun revealedNonGapPromptIsUnchanged() {
        assertEquals(
            plain.prompt,
            DrillFlashcard.prompt(flashcard(plain), revealed = true),
        )
    }

    @Test
    fun revealedHintWithAGapIsTheRuleAlone() {
        assertEquals("Dativ", DrillFlashcard.revealedHint(flashcard(gapped)))
    }

    @Test
    fun revealedHintWithoutAGapJoinsAnswerAndRule() {
        assertEquals("das Buch · Neutrum", DrillFlashcard.revealedHint(flashcard(plain)))
    }

    @Test
    fun revealedHintWithoutAGapOrRuleIsTheAnswerAlone() {
        val noHint = plain.copy(hint = null)
        assertEquals("das Buch", DrillFlashcard.revealedHint(flashcard(noHint)))
    }

    @Test
    fun filledPromptMatchesTheRecapSentence() {
        // A recap teaches the same filled sentence a revealed flashcard does.
        val recap = DrillQuizQuestion(gapped, DrillFormat.RECAP)
        assertEquals(
            DrillFlashcard.filledPrompt(recap),
            DrillFlashcard.prompt(flashcard(gapped), revealed = true),
        )
    }
}
