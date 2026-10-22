package app.olauncher.data

import app.olauncher.helper.HomeSets

/**
 * A profile picked by hand. It holds until [untilMillis] (the next scheduled boundary);
 * With no upcoming boundary it holds for [NO_BOUNDARY_HOLD_MILLIS] only, so it can never block the
 * schedule for good. [NO_EXPIRY] marks an empty (none) override.
 */
data class ManualOverride(val profileId: String, val untilMillis: Long) {
    fun isActive(nowMillis: Long): Boolean =
        profileId.isNotEmpty() && (untilMillis == NO_EXPIRY || nowMillis < untilMillis)

    companion object {
        const val NO_EXPIRY = 0L
        const val MILLIS_PER_MINUTE = 60_000L
        const val NO_BOUNDARY_HOLD_MILLIS = 24 * 60 * MILLIS_PER_MINUTE
        val NONE = ManualOverride("", NO_EXPIRY)

        /** Holds [profileId] from [nowMillis] until the scheduled profile next changes. */
        fun create(
            profileId: String,
            schedule: ProfileSchedule,
            nowMillis: Long,
            nowMinutes: Int,
            dayOfWeek: Int,
        ): ManualOverride {
            val ahead = ProfileSwitch.minutesToNextBoundary(schedule, nowMinutes, dayOfWeek)
                ?: return ManualOverride(profileId, nowMillis + NO_BOUNDARY_HOLD_MILLIS)
            val startOfMinute = nowMillis / MILLIS_PER_MINUTE
            return ManualOverride(profileId, (startOfMinute + ahead) * MILLIS_PER_MINUTE)
        }
    }
}

/** A profile the user can switch to: [id] and the [name] shown for it. */
data class ProfileChoice(val id: String, val name: String)

/** Context-free rules for the manual switch. */
object ProfileSwitch {
    private const val DAYS = 7

    /**
     * Minutes from now until the scheduled profile (before any manual switch or wind-down)
     * differs from the one now; 1 or more. Null when it never changes within a week.
     * A boundary between two rules of the same profile is not a change.
     */
    fun minutesToNextBoundary(schedule: ProfileSchedule, nowMinutes: Int, dayOfWeek: Int): Int? {
        val now = HomeSets.normalize(nowMinutes)
        val current = schedule.resolve(now, dayOfWeek)
        for (ahead in 1..DAYS * HomeSets.MINUTES_PER_DAY) {
            val total = now + ahead
            val day = (dayOfWeek - 1 + total / HomeSets.MINUTES_PER_DAY) % DAYS + 1
            if (schedule.resolve(total % HomeSets.MINUTES_PER_DAY, day) != current) return ahead
        }
        return null
    }

    /**
     * The choice a `:p` query names: an exact id or name (any case) first, else the one choice
     * whose name starts with the query. Null when nothing or more than one matches.
     */
    fun find(query: String, choices: List<ProfileChoice>): ProfileChoice? {
        val q = query.trim()
        if (q.isEmpty()) return null
        choices.firstOrNull { it.id.equals(q, true) || it.name.equals(q, true) }?.let { return it }
        return choices.filter { it.name.startsWith(q, true) }.singleOrNull()
    }

    /** Choices whose id or name starts with [query]; all of them when it is blank. */
    fun matching(query: String, choices: List<ProfileChoice>): List<ProfileChoice> {
        val q = query.trim()
        if (q.isEmpty()) return choices
        return choices.filter { it.id.startsWith(q, true) || it.name.startsWith(q, true) }
    }
}

/** What the profile row shows: a profile, or a profile the user has changed settings of. */
sealed class ProfileState {
    data object None : ProfileState()
    data class Active(val name: String) : ProfileState()
    data class Custom(val basedOn: Profile) : ProfileState()
}

/** Settings that can be read as well as written, to compare them with a profile's bundle. */
interface ProfileSettingsSource : ProfileSettingsTarget {
    fun isTodayCardEnabled(key: String): Boolean
}

