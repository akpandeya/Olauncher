package app.olauncher.ui.german

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GermanSetupFlowTest {

    @Test
    fun `steps run welcome then choose then permission`() {
        assertEquals(GermanSetupStep.CHOOSE, GermanSetupStep.WELCOME.next())
        assertEquals(GermanSetupStep.PERMISSION, GermanSetupStep.CHOOSE.next())
        assertNull(GermanSetupStep.PERMISSION.next())
    }

    @Test
    fun `back goes one step and leaves the setup from the first`() {
        assertEquals(GermanSetupStep.CHOOSE, GermanSetupStep.PERMISSION.previous())
        assertEquals(GermanSetupStep.WELCOME, GermanSetupStep.CHOOSE.previous())
        assertNull(GermanSetupStep.WELCOME.previous())
    }

    @Test
    fun `progress bar has three segments and fills one more per step`() {
        assertEquals(3, GermanSetupStep.SEGMENTS)
        assertEquals(listOf(1, 2, 3), GermanSetupStep.entries.map { it.filledSegments })
    }

    @Test
    fun `only the offline dictionary is on by default`() {
        val defaults = GermanSetupChoices()
        assertTrue(defaults.offlineDictionary)
        assertFalse(defaults.lookupOnline)
        assertFalse(defaults.screenPick)
        assertFalse(defaults.germanNudges)
    }

    @Test
    fun `skipping the permission keeps screen picks off even when switched on in step 2`() {
        val result = GermanSetupFlow.result(
            GermanSetupChoices(offlineDictionary = true, lookupOnline = true, screenPick = true, germanNudges = false),
            openedAccessibilitySettings = false,
        )
        assertEquals(GermanSetupResult(offlineDictionary = true, lookupOnline = true, screenPick = false, germanUi = false), result)
    }

    @Test
    fun `opening accessibility settings in step 3 turns screen picks on`() {
        val result = GermanSetupFlow.result(
            GermanSetupChoices(offlineDictionary = false, lookupOnline = false, screenPick = false, germanNudges = true),
            openedAccessibilitySettings = true,
        )
        assertEquals(GermanSetupResult(offlineDictionary = false, lookupOnline = false, screenPick = true, germanUi = true), result)
    }
}
