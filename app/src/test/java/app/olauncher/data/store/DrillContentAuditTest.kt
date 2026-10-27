package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Content audit for every drill table (content-quality bar):
 *
 * 1. No prompt or hint contains the answer or a word-form of it. The check is
 *    whole-word (like [DrillHints.revealsAnswer]) so that by-design stem sharing
 *    stays legal: the plural table must show the singular, and the separable-verb
 *    tables must name the verb whose Partizip II is asked for. For separable verbs
 *    an additional prefix check forbids any prompt token that starts with the
 *    answer (e.g. "vor Schreck" leaking the answer "vor").
 * 2. No trigger/preposition is dense enough to repeat within a random round.
 *    [DrillTables.selectRound] shuffles the whole table and takes
 *    [DrillTables.ROUND_SIZE] items, so a round's contents depend only on how often
 *    each trigger occurs in the table - not on item order. A trigger with `count`
 *    occurrences in a table of `total` items appears `count * ROUND_SIZE / total`
 *    times per round on average; keeping that below 1 makes 3+ repeats in a
 *    round rare. A table with 30 trotz items spaced
 *    far apart would still fail here. Trigger definitions per table:
 *      - prepositions: the preposition (from the stable item key)
 *      - case endings: the case-triggering word; "Subjekt" is excluded because it
 *        is a grammatical role shared by all Nominativ subject items, not a
 *        lexical trigger
 *      - verb+prepositions: the verb+preposition pair (contrast pairs such as
 *        "freuen auf/über" are intentional, so the pair - not the bare verb - counts)
 *      - separable verbs: the verb; saved-word items carry no table trigger
 *      - adjective endings: the case-triggering word of each template
 *      - pronouns, articles, plurals, word order: no lexical trigger (their items
 *        are not triggered by a repeating cue; noun density and the leak rules apply)
 * 3. No noun is dense enough to repeat within a random round, by the same density
 *    cap as for triggers. Nouns are capitalized mid-sentence tokens; sentence-initial
 *    tokens are skipped (sentence starters and fronted articles are not content
 *    nouns), formal/possessive pronoun forms are stoplisted, and meta prompts
 *    ("Welcher Fall folgt auf ...", quoted verb prompts) carry no content nouns.
 */
class DrillContentAuditTest {

    private val window = DrillTables.ROUND_SIZE

    // ---------------------------------------------------------------------
    // 1. Leak checks
    // ---------------------------------------------------------------------

    @Test
    fun noPromptOrHintContainsTheAnswerInAnyTable() {
        for (type in DrillTables.available()) {
            for (item in DrillTables.items(type)) {
                assertHintDoesNotRevealAnswer(type, item)
                assertPromptDoesNotRevealAnswer(type, item)
            }
        }
    }

    @Test
    fun noPromptOrHintContainsTheAnswerForExtraSavedSeparableVerbs() {
        val savedWords = listOf(
            Word(id = 1L, text = "anrufen", dueDay = 0L, addedAt = 0L),
            Word(id = 2L, text = "einkaufen", dueDay = 0L, addedAt = 0L),
        )
        val presentLookup: (String) -> String? = { text ->
            if (text.contains("anrufen")) "ruft an" else "kauft ein"
        }
        val items = DrillTables.items(DrillType.SEPARABLE_VERBS, savedWords, presentLookup = presentLookup)
        assertTrue("Expected extra saved-verb items keyed by word id", items.any { it.id.key == "1" || it.id.key == "2" })
        for (item in items) {
            assertHintDoesNotRevealAnswer(DrillType.SEPARABLE_VERBS, item)
            assertPromptDoesNotRevealAnswer(DrillType.SEPARABLE_VERBS, item)
        }
    }

    private fun assertHintDoesNotRevealAnswer(type: DrillType, item: DrillItem) {
        val hint = item.hint ?: return
        assertFalse(
            "${type.id} ${item.id}: hint \"$hint\" reveals the answer \"${item.answer}\"",
            DrillHints.revealsAnswer(hint, item.answer),
        )
    }

