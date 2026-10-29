package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureSaveTest {

    /** A boxless word on [nodeId] starting at [start]; the box is irrelevant to the plan. */
    private fun word(text: String, nodeId: Int, start: Int) =
        ScreenWord(text, null, nodeId, start until start + text.length)

    /** Two nodes with distinct, non-default paragraphs: swapped wiring cannot pass. */
    private val paragraphs = mapOf(
        11 to "Die Regierung.",
        22 to "Der Baum.",
    )

    private val paragraphOf: (ScreenWord) -> String = { paragraphs[it.nodeId] ?: "" }

    @Test
    fun planStripsEachTokenAndCutsItFromItsOwnParagraph() {
        val regierung = word("Regierung.", nodeId = 11, start = 4)
        val baum = word("Baum.", nodeId = 22, start = 4)

        val plan = CaptureSave.plan(
            listOf(CapturePick.Word(regierung), CapturePick.Word(baum)),
            paragraphOf,
        )

        assertEquals(2, plan.size)
        assertEquals("Regierung", plan[0].text)
        assertEquals("Die Regierung.", plan[0].context)
        assertEquals(listOf(regierung), plan[0].words)
        assertEquals("Baum", plan[1].text)
        assertEquals("Der Baum.", plan[1].context)
        assertEquals(listOf(baum), plan[1].words)
    }

    @Test
    fun planFollowsPendingOrderNotParagraphOrder() {
        val regierung = word("Regierung.", nodeId = 11, start = 4)
        val baum = word("Baum.", nodeId = 22, start = 4)

        val plan = CaptureSave.plan(
            listOf(CapturePick.Word(baum), CapturePick.Word(regierung)),
            paragraphOf,
        )

        assertEquals(listOf("Baum", "Regierung"), plan.map { it.text })
        assertEquals(listOf("Der Baum.", "Die Regierung."), plan.map { it.context })
    }

    @Test
    fun planOfNoPicksIsEmpty() {
        assertEquals(emptyList<CaptureSave.SaveItem>(), CaptureSave.plan(emptyList(), paragraphOf))
    }

    @Test
    fun splitVerbPickGivesOneItemSpanningVerbToParticle() {
        val paragraph = "Ich nehme das Paket morgen auf."
        val verbStart = paragraph.indexOf("nehme")
        val verb = ScreenWord("nehme", null, 33, verbStart until verbStart + "nehme".length)
        val particleStart = paragraph.indexOf("auf.")
        val particle = ScreenWord("auf.", null, 33, particleStart until particleStart + "auf.".length)

        val plan = CaptureSave.plan(
            listOf(CapturePick.SplitVerb(verb, particle, "aufnehmen")),
            { paragraph },
        )

        assertEquals(1, plan.size)
        assertEquals("aufnehmen", plan[0].text)
        assertEquals("Ich nehme das Paket morgen auf.", plan[0].context)
        assertEquals(listOf(verb, particle), plan[0].words)
    }
}
