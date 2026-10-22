package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingTest {

    @Test
    fun stepOrder() {
        assertEquals(
            listOf(
                OnboardingStep.SET_DEFAULT_LAUNCHER,
                OnboardingStep.PICK_HOME_APPS,
                OnboardingStep.PICK_PROFILE,
                OnboardingStep.OPTIONAL_FEATURES,
            ),
            OnboardingStep.values().toList(),
        )
        assertEquals(OnboardingStep.SET_DEFAULT_LAUNCHER, Onboarding.firstStep)
    }

    @Test
    fun initialStateIsFirstStepNotCompleteNotSkipped() {
        val state = Onboarding.initialState()
        assertEquals(OnboardingStep.SET_DEFAULT_LAUNCHER, state.currentStep)
        assertFalse(state.isComplete)
        assertFalse(state.skipped)
    }

    @Test
    fun nextStepAfterFollowsOrder() {
        assertEquals(OnboardingStep.PICK_HOME_APPS, Onboarding.nextStepAfter(OnboardingStep.SET_DEFAULT_LAUNCHER))
        assertEquals(OnboardingStep.PICK_PROFILE, Onboarding.nextStepAfter(OnboardingStep.PICK_HOME_APPS))
        assertEquals(OnboardingStep.OPTIONAL_FEATURES, Onboarding.nextStepAfter(OnboardingStep.PICK_PROFILE))
    }

    @Test
    fun nextStepAfterLastIsNull() {
        assertNull(Onboarding.nextStepAfter(OnboardingStep.OPTIONAL_FEATURES))
    }

    @Test
    fun advancedFromWalksThenCompletesWithoutSkip() {
        var state = Onboarding.initialState()
        state = Onboarding.advancedFrom(state)
        assertEquals(OnboardingStep.PICK_HOME_APPS, state.currentStep)
        state = Onboarding.advancedFrom(state)
        assertEquals(OnboardingStep.PICK_PROFILE, state.currentStep)
        state = Onboarding.advancedFrom(state)
        assertEquals(OnboardingStep.OPTIONAL_FEATURES, state.currentStep)
        assertFalse(state.isComplete)
        state = Onboarding.advancedFrom(state)
        assertTrue(state.isComplete)
        assertFalse(state.skipped)
        assertEquals(state, Onboarding.advancedFrom(state))
    }

    @Test
    fun skippedMarksSkippedAndComplete() {
        val state = Onboarding.skipped(Onboarding.advancedFrom(Onboarding.initialState()))
        assertTrue(state.isComplete)
        assertTrue(state.skipped)
        assertEquals(OnboardingStep.PICK_HOME_APPS, state.currentStep)
    }

    @Test
    fun resumedFromEachValidName() {
        for (step in OnboardingStep.values()) {
            val state = Onboarding.resumedFrom(step.name)
            assertEquals(step, state.currentStep)
            assertFalse(state.isComplete)
            assertFalse(state.skipped)
        }
    }

    @Test
    fun resumedFromBlankOrUnknownFallsBackToFirstStep() {
        for (saved in listOf(null, "", "   ", "NOPE", "pick_home_apps", " PICK_HOME_APPS")) {
            assertEquals(Onboarding.initialState(), Onboarding.resumedFrom(saved))
        }
    }
}
