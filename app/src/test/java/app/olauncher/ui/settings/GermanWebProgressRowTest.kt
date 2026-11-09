package app.olauncher.ui.settings

import app.olauncher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * The "Move progress to the web app" texts (#835, task 05): unset vs a saved
 * epoch day must read differently, and the date in the args must follow the
 * day, so a mapping that ignores or hardcodes the day cannot pass.
 */
class GermanWebProgressRowTest {

    private val savedDay = 20400L
    private val savedDate = LocalDate.ofEpochDay(savedDay).toString()

    @Test
    fun `value is absent until a guided export landed`() {
        assertNull(GermanWebProgressRow.valueFor(null))
    }

    @Test
    fun `value names the day the guided export landed`() {
        val text = GermanWebProgressRow.valueFor(savedDay)!!
        assertEquals(R.string.german_web_move_progress_saved_on, text.stringRes)
        assertEquals(listOf(savedDate), text.formatArgs)
    }

    @Test
    fun `value follows the day, not a constant`() {
        val otherDay = 20401L
        val text = GermanWebProgressRow.valueFor(otherDay)!!
        assertEquals(listOf(LocalDate.ofEpochDay(otherDay).toString()), text.formatArgs)
    }

    @Test
    fun `dialog shows the three steps while nothing was saved`() {
        val text = GermanWebProgressRow.dialogMessageFor(null)
        assertEquals(R.string.german_web_move_progress_message, text.stringRes)
        assertEquals(emptyList<String>(), text.formatArgs)
    }

    @Test
    fun `dialog names the last saved backup once one exists`() {
        val text = GermanWebProgressRow.dialogMessageFor(savedDay)
        assertEquals(R.string.german_web_move_progress_message_saved, text.stringRes)
        assertEquals(listOf(savedDate), text.formatArgs)
    }

    @Test
    fun `unset and saved dialogs read differently`() {
        assertEquals(
            2,
            setOf(
                GermanWebProgressRow.dialogMessageFor(null).stringRes,
                GermanWebProgressRow.dialogMessageFor(savedDay).stringRes,
            ).size
        )
    }
}
