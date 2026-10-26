package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Test

class DrillSummaryLogicTest {

    private fun item(
        index: Int,
        result: DrillResult,
        prompt: String = "Prompt $index",
        answer: String = "Answer $index",
        hint: String? = null,
        learnerAnswer: String? = null,
    ) = DrillSummaryItem(
        id = DrillItemId(DrillType.PREPOSITIONS, "item_$index"),
        prompt = prompt,
        answer = answer,
        hint = hint,
        result = result,
        learnerAnswer = learnerAnswer,
        roundIndex = index,
    )

    @Test
    fun `sort places wrong first, then almost, then right`() {
        val items = listOf(
            item(0, DrillResult.RIGHT),
            item(1, DrillResult.ALMOST),
            item(2, DrillResult.WRONG),
            item(3, DrillResult.RIGHT),
            item(4, DrillResult.WRONG),
            item(5, DrillResult.ALMOST),
        )

        val sorted = DrillSummaryLogic.sort(items)

        val expectedResults = listOf(
            DrillResult.WRONG,
            DrillResult.WRONG,
            DrillResult.ALMOST,
            DrillResult.ALMOST,
            DrillResult.RIGHT,
            DrillResult.RIGHT,
        )
        assertEquals(expectedResults, sorted.map { it.result })
    }

    @Test
    fun `sort preserves round order for ties`() {
        val items = listOf(
            item(0, DrillResult.RIGHT),
            item(1, DrillResult.WRONG),
            item(2, DrillResult.ALMOST),
            item(3, DrillResult.WRONG),
            item(4, DrillResult.RIGHT),
            item(5, DrillResult.ALMOST),
            item(6, DrillResult.WRONG),
        )

        val sorted = DrillSummaryLogic.sort(items)

        // Wrong items: round indices 1, 3, 6
        assertEquals(listOf(1, 3, 6), sorted.filter { it.result == DrillResult.WRONG }.map { it.roundIndex })
        // Almost items: round indices 2, 5
        assertEquals(listOf(2, 5), sorted.filter { it.result == DrillResult.ALMOST }.map { it.roundIndex })
        // Right items: round indices 0, 4
        assertEquals(listOf(0, 4), sorted.filter { it.result == DrillResult.RIGHT }.map { it.roundIndex })
    }

    @Test
    fun `all ties keep round order`() {
        val items = listOf(
            item(0, DrillResult.RIGHT),
            item(1, DrillResult.RIGHT),
            item(2, DrillResult.RIGHT),
        )
        val sorted = DrillSummaryLogic.sort(items)
        assertEquals(listOf(0, 1, 2), sorted.map { it.roundIndex })
    }

    @Test
    fun `formatPrompt includes learner answer for wrong and almost`() {
        assertEquals(
            "Ich gehe durch ___ Park. (dem)",
            DrillSummaryLogic.formatPrompt("Ich gehe durch ___ Park.", DrillResult.WRONG, "dem"),
        )
        assertEquals(
            "du machst (machstt)",
            DrillSummaryLogic.formatPrompt("du machst", DrillResult.ALMOST, "machstt"),
        )
        // Right items do not display learner answer
        assertEquals(
            "Ich gehe durch ___ Park.",
            DrillSummaryLogic.formatPrompt("Ich gehe durch ___ Park.", DrillResult.RIGHT, "den"),
        )
        // Null or blank learner answer leaves prompt unchanged
        assertEquals(
            "Ich gehe durch ___ Park.",
            DrillSummaryLogic.formatPrompt("Ich gehe durch ___ Park.", DrillResult.WRONG, null),
        )
        assertEquals(
            "Ich gehe durch ___ Park.",
            DrillSummaryLogic.formatPrompt("Ich gehe durch ___ Park.", DrillResult.WRONG, "   "),
        )
    }

