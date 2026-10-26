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
    fun `formatCorrection combines answer and hint without duplication`() {
        assertEquals("den · durch + Akkusativ", DrillSummaryLogic.formatCorrection("den", "durch + Akkusativ"))
        assertEquals("Akkusativ", DrillSummaryLogic.formatCorrection("Akkusativ", "Akkusativ"))
        assertEquals("Akkusativ", DrillSummaryLogic.formatCorrection("Akkusativ", "akkusativ"))
        assertEquals("den", DrillSummaryLogic.formatCorrection("den", null))
        assertEquals("den", DrillSummaryLogic.formatCorrection("den", ""))
        assertEquals("den", DrillSummaryLogic.formatCorrection("den", "   "))
    }
}
