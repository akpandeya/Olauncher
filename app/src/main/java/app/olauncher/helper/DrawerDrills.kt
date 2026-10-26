package app.olauncher.helper

/** One drill row the drawer search offers for a "drill" query. */
data class DrawerDrillRow(
    val text: String,
    /** The drill type a tap starts; null for the "All drills…" row that opens the hub. */
    val typeId: String?,
)

/**
 * Context-free rules for the drawer drill search:
 * - The rows are search-only: no query that merely resembles the word "drills" keeps
 *   them on screen, and nothing shows when German is off or no type is suggested.
 * - A "drill" query (typo-tolerant, at least four letters) shows one suggested row
 *   and an "All drills…" row — never one row per drill type.
 */
object DrawerDrills {

    /** Shortest query that offers the rows, so "d" or "dr" stay app-only searches. */
    private const val MIN_QUERY_CHARS = 4

    /** The word the rows are offered for. */
    private const val DRILL_WORD = "drills"

    /** True when [query] is a (typo-tolerant) match of the drill word. */
    fun matches(query: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_CHARS) return false
        return AppMatcher.score(DRILL_WORD, trimmed) != null
    }

    /**
     * The drawer rows for the query: the suggested row, then the all-drills row.
     * Empty when German is off, no type is suggested, or the query is not a drill query.
     */
    fun rows(
        query: String,
        germanEnabled: Boolean,
        suggestedTypeId: String?,
        suggestedRowText: String,
        allDrillsText: String,
    ): List<DrawerDrillRow> {
        if (!germanEnabled || suggestedTypeId == null) return emptyList()
        if (!matches(query)) return emptyList()
        return listOf(
            DrawerDrillRow(suggestedRowText, suggestedTypeId),
            DrawerDrillRow(allDrillsText, null),
        )
    }
}
