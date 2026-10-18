package app.olauncher.wellbeing

data class PauseDecision(
    val seconds: Int,
    val overAllowance: Boolean,
)

/**
 * Pure policy for computing mindful pause delay based on open counts,
 * escalation settings, and daily allowance usage.
 */
object PausePolicy {

    const val ESCALATION_THRESHOLD_OPENS: Int = 3
    const val ESCALATION_STEP_SECONDS: Int = 2
    const val ESCALATION_CAP_SECONDS: Int = 30
    const val OVER_ALLOWANCE_FLOOR_SECONDS: Int = 15
    const val OVER_ALLOWANCE_CAP_SECONDS: Int = 60

    /** The saved allowance only applies while usage access is granted; otherwise it is off. */
    fun effectiveAllowanceMin(savedMin: Int, usageAccessGranted: Boolean): Int =
        if (usageAccessGranted) savedMin.coerceAtLeast(0) else 0

    /** [earlierOpensToday] counts opens before this one; [delaySeconds] wants the Nth open. */
    fun opensIncludingThisOne(earlierOpensToday: Int): Int = earlierOpensToday.coerceAtLeast(0) + 1

    fun delaySeconds(
        base: Int,
        opensToday: Int,
        minutesToday: Int?,
        allowanceMin: Int,
        escalate: Boolean,
    ): PauseDecision {
        val seconds = if (escalate) {
            val extra = if (opensToday > ESCALATION_THRESHOLD_OPENS) {
                (opensToday - ESCALATION_THRESHOLD_OPENS) * ESCALATION_STEP_SECONDS
            } else {
                0
            }
            minOf(base + extra, ESCALATION_CAP_SECONDS)
        } else {
            base
        }

        val overAllowance = minutesToday != null && allowanceMin > 0 && minutesToday >= allowanceMin
        val finalSeconds = if (overAllowance) {
            minOf(maxOf(seconds, OVER_ALLOWANCE_FLOOR_SECONDS) * 2, OVER_ALLOWANCE_CAP_SECONDS)
        } else {
            seconds
        }

        return PauseDecision(
            seconds = finalSeconds,
            overAllowance = overAllowance,
        )
    }
}
