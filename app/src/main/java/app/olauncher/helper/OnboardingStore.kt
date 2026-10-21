package app.olauncher.helper

/**
 * Context-free save format for [OnboardingState], kept in one string pref.
 * The value is an [OnboardingStep] name while in progress, or [COMPLETE].
 */
object OnboardingStore {

    const val COMPLETE = "COMPLETE"

    fun encode(state: OnboardingState): String =
        if (state.isComplete) COMPLETE else state.currentStep.name

    /** True when [saved] marks onboarding finished or skipped. */
    fun isComplete(saved: String?): Boolean = saved == COMPLETE

    /** Existing installs (no saved value, not a first open) never see the welcome. */
    fun shouldShow(saved: String?, isFirstOpen: Boolean): Boolean {
        if (isComplete(saved)) return false
        return saved.isNullOrEmpty().not() || isFirstOpen
    }

    fun stateFor(saved: String?): OnboardingState = Onboarding.resumedFrom(saved)
}
