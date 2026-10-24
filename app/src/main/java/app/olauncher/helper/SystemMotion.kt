package app.olauncher.helper

import android.animation.ValueAnimator
import android.content.Context

/** Reads the device state that decides whether UI animations run; feeds [MotionPolicy]. */
object SystemMotion {
    fun isAnimationsEnabled(context: Context): Boolean =
        !context.isEinkDisplay() && !context.isSystemAnimationsDisabled() && ValueAnimator.areAnimatorsEnabled()
}
