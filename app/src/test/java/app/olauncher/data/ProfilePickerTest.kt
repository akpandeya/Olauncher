package app.olauncher.data

import app.olauncher.helper.Onboarding
import app.olauncher.helper.OnboardingStep
import app.olauncher.helper.OnboardingStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings with distinguishing, non-default values; readable and writable. */
private class PickerSettings : ProfileSettingsSource {
    override var homeAppsNum = 6
    override var dateTimeVisibility = Constants.DateTime.DATE_ONLY
    override var weatherEnabled = true
    override var homeNextEvent = false
    override var homeContextLine = false
    override var homeMusicPulse = true
    override var homeGlowStrength = 41
    override var homeWeatherEffects = true
    override var homeShortcuts = true
    override var shortcutCount = 11
    override var intentionOnHome = false
    override var pauseEnabled = false
    override var pauseSeconds = 17
    override var pauseAllowanceMin = 23
    override var pauseEscalate = true
    override var quietEnabled = true
    override var windDown = false
    override var windDownGrey = true
    override var nudgeWaterIntervalMin = 33
    override var nudgeWalkIntervalMin = 44
    override var todayOrderSmart = false
    override var drawerRecents = false
    override var drawerLetterStrip = true
    override var searchContacts = true
    val cards = mutableSetOf(ProfileSettings.KNOWN_CARDS.first())

    override fun setTodayCardEnabled(key: String, enabled: Boolean) {
        if (enabled) cards.add(key) else cards.remove(key)
    }

    override fun isTodayCardEnabled(key: String) = key in cards

    /** Every setting as one comparable value. */
    fun snapshot(): List<Any> = listOf(
        homeAppsNum, dateTimeVisibility, weatherEnabled, homeNextEvent, homeContextLine, homeMusicPulse,
        homeGlowStrength, homeWeatherEffects, homeShortcuts, shortcutCount, intentionOnHome, pauseEnabled,
        pauseSeconds, pauseAllowanceMin, pauseEscalate, quietEnabled, windDown, windDownGrey,
        nudgeWaterIntervalMin, nudgeWalkIntervalMin, todayOrderSmart, drawerRecents, drawerLetterStrip,
        searchContacts, cards.toSet(),
    )
}

class ProfilePickerTest {
    private val profiles = BuiltInProfiles.all(emptyList())

    @Test
    fun stepOffersBareFocusRichAndKeepDefaults() {
        assertEquals(
            listOf("bare", "focus", "rich", null),
            ProfilePickerOption.values().map { it.profileId },
        )
        assertEquals(ProfilePickerOption.KEEP_DEFAULTS, ProfilePicker.initial)
    }

    @Test
    fun keepDefaultsLeavesEverySettingUnchanged() {
        val settings = PickerSettings()
        val before = settings.snapshot()
        assertFalse(ProfilePicker.apply(ProfilePickerOption.KEEP_DEFAULTS, profiles, settings))
        assertNull(ProfilePicker.profileIdToApply(ProfilePicker.initial))
        assertEquals(before, settings.snapshot())
    }

    @Test
    fun skippingFinishesWelcomeWithoutApplyingAnything() {
        val settings = PickerSettings()
        val before = settings.snapshot()
        val skipped = Onboarding.skipped(OnboardingStore.stateFor(OnboardingStep.PICK_PROFILE.name))
        assertTrue(skipped.isComplete)
        assertEquals(OnboardingStore.COMPLETE, OnboardingStore.encode(skipped))
        assertEquals(before, settings.snapshot())
    }

    @Test
    fun missingProfileWritesNothing() {
        val settings = PickerSettings()
        val before = settings.snapshot()
        assertFalse(ProfilePicker.apply(ProfilePickerOption.RICH, profiles.filter { it.id != "rich" }, settings))
        assertEquals(before, settings.snapshot())
    }

    @Test
    fun choosingBareAppliesExactlyItsBundle() {
        val settings = PickerSettings()
        assertTrue(ProfilePicker.apply(ProfilePickerOption.BARE, profiles, settings))
        assertEquals(4, settings.homeAppsNum)
        assertEquals(Constants.DateTime.CLOCK_ONLY, settings.dateTimeVisibility)
        assertFalse(settings.weatherEnabled)
        assertFalse(settings.homeShortcuts)
        assertEquals(0, settings.nudgeWaterIntervalMin)
        assertTrue(settings.cards.isEmpty())
        // Settings the bundle leaves alone keep their own values.
        assertEquals(11, settings.shortcutCount)
        assertEquals(17, settings.pauseSeconds)
        assertEquals(23, settings.pauseAllowanceMin)
        assertTrue(settings.pauseEscalate)
        assertFalse(settings.todayOrderSmart)
        assertFalse(ProfileCustom.isCustom(BuiltInProfiles.all(emptyList())[0], settings))
    }

    @Test
    fun choosingFocusAppliesExactlyItsBundle() {
        val settings = PickerSettings()
        assertTrue(ProfilePicker.apply(ProfilePickerOption.FOCUS, profiles, settings))
        assertEquals(5, settings.homeAppsNum)
        assertEquals(Constants.DateTime.ON, settings.dateTimeVisibility)
        assertTrue(settings.pauseEnabled)
        assertEquals(10, settings.pauseAllowanceMin)
        assertEquals(90, settings.nudgeWaterIntervalMin)
        assertEquals(120, settings.nudgeWalkIntervalMin)
        assertTrue(settings.windDown)
        assertEquals(11, settings.shortcutCount)
        assertEquals(17, settings.pauseSeconds)
        assertFalse(ProfileCustom.isCustom(profiles[1], settings))
    }

