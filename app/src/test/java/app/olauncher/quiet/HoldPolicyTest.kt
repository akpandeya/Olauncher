package app.olauncher.quiet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldPolicyTest {

    private val ownPkg = "app.thumbline.debug"
    private val distractingApps = setOf(
        "com.instagram.android",
        "com.google.android.youtube",
        "com.whatsapp",
        "com.google.android.gm"
    )

    @Test
    fun `distracting app normal notification should be held`() {
        val result = HoldPolicy.shouldHold(
            pkg = "com.instagram.android",
            distracting = distractingApps,
            ownPkg = ownPkg,
            category = null,
            isOngoing = false,
            isForegroundService = false,
            isGroupSummary = false
        )
        assertTrue("Normal notification from distracting app should be held", result)
    }

    @Test
    fun `app not in distracting set should not be held`() {
        val result = HoldPolicy.shouldHold(
            pkg = "com.non.distracting.app",
            distracting = distractingApps,
            ownPkg = ownPkg,
            category = null,
            isOngoing = false,
            isForegroundService = false,
            isGroupSummary = false
        )
        assertFalse("App not in distracting set should not be held", result)
    }

    @Test
    fun `own package notification is never held even if in distracting set`() {
        val distractingWithOwn = distractingApps + ownPkg
        val result = HoldPolicy.shouldHold(
            pkg = ownPkg,
            distracting = distractingWithOwn,
            ownPkg = ownPkg,
            category = null,
            isOngoing = false,
            isForegroundService = false,
            isGroupSummary = false
        )
        assertFalse("Thumbline's own notification must never be held", result)
    }

    @Test
    fun `ongoing notification is never held`() {
        val result = HoldPolicy.shouldHold(
            pkg = "com.instagram.android",
            distracting = distractingApps,
            ownPkg = ownPkg,
            category = null,
            isOngoing = true,
            isForegroundService = false,
            isGroupSummary = false
        )
        assertFalse("Ongoing notification should never be held", result)
    }

    @Test
    fun `foreground service notification is never held`() {
        val result = HoldPolicy.shouldHold(
            pkg = "com.google.android.youtube",
            distracting = distractingApps,
            ownPkg = ownPkg,
            category = null,
            isOngoing = false,
            isForegroundService = true,
            isGroupSummary = false
        )
        assertFalse("Foreground service notification should never be held", result)
    }

    @Test
    fun `call category notification is never held`() {
        val result = HoldPolicy.shouldHold(
            pkg = "com.whatsapp",
            distracting = distractingApps,
            ownPkg = ownPkg,
            category = HoldPolicy.CATEGORY_CALL,
            isOngoing = false,
            isForegroundService = false,
            isGroupSummary = false
        )
        assertFalse("Call category notification should never be held", result)
    }

    @Test
    fun `alarm category notification is never held`() {
        val result = HoldPolicy.shouldHold(
            pkg = "com.google.android.youtube",
            distracting = distractingApps,
            ownPkg = ownPkg,
            category = HoldPolicy.CATEGORY_ALARM,
            isOngoing = false,
            isForegroundService = false,
            isGroupSummary = false
        )
        assertFalse("Alarm category notification should never be held", result)
    }

    @Test
    fun `group summary notification is never held`() {
        val result = HoldPolicy.shouldHold(
            pkg = "com.instagram.android",
            distracting = distractingApps,
            ownPkg = ownPkg,
            category = null,
            isOngoing = false,
            isForegroundService = false,
            isGroupSummary = true
        )
        assertFalse("Group summary notification should not be held directly", result)
    }

    @Test
    fun `other notification categories from distracting apps are held`() {
        val categories = listOf("msg", "social", "promo", "recommendation", "err")
        for (category in categories) {
            val result = HoldPolicy.shouldHold(
                pkg = "com.instagram.android",
                distracting = distractingApps,
                ownPkg = ownPkg,
                category = category,
                isOngoing = false,
                isForegroundService = false,
                isGroupSummary = false
            )
            assertTrue("Category '$category' from distracting app should be held", result)
        }
    }
}
