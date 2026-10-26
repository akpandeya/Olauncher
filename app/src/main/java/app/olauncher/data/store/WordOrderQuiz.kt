package app.olauncher.data.store

import java.util.Random

/**
 * A word-order ordering item.
 *
 * This is a deliberate exception to the standard {prompt, answer, choices} shape.
 * It contains a set of [tiles] to be ordered, a list of [accepted] permutations,
 * and an optional [hint].
 */
data class WordOrderItem(
    val id: DrillItemId,
    val tiles: List<String>,
    val accepted: List<List<String>>,
    val hint: String? = null,
) {
    /** The primary accepted sentence, used as the correct answer display when wrong. */
    val canonicalAnswer: String
        get() = accepted.firstOrNull()?.joinToString(" ").orEmpty()

    /**
     * Validates this item.
     * Rejects fewer than 3 tiles, an empty accepted list, and any accepted order
     * that is not a permutation of the tiles.
     */
    fun validate(): DrillItemCheck {
        val problems = mutableListOf<String>()
        if (tiles.size < 3) {
            problems += "Must have at least 3 tiles (found ${tiles.size})"
        }
        if (accepted.isEmpty()) {
            problems += "Accepted orders cannot be empty"
        }
        val sortedTiles = tiles.sorted()
        for (order in accepted) {
            if (order.sorted() != sortedTiles) {
                problems += "Accepted order is not a permutation of tiles: $order"
            }
        }
        return DrillItemCheck(isValid = problems.isEmpty(), problems = problems)
    }
}

/**
 * Represents a single draggable/tappable tile with a stable id to handle duplicate word strings.
 */
data class WordOrderTile(
    val id: Int,
    val text: String,
)

/**
 * The current question state in a [WordOrderQuiz].
 */
class WordOrderQuestion(
    val item: WordOrderItem,
    val initialTiles: List<WordOrderTile>,
) {
    val id: DrillItemId get() = item.id
    val hint: String? get() = item.hint

    private val _answerTiles = mutableListOf<WordOrderTile>()
    val answerTiles: List<WordOrderTile> get() = _answerTiles.toList()

    val availableTiles: List<WordOrderTile>
        get() = initialTiles.filter { it !in _answerTiles }

    fun selectTile(tile: WordOrderTile): Boolean {
        if (tile in availableTiles) {
            _answerTiles.add(tile)
            return true
        }
        return false
    }

    fun unselectTile(tile: WordOrderTile): Boolean {
        return _answerTiles.remove(tile)
    }

    fun currentOrder(): List<String> = _answerTiles.map { it.text }

    fun isComplete(): Boolean = _answerTiles.size == item.tiles.size
}

/**
 * Quiz engine for word order drills.
 *
 * Keeps the [DrillQuiz] rules:
 * - A wrong answer puts the item at the end of the queue.
 * - [firstAttempts] holds only the first attempt per item.
 * - There is no typed round.
 */