    private fun assertPromptDoesNotRevealAnswer(type: DrillType, item: DrillItem) {
        // The plural prompt must show the singular ("der Tag → die ___"), so the
        // answer (the plural form) can never leak by design; only hints are checked.
        if (type == DrillType.PLURALS) return
        val tokens = DrillHints.words(withoutQuoted(item.prompt)).toSet()
        // A relative-pronoun prompt names its referent ("Der Mann, ___"), whose
        // article is itself the answer in nominative (and non-masculine accusative)
        // items. Gender and number are the given — the case is the question — so the
        // prompt's first word is not treated as a leak (gap topics, #651).
        val firstWord = DrillHints.words(item.prompt).firstOrNull()
        val tokensForAnswer = if (firstWord != null) tokens - setOf(firstWord.lowercase()) else tokens
        for (answerWord in DrillHints.words(item.answer)) {
            assertFalse(
                "${type.id} ${item.id}: prompt \"${item.prompt}\" contains the answer word \"$answerWord\"",
                answerWord in tokensForAnswer,
            )
            if (type == DrillType.SEPARABLE_VERBS) {
                assertFalse(
                    "${type.id} ${item.id}: prompt \"${item.prompt}\" gives away the answer \"$answerWord\" via a related word",
                    tokens.any { it.startsWith(answerWord) },
                )
            }
        }
    }

    /** Removes „…“ quoted segments (meta prompts name the verb/preposition they drill). */
    private fun withoutQuoted(text: String): String = text.replace(Regex("„[^“]*“"), " ")

    // ---------------------------------------------------------------------
    // 2. Trigger repetition checks
    // ---------------------------------------------------------------------

    @Test
    fun noTriggerIsDenseEnoughToRepeatInARound() {
        for (type in DrillTables.available()) {
            // Word-order items are WordOrderItems, not DrillItems; audited separately below.
            val items = DrillTables.items(type)
            if (items.isEmpty()) continue
            val triggersPerItem = triggerPerItem(type, items)
            assertEquals("Trigger alignment for ${type.id}", items.size, triggersPerItem.size)
            assertLowRoundDensity(triggersPerItem, "${type.id} trigger")
        }
    }

    private fun triggerPerItem(type: DrillType, items: List<DrillItem>): List<List<String>> = when (type) {
        DrillType.PREPOSITIONS -> items.map { item ->
            // Key format: "prep:<preposition>:<n>"
            listOf(item.id.key.removePrefix("prep:").substringBeforeLast(':').lowercase())
        }
        DrillType.CASE_ENDINGS -> {
            assertEquals(
                "Case endings items must map 1:1 onto TEMPLATES",
                DrillCaseEndings.TEMPLATES.size,
                items.size,
            )
            DrillCaseEndings.TEMPLATES.map { template ->
                listOfNotNull(
                    template.trigger.takeIf { it.isNotBlank() && it != "Subjekt" }?.lowercase(),
                )
            }
        }
        DrillType.VERB_PREPOSITIONS -> {
            assertEquals(
                "Verb+preposition items must be two per PAIRS entry",
                DrillVerbPrepositions.PAIRS.size * 2,
                items.size,
            )
            items.mapIndexed { index, _ ->
                val pair = DrillVerbPrepositions.PAIRS[index / 2]
                listOf("${pair.verb} + ${pair.preposition}".lowercase())
            }
        }
        DrillType.SEPARABLE_VERBS -> items.map { item ->
            // Key format: "sep:<infinitive>:<kind>"; saved-word items keep their noun triggers out
            if (item.id.key.startsWith("sep:")) {
                listOf(item.id.key.removePrefix("sep:").substringBeforeLast(':').lowercase())
            } else {
                emptyList()
            }
        }
        DrillType.ADJECTIVE_ENDINGS -> {
            assertEquals(
                "Adjective endings items must map 1:1 onto TEMPLATES",
                DrillAdjectiveEndings.TEMPLATES.size,
                items.size,
            )
            DrillAdjectiveEndings.TEMPLATES.map { template ->
                listOfNotNull(template.trigger.takeIf { it.isNotBlank() }?.lowercase())
            }
        }
        else -> items.map { emptyList() }
    }

