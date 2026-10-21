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

    /** The welcome's own button asks for the home role, so the automatic first-open request is skipped. */
    fun shouldAutoRequestHomeRole(welcomeShown: Boolean): Boolean = !welcomeShown

    fun stateFor(saved: String?): OnboardingState = Onboarding.resumedFrom(saved)

    /**
     * Next in the shipped welcome. Step 3 has no screen yet, so Next on step 2 finishes
     * (through [Onboarding.advancedFrom] on the last step). Remove when step 3 ships.
     */
    fun nextPressed(state: OnboardingState): OnboardingState =
        if (state.currentStep == OnboardingStep.PICK_HOME_APPS)
            Onboarding.advancedFrom(state.copy(currentStep = OnboardingStep.values().last()))
        else Onboarding.advancedFrom(state)
}
