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
        commandHelpRowText = { "help $it" },
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
    fun `colon alone shows only enabled command rows that fill the query`() {
        val off = DrawerSearch.run(input(":", german = false)).items.filterIsInstance<AppModel.Result>()
        assertEquals(listOf("help t", "help a", "help e", "help n"), off.map { it.text })
        assertEquals(AppModel.Result.Action.FillQuery(":t "), off[0].action)
        val on = DrawerSearch.run(input(":", german = true)).items.filterIsInstance<AppModel.Result>()
        assertEquals(listOf("help t", "help a", "help e", "help n", "help sw", "help w"), on.map { it.text })
        assertEquals(AppModel.Result.Action.FillQuery(":sw "), on[4].action)
    }

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

    private fun commandRows(query: String) =
        DrawerSearch.run(input(query)).items.filterIsInstance<AppModel.Result>()

    @Test
    fun `valid timer alarm and event show a preview row with an action`() {
        val t = commandRows(":t 10 tea").first()
        assertEquals(AppModel.Result.Action.StartTimer(600, "tea"), t.action)
        val a = commandRows(":a 7:30 gym").first()
        assertTrue(a.action is AppModel.Result.Action.SetAlarm)
        val e = commandRows(":e dentist tomorrow 15:00").first()
        assertTrue(e.action is AppModel.Result.Action.AddEvent)
    }

    @Test
    fun `note command shows a save row with cleaned text and none when empty`() {
        val rows = commandRows(":n  call   Mama back")
        assertEquals("call Mama back", (rows.single().action as AppModel.Result.Action.SaveNote).text)
        assertTrue(commandRows(":n").isEmpty())
    }

    @Test
    fun `dated note row carries the date and unreadable date gives an error row`() {
        val dated = commandRows(":n pay rent 1.11").single()
        val action = dated.action as AppModel.Result.Action.SaveNote
        assertEquals("pay rent", action.text)
        assertEquals(java.time.LocalDate.now().let { t -> java.time.LocalDate.of(t.year, 11, 1).let { if (it.isBefore(t)) it.plusYears(1) else it } }, action.showFrom)
        val bad = commandRows(":n pay rent 32.13")
        assertEquals(listOf("Can't read '32.13'"), bad.map { it.text })
        assertEquals(AppModel.Result.Action.Error, bad.single().action)
    }

    @Test
    fun `unreadable timer and alarm input show a cant-read error row with no action`() {
        val t = commandRows(":t 25x tea")
        assertEquals(listOf("Can't read '25x'"), t.map { it.text })
        assertEquals(AppModel.Result.Action.Error, t[0].action)
        val a = commandRows(":a 99:99")
        assertEquals(listOf("Can't read '99:99'"), a.map { it.text })
        assertEquals(AppModel.Result.Action.Error, a[0].action)
    }

    @Test
    fun `unreadable event date shows an error row with no action`() {
        val e = commandRows(":e dentist 25.13")
        assertEquals(1, e.size)
        assertEquals("Can't read '25.13'", e[0].text)
        assertEquals(AppModel.Result.Action.Error, e[0].action)
    }

    @Test
    fun `commands without arguments show no preview or error row`() {
        assertTrue(commandRows(":t ").isEmpty())
        assertTrue(commandRows(":a ").isEmpty())
        assertTrue(commandRows(":e ").isEmpty())
    }

    @Test
    fun `error row text comes from the provided strings`() {
        val items = DrawerSearch.run(
            DrawerSearchInput(
                query = ":t zz", apps = emptyList(), flag = Constants.FLAG_LAUNCH_APP, bottomSearch = false,
                drawerRecents = false, allowAutoLaunch = true, germanEnabled = { false },
                recentApps = { emptyList() }, hiddenApps = { emptySet() }, recentCaption = "",
                callRowText = { it }, saveWordRowText = { it }, saveWordsRowText = { _, s -> s },
                contactSource = { null }, calendarRows = { emptyList() }, currencyRows = { emptyList() },
                spellRows = { emptyList() }, lookUpRows = { emptyList() },
                cantReadRowText = { "nicht lesbar: $it" },
            )
        ).items.filterIsInstance<AppModel.Result>()
        assertEquals("nicht lesbar: zz", items.single().text)
    }

    @Test
    fun `enter on an error row resolves to a result with the no-op error action`() {
        val row = commandRows(":t 25x").first()
        val action = EnterPolicy.resolve(":t 25x", row, null)
        assertEquals(EnterPolicy.Action.ExecuteResult(row), action)
        assertEquals(AppModel.Result.Action.Error, (row.action))
    }
}
