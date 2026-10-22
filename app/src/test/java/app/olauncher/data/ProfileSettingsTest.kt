package app.olauncher.data

import app.olauncher.helper.MusicPulse
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Fake with distinguishing, non-default starting values; records which settings were written. */
private class FakeTarget : ProfileSettingsTarget {
    val written = mutableSetOf<String>()
    val cards = mutableMapOf<String, Boolean>()

    private fun <T> field(name: String, start: T) = object {
        var v = start
        operator fun getValue(r: Any?, p: Any?) = v
        operator fun setValue(r: Any?, p: Any?, value: T) { v = value; written += name }
    }

    override var homeAppsNum: Int by field("homeAppsNum", 99)
    override var dateTimeVisibility: Int by field("dateTimeVisibility", 77)
    override var weatherEnabled: Boolean by field("weatherEnabled", true)
    override var homeNextEvent: Boolean by field("homeNextEvent", true)
    override var homeContextLine: Boolean by field("homeContextLine", true)
    override var homeMusicPulse: Boolean by field("homeMusicPulse", true)
    override var homeGlowStrength: Int by field("homeGlowStrength", 41)
    override var homeWeatherEffects: Boolean by field("homeWeatherEffects", true)
    override var homeShortcuts: Boolean by field("homeShortcuts", true)
    override var shortcutCount: Int by field("shortcutCount", 11)
    override var intentionOnHome: Boolean by field("intentionOnHome", true)
    override var pauseEnabled: Boolean by field("pauseEnabled", true)
    override var pauseSeconds: Int by field("pauseSeconds", 17)
    override var pauseAllowanceMin: Int by field("pauseAllowanceMin", 23)
    override var pauseEscalate: Boolean by field("pauseEscalate", true)
    override var quietEnabled: Boolean by field("quietEnabled", true)
    override var windDown: Boolean by field("windDown", true)
    override var windDownGrey: Boolean by field("windDownGrey", true)
    override var nudgeWaterIntervalMin: Int by field("nudgeWaterIntervalMin", 33)
    override var nudgeWalkIntervalMin: Int by field("nudgeWalkIntervalMin", 44)
    override var todayOrderSmart: Boolean by field("todayOrderSmart", false)
    override var drawerRecents: Boolean by field("drawerRecents", true)
    override var drawerLetterStrip: Boolean by field("drawerLetterStrip", true)
    override var searchContacts: Boolean by field("searchContacts", true)

    override fun setTodayCardEnabled(key: String, enabled: Boolean) {
        cards[key] = enabled
        written += "card:$key"
    }
}

class ProfileSettingsTest {

    @Test
    fun `bare applies its table values and leaves unset sub-settings alone`() {
        val t = FakeTarget()
        BuiltInProfiles.bareSettings.apply(t)
        assertEquals(4, t.homeAppsNum)
        assertEquals(Constants.DateTime.ON, t.dateTimeVisibility)
        assertFalse(t.weatherEnabled)
        assertFalse(t.homeNextEvent)
        assertFalse(t.homeContextLine)
        assertFalse(t.homeMusicPulse)
        assertEquals(0, t.homeGlowStrength)
        assertFalse(t.homeWeatherEffects)
        assertFalse(t.homeShortcuts)
        assertFalse(t.intentionOnHome)
        assertFalse(t.pauseEnabled)
        assertFalse(t.quietEnabled)
        assertFalse(t.windDown)
        assertFalse(t.windDownGrey)
        assertEquals(0, t.nudgeWaterIntervalMin)
        assertEquals(0, t.nudgeWalkIntervalMin)
        assertFalse(t.drawerRecents)
        assertFalse(t.drawerLetterStrip)
        assertFalse(t.searchContacts)
        assertTrue(t.cards.values.none { it })
        // Left alone: the user's own values survive.
        assertEquals(11, t.shortcutCount)
        assertEquals(17, t.pauseSeconds)
        assertEquals(23, t.pauseAllowanceMin)
        assertTrue(t.pauseEscalate)
        assertFalse(t.todayOrderSmart)
        assertFalse("shortcutCount" in t.written)
        assertFalse("todayOrderSmart" in t.written)
    }

