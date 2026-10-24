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
    fun `opening accessibility settings with the step 2 switch on turns screen picks on`() {
        val result = GermanSetupFlow.result(
            GermanSetupChoices(offlineDictionary = false, lookupOnline = false, screenPick = true, germanNudges = true),
            openedAccessibilitySettings = true,
        )
        assertEquals(GermanSetupResult(offlineDictionary = false, lookupOnline = false, screenPick = true, germanUi = true), result)
    }

    @Test
    fun `screen picks stay off when the step 2 switch is off even if accessibility was opened`() {
        val result = GermanSetupFlow.result(
            GermanSetupChoices(offlineDictionary = true, lookupOnline = false, screenPick = false, germanNudges = false),
            openedAccessibilitySettings = true,
        )
        assertFalse(result.screenPick)
    }

    @Test
    fun `an earlier screen pick grant survives running the setup again and skipping the permission`() {
        val result = GermanSetupFlow.result(GermanSetupChoices(screenPick = true), openedAccessibilitySettings = false, alreadyOn = true)
        assertTrue(result.screenPick)
        val switchedOff = GermanSetupFlow.result(GermanSetupChoices(screenPick = false), openedAccessibilitySettings = false, alreadyOn = true)
        assertFalse(switchedOff.screenPick)
    }

    @Test
    fun `permission step is shown only when screen picks are switched on`() {
        assertNull(GermanSetupFlow.next(GermanSetupStep.CHOOSE, GermanSetupChoices(screenPick = false)))
        assertEquals(GermanSetupStep.PERMISSION, GermanSetupFlow.next(GermanSetupStep.CHOOSE, GermanSetupChoices(screenPick = true)))
        assertEquals(GermanSetupStep.CHOOSE, GermanSetupFlow.next(GermanSetupStep.WELCOME, GermanSetupChoices()))
        assertNull(GermanSetupFlow.next(GermanSetupStep.PERMISSION, GermanSetupChoices(screenPick = true)))
    }

    @Test
    fun `first-time setup starts from the defaults`() {
        val choices = GermanSetupFlow.initialChoices(
            previouslySetUp = false, offlineDictionary = false, lookupOnline = true, screenPick = true, germanUi = true,
        )
        assertEquals(GermanSetupChoices(), choices)
    }

    @Test
    fun `repeat setup starts from the stored settings`() {
        val choices = GermanSetupFlow.initialChoices(
            previouslySetUp = true, offlineDictionary = false, lookupOnline = true, screenPick = true, germanUi = true,
        )
        assertEquals(GermanSetupChoices(offlineDictionary = false, lookupOnline = true, screenPick = true, germanNudges = true), choices)
    }
}
