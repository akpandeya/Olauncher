package app.olauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun hm(h: Int, m: Int = 0) = h * 60 + m

/** Settings with distinguishing, non-default values; readable and writable. */
private class FakeSource(base: ProfileSettings) : ProfileSettingsSource {
    override var homeAppsNum = 7
    override var dateTimeVisibility = Constants.DateTime.DATE_ONLY
    override var weatherEnabled = true
    override var homeNextEvent = true
    override var homeContextLine = true
    override var homeMusicPulse = true
    override var homeGlowStrength = 41
    override var homeWeatherEffects = true
    override var homeShortcuts = true
    override var shortcutCount = 11
    override var intentionOnHome = true
    override var pauseEnabled = true
    override var pauseSeconds = 17
    override var pauseAllowanceMin = 23
    override var pauseEscalate = true
    override var quietEnabled = true
    override var windDown = true
    override var windDownGrey = true
    override var nudgeWaterIntervalMin = 33
    override var nudgeWalkIntervalMin = 44
    override var todayOrderSmart = false
    override var drawerRecents = true
    override var drawerLetterStrip = true
    override var searchContacts = true
    val cards = ProfileSettings.KNOWN_CARDS.toMutableSet()

    override fun setTodayCardEnabled(key: String, enabled: Boolean) {
        if (enabled) cards.add(key) else cards.remove(key)
    }

    override fun isTodayCardEnabled(key: String) = key in cards

    init {
        base.apply(this)
    }
}

class ProfileSwitchTest {

    private val weekdays = setOf(1, 2, 3, 4, 5)
    private val schedule = ProfileSchedule("rich", listOf(ProfileRule("focus", hm(9), hm(17), weekdays)))
    private val monday = 1
    private val saturday = 6

    // ---- Boundary and override expiry ----

    @Test
    fun nextBoundaryIsWhenTheScheduledProfileChanges() {
        assertEquals(hm(17) - hm(10), ProfileSwitch.minutesToNextBoundary(schedule, hm(10), monday))
        assertEquals(1, ProfileSwitch.minutesToNextBoundary(schedule, hm(8, 59), monday))
        // Friday evening: the next change is Monday 09:00.
        assertEquals(hm(24) - hm(20) + 2 * 24 * 60 + hm(9), ProfileSwitch.minutesToNextBoundary(schedule, hm(20), 5))
        // Saturday: Monday 09:00 is still two days away.
        assertEquals(hm(24) - hm(12) + 24 * 60 + hm(9), ProfileSwitch.minutesToNextBoundary(schedule, hm(12), saturday))
    }

    @Test
    fun boundaryBetweenRulesOfTheSameProfileIsNotABoundary() {
        val s = ProfileSchedule("rich", listOf(
            ProfileRule("focus", hm(9), hm(12), weekdays),
            ProfileRule("focus", hm(12), hm(17), weekdays),
        ))
        assertEquals(hm(17) - hm(10), ProfileSwitch.minutesToNextBoundary(s, hm(10), monday))
    }

    @Test
    fun noBoundaryWhenTheScheduleNeverChanges() {
        assertNull(ProfileSwitch.minutesToNextBoundary(ProfileSchedule("rich", emptyList()), hm(10), monday))
        val allDay = ProfileSchedule("rich", listOf(ProfileRule("rich", hm(9), hm(17), weekdays)))
        assertNull(ProfileSwitch.minutesToNextBoundary(allDay, hm(10), monday))
    }

    @Test
    fun overrideHoldsUntilTheNextBoundaryAndThenExpires() {
        val now = 1_000_000_000_000L
        val o = ManualOverride.create("bare", schedule, now, hm(10), monday)
        val boundary = (now / 60_000L + (hm(17) - hm(10))) * 60_000L
        assertEquals(ManualOverride("bare", boundary), o)
        assertTrue(o.isActive(now))
        assertTrue(o.isActive(boundary - 1))
        assertFalse(o.isActive(boundary))
        assertFalse(o.isActive(boundary + 5 * 60_000L))
    }

