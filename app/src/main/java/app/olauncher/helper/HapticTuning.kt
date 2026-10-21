package app.olauncher.helper

/** Context-free mapping from the haptic sliders (strength percent, duration ms) to vibrator values. */
object HapticTuning {
    const val STRENGTH_MIN = 0
    const val STRENGTH_MAX = 100
    const val DEFAULT_STRENGTH = 70

    const val DURATION_MIN_MS = 10
    const val DURATION_MAX_MS = 200
    const val DEFAULT_DURATION_MS = 25

    /** Android amplitude range for VibrationEffect.createOneShot (0 is not allowed there). */
    const val AMPLITUDE_MIN = 1
    const val AMPLITUDE_MAX = 255

    fun clampStrength(value: Int): Int = value.coerceIn(STRENGTH_MIN, STRENGTH_MAX)

    fun clampDurationMs(value: Int): Int = value.coerceIn(DURATION_MIN_MS, DURATION_MAX_MS)

    /** Strength 0 means "no buzz" (null); 1..100 maps linearly onto 1..255. Out-of-range input is clamped. */
    fun amplitudeFor(strength: Int): Int? {
        val s = clampStrength(strength)
        if (s == 0) return null
        return AMPLITUDE_MIN + Math.round((s - 1) * (AMPLITUDE_MAX - AMPLITUDE_MIN) / (STRENGTH_MAX - 1).toFloat())
    }

    /** True once the user moved either slider away from the defaults. */
    fun isCustom(strength: Int, durationMs: Int): Boolean =
        clampStrength(strength) != DEFAULT_STRENGTH || clampDurationMs(durationMs) != DEFAULT_DURATION_MS
}
