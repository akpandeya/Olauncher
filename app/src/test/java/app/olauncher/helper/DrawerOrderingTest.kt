package app.olauncher.helper

import android.os.UserHandle
import app.olauncher.data.AppModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerOrderingTest {

    private val dummyUser: UserHandle by lazy {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe")
        theUnsafeField.isAccessible = true
        val unsafe = theUnsafeField.get(null)
        val allocateInstance = unsafeClass.getMethod("allocateInstance", Class::class.java)
        allocateInstance.invoke(unsafe, UserHandle::class.java) as UserHandle
    }

    private fun createApp(
        label: String,
        isNew: Boolean = false,
        firstInstallTime: Long = 0L,
        isArchived: Boolean = false,
        packageName: String = "pkg.$label"
    ): AppModel.App = AppModel.App(
        appLabel = label,
        key = null,
        appPackage = packageName,
        activityClassName = null,
        isNew = isNew,
        user = dummyUser,
        firstInstallTime = firstInstallTime,
        isArchived = isArchived
    )

    private fun createPinnedShortcut(
        label: String,
        isNew: Boolean = false,
        firstInstallTime: Long = 0L,
        shortcutId: String = "sc.$label"
    ): AppModel.PinnedShortcut = AppModel.PinnedShortcut(
        appLabel = label,
        key = null,
        appPackage = "pkg.$label",
        shortcutId = shortcutId,
        isNew = isNew,
        user = dummyUser,
        firstInstallTime = firstInstallTime
    )

    private fun createPrivateHeader(isLocked: Boolean = true): AppModel.PrivateSpaceHeader =
        AppModel.PrivateSpaceHeader(isLocked = isLocked, user = dummyUser)

    private fun createResult(text: String): AppModel.Result =
        AppModel.Result(text = text, action = AppModel.Result.Action.Copy(text))

    @Test
    fun `empty list returns empty list`() {
        assertTrue(DrawerOrdering.order(emptyList(), newFirst = true).isEmpty())
        assertTrue(DrawerOrdering.order(emptyList(), newFirst = false).isEmpty())
    }

    @Test
    fun `when newFirst is false, original collator order is preserved`() {
        val appA = createApp("Calculator", isNew = false, firstInstallTime = 100L)
        val appB = createApp("Camera", isNew = true, firstInstallTime = 500L)
        val appC = createApp("Clock", isNew = false, firstInstallTime = 200L)
        val appD = createApp("Contacts", isNew = true, firstInstallTime = 900L)

        val input = listOf(appA, appB, appC, appD)
        val ordered = DrawerOrdering.order(input, newFirst = false)

        assertEquals(listOf("Calculator", "Camera", "Clock", "Contacts"), ordered.map { it.appLabel })
    }

    @Test
    fun `when newFirst is true, single isNew app comes first`() {
        val appA = createApp("Calculator", isNew = false, firstInstallTime = 100L)
        val appB = createApp("Camera", isNew = false, firstInstallTime = 200L)
        val appC = createApp("Zomato", isNew = true, firstInstallTime = 800L)

        val input = listOf(appA, appB, appC)
        val ordered = DrawerOrdering.order(input, newFirst = true)

        assertEquals(listOf("Zomato", "Calculator", "Camera"), ordered.map { it.appLabel })
    }

    @Test
    fun `when newFirst is true, multiple isNew apps come first ordered by newest install first`() {
        val appA = createApp("Calendar", isNew = false, firstInstallTime = 100L)
        val appB = createApp("Firefox", isNew = true, firstInstallTime = 500L)
        val appC = createApp("Gallery", isNew = false, firstInstallTime = 200L)
        val appD = createApp("Signal", isNew = true, firstInstallTime = 900L)
        val appE = createApp("VLC", isNew = true, firstInstallTime = 700L)

        val input = listOf(appA, appB, appC, appD, appE)
        val ordered = DrawerOrdering.order(input, newFirst = true)

        // Newest install first: Signal (900), VLC (700), Firefox (500)
        // Followed by rest in existing order: Calendar, Gallery
        assertEquals(
            listOf("Signal", "VLC", "Firefox", "Calendar", "Gallery"),
            ordered.map { it.appLabel }
        )
    }

    @Test
    fun `when newFirst is true, ties in install time preserve existing collator order`() {
        val appA = createApp("Amazon", isNew = true, firstInstallTime = 500L)
        val appB = createApp("Chrome", isNew = false, firstInstallTime = 100L)
        val appC = createApp("Duolingo", isNew = true, firstInstallTime = 500L)
        val appD = createApp("Maps", isNew = false, firstInstallTime = 200L)

        val input = listOf(appA, appB, appC, appD)
        val ordered = DrawerOrdering.order(input, newFirst = true)

        // Amazon and Duolingo have same install time (500L), so Amazon comes before Duolingo
        assertEquals(listOf("Amazon", "Duolingo", "Chrome", "Maps"), ordered.map { it.appLabel })
    }

    @Test
    fun `when newFirst is true and no apps are isNew, order is unchanged`() {
        val appA = createApp("Calculator", isNew = false, firstInstallTime = 100L)
        val appB = createApp("Calendar", isNew = false, firstInstallTime = 200L)
        val appC = createApp("Camera", isNew = false, firstInstallTime = 300L)

        val input = listOf(appA, appB, appC)
        val ordered = DrawerOrdering.order(input, newFirst = true)

        assertEquals(listOf("Calculator", "Calendar", "Camera"), ordered.map { it.appLabel })
    }

    @Test
    fun `when newFirst is true and all apps are isNew, all are sorted by newest install`() {
        val appA = createApp("A", isNew = true, firstInstallTime = 100L)
        val appB = createApp("B", isNew = true, firstInstallTime = 300L)
        val appC = createApp("C", isNew = true, firstInstallTime = 200L)

        val input = listOf(appA, appB, appC)
        val ordered = DrawerOrdering.order(input, newFirst = true)

        assertEquals(listOf("B", "C", "A"), ordered.map { it.appLabel })
    }

    @Test
    fun `private-space header and apps stay at the end when newFirst is false`() {
        val appA = createApp("Calculator", isNew = false)
        val appB = createApp("Clock", isNew = true, firstInstallTime = 1000L)
        val header = createPrivateHeader(isLocked = false)
        val privA = createApp("Secret A", isNew = false)
        val privB = createApp("Secret B", isNew = false)

        val input = listOf(appA, appB, header, privA, privB)
        val ordered = DrawerOrdering.order(input, newFirst = false)

        assertEquals(
            listOf("Calculator", "Clock", "", "Secret A", "Secret B"),
            ordered.map { it.appLabel }
        )
        assertEquals(header, ordered[2])
    }

    @Test
    fun `private-space header and apps stay at the end when newFirst is true`() {
        val appA = createApp("Browser", isNew = false, firstInstallTime = 100L)
        val appB = createApp("Notes", isNew = true, firstInstallTime = 500L)
        val appC = createApp("Phone", isNew = false, firstInstallTime = 200L)
        val header = createPrivateHeader(isLocked = false)
        val privA = createApp("Hidden 1", isNew = false)
        val privB = createApp("Hidden 2", isNew = false)

        val input = listOf(appA, appB, appC, header, privA, privB)
        val ordered = DrawerOrdering.order(input, newFirst = true)

        // Notes is isNew, so it moves to front. Browser and Phone stay in existing order.
        // Header and private apps stay at the end.
        assertEquals(
            listOf("Notes", "Browser", "Phone", "", "Hidden 1", "Hidden 2"),
            ordered.map { it.appLabel }
        )
        assertEquals(header, ordered[3])
    }

    @Test
    fun `private-space apps are not moved to top even if marked isNew`() {
        val appA = createApp("Browser", isNew = false, firstInstallTime = 100L)
        val appB = createApp("Notes", isNew = true, firstInstallTime = 500L)
        val header = createPrivateHeader(isLocked = false)
        val privNew = createApp("Private New", isNew = true, firstInstallTime = 9999L)

        val input = listOf(appA, appB, header, privNew)
        val ordered = DrawerOrdering.order(input, newFirst = true)

        // Only main app Notes is brought to the top. privNew stays behind the header.
        assertEquals(
            listOf("Notes", "Browser", "", "Private New"),
            ordered.map { it.appLabel }
        )
        assertEquals(header, ordered[2])
        assertEquals(privNew, ordered[3])
    }

    @Test
    fun `list with only private space items is handled correctly`() {
        val header = createPrivateHeader(isLocked = true)
        val input = listOf(header)

        val orderedTrue = DrawerOrdering.order(input, newFirst = true)
        val orderedFalse = DrawerOrdering.order(input, newFirst = false)

        assertEquals(listOf(header), orderedTrue)
        assertEquals(listOf(header), orderedFalse)
    }

    @Test
    fun `pinned shortcuts are preserved in existing order`() {
        val appA = createApp("Alpha", isNew = false, firstInstallTime = 100L)
        val shortcut = createPinnedShortcut("Chat with Alice", isNew = false)
        val appB = createApp("Beta", isNew = true, firstInstallTime = 600L)

        val input = listOf(appA, shortcut, appB)
        val ordered = DrawerOrdering.order(input, newFirst = true)

        assertEquals(listOf("Beta", "Alpha", "Chat with Alice"), ordered.map { it.appLabel })
    }

    @Test
    fun `archived apps are not moved to top even if marked isNew`() {
        val appA = createApp("Calculator", isNew = false, isArchived = true, firstInstallTime = 900L)
        val appB = createApp("Camera", isNew = true, isArchived = false, firstInstallTime = 500L)
        val appC = createApp("Clock", isNew = false, isArchived = false, firstInstallTime = 200L)

        val input = listOf(appA, appB, appC)
        val ordered = DrawerOrdering.order(input, newFirst = true)

        // Only Camera is isNew. Calculator is archived so it stays in restApps in its original place.
        assertEquals(listOf("Camera", "Calculator", "Clock"), ordered.map { it.appLabel })
    }

    @Test
    fun `forSearchPosition returns empty list for empty input on both positions`() {
        assertTrue(DrawerOrdering.forSearchPosition(emptyList<AppModel>(), bottomSearch = false).isEmpty())
        assertTrue(DrawerOrdering.forSearchPosition(emptyList<AppModel>(), bottomSearch = true).isEmpty())
    }

    @Test
    fun `forSearchPosition leaves top search results unchanged`() {
        val app1 = createApp("Maps")
        val app2 = createApp("Mail")
        val input = listOf(app1, app2)

        val ordered = DrawerOrdering.forSearchPosition(input, bottomSearch = false)
        assertEquals(listOf("Maps", "Mail"), ordered.map { it.appLabel })
    }

    @Test
    fun `forSearchPosition reverses results for bottom search so best match is last`() {
        val app1 = createApp("Maps")
        val app2 = createApp("Mail")
        val app3 = createApp("Messages")
        val input = listOf(app1, app2, app3)

        val ordered = DrawerOrdering.forSearchPosition(input, bottomSearch = true)
        assertEquals(listOf("Messages", "Mail", "Maps"), ordered.map { it.appLabel })
    }

    @Test
    fun `forSearchPosition keeps result rows nearest the box for both search positions`() {
        val calcRow = createResult("= 42")
        val dialRow = createResult("Call 12345")
        val app1 = createApp("Maps")
        val app2 = createApp("Mail")
        val input = listOf(calcRow, dialRow, app1, app2)

        val topSearch = DrawerOrdering.forSearchPosition(input, bottomSearch = false)
        // For top search, search box is at the top: calc and dial are at the start, nearest the box
        assertEquals(listOf(calcRow, dialRow, app1, app2), topSearch)
        assertEquals(calcRow, topSearch.first())

        val bottomSearch = DrawerOrdering.forSearchPosition(input, bottomSearch = true)
        // For bottom search, search box is at the bottom: calc and dial are at the end, nearest the box
        assertEquals(listOf(app2, app1, dialRow, calcRow), bottomSearch)
        assertEquals(calcRow, bottomSearch.last())
        assertEquals(dialRow, bottomSearch[bottomSearch.size - 2])
        assertEquals(app1, bottomSearch[bottomSearch.size - 3])
    }

    @Test
    fun `forSearchPosition handles single item list on both positions`() {
        val calcRow = createResult("= 42")
        val input = listOf(calcRow)

        assertEquals(input, DrawerOrdering.forSearchPosition(input, bottomSearch = false))
        assertEquals(input, DrawerOrdering.forSearchPosition(input, bottomSearch = true))
    }

    @Test
    fun `forSearchPosition handles only result rows`() {
        val calcRow = createResult("= 100")
        val dialRow = createResult("Call 911")
        val input = listOf(calcRow, dialRow)

        val topSearch = DrawerOrdering.forSearchPosition(input, bottomSearch = false)
        assertEquals(listOf(calcRow, dialRow), topSearch)

        val bottomSearch = DrawerOrdering.forSearchPosition(input, bottomSearch = true)
        assertEquals(listOf(dialRow, calcRow), bottomSearch)
    }
}
