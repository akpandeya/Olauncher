package app.olauncher.ui.words

import app.olauncher.R
import app.olauncher.data.store.ReviewEvent

/**
 * Three tones for review grade buttons (#508): No is rose, Almost is sand, Knew it is sage.
 */
object ReviewButtonTones {

    /** Returns the accent colour resource for [result]. */
    fun colorResForResult(result: String): Int = when (result) {
        ReviewEvent.Result.AGAIN -> R.color.accentRose
        ReviewEvent.Result.ALMOST -> R.color.accentSand
        ReviewEvent.Result.KNEW -> R.color.accentSage
        else -> R.color.accentSage
    }
}
