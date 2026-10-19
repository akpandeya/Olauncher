package app.olauncher.helper

import app.olauncher.data.AppModel

object EnterPolicy {

    sealed class Action {
        data class LaunchApp(val app: AppModel) : Action()
        data class ExecuteResult(val result: AppModel.Result) : Action()
        object WebSearch : Action()
    }

    /**
     * [topApp] is the best app match below any result rows. Answer rows (calc, unit,
     * currency, dial) take Enter; lookup rows (contact, event) only take it when no
     * app scores >= 60, so contacts search never steals Enter from a matching app.
     */
    fun resolve(
        query: String?,
        topItem: AppModel?,
        topScore: Int?,
        topApp: AppModel? = null,
    ): Action {
        if (query.isNullOrEmpty()) return Action.WebSearch
        if (query.startsWith("!")) return Action.WebSearch

        if (topItem is AppModel.Result) {
            val isLookupRow = topItem.action is AppModel.Result.Action.Contact ||
                topItem.action is AppModel.Result.Action.Event
            if (isLookupRow && topApp != null) {
                val appScore = topScore ?: AppMatcher.score(topApp.appLabel, query) ?: 0
                if (appScore >= AppMatcher.SCORE_SUBSTRING) return Action.LaunchApp(topApp)
            }
            return Action.ExecuteResult(topItem)
        }

        if (topItem != null && topItem !is AppModel.PrivateSpaceHeader && topItem !is AppModel.RecentHeader) {
            val score = topScore ?: AppMatcher.score(topItem.appLabel, query) ?: 0
            if (score >= AppMatcher.SCORE_SUBSTRING) {
                return Action.LaunchApp(topItem)
            }
        }

        return Action.WebSearch
    }
}
