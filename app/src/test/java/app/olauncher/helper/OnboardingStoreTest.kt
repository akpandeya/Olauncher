package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingStoreTest {

    private fun at(step: OnboardingStep) = Onboarding.initialState().copy(currentStep = step)

    @Test
    fun defaultHeldMovesStepOneToStepTwo() {
        val next = OnboardingStore.advancedIfDefault(at(OnboardingStep.SET_DEFAULT_LAUNCHER), true)
        assertEquals(at(OnboardingStep.PICK_HOME_APPS), next)
    }

    @Test
    fun notDefaultOrUnknownStaysOnStepOne() {
        val state = at(OnboardingStep.SET_DEFAULT_LAUNCHER)
        assertEquals(state, OnboardingStore.advancedIfDefault(state, false))
        assertEquals(state, OnboardingStore.advancedIfDefault(state, null))
    }

    @Test
    fun laterStepsAndFinishedStateAreUntouched() {
        val pick = at(OnboardingStep.PICK_HOME_APPS)
        assertEquals(pick, OnboardingStore.advancedIfDefault(pick, true))
        val skipped = Onboarding.skipped(at(OnboardingStep.SET_DEFAULT_LAUNCHER))
        assertEquals(skipped, OnboardingStore.advancedIfDefault(skipped, true))
    }

    @Test
    fun savedStepResumesAndSkipNeverShowsAgain() {
        assertEquals(at(OnboardingStep.OPTIONAL_FEATURES), OnboardingStore.stateFor("OPTIONAL_FEATURES"))
        val saved = OnboardingStore.encode(Onboarding.skipped(at(OnboardingStep.PICK_HOME_APPS)))
        assertEquals(OnboardingStore.COMPLETE, saved)
        assertEquals(false, OnboardingStore.shouldShow(saved, isFirstOpen = false))
        assertEquals(false, OnboardingStore.shouldShow(saved, isFirstOpen = true))
    }
}
