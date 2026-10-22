package app.olauncher.helper

/** First-run welcome steps, declared in the order they are shown. */
enum class OnboardingStep {
    SET_DEFAULT_LAUNCHER,
    PICK_HOME_APPS,
    PICK_PROFILE,
    OPTIONAL_FEATURES,
}

/**
 * Snapshot of first-run progress.
 *
 * @property currentStep the step to show (the last shown step once complete).
 * @property isComplete true when onboarding is finished or skipped.
 * @property skipped true when the user skipped instead of finishing.
 */
data class OnboardingState(
    val currentStep: OnboardingStep,
    val isComplete: Boolean,
    val skipped: Boolean,
)

/** Pure, Context-free first-run step logic. Every function returns a new state. */
object Onboarding {

    val firstStep: OnboardingStep = OnboardingStep.values().first()

    fun initialState(): OnboardingState =
        OnboardingState(currentStep = firstStep, isComplete = false, skipped = false)

    /** The step after [step], or null when [step] is the last one. */
    fun nextStepAfter(step: OnboardingStep): OnboardingStep? =
        OnboardingStep.values().getOrNull(step.ordinal + 1)

    /** Moves [state] to the next step, or marks it complete (not skipped) after the last step. */
    fun advancedFrom(state: OnboardingState): OnboardingState {
        if (state.isComplete) return state
        val next = nextStepAfter(state.currentStep)
            ?: return state.copy(isComplete = true)
        return state.copy(currentStep = next)
    }

    /** Marks [state] as skipped and complete. */
    fun skipped(state: OnboardingState): OnboardingState =
        state.copy(isComplete = true, skipped = true)

    /**
     * Resumes at the step saved under [savedStepName] (an [OnboardingStep] name).
     * Null, blank or unknown values fall back to the first step.
     */
    fun resumedFrom(savedStepName: String?): OnboardingState {
        val step = OnboardingStep.values().firstOrNull { it.name == savedStepName }
            ?: return initialState()
        return initialState().copy(currentStep = step)
    }
}
