package app.olauncher.data.store

/**
 * One hand-checked further-reading link of a grammar topic group (#810,
 * docs/design/german-hidden-surfaces.md §4.1). Every [url] was opened and read
 * during implementation and its angle and key points are distilled in
 * docs/design/grammar-further-reading.md; nothing is fetched at runtime — the
 * link only opens in the user's browser.
 */
data class GrammarFurtherReadingLink(
    /** Short human-readable source name shown as the row's title. */
    val source: String,
    /** The https URL the row opens in the browser. */
    val url: String,
    /** One line saying what this link covers that the topic's others do not. */
    val angle: String,
)

/**
 * Further-reading links keyed on the stable [GrammarRuleGroups] group keys
 * (#810). The map is fully static and bundled: no network call anywhere, a tap
 * only hands the URL to the browser. Source priority follows the owner
 * decision: high-quality open sources first, Wikipedia last, and more than one
 * link per topic only when each covers a different angle. A topic with no good
 * source has no entry here rather than a weak link — the rule detail hides the
 * section for it (GrammarFurtherReadingTest checks keys, https and domains).
 */
object GrammarFurtherReading {

    /** Group key -> the topic's links, best first. */
    val byGroup: Map<String, List<GrammarFurtherReadingLink>> = mapOf(
        "articles" to listOf(
            GrammarFurtherReadingLink(
                source = "Lingolia – der, die, das: noun gender",
                url = "https://deutsch.lingolia.com/en/grammar/nouns-and-articles",
                angle = "Gender patterns and exceptions: suffix tables, compound nouns taking the last noun's gender, two-article nouns.",
            ),
        ),
        "plurals" to listOf(
            GrammarFurtherReadingLink(
                source = "Lingolia – plural nouns",
                url = "https://deutsch.lingolia.com/en/grammar/nouns-and-articles/plural",
                angle = "Which noun endings take -n/-en, -e, -r/-er, -s or no ending, plus nouns used only in singular or only in plural.",
            ),
        ),
        "pronouns" to listOf(
            GrammarFurtherReadingLink(
                source = "Lingolia – personal pronouns",
                url = "https://deutsch.lingolia.com/en/grammar/pronouns/personal-pronouns",
                angle = "The full case table of ich/du/er… and the three sie forms: she, they, and formal Sie.",
            ),
            GrammarFurtherReadingLink(
                source = "Lingolia – relative pronouns",
                url = "https://deutsch.lingolia.com/en/grammar/pronouns/relative-pronouns",
                angle = "The der/die/das and welcher full four-case table, including the genitive dessen/deren.",
            ),
        ),
        "word_order" to listOf(
            GrammarFurtherReadingLink(
                source = "Lingolia – main clauses",
                url = "https://deutsch.lingolia.com/en/grammar/sentence-structure/main-clauses",
                angle = "Verb-second with any first position, and the middle-field order: subject, indirect object, time, place, direct object.",
            ),
            GrammarFurtherReadingLink(
                source = "Lingolia – dependent clauses",
                url = "https://deutsch.lingolia.com/en/grammar/sentence-structure/dependent-clauses",
                angle = "The finite verb going to the end of a subordinate clause, and the fronted clause flipping verb-first.",
            ),
        ),
        "cases" to listOf(
            GrammarFurtherReadingLink(
                source = "Lingolia – the four cases",
                url = "https://deutsch.lingolia.com/en/grammar/declension",
                angle = "When each case is used: question words, verb and preposition signal words, accusative-before-dative object order.",
            ),
        ),
        "prepositions" to listOf(
            GrammarFurtherReadingLink(
                source = "Lingolia – prepositions",
                url = "https://deutsch.lingolia.com/en/grammar/prepositions",
                angle = "Case per preposition, the nine two-way prepositions with wo/wohin, contractions like im and zum, and verb+preposition units.",
            ),
        ),
        "verbs" to listOf(
            GrammarFurtherReadingLink(
                source = "Lingolia – modal verbs",
                url = "https://deutsch.lingolia.com/en/grammar/verbs/modal-verbs",
                angle = "The six modals' full present and simple-past tables, double infinitive in the perfect, and dropped full verbs.",
            ),
            GrammarFurtherReadingLink(
                source = "Lingolia – separable and inseparable verbs",
                url = "https://deutsch.lingolia.com/en/grammar/verbs/separable-verbs",
                angle = "Which prefixes separate, the ge- position in the participle, and dual prefixes like um- changing meaning.",
            ),
        ),
        "tenses" to listOf(
            GrammarFurtherReadingLink(
                source = "Lingolia – Präteritum",
                url = "https://deutsch.lingolia.com/en/grammar/tenses/simple-past",
                angle = "Weak -te endings, strong-vowel stems, the sein/hatten forms, and the inserted -e- after d/t stems.",
            ),
            GrammarFurtherReadingLink(
                source = "Lingolia – Futur I",
                url = "https://deutsch.lingolia.com/en/grammar/tenses/future",
                angle = "werden + infinitive forms and the assumption use with wohl/sicher/bestimmt, where present tense also works.",
            ),
        ),
        "adjectives" to listOf(
            GrammarFurtherReadingLink(
                source = "Lingolia – adjective declension",
                url = "https://deutsch.lingolia.com/en/grammar/adjectives/declension",
                angle = "Full ending tables for the three article situations: after the definite article, after ein/kein/possessive, and with no article.",
            ),
        ),
        "passive" to listOf(
            GrammarFurtherReadingLink(
                source = "Lingolia – the passive voice",
                url = "https://deutsch.lingolia.com/en/grammar/verbs/passive",
                angle = "Active-to-passive steps, sein + participle + worden in the perfect, von vs durch, and verbs that have no passive.",
            ),
        ),
        "moods" to listOf(
            GrammarFurtherReadingLink(
                source = "Lingolia – Konjunktiv I and II",
                url = "https://deutsch.lingolia.com/en/grammar/verbs/subjunctive",
                angle = "Both moods on one page: würde + infinitive and hätte/wäre + participle for II, infinitive-minus-n for reported speech.",
            ),
        ),
    )

    /**
     * The links of the group that owns [ruleId] in [GrammarRuleGroups], in the
     * map's order; empty for an unknown or ungrouped id (no section then).
     */
    fun forRule(ruleId: String): List<GrammarFurtherReadingLink> {
        val key = groupKeyOf(ruleId) ?: return emptyList()
        return byGroup.getValue(key)
    }

    /** The stable [GrammarRuleGroups] key that owns [ruleId], or null. */
    fun groupKeyOf(ruleId: String): String? =
        GrammarRuleGroups.ordered.firstOrNull { ruleId in it.ruleIds }?.key
}
