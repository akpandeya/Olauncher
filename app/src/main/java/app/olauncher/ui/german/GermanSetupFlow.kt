package app.olauncher.ui.german

/** The three screens of the German setup, in order. */
enum class GermanSetupStep {
    WELCOME, CHOOSE, PERMISSION;

    /** Segments of the progress bar that are filled on this step (1 to [SEGMENTS]). */
    val filledSegments: Int get() = ordinal + 1

    /** The following step, or null after the last one. */
    fun next(): GermanSetupStep? = entries.getOrNull(ordinal + 1)

    /** The step before, or null on the first one (back then leaves the setup). */
    fun previous(): GermanSetupStep? = entries.getOrNull(ordinal - 1)

    companion object {
        val SEGMENTS: Int = entries.size
    }
}

/** What the user switched on in step 2. Nothing except the offline dictionary is on by default. */
data class GermanSetupChoices(
    val offlineDictionary: Boolean = true,
    val lookupOnline: Boolean = false,
    val screenPick: Boolean = false,
    val germanNudges: Boolean = false,
)

/** The German settings to store once setup ends. */
data class GermanSetupResult(
    val offlineDictionary: Boolean,
    val lookupOnline: Boolean,
    val screenPick: Boolean,
    val germanUi: Boolean,
)

object GermanSetupFlow {

    /**
     * Screen picks need an accessibility permission, which is asked for only in step 3.
     * They stay off unless the user pressed "Open accessibility settings" there; the
     * step 2 switch alone never turns them on.
     */
    fun result(choices: GermanSetupChoices, openedAccessibilitySettings: Boolean) = GermanSetupResult(
        offlineDictionary = choices.offlineDictionary,
        lookupOnline = choices.lookupOnline,
        screenPick = openedAccessibilitySettings,
        germanUi = choices.germanNudges,
    )
}
