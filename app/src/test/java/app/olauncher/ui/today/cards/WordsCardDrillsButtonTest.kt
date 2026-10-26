package app.olauncher.ui.today.cards

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordsCardDrillsButtonTest {

    @Test
    fun drillsButtonShowsWhenGermanIsOn() {
        assertTrue(WordsCardLogic.showDrillsButton(germanEnabled = true))
    }

    @Test
    fun drillsButtonHidesWhenGermanIsOff() {
        assertFalse(WordsCardLogic.showDrillsButton(germanEnabled = false))
    }
}
