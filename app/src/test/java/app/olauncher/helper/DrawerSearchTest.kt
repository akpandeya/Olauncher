package app.olauncher.helper

import android.os.UserHandle
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerSearchTest {

    private val dummyUser: UserHandle by lazy {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe")
        theUnsafeField.isAccessible = true
        val unsafe = theUnsafeField.get(null)
        val allocateInstance = unsafeClass.getMethod("allocateInstance", Class::class.java)
        allocateInstance.invoke(unsafe, UserHandle::class.java) as UserHandle
    }

    private fun app(label: String, pkg: String = "pkg.${label.lowercase().replace(" ", "")}") =
        AppModel.App(appLabel = label, key = null, appPackage = pkg, activityClassName = "$pkg.Main", user = dummyUser)

    private class FakeContacts : ContactSource {
        override fun contacts() = listOf(ContactEntry("Zebulon Quux", "lookup://zq"))
        override fun primaryNumber(lookupUri: String): String? = null
    }

    private fun input(
        query: String?,
        apps: List<AppModel> = emptyList(),
        flag: Int = Constants.FLAG_LAUNCH_APP,
        bottomSearch: Boolean = false,
        drawerRecents: Boolean = true,
        allowAutoLaunch: Boolean = true,
        german: Boolean = false,
        recents: List<RecentApp> = emptyList(),
        hidden: Set<String> = emptySet(),
        contacts: () -> ContactSource? = { null },
        calendar: (String) -> List<AppModel.Result> = { emptyList() },
        currency: (String) -> List<AppModel.Result> = { emptyList() },
    ) = DrawerSearchInput(
        query = query,
        apps = apps,
        flag = flag,
        bottomSearch = bottomSearch,
        drawerRecents = drawerRecents,
        allowAutoLaunch = allowAutoLaunch,
        germanEnabled = { german },
        recentApps = { recents },
        hiddenApps = { hidden },
        recentCaption = "Zuletzt",
        callRowText = { "Anruf $it" },
        saveWordRowText = { "Wort: $it" },
        saveWordsRowText = { n, shown -> "$n Wörter: $shown" },
        contactSource = contacts,
        calendarRows = calendar,
        currencyRows = currency,
        spellRows = { emptyList() },
        lookUpRows = { emptyList() },
        userKey = { "u0" },
    )

    @Test
    fun `prefix match scores above word start match`() {
        val prefix = app("Maps Pro")
        val wordStart = app("Pro Maps")
        val result = DrawerSearch.run(input("map", listOf(wordStart, prefix)))
        assertEquals(listOf<AppModel>(prefix, wordStart), result.items)
        assertTrue(result.scores[0] > result.scores[1])
    }

    @Test
    fun `accent insensitive match finds Cafe label`() {
        val cafe = app("Café")
        val result = DrawerSearch.run(input("cafe", listOf(cafe, app("Other"))))
        assertEquals(listOf<AppModel>(cafe), result.items)
    }

    @Test
    fun `renamed label is matched not the package`() {
        val renamed = app("Frobnicator", pkg = "com.zzz.original")
        val byLabel = DrawerSearch.run(input("frob", listOf(renamed)))
        val byPackage = DrawerSearch.run(input("zzz", listOf(renamed)))
        assertEquals(1, byLabel.items.size)
        assertTrue(byPackage.items.isEmpty())
    }

    @Test
    fun `hidden apps are left out of empty query recents`() {
        val shown = app("Alpha")
        val hiddenApp = app("Beta")
        val recents = listOf(
            RecentApp(hiddenApp.appPackage, "x", "u0"),
            RecentApp(shown.appPackage, "x", "u0"),
        )
        val hidden = setOf(hiddenApp.appPackage + "|u0")
        val result = DrawerSearch.run(input("", listOf(shown, hiddenApp), recents = recents, hidden = hidden))
        assertEquals("Zuletzt", (result.items[0] as AppModel.RecentHeader).title)
        val recent = result.items[1] as AppModel.App
        assertEquals(shown.appPackage, recent.appPackage)
        assertTrue(recent.isRecent)
        assertEquals(listOf("Alpha", "Beta"), result.items.drop(2).map { it.appLabel })
        assertEquals(4, result.items.size)
        assertTrue(result.scores.isEmpty())
    }

    @Test
    fun `calc dial contact and command rows come before apps`() {
        val a = app("Plain")
        val calc = DrawerSearch.run(input("2+2", listOf(app("2+2 app"))))
        assertEquals(AppModel.Result("= 4", AppModel.Result.Action.Copy("4")), calc.items[0])

        val dial = DrawerSearch.run(input("0301234", listOf(a)))
        assertEquals(AppModel.Result("Anruf 0301234", AppModel.Result.Action.Dial("0301234")), dial.items[0])

        val contact = DrawerSearch.run(input("zebulon", listOf(app("Zebulon Game")), contacts = { FakeContacts() }))
        assertEquals(AppModel.Result.Action.Contact("lookup://zq"), (contact.items[0] as AppModel.Result).action)
        assertTrue(contact.items[1] is AppModel.App)

        val cmd = DrawerSearch.run(input(":sw Haus, Baum, Tisch", listOf(a), german = true))
        val row = cmd.items[0] as AppModel.Result
        assertEquals("3 Wörter: Haus, Baum, …", row.text)
        assertEquals(AppModel.Result.Action.SaveWords(listOf("Haus", "Baum", "Tisch")), row.action)

        val one = DrawerSearch.run(input(":sw Haus", german = true))
        assertEquals("Wort: Haus", (one.items[0] as AppModel.Result).text)
    }

    @Test
    fun `bottomSearch flips rows and apps`() {
        val first = app("Mapa")
        val second = app("Mapb")
        val top = DrawerSearch.run(input("map", listOf(first, second)))
        val bottom = DrawerSearch.run(input("map", listOf(first, second), bottomSearch = true))
        assertEquals(top.items.reversed(), bottom.items)

        val calcBottom = DrawerSearch.run(input("2+2", listOf(app("2+2 app")), bottomSearch = true))
        assertTrue(calcBottom.items.last() is AppModel.Result)
    }

    @Test
    fun `throwing providers add no rows and do not crash`() {
        val a = app("Zebulon Game")
        val result = DrawerSearch.run(
            input(
                "zebulon",
                listOf(a),
                contacts = { throw IllegalStateException("revoked") },
                calendar = { throw SecurityException("no permission") },
                currency = { throw RuntimeException("offline") },
            )
        )
        assertEquals(listOf<AppModel>(a), result.items)
    }

    @Test
    fun `provider rows are included in order`() {
        val cur = AppModel.Result("1 EUR = 2 XYZ", AppModel.Result.Action.Copy("2"))
        val cal = AppModel.Result("Event", AppModel.Result.Action.Event(7L))
        val result = DrawerSearch.run(input("1 eur", currency = { listOf(cur) }, calendar = { listOf(cal) }))
        assertEquals(listOf<AppModel>(cur, cal), result.items.filterIsInstance<AppModel.Result>().filter { it === cur || it === cal })
    }

    @Test
    fun `bang leading space and colon flags`() {
        val bang = DrawerSearch.run(input("!pay"))
        assertTrue(bang.isBangSearch)
        assertTrue(bang.autoLaunch)

        val space = DrawerSearch.run(input(" map"))
        assertFalse(space.isBangSearch)
        assertFalse(space.autoLaunch)

        val colon = DrawerSearch.run(input(" :w Haus"))
        assertFalse(colon.autoLaunch)
        val colon2 = DrawerSearch.run(input(":w Haus"))
        assertFalse(colon2.autoLaunch)

        val plain = DrawerSearch.run(input("map"))
        assertFalse(plain.isBangSearch)
        assertTrue(plain.autoLaunch)
        assertEquals("map", plain.searchQuery)

        assertFalse(DrawerSearch.run(input("map", allowAutoLaunch = false)).autoLaunch)
    }
}
