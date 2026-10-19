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
        CLOCK_TICK;
    }

    var enabledSupplier: ((Context) -> Boolean)? = null

    fun isEnabled(context: Context): Boolean =
        enabledSupplier?.invoke(context) ?: Prefs(context).haptics

    /**
     * Pure gate mapping kind and enabled state to an Android HapticFeedbackConstant, or null if disabled.
     */
    fun constantFor(kind: Kind, enabled: Boolean): Int? {
        if (!enabled) return null
        return when (kind) {
            Kind.TICK, Kind.CLOCK_TICK -> HapticFeedbackConstants.CLOCK_TICK
            Kind.CONFIRM -> HapticFeedbackConstants.CONFIRM
            Kind.LONG_PRESS -> HapticFeedbackConstants.LONG_PRESS
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
