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
     * The choices the setup opens with: the first time the fixed defaults, afterwards the
     * stored German settings so finishing again never overwrites what the user changed later.
     */
    fun initialChoices(
        previouslySetUp: Boolean,
        offlineDictionary: Boolean,
        lookupOnline: Boolean,
        screenPick: Boolean,
        germanUi: Boolean,
    ): GermanSetupChoices =
        if (previouslySetUp) {
            GermanSetupChoices(offlineDictionary, lookupOnline, screenPick, germanUi)
        } else {
            GermanSetupChoices()
        }

    /** The step after [step], or null when setup ends. The permission step shows only if screen picks are on. */
    fun next(step: GermanSetupStep, choices: GermanSetupChoices): GermanSetupStep? {
        val following = step.next() ?: return null
        return if (following == GermanSetupStep.PERMISSION && !choices.screenPick) null else following
    }

    /**
     * Screen picks need an accessibility permission, which is asked for only in step 3, and
     * step 3 is reached only when the step 2 switch is on. They turn on only when both hold:
     * the switch is on and the user pressed "Open accessibility settings". [alreadyOn] keeps an
     * earlier grant when the setup is run again and the permission step is skipped.
     */
    fun result(
        choices: GermanSetupChoices,
        openedAccessibilitySettings: Boolean,
        alreadyOn: Boolean = false,
    ) = GermanSetupResult(
        offlineDictionary = choices.offlineDictionary,
        lookupOnline = choices.lookupOnline,
        screenPick = choices.screenPick && (openedAccessibilitySettings || alreadyOn),
        germanUi = choices.germanNudges,
    )
}
