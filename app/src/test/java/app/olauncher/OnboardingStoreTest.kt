package app.olauncher

import app.olauncher.helper.Onboarding
import app.olauncher.helper.OnboardingStep
import app.olauncher.helper.OnboardingStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingStoreTest {

    @Test
    fun nextAndSkipBothEncodeAsComplete() {
        val start = Onboarding.initialState()
        val lastStep = start.copy(currentStep = OnboardingStep.OPTIONAL_FEATURES)
        assertEquals(OnboardingStore.COMPLETE, OnboardingStore.encode(Onboarding.advancedFrom(lastStep)))
        assertEquals(OnboardingStore.COMPLETE, OnboardingStore.encode(Onboarding.skipped(start)))
    }

    @Test
    fun inProgressEncodesStepName() {
        assertEquals("PICK_HOME_APPS", OnboardingStore.encode(Onboarding.advancedFrom(Onboarding.initialState())))
    }

    @Test
    fun showsOnFreshInstallOnly() {
        assertTrue(OnboardingStore.shouldShow(null, isFirstOpen = true))
        assertTrue(OnboardingStore.shouldShow("", isFirstOpen = true))
        assertFalse(OnboardingStore.shouldShow(null, isFirstOpen = false))
        assertFalse(OnboardingStore.shouldShow(OnboardingStore.COMPLETE, isFirstOpen = true))
        assertTrue(OnboardingStore.shouldShow("SET_DEFAULT_LAUNCHER", isFirstOpen = false))
    }

    @Test
    fun autoHomeRoleRequestSkippedWhenWelcomeShown() {
        assertFalse(OnboardingStore.shouldAutoRequestHomeRole(welcomeShown = true))
        assertTrue(OnboardingStore.shouldAutoRequestHomeRole(welcomeShown = false))
    }

    @Test
    fun resumeFromSavedStep() {
        assertEquals(OnboardingStep.PICK_HOME_APPS, OnboardingStore.stateFor("PICK_HOME_APPS").currentStep)
        assertEquals(OnboardingStep.SET_DEFAULT_LAUNCHER, OnboardingStore.stateFor("junk").currentStep)
    }

    @Test
    fun step1NextSavesPickHomeApps() {
        val next = OnboardingStore.nextPressed(Onboarding.initialState())
        assertEquals(OnboardingStep.PICK_HOME_APPS, next.currentStep)
        assertEquals("PICK_HOME_APPS", OnboardingStore.encode(next))
    }

    @Test
    fun step2NextAndSkipBothSaveComplete() {
        val step2 = OnboardingStore.stateFor("PICK_HOME_APPS")
        assertEquals(OnboardingStore.COMPLETE, OnboardingStore.encode(OnboardingStore.nextPressed(step2)))
        assertEquals(OnboardingStore.COMPLETE, OnboardingStore.encode(Onboarding.skipped(step2)))
    }

    @Test
    fun step2NextIsNotSkipped() {
        assertFalse(OnboardingStore.nextPressed(OnboardingStore.stateFor("PICK_HOME_APPS")).skipped)
    }
}
