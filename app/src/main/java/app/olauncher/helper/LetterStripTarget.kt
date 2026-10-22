package app.olauncher.helper

/** Maps a letter strip index (position in the ordered app list) to an adapter row. */
object LetterStripTarget {

    /**
     * [totalRows] is the shown list size and [recentRows] the recents header plus recent apps in it.
     * Top search: recents come first, then the app list. Bottom search: the app list is
     * reversed and the recents block comes after it, so only the app rows are counted.
     */
    fun position(orderedIndex: Int, bottomSearch: Boolean, totalRows: Int, recentRows: Int): Int {
        if (totalRows <= 0) return 0
        val raw = if (bottomSearch) totalRows - recentRows - 1 - orderedIndex else orderedIndex + recentRows
        return raw.coerceIn(0, totalRows - 1)
    }
}
