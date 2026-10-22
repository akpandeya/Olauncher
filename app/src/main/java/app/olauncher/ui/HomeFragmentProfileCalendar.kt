package app.olauncher.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import app.olauncher.glance.CalendarRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun HomeFragment.calendarReadable(): Boolean {
    val ctx = context ?: return false
    return ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
}

/**
 * Reads the events running now for the "while an event is on" profile rules, off the main thread,
 * and redraws Home when that changes the active profile. Without calendar access the events stay
 * null, so the event rules are skipped.
 */
internal fun HomeFragment.refreshProfileCalendarEvents() {
    val ctx = context ?: return
    if (!prefs.hasProfileEventRules) {
        prefs.profileCalendarEvents = null
        return
    }
    val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    if (!granted) {
        prefs.profileCalendarEvents = null
        return
    }
    val repository = CalendarRepository(ctx.applicationContext)
    viewLifecycleOwner.lifecycleScope.launch {
        val now = System.currentTimeMillis()
        val events = withContext(Dispatchers.IO) { repository.events(now, now + 1) }
        if (_binding == null) return@launch
        val before = prefs.profileCalendarEvents
        prefs.profileCalendarEvents = events
        if (before != events) refreshHomeSetIfChanged()
    }
}
