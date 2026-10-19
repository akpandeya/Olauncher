package app.olauncher.helper

import android.os.UserHandle
import app.olauncher.data.AppModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentAppsTest {

    private val dummyUser: UserHandle by lazy {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe")
        theUnsafeField.isAccessible = true
        val unsafe = theUnsafeField.get(null)
        val allocateInstance = unsafeClass.getMethod("allocateInstance", Class::class.java)
        allocateInstance.invoke(unsafe, UserHandle::class.java) as UserHandle
    }

    private fun createApp(label: String, pkg: String = label.lowercase()): AppModel.App =
        AppModel.App(
            appLabel = label,
            key = null,
            appPackage = pkg,
            activityClassName = "com.example.$label",
            user = dummyUser,
        )

    @Test
    fun `push on empty list returns single item`() {
        val app = RecentApp("com.google.android.apps.maps", "com.google.android.maps.MapsActivity", "UserHandle{0}")
        val result = RecentApps.push(emptyList(), app, max = 3)
        assertEquals(listOf(app), result)
    }

    @Test
    fun `push distinct apps puts newest first up to max`() {
        val app1 = RecentApp("pkg1", "act1", "u0")
        val app2 = RecentApp("pkg2", "act2", "u0")
        val app3 = RecentApp("pkg3", "act3", "u0")

        var list = RecentApps.push(emptyList(), app1, max = 3)
        assertEquals(listOf(app1), list)

        list = RecentApps.push(list, app2, max = 3)
        assertEquals(listOf(app2, app1), list)

        list = RecentApps.push(list, app3, max = 3)
        assertEquals(listOf(app3, app2, app1), list)
    }

    @Test
    fun `push exceeding max drops oldest item`() {
        val app1 = RecentApp("pkg1", "act1", "u0")
        val app2 = RecentApp("pkg2", "act2", "u0")
        val app3 = RecentApp("pkg3", "act3", "u0")
        val app4 = RecentApp("pkg4", "act4", "u0")

        val list = listOf(app3, app2, app1)
        val result = RecentApps.push(list, app4, max = 3)
        assertEquals(listOf(app4, app3, app2), result)
    }

    @Test
    fun `push existing app moves it to front without duplicating`() {
        val app1 = RecentApp("pkg1", "act1", "u0")
        val app2 = RecentApp("pkg2", "act2", "u0")
        val app3 = RecentApp("pkg3", "act3", "u0")

        val list = listOf(app3, app2, app1)
        // Launch app1 again
        val result = RecentApps.push(list, app1, max = 3)
        assertEquals(listOf(app1, app3, app2), result)
        assertEquals(3, result.size)
    }

    @Test
    fun `push existing app with updated activity moves to front and updates activity`() {
        val app1 = RecentApp("pkg1", "actOld", "u0")
        val app2 = RecentApp("pkg2", "act2", "u0")
        val app1New = RecentApp("pkg1", "actNew", "u0")

        val list = listOf(app2, app1)
        val result = RecentApps.push(list, app1New, max = 3)
        assertEquals(listOf(app1New, app2), result)
        assertEquals("actNew", result.first().activityClassName)
    }

    @Test
    fun `push with custom max limits list size`() {
        val app1 = RecentApp("pkg1", "act1", "u0")
        val app2 = RecentApp("pkg2", "act2", "u0")
        val app3 = RecentApp("pkg3", "act3", "u0")

        val result = RecentApps.push(listOf(app2, app1), app3, max = 2)
        assertEquals(listOf(app3, app2), result)
    }

    @Test
    fun `push with max less than or equal to zero returns empty list`() {
        val app = RecentApp("pkg", "act", "u0")
        assertTrue(RecentApps.push(emptyList(), app, max = 0).isEmpty())
        assertTrue(RecentApps.push(emptyList(), app, max = -1).isEmpty())
    }

    @Test
    fun `push ignores invalid app with blank package or blank user`() {
        val app1 = RecentApp("pkg1", "act1", "u0")
        val invalidPkg = RecentApp("", "act", "u0")
        val invalidUser = RecentApp("pkg2", "act", "   ")

        val initial = listOf(app1)
        assertEquals(initial, RecentApps.push(initial, invalidPkg, max = 3))
        assertEquals(initial, RecentApps.push(initial, invalidUser, max = 3))
    }

    @Test
    fun `encode and decode round-trip preserves order and all fields`() {
        val apps = listOf(
            RecentApp("com.google.android.apps.maps", "com.google.android.maps.MapsActivity", "UserHandle{0}"),
            RecentApp("com.android.chrome", null, "UserHandle{0}"),
            RecentApp("org.mozilla.firefox", "org.mozilla.firefox.App", "UserHandle{10}")
        )
        val encoded = RecentApps.encode(apps)
        val decoded = RecentApps.decode(encoded)
        assertEquals(apps, decoded)
    }

    @Test
    fun `decode returns empty list on null, empty, or blank string`() {
        assertTrue(RecentApps.decode(null).isEmpty())
        assertTrue(RecentApps.decode("").isEmpty())
        assertTrue(RecentApps.decode("   \n\t  ").isEmpty())
    }

    @Test
    fun `decode corrupt strings returns empty list without crashing`() {
        val corrupt = listOf(
            "not a json",
            "{ not valid json",
            "[ unclosed array",
            "12345",
            "true",
            "null"
        )
        for (sample in corrupt) {
            assertTrue("Expected empty list for '$sample'", RecentApps.decode(sample).isEmpty())
        }
    }

    @Test
    fun `decode json object instead of array returns empty list without crashing`() {
        val json = """{"package": "com.test", "user": "UserHandle{0}"}"""
        assertTrue(RecentApps.decode(json).isEmpty())
    }

    @Test
    fun `decode skips malformed entries and keeps valid ones`() {
        val json = """
            [
                {"package": "pkg1", "activity": "act1", "user": "u0"},
                {"missing_keys": true},
                "not an object",
                {"package": "", "user": "u0"},
                {"package": "pkg2", "user": ""},
                {"package": "pkg3", "user": "u0"}
            ]
        """.trimIndent()
        val decoded = RecentApps.decode(json)
        assertEquals(2, decoded.size)
        assertEquals("pkg1", decoded[0].packageName)
        assertEquals("act1", decoded[0].activityClassName)
        assertEquals("u0", decoded[0].user)
        assertEquals("pkg3", decoded[1].packageName)
        assertNull(decoded[1].activityClassName)
        assertEquals("u0", decoded[1].user)
    }

    @Test
    fun `decode supports alternative keys packageName and activityClassName`() {
        val json = """
            [
                {"packageName": "pkgAlt", "activityClassName": "actAlt", "user": "u0"}
            ]
        """.trimIndent()
        val decoded = RecentApps.decode(json)
        assertEquals(1, decoded.size)
        assertEquals("pkgAlt", decoded[0].packageName)
        assertEquals("actAlt", decoded[0].activityClassName)
        assertEquals("u0", decoded[0].user)
    }

    @Test
    fun `forEmptySearch with empty recentApps returns search position ordering`() {
        val apps = listOf(createApp("A"), createApp("B"))
        assertEquals(
            apps,
            RecentApps.forEmptySearch(apps, emptyList(), "Recent", bottomSearch = false)
        )
        assertEquals(
            apps.reversed(),
            RecentApps.forEmptySearch(apps, emptyList(), "Recent", bottomSearch = true)
        )
    }

    @Test
    fun `forEmptySearch with top search puts caption then recents newest first above full list`() {
        val fullList = listOf(createApp("Alpha"), createApp("Beta"))
        val maps = createApp("Maps")
        val mail = createApp("Mail")
        val recentApps = listOf(maps, mail) // maps is newest

        val result = RecentApps.forEmptySearch(fullList, recentApps, "Recent", bottomSearch = false)

        assertEquals(5, result.size)
        // Caption at index 0
        assertTrue(result[0] is AppModel.RecentHeader)
        assertEquals("Recent", (result[0] as AppModel.RecentHeader).title)

        // Recent apps newest first
        assertEquals("Maps", result[1].appLabel)
        assertEquals("Mail", result[2].appLabel)

        // Followed by full list
        assertEquals("Alpha", result[3].appLabel)
        assertEquals("Beta", result[4].appLabel)
    }

    @Test
    fun `forEmptySearch with bottom search puts caption above recents with newest closest to search box`() {
        val fullList = listOf(createApp("Alpha"), createApp("Beta"))
        val maps = createApp("Maps")
        val mail = createApp("Mail")
        val recentApps = listOf(maps, mail) // maps is newest

        val result = RecentApps.forEmptySearch(fullList, recentApps, "Recent", bottomSearch = true)

        assertEquals(5, result.size)
        // Full list reversed at top
        assertEquals("Beta", result[0].appLabel)
        assertEquals("Alpha", result[1].appLabel)

        // Then caption above recents
        assertTrue(result[2] is AppModel.RecentHeader)
        assertEquals("Recent", (result[2] as AppModel.RecentHeader).title)

        // Recents with newest at the very end (closest to bottom search box)
        assertEquals("Mail", result[3].appLabel)
        assertEquals("Maps", result[4].appLabel)
        assertEquals("Maps", result.last().appLabel)
    }
}
