package app.olauncher.data.store

import app.olauncher.R

/**
 * Registry of drill types with content and their item builders.
 * Both [available] and [items] are Context-free.
 */
object DrillTables {

    /** Returns all drill types that currently have content. */
    fun available(): List<DrillType> = listOf(
        DrillType.PREPOSITIONS,
        DrillType.PRONOUNS,
        DrillType.ARTICLES,
        DrillType.PLURALS,
        DrillType.CASE_ENDINGS,
        DrillType.VERB_PREPOSITIONS,
        DrillType.SEPARABLE_VERBS,
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
        else -> emptyList()
    }

    /** Returns the string resource for the display label of a [DrillType]. */
    fun labelRes(type: DrillType): Int = when (type) {
        DrillType.PREPOSITIONS -> R.string.drill_type_prepositions
        DrillType.PRONOUNS -> R.string.drill_type_pronouns
        DrillType.ARTICLES -> R.string.drill_type_articles
        DrillType.PLURALS -> R.string.drill_type_plurals
        DrillType.CASE_ENDINGS -> R.string.drill_type_case_endings
        DrillType.VERB_PREPOSITIONS -> R.string.drill_type_verb_prepositions
        DrillType.SEPARABLE_VERBS -> R.string.drill_type_separable_verbs
        else -> R.string.drills
    }
}