    @Test
    fun `focus applies its table values`() {
        val t = FakeTarget()
        BuiltInProfiles.focusSettings.apply(t)
        assertEquals(5, t.homeAppsNum)
        assertEquals(Constants.DateTime.ON, t.dateTimeVisibility)
        assertFalse(t.weatherEnabled)
        assertTrue(t.homeNextEvent)
        assertFalse(t.homeContextLine)
        assertFalse(t.homeMusicPulse)
        assertFalse(t.homeWeatherEffects)
        assertFalse(t.homeShortcuts)
        assertTrue(t.intentionOnHome)
        assertTrue(t.pauseEnabled)
        assertEquals(10, t.pauseAllowanceMin)
        assertTrue(t.pauseEscalate)
        assertEquals(17, t.pauseSeconds)
        assertTrue(t.quietEnabled)
        assertTrue(t.windDown)
        assertTrue(t.windDownGrey)
        assertEquals(90, t.nudgeWaterIntervalMin)
        assertEquals(120, t.nudgeWalkIntervalMin)
        assertTrue(t.drawerRecents)
        assertFalse(t.drawerLetterStrip)
        assertFalse(t.searchContacts)
        assertEquals(setOf("next_up", "held", "week"), t.cards.filterValues { it }.keys)
    }

    @Test
    fun `rich applies its table values`() {
        val t = FakeTarget()
        BuiltInProfiles.richSettings.apply(t)
        assertEquals(7, t.homeAppsNum)
        assertEquals(Constants.DateTime.ON, t.dateTimeVisibility)
        assertTrue(t.weatherEnabled)
        assertTrue(t.homeNextEvent)
        assertTrue(t.homeContextLine)
        assertTrue(t.homeMusicPulse)
        assertEquals(MusicPulse.GLOW_STRENGTH_DEFAULT, t.homeGlowStrength)
        assertTrue(t.homeWeatherEffects)
        assertTrue(t.homeShortcuts)
        assertEquals(4, t.shortcutCount)
        assertTrue(t.intentionOnHome)
        assertTrue(t.pauseEnabled)
        assertEquals(3, t.pauseSeconds)
        assertFalse(t.pauseEscalate)
        assertEquals(23, t.pauseAllowanceMin)
        assertFalse(t.quietEnabled)
        assertTrue(t.windDown)
        assertFalse(t.windDownGrey)
        assertEquals(60, t.nudgeWaterIntervalMin)
        assertEquals(90, t.nudgeWalkIntervalMin)
        assertTrue(t.todayOrderSmart)
        assertTrue(t.drawerRecents)
        assertTrue(t.drawerLetterStrip)
        assertTrue(t.searchContacts)
        assertEquals(ProfileSettings.KNOWN_CARDS.toSet(), t.cards.filterValues { it }.keys)
    }

    @Test
    fun `applying writes only settings the table names and no protected setting`() {
        val allowed = setOf(
            "homeAppsNum", "dateTimeVisibility", "weatherEnabled", "homeNextEvent", "homeContextLine",
            "homeMusicPulse", "homeGlowStrength", "homeWeatherEffects", "homeShortcuts", "shortcutCount",
            "intentionOnHome", "pauseEnabled", "pauseSeconds", "pauseAllowanceMin", "pauseEscalate",
            "quietEnabled", "windDown", "windDownGrey", "nudgeWaterIntervalMin", "nudgeWalkIntervalMin",
            "todayOrderSmart", "drawerRecents", "drawerLetterStrip", "searchContacts",
        ) + ProfileSettings.KNOWN_CARDS.map { "card:$it" }
        listOf(BuiltInProfiles.bareSettings, BuiltInProfiles.focusSettings, BuiltInProfiles.richSettings).forEach {
            val t = FakeTarget()
            it.apply(t)
            assertTrue(allowed.containsAll(t.written))
        }
        // The target type itself has no wallpaper, theme, font, accent, hidden apps or labels.
        val names = ProfileSettingsTarget::class.java.methods.map { it.name.lowercase() }
        listOf("wallpaper", "theme", "font", "accent", "hidden", "rename", "label").forEach { banned ->
            assertTrue(banned, names.none { banned in it })
        }
    }

