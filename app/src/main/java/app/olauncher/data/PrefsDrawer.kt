package app.olauncher.data

import android.content.SharedPreferences
import androidx.core.content.edit
import app.olauncher.data.Prefs.Companion.CURRENCY_RATES
import app.olauncher.data.Prefs.Companion.DRAWER_BOTTOM_SEARCH
import app.olauncher.data.Prefs.Companion.DRAWER_LETTER_STRIP
import app.olauncher.data.Prefs.Companion.DRAWER_NEW_FIRST
import app.olauncher.data.Prefs.Companion.DRAWER_RECENTS
import app.olauncher.data.Prefs.Companion.NEW_APP_TIP_SEEN
import app.olauncher.data.Prefs.Companion.RECENT_APPS
import app.olauncher.data.Prefs.Companion.SEARCH_CONTACTS
import app.olauncher.data.Prefs.Companion.SEARCH_ENGINE
import app.olauncher.data.Prefs.Companion.SEARCH_ENGINE_CUSTOM_URL

/** Settings group: Drawer (see [Prefs]). */
interface DrawerPrefs {
    var drawerBottomSearch: Boolean

    var drawerNewFirst: Boolean

    var drawerLetterStrip: Boolean

    var newAppTipSeen: Boolean

    var searchContacts: Boolean

    var currencyRates: Boolean

    var drawerRecents: Boolean

    var recentApps: String

    var searchEngine: String

    var searchEngineCustomUrl: String
}

internal class DrawerPrefsStore(private val prefs: SharedPreferences) : DrawerPrefs {
    override var drawerBottomSearch: Boolean
        get() = prefs.getBoolean(DRAWER_BOTTOM_SEARCH, true)
        set(value) = prefs.edit { putBoolean(DRAWER_BOTTOM_SEARCH, value).apply() }

    override var drawerNewFirst: Boolean
        get() = prefs.getBoolean(DRAWER_NEW_FIRST, true)
        set(value) = prefs.edit { putBoolean(DRAWER_NEW_FIRST, value).apply() }

    override var drawerLetterStrip: Boolean
        get() = prefs.getBoolean(DRAWER_LETTER_STRIP, false)
        set(value) = prefs.edit { putBoolean(DRAWER_LETTER_STRIP, value).apply() }

    override var newAppTipSeen: Boolean
        get() = prefs.getBoolean(NEW_APP_TIP_SEEN, false)
        set(value) = prefs.edit { putBoolean(NEW_APP_TIP_SEEN, value).apply() }

    override var searchContacts: Boolean
        get() = prefs.getBoolean(SEARCH_CONTACTS, false)
        set(value) = prefs.edit { putBoolean(SEARCH_CONTACTS, value).apply() }

    override var currencyRates: Boolean
        get() = prefs.getBoolean(CURRENCY_RATES, false)
        set(value) = prefs.edit { putBoolean(CURRENCY_RATES, value).apply() }

    override var drawerRecents: Boolean
        get() = prefs.getBoolean(DRAWER_RECENTS, true)
        set(value) = prefs.edit { putBoolean(DRAWER_RECENTS, value).apply() }

    override var recentApps: String
        get() = prefs.getString(RECENT_APPS, "") ?: ""
        set(value) = prefs.edit { putString(RECENT_APPS, value).apply() }

    override var searchEngine: String
        get() = prefs.getString(SEARCH_ENGINE, SearchEngine.PHONE_DEFAULT) ?: SearchEngine.PHONE_DEFAULT
        set(value) = prefs.edit { putString(SEARCH_ENGINE, value).apply() }

    override var searchEngineCustomUrl: String
        get() = prefs.getString(SEARCH_ENGINE_CUSTOM_URL, "") ?: ""
        set(value) = prefs.edit { putString(SEARCH_ENGINE_CUSTOM_URL, value).apply() }
}
