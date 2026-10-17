package app.olauncher.wellbeing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DistractingAppsTest {

    @Test
    fun `default candidate packages contains expected social apps`() {
        val expected = setOf(
            "com.instagram.android",
            "com.zhiliaoapp.musically",
            "com.google.android.youtube",
            "com.reddit.frontpage",
            "com.twitter.android",
            "com.facebook.katana",
            "com.snapchat.android",
        )
        assertEquals(expected, DistractingApps.DEFAULT_PACKAGES)
    }

    @Test
    fun `initialSelection on first open with empty saved pre-ticks installed defaults`() {
        val installed = setOf(
            "com.instagram.android",
            "com.google.android.youtube",
            "com.example.calculator",
            "com.example.notes"
        )
        val selected = DistractingApps.initialSelection(installed, emptySet(), hasSaved = false)
        assertEquals(
            setOf("com.instagram.android", "com.google.android.youtube"),
            selected
        )
    }

    @Test
    fun `initialSelection with saved choices returns saved apps that are installed`() {
        val installed = setOf(
            "com.instagram.android",
            "com.example.customapp",
            "com.google.android.youtube"
        )
        val saved = setOf("com.example.customapp")
        val selected = DistractingApps.initialSelection(installed, saved, hasSaved = true)
        assertEquals(setOf("com.example.customapp"), selected)
    }

    @Test
    fun `initialSelection when user explicitly saved empty set returns empty set`() {
        val installed = setOf("com.instagram.android", "com.google.android.youtube")
        val selected = DistractingApps.initialSelection(installed, emptySet(), hasSaved = true)
        assertTrue(selected.isEmpty())
    }

    @Test
    fun `isDistracting returns true if package is in set`() {
        val distracting = setOf("com.instagram.android", "com.reddit.frontpage")
        assertTrue(DistractingApps.isDistracting("com.instagram.android", distracting))
        assertFalse(DistractingApps.isDistracting("com.example.notes", distracting))
    }

    @Test
    fun `shouldPauseLaunch respects pauseEnabled flag`() {
        val distracting = setOf("com.instagram.android")
        assertFalse(DistractingApps.shouldPauseLaunch("com.instagram.android", pauseEnabled = false, distracting))
        assertTrue(DistractingApps.shouldPauseLaunch("com.instagram.android", pauseEnabled = true, distracting))
    }

    @Test
    fun `shouldPauseLaunch returns false for null or non-distracting package`() {
        val distracting = setOf("com.instagram.android")
        assertFalse(DistractingApps.shouldPauseLaunch(null, pauseEnabled = true, distracting))
        assertFalse(DistractingApps.shouldPauseLaunch("com.example.notes", pauseEnabled = true, distracting))
    }

    @Test
    fun `shouldPauseLaunch bypasses when package matches one-shot bypassPackage`() {
        val distracting = setOf("com.instagram.android")
        assertFalse(
            DistractingApps.shouldPauseLaunch(
                pkg = "com.instagram.android",
                pauseEnabled = true,
                distractingApps = distracting,
                bypassPackage = "com.instagram.android"
            )
        )
        assertTrue(
            DistractingApps.shouldPauseLaunch(
                pkg = "com.instagram.android",
                pauseEnabled = true,
                distractingApps = distracting,
                bypassPackage = "com.other.package"
            )
        )
    }
}
