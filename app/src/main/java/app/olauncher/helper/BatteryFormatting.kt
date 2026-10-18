package app.olauncher.helper

import app.olauncher.data.Prefs

object BatteryFormatting {

    const val CHARGING_SYMBOL = " \u26A1\uFE0E"

    fun dateLine(
        date: String,
        percent: Int,
        charging: Boolean,
        mode: Int,
        statusBarShown: Boolean
    ): String {
        val showBattery = percent > 0 && when (mode) {
            Prefs.BATTERY_MODE_ALWAYS -> true
            Prefs.BATTERY_MODE_OFF -> false
            else -> !statusBarShown
        }

        val raw = if (showBattery) {
            val base = "$date, $percent%"
            if (charging) "$base$CHARGING_SYMBOL" else base
        } else {
            date
        }

        return raw.replace(".,", ",")
    }
}
