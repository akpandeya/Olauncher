package app.olauncher.ui

import app.olauncher.R
import app.olauncher.data.Constants
import app.olauncher.ui.AppAction.Id
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppActionListTest {

    private fun ids(list: List<AppAction>) = list.map { it.id }

    @Test
    fun normalAppHasAllActionsInOrder() {
        val list = AppActionList.build(isArchived = false, isHidden = false, isDistracting = false)
        assertEquals(
            listOf(Id.ADD_TO_HOME, Id.RENAME, Id.HIDE, Id.DISTRACTING, Id.APP_INFO, Id.UNINSTALL),
            ids(list),
        )
        assertEquals(R.string.hide, list.first { it.id == Id.HIDE }.labelRes)
        assertEquals(R.string.mark_distracting, list.first { it.id == Id.DISTRACTING }.labelRes)
    }

    @Test
    fun hiddenAndDistractingChangeLabels() {
        val list = AppActionList.build(isArchived = false, isHidden = true, isDistracting = true)
        assertEquals(R.string.unhide, list.first { it.id == Id.HIDE }.labelRes)
        assertEquals(R.string.not_distracting, list.first { it.id == Id.DISTRACTING }.labelRes)
    }

    @Test
    fun archivedAppOnlyHasInfoAndUninstall() {
        val list = AppActionList.build(isArchived = true, isHidden = false, isDistracting = false)
        assertEquals(listOf(Id.APP_INFO, Id.UNINSTALL), ids(list))
    }

    @Test
    fun hiddenAppsListHasNoRename() {
        val list = AppActionList.build(false, true, false, Constants.FLAG_HIDDEN_APPS)
        assertTrue(Id.RENAME !in ids(list))
        assertTrue(Id.ADD_TO_HOME in ids(list))
    }

    @Test
    fun onlyUninstallIsDestructive() {
        val list = AppActionList.build(false, false, false)
        assertEquals(listOf(Id.UNINSTALL), list.filter { it.isDestructive }.map { it.id })
    }

    @Test
    fun shortcutListIsChangeAppBuiltinReset() {
        val list = AppActionList.buildShortcut()
        assertEquals(listOf(Id.CHANGE_APP, Id.CHOOSE_BUILTIN, Id.RESET_SHORTCUT), ids(list))
        assertTrue(list.none { it.isDestructive })
    }
}
