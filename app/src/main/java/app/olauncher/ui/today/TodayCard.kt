package app.olauncher.ui.today

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.fragment.app.Fragment
import java.time.ZonedDateTime
import app.olauncher.helper.MotionPolicy
import app.olauncher.ui.today.cards.HeldCard
import app.olauncher.ui.today.cards.NextUpCard
import app.olauncher.ui.today.cards.NotesCard
import app.olauncher.ui.today.cards.SavedCard
import app.olauncher.ui.today.cards.WeatherCard
import app.olauncher.ui.today.cards.WellbeingCard
import app.olauncher.ui.today.cards.WordsCard

/**
 * Shrinks the view slightly on press and restores to full size on release or cancel.
 * Respects MotionPolicy: transitions are instant when animations are off.
 */
@SuppressLint("ClickableViewAccessibility")
fun View.attachPressScale(
    targetScale: Float = MotionPolicy.PRESS_SCALE,
    durationMs: Long = MotionPolicy.PRESS_DURATION_MS,
) {
    if (!isClickable && !isFocusable) {
        isClickable = true
    }
    setOnTouchListener { v, event ->
        val animatorsEnabled = MotionPolicy.isAnimationsEnabled(v.context)
        val duration = MotionPolicy.durationMs(durationMs, animatorsEnabled)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (duration > 0L) {
                    v.animate()
                        .scaleX(targetScale)
                        .scaleY(targetScale)
                        .setDuration(duration)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                } else {
                    v.scaleX = targetScale
                    v.scaleY = targetScale
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (duration > 0L) {
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(duration)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                } else {
                    v.scaleX = 1f
                    v.scaleY = 1f
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.x < 0 || event.x > v.width || event.y < 0 || event.y > v.height) {
                    if (duration > 0L) {
                        v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(duration)
                            .setInterpolator(DecelerateInterpolator())
                            .start()
                    } else {
                        v.scaleX = 1f
                        v.scaleY = 1f
                    }
                }
            }
        }
        false
    }
}

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
     * for built-in cards, which are full-width rows.
     */
    val gridSpan: Int? get() = null

    fun createView(inflater: LayoutInflater, parent: ViewGroup, fragment: Fragment): View
    fun onResume() {}

    /** Called on resume with the view this card returned from [createView]. */
    fun onResume(view: View) = onResume()
}

object TodayCards {
    val all: List<TodayCard> = listOf(WeatherCard, WordsCard, NotesCard, WellbeingCard, SavedCard, NextUpCard, HeldCard)
}
