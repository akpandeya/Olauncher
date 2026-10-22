package app.olauncher.data

import app.olauncher.glance.CalendarEvent
import app.olauncher.helper.HomeSet
import app.olauncher.helper.HomeSetStarts
import app.olauncher.helper.HomeSets
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * One schedule rule: [profileId] is active from [startMinutes] to [endMinutes] (minutes since
 * midnight, end not included) on the [weekdays] (ISO: 1 = Monday .. 7 = Sunday).
 * A window with end before start crosses midnight; its weekdays are the day it starts on.
 * End equal to start is a full 24-hour window.
 */
data class ProfileRule(
    val profileId: String,
    val startMinutes: Int,
    val endMinutes: Int,
    val weekdays: Set<Int>,
) {
    init {
        require(startMinutes in 0 until HomeSets.MINUTES_PER_DAY) { "start out of range: $startMinutes" }
        require(endMinutes in 0 until HomeSets.MINUTES_PER_DAY) { "end out of range: $endMinutes" }
        require(weekdays.all { it in 1..7 }) { "weekday out of range" }
    }

    fun matches(nowMinutes: Int, dayOfWeek: Int): Boolean {
        val now = HomeSets.normalize(nowMinutes)
        if (startMinutes < endMinutes) return dayOfWeek in weekdays && now >= startMinutes && now < endMinutes
        val previousDay = if (dayOfWeek == 1) 7 else dayOfWeek - 1
        return (dayOfWeek in weekdays && now >= startMinutes) || (previousDay in weekdays && now < endMinutes)
    }
}

/**
 * "While an event is on" rule: [profileId] is active while a calendar event is running that is in
 * calendar [calendarId] (null = any calendar) and whose title contains [keyword] (blank = any title).
 * At least one of the two must be set. All-day events are ignored unless [includeAllDay].
 */
data class ProfileEventRule(
    val profileId: String,
    val calendarId: Long? = null,
    val keyword: String = "",
    val includeAllDay: Boolean = false,
) {
    init {
        require(calendarId != null || keyword.isNotBlank()) { "event rule needs a calendar or a keyword" }
    }

    fun matches(event: CalendarEvent, nowMillis: Long): Boolean {
        if (event.allDay && !includeAllDay) return false
        if (nowMillis < event.startMillis || nowMillis >= event.endMillis) return false
        if (calendarId != null && event.calendarId != calendarId) return false
        val word = keyword.trim()
        return word.isEmpty() || event.title.contains(word, ignoreCase = true)
    }

    fun matchesAny(events: List<CalendarEvent>, nowMillis: Long): Boolean = events.any { matches(it, nowMillis) }
}

/**
 * Rules plus a default profile for uncovered time. Where rules overlap, the later rule wins.
 * [eventRules] follow the calendar and beat the time rules while one of them matches.
 */
data class ProfileSchedule(
    val defaultProfileId: String,
    val rules: List<ProfileRule>,
    val eventRules: List<ProfileEventRule> = emptyList(),
) {
    /**
     * The profile of the last event rule that matches, or null. [events] null means calendar access
     * is missing: event rules are skipped.
     */
    fun resolveEvent(events: List<CalendarEvent>?, nowMillis: Long): String? {
        if (events == null) return null
        return eventRules.lastOrNull { it.matchesAny(events, nowMillis) }?.profileId
    }

    fun resolve(nowMinutes: Int, dayOfWeek: Int): String =
        rules.lastOrNull { it.matches(nowMinutes, dayOfWeek) }?.profileId ?: defaultProfileId

    fun toJson(): String = JSONObject().apply {
        put(F_DEFAULT, defaultProfileId)
        put(F_RULES, JSONArray().also { arr ->
            rules.forEach { r ->
                arr.put(JSONObject().apply {
                    put(F_PROFILE, r.profileId)
                    put(F_START, r.startMinutes)
                    put(F_END, r.endMinutes)
                    put(F_WEEKDAYS, JSONArray(r.weekdays.sorted()))
                })
            }
        })
        if (eventRules.isNotEmpty()) put(F_EVENT_RULES, JSONArray().also { arr ->
            eventRules.forEach { r ->
                arr.put(JSONObject().apply {
                    put(F_PROFILE, r.profileId)
                    r.calendarId?.let { put(F_CALENDAR, it) }
                    put(F_KEYWORD, r.keyword)
                    put(F_ALL_DAY, r.includeAllDay)
                })
            }
        })
    }.toString()

    companion object {
        val EVERY_DAY: Set<Int> = (1..7).toSet()

        private const val F_DEFAULT = "default"
        private const val F_RULES = "rules"
        private const val F_PROFILE = "profile"
        private const val F_START = "start"
        private const val F_END = "end"
        private const val F_WEEKDAYS = "weekdays"
        private const val F_EVENT_RULES = "eventRules"
        private const val F_CALENDAR = "calendarId"
        private const val F_KEYWORD = "keyword"
        private const val F_ALL_DAY = "includeAllDay"

        /** Strict: bad or unknown input throws and nothing is returned. */
        fun fromJson(text: String): ProfileSchedule {
            try {
                val json = JSONObject(text)
                val unknown = json.keys().asSequence().toSet() - setOf(F_DEFAULT, F_RULES, F_EVENT_RULES)
                if (unknown.isNotEmpty()) throw ProfileFormatException("Unknown schedule field: ${unknown.sorted().joinToString()}")
                val arr = json.getJSONArray(F_RULES)
                val rules = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    val extra = o.keys().asSequence().toSet() - setOf(F_PROFILE, F_START, F_END, F_WEEKDAYS)
                    if (extra.isNotEmpty()) throw ProfileFormatException("Unknown rule field: ${extra.sorted().joinToString()}")
                    val days = o.getJSONArray(F_WEEKDAYS)
                    ProfileRule(
                        profileId = o.getString(F_PROFILE),
                        startMinutes = o.getInt(F_START),
                        endMinutes = o.getInt(F_END),
                        weekdays = (0 until days.length()).map { days.getInt(it) }.toSet(),
                    )
                }
                val eventArr = json.optJSONArray(F_EVENT_RULES)
                val eventRules = if (eventArr == null) emptyList() else (0 until eventArr.length()).map { i ->
                    val o = eventArr.getJSONObject(i)
                    val extra = o.keys().asSequence().toSet() - setOf(F_PROFILE, F_CALENDAR, F_KEYWORD, F_ALL_DAY)
                    if (extra.isNotEmpty()) throw ProfileFormatException("Unknown event rule field: ${extra.sorted().joinToString()}")
                    ProfileEventRule(
                        profileId = o.getString(F_PROFILE),
                        calendarId = if (o.has(F_CALENDAR)) o.getLong(F_CALENDAR) else null,
                        keyword = o.getString(F_KEYWORD),
                        includeAllDay = o.getBoolean(F_ALL_DAY),
                    )
                }
                return ProfileSchedule(json.getString(F_DEFAULT), rules, eventRules)
            } catch (e: JSONException) {
                throw ProfileFormatException("Bad schedule", e)
            } catch (e: IllegalArgumentException) {
                throw ProfileFormatException(e.message ?: "Bad schedule value", e)
            }
        }
    }
}

