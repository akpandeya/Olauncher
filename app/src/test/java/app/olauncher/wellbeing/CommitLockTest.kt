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
