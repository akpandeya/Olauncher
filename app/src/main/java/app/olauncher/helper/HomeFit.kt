package app.olauncher.helper

object HomeFit {
    /**
     * How many home app rows fit between the clock block and the bottom of the screen.
     * [reservedDp] is everything that is not a home row (clock block and bottom margin).
     * Returns 0 when nothing fits; never more than [wanted].
     */
    fun visibleCount(availableDp: Float, rowDp: Float, reservedDp: Float, wanted: Int): Int {
        if (wanted <= 0 || rowDp <= 0f) return 0
        val room = availableDp - reservedDp
        if (room < rowDp) return 0
        return (room / rowDp).toInt().coerceIn(0, wanted)
    }
}
