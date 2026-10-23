package app.olauncher.ui

import app.olauncher.R
import app.olauncher.data.Constants

/** One entry in an app's long-press menu. */
data class AppAction(val id: Id, val labelRes: Int, val isDestructive: Boolean = false) {
    enum class Id { CHANGE_APP, ADD_TO_HOME, RENAME, HIDE, REMOVE_FROM_HOME, DISTRACTING, APP_INFO, UNINSTALL, CHOOSE_BUILTIN, RESET_SHORTCUT }
}

/**
 * Builds the list of long-press actions for an app. Context-free so it can be
 * shared by the drawer, home and shortcut popups and unit tested.
 */
object AppActionList {

    /** Actions for a home shortcut icon: pick an app, pick a built-in, or reset. */
    fun buildShortcut(): List<AppAction> = listOf(
        AppAction(AppAction.Id.CHANGE_APP, R.string.change_app),
        AppAction(AppAction.Id.CHOOSE_BUILTIN, R.string.shortcut_choose_builtin),
        AppAction(AppAction.Id.RESET_SHORTCUT, R.string.shortcut_reset),
    )

    /**
     * Actions for a home app: Change app, Rename | Remove from home, Hide, Mark distracting | App info, Uninstall.
     * A home shortcut (not an app) only gets Change app and Remove from home.
     */
    fun buildHome(isShortcut: Boolean, isHidden: Boolean, isDistracting: Boolean): List<AppAction> {
        val changeApp = AppAction(AppAction.Id.CHANGE_APP, R.string.change_app)
        val removeFromHome = AppAction(AppAction.Id.REMOVE_FROM_HOME, R.string.remove_from_home)
        if (isShortcut) return listOf(changeApp, removeFromHome)
        val shared = build(isArchived = false, isHidden = isHidden, isDistracting = isDistracting)
            .filter { it.id != AppAction.Id.ADD_TO_HOME } // already on home
        val (first, rest) = shared.partition { it.id == AppAction.Id.RENAME }
        val (tail, middle) = rest.partition { it.id == AppAction.Id.APP_INFO || it.id == AppAction.Id.UNINSTALL }
        return listOf(changeApp) + first + removeFromHome + middle + tail
    }

    fun build(
        isArchived: Boolean,
        isHidden: Boolean,
        isDistracting: Boolean,
        flag: Int = Constants.FLAG_LAUNCH_APP,
    ): List<AppAction> {
        val tail = listOf(
            AppAction(AppAction.Id.APP_INFO, R.string.app_info),
            AppAction(AppAction.Id.UNINSTALL, R.string.delete, isDestructive = true),
        )
        if (isArchived) return tail
        return buildList {
            add(AppAction(AppAction.Id.ADD_TO_HOME, R.string.add_to_home))
            if (flag != Constants.FLAG_HIDDEN_APPS) add(AppAction(AppAction.Id.RENAME, R.string.rename))
            add(AppAction(AppAction.Id.HIDE, if (isHidden) R.string.unhide else R.string.hide))
            add(
                AppAction(
                    AppAction.Id.DISTRACTING,
                    if (isDistracting) R.string.not_distracting else R.string.mark_distracting,
                )
            )
            addAll(tail)
        }
    }

    /** Group number of an action; a divider is drawn where the number changes. */
    private fun group(id: AppAction.Id): Int = when (id) {
        AppAction.Id.CHANGE_APP, AppAction.Id.ADD_TO_HOME, AppAction.Id.RENAME,
        AppAction.Id.CHOOSE_BUILTIN, AppAction.Id.RESET_SHORTCUT -> 0
        AppAction.Id.HIDE, AppAction.Id.DISTRACTING, AppAction.Id.REMOVE_FROM_HOME -> 1
        AppAction.Id.APP_INFO, AppAction.Id.UNINSTALL -> 2
    }

    /** Indexes in [actions] that start a new group, so a divider goes right before them. */
    fun groupStarts(actions: List<AppAction>): Set<Int> =
        actions.indices.filter { it > 0 && group(actions[it].id) != group(actions[it - 1].id) }.toSet()
}
