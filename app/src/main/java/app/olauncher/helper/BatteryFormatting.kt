package app.olauncher.helper

import app.olauncher.data.Prefs

data class BatteryReading(val percent: Int, val charging: Boolean)

object BatteryFormatting {

    fun readingChanged(previous: BatteryReading?, current: BatteryReading): Boolean =
        previous != current

    const val CHARGING_SYMBOL = " \u26A1\uFE0E"

    /** "82%" (plus charging symbol) when the battery should show, else null. */
    fun infoText(percent: Int, charging: Boolean, mode: Int, statusBarShown: Boolean): String? {
        val show = percent > 0 && when (mode) {
            Prefs.BATTERY_MODE_ALWAYS -> true
            Prefs.BATTERY_MODE_OFF -> false
            else -> !statusBarShown
        }
        if (!show) return null
        return if (charging) "$percent%$CHARGING_SYMBOL" else "$percent%"
    }

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
