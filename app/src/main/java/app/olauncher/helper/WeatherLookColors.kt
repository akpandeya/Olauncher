package app.olauncher.helper

/**
 * Pure colour-token object providing ARGB integer colours for weather effects.
 * Context-free, shared across day and night.
 */
object WeatherLookColors {
    val SUN: Int = 0xFFFFD27AL.toInt()
    val CLOUDS: Int = 0xFFF2F4F7L.toInt()
    val CLOUD_GREY: Int = 0xFFA8B0BEL.toInt()
    val OVERCAST_GREY: Int = CLOUD_GREY
    val FOG: Int = 0xFFE6E9EEL.toInt()
    val SNOW: Int = 0xFFFFFFFFL.toInt()
    val STORM_FLASH: Int = 0xFFE8F0FFL.toInt()
    val FLASH: Int = STORM_FLASH
    val STAR: Int = 0xFFDCE6FFL.toInt()
    val SHEEN: Int = 0xFFDCE8FFL.toInt()

    val IMPACT: Int = 0xFFECF4FFL.toInt()
    val IMPACT_RGB: Int = IMPACT
    val IMPACT_EDGE: Int = 0xFF000A1EL.toInt()
    val IMPACT_EDGE_RGB: Int = IMPACT_EDGE

    /**
     * Returns overcast grey for [WeatherLook.OVERCAST], or [CLOUDS] otherwise.
     */
    fun cloudColor(look: WeatherLook): Int =
        if (look == WeatherLook.OVERCAST) CLOUD_GREY else CLOUDS
}
