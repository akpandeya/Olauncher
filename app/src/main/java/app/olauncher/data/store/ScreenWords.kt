package app.olauncher.data.store

/**
 * Axis-aligned box in screen pixel coordinates. Plain data so it stays JVM-testable;
 * [android.graphics.Rect] and [android.graphics.RectF] stay behind in [app.olauncher.helper.WordScreenService].
 */
data class ScreenBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    /** Smallest box containing both; joins per-character boxes into a word box. */
    fun union(other: ScreenBox): ScreenBox = ScreenBox(
        minOf(left, other.left),
        minOf(top, other.top),
        maxOf(right, other.right),
        maxOf(bottom, other.bottom),
    )
}

/**
 * One accessibility node read from the screen. [text] is the node's full text and stays the
 * paragraph for [ContextSentence.around]; [charBoxes] holds one box per character aligned with
 * that text, or null when no character locations were delivered — no geometry is ever guessed.
 * [id] and [parentId] form the tree of the walk that produced the nodes.
 */
data class ScreenNode(
    val id: Int,
    val parentId: Int,
    val text: String?,
    val bounds: ScreenBox?,
    val charBoxes: List<ScreenBox>?,
    val className: String?,
    val contentDescription: String?,
)

/** One word cut from a node's text. [charRange] indexes into the node's full text. */
data class ScreenWord(
    val text: String,
    val box: ScreenBox?,
    val nodeId: Int,
    val charRange: IntRange,
)

/**
 * Turns screen nodes into words in reading order. Boxes say where, the node text says what:
 * word boxes for hit-testing come from per-character geometry, while the context sentence is
 * always taken from the containing node's paragraph text — never stitched from boxes.
 */
