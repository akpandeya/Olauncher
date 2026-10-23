package app.olauncher.helper

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.util.Log
import app.olauncher.data.Prefs

/**
 * Stores the clock colour for the current wallpaper (see [ClockColour]).
 * Called when the photo wallpaper changes and when the option is switched on.
 * [photo] is the bitmap just applied; without it the system wallpaper's colours are read.
 */
fun Context.updateClockColour(prefs: Prefs, photo: Bitmap? = null) {
    if (!prefs.clockFromWallpaper) return
    val colors: WallpaperColors? = try {
        if (photo != null) WallpaperColors.fromBitmap(photo)
        else WallpaperManager.getInstance(this).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
    } catch (e: Exception) {
        Log.w("ClockColour", "Failed to read wallpaper colours", e)
        null
    }
    // The app theme decides, not the context: workers only see the phone's system setting.
    val systemIsDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    val themeIsDark = ClockColour.themeIsDark(prefs.appTheme, systemIsDark)
    val wallpaperColors = listOfNotNull(colors?.primaryColor, colors?.secondaryColor, colors?.tertiaryColor)
        .map { it.toArgb() }
    prefs.clockWallpaperColor = ClockColour.pick(wallpaperColors, themeIsDark) ?: 0
}
