package app.olauncher.helper

import app.olauncher.data.AppModel

object EnterPolicy {

    sealed class Action {
        data class LaunchApp(val app: AppModel) : Action()
        data class ExecuteResult(val result: AppModel.Result) : Action()
        object WebSearch : Action()
    }

    fun resolve(
        query: String?,
        topItem: AppModel?,
        topScore: Int?
    ): Action {
        if (query.isNullOrEmpty()) return Action.WebSearch
        if (query.startsWith("!")) return Action.WebSearch

        if (topItem is AppModel.Result) {
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
