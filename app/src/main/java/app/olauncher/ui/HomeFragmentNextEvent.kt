package app.olauncher.ui

import android.Manifest
import android.content.pm.PackageManager
import android.text.format.DateFormat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import app.olauncher.R
import app.olauncher.glance.CalendarRepository
import app.olauncher.glance.HomeNextEvent
import app.olauncher.glance.HomeNextEventLine
import app.olauncher.glance.NextUpPlanner
import app.olauncher.helper.OpenTargets
import app.olauncher.helper.showToast
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

internal fun HomeFragment.populateNextEventLine() {
    if (_binding == null) return
    val ctx = context ?: return
    val appContext = ctx.applicationContext
    val prefOn = prefs.homeNextEvent
    val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    if (!HomeNextEvent.shouldShow(prefOn, granted, HomeNextEventLine("", 0))) {
        nextEventId = null
        binding.nextEventLine.isVisible = false
        return
    }
    val nowTemplate = getString(R.string.next_up_now)
    val is24Hour = DateFormat.is24HourFormat(ctx)
    viewLifecycleOwner.lifecycleScope.launch {
        val now = Instant.now()
        val line = try {
            val events = CalendarRepository(appContext)
                .next(now.toEpochMilli(), hours = 24, limit = NextUpPlanner.FETCH_LIMIT)
            HomeNextEvent.line(events, now, ZoneId.systemDefault(), is24Hour, nowTemplate)
        } catch (_: Exception) {
            null
        }
        if (_binding == null) return@launch
        nextEventId = line?.eventId
        binding.nextEventLine.text = line?.text
        binding.nextEventLine.isVisible = HomeNextEvent.shouldShow(prefOn, granted, line)
    }
}

internal fun HomeFragment.openNextEvent() {
    val context = context ?: return
    val id = nextEventId ?: return
    if (!OpenTargets.openEvent(context, id)) {
        context.showToast(getString(R.string.unable_to_open_app))
    }
}
