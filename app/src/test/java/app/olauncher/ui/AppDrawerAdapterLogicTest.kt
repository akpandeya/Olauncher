package app.olauncher.ui

import app.olauncher.data.AppModel
import app.olauncher.helper.AppMatcher
import app.olauncher.helper.DrawerAdapterLogic
import app.olauncher.helper.DrawerFilterPayload
import app.olauncher.helper.DrawerFilterState
import app.olauncher.helper.DrawerSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDrawerAdapterLogicTest {

    private val user = AppModel.defaultUser

    private fun app(pkg: String, label: String = pkg) = AppModel.App(
        appLabel = label, key = null, appPackage = pkg, activityClassName = "$pkg.Main", user = user,
    )

    private fun result(text: String) = AppModel.Result(text, AppModel.Result.Action.Web(text))

    private val previous = DrawerFilterState(
        items = listOf(result("old")),
        scores = listOf(90),
        searchQuery = "old",
        isBangSearch = true,
        autoLaunch = false,
    )

    @Test
    fun payloadFrom_copiesEveryFieldOfTheSearchResult() {
        val items = listOf(app("com.a"), result("r"))
        val payload = DrawerFilterPayload.from(DrawerSearchResult(items, listOf(100, 60), "!g cats", true, false))
        assertSame(items, payload.items)
        assertEquals(listOf(100, 60), payload.scores)
        assertEquals("!g cats", payload.searchQuery)
        assertTrue(payload.isBangSearch)
        assertFalse(payload.autoLaunch)
    }

    @Test
    fun publish_withPayload_takesQueryBangAndAutoLaunchFromPayload() {
        val items = listOf(app("com.a"))
        val payload = DrawerFilterPayload(items, listOf(75), "mail", false, true)
        val state = DrawerAdapterLogic.publish("ignored constraint", payload, previous)
        assertSame(items, state.items)
        assertEquals(listOf(75), state.scores)
        assertEquals("mail", state.searchQuery)
        assertFalse(state.isBangSearch)
        assertTrue(state.autoLaunch)
    }

    @Test
    fun publish_withoutPayload_emptiesListAndScoresAndKeepsConstraintAsQuery() {
        val state = DrawerAdapterLogic.publish("zzz", null, previous)
        assertTrue(state.items.isEmpty())
        assertTrue(state.scores.isEmpty())
        assertEquals("zzz", state.searchQuery)
        assertTrue(state.isBangSearch)
        assertFalse(state.autoLaunch)
    }

    @Test
    fun publish_withoutPayloadAndNullConstraint_usesEmptyQuery() {
        assertEquals("", DrawerAdapterLogic.publish(null, null, previous).searchQuery)
    }

    @Test
    fun scrollTarget_topSearchScrollsToFirstRow() {
        assertEquals(0, DrawerAdapterLogic.scrollTarget(bottomSearch = false, itemCount = 7))
    }

    @Test
    fun scrollTarget_bottomSearchScrollsToLastRow() {
        assertEquals(6, DrawerAdapterLogic.scrollTarget(bottomSearch = true, itemCount = 7))
    }

    @Test
    fun scrollTarget_emptyListHasNoTarget() {
        assertNull(DrawerAdapterLogic.scrollTarget(bottomSearch = true, itemCount = 0))
        assertNull(DrawerAdapterLogic.scrollTarget(bottomSearch = false, itemCount = 0))
    }

    @Test
    fun strongMatches_countsScoresAtOrAboveSubstring() {
        val scores = listOf(100, AppMatcher.SCORE_SUBSTRING, AppMatcher.SCORE_SUBSTRING - 1, 0)
        assertEquals(2, DrawerAdapterLogic.strongMatches(scores))
        assertEquals(0, DrawerAdapterLogic.strongMatches(emptyList()))
    }

    @Test
    fun shouldHighlightRow_isTrueOnlyForMatchingNonNegativeIndex() {
        assertTrue(DrawerAdapterLogic.shouldHighlightRow(rowIndex = 3, highlightedIndex = 3))
        assertFalse(DrawerAdapterLogic.shouldHighlightRow(rowIndex = 3, highlightedIndex = 4))
        assertFalse(DrawerAdapterLogic.shouldHighlightRow(rowIndex = 3, highlightedIndex = -1))
        assertFalse(DrawerAdapterLogic.shouldHighlightRow(rowIndex = -1, highlightedIndex = -1))
    }

    @Test
    fun autoLaunchCandidate_isFirstForTopSearchAndLastForBottomSearch() {
        val list = listOf(app("com.a"), app("com.b"), app("com.c"))
        assertEquals("com.a", DrawerAdapterLogic.autoLaunchCandidate(list, bottomSearch = false)?.appPackage)
        assertEquals("com.c", DrawerAdapterLogic.autoLaunchCandidate(list, bottomSearch = true)?.appPackage)
        assertNull(DrawerAdapterLogic.autoLaunchCandidate(emptyList(), bottomSearch = true))
    }

    @Test
    fun removeApp_dropsOnlyRowsWithSamePackage() {
        val a = app("com.a")
        val b = app("com.b")
        val c = app("com.c")
        val out = DrawerAdapterLogic.removeApp(listOf(a, b, c), app("com.b", label = "renamed"))
        assertEquals(listOf(a, c), out)
    }

    @Test
    fun removeApp_dropsEveryRowOfThatPackage() {
        val recent = app("com.a").copy(isRecent = true)
        val b = app("com.b")
        val list = listOf(recent, b, app("com.a"))
        val out = DrawerAdapterLogic.removeApp(list, app("com.a"))
        assertEquals(1, out.size)
        assertSame(b, out[0])
    }

    @Test
    fun removeApp_unknownPackageKeepsAllRows() {
        val list = listOf(app("com.a"), app("com.b"))
        assertEquals(list, DrawerAdapterLogic.removeApp(list, app("com.zzz")))
    }

    @Test
    fun removeApp_doesNotMutateInput() {
        val list = listOf(app("com.a"), app("com.b"))
        val copy = list.toList()
        val out = DrawerAdapterLogic.removeApp(list, app("com.a"))
        assertEquals(copy, list)
        assertEquals(1, out.size)
    }
}