class WordOrderQuiz(
    val items: List<WordOrderItem>,
    private val random: Random = Random(),
) {
    private val firstAttemptsMap = mutableMapOf<DrillItemId, Boolean>()
    private val queue = ArrayDeque<WordOrderItem>()
    private var question: WordOrderQuestion? = null
    private var lastAnswerValue: QuizAnswer? = null

    init {
        if (items.isNotEmpty()) {
            queue.addAll(items)
            buildQuestion()
        }
    }

    val total: Int get() = items.size

    val current: WordOrderQuestion? get() = question

    val currentItem: WordOrderItem? get() = question?.item

    val lastAnswer: QuizAnswer? get() = lastAnswerValue

    val isAnswered: Boolean get() = lastAnswerValue != null

    val isFinished: Boolean get() = question == null

    fun firstAttempts(): Map<DrillItemId, Boolean> = firstAttemptsMap.toMap()

    val firstTryScore: Int get() = firstAttemptsMap.values.count { it }

    fun selectTile(tile: WordOrderTile): Boolean {
        if (isAnswered) return false
        return question?.selectTile(tile) ?: false
    }

    fun unselectTile(tile: WordOrderTile): Boolean {
        if (isAnswered) return false
        return question?.unselectTile(tile) ?: false
    }

    /** Grades a custom sequence of tile texts against the current item's accepted orders. */
    fun answer(order: List<String>): QuizAnswer? {
        val q = question ?: return null
        if (lastAnswerValue != null) return null
        val lowerOrder = order.map { it.lowercase() }
        val isCorrect = order in q.item.accepted || q.item.accepted.any { it.map(String::lowercase) == lowerOrder }
        val result = QuizAnswer(isCorrect, q.item.canonicalAnswer)
        recordAttempt(q.item, isCorrect)
        lastAnswerValue = result
        return result
    }

    /** Grades the currently selected answer tiles. */
    fun check(): QuizAnswer? {
        val q = question ?: return null
        return answer(q.currentOrder())
    }

    /**
     * Advances to the next item.
     * Requeues the current item at the end if answered incorrectly.
     * Quiz finishes once all items are cleared.
     */
    fun advance() {
        val q = question ?: return
        val result = lastAnswerValue ?: return
        queue.removeFirst()
        if (!result.correct) {
            queue.addLast(q.item)
        }
        lastAnswerValue = null
        if (queue.isEmpty()) {
            question = null
        } else {
            buildQuestion()
        }
    }

    private fun recordAttempt(item: WordOrderItem, isCorrect: Boolean) {
        if (item.id !in firstAttemptsMap) {
            firstAttemptsMap[item.id] = isCorrect
        }
    }

    private fun buildQuestion() {
        val item = queue.firstOrNull()
        if (item == null) {
            question = null
            return
        }
        val tiles = item.tiles.mapIndexed { idx, text -> WordOrderTile(idx, text) }.shuffled(random)
        question = WordOrderQuestion(item, tiles)
    }

    companion object {
        val SAMPLE_ITEMS: List<WordOrderItem> = listOf(
            WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "1"),
                tiles = listOf("Ich", "gehe", "gerne", "ins", "Kino"),
                accepted = listOf(
                    listOf("Ich", "gehe", "gerne", "ins", "Kino"),
                    listOf("Ich", "gehe", "ins", "Kino", "gerne"),
                ),
                hint = "Position 2: Verb",
            ),
            WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "2"),
                tiles = listOf("Wir", "kaufen", "morgen", "ein", "Auto"),
                accepted = listOf(
                    listOf("Wir", "kaufen", "morgen", "ein", "Auto"),
                ),
                hint = "Position 2: Verb",
            ),
            WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "3"),
                tiles = listOf("Er", "liest", "gerne", "ein", "Buch"),
                accepted = listOf(
                    listOf("Er", "liest", "gerne", "ein", "Buch"),
                    listOf("Er", "liest", "ein", "Buch", "gerne"),
                ),
                hint = "Subjekt – Verb – Adverb – Objekt",
            ),
            WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "4"),
                tiles = listOf("Sie", "fährt", "immer", "mit", "dem", "Zug"),
                accepted = listOf(
                    listOf("Sie", "fährt", "immer", "mit", "dem", "Zug"),
                    listOf("Sie", "fährt", "mit", "dem", "Zug", "immer"),
                ),
                hint = "Dativ nach mit",
            ),
            WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "5"),
                tiles = listOf("Sie", "kochen", "abends", "das", "Essen"),
                accepted = listOf(
                    listOf("Sie", "kochen", "abends", "das", "Essen"),
                    listOf("Sie", "kochen", "das", "Essen", "abends"),
                ),
                hint = "Subjekt – Verb – Adverb – Objekt",
            ),
        )

        fun items(words: Collection<Word> = emptyList()): List<WordOrderItem> {
            if (words.isEmpty()) return SAMPLE_ITEMS
            val wordMap = words.associateBy { it.text.trim().lowercase() }
            return SAMPLE_ITEMS.map { item ->
                val matchingWord = item.tiles.firstNotNullOfOrNull { tile ->
                    wordMap[tile.lowercase()]
                }
                if (matchingWord != null) {
                    item.copy(id = DrillItemId(DrillType.WORD_ORDER, "${matchingWord.id}:${item.id.key}"))
                } else {
                    item
                }
            }
        }
    }
}
