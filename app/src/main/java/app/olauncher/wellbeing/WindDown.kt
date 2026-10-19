package app.olauncher.wellbeing

import app.olauncher.helper.HomeSets

/** Pure evening wind-down window maths. Times are minutes since local midnight. */
object WindDown {
    const val DEFAULT_START = 21 * 60 + 30
    const val DEFAULT_END = 6 * 60

    /**
     * True while [nowMinutes] is inside [start, end). The window wraps past midnight
     * when [end] is not after [start]. Equal start and end gives an empty window.
     */
    fun isActive(nowMinutes: Int, start: Int, end: Int): Boolean {
        val now = HomeSets.normalize(nowMinutes)
        val s = HomeSets.normalize(start)
        val e = HomeSets.normalize(end)
        return when {
            s == e -> false
            s < e -> now in s until e
            else -> now >= s || now < e
        }
    }
}
