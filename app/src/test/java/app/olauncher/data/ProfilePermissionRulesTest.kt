package app.olauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePermissionRulesTest {
    private val profiles = BuiltInProfiles.all(emptyList())
    private fun settings(id: String) = profiles.first { it.id == id }.settings
    private val none = ProfilePermissions(weather = false, calendar = false, notificationAccess = false, contacts = false)

    @Test
    fun richWithNothingGrantedMissesEverythingInOrder() {
        // Rich does not switch quiet notifications on.
        assertEquals(
            ProfileFeature.values().toList() - ProfileFeature.QUIET,
            ProfilePermissionRules.missing(settings(BuiltInProfiles.RICH_ID), none),
        )
    }

    @Test
    fun bareMissesNothing() {
        assertTrue(ProfilePermissionRules.missing(settings(BuiltInProfiles.BARE_ID), none).isEmpty())
    }

    @Test
    fun focusMissesOnlyTheFeaturesItTurnsOn() {
        assertEquals(
            listOf(ProfileFeature.NEXT_EVENT, ProfileFeature.MUSIC_LINE, ProfileFeature.QUIET),
            ProfilePermissionRules.missing(settings(BuiltInProfiles.FOCUS_ID), none),
        )
    }

    @Test
    fun everythingGrantedMissesNothing() {
        assertTrue(ProfilePermissionRules.missing(settings(BuiltInProfiles.RICH_ID), ProfilePermissions.ALL).isEmpty())
    }

    @Test
    fun oneGrantedPermissionDropsOnlyItsFeatures() {
        val granted = none.copy(notificationAccess = true)
        assertEquals(
            listOf(ProfileFeature.WEATHER, ProfileFeature.CONTACTS_SEARCH, ProfileFeature.NEXT_EVENT),
            ProfilePermissionRules.missing(settings(BuiltInProfiles.RICH_ID), granted),
        )
    }

    @Test
    fun toAskMergesListenerFeaturesAndKeepsOrder() {
        val missing = listOf(
            ProfileFeature.WEATHER, ProfileFeature.NEXT_EVENT, ProfileFeature.MUSIC_LINE,
            ProfileFeature.MUSIC_PULSE, ProfileFeature.QUIET,
        )
        assertEquals(
            listOf(ProfilePermission.LOCATION, ProfilePermission.CALENDAR, ProfilePermission.NOTIFICATION_ACCESS),
            ProfilePermissionRules.toAsk(missing, emptySet()),
        )
    }

    @Test
    fun toAskDropsDeclined() {
        val missing = ProfileFeature.values().toList()
        assertEquals(
            listOf(ProfilePermission.CONTACTS, ProfilePermission.NOTIFICATION_ACCESS),
            ProfilePermissionRules.toAsk(missing, setOf(ProfilePermission.LOCATION, ProfilePermission.CALENDAR)),
        )
        assertTrue(ProfilePermissionRules.toAsk(missing, ProfilePermission.values().toSet()).isEmpty())
    }

    @Test
    fun declinedNamesRoundTripAndIgnoreUnknown() {
        val declined = setOf(ProfilePermission.CONTACTS, ProfilePermission.LOCATION)
        assertEquals(declined, ProfilePermissionRules.parseDeclined(ProfilePermissionRules.declinedNames(declined)))
        assertEquals(setOf(ProfilePermission.CALENDAR), ProfilePermissionRules.parseDeclined(setOf("CALENDAR", "BOGUS")))
    }

    @Test
    fun featuresForListsOnlyAskedPermissions() {
        assertEquals(
            listOf(ProfileFeature.CONTACTS_SEARCH),
            ProfilePermissionRules.featuresFor(ProfileFeature.values().toList(), listOf(ProfilePermission.CONTACTS)),
        )
    }
}
