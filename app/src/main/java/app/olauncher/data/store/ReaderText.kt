package app.olauncher.data.store

/**
 * Classifies the kept node texts of one screen capture into kicker, headline and body
 * paragraphs for the Reader view (#591). Context-free and JVM-testable; see ReaderTextTest.
 */
object ReaderText {

    enum class Kind { KICKER, HEADLINE, BODY }

    /**
     * One paragraph of the reader. [text] is the node's full, untouched text — punctuation
     * and stopwords stay exactly as captured, so word [ScreenWord.charRange]s index into it.
     * [nodeId] links the block back to its [ScreenNode] for context sentences and geometry.
     */
    data class Block(
        val text: String,
        val kind: Kind,
        val nodeId: Int,
    )

    /** A kicker label is a short line ("Nach der Elternzeit", "HINTERGRUND"). */
    private const val KICKER_MAX_CHARS = 40

    /** Body prose starts at the first line this long; everything above it is front matter. */
    private const val BODY_MIN_CHARS = 100

    /**
     * One block per kept node, in the #588 reading order. Runs the nodes through
     * [ScreenWords.build], so callers may pass the raw capture — noise never becomes a block.
     *
     * Classification rule: the first long prose line (>= [BODY_MIN_CHARS]) is where the body
     * starts. The line directly above it is the headline when it is shorter than that body
     * paragraph; a short line (< [KICKER_MAX_CHARS]) directly above the headline is the
     * kicker. Everything else — including lines above the kicker — is body. With no long
     * line at all there is no headline to anchor on and every block stays body.
     */
    fun blocks(nodes: List<ScreenNode>): List<Block> {
        val texts = ScreenWords.build(nodes).nodes
            .mapNotNull { node -> node.text?.let { node to it } }
            .filter { (_, text) -> text.isNotBlank() }
        val bodyStart = texts.indexOfFirst { (_, text) -> text.trim().length >= BODY_MIN_CHARS }
        if (bodyStart <= 0) return texts.map { (node, text) -> Block(text, Kind.BODY, node.id) }

        val kinds = Array(texts.size) { Kind.BODY }
        val (headlineNode, headlineText) = texts[bodyStart - 1]
        if (headlineText.trim().length < texts[bodyStart].second.trim().length) {
            kinds[bodyStart - 1] = Kind.HEADLINE
            if (bodyStart >= 2) {
                val (kickerNode, kickerText) = texts[bodyStart - 2]
                if (kickerText.trim().length < KICKER_MAX_CHARS) {
                    kinds[bodyStart - 2] = Kind.KICKER
                }
            }
        }
        return texts.mapIndexed { i, (node, text) -> Block(text, kinds[i], node.id) }
    }
}
