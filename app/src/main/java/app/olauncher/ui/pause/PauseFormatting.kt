package app.olauncher.ui.pause

/**
 * Pure functions for formatting pause screen open count and duration text.
 */
object PauseFormatting {

    fun formatDuration(millis: Long): String {
        val totalMinutes = millis / 1000 / 60
        val hours = totalMinutes / 60
        val remMinutes = totalMinutes % 60
        return when {
            millis <= 0L -> "0 min"
            totalMinutes == 0L -> "<1 min"
            hours > 0 && remMinutes > 0 -> "${hours}h ${remMinutes}m"
            hours > 0 -> "${hours}h"
            else -> "$totalMinutes min"
        }
    }

    fun formatTimes(count: Int): String {
        return if (count == 1) "1 time" else "$count times"
    }

    fun formatUsageStatsLine(openCount: Int, foregroundMillis: Long): String {
        val times = formatTimes(openCount)
        val duration = formatDuration(foregroundMillis)
        return "Opened $times today · $duration"
    }

    fun formatThumblineOpensLine(openCount: Int): String {
        val times = formatTimes(openCount)
        return "Opened $times from Thumbline today"
    }

    fun openLabel(countdownSeconds: Int = 0, german: Boolean = false): String {
        return if (german) {
            if (countdownSeconds > 0) "Öffnen ($countdownSeconds)" else "Öffnen"
        } else {
            if (countdownSeconds > 0) "Open ($countdownSeconds)" else "Open"
        }
    }

    fun notNowLabel(german: Boolean = false): String = if (german) "Jetzt nicht" else "Not now"
}
