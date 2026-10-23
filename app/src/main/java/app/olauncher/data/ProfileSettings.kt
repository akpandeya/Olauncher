package app.olauncher.data

import app.olauncher.helper.HomeSets
import app.olauncher.helper.MusicPulse
import app.olauncher.ui.today.cards.HeldCard
import app.olauncher.ui.today.cards.NextUpCard
import app.olauncher.ui.today.cards.SavedCard
import app.olauncher.ui.today.cards.WeatherCard
import app.olauncher.ui.today.TodayOrder
import app.olauncher.ui.today.cards.WellbeingCard
import app.olauncher.ui.today.cards.WordsCard
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class ProfileFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The only settings a profile may write. It has no wallpaper, theme, font, accent,
 * hidden apps or renamed labels, so applying a profile cannot touch them.
 */
interface ProfileSettingsTarget {
    var homeAppsNum: Int
    var dateTimeVisibility: Int
    var weatherEnabled: Boolean
    var homeNextEvent: Boolean
    var homeContextLine: Boolean
    var homeMusicPulse: Boolean
    var homeGlowStrength: Int
    var homeWeatherEffects: Boolean
    var homeShortcuts: Boolean
    var shortcutCount: Int
    var intentionOnHome: Boolean
    var pauseEnabled: Boolean
    var pauseSeconds: Int
    var pauseAllowanceMin: Int
    var pauseEscalate: Boolean
    var quietEnabled: Boolean
    var windDown: Boolean
    var windDownGrey: Boolean
    var nudgeWaterIntervalMin: Int
    var nudgeWalkIntervalMin: Int
    var todayOrderSmart: Boolean
    var drawerRecents: Boolean
    var drawerLetterStrip: Boolean
    var searchContacts: Boolean
    fun setTodayCardEnabled(key: String, enabled: Boolean)
}

/**
 * Settings of one profile (issue #320 table). A null `Int?` or `Boolean?` field means
 * "leave the user's value alone".
 */