    @Test
    fun overrideWithNoBoundaryHoldsForABoundedTimeThenExpires() {
        val now = 5_000_000L
        val o = ManualOverride.create("bare", ProfileSchedule("rich", emptyList()), now, hm(10), monday)
        assertEquals(now + ManualOverride.NO_BOUNDARY_HOLD_MILLIS, o.untilMillis)
        assertTrue(o.isActive(now))
        assertTrue(o.isActive(now + ManualOverride.NO_BOUNDARY_HOLD_MILLIS - 1))
        assertFalse(o.isActive(now + ManualOverride.NO_BOUNDARY_HOLD_MILLIS))
    }

    @Test
    fun overrideWithOnlyEventRulesIsBoundedToo() {
        val events = ProfileSchedule("rich", emptyList(), listOf(ProfileEventRule("bare", keyword = "work")))
        val o = ManualOverride.create("focus", events, 7L, hm(10), monday)
        assertEquals(7L + ManualOverride.NO_BOUNDARY_HOLD_MILLIS, o.untilMillis)
        assertFalse(o.isActive(7L + ManualOverride.NO_BOUNDARY_HOLD_MILLIS))
    }

    @Test
    fun noOverrideIsNeverActive() {
        assertFalse(ManualOverride.NONE.isActive(0L))
        assertFalse(ManualOverride.NONE.isActive(Long.MAX_VALUE))
    }

    @Test
    fun manualProfileBeatsScheduleAndWindDown() {
        fun active(manual: String?, windDown: Boolean) = ProfileScheduleResolver.activeProfileId(
            schedule = schedule, nowMinutes = hm(10), dayOfWeek = monday,
            windDownActive = windDown, windDownProfileId = "evening", manualProfileId = manual,
        )
        assertEquals("focus", active(null, false))
        assertEquals("evening", active(null, true))
        assertEquals("bare", active("bare", false))
        assertEquals("bare", active("bare", true))
    }

    @Test
    fun manualProfileWithoutAppsFallsBackToTheDefault() {
        val id = ProfileScheduleResolver.activeProfileId(
            schedule = schedule, nowMinutes = hm(10), dayOfWeek = monday,
            windDownActive = false, windDownProfileId = null, hasApps = { false }, manualProfileId = "evening",
        )
        assertEquals("rich", id)
    }

    // ---- :p lookup ----

    private val choices = listOf(ProfileChoice("bare", "Bare"), ProfileChoice("focus", "Focus"), ProfileChoice("home-evening", "Evening"))

    @Test
    fun findMatchesIdNameOrUniquePrefix() {
        assertEquals("focus", ProfileSwitch.find("focus", choices)?.id)
        assertEquals("focus", ProfileSwitch.find("  FOC ", choices)?.id)
        assertEquals("home-evening", ProfileSwitch.find("home-evening", choices)?.id)
        assertNull(ProfileSwitch.find("", choices))
        assertNull(ProfileSwitch.find("zzz", choices))
        assertNull(ProfileSwitch.find("e", listOf(ProfileChoice("a", "Eve"), ProfileChoice("b", "Eden"))))
    }

    @Test
    fun matchingListsAllWhenBlank() {
        assertEquals(choices, ProfileSwitch.matching("", choices))
        assertEquals(listOf("focus"), ProfileSwitch.matching("f", choices).map { it.id })
    }

    // ---- Custom state ----

    private val focus = Profile(BuiltInProfiles.FOCUS_ID, "Focus", emptyList(), BuiltInProfiles.focusSettings)

    @Test
    fun appliedBundleIsNotCustom() {
        val source = FakeSource(focus.settings)
        assertFalse(ProfileCustom.isCustom(focus, source))
    }

