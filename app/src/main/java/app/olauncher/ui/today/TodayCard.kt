package app.olauncher.ui.today

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import app.olauncher.ui.today.cards.HeldCard
import app.olauncher.ui.today.cards.NextUpCard
import app.olauncher.ui.today.cards.SavedCard
import app.olauncher.ui.today.cards.WeatherCard
import app.olauncher.ui.today.cards.WeekCard
import app.olauncher.ui.today.cards.WellbeingCard
import app.olauncher.ui.today.cards.WordsCard

/**
 * Today card contract.
 *
 * add a card = one class + one line here (in [TodayCards.all])
 */
interface TodayCard {
    val key: String
    val titleRes: Int
    val defaultEnabled: Boolean get() = true

    /** Emoji put in front of the title in the card's panel header. */
    val emoji: String get() = ""

    /** True for cards drawn in the page header instead of a panel (the weather). */
    val inHero: Boolean get() = false

    fun isAvailable(context: Context): Boolean = true

    /** False when the card would render nothing at all; its panel is then left out. */
    fun hasContent(context: Context): Boolean = true
    fun createView(inflater: LayoutInflater, parent: ViewGroup, fragment: Fragment): View
    fun onResume() {}

    /** Called on resume with the view this card returned from [createView]. */
    fun onResume(view: View) = onResume()
}

object TodayCards {
    val all: List<TodayCard> = listOf(WeatherCard, NextUpCard, SavedCard, HeldCard, WellbeingCard, WordsCard, WeekCard)
}

