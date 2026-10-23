package app.olauncher.ui.settings

/** How strong a settings value is drawn: "Off" is dimmer than "On" and any other value. */
object SettingsValueTone {
    const val FULL_ALPHA = 1f
    const val DIMMED_ALPHA = 0.7f

    fun alphaFor(text: CharSequence?, offLabel: String): Float =
        if (text?.toString()?.trim() == offLabel) DIMMED_ALPHA else FULL_ALPHA
}
