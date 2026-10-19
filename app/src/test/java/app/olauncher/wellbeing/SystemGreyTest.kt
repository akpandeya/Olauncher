package app.olauncher.wellbeing

import app.olauncher.wellbeing.SystemGrey.Daltonizer
import app.olauncher.wellbeing.SystemGrey.Writes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemGreyTest {
    private val deuteranomaly = Daltonizer(enabled = 1, mode = 12)
    private val off = Daltonizer(enabled = 0, mode = -1)

    @Test
    fun startSavesCurrentAndWritesGrey() {
        val w = SystemGrey.plan(saved = null, active = true, current = deuteranomaly)
        assertEquals(Writes(toWrite = Daltonizer(1, 0), newSaved = deuteranomaly), w)
    }

    @Test
    fun startFromOffSavesOff() {
        val w = SystemGrey.plan(null, true, off)
        assertEquals(Writes(SystemGrey.GREY, off), w)
    }

    @Test
    fun userAlreadyHadGreyOnIsNeverTouched() {
        val w = SystemGrey.plan(null, true, Daltonizer(1, 0))
        assertEquals(Writes(null, null), w)
        // At the end nothing is saved, so nothing is restored: grey stays on.
        assertEquals(Writes(null, null), SystemGrey.plan(w.newSaved, false, Daltonizer(1, 0)))
    }

    @Test
    fun enabledButOtherModeIsNotGrey() {
        assertFalse(Daltonizer(1, 11).isGrey)
        assertFalse(Daltonizer(0, 0).isGrey)
        assertTrue(Daltonizer(1, 0).isGrey)
    }

    @Test
    fun endRestoresSavedAndClearsIt() {
        val w = SystemGrey.plan(saved = deuteranomaly, active = false, current = SystemGrey.GREY)
        assertEquals(Writes(toWrite = deuteranomaly, newSaved = null), w)
    }

    @Test
    fun repeatedStartKeepsOriginalSave() {
        val w = SystemGrey.plan(saved = deuteranomaly, active = true, current = SystemGrey.GREY)
        assertEquals(Writes(null, deuteranomaly), w)
    }

    @Test
    fun idleWritesNothing() {
        assertEquals(Writes(null, null), SystemGrey.plan(null, false, off))
    }
}
