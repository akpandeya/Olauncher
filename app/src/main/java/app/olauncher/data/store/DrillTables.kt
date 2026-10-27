package app.olauncher.data.store

import app.olauncher.R
import java.util.Random

/**
 * Registry of drill types with content and their item builders.
 * Both [available] and [items] are Context-free.
 */
object DrillTables {

    const val ROUND_SIZE = DRILL_ROUND_SIZE

    /** Returns all drill types that currently have content. */
    fun available(): List<DrillType> = listOf(
        DrillType.PREPOSITIONS,
        DrillType.PRONOUNS,
        DrillType.ARTICLES,
        DrillType.PLURALS,
        DrillType.CASE_ENDINGS,
        DrillType.VERB_PREPOSITIONS,
        DrillType.SEPARABLE_VERBS,
        DrillType.ADJECTIVE_ENDINGS,
        DrillType.WORD_ORDER,
        DrillType.PASSIVE,
        DrillType.KONJUNKTIV_II,
        DrillType.RELATIVE_PRONOUNS,
        DrillType.GENITIVE,
        DrillType.N_DECLENSION,
        DrillType.IMPERATIVE,
        DrillType.WERDEN,
        DrillType.PRAETERITUM,
        DrillType.INFINITIV_ZU,
        DrillType.MODAL_VERBS,
        DrillType.KONJUNKTIV_I,
    )

    /** Per-type flag: true if the drill type has only a choice round. */
    fun isChoiceOnly(type: DrillType): Boolean = type.isChoiceOnly

    /** Per-type flag: true if the drill type keeps a typed round. */
    fun hasTypedRound(type: DrillType): Boolean = type.hasTypedRound

    /** Returns the round shape for the given [type]. */
    fun roundShape(type: DrillType): DrillRoundShape = type.roundShape

    /**
     * Selects up to [limit] shuffled items for a round, or all items if fewer.
     * A round draws 3 to 5 items ([DrillRoundPlanner.MIN_ROUND_SIZE] to
     * [DrillRoundPlanner.MAX_ROUND_SIZE]); [limit] is the cap.
     */
    fun <T> selectRound(
        items: List<T>,
        limit: Int = ROUND_SIZE,
        random: Random = Random(),
    ): List<T> {
        if (items.isEmpty()) return emptyList()
        val shuffled = items.shuffled(random)
        return if (shuffled.size <= limit) shuffled else shuffled.take(limit)
    }

    /** Builds a round of up to [limit] shuffled items for [type] and saved [words]. */
    fun roundItems(
        type: DrillType,
        words: Collection<Word> = emptyList(),
        genderLookup: ((String) -> String?)? = null,
        pluralLookup: ((String) -> String?)? = null,
        presentLookup: ((String) -> String?)? = null,
        limit: Int = ROUND_SIZE,
        random: Random = Random(),
    ): List<DrillItem> = selectRound(
        items(type, words, genderLookup, pluralLookup, presentLookup),
        limit = limit,
        random = random,
    )

    /** Builds a round of up to [limit] shuffled word-order items for saved [words]. */
    fun roundWordOrderItems(
        words: Collection<Word> = emptyList(),
        limit: Int = ROUND_SIZE,
        random: Random = Random(),
    ): List<WordOrderItem> = selectRound(
        wordOrderItems(words),
        limit = limit,
        random = random,
    )

    /** Builds the drill items for the given [type] and saved [words]. */
    fun items(
        type: DrillType,
        words: Collection<Word> = emptyList(),
        genderLookup: ((String) -> String?)? = null,
        pluralLookup: ((String) -> String?)? = null,
        presentLookup: ((String) -> String?)? = null,
    ): List<DrillItem> = when (type) {
        DrillType.PREPOSITIONS -> DrillPrepositions.items(words)
        DrillType.PRONOUNS -> DrillPronouns.items(words)
        DrillType.ARTICLES -> DrillArticles.items(words, genderLookup)
        DrillType.PLURALS -> DrillPlurals.items(words, pluralLookup, genderLookup)
        DrillType.CASE_ENDINGS -> DrillCaseEndings.items(words, genderLookup)
        DrillType.VERB_PREPOSITIONS -> DrillVerbPrepositions.items(words)
        DrillType.SEPARABLE_VERBS -> DrillSeparableVerbs.items(words, presentLookup)
        DrillType.ADJECTIVE_ENDINGS -> DrillAdjectiveEndings.items(words)
        DrillType.PASSIVE,
        DrillType.KONJUNKTIV_II,
        DrillType.RELATIVE_PRONOUNS,
        DrillType.GENITIVE,
        DrillType.N_DECLENSION,
        DrillType.IMPERATIVE,
        DrillType.WERDEN,
        DrillType.PRAETERITUM,
        DrillType.INFINITIV_ZU,
        DrillType.MODAL_VERBS,
        DrillType.KONJUNKTIV_I,
        -> DrillGapTopics.items(type).orEmpty()
        else -> emptyList()
    }

    /** Builds the word-order drill items for saved [words]. */
    fun wordOrderItems(words: Collection<Word> = emptyList()): List<WordOrderItem> =
        DrillWordOrder.items(words)

    /** Returns the string resource for the display label of a [DrillType]. */
    fun labelRes(type: DrillType): Int = when (type) {
        DrillType.PREPOSITIONS -> R.string.drill_type_prepositions
        DrillType.PRONOUNS -> R.string.drill_type_pronouns
        DrillType.ARTICLES -> R.string.drill_type_articles
        DrillType.PLURALS -> R.string.drill_type_plurals
        DrillType.CASE_ENDINGS -> R.string.drill_type_case_endings
        DrillType.VERB_PREPOSITIONS -> R.string.drill_type_verb_prepositions
        DrillType.SEPARABLE_VERBS -> R.string.drill_type_separable_verbs
        DrillType.ADJECTIVE_ENDINGS -> R.string.drill_type_adjective_endings
        DrillType.WORD_ORDER -> R.string.drill_type_word_order
        DrillType.PASSIVE -> R.string.drill_type_passive
        DrillType.KONJUNKTIV_II -> R.string.drill_type_konjunktiv_ii
        DrillType.RELATIVE_PRONOUNS -> R.string.drill_type_relative_pronouns
        DrillType.GENITIVE -> R.string.drill_type_genitive
        DrillType.N_DECLENSION -> R.string.drill_type_n_declension
        DrillType.IMPERATIVE -> R.string.drill_type_imperative
        DrillType.WERDEN -> R.string.drill_type_werden
        DrillType.PRAETERITUM -> R.string.drill_type_praeteritum
        DrillType.INFINITIV_ZU -> R.string.drill_type_infinitiv_zu
        DrillType.MODAL_VERBS -> R.string.drill_type_modal_verbs
        DrillType.KONJUNKTIV_I -> R.string.drill_type_konjunktiv_i
        else -> R.string.drills
    }
}