data class ProfileSettings(
    val homeAppsCount: Int,
    val showClock: Boolean,
    val showDate: Boolean,
    val showWeather: Boolean,
    val nextEvent: Boolean,
    /** The music line (stored in the context-line pref, which shows now-playing media). */
    val musicLine: Boolean,
    val musicPulse: Boolean,
    /** Music glow strength in percent; 0 is off. */
    val glowStrength: Int,
    val weatherEffects: Boolean,
    val shortcutIcons: Boolean,
    val shortcutCount: Int?,
    val intentionLine: Boolean,
    val pause: Boolean,
    /** Pause length in seconds. */
    val pauseSeconds: Int?,
    /** Minutes allowed before pausing again. */
    val pauseAllowanceMin: Int?,
    val pauseEscalate: Boolean?,
    val quietNotifications: Boolean,
    val windDown: Boolean,
    val windDownGrey: Boolean,
    /** Water / walk nudge intervals in minutes; 0 is off. */
    val nudgeWaterIntervalMin: Int,
    val nudgeWalkIntervalMin: Int,
    /** Today card keys that are on; every other known card is switched off. */
    val todayCards: Set<String>,
    val todayOrderSmart: Boolean?,
    val drawerRecents: Boolean,
    val drawerLetterStrip: Boolean,
    val drawerContacts: Boolean,
) {
    init {
        require(homeAppsCount in 0..HomeSets.MAX_SLOTS) { "homeAppsCount out of range: $homeAppsCount" }
        require(glowStrength in 0..100) { "glowStrength out of range: $glowStrength" }
        require(todayCards.all { it in KNOWN_CARDS || it == TodayOrder.LEGACY_WEEK_KEY }) { "unknown today card" }
    }

    /** Writes every setting of this profile. Nothing outside [ProfileSettingsTarget] is touched. */
    fun apply(target: ProfileSettingsTarget) {
        target.homeAppsNum = homeAppsCount
        target.dateTimeVisibility = when {
            showClock && showDate -> Constants.DateTime.ON
            showClock -> Constants.DateTime.CLOCK_ONLY
            showDate -> Constants.DateTime.DATE_ONLY
            else -> Constants.DateTime.OFF
        }
        target.weatherEnabled = showWeather
        target.homeNextEvent = nextEvent
        target.homeContextLine = musicLine
        target.homeMusicPulse = musicPulse
        target.homeGlowStrength = glowStrength
        target.homeWeatherEffects = weatherEffects
        target.homeShortcuts = shortcutIcons
        shortcutCount?.let { target.shortcutCount = it }
        target.intentionOnHome = intentionLine
        target.pauseEnabled = pause
        pauseSeconds?.let { target.pauseSeconds = it }
        pauseAllowanceMin?.let { target.pauseAllowanceMin = it }
        pauseEscalate?.let { target.pauseEscalate = it }
        target.quietEnabled = quietNotifications
        target.windDown = windDown
        target.windDownGrey = windDownGrey
        target.nudgeWaterIntervalMin = nudgeWaterIntervalMin
        target.nudgeWalkIntervalMin = nudgeWalkIntervalMin
        KNOWN_CARDS.forEach { key ->
            // An old profile may still list the week card, which now lives in Wellbeing.
            val on = key in todayCards || (key == WellbeingCard.KEY && TodayOrder.LEGACY_WEEK_KEY in todayCards)
            target.setTodayCardEnabled(key, on)
        }
        todayOrderSmart?.let { target.todayOrderSmart = it }
        target.drawerRecents = drawerRecents
        target.drawerLetterStrip = drawerLetterStrip
        target.searchContacts = drawerContacts
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put(F_HOME_APPS_COUNT, homeAppsCount)
        put(F_SHOW_CLOCK, showClock)
        put(F_SHOW_DATE, showDate)
        put(F_SHOW_WEATHER, showWeather)
        put(F_NEXT_EVENT, nextEvent)
        put(F_MUSIC_LINE, musicLine)
        put(F_MUSIC_PULSE, musicPulse)
        put(F_GLOW_STRENGTH, glowStrength)
        put(F_WEATHER_EFFECTS, weatherEffects)
        put(F_SHORTCUT_ICONS, shortcutIcons)
        shortcutCount?.let { put(F_SHORTCUT_COUNT, it) }
        put(F_INTENTION_LINE, intentionLine)
        put(F_PAUSE, pause)
        pauseSeconds?.let { put(F_PAUSE_SECONDS, it) }
        pauseAllowanceMin?.let { put(F_PAUSE_ALLOWANCE_MIN, it) }
        pauseEscalate?.let { put(F_PAUSE_ESCALATE, it) }
        put(F_QUIET, quietNotifications)
        put(F_WIND_DOWN, windDown)
        put(F_WIND_DOWN_GREY, windDownGrey)
        put(F_NUDGE_WATER, nudgeWaterIntervalMin)
        put(F_NUDGE_WALK, nudgeWalkIntervalMin)
        put(F_TODAY_CARDS, JSONArray(todayCards.sorted()))
        todayOrderSmart?.let { put(F_TODAY_ORDER_SMART, it) }
        put(F_DRAWER_RECENTS, drawerRecents)
        put(F_DRAWER_LETTER_STRIP, drawerLetterStrip)
        put(F_DRAWER_CONTACTS, drawerContacts)
    }

    companion object {
        val KNOWN_CARDS: List<String> = listOf(
            WeatherCard.KEY, NextUpCard.KEY, SavedCard.KEY, HeldCard.KEY,
            WellbeingCard.KEY, WordsCard.KEY,
        )

        private const val F_HOME_APPS_COUNT = "homeAppsCount"
        private const val F_SHOW_CLOCK = "showClock"
        private const val F_SHOW_DATE = "showDate"
        private const val F_SHOW_WEATHER = "showWeather"
        private const val F_NEXT_EVENT = "nextEvent"
        private const val F_MUSIC_LINE = "musicLine"
        private const val F_MUSIC_PULSE = "musicPulse"
        private const val F_GLOW_STRENGTH = "glowStrength"
        private const val F_WEATHER_EFFECTS = "weatherEffects"
        private const val F_SHORTCUT_ICONS = "shortcutIcons"
        private const val F_SHORTCUT_COUNT = "shortcutCount"
        private const val F_INTENTION_LINE = "intentionLine"
        private const val F_PAUSE = "pause"
        private const val F_PAUSE_SECONDS = "pauseSeconds"
        private const val F_PAUSE_ALLOWANCE_MIN = "pauseAllowanceMin"
        private const val F_PAUSE_ESCALATE = "pauseEscalate"
        private const val F_QUIET = "quietNotifications"
        private const val F_WIND_DOWN = "windDown"
        private const val F_WIND_DOWN_GREY = "windDownGrey"
        private const val F_NUDGE_WATER = "nudgeWaterIntervalMin"
        private const val F_NUDGE_WALK = "nudgeWalkIntervalMin"
        private const val F_TODAY_CARDS = "todayCards"
        private const val F_TODAY_ORDER_SMART = "todayOrderSmart"
        private const val F_DRAWER_RECENTS = "drawerRecents"
        private const val F_DRAWER_LETTER_STRIP = "drawerLetterStrip"
        private const val F_DRAWER_CONTACTS = "drawerContacts"

        private val REQUIRED = setOf(
            F_HOME_APPS_COUNT, F_SHOW_CLOCK, F_SHOW_DATE, F_SHOW_WEATHER, F_NEXT_EVENT, F_MUSIC_LINE,
            F_MUSIC_PULSE, F_GLOW_STRENGTH, F_WEATHER_EFFECTS, F_SHORTCUT_ICONS, F_INTENTION_LINE, F_PAUSE,
            F_QUIET, F_WIND_DOWN, F_WIND_DOWN_GREY, F_NUDGE_WATER, F_NUDGE_WALK, F_TODAY_CARDS,
            F_DRAWER_RECENTS, F_DRAWER_LETTER_STRIP, F_DRAWER_CONTACTS,
        )
        private val OPTIONAL = setOf(
            F_SHORTCUT_COUNT, F_PAUSE_SECONDS, F_PAUSE_ALLOWANCE_MIN, F_PAUSE_ESCALATE, F_TODAY_ORDER_SMART,
        )

        /** Parses strictly: an unknown or missing field, or a wrong type, throws and yields nothing. */
        fun fromJson(json: JSONObject): ProfileSettings {
            val keys = json.keys().asSequence().toSet()
            val unknown = keys - REQUIRED - OPTIONAL
            if (unknown.isNotEmpty()) throw ProfileFormatException("Unknown profile field: ${unknown.sorted().joinToString()}")
            val missing = REQUIRED - keys
            if (missing.isNotEmpty()) throw ProfileFormatException("Missing profile field: ${missing.sorted().joinToString()}")
            try {
                val cards = json.getJSONArray(F_TODAY_CARDS)
                val cardKeys = (0 until cards.length()).map { cards.getString(it) }.toSet()
                return ProfileSettings(
                    homeAppsCount = json.strictInt(F_HOME_APPS_COUNT),
                    showClock = json.strictBool(F_SHOW_CLOCK),
                    showDate = json.strictBool(F_SHOW_DATE),
                    showWeather = json.strictBool(F_SHOW_WEATHER),
                    nextEvent = json.strictBool(F_NEXT_EVENT),
                    musicLine = json.strictBool(F_MUSIC_LINE),
                    musicPulse = json.strictBool(F_MUSIC_PULSE),
                    glowStrength = json.strictInt(F_GLOW_STRENGTH),
                    weatherEffects = json.strictBool(F_WEATHER_EFFECTS),
                    shortcutIcons = json.strictBool(F_SHORTCUT_ICONS),
                    shortcutCount = if (json.has(F_SHORTCUT_COUNT)) json.strictInt(F_SHORTCUT_COUNT) else null,
                    intentionLine = json.strictBool(F_INTENTION_LINE),
                    pause = json.strictBool(F_PAUSE),
                    pauseSeconds = if (json.has(F_PAUSE_SECONDS)) json.strictInt(F_PAUSE_SECONDS) else null,
                    pauseAllowanceMin = if (json.has(F_PAUSE_ALLOWANCE_MIN)) json.strictInt(F_PAUSE_ALLOWANCE_MIN) else null,
                    pauseEscalate = if (json.has(F_PAUSE_ESCALATE)) json.strictBool(F_PAUSE_ESCALATE) else null,
                    quietNotifications = json.strictBool(F_QUIET),
                    windDown = json.strictBool(F_WIND_DOWN),
                    windDownGrey = json.strictBool(F_WIND_DOWN_GREY),
                    nudgeWaterIntervalMin = json.strictInt(F_NUDGE_WATER),
                    nudgeWalkIntervalMin = json.strictInt(F_NUDGE_WALK),
                    todayCards = cardKeys,
                    todayOrderSmart = if (json.has(F_TODAY_ORDER_SMART)) json.strictBool(F_TODAY_ORDER_SMART) else null,
                    drawerRecents = json.strictBool(F_DRAWER_RECENTS),
                    drawerLetterStrip = json.strictBool(F_DRAWER_LETTER_STRIP),
                    drawerContacts = json.strictBool(F_DRAWER_CONTACTS),
                )
            } catch (e: JSONException) {
                throw ProfileFormatException("Bad profile field", e)
            } catch (e: IllegalArgumentException) {
                throw ProfileFormatException(e.message ?: "Bad profile value", e)
            }
        }

        private fun JSONObject.strictBool(key: String): Boolean {
            if (get(key) !is Boolean) throw JSONException("$key is not a boolean")
            return getBoolean(key)
        }

        private fun JSONObject.strictInt(key: String): Int {
            if (get(key) !is Int) throw JSONException("$key is not an int")
            return getInt(key)
        }
    }
}