/** Ids of the profiles that the old Home sets become. Their home apps stay in the old set keys. */
object HomeSetProfiles {
    const val MORNING_ID = "home-morning"
    const val DAY_ID = "home-day"
    const val EVENING_ID = "home-evening"

    fun idOf(set: HomeSet): String = when (set) {
        HomeSet.MORNING -> MORNING_ID
        HomeSet.DAY -> DAY_ID
        HomeSet.EVENING -> EVENING_ID
    }

    /** The Home set a profile shows; any profile that is not a migrated set shows the Day apps. */
    fun setOf(profileId: String): HomeSet = when (profileId) {
        MORNING_ID -> HomeSet.MORNING
        EVENING_ID -> HomeSet.EVENING
        else -> HomeSet.DAY
    }

    /** Sets in tie order: when two starts are equal, the earlier one here wins (as in [HomeSets.active]). */
    private val PRIORITY = listOf(HomeSet.DAY, HomeSet.MORNING, HomeSet.EVENING)

    /**
     * The schedule that gives the same set as [HomeSets.active] at every minute: each set runs
     * from its start to the next different start, every day. Day is the default.
     */
    fun scheduleFrom(starts: HomeSetStarts): ProfileSchedule {
        val rules = PRIORITY.mapNotNull { set ->
            val start = starts.startOf(set)
            val lostTie = PRIORITY.takeWhile { it != set }.any { starts.startOf(it) == start }
            if (lostTie) return@mapNotNull null
            val end = PRIORITY.map { starts.startOf(it) }.filter { it != start }
                .minByOrNull { HomeSets.normalize(it - start) } ?: start
            ProfileRule(idOf(set), start, end, ProfileSchedule.EVERY_DAY)
        }
        return ProfileSchedule(DAY_ID, rules)
    }
}

/** Picks the active profile id; context-free so it can be tested with a fixed clock and weekday. */
object ProfileScheduleResolver {
    /**
     * [windDownProfileId] is the profile wind-down forces (the Evening profile), or null when
     * wind-down does not force one. [manualProfileId] is a profile the user picked by hand; it beats
     * the schedule and wind-down, and its expiry is handled by the caller. Event rules beat time rules; [runningEvents] null (no calendar access) skips them. A profile other than the default that has no apps
     * ([hasApps] false) falls back to the default, as an empty Home set falls back to Day.
     */
    fun activeProfileId(
        schedule: ProfileSchedule,
        nowMinutes: Int,
        dayOfWeek: Int,
        windDownActive: Boolean,
        windDownProfileId: String?,
        hasApps: (String) -> Boolean = { true },
        manualProfileId: String? = null,
        runningEvents: List<CalendarEvent>? = null,
        nowMillis: Long = 0L,
    ): String {
        if (manualProfileId != null) {
            return if (manualProfileId != schedule.defaultProfileId && !hasApps(manualProfileId)) schedule.defaultProfileId else manualProfileId
        }
        if (windDownActive && windDownProfileId != null && hasApps(windDownProfileId)) return windDownProfileId
        val id = schedule.resolveEvent(runningEvents, nowMillis) ?: schedule.resolve(nowMinutes, dayOfWeek)
        return if (id != schedule.defaultProfileId && !hasApps(id)) schedule.defaultProfileId else id
    }

    /**
     * True when the profile must not be applied yet: event rules exist, the calendar can be read,
     * but the running events were not read yet. Applying then would show the time rule's profile
     * for a moment, and the event's profile after the read.
     */
    fun shouldDeferApply(hasEventRules: Boolean, calendarGranted: Boolean, eventsRead: Boolean): Boolean =
        hasEventRules && calendarGranted && !eventsRead

    /** The profile whose settings bundle must be applied now, or null when nothing changed or it has no bundle. */
    fun profileToApply(activeId: String, lastAppliedId: String, profiles: List<Profile>): Profile? {
        if (activeId == lastAppliedId) return null
        return profiles.firstOrNull { it.id == activeId }
    }
}