    @Test
    fun richWithWeatherPermissionDeniedKeepsWeatherAndAppliesTheRest() {
        val settings = PickerSettings()
        assertTrue(ProfilePicker.wantsWeather(ProfilePickerOption.RICH, profiles))
        assertFalse(ProfilePicker.wantsWeather(ProfilePickerOption.FOCUS, profiles))
        assertTrue(ProfilePicker.apply(ProfilePickerOption.RICH, profiles, settings, weatherGranted = false))
        // Weather and effects keep their earlier values (here: weather on, effects on at start).
        val off = PickerSettings().apply { weatherEnabled = false; homeWeatherEffects = false }
        assertTrue(ProfilePicker.apply(ProfilePickerOption.RICH, profiles, off, weatherGranted = false))
        assertFalse(off.weatherEnabled)
        assertFalse(off.homeWeatherEffects)
        assertEquals(7, off.homeAppsNum)
        assertEquals(4, off.shortcutCount)
        assertTrue(off.homeShortcuts)
        assertTrue(off.homeMusicPulse)
        assertEquals(ProfileSettings.KNOWN_CARDS.toSet(), off.cards)
        // Granted: weather and effects come on with the bundle.
        val granted = PickerSettings().apply { weatherEnabled = false; homeWeatherEffects = false }
        assertTrue(ProfilePicker.apply(ProfilePickerOption.RICH, profiles, granted, weatherGranted = true))
        assertTrue(granted.weatherEnabled)
        assertTrue(granted.homeWeatherEffects)
    }

    @Test
    fun denialDoesNotAffectProfilesWithoutWeather() {
        val settings = PickerSettings()
        assertTrue(ProfilePicker.apply(ProfilePickerOption.BARE, profiles, settings, weatherGranted = false))
        assertFalse(settings.weatherEnabled)
        assertFalse(settings.homeWeatherEffects)
    }

    @Test
    fun choosingRichAppliesExactlyItsBundleAndProfilesDiffer() {
        val settings = PickerSettings()
        assertTrue(ProfilePicker.apply(ProfilePickerOption.RICH, profiles, settings))
        assertEquals(7, settings.homeAppsNum)
        assertTrue(settings.weatherEnabled)
        assertEquals(4, settings.shortcutCount)
        assertEquals(3, settings.pauseSeconds)
        assertEquals(ProfileSettings.KNOWN_CARDS.toSet(), settings.cards)
        assertTrue(settings.todayOrderSmart)
        assertEquals(23, settings.pauseAllowanceMin)
        assertFalse(ProfileCustom.isCustom(profiles[2], settings))

        val bare = PickerSettings().also { ProfilePicker.apply(ProfilePickerOption.BARE, profiles, it) }
        assertNotEquals(bare.snapshot(), settings.snapshot())
    }

    private val none = ProfilePermissions(weather = false, calendar = false, notificationAccess = false, contacts = false)

    private fun richOnto(permissions: ProfilePermissions): PickerSettings {
        val settings = PickerSettings().apply { homeNextEvent = false; homeContextLine = false; searchContacts = false }
        assertTrue(ProfilePicker.apply(ProfilePickerOption.RICH, profiles, settings, permissions = permissions))
        return settings
    }

    @Test
    fun missingPermissionsKeepTheirFeaturesOffAndTheRestIsApplied() {
        val s = richOnto(none)
        assertFalse(s.homeNextEvent)
        assertFalse(s.homeContextLine)
        assertFalse(s.searchContacts)
        assertTrue(s.homeMusicPulse == true)
        assertEquals(7, s.homeAppsNum)
    }

    @Test
    fun quietNotificationsStayAsTheyWereWithoutNotificationAccess() {
        val focus = profiles.first { it.id == "focus" }
        val s = PickerSettings().apply { quietEnabled = false }
        ProfilePicker.applyBundle(focus.settings, s, none)
        assertFalse(s.quietEnabled)
        ProfilePicker.applyBundle(focus.settings, s, none.copy(notificationAccess = true))
        assertTrue(s.quietEnabled)
    }

    @Test
    fun eachPermissionOnlyUnlocksItsOwnFeature() {
        val calendar = richOnto(none.copy(calendar = true))
        assertTrue(calendar.homeNextEvent)
        assertFalse(calendar.homeContextLine)
        assertFalse(calendar.searchContacts)
        val notif = richOnto(none.copy(notificationAccess = true))
        assertTrue(notif.homeContextLine)
        assertFalse(notif.homeNextEvent)
        val contacts = richOnto(none.copy(contacts = true))
        assertTrue(contacts.searchContacts)
        assertFalse(contacts.homeNextEvent)
        val all = richOnto(ProfilePermissions.ALL)
        assertTrue(all.homeNextEvent && all.homeContextLine && all.searchContacts)
    }

    @Test
    fun aSkippedFeatureDoesNotMakeTheProfileCustom() {
        val rich = profiles.first { it.id == "rich" }
        val s = richOnto(none)
        assertFalse(ProfileCustom.isCustom(rich, s, none))
        assertTrue(ProfileCustom.isCustom(rich, s, ProfilePermissions.ALL))
        assertEquals(ProfileState.Active("Rich"), ProfileCustom.state("rich", profiles, { it }, s, none))
        s.homeShortcuts = !s.homeShortcuts
        assertTrue(ProfileCustom.isCustom(rich, s, none))
    }
}
