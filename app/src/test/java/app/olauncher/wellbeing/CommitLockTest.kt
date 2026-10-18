package app.olauncher.wellbeing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommitLockTest {

    @Test
    fun `waitSeconds constant equals 30`() {
        assertEquals(30, CommitLock.WAIT_SECONDS)
    }

    @Test
    fun `when lock is off needsWait is false for all changes`() {
        assertFalse(CommitLock.needsWait(lockOn = false, FrictionChange.Pause(enabled = false)))
        assertFalse(CommitLock.needsWait(lockOn = false, FrictionChange.Pause(enabled = true)))

        assertFalse(CommitLock.needsWait(lockOn = false, FrictionChange.DistractingApps(setOf("app1"), emptySet())))
        assertFalse(CommitLock.needsWait(lockOn = false, FrictionChange.DistractingApps(emptySet(), setOf("app1"))))
        assertFalse(CommitLock.needsWait(lockOn = false, FrictionChange.RemoveDistractingApp("app1")))

        assertFalse(CommitLock.needsWait(lockOn = false, FrictionChange.Allowance(15, 30)))
        assertFalse(CommitLock.needsWait(lockOn = false, FrictionChange.Allowance(30, 0)))
        assertFalse(CommitLock.needsWait(lockOn = false, FrictionChange.Allowance(30, 15)))
        assertFalse(CommitLock.needsWait(lockOn = false, FrictionChange.Allowance(0, 15)))

        assertFalse(CommitLock.needsWait(lockOn = false, FrictionChange.Escalate(enabled = false)))
        assertFalse(CommitLock.needsWait(lockOn = false, FrictionChange.Escalate(enabled = true)))

        assertFalse(CommitLock.needsWait(lockOn = false, FrictionChange.AskWhy(enabled = false)))
        assertFalse(CommitLock.needsWait(lockOn = false, FrictionChange.AskWhy(enabled = true)))

        assertFalse(CommitLock.needsWait(lockOn = false, FrictionChange.Lock(enabled = false)))
        assertFalse(CommitLock.needsWait(lockOn = false, FrictionChange.Lock(enabled = true)))
    }

    @Test
    fun `pause off needs wait but pause on does not`() {
        assertTrue(CommitLock.needsWait(lockOn = true, FrictionChange.Pause(enabled = false)))
        assertFalse(CommitLock.needsWait(lockOn = true, FrictionChange.Pause(enabled = true)))
    }

    @Test
    fun `removing distracting app needs wait but adding does not`() {
        // Removing an app from the set
        assertTrue(
            CommitLock.needsWait(
                lockOn = true,
                FrictionChange.DistractingApps(setOf("pkg1", "pkg2"), setOf("pkg1"))
            )
        )
        // Removing all apps
        assertTrue(
            CommitLock.needsWait(
                lockOn = true,
                FrictionChange.DistractingApps(setOf("pkg1"), emptySet())
            )
        )
        // RemoveDistractingApp single helper
        assertTrue(
            CommitLock.needsWait(
                lockOn = true,
                FrictionChange.RemoveDistractingApp("pkg1")
            )
        )
        // Swapping an app still removes an app
        assertTrue(
            CommitLock.needsWait(
                lockOn = true,
                FrictionChange.DistractingApps(setOf("pkg1"), setOf("pkg2"))
            )
        )

        // Adding an app (tightening) needs no wait
        assertFalse(
            CommitLock.needsWait(
                lockOn = true,
                FrictionChange.DistractingApps(setOf("pkg1"), setOf("pkg1", "pkg2"))
            )
        )
        assertFalse(
            CommitLock.needsWait(
                lockOn = true,
                FrictionChange.DistractingApps(emptySet(), setOf("pkg1"))
            )
        )

        // Keeping same apps needs no wait
        assertFalse(
            CommitLock.needsWait(
                lockOn = true,
                FrictionChange.DistractingApps(setOf("pkg1"), setOf("pkg1"))
            )
        )
        assertFalse(
            CommitLock.needsWait(
                lockOn = true,
                FrictionChange.DistractingApps(emptySet(), emptySet())
            )
        )
    }

    @Test
    fun `raising or removing allowance needs wait but lowering or adding does not`() {
        // Raising allowance (loosening)
        assertTrue(CommitLock.needsWait(lockOn = true, FrictionChange.Allowance(currentMin = 15, updatedMin = 30)))
        assertTrue(CommitLock.needsWait(lockOn = true, FrictionChange.Allowance(currentMin = 30, updatedMin = 60)))
        assertTrue(CommitLock.needsWait(lockOn = true, FrictionChange.Allowance(currentMin = 15, updatedMin = 120)))

        // Removing allowance (loosening: turning off)
        assertTrue(CommitLock.needsWait(lockOn = true, FrictionChange.Allowance(currentMin = 15, updatedMin = 0)))
        assertTrue(CommitLock.needsWait(lockOn = true, FrictionChange.Allowance(currentMin = 60, updatedMin = 0)))

        // Lowering allowance (tightening: friction happens sooner)
        assertFalse(CommitLock.needsWait(lockOn = true, FrictionChange.Allowance(currentMin = 60, updatedMin = 30)))
        assertFalse(CommitLock.needsWait(lockOn = true, FrictionChange.Allowance(currentMin = 30, updatedMin = 15)))
        assertFalse(CommitLock.needsWait(lockOn = true, FrictionChange.Allowance(currentMin = 120, updatedMin = 15)))

        // Adding allowance when none existed (tightening: turning on from 0)
        assertFalse(CommitLock.needsWait(lockOn = true, FrictionChange.Allowance(currentMin = 0, updatedMin = 15)))
        assertFalse(CommitLock.needsWait(lockOn = true, FrictionChange.Allowance(currentMin = 0, updatedMin = 60)))

        // Same allowance
        assertFalse(CommitLock.needsWait(lockOn = true, FrictionChange.Allowance(currentMin = 30, updatedMin = 30)))
        assertFalse(CommitLock.needsWait(lockOn = true, FrictionChange.Allowance(currentMin = 0, updatedMin = 0)))
    }

    @Test
    fun `escalation off needs wait but escalation on does not`() {
        assertTrue(CommitLock.needsWait(lockOn = true, FrictionChange.Escalate(enabled = false)))
        assertFalse(CommitLock.needsWait(lockOn = true, FrictionChange.Escalate(enabled = true)))
    }

    @Test
    fun `ask-why off needs wait but ask-why on does not`() {
        assertTrue(CommitLock.needsWait(lockOn = true, FrictionChange.AskWhy(enabled = false)))
        assertFalse(CommitLock.needsWait(lockOn = true, FrictionChange.AskWhy(enabled = true)))
    }

    @Test
    fun `lock off needs wait but lock on does not`() {
        assertTrue(CommitLock.needsWait(lockOn = true, FrictionChange.Lock(enabled = false)))
        assertFalse(CommitLock.needsWait(lockOn = true, FrictionChange.Lock(enabled = true)))
    }

    @Test
    fun `isLoosening and isTightening helper methods`() {
        assertTrue(CommitLock.isLoosening(FrictionChange.Pause(false)))
        assertFalse(CommitLock.isTightening(FrictionChange.Pause(false)))

        assertFalse(CommitLock.isLoosening(FrictionChange.Pause(true)))
        assertTrue(CommitLock.isTightening(FrictionChange.Pause(true)))

        assertTrue(CommitLock.isLoosening(FrictionChange.Allowance(15, 30)))
        assertFalse(CommitLock.isTightening(FrictionChange.Allowance(15, 30)))

        assertFalse(CommitLock.isLoosening(FrictionChange.Allowance(30, 15)))
        assertTrue(CommitLock.isTightening(FrictionChange.Allowance(30, 15)))
    }
}

