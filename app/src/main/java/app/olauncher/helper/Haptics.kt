package app.olauncher.helper

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
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

    /** What [perform] should do for one haptic. Pure so the wiring can be unit tested. */
    sealed class Plan {
        object Skip : Plan()
        data class System(val constant: Int) : Plan()
        data class Custom(val amplitude: Int, val durationMs: Int) : Plan()
    }

    /**
     * Decides how to play a haptic. Disabled or strength 0 means [Plan.Skip]. Untouched sliders, or a
     * device without amplitude control, use the system haptic constant (the original feel). Otherwise
     * the user's strength and duration are played as a one-shot vibration.
     */
    fun planFor(kind: Kind, enabled: Boolean, strength: Int, durationMs: Int, hasAmplitudeControl: Boolean): Plan {
        val constant = constantFor(kind, enabled) ?: return Plan.Skip
        if (!hasAmplitudeControl || !HapticTuning.isCustom(strength, durationMs)) return Plan.System(constant)
        val amplitude = HapticTuning.amplitudeFor(strength) ?: return Plan.Skip
        return Plan.Custom(amplitude, HapticTuning.clampDurationMs(durationMs))
    }

    fun tick(view: View?): Boolean = perform(view, Kind.TICK)

    fun confirm(view: View?): Boolean = perform(view, Kind.CONFIRM)

    fun longPress(view: View?): Boolean = perform(view, Kind.LONG_PRESS)

    fun perform(view: View?, kind: Kind): Boolean {
        if (view == null) return false
        val context = view.context
        val prefs = Prefs(context)
        val vibrator = context.getSystemService(Vibrator::class.java)
        // Devices without amplitude control cannot honour the strength slider, so planFor falls back
        // to the existing performHapticFeedback path for them.
        val plan = planFor(
            kind, isEnabled(context), prefs.hapticStrength, prefs.hapticDurationMs,
            vibrator?.hasAmplitudeControl() == true,
        )
        return when (plan) {
            Plan.Skip -> false
            is Plan.System -> view.performHapticFeedback(plan.constant)
            is Plan.Custom -> {
                // Plays through the Vibrator, so it does not depend on the system "Touch vibration" setting.
                vibrator?.vibrate(VibrationEffect.createOneShot(plan.durationMs.toLong(), plan.amplitude))
                true
            }
        }
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