    @Test
    fun `formatPrompt shows the answer and the near-miss note on an almost row`() {
        assertEquals(
            "___ Sonne ist hell (diez → die · near miss, one retry used)",
            DrillSummaryLogic.formatPrompt(
                "___ Sonne ist hell",
                DrillResult.ALMOST,
                "diez",
                answer = "die",
                almostNote = "near miss, one retry used",
            ),
        )
        // Without note or answer it falls back to the plain learner answer.
        assertEquals(
            "du machst (machstt)",
            DrillSummaryLogic.formatPrompt("du machst", DrillResult.ALMOST, "machstt", answer = null, almostNote = null),
        )
        assertEquals(
            "du machst (machstt → macht)",
            DrillSummaryLogic.formatPrompt("du machst", DrillResult.ALMOST, "machstt", answer = "macht"),
        )
        // Wrong rows ignore the almost fields.
        assertEquals(
            "Ich gehe durch ___ Park. (dem)",
            DrillSummaryLogic.formatPrompt(
                "Ich gehe durch ___ Park.",
                DrillResult.WRONG,
                "dem",
                answer = "den",
                almostNote = "near miss, one retry used",
            ),
        )
    }

    @Test
    fun `formatCorrection combines answer and hint without duplication`() {
        // Prompt differs from answer: answer shown, hint appended when distinct.
        assertEquals("den · durch + Akkusativ", DrillSummaryLogic.formatCorrection("Prompt 0", "den", "durch + Akkusativ"))
        assertEquals("Akkusativ", DrillSummaryLogic.formatCorrection("Prompt 1", "Akkusativ", "Akkusativ"))
        assertEquals("Akkusativ", DrillSummaryLogic.formatCorrection("Prompt 2", "Akkusativ", "akkusativ"))
        assertEquals("den", DrillSummaryLogic.formatCorrection("Prompt 3", "den", null))
        assertEquals("den", DrillSummaryLogic.formatCorrection("Prompt 4", "den", ""))
        assertEquals("den", DrillSummaryLogic.formatCorrection("Prompt 5", "den", "   "))
    }

    @Test
    fun `formatCorrection omits answer when it repeats the word-order prompt`() {
        // Word-order rows show the full sentence as the prompt; repeating it as the
        // correction would be redundant, so only the hint is shown.
        val sentence = "Er bleibt im Bett weil er krank ist"
        assertEquals("", DrillSummaryLogic.formatCorrection(sentence, sentence, null))
        assertEquals("", DrillSummaryLogic.formatCorrection(sentence, sentence, "   "))
        assertEquals(
            "Nebensatz: Konjugiertes Verb am Satzende nach weil",
            DrillSummaryLogic.formatCorrection(
                "$sentence (Er bleibt weil krank er im Bett ist)",
                sentence,
                "Nebensatz: Konjugiertes Verb am Satzende nach weil",
            ),
        )
        // casing differences still count as the same sentence
        assertEquals("", DrillSummaryLogic.formatCorrection("Ich gehe ins Kino", "ich gehe ins kino", null))
    }

    @Test
    fun `counts matches grouped sort output for the summary header`() {
        val items = listOf(
            item(0, DrillResult.RIGHT),
            item(1, DrillResult.WRONG),
            item(2, DrillResult.ALMOST),
            item(3, DrillResult.WRONG),
            item(4, DrillResult.RIGHT),
            item(5, DrillResult.ALMOST),
        )

        val expected = DrillSummaryCounts(right = 2, almost = 2, wrong = 2)
        assertEquals(expected, DrillSummaryLogic.counts(items))
        val sorted = DrillSummaryLogic.sort(items)
        assertEquals(
            expected,
            DrillSummaryCounts(
                right = sorted.count { it.result == DrillResult.RIGHT },
                almost = sorted.count { it.result == DrillResult.ALMOST },
                wrong = sorted.count { it.result == DrillResult.WRONG },
            ),
        )
    }

    @Test
    fun `counts handles an empty summary`() {
        assertEquals(DrillSummaryCounts(right = 0, almost = 0, wrong = 0), DrillSummaryLogic.counts(emptyList()))
    }
}