class CommitLockRestoreTest {

    private val strict = FrictionSettings(
        pauseEnabled = true,
        distractingApps = setOf("com.a", "com.b"),
        allowanceMin = 45,
        escalate = true,
        askWhy = true,
        commitLock = true,
    )

    @Test
    fun `restore that loosens any friction setting needs a wait when lock is on`() {
        val loosened = listOf(
            strict.copy(pauseEnabled = false),
            strict.copy(distractingApps = setOf("com.a")),
            strict.copy(allowanceMin = 0),
            strict.copy(allowanceMin = 60),
            strict.copy(escalate = false),
            strict.copy(askWhy = false),
            strict.copy(commitLock = false),
        )
        for (after in loosened) {
            assertTrue("expected wait for $after", CommitLock.restoreNeedsWait(true, strict, after))
        }
    }

    @Test
    fun `restore needs no wait when lock is off or friction is unchanged or tightened`() {
        val off = strict.copy(pauseEnabled = false, allowanceMin = 0, escalate = false)
        assertFalse(CommitLock.restoreNeedsWait(false, strict, off))
        assertFalse(CommitLock.restoreNeedsWait(true, strict, strict))
        assertFalse(CommitLock.restoreNeedsWait(true, off, strict))
        assertFalse(CommitLock.restoreNeedsWait(true, off, off))
        assertFalse(CommitLock.restoreNeedsWait(true, strict, strict.copy(distractingApps = setOf("com.a", "com.b", "com.c"), allowanceMin = 30)))
    }

    @Test
    fun `settings parsed from backup prefs use distinguishing values and defaults for missing keys`() {
        val parsed = FrictionSettings.fromPrefsMap(
            mapOf(
                "PAUSE_ENABLED" to true,
                "DISTRACTING_APPS" to setOf("x.y", "z.w"),
                "PAUSE_ALLOWANCE_MIN" to 75L,
                "PAUSE_ASK_WHY" to true,
            )
        )
        assertEquals(FrictionSettings(true, setOf("x.y", "z.w"), 75, escalate = false, askWhy = true, commitLock = false), parsed)
        assertEquals(FrictionSettings(false, emptySet(), 0, false, false, false), FrictionSettings.fromPrefsMap(emptyMap()))
    }

    @Test
    fun `backup restoring an empty prefs map loosens a strict setup`() {
        assertTrue(CommitLock.restoreNeedsWait(true, strict, FrictionSettings.fromPrefsMap(emptyMap())))
    }

    @Test
    fun `friction keys match Prefs constants`() {
        assertEquals(app.olauncher.data.Prefs.PAUSE_ALLOWANCE_MIN, FrictionSettings.KEY_ALLOWANCE_MIN)
        assertEquals(app.olauncher.data.Prefs.PAUSE_ESCALATE, FrictionSettings.KEY_ESCALATE)
        assertEquals(app.olauncher.data.Prefs.PAUSE_ASK_WHY, FrictionSettings.KEY_ASK_WHY)
        assertEquals(app.olauncher.data.Prefs.COMMIT_LOCK, FrictionSettings.KEY_COMMIT_LOCK)
    }
}
