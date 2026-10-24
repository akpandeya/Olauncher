package app.olauncher.ui.today

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import java.time.ZonedDateTime
import app.olauncher.ui.today.cards.HeldCard
import app.olauncher.ui.today.cards.NextUpCard
import app.olauncher.ui.today.cards.NotesCard
import app.olauncher.ui.today.cards.SavedCard
import app.olauncher.ui.today.cards.WeatherCard
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

    fun title(context: Context): CharSequence = context.getString(titleRes)

    /** True when the card draws its own heading (with controls in it), so the panel adds none. */
    val drawsOwnHeader: Boolean get() = false

    /** True for cards drawn on a warm tint (Held) instead of the plain card surface. */
    val tinted: Boolean get() = false

    /** True for cards that sit two to a row, side by side, on the page. */
    val halfWidth: Boolean get() = false

    /** True for cards drawn in the page header instead of a panel (the weather). */
    val inHero: Boolean get() = false

    fun isAvailable(context: Context): Boolean = true

    /** Higher shows nearer the top when the order is smart. Default keeps the list position. */
    fun priority(context: Context, now: ZonedDateTime): Int =
        TodayOrder.defaultPriority(TodayCards.all.indexOf(this).coerceAtLeast(0))

    /** True when the card belongs at the top of the page right now (smart order only). */
    fun pinnedToTop(context: Context, now: ZonedDateTime): Boolean = false

    /** False when the card would render nothing at all; its panel is then left out. */
    fun hasContent(context: Context): Boolean = true

    /**
     * Column span on the Today grid for cards that live on it (widgets), or null
     * for built-in cards, which stay full-width rows (or half-width pairs, see [halfWidth]).
     */
    val gridSpan: Int? get() = null

    /** Column span of this card's compact tile, or null when it has no tile variant. */
    val tileSpan: Int? get() = null

    /** Span the page uses: [gridSpan] unless the card is shown as a tile (see [TodayTiles]). */
    fun gridSpan(context: Context): Int? = gridSpan

    fun createView(inflater: LayoutInflater, parent: ViewGroup, fragment: Fragment): View
    fun onResume() {}

    /** Called on resume with the view this card returned from [createView]. */
    fun onResume(view: View) = onResume()
}

/** Pure helpers for the opt-in tile variants of built-in cards. */
object TodayTiles {
    /** Grid span of a card: its tile span when the tile is on and exists, else null (full row). */
    fun span(tileSpan: Int?, tileOn: Boolean): Int? =
        if (tileOn && tileSpan != null) tileSpan.coerceIn(1, TodayWidgetResize.COLUMNS) else null

    /** Cards that offer a tile variant. */
    fun tileCards(cards: List<TodayCard>): List<TodayCard> = cards.filter { it.tileSpan != null }
}

object TodayCards {
    val all: List<TodayCard> = listOf(WeatherCard, WordsCard, NotesCard, WellbeingCard, SavedCard, NextUpCard, HeldCard)
}
