package app.olauncher.data.store

/**
 * One topic group of the grammar-rules index (#809, docs/design/german-hidden-surfaces.md
 * §4.1): a stable lowercase key and the ids of [DrillHelpContent]'s rules that belong
 * to it, ordered simpler to harder.
 *
 * The keys stay stable because Task-05 keys further-reading links on them; each key
 * also names its user-facing label, `grammar_rules_group_<key>` in strings.xml. The
 * map holds no rule content — [DrillHelpRule] and the rule bodies are untouched —
 * so a rule id that is grouped here must still exist in the registry
 * (GrammarRuleGroupsTest checks both directions).
 */
data class GrammarRuleGroup(
    /** Stable, lowercase, descriptive key; also the label string's name suffix. */
    val key: String,
    /** The group's rule ids, in the order the index shows them. */
    val ruleIds: List<String>,
)

/**
 * The grammar-rules index grouped by topic in a suggested learning order (#809,
 * docs/design/german-hidden-surfaces.md §4.1). The groups run roughly A1 to C1:
 * articles, plurals and pronouns first, moods last, with Konjunktiv I (reported
 * speech) at the very end. §4.1 records the per-group rationale; the reviewer
 * reorders the learning path here, in this one map.
 */
object GrammarRuleGroups {

    /** The index groups, in the suggested learning order. */
    val ordered: List<GrammarRuleGroup> = listOf(
        GrammarRuleGroup(
            key = "articles",
            ruleIds = listOf(
                "art-gender-natural",
                "art-gender-suffix-ung",
                "art-gender-suffix-heit",
                "art-gender-suffix-keit",
                "art-gender-suffix-schaft",
                "art-gender-suffix-ion",
                "art-gender-suffix-taet",
                "art-gender-suffix-ei",
                "art-gender-suffix-e",
                "art-gender-suffix-ismus",
                "art-gender-suffix-ling",
                "art-gender-suffix-ment",
                "art-gender-suffix-chen",
                "art-gender-suffix-lein",
                "art-gender-suffix-um",
                "art-gender-fixed",
                "art-gender-suffix-exception",
            ),
        ),
        GrammarRuleGroup(
            key = "plurals",
            ruleIds = listOf(
                "plural-e",
                "plural-umlaut-e",
                "plural-er",
                "plural-umlaut-er",
                "plural-n",
                "plural-en",
                "plural-s",
                "plural-no-change",
            ),
        ),
        GrammarRuleGroup(
            key = "pronouns",
            ruleIds = listOf(
                "pron-first-second",
                "pron-third",
                "pron-formal",
                "pron-reflex-person",
                "pron-reflex-shift",
                "pron-poss-ending",
                "pron-poss-edrop",
                "rel-agreement",
                "rel-after-prep",
                "rel-genitive",
            ),
        ),
        GrammarRuleGroup(
            key = "word_order",
            ruleIds = listOf(
                "wo-verb-second",
                "wo-verb-first-question",
                "wo-verb-last-sub",
                "wo-separable-end",
                "wo-modal-infinitive",
                "wo-perfekt-bracket",
                "wo-tekamolo",
            ),
        ),
        GrammarRuleGroup(
            key = "cases",
            ruleIds = listOf(
                "case-subject-nom",
                "case-sein-nom",
                "case-verb-acc",
                "case-verb-dat",
                "ndecl-weak-en",
                "gen-article",
                "gen-noun-s",
                "gen-noun-weak",
                "gen-noun-zero",
                "gen-kinship-s",
                "gen-time-phrase",
                "gen-question-whose",
            ),
        ),
        GrammarRuleGroup(
            key = "prepositions",
            ruleIds = listOf(
                "prep-which-fixed",
                "prep-fixed-acc",
                "prep-fixed-dat",
                "prep-fixed-gen",
                "prep-two-way",
                "verb-prep-gap-fixed",
                "verb-prep-gap-meaning",
                "verb-prep-case-pair",
            ),
        ),
        GrammarRuleGroup(
            key = "verbs",
            ruleIds = listOf(
                "werden-conjugation",
                "modal-meaning",
                "modal-person-form",
                "sep-which",
                "sep-prefix-end",
                "sep-participle-ge",
                "sep-participle-no-ge",
                "sep-dual",
                "imp-person",
                "imp-du-vowel-change",
                "imp-sein",
                "imp-sie-infinitive",
                "infzu-bare-vs-linked",
                "infzu-separable-split",
                "infzu-clause-meaning",
            ),
        ),
        GrammarRuleGroup(
            key = "tenses",
            ruleIds = listOf(
                "praet-sein",
                "praet-haben",
                "praet-modal",
                "praet-strong-vowel",
                "futur-helper-form",
            ),
        ),
        GrammarRuleGroup(
            key = "adjectives",
            ruleIds = listOf(
                "adj-weak-nom",
                "adj-weak-akk",
                "adj-weak-dat",
                "adj-weak-gen",
                "adj-mixed-nom",
                "adj-mixed-akk",
                "adj-mixed-dat",
                "adj-mixed-gen",
            ),
        ),
        GrammarRuleGroup(
            key = "passive",
            ruleIds = listOf(
                "passiv-aux-form",
                "passiv-partizip-ii",
                "passiv-agent",
                "passiv-perfekt-sein",
                "passiv-perfekt-partizip",
            ),
        ),
        GrammarRuleGroup(
            key = "moods",
            ruleIds = listOf(
                "werden-subjunctive-ii",
                "konj2-wenn-unreal",
                "konj2-subject-agreement",
                "konj2-helper-infinitive",
                "konj2-past-wish",
                "konj2-single-form",
                "konj2-polite-request",
                "konj1-present",
                "konj1-switch",
                "konj1-perfect",
                "konj1-future",
            ),
        ),
    )
}
