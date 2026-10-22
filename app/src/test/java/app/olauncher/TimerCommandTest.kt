package app.olauncher

import app.olauncher.helper.DrawerCommand
import app.olauncher.helper.TimerCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimerCommandTest {
    private fun secs(args: String) = TimerCommand.parse(args)?.seconds

    @Test fun bareNumberIsMinutes() = assertEquals(600, secs("10"))
    @Test fun hoursAndMinutes() = assertEquals(5400, secs("1h30"))
    @Test fun hoursAndMinutesWithUnit() = assertEquals(5400, secs("1h30m"))
    @Test fun hoursOnly() = assertEquals(3600, secs("1h"))
    @Test fun minutes() = assertEquals(2700, secs("45m"))
    @Test fun seconds() = assertEquals(90, secs("90s"))
    @Test fun upperCaseUnits() = assertEquals(5400, secs("1H30M"))
    @Test fun exactlyTwentyFourHours() = assertEquals(86400, secs("24h"))

    @Test fun labelWords() {
        val t = TimerCommand.parse("25 tea time")!!
        assertEquals(1500, t.seconds)
        assertEquals("tea time", t.label)
    }

    @Test fun numbersAfterLabelStartAreLabel() {
        val t = TimerCommand.parse("10 tea 5 min")!!
        assertEquals(600, t.seconds)
        assertEquals("tea 5 min", t.label)
    }

    @Test fun noLabelIsEmpty() = assertEquals("", TimerCommand.parse("10")!!.label)

    @Test fun rejects() {
        for (bad in listOf("", "  ", "0", "0m", "0h0", "25h", "1441", "86401s", "10x", "tea 10", "h", "1.5h", "-5", "1h30x")) {
            assertNull("should reject '$bad'", TimerCommand.parse(bad))
        }
    }

    @Test fun formatDuration() {
        assertEquals("1 h 30 min", TimerCommand.formatDuration(5400))
        assertEquals("10 min", TimerCommand.formatDuration(600))
        assertEquals("1 min 30 s", TimerCommand.formatDuration(90))
        assertEquals("1 h", TimerCommand.formatDuration(3600))
    }

    @Test fun previewText() {
        assertEquals("⏱ 1 h 30 min · tea", TimerCommand.previewText(TimerCommand.parse("1h30 tea")!!))
        assertEquals("⏱ 45 min", TimerCommand.previewText(TimerCommand.parse("45m")!!))
    }

    @Test fun drawerCommandHandsArgsToParser() {
        val cmd = DrawerCommand.parse(":t 1h30 tea", germanEnabled = false) as DrawerCommand.Timer
        assertEquals(5400, TimerCommand.parse(cmd.args)?.seconds)
    }
}
