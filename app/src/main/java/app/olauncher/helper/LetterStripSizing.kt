package app.olauncher.helper

object LetterStripSizing {
    /** Letter size that fits [count] slots in [heightPx], never above [maxPx]. */
    fun textSizePx(heightPx: Int, count: Int, maxPx: Float): Float {
        if (heightPx <= 0 || count <= 0) return maxPx
        // A glyph needs about 1.2x its size in line height.
        return minOf(maxPx, heightPx.toFloat() / count / 1.2f)
    }
}
