package app.olauncher.ui.today.cards

import app.olauncher.data.store.DrillLastRound
import app.olauncher.data.store.DrillScores
import app.olauncher.data.store.DrillTables
import app.olauncher.data.store.DrillType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillsCardTest {

    private val types = DrillTables.available()

    private fun score(asked: Int, right: Int) = DrillScores.Score(asked = asked, firstTryRight = right)

    // --- Availability -----------------------------------------------------------

    @Test
    fun availableOnlyWhenGermanIsOn() {
        assertTrue(DrillsCardLogic.isAvailable(germanEnabled = true))
        assertFalse(DrillsCardLogic.isAvailable(germanEnabled = false))
    }

    // --- Suggested type ---------------------------------------------------------

    @Test
    fun suggestedTypeIsWeakestScoredType() {
        val scores = mapOf(
            "prepositions" to score(asked = 10, right = 9),
            "pronouns" to score(asked = 10, right = 3),
        )
        assertEquals(DrillType.PRONOUNS, DrillsCardLogic.suggestedType(types, scores, lastTypeId = null))
    }

    @Test
    fun suggestedTypeFallsBackToLastTypeWhenNothingScored() {
        assertEquals(
            DrillType.WORD_ORDER,
            DrillsCardLogic.suggestedType(types, emptyMap(), lastTypeId = "word_order"),
        )
    }

    @Test
    fun suggestedTypeWithNoHistoryIsFirstAvailableType() {
        assertEquals(
            DrillType.PREPOSITIONS,
            DrillsCardLogic.suggestedType(types, emptyMap(), lastTypeId = null),
        )
    }

    // --- Line choice ------------------------------------------------------------

    @Test
    fun lineIsSuggestedWhenNoRoundPlayed() {
        val line = DrillsCardLogic.line(suggestedTypeId = "prepositions", lastRound = null)
        assertEquals(DrillsCardLine.Suggested("prepositions"), line)
    }

    @Test
    fun lineIsLastRoundOnceARoundWasPlayed() {
        val lastRound = DrillLastRound(typeId = "articles", right = 7, total = 10)
        val line = DrillsCardLogic.line(suggestedTypeId = "prepositions", lastRound = lastRound)
        assertEquals(DrillsCardLine.LastRound("articles", right = 7, total = 10), line)
    }

    @Test
    fun lineIsNullWithoutSuggestionOrRound() {
        assertNull(DrillsCardLogic.line(suggestedTypeId = null, lastRound = null))
    }

    // --- Line text --------------------------------------------------------------

    private fun text(line: DrillsCardLine?, typeName: String) = DrillsCardLogic.lineText(
        suggestedTemplate = "Suggested: %1\$s · Start %2\$d",
        lastRoundTemplate = "Last round: %1\$s · %2\$d of %3\$d",
        typeName = typeName,
        line = line,
    )

    @Test
    fun suggestedLineTextNamesTheTypeAndTheOffer() {
        assertEquals(
            "Suggested: Prepositions · Start 5",
            text(DrillsCardLine.Suggested("prepositions"), "Prepositions"),
        )
    }

    @Test
    fun lastRoundLineTextNamesTheTypeAndTheResult() {
        assertEquals(
            "Last round: Articles · 7 of 10",
            text(DrillsCardLine.LastRound("articles", right = 7, total = 10), "Articles"),
        )
    }

    @Test
    fun missingLineHasNoText() {
        assertNull(text(null, "Prepositions"))
    }
}
