package app.olauncher.helper

/** Animated weather layer drawn over the wallpaper on Home. */
enum class WeatherEffect { NONE, SUN, FOG, RAIN, SNOW, STORM, CLOUDS }

/** Colours for one effect: [primary] is the main element, [secondary] the storm flash. */
data class EffectTint(val primary: Int, val secondary: Int)

/** Maps WMO weather codes to an effect and its day or night colours. Context-free. */
object WeatherEffects {
    const val MAX_FPS = 30
    /** Minimum gap between two drawn frames; 33 ms gives at most 30 fps. */
    const val FRAME_INTERVAL_MS = 1000L / MAX_FPS

    /** The effect for a WMO code; NONE for unknown codes. */
    fun forCode(code: Int): WeatherEffect = when (code) {
        0, 1 -> WeatherEffect.SUN
        2, 3 -> WeatherEffect.CLOUDS
        45, 48 -> WeatherEffect.FOG
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> WeatherEffect.RAIN
        71, 73, 75, 77, 85, 86 -> WeatherEffect.SNOW
        95, 96, 99 -> WeatherEffect.STORM
        else -> WeatherEffect.NONE
    }

    /** NONE when the setting is off or there is no cached weather code. */
    fun select(enabled: Boolean, code: Int?): WeatherEffect {
        if (!enabled || code == null) return WeatherEffect.NONE
        return forCode(code)
    }

    fun tint(effect: WeatherEffect, isDay: Boolean): EffectTint {
        val (day, night) = when (effect) {
            WeatherEffect.SUN -> 0xFFFFD27A.toInt() to 0xFFBFD4FF.toInt()
            WeatherEffect.FOG -> 0xFFE6E9EE.toInt() to 0xFF8E98A8.toInt()
            WeatherEffect.RAIN -> 0xFFB8D8F0.toInt() to 0xFF6F86A6.toInt()
            WeatherEffect.SNOW -> 0xFFFFFFFF.toInt() to 0xFFDDE6F5.toInt()
            WeatherEffect.STORM -> 0xFFB0C4DE.toInt() to 0xFF5C6F8F.toInt()
            WeatherEffect.CLOUDS -> 0xFFF2F4F7.toInt() to 0xFF55627A.toInt()
            WeatherEffect.NONE -> 0x00000000 to 0x00000000
        }
        val primary = if (isDay) day else night
        val flash = if (isDay) 0xFFE8F0FF.toInt() else 0xFF9FB3D6.toInt()
        return EffectTint(primary, if (effect == WeatherEffect.STORM) flash else primary)
    }
}