class ScreenWords private constructor(
    /** Words in reading order; only nodes that survived the noise filter contribute. */
    val words: List<ScreenWord>,
    /** Nodes that survived the noise filter, in reading order. */
    val nodes: List<ScreenNode>,
) {
    private val nodeById = nodes.associateBy { it.id }

    /**
     * The full text of the node the word was cut from — the paragraph that
     * [ContextSentence.around] splits into the word's sentence.
     */
    fun paragraphOf(word: ScreenWord): String = nodeById[word.nodeId]?.text ?: ""

    companion object {

        fun build(nodes: List<ScreenNode>): ScreenWords {
            val kept = readingOrder(nodes.filterNot { isNoise(it) })
            val words = ArrayList<ScreenWord>()
            for (node in kept) words += wordsOf(node)
            return ScreenWords(words, kept)
        }

        // News-app clutter (kicker-adjacent banners, captions, dates, bylines). Intentionally
        // conservative: short banner-like or attribution texts only, never running prose.
        private val NOISE_PATTERNS: List<Pair<String, Regex>> = listOf(
            "media badge" to Regex("""(?i)\b(video|audio|livestream|galerie)\b[^|]*\|\s*\d+\s*(min\.?|std\.?|stu?nden?)"""),
            "duration" to Regex("""(?i)^\s*\d{1,3}\s*(min\.?|std\.?)\s*$"""),
            "credit" to Regex("""(?i)picture\s+alliance|\b(dpa|reuters|afp|getty\s*images?|imago)\b"""),
            "foto credit" to Regex("""(?i)^\s*foto\s*:"""),
            "clock time" to Regex("""^\s*\d{1,2}[:.]\d{2}\s*(uhr\.?)?\s*$""", RegexOption.IGNORE_CASE),
            // Date alone or with a trailing time, comma- or bullet-separated ("22.10.2026 • 16:26 Uhr").
            "date" to Regex("""^\s*\d{1,2}\.\d{1,2}\.\d{2,4}\.?(?:\s*[,•]?\s*\d{1,2}[:.]\d{2}\s*(?:uhr\.?)?\s*)?$""", RegexOption.IGNORE_CASE),
            "stand timestamp" to Regex("""(?i)^\s*stand\s*:"""),
            "byline" to Regex("""^\s*[Vv]on\s+[A-ZÄÖÜ][^\s,]*\s+[A-ZÄÖÜ][^\s,]*(,|$)"""),
            // Player overlays, media-player badges in article bodies and teasers.
            "player badge" to Regex("""(?i)^\s*player\s*:"""),
            // Image copyright lines ("© ARD-aktuell / tagesschau.de").
            "copyright" to Regex("""^\s*©"""),
            // Share-row labels render as nodes holding exactly the label.
            "share button" to Regex("""^\s*(Teilen( Teilen)?|Facebook|WhatsApp|Mail|Drucken)\s*$"""),
            // Topic tag chips render as a node holding exactly the tag word — exact match only.
            "tag chip" to Regex("""^\s*(Elternzeit|Arbeit|Teilzeit)\s*$"""),
            // Comment counters ("(80)").
            "comment count" to Regex("""^\s*\(\d+\)\s*$"""),
        )

        /** True for nodes that carry no pickable prose: empty or content-description-only, images, clutter. */
        internal fun isNoise(node: ScreenNode): Boolean {
            val text = node.text?.trim()
            if (text.isNullOrEmpty()) return true // content-description-only or empty node
            if (node.className?.contains("Image") == true) return true // alt text is not article prose
            return NOISE_PATTERNS.any { it.second.containsMatchIn(text) }
        }

        /**
         * Column-aware top-to-bottom order. Nodes chained while the next one horizontally
         * overlaps the previous one (at least half of the narrower box) and starts no further
         * below it than the shorter of the two heights; each chain is one run of a column.
         * Runs are ordered by their top edge (left edge on ties), so side-by-side columns stay
         * together column-major while everything else reads row-major.
         */
        internal fun readingOrder(nodes: List<ScreenNode>): List<ScreenNode> {
            if (nodes.size < 2) return nodes
            val (positioned, unpositioned) = nodes.partition { n ->
                val b = n.bounds
                b != null && b.width > 0 && b.height > 0
            }
            val sorted = positioned.sortedWith(compareBy({ it.bounds!!.top }, { it.bounds!!.left }, { it.id }))
            val runs = ArrayList<MutableList<ScreenNode>>()
            for (node in sorted) {
                val bounds = node.bounds!!
                var best: MutableList<ScreenNode>? = null
                var bestOverlap = 0
                for (run in runs) {
                    val tailBounds = run.last().bounds ?: continue
                    val overlap = minOf(tailBounds.right, bounds.right) - maxOf(tailBounds.left, bounds.left)
                    if (overlap * 2 < minOf(tailBounds.width, bounds.width)) continue
                    if (bounds.top - tailBounds.bottom > minOf(tailBounds.height, bounds.height)) continue
                    if (overlap > bestOverlap) {
                        bestOverlap = overlap
                        best = run
                    }
                }
                (best ?: ArrayList<ScreenNode>().also { runs += it }) += node
            }
            val ordered = runs
                .sortedWith(compareBy({ it.first().bounds!!.top }, { it.first().bounds!!.left }))
                .flatten()
            return ordered + unpositioned
        }

        /** Words from [node]'s text; the box is the union of the word's per-character boxes. */
        private fun wordsOf(node: ScreenNode): List<ScreenWord> {
            val text = node.text ?: return emptyList()
            // A mismatch between boxes and text would misplace every word — treat as no boxes.
            val charBoxes = node.charBoxes?.takeIf { it.size == text.length }
            val words = ArrayList<ScreenWord>()
            var i = 0
            while (i < text.length) {
                while (i < text.length && text[i].isWhitespace()) i++
                val start = i
                while (i < text.length && !text[i].isWhitespace()) i++
                val word = text.substring(start, i)
                if (word.none { it.isLetter() }) continue
                val box = charBoxes?.let { boxes -> boxes.subList(start, i).reduce(ScreenBox::union) }
                words += ScreenWord(word, box, node.id, start until i)
            }
            return words
        }
    }
}
