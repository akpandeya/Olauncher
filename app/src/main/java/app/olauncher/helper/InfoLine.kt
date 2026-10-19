package app.olauncher.helper

object InfoLine {

    const val SEPARATOR = " · "

    /** Joins the non-blank parts with a middle dot; missing parts leave no extra separators. */
    fun compose(
        date: String,
        weather: String? = null,
        battery: String? = null,
        screenTime: String? = null
    ): String =
        listOf(date, weather, battery, screenTime)
            .mapNotNull { it?.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(SEPARATOR)
}
