package app.olauncher.ui.words

import app.olauncher.data.store.DrillRoundPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pins the drill-screen motion design language (#640): the token durations, the easing
 * curve and the reduced-motion behaviour come from [DrillMotion] and the approved mock
 * `docs/design/drills-motion-mock.html`. A regression here must fail CI.
 */
class DrillMotionDesignLanguageTest {

    private fun resolveFile(relative: String): File {
        return listOf(
            File(relative),
            File("app/$relative"),
        ).firstOrNull { it.isFile } ?: error("File $relative not found from ${File(".").absolutePath}")
    }

    @Test
    fun `the motion token durations match the approved mock`() {
        assertEquals(140L, DrillMotion.EXIT_MS)
        assertEquals(220L, DrillMotion.ENTER_MS)
        assertEquals(180L, DrillMotion.POP_MS)
        assertEquals(320L, DrillMotion.SHAKE_MS)
        assertEquals(450L, DrillMotion.FLASH_MS)
        assertEquals(200L, DrillMotion.RISE_MS)
        assertEquals(240L, DrillMotion.ROW_MS)
        assertEquals(50L, DrillMotion.STAGGER_MS)
        assertEquals(120L, DrillMotion.SUMMARY_BASE_DELAY_MS)
        assertEquals(500L, DrillMotion.AUTO_ADVANCE_MS)
        assertEquals(700L, DrillMotion.SUMMARY_BUDGET_MS)
    }

    @Test
    fun `the motion geometry matches the approved mock`() {
        assertEquals(24f, DrillMotion.SLIDE_X_DP)
        assertEquals(7f, DrillMotion.SHAKE_X_DP)
        assertEquals(10f, DrillMotion.RISE_Y_DP)
        assertEquals(1.12f, DrillMotion.POP_MAX_SCALE)
        assertEquals(0.45f, DrillMotion.DIMMED_ALPHA)
    }

    @Test
    fun `the easing curve is cubic-bezier of 02 0 0 1`() {
        assertEquals(0.2f, DrillMotion.EASE_CONTROL_X1)
        assertEquals(0f, DrillMotion.EASE_CONTROL_Y1)
        assertEquals(0f, DrillMotion.EASE_CONTROL_X2)
        assertEquals(1f, DrillMotion.EASE_CONTROL_Y2)
    }

    @Test
    fun `full motion translates, scales and shakes`() {
        val spec = DrillMotion.spec(reducedMotion = false)
        assertFalse(spec.reducedMotion)
        assertTrue(spec.translates)
        assertTrue(spec.scales)
        assertTrue(spec.shakes)
        assertEquals(DrillMotion.EXIT_MS, spec.exitMs)
        assertEquals(DrillMotion.ENTER_MS, spec.enterMs)
        assertEquals(DrillMotion.POP_MS, spec.popMs)
        assertEquals(DrillMotion.SHAKE_MS, spec.shakeMs)
        assertEquals(DrillMotion.FLASH_MS, spec.flashMs)
        assertEquals(DrillMotion.RISE_MS, spec.riseMs)
        assertEquals(DrillMotion.ROW_MS, spec.rowMs)
        assertEquals(DrillMotion.STAGGER_MS, spec.staggerMs)
        assertEquals(DrillMotion.AUTO_ADVANCE_MS, spec.autoAdvanceMs)
    }

    @Test
    fun `reduced motion drops translation, shake and scale but keeps short opacity fades`() {
        val spec = DrillMotion.spec(reducedMotion = true)
        assertTrue(spec.reducedMotion)
        assertFalse("reduced motion must remove every translation", spec.translates)
        assertFalse("reduced motion must remove every scale", spec.scales)
        assertFalse("reduced motion must remove every shake", spec.shakes)
        // Short opacity-only fades stay: positive, shorter than the full durations.
        assertEquals(80L, spec.exitMs)
        assertEquals(120L, spec.enterMs)
        assertEquals(100L, spec.riseMs)
        assertEquals(120L, spec.rowMs)
        assertEquals(220L, spec.flashMs)
        assertTrue(spec.exitMs < DrillMotion.EXIT_MS)
        assertTrue(spec.enterMs < DrillMotion.ENTER_MS)
        assertTrue(spec.riseMs < DrillMotion.RISE_MS)
        assertTrue(spec.rowMs < DrillMotion.ROW_MS)
        assertEquals("reduced motion has no stagger", 0L, spec.staggerMs)
        assertEquals("the auto-advance beat is a timing, not a motion", DrillMotion.AUTO_ADVANCE_MS, spec.autoAdvanceMs)
    }

    @Test
    fun `the shake path leaves and returns to rest`() {
        val fractions = DrillMotion.shakeFractions()
        assertEquals(-1f, fractions.first())
        assertEquals(1f, fractions[1])
        assertEquals(-0.6f, fractions[2])
        assertEquals(0.6f, fractions.last())
    }

    @Test
    fun `summary rows rise in a strict stagger with the buttons in their own last slot`() {
        val spec = DrillMotion.spec(reducedMotion = false)
        assertEquals(120L, DrillMotion.summaryRowDelayMs(0, 5, spec))
        assertEquals(120L + DrillMotion.STAGGER_MS, DrillMotion.summaryRowDelayMs(1, 5, spec))
        for (rows in DrillRoundPlanner.MIN_ROUND_SIZE..DrillRoundPlanner.MAX_ROUND_SIZE) {
            assertTrue(
                "buttons must come after every row ($rows rows)",
                DrillMotion.summaryButtonsDelayMs(rows, spec) > DrillMotion.summaryRowDelayMs(rows - 1, rows, spec),
            )
        }
    }

    @Test
    fun `the summary entrance stays under the budget for the largest round`() {
        val full = DrillMotion.spec(reducedMotion = false)
        val reduced = DrillMotion.spec(reducedMotion = true)
        for (rows in DrillRoundPlanner.MIN_ROUND_SIZE..DrillRoundPlanner.MAX_ROUND_SIZE) {
            assertTrue(
                "full-motion summary with $rows rows must fit the ~700ms budget",
                DrillMotion.summaryTotalMs(rows, full) <= DrillMotion.SUMMARY_BUDGET_MS,
            )
            assertTrue(
                "reduced-motion summary with $rows rows must fit the ~700ms budget",
                DrillMotion.summaryTotalMs(rows, reduced) <= DrillMotion.SUMMARY_BUDGET_MS,
            )
        }
    }

    @Test
    fun `the stagger step shrinks so large rounds keep every slot inside the budget`() {
        val full = DrillMotion.spec(reducedMotion = false)
        assertEquals(
            "the full 50ms step fits a 5-row round",
            DrillMotion.STAGGER_MS,
            DrillMotion.summaryStaggerStepMs(5, full),
        )
        assertTrue(
            "an 8-row round must shrink the step instead of collapsing rows into one slot",
            DrillMotion.summaryStaggerStepMs(8, full) < DrillMotion.STAGGER_MS,
        )
    }

    @Test
    fun `the drill fragment wires its motion through DrillMotion`() {
        val source = resolveFile("src/main/java/app/olauncher/ui/words/DrillFragment.kt").readText()
        assertTrue(
            "DrillFragment must take its spec from DrillMotion",
            source.contains("DrillMotion.spec("),
        )
        assertTrue(
            "DrillFragment must use the drill motion easing",
            source.contains("DrillMotion.EASE_CONTROL_X1"),
        )
        assertFalse(
            "the drill auto-advance must use DrillMotion.AUTO_ADVANCE_MS, not the old 800ms conjugation delay",
            source.contains("AUTO_ADVANCE_DELAY_MS"),
        )
    }
}
