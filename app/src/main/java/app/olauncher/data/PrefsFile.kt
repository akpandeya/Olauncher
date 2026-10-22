package app.olauncher.data

import android.content.Context
import android.content.SharedPreferences

private const val PREFS_FILENAME = "app.olauncher"

internal fun Context.appPrefs(): SharedPreferences = getSharedPreferences(PREFS_FILENAME, 0)
