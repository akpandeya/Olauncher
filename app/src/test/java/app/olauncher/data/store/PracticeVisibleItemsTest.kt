package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The read-time view over stored practice items: duplicates, bare gaps and AI
 * cards the current filters reject are hidden, never deleted, and a word left
 * without a card still gets its cloze fallback. Synthetic words only.
 */
class PracticeVisibleItemsTest {

    private val verb = Word(
        id = 41,
        text = "verzichten",
        meaning = "etwas nicht tun oder nehmen",
        context = "Die Firma verzichtet auf neue Zölle.",
        dueDay = 20_261_101,
        addedAt = 1,
    )

    private fun aiItem(
        id: Long,
        prompt: String,
        answer: String,
        type: DrillType = DrillType.CLOZE,
        wordId: Long = verb.id,
        hint: String? = null,
    ) = PracticeItem(
        id = id, wordId = wordId, type = type, prompt = prompt, answer = answer,
        choices = listOf(answer, "${answer}x"), hint = hint,
        source = PracticeItem.Source.WORD, model = "test-model", createdAt = 100,
    )

    private fun localItem(id: Long, prompt: String, answer: String, wordId: Long = verb.id) = PracticeItem(
        id = id, wordId = wordId, type = DrillType.CLOZE, prompt = prompt, answer = answer,
        choices = listOf(answer, "${answer}n"), source = PracticeItem.Source.DIGEST, model = null, createdAt = 200,
    )

    private val goodCard = aiItem(1, "Die Firma ___ (verzichten) auf neue Zölle.", "verzichtet")

    @Test
    fun `a good card is kept unchanged`() {
        assertEquals(listOf(goodCard), PracticeVisibleItems.of(listOf(goodCard), listOf(verb)))
    }

    @Test
    fun `an exact duplicate is hidden and the earliest copy stays`() {
        // Same word, type, prompt and answer: a later fallback copy with other
        // choices and no hint is still the same card for the learner.
        val copy = localItem(7, goodCard.prompt, goodCard.answer)
        val anotherCopy = copy.copy(id = 9, choices = copy.choices.reversed())

        val visible = PracticeVisibleItems.of(listOf(goodCard, copy, anotherCopy), listOf(verb))

        assertEquals(listOf(1L), visible.map { it.id })
    }

    @Test
    fun `a bare gap is hidden`() {
        val bare = localItem(2, "___", "verzichten")

        val visible = PracticeVisibleItems.of(listOf(bare, goodCard), listOf(verb))

        assertEquals(listOf(1L), visible.map { it.id })
    }

    @Test
    fun `an AI card that never names the verb is hidden (missing_base)`() {
        val noBase = aiItem(3, "Die Firma ___ auf neue Zölle.", "verzichtet", type = DrillType.CONJUGATION)

        val visible = PracticeVisibleItems.of(listOf(goodCard, noBase), listOf(verb))

        assertEquals(listOf(1L), visible.map { it.id })
        // The same card is fine on a word that is no verb infinitive.
        val noun = verb.copy(id = 42, text = "Firma")
        val nounCard = noBase.copy(id = 4, wordId = noun.id)
        assertEquals(listOf(4L), PracticeVisibleItems.of(listOf(nounCard), listOf(noun)).map { it.id })
    }

    @Test
    fun `other filters run on stored AI cards too`() {
        // The hint spells out the answer (leak).
        val leak = aiItem(5, "Die Firma ___ (verzichten) auf neue Zölle.", "verzichtete", hint = "verzichtete, Präteritum")

        val visible = PracticeVisibleItems.of(listOf(goodCard, leak), listOf(verb))

        assertEquals(listOf(1L), visible.map { it.id })
    }

    @Test
    fun `a later local copy of a hidden AI card is shown`() {
        // The hidden AI card does not count as the first copy: the local
        // fallback with the same prompt and answer skips the AI filters, so it
        // is the first visible copy and stays.
        val noBase = aiItem(3, "Die Firma ___ auf neue Zölle.", "verzichtet")
        val copy = localItem(8, noBase.prompt, noBase.answer)

        val visible = PracticeVisibleItems.of(listOf(goodCard, noBase, copy), listOf(verb))

        assertEquals(listOf(1L, 8L), visible.map { it.id })
    }

    @Test
    fun `a hint leak hides only the leaking copy, not a later clean one`() {
        // Same word, type, prompt and answer; only the first copy's hint spells
        // out the answer. The leak is hidden, the later clean copy is shown, and
        // a third copy after it is still a duplicate.
        val leak = aiItem(13, "Die Firma ___ (verzichten) auf alte Zölle.", "verzichtete", hint = "verzichtete, Präteritum")
        val clean = aiItem(14, leak.prompt, leak.answer, hint = "Präteritum")
        val third = localItem(15, leak.prompt, leak.answer)

        val visible = PracticeVisibleItems.of(listOf(leak, clean, third), listOf(verb))

        assertEquals(listOf(14L), visible.map { it.id })
        assertEquals("Präteritum", visible.single().hint)
    }

    @Test
    fun `local fallbacks skip the AI filters`() {
        val fallback = localItem(6, "Die Firma ___ auf neue Zölle.", "verzichtet")

        assertEquals(listOf(fallback), PracticeVisibleItems.of(listOf(fallback), listOf(verb)))
    }

    @Test
    fun `a word whose cards are all hidden still gets a cloze fallback`() {
        // No context match, so the fallback is the meaning cue (#750), not a bare gap.
        val word = verb.copy(id = 43, context = "Der Satz nennt das Wort nicht.")
        val stored = listOf(
            localItem(10, "___", "verzichten", wordId = word.id),
            localItem(11, "___", "verzichten", wordId = word.id).copy(choices = listOf("verzichtenn", "verzichten")),
            aiItem(12, "Die Firma ___ auf neue Zölle.", "verzichtet", wordId = word.id),
        )

        val visible = PracticeVisibleItems.of(stored, listOf(word))

        assertEquals(1, visible.size)
        val card = visible.single()
        assertEquals(word.id, card.wordId)
        assertEquals(DrillType.CLOZE, card.type)
        assertEquals("etwas nicht tun oder nehmen → ___", card.prompt)
        assertEquals("verzichten", card.answer)
        assertNull(card.model)
        assertNull(card.hint)
        assertEquals(PracticeItem.Source.DIGEST, card.source)
        assertEquals(0L, card.id)
        // It is what the store's padding would have built today.
        assertEquals(PracticeContract.fallbacksFor(word, 1, PracticeItem.Source.DIGEST), visible)
    }

    @Test
    fun `a word without stored items gets no fallback, and unknown words keep their cards`() {
        // Review keeps the plain word for an empty pool; a deleted word's items
        // pass unfiltered, as before.
        val orphan = aiItem(13, "Die Firma ___ auf neue Zölle.", "verzichtet", wordId = 99)

        val visible = PracticeVisibleItems.of(listOf(orphan), listOf(verb))

        assertEquals(listOf(orphan), visible)
        assertTrue(PracticeVisibleItems.of(emptyList(), listOf(verb)).isEmpty())
    }
}
