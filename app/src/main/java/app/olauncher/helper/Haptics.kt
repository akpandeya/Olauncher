package app.olauncher.helper

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View
import app.olauncher.data.Prefs

object Haptics {

    enum class Kind {
        TICK,
        CONFIRM,
        LONG_PRESS,
        CLOCK_TICK,
        REJECT,
        GESTURE,
        MENU;
    }

    var enabledSupplier: ((Context) -> Boolean)? = null

    fun isEnabled(context: Context): Boolean =
        enabledSupplier?.invoke(context) ?: Prefs(context).haptics

    /** Pure gate: Thumbline haptics fire only when the user pref is on, for every kind. */
    @Suppress("UNUSED_PARAMETER")
    fun hapticsEnabled(prefsOn: Boolean, kind: Kind): Boolean = prefsOn

    /**
     * Pure gate mapping kind and enabled state to an Android HapticFeedbackConstant, or null if disabled.
     */
    fun constantFor(kind: Kind, enabled: Boolean): Int? {
        if (!hapticsEnabled(enabled, kind)) return null
        return when (kind) {
            Kind.TICK, Kind.CLOCK_TICK -> HapticFeedbackConstants.CLOCK_TICK
            Kind.CONFIRM -> HapticFeedbackConstants.CONFIRM
            Kind.LONG_PRESS -> HapticFeedbackConstants.LONG_PRESS
            Kind.REJECT -> HapticFeedbackConstants.REJECT
            Kind.GESTURE -> HapticFeedbackConstants.SEGMENT_TICK
            // CONTEXT_CLICK plays nothing on many phones, so menu choices use CONFIRM.
            Kind.MENU -> HapticFeedbackConstants.CONFIRM
        }
    }

    fun tick(view: View?): Boolean = perform(view, Kind.TICK)

    fun confirm(view: View?): Boolean = perform(view, Kind.CONFIRM)

    fun longPress(view: View?): Boolean = perform(view, Kind.LONG_PRESS)

    fun perform(view: View?, kind: Kind): Boolean {
        if (view == null) return false
        val enabled = isEnabled(view.context)
        val constant = constantFor(kind, enabled) ?: return false
        return view.performHapticFeedback(constant)
    }
}

/**
 * Sets a long-click listener that returns true, so Android's own long-press buzz is suppressed,
 * then fires the Thumbline haptic (only when the haptics pref is on) before running [onLongClick].
 */
fun View.hapticLongClick(kind: Haptics.Kind = Haptics.Kind.LONG_PRESS, onLongClick: () -> Unit) {
    setOnLongClickListener { view ->
        Haptics.perform(view, kind)
        onLongClick()
        true
    }
}
