package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The coarse word-class type filter (round 3): nouns get article/plural/case
 * types, infinitive-shaped words get verb types, unknown words get nothing (the
 * caller then sends the full list as before).
 */
class PracticeTypeNarrowingTest {

    private fun word(text: String) = Word(
        id = 1, text = text, context = "Kontext.", dueDay = 0, addedAt = 0,
    )

    @Test
    fun `a capitalized word is treated as a noun and gets the article and case types`() {
        val narrowed = PracticeTypeNarrowing.narrow(word("Unterschrift"))
        assertEquals(PracticeTypeNarrowing.NOUN_TYPES, narrowed)
        assertTrue(DrillType.ARTICLES in narrowed)
        assertTrue(DrillType.PLURALS in narrowed)
        assertTrue(DrillType.CASE_ENDINGS in narrowed)
        // A noun never gets the verb-only drills.
        assertFalse(DrillType.CONJUGATION in narrowed)
        assertFalse(DrillType.KONJUNKTIV_I in narrowed)
    }

    @Test
    fun `an infinitive-shaped word is treated as a verb and gets the verb types`() {
        val narrowed = PracticeTypeNarrowing.narrow(word("insistieren"))
        assertEquals(PracticeTypeNarrowing.VERB_TYPES, narrowed)
        assertTrue(DrillType.CONJUGATION in narrowed)
        assertTrue(DrillType.SEPARABLE_VERBS in narrowed)
        assertTrue(DrillType.MODAL_VERBS in narrowed)
        // A verb never gets the noun-only drills.
        assertFalse(DrillType.ARTICLES in narrowed)
        assertFalse(DrillType.N_DECLENSION in narrowed)
    }

    @Test
    fun `an unknown word class gets no narrowing so the caller sends the full list`() {
        // Adjective.
        assertTrue(PracticeTypeNarrowing.narrow(word("schnell")).isEmpty())
        // Multi-word phrase.
        assertTrue(PracticeTypeNarrowing.narrow(word("insistieren auf")).isEmpty())
        // Blank text.
        assertTrue(PracticeTypeNarrowing.narrow(word("  ")).isEmpty())
        // Short lowercase word ("hin"): not reliably a verb.
        assertTrue(PracticeTypeNarrowing.narrow(word("hin")).isEmpty())
    }

    @Test
    fun `cloze is never in a narrowed list because the contract always asks for one`() {
        assertFalse(DrillType.CLOZE in PracticeTypeNarrowing.NOUN_TYPES)
        assertFalse(DrillType.CLOZE in PracticeTypeNarrowing.VERB_TYPES)
        assertFalse(DrillType.CLOZE in PracticeTypeNarrowing.narrow(word("Haus")))
        assertFalse(DrillType.CLOZE in PracticeTypeNarrowing.narrow(word("insistieren")))
    }
}
