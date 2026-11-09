package app.olauncher.ui.settings

import androidx.annotation.StringRes
import app.olauncher.R
import java.time.LocalDate

/**
 * The texts of the "Move progress to the web app" row (epic #835, task 05):
 * the row's value line ("Saved on <date>" once an export from this row landed)
 * and the how-to dialog's message, which names the last saved backup once one
 * exists. Context-free so the day-to-text mapping is unit-tested; the fragment
 * only formats it.
 */
data class GermanWebProgressText(
    @StringRes val stringRes: Int,
    /** The arguments the string takes, in order (the formatted date). */
    val formatArgs: List<String>,
)

object GermanWebProgressRow {

    private fun dateOf(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).toString()

    /** The row's value line: null until an export landed from this row, then "Saved on <date>". */
    fun valueFor(savedEpochDay: Long?): GermanWebProgressText? = savedEpochDay?.let {
        GermanWebProgressText(R.string.german_web_move_progress_saved_on, listOf(dateOf(it)))
    }

    /**
     * The how-to dialog's message: the three steps (backup, Konto import, word
     * count), naming the last saved backup in the first step once one exists.
     */
    fun dialogMessageFor(savedEpochDay: Long?): GermanWebProgressText = if (savedEpochDay == null) {
        GermanWebProgressText(R.string.german_web_move_progress_message, emptyList())
    } else {
        GermanWebProgressText(R.string.german_web_move_progress_message_saved, listOf(dateOf(savedEpochDay)))
    }
}
