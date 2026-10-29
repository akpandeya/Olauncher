package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Test

class SavedMarksTest {

    private fun word(text: String) =
        ScreenWord(text, null, nodeId = 0, charRange = 0 until text.length)

    @Test
    fun `punctuated word matches its saved token`() {
        // Saves store the stripped token ("Regierung"); the screen word keeps its raw
        // text ("Regierung.") — the key must still be the token, so the dot is drawn.
        assertEquals("regierung", SavedMarks.keyOf(word("Regierung.")))
        assertEquals("regierung", SavedMarks.keyOf(word("„Regierung“")))
        assertEquals("regierung", SavedMarks.keyOf(word("Regierung, die")))
        assertEquals("die", SavedMarks.keyOf(word("die,")))
    }

    @Test
    fun `plain word keys to its lowercased text`() {
        assertEquals("regierung", SavedMarks.keyOf(word("Regierung")))
    }

    @Test
    fun `word without tokens falls back to the raw text`() {
        // tokenize drops tokens without letters or digits — nothing to strip here.
        assertEquals("—", SavedMarks.keyOf(word("—")))
    }

    @Test
    fun `a plain word pick keys like its screen word`() {
        val pick = CapturePick.Word(word("Regierung."))
        assertEquals("regierung", SavedMarks.keyOf(pick))
        assertEquals("Regierung", SavedMarks.tokenOf(pick))
    }

    @Test
    fun `a split verb keys to its lowercased lemma`() {
        val pick = CapturePick.SplitVerb(word("nehme"), word("auf."), "Aufnehmen")
        assertEquals("aufnehmen", SavedMarks.keyOf(pick))
        assertEquals("Aufnehmen", SavedMarks.tokenOf(pick))
    }
}
