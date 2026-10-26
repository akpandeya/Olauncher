package app.olauncher.ui.words

/**
 * Motion tokens and animation decisions for the drill screen (#640), mirrored from the
 * approved mock `docs/design/drills-motion-mock.html` and the spec section
 * "Motion on the drill screen" in `docs/design/drills-ux.md`.
 *
 * Everything here is Context-free so JVM tests can pin the design language
 * (see DrillMotionDesignLanguageTest); DrillFragment only applies what this object decides.
 */
object DrillMotion {

    // ---- durations (ms) ----
    /** The old question card fades out toward the left. */
    const val EXIT_MS = 140L
    /** The new question card slides in from the right. */
    const val ENTER_MS = 220L
    /** The right-answer check / summary counts pop. */
    const val POP_MS = 180L
    /** The wrong-answer shake on the tapped tile (also the near-miss border pulse length). */
    const val SHAKE_MS = 320L
    /** The accentRose flash on the wrong tile. */
    const val FLASH_MS = 450L
    /** The inline correction slides up. */
    const val RISE_MS = 200L
    /** One summary row rises. */
    const val ROW_MS = 240L
    /** The summary row stagger. */
    const val STAGGER_MS = 50L
    /** The first summary element starts after this beat, like the mock's `120ms +`. */
    const val SUMMARY_BASE_DELAY_MS = 120L
    /** Auto-advance after a right answer. */
    const val AUTO_ADVANCE_MS = 500L
    /** The whole summary entrance must stay under this budget. */
    const val SUMMARY_BUDGET_MS = 700L

    // ---- geometry and scale ----
    const val SLIDE_X_DP = 24f
    const val SHAKE_X_DP = 7f
    const val RISE_Y_DP = 10f
    /** The pop overshoot: the view starts here and settles to 1. */
    const val POP_MAX_SCALE = 1.12f
    /** Other tiles dim to this alpha on a right or wrong answer. */
    const val DIMMED_ALPHA = 0.45f

    // ---- easing: cubic-bezier(.2, 0, 0, 1), decelerate ----
    const val EASE_CONTROL_X1 = 0.2f
    const val EASE_CONTROL_Y1 = 0f
    const val EASE_CONTROL_X2 = 0f
    const val EASE_CONTROL_Y2 = 1f

    // ---- reduced motion (the mock's prefers-reduced-motion block) ----
    const val REDUCED_EXIT_MS = 80L
    const val REDUCED_ENTER_MS = 120L
    const val REDUCED_FLASH_MS = 220L
    const val REDUCED_RISE_MS = 100L
    const val REDUCED_ROW_MS = 120L

    /**
     * What the animations may do. Under reduced motion every translation, shake and
     * scale is dropped and only short opacity-only fades remain.
     */
    data class Spec(
        val reducedMotion: Boolean,
        val exitMs: Long,
        val enterMs: Long,
        val popMs: Long,
        val shakeMs: Long,
        val flashMs: Long,
        val riseMs: Long,
        val rowMs: Long,
        val staggerMs: Long,
        val autoAdvanceMs: Long,
        val translates: Boolean,
        val scales: Boolean,
    ) {
        /** A shake is a translation, so it follows the same switch. */
        val shakes: Boolean get() = translates
    }

    fun spec(reducedMotion: Boolean): Spec = if (reducedMotion) {
        Spec(
            reducedMotion = true,
            exitMs = REDUCED_EXIT_MS,
            enterMs = REDUCED_ENTER_MS,
            popMs = POP_MS,
            shakeMs = SHAKE_MS,
            flashMs = REDUCED_FLASH_MS,
            riseMs = REDUCED_RISE_MS,
            rowMs = REDUCED_ROW_MS,
            staggerMs = 0L,
            autoAdvanceMs = AUTO_ADVANCE_MS,
            translates = false,
            scales = false,
        )
    } else {
        Spec(
            reducedMotion = false,
            exitMs = EXIT_MS,
            enterMs = ENTER_MS,
            popMs = POP_MS,
            shakeMs = SHAKE_MS,
            flashMs = FLASH_MS,
            riseMs = RISE_MS,
            rowMs = ROW_MS,
            staggerMs = STAGGER_MS,
            autoAdvanceMs = AUTO_ADVANCE_MS,
            translates = true,
            scales = true,
        )
    }

    /** Shake keyframe offsets as fractions of [SHAKE_X_DP]: out, back, then half as far, rest. */
    fun shakeFractions(): FloatArray = floatArrayOf(-1f, 1f, -0.6f, 0.6f)

    /**
     * The stagger step for a summary with [rowCount] rows: the full [STAGGER_MS] when
     * that still fits [SUMMARY_BUDGET_MS], else shrunk so even the largest round
     * ([app.olauncher.data.store.DrillRoundPlanner.MAX_ROUND_SIZE] rows + buttons)
     * gives every row and the buttons a stagger slot of their own inside the budget.
     */
    fun summaryStaggerStepMs(rowCount: Int, spec: Spec): Long = minOf(
        spec.staggerMs,
        (SUMMARY_BUDGET_MS - SUMMARY_BASE_DELAY_MS - spec.rowMs) / (rowCount + 1),
    ).coerceAtLeast(0L)

    /**
     * Start delay of summary row [index] of [rowCount] rows: the base beat plus one
     * stagger slot per row. Rows rise in the order the fragment binds them —
     * [app.olauncher.data.store.DrillSummaryLogic.sort] order, misses first.
     */
    fun summaryRowDelayMs(index: Int, rowCount: Int, spec: Spec): Long =
        SUMMARY_BASE_DELAY_MS + index * summaryStaggerStepMs(rowCount, spec)

    /** The buttons come last, in the stagger slot after the last row. */
    fun summaryButtonsDelayMs(rowCount: Int, spec: Spec): Long =
        summaryRowDelayMs(rowCount, rowCount, spec)

    /** Total summary entrance: the buttons' slot plus one row rise. */
    fun summaryTotalMs(rowCount: Int, spec: Spec): Long =
        summaryButtonsDelayMs(rowCount, spec) + spec.rowMs
}
