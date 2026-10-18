package app.olauncher.helper

import app.olauncher.data.Prefs
import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryFormattingTest {

    private val date = "Sat, 18 Oct"

    @Test
    fun `auto mode shows battery when status bar is hidden`() {
        val result = BatteryFormatting.dateLine(
            date = date,
            percent = 76,
            charging = false,
            mode = Prefs.BATTERY_MODE_AUTO,
            statusBarShown = false
        )
        assertEquals("Sat, 18 Oct, 76%", result)
    }

    @Test
    fun `auto mode appends charging symbol when charging and status bar hidden`() {
        val result = BatteryFormatting.dateLine(
            date = date,
            percent = 76,
            charging = true,
            mode = Prefs.BATTERY_MODE_AUTO,
            statusBarShown = false
        )
        assertEquals("Sat, 18 Oct, 76% \u26A1\uFE0E", result)
    }

    @Test
    fun `auto mode hides battery when status bar is shown`() {
        val notCharging = BatteryFormatting.dateLine(
            date = date,
            percent = 76,
            charging = false,
            mode = Prefs.BATTERY_MODE_AUTO,
            statusBarShown = true
        )
        assertEquals(date, notCharging)

        val charging = BatteryFormatting.dateLine(
            date = date,
            percent = 76,
            charging = true,
            mode = Prefs.BATTERY_MODE_AUTO,
            statusBarShown = true
        )
        assertEquals(date, charging)
    }

    @Test
    fun `always mode shows battery even when status bar is shown`() {
        val notCharging = BatteryFormatting.dateLine(
            date = date,
            percent = 76,
            charging = false,
            mode = Prefs.BATTERY_MODE_ALWAYS,
            statusBarShown = true
        )
        assertEquals("Sat, 18 Oct, 76%", notCharging)

        val charging = BatteryFormatting.dateLine(
            date = date,
            percent = 76,
            charging = true,
            mode = Prefs.BATTERY_MODE_ALWAYS,
            statusBarShown = true
        )
        assertEquals("Sat, 18 Oct, 76% \u26A1\uFE0E", charging)
    }

    @Test
    fun `always mode shows battery when status bar is hidden`() {
        val notCharging = BatteryFormatting.dateLine(
            date = date,
            percent = 76,
            charging = false,
            mode = Prefs.BATTERY_MODE_ALWAYS,
            statusBarShown = false
        )
        assertEquals("Sat, 18 Oct, 76%", notCharging)

        val charging = BatteryFormatting.dateLine(
            date = date,
            percent = 76,
            charging = true,
            mode = Prefs.BATTERY_MODE_ALWAYS,
            statusBarShown = false
        )
        assertEquals("Sat, 18 Oct, 76% \u26A1\uFE0E", charging)
    }

    @Test
    fun `off mode hides battery in all conditions`() {
        val hiddenUnplugged = BatteryFormatting.dateLine(
            date = date,
            percent = 76,
            charging = false,
            mode = Prefs.BATTERY_MODE_OFF,
            statusBarShown = false
        )
        assertEquals(date, hiddenUnplugged)

        val hiddenCharging = BatteryFormatting.dateLine(
            date = date,
            percent = 76,
            charging = true,
            mode = Prefs.BATTERY_MODE_OFF,
            statusBarShown = false
        )
        assertEquals(date, hiddenCharging)

        val shownUnplugged = BatteryFormatting.dateLine(
            date = date,
            percent = 76,
            charging = false,
            mode = Prefs.BATTERY_MODE_OFF,
            statusBarShown = true
        )
        assertEquals(date, shownUnplugged)

        val shownCharging = BatteryFormatting.dateLine(
            date = date,
            percent = 76,
            charging = true,
            mode = Prefs.BATTERY_MODE_OFF,
            statusBarShown = true
        )
        assertEquals(date, shownCharging)
    }

    @Test
    fun `percent less than or equal to 0 returns date alone`() {
        val zeroAlways = BatteryFormatting.dateLine(
            date = date,
            percent = 0,
            charging = false,
            mode = Prefs.BATTERY_MODE_ALWAYS,
            statusBarShown = false
        )
        assertEquals(date, zeroAlways)

        val zeroAlwaysCharging = BatteryFormatting.dateLine(
            date = date,
            percent = 0,
            charging = true,
            mode = Prefs.BATTERY_MODE_ALWAYS,
            statusBarShown = false
        )
        assertEquals(date, zeroAlwaysCharging)

        val negativeAlways = BatteryFormatting.dateLine(
            date = date,
            percent = -1,
            charging = false,
            mode = Prefs.BATTERY_MODE_ALWAYS,
            statusBarShown = false
        )
        assertEquals(date, negativeAlways)

        val negativeAutoCharging = BatteryFormatting.dateLine(
            date = date,
            percent = -10,
            charging = true,
            mode = Prefs.BATTERY_MODE_AUTO,
            statusBarShown = false
        )
        assertEquals(date, negativeAutoCharging)
    }

    @Test
    fun `boundary percentage values format correctly`() {
        val onePercent = BatteryFormatting.dateLine(
            date = date,
            percent = 1,
            charging = false,
            mode = Prefs.BATTERY_MODE_ALWAYS,
            statusBarShown = false
        )
        assertEquals("Sat, 18 Oct, 1%", onePercent)

        val hundredPercentCharging = BatteryFormatting.dateLine(
            date = date,
            percent = 100,
            charging = true,
            mode = Prefs.BATTERY_MODE_ALWAYS,
            statusBarShown = false
        )
        assertEquals("Sat, 18 Oct, 100% \u26A1\uFE0E", hundredPercentCharging)
    }

    @Test
    fun `german date dot comma is cleaned up to comma`() {
        val germanDateWithDot = "Sa., 18. Okt."
        val result = BatteryFormatting.dateLine(
            date = germanDateWithDot,
            percent = 76,
            charging = false,
            mode = Prefs.BATTERY_MODE_AUTO,
            statusBarShown = false
        )
        assertEquals("Sa, 18. Okt, 76%", result)

        val resultCharging = BatteryFormatting.dateLine(
            date = germanDateWithDot,
            percent = 76,
            charging = true,
            mode = Prefs.BATTERY_MODE_AUTO,
            statusBarShown = false
        )
        assertEquals("Sa, 18. Okt, 76% \u26A1\uFE0E", resultCharging)

        val hiddenWithDot = BatteryFormatting.dateLine(
            date = "18., Oktober",
            percent = 76,
            charging = false,
            mode = Prefs.BATTERY_MODE_OFF,
            statusBarShown = false
        )
        assertEquals("18, Oktober", hiddenWithDot)
    }
}
