package app.olauncher.helper

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import app.olauncher.data.Prefs

/** A saved app choice (clock, calendar, weather) that can be launched. */
data class AppTarget(
    val label: String,
    val pkg: String,
    val className: String?,
    val user: String,
)

object OpenTargets {

    /** Returns null when no app is chosen yet (blank package). */
    fun target(label: String, pkg: String, className: String?, user: String): AppTarget? {
        if (pkg.isBlank()) return null
        return AppTarget(label, pkg, className, user)
    }

    fun clockTarget(prefs: Prefs): AppTarget? =
        target("Clock", prefs.clockAppPackage, prefs.clockAppClassName, prefs.clockAppUser)

    fun calendarTarget(prefs: Prefs): AppTarget? =
        target("Calendar", prefs.calendarAppPackage, prefs.calendarAppClassName, prefs.calendarAppUser)

    fun weatherTarget(context: Context, prefs: Prefs): AppTarget? =
        target(
            context.getString(app.olauncher.R.string.weather),
            prefs.weatherAppPackage,
            prefs.weatherAppClassName,
            prefs.weatherAppUser,
        )

    /** Opens the chosen clock app, or the system alarms screen when none is chosen. */
    fun openClockApp(context: Context, prefs: Prefs, launch: (AppTarget) -> Unit) {
        val target = clockTarget(prefs)
        if (target == null) openAlarmApp(context) else launch(target)
    }

    /** Opens the chosen calendar app, or the system calendar when none is chosen. */
    fun openCalendarApp(context: Context, prefs: Prefs, launch: (AppTarget) -> Unit) {
        val target = calendarTarget(prefs)
        if (target == null) openCalendar(context) else launch(target)
    }

    /** Launches the chosen weather app, or calls [pickApp] so the user can choose one. */
    fun openWeatherApp(context: Context, prefs: Prefs, pickApp: () -> Unit, launch: (AppTarget) -> Unit) {
        val target = weatherTarget(context, prefs)
        if (target == null) pickApp() else launch(target)
    }

    /** A calendar event can only be opened with a real (positive) id. */
    fun canOpenEvent(eventId: Long): Boolean = eventId > 0L

    fun openEvent(context: Context, eventId: Long): Boolean {
        if (!canOpenEvent(eventId)) return false
        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    data = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            true
        } catch (_: Exception) {
            false
        }
    }
}
