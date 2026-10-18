package app.olauncher.wellbeing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PausePolicyTest {

    @Test
    fun `base delay only without escalation or allowance`() {
        val decision = PausePolicy.delaySeconds(
            base = 5,
            opensToday = 1,
            minutesToday = 0,
            allowanceMin = 0,
            escalate = false,
        )
        assertEquals(5, decision.seconds)
        assertFalse(decision.overAllowance)

        val zeroOpens = PausePolicy.delaySeconds(
            base = 5,
            opensToday = 0,
            minutesToday = 0,
            allowanceMin = 0,
            escalate = false,
        )
        assertEquals(5, zeroOpens.seconds)
        assertFalse(zeroOpens.overAllowance)

        val customBase = PausePolicy.delaySeconds(
            base = 10,
            opensToday = 10,
            minutesToday = 50,
            allowanceMin = 0,
            escalate = false,
        )
        assertEquals(10, customBase.seconds)
        assertFalse(customBase.overAllowance)
    }

    @Test
    fun `escalation steps and cap`() {
        // First 3 opens do not add delay
        val open1 = PausePolicy.delaySeconds(base = 5, opensToday = 1, minutesToday = 0, allowanceMin = 0, escalate = true)
        assertEquals(5, open1.seconds)
        assertFalse(open1.overAllowance)

        val open2 = PausePolicy.delaySeconds(base = 5, opensToday = 2, minutesToday = 0, allowanceMin = 0, escalate = true)
        assertEquals(5, open2.seconds)
        assertFalse(open2.overAllowance)

        val open3 = PausePolicy.delaySeconds(base = 5, opensToday = 3, minutesToday = 0, allowanceMin = 0, escalate = true)
        assertEquals(5, open3.seconds)
        assertFalse(open3.overAllowance)

        // 4th open -> base + 2 s
        val open4 = PausePolicy.delaySeconds(base = 5, opensToday = 4, minutesToday = 0, allowanceMin = 0, escalate = true)
        assertEquals(7, open4.seconds)
        assertFalse(open4.overAllowance)

        // 5th open -> base + 4 s
        val open5 = PausePolicy.delaySeconds(base = 5, opensToday = 5, minutesToday = 0, allowanceMin = 0, escalate = true)
        assertEquals(9, open5.seconds)
        assertFalse(open5.overAllowance)

        // 6th open -> base + 6 s
        val open6 = PausePolicy.delaySeconds(base = 5, opensToday = 6, minutesToday = 0, allowanceMin = 0, escalate = true)
        assertEquals(11, open6.seconds)
        assertFalse(open6.overAllowance)

        // 15th open -> 5 + (12 * 2) = 29 s
        val open15 = PausePolicy.delaySeconds(base = 5, opensToday = 15, minutesToday = 0, allowanceMin = 0, escalate = true)
        assertEquals(29, open15.seconds)
        assertFalse(open15.overAllowance)

        // 16th open -> 5 + (13 * 2) = 31 s -> capped at 30 s
        val open16 = PausePolicy.delaySeconds(base = 5, opensToday = 16, minutesToday = 0, allowanceMin = 0, escalate = true)
        assertEquals(30, open16.seconds)
        assertFalse(open16.overAllowance)

        // Very high open count capped at 30 s
        val open50 = PausePolicy.delaySeconds(base = 5, opensToday = 50, minutesToday = 0, allowanceMin = 0, escalate = true)
        assertEquals(30, open50.seconds)
        assertFalse(open50.overAllowance)
    }

    @Test
    fun `over allowance without escalation`() {
        // Base 5, allowance 15 min, minutes 15 -> max(5, 15) * 2 = 30 s
        val exactAllowance = PausePolicy.delaySeconds(
            base = 5,
            opensToday = 1,
            minutesToday = 15,
            allowanceMin = 15,
            escalate = false,
        )
        assertEquals(30, exactAllowance.seconds)
        assertTrue(exactAllowance.overAllowance)

        // Base 5, allowance 15 min, minutes 14 -> under allowance
        val underAllowance = PausePolicy.delaySeconds(
            base = 5,
            opensToday = 1,
            minutesToday = 14,
            allowanceMin = 15,
            escalate = false,
        )
        assertEquals(5, underAllowance.seconds)
        assertFalse(underAllowance.overAllowance)

        // Base 20, allowance 30 min, minutes 35 -> max(20, 15) * 2 = 40 s
        val aboveBaseFloor = PausePolicy.delaySeconds(
            base = 20,
            opensToday = 2,
            minutesToday = 35,
            allowanceMin = 30,
            escalate = false,
        )
        assertEquals(40, aboveBaseFloor.seconds)
        assertTrue(aboveBaseFloor.overAllowance)

        // High base (35) over allowance -> max(35, 15) * 2 = 70 -> capped at 60 s
        val cappedAt60 = PausePolicy.delaySeconds(
            base = 35,
            opensToday = 1,
            minutesToday = 30,
            allowanceMin = 30,
            escalate = false,
        )
        assertEquals(60, cappedAt60.seconds)
        assertTrue(cappedAt60.overAllowance)
    }

    @Test
    fun `over allowance with escalation`() {
        // 4th open (escalated = 7 s), over 15 min allowance -> max(7, 15) * 2 = 30 s
        val open4Over = PausePolicy.delaySeconds(
            base = 5,
            opensToday = 4,
            minutesToday = 15,
            allowanceMin = 15,
            escalate = true,
        )
        assertEquals(30, open4Over.seconds)
        assertTrue(open4Over.overAllowance)

        // 6th open (escalated = 11 s), over 15 min allowance -> max(11, 15) * 2 = 30 s
        val open6Over = PausePolicy.delaySeconds(
            base = 5,
            opensToday = 6,
            minutesToday = 20,
            allowanceMin = 15,
            escalate = true,
        )
        assertEquals(30, open6Over.seconds)
        assertTrue(open6Over.overAllowance)

        // 10th open (escalated = 5 + 7*2 = 19 s), over allowance -> max(19, 15) * 2 = 38 s
        val open10Over = PausePolicy.delaySeconds(
            base = 5,
            opensToday = 10,
            minutesToday = 30,
            allowanceMin = 30,
            escalate = true,
        )
        assertEquals(38, open10Over.seconds)
        assertTrue(open10Over.overAllowance)

        // 16th open (escalation capped at 30 s), over allowance -> max(30, 15) * 2 = 60 s
        val open16Over = PausePolicy.delaySeconds(
            base = 5,
            opensToday = 16,
            minutesToday = 30,
            allowanceMin = 30,
            escalate = true,
        )
        assertEquals(60, open16Over.seconds)
        assertTrue(open16Over.overAllowance)

        // 30th open (escalation capped at 30 s), over allowance -> 60 s cap maintained
        val open30Over = PausePolicy.delaySeconds(
            base = 5,
            opensToday = 30,
            minutesToday = 45,
            allowanceMin = 30,
            escalate = true,
        )
        assertEquals(60, open30Over.seconds)
        assertTrue(open30Over.overAllowance)
    }

    @Test
    fun `null minutesToday ignores allowance`() {
        val decisionNoEscalate = PausePolicy.delaySeconds(
            base = 5,
            opensToday = 2,
            minutesToday = null,
            allowanceMin = 15,
            escalate = false,
        )
        assertEquals(5, decisionNoEscalate.seconds)
        assertFalse(decisionNoEscalate.overAllowance)

        val decisionWithEscalate = PausePolicy.delaySeconds(
            base = 5,
            opensToday = 6,
            minutesToday = null,
            allowanceMin = 15,
            escalate = true,
        )
        assertEquals(11, decisionWithEscalate.seconds)
        assertFalse(decisionWithEscalate.overAllowance)
    }

    @Test
    fun `allowance 0 is off and ignores minutes`() {
        val decisionNoEscalate = PausePolicy.delaySeconds(
            base = 5,
            opensToday = 2,
            minutesToday = 120,
            allowanceMin = 0,
            escalate = false,
        )
        assertEquals(5, decisionNoEscalate.seconds)
        assertFalse(decisionNoEscalate.overAllowance)

        val decisionWithEscalate = PausePolicy.delaySeconds(
            base = 5,
            opensToday = 6,
            minutesToday = 120,
            allowanceMin = 0,
            escalate = true,
        )
        assertEquals(11, decisionWithEscalate.seconds)
        assertFalse(decisionWithEscalate.overAllowance)
    }

    @Test
    fun `fragment path feeds the Nth open so the 4th open escalates`() {
        val earlierOpens = 3
        val nth = PausePolicy.opensIncludingThisOne(earlierOpens)
        assertEquals(4, nth)
        val viaFragmentPath = PausePolicy.delaySeconds(
            base = 5, opensToday = nth, minutesToday = null, allowanceMin = 0, escalate = true,
        )
        assertEquals(7, viaFragmentPath.seconds)
        val rawEarlierCount = PausePolicy.delaySeconds(
            base = 5, opensToday = earlierOpens, minutesToday = null, allowanceMin = 0, escalate = true,
        )
        assertEquals(5, rawEarlierCount.seconds)
    }

    @Test
    fun `opensIncludingThisOne is one for the first open and ignores negatives`() {
        assertEquals(1, PausePolicy.opensIncludingThisOne(0))
        assertEquals(1, PausePolicy.opensIncludingThisOne(-2))
        assertEquals(12, PausePolicy.opensIncludingThisOne(11))
    }

    @Test
    fun `effective allowance is off without usage access and keeps the saved value with it`() {
        assertEquals(0, PausePolicy.effectiveAllowanceMin(savedMin = 45, usageAccessGranted = false))
        assertEquals(45, PausePolicy.effectiveAllowanceMin(savedMin = 45, usageAccessGranted = true))
        assertEquals(0, PausePolicy.effectiveAllowanceMin(savedMin = -3, usageAccessGranted = true))
    }

    @Test
    fun `revoked usage access means the saved allowance does not push the pause over allowance`() {
        val effective = PausePolicy.effectiveAllowanceMin(savedMin = 15, usageAccessGranted = false)
        val decision = PausePolicy.delaySeconds(
            base = 5, opensToday = 1, minutesToday = 200, allowanceMin = effective, escalate = false,
        )
        assertFalse(decision.overAllowance)
        assertEquals(5, decision.seconds)
    }
}
