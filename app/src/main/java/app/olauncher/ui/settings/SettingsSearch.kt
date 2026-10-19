package app.olauncher.ui.settings

import androidx.annotation.IdRes
import app.olauncher.helper.AppMatcher

data class SettingsRow(
    val label: String,
    val sectionKey: String,
    @IdRes val viewId: Int,
)

object SettingsSearch {

    private fun sectionOrder(sectionKey: String): Int {
        val idx = SettingsSections.indexOfFirst { it.key.equals(sectionKey, ignoreCase = true) }
        return if (idx >= 0) idx else Int.MAX_VALUE
    }

    fun match(query: String?, rows: List<SettingsRow>): List<SettingsRow> {
        if (query.isNullOrBlank()) return emptyList()

        val scored = rows.mapNotNull { row ->
            val score = AppMatcher.score(row.label, query) ?: return@mapNotNull null
            row to score
        }

        return scored.sortedWith(
            compareByDescending<Pair<SettingsRow, Int>> { it.second }
                .thenBy { sectionOrder(it.first.sectionKey) }
        ).map { it.first }
    }
}
