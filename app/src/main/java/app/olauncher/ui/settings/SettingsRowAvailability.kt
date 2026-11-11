package app.olauncher.ui.settings

import androidx.annotation.IdRes
import app.olauncher.R

/**
 * Rows that only make sense while a parent switch is on. Sections and search must not
 * show these while the switch is off (the populate* functions hide them).
 */
object SettingsRowAvailability {

    private val shortcutDependentRows = setOf(
        R.id.shortcutCount,
        R.id.shortcutSize,
        R.id.shortcutSide,
        R.id.shortcutHeight,
        R.id.shortcutIconStyle,
    )

    private val germanDependentRows = setOf(
        R.id.dictionaryApp,
        R.id.germanClock,
        R.id.germanOfflineDictionaryOnOff,
        R.id.germanLookupOnOff,
        R.id.germanSpellSuggestOnOff,
        R.id.germanPauseWordOnOff,
        R.id.germanUiOnOff,
        R.id.germanWebAccount,
        R.id.germanWebProgress,
        R.id.aiPracticeOnOff,
        R.id.askAiConversations,
        R.id.newsSentencesOnOff,
    )

    /**
     * The screen-pick re-enable hint (#590): it follows the hint state the fragment
     * computes ([SettingsFragment.showScreenPickScreenshotHint]), not just a switch, so
     * the row binds with its final visibility. populateGerman used to hide it after the
     * bind, which changed a child's visibility in the middle of the list's layout pass —
     * the one visibility decision in Settings that the adapter did not make at bind time.
     */
    private val germanScreenPickHintRows = setOf(
        R.id.germanScreenPickScreenshot,
    )

    private val windDownDependentRows = setOf(
        R.id.windDownGreyOnOff,
        R.id.systemGreyOnOff,
    )

    private val holdDependentRows = setOf(
        R.id.quietReleaseTimes,
    )

    /** Rows that only make sense while a theme pack is active. */
    private val packDependentRows = setOf(
        R.id.backgroundOpacity,
    )

    fun isAvailable(
        @IdRes viewId: Int,
        shortcutsOn: Boolean,
        germanOn: Boolean,
        windDownOn: Boolean = true,
        holdOn: Boolean = true,
        packOn: Boolean = false,
        germanScreenPickHintOn: Boolean = false,
    ): Boolean = when (viewId) {
        in shortcutDependentRows -> shortcutsOn
        in germanDependentRows -> germanOn
        in germanScreenPickHintRows -> germanOn && germanScreenPickHintOn
        in windDownDependentRows -> windDownOn
        in holdDependentRows -> holdOn
        in packDependentRows -> packOn
        else -> true
    }
}