/** The three built-in profiles. */
object BuiltInProfiles {
    const val BARE_ID = "bare"
    const val FOCUS_ID = "focus"
    const val RICH_ID = "rich"

    val bareSettings = ProfileSettings(
        homeAppsCount = 4,
        showClock = true, showDate = false, showWeather = false,
        nextEvent = false, musicLine = false,
        musicPulse = false, glowStrength = 0, weatherEffects = false,
        shortcutIcons = false, shortcutCount = null,
        intentionLine = false,
        pause = false, pauseSeconds = null, pauseAllowanceMin = null, pauseEscalate = null,
        quietNotifications = false,
        windDown = false, windDownGrey = false,
        nudgeWaterIntervalMin = 0, nudgeWalkIntervalMin = 0,
        todayCards = emptySet(), todayOrderSmart = null,
        drawerRecents = false, drawerLetterStrip = false, drawerContacts = false,
    )

    val focusSettings = ProfileSettings(
        homeAppsCount = 5,
        showClock = true, showDate = true, showWeather = false,
        nextEvent = true, musicLine = true,
        musicPulse = false, glowStrength = 0, weatherEffects = false,
        shortcutIcons = false, shortcutCount = null,
        intentionLine = true,
        pause = true, pauseSeconds = null, pauseAllowanceMin = 10, pauseEscalate = true,
        quietNotifications = true,
        windDown = true, windDownGrey = true,
        nudgeWaterIntervalMin = 90, nudgeWalkIntervalMin = 120,
        todayCards = emptySet(), todayOrderSmart = null,
        drawerRecents = true, drawerLetterStrip = false, drawerContacts = false,
    )

