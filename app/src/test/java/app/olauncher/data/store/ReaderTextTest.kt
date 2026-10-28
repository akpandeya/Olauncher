package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTextTest {

    private val headline = "Nach der Elternzeit Wenn die Rückkehr in den Job schwierig wird"
    private val lead =
        "Viele Mütter und Väter wollen nach der Elternzeit wieder arbeiten, häufig in Teilzeit. " +
            "Doch nicht immer finden sie mit ihren Arbeitgebern eine Lösung. Betroffene berichten " +
            "von Benachteiligungen, Fachleute fordern mehr Schutz."
    private val julie =
        "Julie K. ist 22 Jahre alt und Mutter eines kleinen Sohnes. Vor seiner Geburt hat sie eine " +
            "Ausbildung zur Tiermedizinischen Fachangestellten in einer Tierklinik begonnen. Nach " +
            "der Elternzeit will sie ihre Ausbildung unbedingt fortsetzen, allerdings in Teilzeit."

    @Test
    fun realDumpKeepsKickerHeadlineAndBodyWhole() {
        val blocks = ReaderText.blocks(UiAutomatorFixture.loadTagesschau())

        // Noise from 10.2 never becomes a block.
        val texts = blocks.map { it.text }
        for (clutter in listOf("Player: video", "Teilen Teilen", "Facebook", "(80)")) {
            assertTrue("dropped: $clutter", clutter !in texts)
        }

        // The headline block keeps kicker and headline text intact — punctuation and
        // stopwords untouched, nothing tokenized away.
        val headlineBlock = blocks.first { it.kind == ReaderText.Kind.HEADLINE }
        assertTrue(headlineBlock.text.startsWith("Nach der Elternzeit"))
        assertTrue(headlineBlock.text.contains("Wenn die Rückkehr in den Job schwierig wird"))

        // The body paragraphs are present as body blocks.
        val bodyTexts = blocks.filter { it.kind == ReaderText.Kind.BODY }.map { it.text }
        assertTrue(bodyTexts.any { it == lead })
        assertTrue(bodyTexts.any { it.startsWith("Julie K. ist 22 Jahre alt") })
    }

    @Test
    fun separateKickerAndHeadlineNodesClassifyFromFirstLongBody() {
        // two_column.xml: "Nach der Elternzeit" and "Wenn die Rückkehr…" are their own nodes
        // above the first long body paragraph.
        val blocks = ReaderText.blocks(UiAutomatorFixture.loadTwoColumn())

        assertEquals(ReaderText.Kind.KICKER, blocks[0].kind)
        assertEquals("Nach der Elternzeit", blocks[0].text)
        assertEquals(ReaderText.Kind.HEADLINE, blocks[1].kind)
        assertEquals("Wenn die Rückkehr in den Job schwierig wird", blocks[1].text)
        assertTrue(blocks.drop(2).all { it.kind == ReaderText.Kind.BODY })
    }

    @Test
    fun textIsKeptVerbatimSoCharRangesStayValid() {
        val blocks = ReaderText.blocks(UiAutomatorFixture.loadTagesschau())
        val nodes = ScreenWords.build(UiAutomatorFixture.loadTagesschau()).nodes

        // Every block maps 1:1 to a kept node and carries the node's exact text —
        // word charRange indexes into it must not shift.
        assertEquals(nodes.map { it.id }, blocks.map { it.nodeId })
        assertEquals(nodes.map { it.text }, blocks.map { it.text })
    }

    @Test
    fun longFirstNodeMeansAllBody() {
        val blocks = ReaderText.blocks(
            listOf(
                ScreenNode(0, -1, "A".repeat(150), null, null, null, null),
                ScreenNode(1, -1, "B".repeat(150), null, null, null, null),
            )
        )
        assertTrue(blocks.all { it.kind == ReaderText.Kind.BODY })
    }

    @Test
    fun noLongNodeMeansAllBody() {
        val blocks = ReaderText.blocks(
            listOf(
                ScreenNode(0, -1, "Nach der Elternzeit", null, null, null, null),
                ScreenNode(1, -1, "Wenn die Rückkehr in den Job schwierig wird", null, null, null, null),
            )
        )
        assertTrue(blocks.all { it.kind == ReaderText.Kind.BODY })
    }

    @Test
    fun singleShortNodeIsBody() {
        val blocks = ReaderText.blocks(
            listOf(ScreenNode(0, -1, "Nur ein Satz.", null, null, null, null))
        )
        assertEquals(listOf(ReaderText.Kind.BODY), blocks.map { it.kind })
    }

    @Test
    fun longHeadlineAboveBodyStaysBody() {
        // The line above the body must be shorter than the body paragraph to count as
        // the headline; a long prose node above it is just more body.
        val blocks = ReaderText.blocks(
            listOf(
                ScreenNode(0, -1, "C".repeat(200), null, null, null, null),
                ScreenNode(1, -1, "B".repeat(150), null, null, null, null),
            )
        )
        assertTrue(blocks.all { it.kind == ReaderText.Kind.BODY })
    }
}