    // ---------------------------------------------------------------------
    // 3. Noun spread checks
    // ---------------------------------------------------------------------

    @Test
    fun noNounIsDenseEnoughToRepeatInARound() {
        for (type in DrillTables.available()) {
            // Word-order items are WordOrderItems, not DrillItems; audited separately below.
            val items = DrillTables.items(type)
            if (items.isEmpty()) continue
            val perItem = items.map { nounsOf(it.prompt).toList() }
            // Gap topics (#651) ship 10-item tables while rounds draw 3-5 of them
            // (DrillRoundPlanner), so a content noun may appear in up to 3 items —
            // at most ~1.5 occurrences per round, still far from the 3+ repeats this
            // check guards against. The reviewed #654 data relies on it (the relative
            // pronoun table points back at der Mann / die Frau throughout).
            val isGapTopic = DrillGapTopics.isGapTopic(type)
            val window = if (isGapTopic) DrillRoundPlanner.MAX_ROUND_SIZE else DrillTables.ROUND_SIZE
            val maxPerRound = if (isGapTopic) 2.0 else 1.0
            assertLowRoundDensity(perItem, "${type.id} noun", window, maxPerRound)
        }
    }

    @Test
    fun noNounIsDenseEnoughToRepeatInAWordOrderRound() {
        val perItem = DrillWordOrder.ENTRIES.map { entry ->
            nounsOf(entry.item.tiles.joinToString(" ")).toList()
        }
        assertLowRoundDensity(perItem, "word_order noun")
    }

    /** Formal/possessive pronoun forms that appear capitalized mid-sentence. */
    private val pronounStopWords = setOf(
        "sie", "ihnen", "ihr", "ihre", "ihren", "ihrem", "ihrer", "ihres",
        "er", "es", "ich", "du", "wir", "uns", "euch",
    )

    /**
     * Capitalized mid-sentence tokens of [text], lowercased. Each sentence's first
     * token is skipped (fronted articles, adverbs and verbs are not content nouns).
     * Prompts containing „…“ quotes are meta prompts ("Welcher Fall folgt auf
     * „durch“?", "Das Verb „anrufen“ ist ___") and carry no content nouns.
     */
    private fun nounsOf(text: String): Set<String> {
        if (text.contains('„')) return emptySet()
        return text.split(Regex("[.!?]+"))
            .flatMap { sentence -> words(sentence).drop(1) }
            .filter { token ->
                token[0].isUpperCase() &&
                    token.drop(1).any { it.isLowerCase() } && // excludes roman numerals such as "II"
                    token.lowercase() !in pronounStopWords
            }
            .map { it.lowercase() }
            .toSet()
    }

    private fun words(text: String): List<String> =
        text.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    // ---------------------------------------------------------------------
    // Density machinery
    // ---------------------------------------------------------------------

    /**
     * Rounds are a shuffle of the table that keeps [window] items, so what a round can
     * repeat is decided by per-table density, not by item order. A key with `count`
     * occurrences among `total` items appears `count * window / total` times per round
     * on average; keeping that average below [maxPerRound] makes 3+ repeats in a round
     * rare.
     */
    private fun assertLowRoundDensity(
        perItem: List<List<String>>,
        label: String,
        window: Int = DrillTables.ROUND_SIZE,
        maxPerRound: Double = 1.0,
    ) {
        assertTrue("$label: table must not be empty", perItem.isNotEmpty())
        val total = perItem.size
        val counts = perItem.flatten().groupingBy { it }.eachCount()
        val offenders = counts.filterValues { it * window >= maxPerRound * total }.keys
        assertTrue(
            "$label: $offenders is dense enough to repeat within a $window-item round " +
                "(a round shuffles the whole table, so only per-table density matters)",
            offenders.isEmpty(),
        )
    }
}
