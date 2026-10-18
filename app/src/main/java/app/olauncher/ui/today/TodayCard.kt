package app.olauncher.ui.today

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import app.olauncher.ui.today.cards.SavedCard
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
    fun isAvailable(context: Context): Boolean = true
    fun createView(inflater: LayoutInflater, parent: ViewGroup, fragment: Fragment): View
    fun onResume() {}
}

object TodayCards {
    val all: List<TodayCard> = listOf(SavedCard, WellbeingCard, WordsCard)
}

