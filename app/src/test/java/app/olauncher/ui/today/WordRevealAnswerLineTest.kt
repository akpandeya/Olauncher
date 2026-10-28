package app.olauncher.ui.today

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The reveal's meaning line when a practice prompt replaces the word (#603): the
 * item's answer leads, so the user can check themselves (review R1 #10).
 */
class WordRevealAnswerLineTest {

    @Test
    fun `the answer leads the meaning line`() {
        assertEquals("die Schule · school", revealMeaningWithAnswer("die Schule", "school"))
    }

    @Test
    fun `a blank meaning shows the answer alone`() {
        assertEquals("die Schule", revealMeaningWithAnswer("die Schule", ""))
    }

    @Test
    fun `without an override the meaning line is unchanged`() {
        assertEquals("school", revealMeaningWithAnswer(null, "school"))
        assertEquals("school", revealMeaningWithAnswer("", "school"))
        assertEquals("", revealMeaningWithAnswer(null, ""))
    }
}