    @Test
    fun changingAnySettingOfTheBundleMakesItCustom() {
        val checks: List<(FakeSource) -> Unit> = listOf(
            { it.homeAppsNum = it.homeAppsNum + 1 },
            { it.dateTimeVisibility = Constants.DateTime.OFF.takeIf { v -> v != it.dateTimeVisibility } ?: Constants.DateTime.ON },
            { it.weatherEnabled = !it.weatherEnabled },
            { it.homeNextEvent = !it.homeNextEvent },
            { it.homeGlowStrength = it.homeGlowStrength + 1 },
            { it.pauseEnabled = !it.pauseEnabled },
            { it.windDown = !it.windDown },
            { it.nudgeWaterIntervalMin = it.nudgeWaterIntervalMin + 1 },
            { it.drawerRecents = !it.drawerRecents },
            { it.searchContacts = !it.searchContacts },
            { s -> ProfileSettings.KNOWN_CARDS.first().let { s.setTodayCardEnabled(it, !s.isTodayCardEnabled(it)) } },
        )
        checks.forEachIndexed { i, change ->
            val source = FakeSource(focus.settings)
            change(source)
            assertTrue("change #$i should be Custom", ProfileCustom.isCustom(focus, source))
        }
    }

    @Test
    fun settingsTheBundleLeavesAloneNeverMakeItCustom() {
        val loose = focus.copy(settings = focus.settings.copy(shortcutCount = null, pauseSeconds = null, pauseAllowanceMin = null, pauseEscalate = null, todayOrderSmart = null))
        val source = FakeSource(loose.settings)
        source.shortcutCount = 99
        source.pauseSeconds = 98
        source.pauseAllowanceMin = 97
        source.pauseEscalate = !source.pauseEscalate
        source.todayOrderSmart = !source.todayOrderSmart
        assertFalse(ProfileCustom.isCustom(loose, source))
    }

    @Test
    fun changeThenChangeBackIsNotCustom() {
        val source = FakeSource(focus.settings)
        val before = source.homeAppsNum
        source.homeAppsNum = before + 2
        assertTrue(ProfileCustom.isCustom(focus, source))
        source.homeAppsNum = before
        assertFalse(ProfileCustom.isCustom(focus, source))
    }

    @Test
    fun stateIsNoneActiveOrCustomBasedOnTheAppliedProfile() {
        val source = FakeSource(focus.settings)
        val name = { id: String -> "name:$id" }
        assertEquals(ProfileState.None, ProfileCustom.state("", listOf(focus), name, source))
        assertEquals(ProfileState.Active("Focus"), ProfileCustom.state("focus", listOf(focus), name, source))
        // A profile without a bundle (a Home set) is never Custom.
        assertEquals(ProfileState.Active("name:home-day"), ProfileCustom.state("home-day", listOf(focus), name, source))
        source.weatherEnabled = !source.weatherEnabled
        assertEquals(ProfileState.Custom(focus), ProfileCustom.state("focus", listOf(focus), name, source))
        // Same settings, other applied profile: Rich differs from Focus, so it reads as Custom (based on Rich).
        val rich = Profile(BuiltInProfiles.RICH_ID, "Rich", emptyList(), BuiltInProfiles.richSettings)
        assertEquals(ProfileState.Custom(rich), ProfileCustom.state("rich", listOf(focus, rich), name, FakeSource(focus.settings)))
    }

    @Test
    fun saveToProfileMakesTheCurrentSettingsTheBundle() {
        val source = FakeSource(focus.settings)
        source.homeAppsNum = 5
        source.dateTimeVisibility = Constants.DateTime.CLOCK_ONLY
        source.todayOrderSmart = true
        assertTrue(ProfileCustom.isCustom(focus, source))
        val saved = focus.copy(settings = ProfileCustom.capture(focus.settings, source))
        assertEquals(5, saved.settings.homeAppsCount)
        assertTrue(saved.settings.showClock)
        assertFalse(saved.settings.showDate)
        assertFalse(ProfileCustom.isCustom(saved, source))
    }

    @Test
    fun saveKeepsSettingsTheProfileLeavesAloneUnset() {
        val loose = focus.settings.copy(shortcutCount = null, pauseEscalate = null)
        val captured = ProfileCustom.capture(loose, FakeSource(loose))
        assertNull(captured.shortcutCount)
        assertNull(captured.pauseEscalate)
        assertEquals(loose.pause, captured.pause)
    }

    @Test
    fun resetAppliesTheBundleAgain() {
        val source = FakeSource(focus.settings)
        source.homeAppsNum = 8
        source.drawerRecents = !source.drawerRecents
        focus.settings.apply(source)
        assertFalse(ProfileCustom.isCustom(focus, source))
    }
}
