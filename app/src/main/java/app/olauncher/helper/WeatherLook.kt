package app.olauncher.helper

/** Animated weather look shared across Home and Today. Context-free. */
enum class WeatherLook(val params: WeatherLookParams) {
    CLEAR(
        WeatherLookParams(
            wind = 0.3f,
            sun = 1f,
        )
    ),
    PARTLY(
        WeatherLookParams(
            wind = 0.6f,
            gustiness = 0.3f,
            sun = 0.6f,
            clouds = 0.45f,
            cloudLayers = 1,
        )
    ),
    OVERCAST(
        WeatherLookParams(
            wind = 0.4f,
            gustiness = 0.2f,
            clouds = 1f,
            cloudLayers = 2,
        )
    ),
    LIGHT_RAIN(
        WeatherLookParams(
            density = 0.35f,
            fallSpeed = 1.0f,
            glassDrops = 6f,
            slideSpeed = 0.3f,
            wind = 0.5f,
            gustiness = 0.5f,
        )
    ),
    HEAVY_RAIN(
        WeatherLookParams(
            density = 0.9f,
            fallSpeed = 2.2f,
            glassDrops = 26f,
            slideSpeed = 1.0f,
            wind = 1f,
            gustiness = 0.8f,
        )
    ),
    STORM(
        WeatherLookParams(
            density = 0.95f,
            fallSpeed = 2.5f,
            glassDrops = 30f,
            slideSpeed = 1.0f,
            bolt = 1f,
            wind = 1f,
            gustiness = 1f,
        )
    ),
    SNOW(
        WeatherLookParams(
            density = 0.6f,
            fallSpeed = 0.5f,
            wind = 0.8f,
            gustiness = 0.6f,
            snow = true,
        )
    ),
    FOG(
        WeatherLookParams(
            wind = 0.2f,
            gustiness = 0.1f,
            fog = 1f,
        )
    ),
    CLEAR_NIGHT(
        WeatherLookParams(
            stars = 1f,
        )
    );

    val isRain: Boolean
        get() = this == LIGHT_RAIN || this == HEAVY_RAIN || this == STORM
}

/**
 * Parameters driving the weather effect rendering. Context-free.
 * All ranges sit within:
 * [density] 0..1, [fallSpeed] 0.2..3, [glassDrops] 0..30, [slideSpeed] 0..1.2, [bolt] 0 or 1,
 * [wind], [gustiness], [sun], [clouds], [fog], [stars] 0..1, [cloudLayers] 0..2, [snow] Boolean.
 */
data class WeatherLookParams(
    val density: Float = 0f,
    val fallSpeed: Float = 0.2f,
    val glassDrops: Float = 0f,
    val slideSpeed: Float = 0f,
    val bolt: Float = 0f,
    val wind: Float = 0f,
    val gustiness: Float = 0f,
    val sun: Float = 0f,
    val clouds: Float = 0f,
    val cloudLayers: Int = 0,
    val fog: Float = 0f,
    val stars: Float = 0f,
    val snow: Boolean = false,
)

/** Pure mapping from WMO forecast codes to looks and parameters. Context-free. */
object WeatherLooks {

    /** Maps a WMO code and day/night state to a [WeatherLook]; null for unknown codes. */
    fun forForecast(code: Int, isDay: Boolean): WeatherLook? = when (code) {
        0 -> if (isDay) WeatherLook.CLEAR else WeatherLook.CLEAR_NIGHT
        1 -> if (isDay) WeatherLook.PARTLY else WeatherLook.CLEAR_NIGHT
        2 -> if (isDay) WeatherLook.PARTLY else WeatherLook.OVERCAST
        3 -> WeatherLook.OVERCAST
        45, 48 -> WeatherLook.FOG
        51, 53, 55, 56, 57, 61 -> WeatherLook.LIGHT_RAIN
        63, 65, 66, 67, 80, 81, 82 -> WeatherLook.HEAVY_RAIN
        71, 73, 75, 77, 85, 86 -> WeatherLook.SNOW
        95, 96, 99 -> WeatherLook.STORM
        else -> null
    }

    /** Returns the base params for a look. */
    fun params(look: WeatherLook): WeatherLookParams = look.params

    /**
     * Look parameters for a forecast code and day/night state.
     * Snow density is parameterized by code (71 -> 0.3, 73 -> 0.6, 75/77/85/86 -> 0.9).
     * Returns null for unknown codes.
     */
    fun paramsFor(code: Int, isDay: Boolean): WeatherLookParams? {
        val look = forForecast(code, isDay) ?: return null
        return if (look == WeatherLook.SNOW) {
            val snowDensity = when (code) {
                71 -> 0.3f
                73 -> 0.6f
                75, 77, 85, 86 -> 0.9f
                else -> look.params.density
            }
            look.params.copy(density = snowDensity)
        } else {
            look.params
        }
    }
}
