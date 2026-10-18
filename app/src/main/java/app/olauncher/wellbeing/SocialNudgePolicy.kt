package app.olauncher.wellbeing

/**
 * Pure policy deciding when to nudge the user to put the phone down based on
 * distracting-app foreground usage.
 */
object SocialNudgePolicy {

    /**
     * Calculates whether a nudge should be posted and returns the new bucket index to record,
     * or null if no nudge should be posted.
     *
     * @param minutesToday Minutes spent on distracting apps today.
     * @param threshold Threshold in minutes before the first nudge (0 = off).
     * @param repeat Interval in minutes for subsequent repeat nudges.
     * @param lastBucket The last bucket index recorded today, or null / negative if none.
     * @param inForegroundRecently True if a distracting app had an ACTIVITY_RESUMED recently.
     * @return The new bucket index to record (0, 1, 2, ...), or null.
     */
    fun shouldNudge(
        minutesToday: Int,
        threshold: Int,
        repeat: Int,
        lastBucket: Int?,
        inForegroundRecently: Boolean,
    ): Int? {
        if (!inForegroundRecently) return null
        if (threshold <= 0 || repeat <= 0) return null
        if (minutesToday < threshold) return null

        val currentBucket = (minutesToday - threshold) / repeat
        if (lastBucket != null && lastBucket >= 0 && currentBucket <= lastBucket) {
            return null
        }
        return currentBucket
    }
}