/** In-memory copy of the settings; equal copies mean equal settings. */
private data class MemorySettings(
    override var homeAppsNum: Int,
    override var dateTimeVisibility: Int,
    override var weatherEnabled: Boolean,
    override var homeNextEvent: Boolean,
    override var homeContextLine: Boolean,
    override var homeMusicPulse: Boolean,
    override var homeGlowStrength: Int,
    override var homeWeatherEffects: Boolean,
    override var homeShortcuts: Boolean,
    override var shortcutCount: Int,
    override var intentionOnHome: Boolean,
    override var pauseEnabled: Boolean,
    override var pauseSeconds: Int,
    override var pauseAllowanceMin: Int,
    override var pauseEscalate: Boolean,
    override var quietEnabled: Boolean,
    override var windDown: Boolean,
    override var windDownGrey: Boolean,
    override var nudgeWaterIntervalMin: Int,
    override var nudgeWalkIntervalMin: Int,
    override var todayOrderSmart: Boolean,
    override var drawerRecents: Boolean,
    override var drawerLetterStrip: Boolean,
    override var searchContacts: Boolean,
    val cardsOn: MutableSet<String>,
) : ProfileSettingsTarget {
    override fun setTodayCardEnabled(key: String, enabled: Boolean) {
        if (enabled) cardsOn.add(key) else cardsOn.remove(key)
    }

    fun duplicate() = copy(cardsOn = cardsOn.toMutableSet())

    companion object {
        fun of(s: ProfileSettingsSource) = MemorySettings(
            s.homeAppsNum, s.dateTimeVisibility, s.weatherEnabled, s.homeNextEvent, s.homeContextLine,
            s.homeMusicPulse, s.homeGlowStrength, s.homeWeatherEffects, s.homeShortcuts, s.shortcutCount,
            s.intentionOnHome, s.pauseEnabled, s.pauseSeconds, s.pauseAllowanceMin, s.pauseEscalate,
            s.quietEnabled, s.windDown, s.windDownGrey, s.nudgeWaterIntervalMin, s.nudgeWalkIntervalMin,
            s.todayOrderSmart, s.drawerRecents, s.drawerLetterStrip, s.searchContacts,
            ProfileSettings.KNOWN_CARDS.filter { s.isTodayCardEnabled(it) }.toMutableSet(),
        )
    }
}

/** Decides whether the user has changed the settings of the profile that was applied. */
object ProfileCustom {
    /**
     * True when applying [profile]'s bundle to [current] would change something, that is, the user
     * changed a setting the bundle sets. Settings the bundle leaves alone (null) never count.
     */
    fun isCustom(
        profile: Profile,
        current: ProfileSettingsSource,
        permissions: ProfilePermissions = ProfilePermissions.ALL,
    ): Boolean {
        val before = MemorySettings.of(current)
        val after = before.duplicate()
        // A feature skipped for a missing permission is not a change by the user.
        ProfilePicker.applyBundle(profile.settings, after, permissions)
        return before != after
    }

    /** The profile row state for [appliedId]; [profiles] holds every profile that has a bundle. */
    fun state(
        appliedId: String,
        profiles: List<Profile>,
        nameOf: (String) -> String,
        current: ProfileSettingsSource,
        permissions: ProfilePermissions = ProfilePermissions.ALL,
    ): ProfileState {
        if (appliedId.isEmpty()) return ProfileState.None
        val profile = profiles.firstOrNull { it.id == appliedId } ?: return ProfileState.Active(nameOf(appliedId))
        return if (isCustom(profile, current, permissions)) ProfileState.Custom(profile) else ProfileState.Active(profile.name)
    }

    /**
     * [base] with the values now in [current]. A setting [base] leaves alone (null) stays left alone,
     * so "Save to X" keeps what X chose not to control.
     */
    fun capture(base: ProfileSettings, current: ProfileSettingsSource): ProfileSettings {
        val dt = current.dateTimeVisibility
        return ProfileSettings(
            homeAppsCount = current.homeAppsNum.coerceIn(0, HomeSets.MAX_SLOTS),
            showClock = dt == Constants.DateTime.ON || dt == Constants.DateTime.CLOCK_ONLY,
            showDate = dt == Constants.DateTime.ON || dt == Constants.DateTime.DATE_ONLY,
            showWeather = current.weatherEnabled,
            nextEvent = current.homeNextEvent,
            musicLine = current.homeContextLine,
            musicPulse = current.homeMusicPulse,
            glowStrength = current.homeGlowStrength.coerceIn(0, 100),
            weatherEffects = current.homeWeatherEffects,
            shortcutIcons = current.homeShortcuts,
            shortcutCount = base.shortcutCount?.let { current.shortcutCount },
            intentionLine = current.intentionOnHome,
            pause = current.pauseEnabled,
            pauseSeconds = base.pauseSeconds?.let { current.pauseSeconds },
            pauseAllowanceMin = base.pauseAllowanceMin?.let { current.pauseAllowanceMin },
            pauseEscalate = base.pauseEscalate?.let { current.pauseEscalate },
            quietNotifications = current.quietEnabled,
            windDown = current.windDown,
            windDownGrey = current.windDownGrey,
            nudgeWaterIntervalMin = current.nudgeWaterIntervalMin,
            nudgeWalkIntervalMin = current.nudgeWalkIntervalMin,
            todayCards = ProfileSettings.KNOWN_CARDS.filter { current.isTodayCardEnabled(it) }.toSet(),
            todayOrderSmart = base.todayOrderSmart?.let { current.todayOrderSmart },
            drawerRecents = current.drawerRecents,
            drawerLetterStrip = current.drawerLetterStrip,
            drawerContacts = current.searchContacts,
        )
    }
}
