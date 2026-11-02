package app.olauncher.data.store

/**
 * Context-free parser for the AI `word_order` tile card (#804, part 1).
 *
 * The generation contract writes the prompt as `"Ordne: " + tiles joined by
 * " / "`, the answer as exactly those tiles in their accepted order, and the
 * choices as the answer plus 1-2 wrong orders of the same tiles. This parser
 * turns that (prompt, answer, choices) triple into the card data task-02
 * renders as draggable tiles.
 *
 * Partial or unsupported input is rejected before anything is produced: no
 * tile list, fewer than 3 tiles, a blank tile, or tiles that do not spell
 * exactly the answer's words. The tile/answer comparison is the independent
 * completeness check — a matching tile count alone does not prove the tiles
 * spell the answer, so the words are compared as a multiset, case and
 * punctuation ignored ([PracticeFilters.normalize]).
 */
object PracticeWordOrder {

    /** The prompt prefix and tile separator the generation contract fixes. */
    const val PROMPT_PREFIX = "Ordne: "
    const val TILE_SEPARATOR = " / "

    /** Fewest tiles a word-order card may have (matches `WordOrderItem.validate`). */
    const val MIN_TILES = 3

    /**
     * One parsed word-order card: the [tiles] as written in the prompt
     * (original spelling, for rendering), the [acceptedOrder] — the tiles in
     * the answer's order, in the tiles' own spelling — and the [wrongOrders]:
     * the choices that differ from the answer after normalisation.
     */
    data class Card(
        val tiles: List<String>,
        val acceptedOrder: List<String>,
        val wrongOrders: List<String>,
    )

    /** Outcome of [parse]: a parsed [Parsed.card], or a [Rejected.reason]. */
    sealed class Result {
        data class Parsed(val card: Card) : Result()
        data class Rejected(val reason: String) : Result()
    }

    fun parse(prompt: String, answer: String, choices: List<String>): Result {
        if (!prompt.startsWith(PROMPT_PREFIX) || !prompt.contains(TILE_SEPARATOR)) {
            return Result.Rejected(
                "prompt is not \"$PROMPT_PREFIX\" + tiles joined by \"$TILE_SEPARATOR\"",
            )
        }
        val tiles = prompt.removePrefix(PROMPT_PREFIX).split(TILE_SEPARATOR).map { it.trim() }
        if (tiles.size < MIN_TILES) {
            return Result.Rejected("only ${tiles.size} tiles — at least $MIN_TILES are required")
        }
        if (tiles.any { PracticeFilters.normalize(it).isEmpty() }) {
            return Result.Rejected("blank (punctuation-only) tile in $tiles")
        }
        val answerTokens = answer.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val tileKeys = tiles.map { PracticeFilters.normalize(it) }
        val answerKeys = answerTokens.map { PracticeFilters.normalize(it) }
        if (tileKeys.sorted() != answerKeys.sorted()) {
            return Result.Rejected("tiles $tiles do not spell the answer's words $answerTokens")
        }
        // The accepted order is the tiles in the answer's order, in the tiles'
        // original spelling — so a rendered arrangement can be compared with
        // the tiles directly. The multiset check above guarantees every answer
        // token finds its tile.
        val available = tiles.toMutableList()
        val acceptedOrder = answerTokens.map { token ->
            val key = PracticeFilters.normalize(token)
            available.removeAt(available.indexOfFirst { PracticeFilters.normalize(it) == key })
        }
        val answerKey = PracticeFilters.normalize(answer)
        val wrongOrders = choices.filter { PracticeFilters.normalize(it) != answerKey }
        return Result.Parsed(Card(tiles = tiles, acceptedOrder = acceptedOrder, wrongOrders = wrongOrders))
    }
}
