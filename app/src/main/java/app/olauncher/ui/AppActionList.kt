package app.olauncher.ui

import app.olauncher.R
import app.olauncher.data.Constants

/** One entry in an app's long-press menu. */
data class AppAction(val id: Id, val labelRes: Int, val isDestructive: Boolean = false) {
    enum class Id { ADD_TO_HOME, RENAME, HIDE, DISTRACTING, APP_INFO, UNINSTALL }
}

/**
 * Builds the list of long-press actions for an app. Context-free so it can be
 * shared by the drawer, home and shortcut popups and unit tested.
 */
object AppActionList {

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
            add(AppAction(AppAction.Id.HIDE, if (isHidden) R.string.unhide else R.string.hide, isDestructive = true))
            add(
                AppAction(
                    AppAction.Id.DISTRACTING,
                    if (isDistracting) R.string.not_distracting else R.string.mark_distracting,
                )
            )
            addAll(tail)
        }
    }
}