    @Test
    fun `every known today card is written so switching is deterministic`() {
        val t = FakeTarget()
        BuiltInProfiles.focusSettings.apply(t)
        assertEquals(ProfileSettings.KNOWN_CARDS.toSet(), t.cards.keys)
    }

    @Test
    fun `json round trip keeps each built-in`() {
        listOf(BuiltInProfiles.bareSettings, BuiltInProfiles.focusSettings, BuiltInProfiles.richSettings).forEach {
            assertEquals(it, ProfileSettings.fromJson(JSONObject(it.toJson().toString())))
        }
    }

    @Test
    fun `unknown field is rejected and nothing is applied`() {
        val json = BuiltInProfiles.richSettings.toJson().put("wallpaper", "x")
        val t = FakeTarget()
        try {
            ProfileSettings.fromJson(json).apply(t)
            fail("expected rejection")
        } catch (_: ProfileFormatException) {
        }
        assertTrue(t.written.isEmpty())
    }

    @Test
    fun `missing field, wrong type and bad values are rejected`() {
        val base = BuiltInProfiles.focusSettings.toJson()
        fun rejects(json: JSONObject) = try {
            ProfileSettings.fromJson(json); false
        } catch (_: ProfileFormatException) {
            true
        }
        assertTrue(rejects(JSONObject(base.toString()).apply { remove("pause") }))
        assertTrue(rejects(JSONObject(base.toString()).put("pause", "yes")))
        assertTrue(rejects(JSONObject(base.toString()).put("homeAppsCount", 50)))
        assertTrue(rejects(JSONObject(base.toString()).put("todayCards", org.json.JSONArray(listOf("nope")))))
    }

    @Test
    fun `built-in home apps copy the current ones cut to the count`() {
        val current = (1..8).map { HomeApp("App$it", "pkg$it", "cls$it", "user$it", it == 2, "sc$it") }
        val profiles = BuiltInProfiles.all(current).associateBy { it.id }
        assertEquals(current.take(4), profiles.getValue("bare").homeApps)
        assertEquals(current.take(5), profiles.getValue("focus").homeApps)
        assertEquals(current.take(7), profiles.getValue("rich").homeApps)
        assertEquals(listOf("Bare", "Focus", "Rich"), BuiltInProfiles.all(emptyList()).map { it.name })
        assertTrue(BuiltInProfiles.all(emptyList()).all { it.homeApps.isEmpty() })
    }

    @Test
    fun `profile list json round trips and one bad entry rejects the list`() {
        val current = listOf(HomeApp("Mail", "p.mail", null, "u0"), HomeApp("Maps", "p.maps", "c.Maps", "u1", true, "sid"))
        val profiles = BuiltInProfiles.all(current)
        assertEquals(profiles, Profile.listFromJson(Profile.listToJson(profiles)))
        assertNull(Profile.listFromJson(Profile.listToJson(profiles)).first().homeApps.first().activityClassName)
        assertEquals(emptyList<Profile>(), Profile.listFromJson(""))
        val bad = org.json.JSONArray(Profile.listToJson(profiles))
        bad.getJSONObject(1).put("extra", 1)
        try {
            Profile.listFromJson(bad.toString())
            fail("expected rejection")
        } catch (_: ProfileFormatException) {
        }
    }
}
