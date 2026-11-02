package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PracticeWordOrder.parse] (#804, part 1): the Context-free tile parser behind
 * the word_order filter and the task-02 tile rendering. Accepts contract-shaped
 * cards and rejects partial input before producing anything. Pure JVM — no
 * provider, no network.
 */
class PracticeWordOrderTest {

    private val prompt = "Ordne: Das / Personal / wächst / heute / an"
    private val answer = "Das Personal wächst heute an."
    private val choices = listOf(answer, "Das Personal heute an wächst")

    private fun cardOf(result: PracticeWordOrder.Result): PracticeWordOrder.Card =
        when (result) {
            is PracticeWordOrder.Result.Parsed -> result.card
            is PracticeWordOrder.Result.Rejected ->
                throw AssertionError("expected a parse, rejected: ${result.reason}")
        }

    private fun rejectionOf(result: PracticeWordOrder.Result): String =
        when (result) {
            is PracticeWordOrder.Result.Parsed ->
                throw AssertionError("expected a rejection, parsed: ${result.card}")
            is PracticeWordOrder.Result.Rejected -> result.reason
        }

    @Test
    fun `parse accepts a contract-shaped card and keeps the original tile spelling`() {
        val card = cardOf(PracticeWordOrder.parse(prompt, answer, choices))
        assertEquals(listOf("Das", "Personal", "wächst", "heute", "an"), card.tiles)
        assertEquals(listOf("Das", "Personal", "wächst", "heute", "an"), card.acceptedOrder)
        assertEquals(listOf("Das Personal heute an wächst"), card.wrongOrders)
    }

    @Test
    fun `parse tolerates case and punctuation between tiles and answer`() {
        val card = cardOf(
            PracticeWordOrder.parse(
                "Ordne: die / Arbeit / insistiert / heute",
                "Die Arbeit insistiert heute.",
                listOf("Die Arbeit insistiert heute.", "Arbeit die heute insistiert"),
            ),
        )
        assertEquals(listOf("die", "Arbeit", "insistiert", "heute"), card.tiles)
        assertEquals(1, card.wrongOrders.size)
    }

    @Test
    fun `parse treats a choice equal to the answer as the answer slot not a wrong order`() {
        // Same order in a different case/punctuation: the answer's slot, never a
        // distractor.
        val card = cardOf(PracticeWordOrder.parse(prompt, answer, listOf(answer, "das personal wächst heute an")))
        assertTrue(card.wrongOrders.isEmpty())
    }

    @Test
    fun `parse rejects a prompt without the Ordne prefix`() {
        val reason = rejectionOf(PracticeWordOrder.parse("Das / Personal / wächst / heute / an", answer, choices))
        assertTrue(reason.contains("Ordne"))
    }

    @Test
    fun `parse rejects a prompt without the tile separator`() {
        val reason = rejectionOf(PracticeWordOrder.parse("Ordne: Das Personal wächst heute an", answer, choices))
        assertTrue(reason.contains("Ordne"))
    }

    @Test
    fun `parse rejects fewer than three tiles`() {
        val reason = rejectionOf(PracticeWordOrder.parse("Ordne: Das / Personal", "Das Personal.", choices))
        assertTrue(reason.contains("at least 3"))
    }

    @Test
    fun `parse rejects a blank punctuation-only tile`() {
        val reason = rejectionOf(PracticeWordOrder.parse("Ordne: Das / Personal / . / wächst / an", answer, choices))
        assertTrue(reason.contains("blank"))
    }

    @Test
    fun `parse rejects tiles that do not spell the answer's words`() {
        // A merged tile ("6.000 auf") breaks the multiset even though a naive
        // count could look right.
        val merged = PracticeWordOrder.parse(
            "Ordne: Das / Personal / 6.000 auf / wächst / an",
            "Das Personal wächst auf 6.000 an.",
            choices,
        )
        assertTrue(rejectionOf(merged).contains("do not spell"))

        // A dropped tile fails the same completeness check.
        val dropped = PracticeWordOrder.parse(
            "Ordne: Das / Personal / wächst / an",
            "Das Personal wächst heute an.",
            choices,
        )
        assertTrue(rejectionOf(dropped).contains("do not spell"))
    }
}
