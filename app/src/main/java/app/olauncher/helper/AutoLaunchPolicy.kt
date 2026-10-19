package app.olauncher.helper

import app.olauncher.data.AppModel
import app.olauncher.data.Constants

object AutoLaunchPolicy {

    val CURRENCY_SYMBOLS = setOf('€', '$', '£', '¥', '₹')

    fun canAutoLaunch(
        query: String? = null,
        hasResultRow: Boolean = false,
        strongMatches: Int = 1,
        autoLaunch: Boolean = true,
        isBangSearch: Boolean = query?.startsWith("!") ?: false,
        flag: Int = Constants.FLAG_LAUNCH_APP,
        firstItem: AppModel?
    ): Boolean {
        if (hasResultRow) return false
        if (query != null) {
            if (query.startsWith(" ")) return false
            if (query.startsWith("!")) return false
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return false
            val firstChar = trimmed[0]
            if (firstChar.isDigit() || firstChar in CURRENCY_SYMBOLS) return false
        }
        return strongMatches == 1
            && autoLaunch
            && !isBangSearch
            && flag == Constants.FLAG_LAUNCH_APP
            && firstItem != null
            && firstItem !is AppModel.PrivateSpaceHeader
            && firstItem !is AppModel.Result
            && !firstItem.isArchived
    }
}
