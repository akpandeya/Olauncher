package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The answer cell of the help card's forms table is highlighted by position, not by
 * text (#723): the genitive *des* fills both the masculine and the neuter column of the
 * shared preposition table, and only one of them is this item's cell. A text match
 * lights every column that happens to read the same and quietly approves the answer
 * for either gender.
 */
class DrillHelpCardCellTest {

    /** The shared prep-fixed table: Akk/Dat/Gen rows, m/f/n/pl columns. */
    private val prepTable = DrillHelpTable(
        headers = listOf("", "m", "f", "n", "pl"),
        rows = listOf(
            DrillHelpRow("Akk", listOf("den", "die", "das", "die")),
            DrillHelpRow("Dat", listOf("dem", "der", "dem", "den")),
            DrillHelpRow("Gen", listOf("des", "der", "des", "der")),
        ),
    )

    @Test
    fun answerRepeatedAcrossColumnsLightsNothingWithoutAnAuthoredCell() {
        // "des" sits in both the masculine and the neuter genitive cell; without the
        // item's position no single cell is its own, so nothing is highlighted.
        assertNull(DrillHelpCard.answerCellPosition(prepTable, "des", cell = null))
    }

    @Test
    fun authoredCellHighlightsOnlyItsOwnPosition() {
        // Wetter is neuter: the item's cell is Gen/n, never the masculine des.
        assertEquals(2 to 2, DrillHelpCard.answerCellPosition(prepTable, "des", DrillHelpCard.Cell("Gen", "n")))
        // A masculine item of the same rule (Trotz des Regens) lights the other des.
        assertEquals(2 to 0, DrillHelpCard.answerCellPosition(prepTable, "des", DrillHelpCard.Cell("Gen", "m")))
    }

    @Test
    fun everyRepeatedFormOfTheTableIsAmbiguousWithoutAnAuthoredCell() {
        // die, der, den and dem repeat too — none may highlight from text alone.
        assertNull(DrillHelpCard.answerCellPosition(prepTable, "die", cell = null))
        assertNull(DrillHelpCard.answerCellPosition(prepTable, "der", cell = null))
        assertNull(DrillHelpCard.answerCellPosition(prepTable, "den", cell = null))
        assertNull(DrillHelpCard.answerCellPosition(prepTable, "dem", cell = null))
    }

    @Test
    fun uniqueTextMatchStillHighlightsWithoutAnAuthoredCell() {
        val single = DrillHelpTable(
            headers = listOf("", "m", "f"),
            rows = listOf(DrillHelpRow("Nom", listOf("der", "die"))),
        )
        assertEquals(0 to 0, DrillHelpCard.answerCellPosition(single, "der", cell = null))
    }

    @Test
    fun unresolvedAuthoredCellFallsBackToTheUniqueTextMatch() {
        val single = DrillHelpTable(
            headers = listOf("", "m", "f"),
            rows = listOf(DrillHelpRow("Nom", listOf("der", "die"))),
        )
        // A drifted label must not light the wrong position, nor nothing at all.
        assertEquals(0 to 0, DrillHelpCard.answerCellPosition(single, "der", DrillHelpCard.Cell("Akk", "q")))
    }

    @Test
    fun unresolvedAuthoredCellWithAmbiguousAnswerLightsNothing() {
        // An authored cell that no longer resolves must not revive the over-lit row.
        assertNull(DrillHelpCard.answerCellPosition(prepTable, "des", DrillHelpCard.Cell("Akk", "q")))
    }

    @Test
    fun matchIgnoresCaseAndSurroundingSpace() {
        assertEquals(
            2 to 2,
            DrillHelpCard.answerCellPosition(prepTable, " DES ", DrillHelpCard.Cell(" gen ", " n ")),
        )
    }

    @Test
    fun noAnswerLightsNothing() {
        // The "Why?" state binds with no answer: the card names nothing.
        assertNull(DrillHelpCard.answerCellPosition(prepTable, null, DrillHelpCard.Cell("Gen", "n")))
        assertNull(DrillHelpCard.answerCellPosition(prepTable, " ", DrillHelpCard.Cell("Gen", "n")))
    }

    @Test
    fun flashcardStateCarriesTheItemsOwnCell() {
        // The issue's item: Wegen ___ Wetters — Wetter is neuter, so Gen/n.
        val wegen = DrillPrepositions.items().first { it.id.key == "prep:wegen:1" }
        assertEquals("des", wegen.answer)
        assertEquals(DrillHelpCard.Cell("Gen", "n"), wegen.helpCell)
        val question = DrillQuizQuestion(wegen, DrillFormat.FLASHCARD, wegen.choices)
        val state = DrillHelpCard.state(question, whyOpen = false, answeredWrong = false)!!
        assertEquals(wegen.helpCell, state.answerCell)

        // A masculine item of the same rule carries the other cell — the two must
        // never collapse into one highlight.
        val trotz = DrillPrepositions.items().first { it.id.key == "prep:trotz:1" }
        assertEquals(DrillHelpCard.Cell("Gen", "m"), trotz.helpCell)
        assertNotEquals(wegen.helpCell, trotz.helpCell)
    }

    @Test
    fun caseEndingItemsCarryTheirPositionInTheCaseTables() {
        // The case-* tables put the cases in the columns and the genders in the rows,
        // with masculine and neuter sharing one row. The prep-fixed-gen items of the
        // same type use the shared preposition table instead: cases in the rows.
        val konzert = DrillCaseEndings.items().first { it.id.key == "case:gen_n_waehrend_konzerts" }
        assertEquals("prep-fixed-gen", konzert.ruleId)
        assertEquals("des", konzert.answer)
        assertEquals(DrillHelpCard.Cell("Gen", "n"), konzert.helpCell)

        // A feminine dative item lands in its own row and the dative column.
        val mutter = DrillCaseEndings.items().first { it.id.key == "case:dat_f_schmecken_mutter" }
        assertEquals("case-verb-dat", mutter.ruleId)
        assertEquals("der", mutter.answer)
        assertEquals(DrillHelpCard.Cell("f", "Wem? → Dat"), mutter.helpCell)
    }

    @Test
    fun articleItemsCarryTheirGenderColumnInTheSharedTable() {
        // The article table is one Nom row of der/die/das/die: "die" sits in both
        // the feminine and the plural column. Every table noun is singular (a
        // plural noun demonstrates no gender rule), so a die item's cell is always
        // the feminine one (#723).
        val zeitung = DrillArticles.items().first { it.id.key == "art:zeitung" }
        assertEquals("die", zeitung.answer)
        assertEquals(DrillHelpCard.Cell("Nom", "f"), zeitung.helpCell)

        // der and das items land in their own columns — a shared constant cell
        // cannot pass.
        val mann = DrillArticles.items().first { it.id.key == "art:mann" }
        assertEquals("der", mann.answer)
        assertEquals(DrillHelpCard.Cell("Nom", "m"), mann.helpCell)
        val dasItem = DrillArticles.items().first { it.answer == "das" && it.helpCell != null }
        assertEquals(DrillHelpCard.Cell("Nom", "n"), dasItem.helpCell)
    }

    @Test
    fun bareGenderHeadersProduceNoChipsRow() {
        // m · f · n · pl detached from their cells only repeat the table's own
        // header row rendered below; the chips row hides for them (#723).
        assertEquals(emptyList<String>(), DrillHelpCard.patternChips(prepTable))
    }

    @Test
    fun questionWordHeadersStayAsChips() {
        val caseTable = DrillHelpTable(
            headers = listOf("", "Wer? → Nom", "Wem? → Dat"),
            rows = listOf(DrillHelpRow("m · n", listOf("der · das", "dem"))),
        )
        assertEquals(listOf("Wer? → Nom", "Wem? → Dat"), DrillHelpCard.patternChips(caseTable))
    }

    @Test
    fun blankHeadersAreDroppedBeforeTheBareCheck() {
        val sparse = DrillHelpTable(
            headers = listOf("", "", "Wer? → Nom"),
            rows = listOf(DrillHelpRow("Nom", listOf("der", "die"))),
        )
        assertEquals(listOf("Wer? → Nom"), DrillHelpCard.patternChips(sparse))
    }

    @Test
    fun realTablesSplitTheirChipsAsExpected() {
        // Content drift check: the shared preposition table loses its chips row,
        // the case table keeps its question words.
        assertEquals(
            emptyList<String>(),
            DrillHelpCard.patternChips(DrillHelpContent.rule("prep-fixed-gen")!!.table!!),
        )
        assertEquals(
            listOf("Wer? → Nom", "Wen? → Akk", "Wem? → Dat", "Wessen? → Gen"),
            DrillHelpCard.patternChips(DrillHelpContent.rule("case-verb-dat")!!.table!!),
        )
    }

    private fun assertNotEquals(a: Any?, b: Any?) {
        org.junit.Assert.assertNotEquals(a, b)
    }
}
