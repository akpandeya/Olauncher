package app.olauncher.helper

/** Animated weather layer drawn over the wallpaper on Home. */
enum class WeatherEffect { NONE, SUN, FOG, RAIN, SNOW, STORM, CLOUDS }

/** How strong the weather is, from the WMO code. */
enum class Intensity { LIGHT, MODERATE, HEAVY }

/** One effect with its strength; [freezing] is freezing rain or drizzle, [hail] a thunderstorm with hail. */
data class EffectSpec(
    val effect: WeatherEffect,
    val intensity: Intensity,
    val freezing: Boolean = false,
    val hail: Boolean = false,
)

/** Colours for one effect: [primary] is the main element, [secondary] the storm flash. */
data class EffectTint(val primary: Int, val secondary: Int)

/** Maps WMO weather codes to an effect and its day or night colours. Context-free. */
object WeatherEffects {
    const val MAX_FPS = 30
    /** Minimum gap between two drawn frames; 33 ms gives at most 30 fps. */
    const val FRAME_INTERVAL_MS = 1000L / MAX_FPS

    /** Effect, strength and freezing/hail flags for a WMO code; NONE for unknown codes. */
    fun specFor(code: Int): EffectSpec = when (code) {
        0, 1 -> EffectSpec(WeatherEffect.SUN, Intensity.MODERATE)
        2, 3 -> EffectSpec(WeatherEffect.CLOUDS, Intensity.MODERATE)
        45, 48 -> EffectSpec(WeatherEffect.FOG, Intensity.MODERATE)
        51, 61, 80 -> EffectSpec(WeatherEffect.RAIN, Intensity.LIGHT)
        53, 63, 81 -> EffectSpec(WeatherEffect.RAIN, Intensity.MODERATE)
        55, 65, 82 -> EffectSpec(WeatherEffect.RAIN, Intensity.HEAVY)
        56, 66 -> EffectSpec(WeatherEffect.RAIN, Intensity.LIGHT, freezing = true)
        57, 67 -> EffectSpec(WeatherEffect.RAIN, Intensity.HEAVY, freezing = true)
        71, 77, 85 -> EffectSpec(WeatherEffect.SNOW, Intensity.LIGHT)
        73 -> EffectSpec(WeatherEffect.SNOW, Intensity.MODERATE)
        75, 86 -> EffectSpec(WeatherEffect.SNOW, Intensity.HEAVY)
        95 -> EffectSpec(WeatherEffect.STORM, Intensity.MODERATE)
        96 -> EffectSpec(WeatherEffect.STORM, Intensity.MODERATE, hail = true)
        99 -> EffectSpec(WeatherEffect.STORM, Intensity.HEAVY, hail = true)
        else -> EffectSpec(WeatherEffect.NONE, Intensity.LIGHT)
    }

    /** The effect for a WMO code; NONE for unknown codes. */
    fun forCode(code: Int): WeatherEffect = specFor(code).effect

    /** The spec for the cached code, or a NONE spec when the setting is off or there is no code. */
    fun selectSpec(enabled: Boolean, code: Int?): EffectSpec =
        if (!enabled || code == null) specFor(Int.MIN_VALUE) else specFor(code)

    /** Frames move only while Home is visible, a weather look is shown and system animations are on. */
    fun shouldAnimate(homeVisible: Boolean, look: WeatherLook?, animatorsEnabled: Boolean): Boolean =
        homeVisible && look != null && animatorsEnabled

    /** NONE when the setting is off or there is no cached weather code. */
    fun select(enabled: Boolean, code: Int?): WeatherEffect {
        if (!enabled || code == null) return WeatherEffect.NONE
        return forCode(code)
    }

    fun tint(effect: WeatherEffect, isDay: Boolean = true): EffectTint {
        val color = when (effect) {
            WeatherEffect.SUN -> WeatherLookColors.SUN
            WeatherEffect.FOG -> WeatherLookColors.FOG
            WeatherEffect.RAIN -> 0xFFB8D8F0.toInt()
            WeatherEffect.SNOW -> WeatherLookColors.SNOW
            WeatherEffect.STORM -> 0xFFB0C4DE.toInt()
            WeatherEffect.CLOUDS -> WeatherLookColors.CLOUDS
            WeatherEffect.NONE -> 0x00000000
        }
        val flash = WeatherLookColors.FLASH
        return EffectTint(color, if (effect == WeatherEffect.STORM) flash else color)
    }
}