    val richSettings = ProfileSettings(
        homeAppsCount = 7,
        showClock = true, showDate = true, showWeather = true,
        nextEvent = true, musicLine = true,
        musicPulse = true, glowStrength = MusicPulse.GLOW_STRENGTH_DEFAULT, weatherEffects = true,
        shortcutIcons = true, shortcutCount = 4,
        intentionLine = true,
        pause = true, pauseSeconds = 3, pauseAllowanceMin = null, pauseEscalate = false,
        quietNotifications = false,
        windDown = true, windDownGrey = false,
        nudgeWaterIntervalMin = 60, nudgeWalkIntervalMin = 90,
        todayCards = ProfileSettings.KNOWN_CARDS.toSet(), todayOrderSmart = true,
        drawerRecents = true, drawerLetterStrip = true, drawerContacts = true,
    )

    /** Built-ins whose home apps start as a copy of [currentHomeApps], cut to each profile's count. */
    fun all(currentHomeApps: List<HomeApp>): List<Profile> = listOf(
        Profile(BARE_ID, "Bare", currentHomeApps.take(bareSettings.homeAppsCount), bareSettings),
        Profile(FOCUS_ID, "Focus", currentHomeApps.take(focusSettings.homeAppsCount), focusSettings),
        Profile(RICH_ID, "Rich", currentHomeApps.take(richSettings.homeAppsCount), richSettings),
    )
}
