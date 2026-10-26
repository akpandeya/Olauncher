package app.olauncher.wellbeing

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import app.olauncher.BuildConfig
import app.olauncher.data.Prefs

/**
 * Whole-phone grey through the system colour-correction (daltonizer) settings.
 * Needs WRITE_SECURE_SETTINGS, which can only be granted once over adb.
 */
object SystemGrey {
    const val KEY_ENABLED = "accessibility_display_daltonizer_enabled"
    const val KEY_MODE = "accessibility_display_daltonizer"
    const val MODE_GREY = 0
    const val MODE_OFF = -1
    val GRANT_COMMAND = "adb shell pm grant ${BuildConfig.APPLICATION_ID} android.permission.WRITE_SECURE_SETTINGS"

    /** The two system values. Grey is enabled = 1, mode = 0. */
    data class Daltonizer(val enabled: Int, val mode: Int) {
        val isGrey: Boolean get() = enabled == 1 && mode == MODE_GREY
    }

    val GREY = Daltonizer(enabled = 1, mode = MODE_GREY)

    /**
     * What to do to the system values. [toWrite] is null for "write nothing".
     * [newSaved] is what we should hold to restore later, or null to hold nothing.
     */
    data class Writes(val toWrite: Daltonizer?, val newSaved: Daltonizer?)

    /**
     * [saved] is what we replaced earlier (null if we hold nothing), [active] is whether grey is wanted now,
     * [current] is what the system has now. If the user already had grey on, nothing is written or saved,
     * so we never turn it off.
     */
    fun plan(saved: Daltonizer?, active: Boolean, current: Daltonizer): Writes = when {
        active && saved == null ->
            if (current.isGrey) Writes(null, null) else Writes(GREY, current)
        active -> Writes(null, saved)
        saved != null -> Writes(saved, null)
        else -> Writes(null, null)
    }

    fun isGranted(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    /** Applies the plan for [active]. Does nothing without the grant. */
    fun sync(context: Context, prefs: Prefs, active: Boolean) {
        if (!isGranted(context)) return
        try {
            val resolver = context.contentResolver
            val current = Daltonizer(
                Settings.Secure.getInt(resolver, KEY_ENABLED, 0),
                Settings.Secure.getInt(resolver, KEY_MODE, MODE_OFF),
            )
            val writes = plan(prefs.systemGreySaved, active, current)
            writes.toWrite?.let {
                // Save first, so a crash between the writes cannot lose the user's values.
                prefs.systemGreySaved = writes.newSaved
                Settings.Secure.putInt(resolver, KEY_MODE, it.mode)
                Settings.Secure.putInt(resolver, KEY_ENABLED, it.enabled)
            } ?: run { prefs.systemGreySaved = writes.newSaved }
        } catch (_: SecurityException) {
            // Grant revoked meanwhile: leave the app working.
        }
    }

    /**
     * Opens Digital Wellbeing (where Bedtime mode lives), falling back to system Settings.
     * There is no documented public intent for the Bedtime mode screen itself.
     */
    fun openBedtimeMode(context: Context) {
        val intents = listOf(
            context.packageManager.getLaunchIntentForPackage("com.google.android.apps.wellbeing"),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (intent in intents.filterNotNull()) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {
            }
        }
    }
}
