package app.olauncher.helper

/** Plain m:ss (or h:mm:ss) clock text for a song time, used in spoken descriptions. Context-free. */
object SpokenTime {
    fun format(ms: Long): String {
        val totalSeconds = ms.coerceAtLeast(0L) / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
        else "%d:%02d".format(minutes, seconds)
    }
}
