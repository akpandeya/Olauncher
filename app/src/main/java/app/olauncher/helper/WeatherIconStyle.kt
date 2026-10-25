package app.olauncher.helper

/** Style of the weather icon shown on the Home screen. Context-free. */
enum class WeatherIconStyle(val key: String) {
    COLOUR("colour"),
    MONOCHROME("monochrome");

    companion object {
        val DEFAULT: WeatherIconStyle = COLOUR

        fun parse(value: String?): WeatherIconStyle = when (value?.trim()?.lowercase()) {
            "monochrome" -> MONOCHROME
            "colour", "color" -> COLOUR
            else -> DEFAULT
        }

        fun from(value: String?): WeatherIconStyle = parse(value)

        fun fromKey(value: String?): WeatherIconStyle = parse(value)

        fun fromStored(value: String?): WeatherIconStyle = parse(value)
    }
}
