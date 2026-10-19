package app.olauncher.helper

import app.olauncher.data.AppModel
import app.olauncher.data.Constants

object ArchivedAppsHelper {

    const val ARCHIVED_APP_SUFFIX = " ☁︎"
    const val NEW_APP_SUFFIX = " ✦"

    fun formatAppTitle(appModel: AppModel): String = buildString {
        append(appModel.appLabel)
        if (appModel.isArchived) {
            append(ARCHIVED_APP_SUFFIX)
        } else if (appModel.isNew) {
            append(NEW_APP_SUFFIX)
        }
    }

    fun isPickerFlag(flag: Int): Boolean =
        flag in Constants.FLAG_SET_HOME_APP_1..Constants.FLAG_SET_WEATHER_APP
            || flag in Constants.FLAG_SET_SHORTCUT_1..Constants.FLAG_SET_SHORTCUT_12

    fun filterForPicker(apps: List<AppModel>, flag: Int): List<AppModel> =
        if (isPickerFlag(flag)) {
            apps.filter { !it.isArchived }
        } else {
            apps
        }

    val CURRENCY_SYMBOLS = AutoLaunchPolicy.CURRENCY_SYMBOLS

    fun canAutoLaunch(
        query: String?,
        hasResultRow: Boolean = false,
        strongMatches: Int = 1,
        autoLaunch: Boolean = true,
        isBangSearch: Boolean = query?.startsWith("!") ?: false,
        flag: Int = Constants.FLAG_LAUNCH_APP,
        firstItem: AppModel?
    ): Boolean = AutoLaunchPolicy.canAutoLaunch(
        query = query,
        hasResultRow = hasResultRow,
        strongMatches = strongMatches,
        autoLaunch = autoLaunch,
        isBangSearch = isBangSearch,
        flag = flag,
        firstItem = firstItem
    )

    fun canAutoLaunch(
        hasResultRow: Boolean,
        strongMatches: Int,
        autoLaunch: Boolean,
        isBangSearch: Boolean,
        flag: Int,
        firstItem: AppModel?
    ): Boolean = AutoLaunchPolicy.canAutoLaunch(
        query = null,
        hasResultRow = hasResultRow,
        strongMatches = strongMatches,
        autoLaunch = autoLaunch,
        isBangSearch = isBangSearch,
        flag = flag,
        firstItem = firstItem
    )
}
