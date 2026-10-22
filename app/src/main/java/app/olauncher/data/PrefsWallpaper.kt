package app.olauncher.data

import android.content.SharedPreferences
import androidx.core.content.edit
import app.olauncher.helper.RotationInterval

/** Settings group: Wallpaper (see [Prefs]). */
interface WallpaperPrefs {
    var dailyWallpaper: Boolean

    var dailyWallpaperUrl: String

    var photoWallpaperCount: Int

    var photoWallpaperIndex: Int

    var photoWallpaperTarget: Int

    var photoWallpaperLastDay: Int

    var photoWallpaperHomeMenu: Boolean

    var photoWallpaperInterval: Int

    var photoWallpaperLastChanged: Long
}

internal class WallpaperPrefsStore(private val prefs: SharedPreferences) : WallpaperPrefs {
    private val DAILY_WALLPAPER = "DAILY_WALLPAPER"
    private val DAILY_WALLPAPER_URL = "DAILY_WALLPAPER_URL"
    private val PHOTO_WALLPAPER_COUNT = "PHOTO_WALLPAPER_COUNT"
    private val PHOTO_WALLPAPER_INDEX = "PHOTO_WALLPAPER_INDEX"
    private val PHOTO_WALLPAPER_TARGET = "PHOTO_WALLPAPER_TARGET"
    private val PHOTO_WALLPAPER_LAST_DAY = "PHOTO_WALLPAPER_LAST_DAY"
    private val PHOTO_WALLPAPER_HOME_MENU = "PHOTO_WALLPAPER_HOME_MENU"
    private val PHOTO_WALLPAPER_INTERVAL = "PHOTO_WALLPAPER_INTERVAL"
    private val PHOTO_WALLPAPER_LAST_CHANGED = "PHOTO_WALLPAPER_LAST_CHANGED"

    override var dailyWallpaper: Boolean
        get() = prefs.getBoolean(DAILY_WALLPAPER, false)
        set(value) = prefs.edit { putBoolean(DAILY_WALLPAPER, value).apply() }

    override var dailyWallpaperUrl: String
        get() = prefs.getString(DAILY_WALLPAPER_URL, "").toString()
        set(value) = prefs.edit { putString(DAILY_WALLPAPER_URL, value).apply() }

    override var photoWallpaperCount: Int
        get() = prefs.getInt(PHOTO_WALLPAPER_COUNT, 0)
        set(value) = prefs.edit { putInt(PHOTO_WALLPAPER_COUNT, value).apply() }

    override var photoWallpaperIndex: Int
        get() = prefs.getInt(PHOTO_WALLPAPER_INDEX, 0)
        set(value) = prefs.edit { putInt(PHOTO_WALLPAPER_INDEX, value).apply() }

    override var photoWallpaperTarget: Int
        get() = prefs.getInt(PHOTO_WALLPAPER_TARGET, 0)
        set(value) = prefs.edit { putInt(PHOTO_WALLPAPER_TARGET, value).apply() }

    override var photoWallpaperLastDay: Int
        get() = prefs.getInt(PHOTO_WALLPAPER_LAST_DAY, 0)
        set(value) = prefs.edit { putInt(PHOTO_WALLPAPER_LAST_DAY, value).apply() }

    override var photoWallpaperHomeMenu: Boolean
        get() = prefs.getBoolean(PHOTO_WALLPAPER_HOME_MENU, false)
        set(value) = prefs.edit { putBoolean(PHOTO_WALLPAPER_HOME_MENU, value).apply() }

    override var photoWallpaperInterval: Int
        get() = prefs.getInt(PHOTO_WALLPAPER_INTERVAL, RotationInterval.DEFAULT.id)
        set(value) = prefs.edit { putInt(PHOTO_WALLPAPER_INTERVAL, value).apply() }

    override var photoWallpaperLastChanged: Long
        get() = prefs.getLong(PHOTO_WALLPAPER_LAST_CHANGED, 0L)
        set(value) = prefs.edit { putLong(PHOTO_WALLPAPER_LAST_CHANGED, value).apply() }
}
