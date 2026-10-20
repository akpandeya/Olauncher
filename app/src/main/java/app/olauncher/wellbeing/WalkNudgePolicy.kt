package app.olauncher.wellbeing

/** An exercise session as epoch milliseconds. */
data class ExerciseSpan(val startMs: Long, val endMs: Long)

/**
 * Steps and exercise sessions read for the last walk interval.
 * A field is null when it could not be read (no permission or error).
 */
data class WalkWindowReading(val steps: Long?, val exercise: List<ExerciseSpan>?)

object WalkNudgePolicy {

    const val MIN_STEPS_TO_SKIP = 500L

    /**
     * True when the walk nudge should be skipped: enough steps, or an exercise
     * session overlapping [windowStartMs, windowEndMs]. Missing data never skips.
     */
    fun shouldSkip(
        steps: Long?,
        exerciseSessions: List<ExerciseSpan>?,
        windowStartMs: Long,
        windowEndMs: Long,
    ): Boolean {
        if (steps != null && steps >= MIN_STEPS_TO_SKIP) return true
        return exerciseSessions.orEmpty().any { it.startMs <= windowEndMs && it.endMs >= windowStartMs }
    }
}
