package app.olauncher.data.store

// Per-item help content of the live (words-driven) drill types (#699), transcribed from
// docs/design/drill-content/prepositions.md, case-endings.md and verb-prepositions.md:
// which rule each built-in item carries and the miss line per wrong choice. Saved-word
// items never match these keys, so they stay without a rule.

/** Rule id and per-wrong-choice miss lines of one prepositions item, keyed by `prep:<p>:<n>`. */
internal data class PrepositionRuleContent(
    val ruleId: String,
    val missByChoice: Map<String, String>,
)

/** Rule id and per-wrong-choice miss lines of one case-endings template, keyed by template key. */
internal data class CaseEndingRuleContent(
    val ruleId: String,
    val missByChoice: Map<String, String>,
)

/** Rules and miss lines of the gap item and the which-case item of one verb + preposition pair. */
internal data class VerbPrepRuleContent(
    val gapRuleId: String,
    /** Miss line per wrong preposition choice of the gap item. */
    val gapMiss: Map<String, String>,
    val caseRuleId: String,
    /** The one miss line of the which-case item, keyed by the wrong case. */
    val caseMiss: Map<String, String>,
)

internal val PREPOSITION_RULE_CONTENT: Map<String, PrepositionRuleContent> = mapOf(
    "prep:durch:0" to PrepositionRuleContent(
        "prep-which-fixed",
        mapOf(
            "Dativ" to "this preposition never takes the Dativ; it always takes the Akkusativ.",
            "Genitiv" to "this preposition never takes the Genitiv; it always takes the Akkusativ.",
        ),
    ),
    "prep:durch:1" to PrepositionRuleContent(
        "prep-fixed-acc",
        mapOf(
            "dem" to "dative. These prepositions take the accusative, so den.",
            "das" to "neuter. The noun is masculine, so den.",
            "des" to "genitive. These prepositions take the accusative, so den.",
        ),
    ),
    "prep:für:0" to PrepositionRuleContent(
        "prep-which-fixed",
        mapOf(
            "Dativ" to "this preposition never takes the Dativ; it always takes the Akkusativ.",
            "Genitiv" to "this preposition never takes the Genitiv; it always takes the Akkusativ.",
        ),
    ),
    "prep:für:1" to PrepositionRuleContent(
        "prep-fixed-acc",
        mapOf(
            "dem" to "dative. These prepositions take the accusative, so den.",
            "der" to "feminine dative or masculine nominative. The noun is masculine, so den.",
            "des" to "genitive. These prepositions take the accusative, so den.",
        ),
    ),
    "prep:gegen:0" to PrepositionRuleContent(
        "prep-which-fixed",
        mapOf(
            "Dativ" to "this preposition never takes the Dativ; it always takes the Akkusativ.",
            "Genitiv" to "this preposition never takes the Genitiv; it always takes the Akkusativ.",
        ),
    ),
    "prep:gegen:1" to PrepositionRuleContent(
        "prep-fixed-acc",
        mapOf(
            "dem" to "dative. These prepositions take the accusative, so den.",
            "der" to "feminine dative or masculine nominative. The noun is masculine, so den.",
            "des" to "genitive. These prepositions take the accusative, so den.",
        ),
    ),
    "prep:ohne:0" to PrepositionRuleContent(
        "prep-which-fixed",
        mapOf(
            "Dativ" to "this preposition never takes the Dativ; it always takes the Akkusativ.",
            "Genitiv" to "this preposition never takes the Genitiv; it always takes the Akkusativ.",
        ),
    ),
    "prep:ohne:1" to PrepositionRuleContent(
        "prep-fixed-acc",
        mapOf(
            "dem" to "dative. These prepositions take the accusative, so den.",
            "das" to "neuter. The noun is masculine, so den.",
            "des" to "genitive. These prepositions take the accusative, so den.",
        ),
    ),
    "prep:um:0" to PrepositionRuleContent(
        "prep-which-fixed",
        mapOf(
            "Dativ" to "this preposition never takes the Dativ; it always takes the Akkusativ.",
            "Genitiv" to "this preposition never takes the Genitiv; it always takes the Akkusativ.",
        ),
    ),
    "prep:um:1" to PrepositionRuleContent(
        "prep-fixed-acc",
        mapOf(
            "dem" to "dative. These prepositions take the accusative, so den.",
            "der" to "feminine dative or masculine nominative. The noun is masculine, so den.",
            "des" to "genitive. These prepositions take the accusative, so den.",
        ),
    ),
    "prep:bis:0" to PrepositionRuleContent(
        "prep-which-fixed",
        mapOf(
            "Dativ" to "this preposition never takes the Dativ; it always takes the Akkusativ.",
            "Genitiv" to "this preposition never takes the Genitiv; it always takes the Akkusativ.",
        ),
    ),
    "prep:entlang:0" to PrepositionRuleContent(
        "prep-which-fixed",
        mapOf(
            "Dativ" to "this preposition never takes the Dativ; it always takes the Akkusativ.",
            "Genitiv" to "this preposition never takes the Genitiv; it always takes the Akkusativ.",
        ),
    ),
    "prep:entlang:1" to PrepositionRuleContent(
        "prep-fixed-acc",
        mapOf(
            "dem" to "dative. These prepositions take the accusative, so den.",
            "der" to "feminine dative or masculine nominative. The noun is masculine, so den.",
            "des" to "genitive. These prepositions take the accusative, so den.",
        ),
    ),
    "prep:aus:0" to PrepositionRuleContent(
        "prep-which-fixed",
        mapOf(
            "Akkusativ" to "this preposition never takes the Akkusativ; it always takes the Dativ.",
            "Genitiv" to "this preposition never takes the Genitiv; it always takes the Dativ.",
        ),
    ),
    "prep:aus:1" to PrepositionRuleContent(
        "prep-fixed-dat",
        mapOf(
            "den" to "masculine accusative. These prepositions take the dative, so dem.",
            "das" to "nominative or accusative. These prepositions take the dative, so dem.",
            "des" to "genitive. aus takes the dative, so dem.",
        ),
    ),
    "prep:bei:0" to PrepositionRuleContent(
        "prep-which-fixed",
        mapOf(
            "Akkusativ" to "this preposition never takes the Akkusativ; it always takes the Dativ.",
            "Genitiv" to "this preposition never takes the Genitiv; it always takes the Dativ.",
        ),
    ),
    "prep:bei:1" to PrepositionRuleContent(
        "prep-fixed-dat",
        mapOf(
            "dem" to "singular dative. The noun is plural, so den.",
            "der" to "singular dative feminine. The noun is plural, so den.",
            "die" to "nominative or accusative plural. bei takes the dative, so den.",
        ),
    ),
    "prep:mit:0" to PrepositionRuleContent(
        "prep-which-fixed",
        mapOf(
            "Akkusativ" to "this preposition never takes the Akkusativ; it always takes the Dativ.",
            "Genitiv" to "this preposition never takes the Genitiv; it always takes the Dativ.",
        ),
    ),
    "prep:mit:1" to PrepositionRuleContent(
        "prep-fixed-dat",
        mapOf(
            "den" to "masculine accusative. These prepositions take the dative, so dem.",
            "das" to "nominative or accusative. These prepositions take the dative, so dem.",
            "der" to "feminine dative. The noun is masculine or neuter, so dem.",
        ),
    ),
    "prep:nach:0" to PrepositionRuleContent(
        "prep-which-fixed",
        mapOf(
            "Akkusativ" to "this preposition never takes the Akkusativ; it always takes the Dativ.",
            "Genitiv" to "this preposition never takes the Genitiv; it always takes the Dativ.",
        ),
    ),
    "prep:nach:1" to PrepositionRuleContent(
        "prep-fixed-dat",
        mapOf(
            "den" to "masculine accusative. These prepositions take the dative, so dem.",
            "das" to "nominative or accusative. These prepositions take the dative, so dem.",
            "der" to "feminine dative. The noun is masculine or neuter, so dem.",
        ),
    ),
    "prep:seit:0" to PrepositionRuleContent(
        "prep-which-fixed",
        mapOf(
            "Akkusativ" to "this preposition never takes the Akkusativ; it always takes the Dativ.",
            "Genitiv" to "this preposition never takes the Genitiv; it always takes the Dativ.",
        ),
    ),
    "prep:seit:1" to PrepositionRuleContent(
        "prep-fixed-dat",
        mapOf(
            "den" to "masculine accusative. These prepositions take the dative, so dem.",
            "das" to "nominative or accusative. These prepositions take the dative, so dem.",
            "der" to "feminine dative. The noun is masculine or neuter, so dem.",
        ),
    ),
    "prep:von:0" to PrepositionRuleContent(
        "prep-which-fixed",
        mapOf(
            "Akkusativ" to "this preposition never takes the Akkusativ; it always takes the Dativ.",
            "Genitiv" to "this preposition never takes the Genitiv; it always takes the Dativ.",
        ),
    ),
    "prep:von:1" to PrepositionRuleContent(
        "prep-fixed-dat",
        mapOf(
            "dem" to "singular dative. The noun is plural, so den.",
            "der" to "singular dative feminine. The noun is plural, so den.",
            "des" to "genitive. von takes the dative, so den.",
        ),
    ),
    "prep:zu:0" to PrepositionRuleContent(
        "prep-which-fixed",
        mapOf(
            "Akkusativ" to "this preposition never takes the Akkusativ; it always takes the Dativ.",
            "Genitiv" to "this preposition never takes the Genitiv; it always takes the Dativ.",
        ),
    ),
    "prep:zu:1" to PrepositionRuleContent(
        "prep-fixed-dat",
        mapOf(
            "den" to "masculine accusative. These prepositions take the dative, so dem.",
            "das" to "nominative or accusative. These prepositions take the dative, so dem.",
            "der" to "feminine dative. The noun is masculine or neuter, so dem.",
        ),
    ),
    "prep:gegenüber:0" to PrepositionRuleContent(
        "prep-which-fixed",
        mapOf(
            "Akkusativ" to "this preposition never takes the Akkusativ; it always takes the Dativ.",
            "Genitiv" to "this preposition never takes the Genitiv; it always takes the Dativ.",
        ),
    ),
    "prep:gegenüber:1" to PrepositionRuleContent(
        "prep-fixed-dat",
        mapOf(
            "den" to "masculine accusative. These prepositions take the dative, so dem.",
            "das" to "nominative or accusative. These prepositions take the dative, so dem.",
            "der" to "feminine dative. The noun is masculine or neuter, so dem.",
        ),
    ),
    "prep:wegen:0" to PrepositionRuleContent(
        "prep-which-fixed",
        mapOf(
            "Akkusativ" to "this preposition never takes the Akkusativ; it always takes the Genitiv.",
            "Dativ" to "this preposition takes the Genitiv in standard written German (the Dativ is " +
                    "colloquial), so Genitiv.",
        ),
    ),
    "prep:wegen:1" to PrepositionRuleContent(
        "prep-fixed-gen",
        mapOf(
            "dem" to "dative. These prepositions take the genitive, so des.",
            "den" to "accusative. The noun is masculine or neuter; its genitive is des.",
            "das" to "nominative or accusative. These prepositions take the genitive, so des.",
        ),
    ),
    "prep:trotz:0" to PrepositionRuleContent(
        "prep-which-fixed",
        mapOf(
            "Akkusativ" to "this preposition never takes the Akkusativ; it always takes the Genitiv.",
            "Dativ" to "this preposition takes the Genitiv in standard written German (the Dativ is " +
                    "colloquial), so Genitiv.",
        ),
    ),
    "prep:trotz:1" to PrepositionRuleContent(
        "prep-fixed-gen",
        mapOf(
            "dem" to "dative. These prepositions take the genitive, so des.",
            "den" to "accusative. The noun is masculine or neuter; its genitive is des.",
            "das" to "nominative or accusative. These prepositions take the genitive, so des.",
        ),
    ),
    "prep:während:0" to PrepositionRuleContent(
        "prep-which-fixed",
        mapOf(
            "Akkusativ" to "this preposition never takes the Akkusativ; it always takes the Genitiv.",
            "Dativ" to "this preposition takes the Genitiv in standard written German (the Dativ is " +
                    "colloquial), so Genitiv.",
        ),
    ),
    "prep:während:1" to PrepositionRuleContent(
        "prep-fixed-gen",
        mapOf(
            "die" to "nominative or accusative feminine. während takes the genitive, so der.",
            "den" to "accusative masculine. Fahrt is feminine, so der.",
            "dem" to "dative. während takes the genitive, so der.",
        ),
    ),
    "prep:statt:0" to PrepositionRuleContent(
        "prep-which-fixed",
        mapOf(
            "Akkusativ" to "this preposition never takes the Akkusativ; it always takes the Genitiv.",
            "Dativ" to "this preposition takes the Genitiv in standard written German (the Dativ is " +
                    "colloquial), so Genitiv.",
        ),
    ),
    "prep:statt:1" to PrepositionRuleContent(
        "prep-fixed-gen",
        mapOf(
            "dem" to "dative. These prepositions take the genitive, so des.",
            "den" to "accusative. The noun is masculine or neuter; its genitive is des.",
            "das" to "nominative or accusative. These prepositions take the genitive, so des.",
        ),
    ),
    "prep:an:0" to PrepositionRuleContent(
        "prep-two-way",
        mapOf(
            "die" to "the movement (Wohin?) form. hängen describes a fixed position (Wo?), so der.",
            "den" to "accusative masculine. Wand is feminine, so der.",
            "dem" to "dative masculine or neuter. Wand is feminine, so der.",
        ),
    ),
    "prep:an:1" to PrepositionRuleContent(
        "prep-two-way",
        mapOf(
            "der" to "the fixed-position (Wo?) form. Hanging the picture up is movement (Wohin?), so die.",
            "den" to "accusative masculine. Wand is feminine, so die.",
            "dem" to "dative masculine or neuter. Wand is feminine and this is movement, so die.",
        ),
    ),
    "prep:auf:0" to PrepositionRuleContent(
        "prep-two-way",
        mapOf(
            "den" to "the movement (Wohin?) form. liegen is a fixed position (Wo?), so dem.",
            "der" to "dative feminine. Tisch is masculine, so dem.",
            "das" to "neuter. Tisch is masculine, so dem.",
        ),
    ),
    "prep:auf:1" to PrepositionRuleContent(
        "prep-two-way",
        mapOf(
            "dem" to "the fixed-position (Wo?) form. legen is movement (Wohin?), so den.",
            "der" to "dative feminine. Tisch is masculine, so den.",
            "das" to "neuter. Tisch is masculine, so den.",
        ),
    ),
    "prep:hinter:0" to PrepositionRuleContent(
        "prep-two-way",
        mapOf(
            "das" to "the movement (Wohin?) form. schlafen is a fixed position (Wo?), so dem.",
            "den" to "accusative masculine. Haus is neuter, so dem.",
            "des" to "genitive. A two-way preposition takes the movement or the position form; a fixed " +
                    "position needs dem.",
        ),
    ),
    "prep:hinter:1" to PrepositionRuleContent(
        "prep-two-way",
        mapOf(
            "dem" to "the fixed-position (Wo?) form. rennen is movement (Wohin?), so das.",
            "den" to "accusative masculine. Haus is neuter, so das.",
            "des" to "genitive. Movement (Wohin?) with a neuter noun needs das.",
        ),
    ),
    "prep:in:0" to PrepositionRuleContent(
        "prep-two-way",
        mapOf(
            "den" to "the movement (Wohin?) form. sitzen is a fixed position (Wo?), so dem.",
            "der" to "dative feminine. Garten is masculine, so dem.",
            "das" to "neuter. Garten is masculine, so dem.",
        ),
    ),
    "prep:in:1" to PrepositionRuleContent(
        "prep-two-way",
        mapOf(
            "dem" to "the fixed-position (Wo?) form. gehen is movement (Wohin?), so den.",
            "der" to "dative feminine. Garten is masculine, so den.",
            "das" to "neuter. Garten is masculine, so den.",
        ),
    ),
    "prep:neben:0" to PrepositionRuleContent(
        "prep-two-way",
        mapOf(
            "das" to "the movement (Wohin?) form. stehen is a fixed position (Wo?), so dem.",
            "den" to "accusative masculine. Fenster is neuter, so dem.",
            "des" to "genitive. A fixed position (Wo?) with a neuter noun needs dem.",
        ),
    ),
    "prep:neben:1" to PrepositionRuleContent(
        "prep-two-way",
        mapOf(
            "dem" to "the fixed-position (Wo?) form. sich stellen is movement (Wohin?), so das.",
            "den" to "accusative masculine. Fenster is neuter, so das.",
            "des" to "genitive. Movement (Wohin?) with a neuter noun needs das.",
        ),
    ),
    "prep:über:0" to PrepositionRuleContent(
        "prep-two-way",
        mapOf(
            "das" to "the movement (Wohin?) form. The lamp hangs in a fixed position (Wo?), so dem.",
            "den" to "accusative masculine. Sofa is neuter, so dem.",
            "der" to "dative feminine. Sofa is neuter, so dem.",
        ),
    ),
    "prep:über:1" to PrepositionRuleContent(
        "prep-two-way",
        mapOf(
            "dem" to "the fixed-position (Wo?) form. Hanging the lamp up is movement (Wohin?), so das.",
            "den" to "accusative masculine. Sofa is neuter, so das.",
            "der" to "dative feminine. Sofa is neuter, so das.",
        ),
    ),
    "prep:unter:0" to PrepositionRuleContent(
        "prep-two-way",
        mapOf(
            "das" to "the movement (Wohin?) form. schlafen is a fixed position (Wo?), so dem.",
            "den" to "accusative masculine. Bett is neuter, so dem.",
            "des" to "genitive. A fixed position (Wo?) with a neuter noun needs dem.",
        ),
    ),
    "prep:unter:1" to PrepositionRuleContent(
        "prep-two-way",
        mapOf(
            "dem" to "the fixed-position (Wo?) form. kriechen unter is movement (Wohin?), so das.",
            "den" to "accusative masculine. Bett is neuter, so das.",
            "des" to "genitive. Movement (Wohin?) with a neuter noun needs das.",
        ),
    ),
    "prep:vor:0" to PrepositionRuleContent(
        "prep-two-way",
        mapOf(
            "die" to "the movement (Wohin?) form. stehen is a fixed position (Wo?), so der.",
            "den" to "accusative masculine. Garage is feminine, so der.",
            "dem" to "dative masculine or neuter. Garage is feminine, so der.",
        ),
    ),
    "prep:vor:1" to PrepositionRuleContent(
        "prep-two-way",
        mapOf(
            "der" to "the fixed-position (Wo?) form. fahren is movement (Wohin?), so die.",
            "den" to "accusative masculine. Garage is feminine, so die.",
            "dem" to "dative masculine or neuter. Garage is feminine and this is movement, so die.",
        ),
    ),
    "prep:zwischen:0" to PrepositionRuleContent(
        "prep-two-way",
        mapOf(
            "die" to "the movement (Wohin?) form. sitzen is a fixed position (Wo?), so den.",
            "dem" to "dative singular. Stühle is plural, so den.",
            "der" to "dative singular feminine. Stühle is plural, so den.",
        ),
    ),
    "prep:zwischen:1" to PrepositionRuleContent(
        "prep-two-way",
        mapOf(
            "den" to "the fixed-position (Wo?) form. sich setzen is movement (Wohin?), so die.",
            "der" to "dative singular. Stühle is plural, so die.",
            "dem" to "dative singular. Stühle is plural and this is movement, so die.",
        ),
    ),
)

internal val CASE_ENDING_RULE_CONTENT: Map<String, CaseEndingRuleContent> = mapOf(
    "nom_m_lehrer" to CaseEndingRuleContent(
        "case-subject-nom",
        mapOf(
            "den" to "den is the accusative form for a masculine noun — it marks the thing acted on. This " +
                    "noun does the action, so der.",
            "dem" to "dem is the dative form for a masculine or neuter noun — it marks the receiver. This " +
                    "noun is the doer, so der.",
            "des" to "des is the genitive masculine form — the possessor's form. Nothing is possessed " +
                    "here; the noun is the doer, so der.",
        ),
    ),
    "nom_m_tisch" to CaseEndingRuleContent(
        "case-subject-nom",
        mapOf(
            "den" to "den is the accusative form for a masculine noun — it marks the thing acted on. This " +
                    "noun does the action, so der.",
            "dem" to "dem is the dative form for a masculine or neuter noun — it marks the receiver. This " +
                    "noun is the doer, so der.",
            "des" to "des is the genitive masculine form — the possessor's form. Nothing is possessed " +
                    "here; the noun is the doer, so der.",
        ),
    ),
    "nom_m_hund" to CaseEndingRuleContent(
        "case-subject-nom",
        mapOf(
            "den" to "den is the accusative form for a masculine noun — it marks the thing acted on. This " +
                    "noun does the action, so der.",
            "dem" to "dem is the dative form for a masculine or neuter noun — it marks the receiver. This " +
                    "noun is the doer, so der.",
            "des" to "des is the genitive masculine form — the possessor's form. Nothing is possessed " +
                    "here; the noun is the doer, so der.",
        ),
    ),
    "nom_f_frau" to CaseEndingRuleContent(
        "case-subject-nom",
        mapOf(
            "der" to "der is the dative feminine form (or the masculine doer's form). This noun is " +
                    "feminine and does the action, so die.",
            "den" to "den is the accusative masculine form. This noun is feminine, so die.",
            "dem" to "dem is the dative form for masculine or neuter nouns. This noun is feminine, so die.",
        ),
    ),
    "nom_f_loesung" to CaseEndingRuleContent(
        "case-sein-nom",
        mapOf(
            "der" to "der is the dative feminine form (or the masculine doer's form). Lösung is feminine " +
                    "and follows sein, so die.",
            "den" to "den is the accusative masculine form. Lösung is feminine, so die.",
            "dem" to "dem is the dative form for masculine or neuter nouns. Lösung is feminine, so die.",
        ),
    ),
    "nom_f_aerztin" to CaseEndingRuleContent(
        "case-subject-nom",
        mapOf(
            "der" to "der is the dative feminine form (or the masculine doer's form). This noun is " +
                    "feminine and does the action, so die.",
            "den" to "den is the accusative masculine form. This noun is feminine, so die.",
            "dem" to "dem is the dative form for masculine or neuter nouns. This noun is feminine, so die.",
        ),
    ),
    "nom_n_buch" to CaseEndingRuleContent(
        "case-subject-nom",
        mapOf(
            "den" to "den is the accusative masculine form. This noun is neuter, so das.",
            "dem" to "dem is the dative form for masculine or neuter nouns — the receiver's form. This " +
                    "noun is the doer, so das.",
            "des" to "des is the genitive form for masculine or neuter nouns — the possessor's form. The " +
                    "noun is the doer, so das.",
        ),
    ),
    "nom_n_auto" to CaseEndingRuleContent(
        "case-subject-nom",
        mapOf(
            "den" to "den is the accusative masculine form. This noun is neuter, so das.",
            "dem" to "dem is the dative form for masculine or neuter nouns — the receiver's form. This " +
                    "noun is the doer, so das.",
            "des" to "des is the genitive form for masculine or neuter nouns — the possessor's form. The " +
                    "noun is the doer, so das.",
        ),
    ),
    "nom_n_kind" to CaseEndingRuleContent(
        "case-subject-nom",
        mapOf(
            "den" to "den is the accusative masculine form. This noun is neuter, so das.",
            "dem" to "dem is the dative form for masculine or neuter nouns — the receiver's form. This " +
                    "noun is the doer, so das.",
            "des" to "des is the genitive form for masculine or neuter nouns — the possessor's form. The " +
                    "noun is the doer, so das.",
        ),
    ),
    "nom_pl_kinder" to CaseEndingRuleContent(
        "case-subject-nom",
        mapOf(
            "den" to "den is the dative plural form — it marks plural receivers. This plural noun is the " +
                    "doer, so die.",
            "der" to "der is the dative feminine or the genitive form. This noun is a plural doer, so die.",
            "dem" to "dem is the dative singular form for masculine or neuter nouns. This noun is plural, " +
                    "so die.",
        ),
    ),
    "nom_pl_freunde" to CaseEndingRuleContent(
        "case-subject-nom",
        mapOf(
            "den" to "den is the dative plural form — it marks plural receivers. This plural noun is the " +
                    "doer, so die.",
            "der" to "der is the dative feminine or the genitive form. This noun is a plural doer, so die.",
            "dem" to "dem is the dative singular form for masculine or neuter nouns. This noun is plural, " +
                    "so die.",
        ),
    ),
    "nom_pl_studenten" to CaseEndingRuleContent(
        "case-subject-nom",
        mapOf(
            "den" to "den is the dative plural form — it marks plural receivers. This plural noun is the " +
                    "doer, so die.",
            "der" to "der is the dative feminine or the genitive form. This noun is a plural doer, so die.",
            "dem" to "dem is the dative singular form for masculine or neuter nouns. This noun is plural, " +
                    "so die.",
        ),
    ),
    "akk_m_sehen_hund" to CaseEndingRuleContent(
        "case-verb-acc",
        mapOf(
            "der" to "der is the nominative masculine form — the doer's form. This noun is the thing " +
                    "being seen, bought or visited, so den.",
            "dem" to "dem is the dative form for a masculine or neuter noun — the receiver's form. These " +
                    "verbs act straight on the noun, so den.",
            "des" to "des is the genitive masculine form — the possessor's form. Nothing is possessed " +
                    "here, so den.",
        ),
    ),
    "akk_m_kaufen_wagen" to CaseEndingRuleContent(
        "case-verb-acc",
        mapOf(
            "der" to "der is the nominative masculine form — the doer's form. This noun is the thing " +
                    "being seen, bought or visited, so den.",
            "dem" to "dem is the dative form for a masculine or neuter noun — the receiver's form. These " +
                    "verbs act straight on the noun, so den.",
            "des" to "des is the genitive masculine form — the possessor's form. Nothing is possessed " +
                    "here, so den.",
        ),
    ),
    "akk_m_besuchen_grossvater" to CaseEndingRuleContent(
        "case-verb-acc",
        mapOf(
            "der" to "der is the nominative masculine form — the doer's form. This noun is the thing " +
                    "being seen, bought or visited, so den.",
            "dem" to "dem is the dative form for a masculine or neuter noun — the receiver's form. These " +
                    "verbs act straight on the noun, so den.",
            "des" to "des is the genitive masculine form — the possessor's form. Nothing is possessed " +
                    "here, so den.",
        ),
    ),
    "akk_m_fuer_vater" to CaseEndingRuleContent(
        "prep-fixed-acc",
        mapOf(
            "der" to "der is the nominative masculine form — the doer's form. The noun follows für, ohne " +
                    "or durch, which take the accusative, so den.",
            "dem" to "dem is the dative form for a masculine or neuter noun. These prepositions take the " +
                    "accusative, so den.",
            "des" to "des is the genitive masculine form. These prepositions take the accusative, so den.",
        ),
    ),
    "akk_m_ohne_schirm" to CaseEndingRuleContent(
        "prep-fixed-acc",
        mapOf(
            "der" to "der is the nominative masculine form — the doer's form. The noun follows für, ohne " +
                    "or durch, which take the accusative, so den.",
            "dem" to "dem is the dative form for a masculine or neuter noun. These prepositions take the " +
                    "accusative, so den.",
            "des" to "des is the genitive masculine form. These prepositions take the accusative, so den.",
        ),
    ),
    "akk_m_durch_tunnel" to CaseEndingRuleContent(
        "prep-fixed-acc",
        mapOf(
            "der" to "der is the nominative masculine form — the doer's form. The noun follows für, ohne " +
                    "or durch, which take the accusative, so den.",
            "dem" to "dem is the dative form for a masculine or neuter noun. These prepositions take the " +
                    "accusative, so den.",
            "des" to "des is the genitive masculine form. These prepositions take the accusative, so den.",
        ),
    ),
    "akk_f_kennen_frau" to CaseEndingRuleContent(
        "case-verb-acc",
        mapOf(
            "der" to "der is the dative feminine form (or the masculine doer's form). kennen and lesen " +
                    "act straight on the noun, so die.",
            "den" to "den is the accusative masculine form. This noun is feminine, so die.",
            "dem" to "dem is the dative form for masculine or neuter nouns. This noun is feminine, so die.",
        ),
    ),
    "akk_f_lesen_zeitung" to CaseEndingRuleContent(
        "case-verb-acc",
        mapOf(
            "der" to "der is the dative feminine form (or the masculine doer's form). kennen and lesen " +
                    "act straight on the noun, so die.",
            "den" to "den is the accusative masculine form. This noun is feminine, so die.",
            "dem" to "dem is the dative form for masculine or neuter nouns. This noun is feminine, so die.",
        ),
    ),
    "akk_f_gegen_grippe" to CaseEndingRuleContent(
        "prep-fixed-acc",
        mapOf(
            "der" to "der is the dative feminine form (or the masculine doer's form). gegen takes the " +
                    "accusative, so die.",
            "den" to "den is the accusative masculine form. Grippe is feminine, so die.",
            "dem" to "dem is the dative form for masculine or neuter nouns. Grippe is feminine, so die.",
        ),
    ),
    "akk_n_oeffnen_fenster" to CaseEndingRuleContent(
        "case-verb-acc",
        mapOf(
            "den" to "den is the accusative masculine form. This noun is neuter, so das.",
            "dem" to "dem is the dative form for masculine or neuter nouns. öffnen and trinken act " +
                    "straight on the noun, so das.",
            "des" to "des is the genitive form for masculine or neuter nouns — the possessor's form. No " +
                    "possession here, so das.",
        ),
    ),
    "akk_n_trinken_wasser" to CaseEndingRuleContent(
        "case-verb-acc",
        mapOf(
            "den" to "den is the accusative masculine form. This noun is neuter, so das.",
            "dem" to "dem is the dative form for masculine or neuter nouns. öffnen and trinken act " +
                    "straight on the noun, so das.",
            "des" to "des is the genitive form for masculine or neuter nouns — the possessor's form. No " +
                    "possession here, so das.",
        ),
    ),
    "akk_n_um_schloss" to CaseEndingRuleContent(
        "prep-fixed-acc",
        mapOf(
            "den" to "den is the accusative masculine form. Schloss is neuter, so das.",
            "dem" to "dem is the dative form for masculine and neuter nouns. um takes the accusative, so " +
                    "das.",
            "des" to "des is the genitive form for masculine and neuter nouns. um takes the accusative, " +
                    "so das.",
        ),
    ),
    "akk_pl_zaehlen_tage" to CaseEndingRuleContent(
        "case-verb-acc",
        mapOf(
            "den" to "den is the dative plural form. zählt takes the days as a direct object, so die.",
            "der" to "der is the dative or genitive feminine form. The days are a plural direct object, " +
                    "so die.",
            "dem" to "dem is the dative singular form for masculine or neuter nouns. This noun is plural, " +
                    "so die.",
        ),
    ),
    "akk_pl_fuer_grosseltern" to CaseEndingRuleContent(
        "prep-fixed-acc",
        mapOf(
            "den" to "den is the dative plural form. für takes the accusative, so die.",
            "der" to "der is the dative or genitive feminine form. Großeltern is a plural accusative, so " +
                    "die.",
            "dem" to "dem is the dative singular form for masculine or neuter nouns. Großeltern is " +
                    "plural, so die.",
        ),
    ),
    "dat_m_mit_bus" to CaseEndingRuleContent(
        "prep-fixed-dat",
        mapOf(
            "der" to "der is the nominative masculine form — the doer's form. The noun follows mit, nach " +
                    "or bei, which take the dative, so dem.",
            "den" to "den is the accusative masculine (or dative plural) form. These prepositions take " +
                    "the dative, so dem.",
            "des" to "des is the genitive masculine form. These prepositions take the dative, so dem.",
        ),
    ),
    "dat_m_nach_unterricht" to CaseEndingRuleContent(
        "prep-fixed-dat",
        mapOf(
            "der" to "der is the nominative masculine form — the doer's form. The noun follows mit, nach " +
                    "or bei, which take the dative, so dem.",
            "den" to "den is the accusative masculine (or dative plural) form. These prepositions take " +
                    "the dative, so dem.",
            "des" to "des is the genitive masculine form. These prepositions take the dative, so dem.",
        ),
    ),
    "dat_m_bei_onkel" to CaseEndingRuleContent(
        "prep-fixed-dat",
        mapOf(
            "der" to "der is the nominative masculine form — the doer's form. The noun follows mit, nach " +
                    "or bei, which take the dative, so dem.",
            "den" to "den is the accusative masculine (or dative plural) form. These prepositions take " +
                    "the dative, so dem.",
            "des" to "des is the genitive masculine form. These prepositions take the dative, so dem.",
        ),
    ),
    "dat_m_helfen_mann" to CaseEndingRuleContent(
        "case-verb-dat",
        mapOf(
            "der" to "der is the nominative masculine form — the doer's form. The doer is ich or wir; the " +
                    "man or the doctor receives the help or the thanks, so dem.",
            "den" to "den is the accusative masculine form — the direct object's form. These verbs do not " +
                    "act on the person, so dem.",
            "des" to "des is the genitive masculine form — the possessor's form. No possession is marked " +
                    "on the article here, so dem.",
        ),
    ),
    "dat_m_danken_arzt" to CaseEndingRuleContent(
        "case-verb-dat",
        mapOf(
            "der" to "der is the nominative masculine form — the doer's form. The doer is ich or wir; the " +
                    "man or the doctor receives the help or the thanks, so dem.",
            "den" to "den is the accusative masculine form — the direct object's form. These verbs do not " +
                    "act on the person, so dem.",
            "des" to "des is the genitive masculine form — the possessor's form. No possession is marked " +
                    "on the article here, so dem.",
        ),
    ),
    "dat_f_schmecken_mutter" to CaseEndingRuleContent(
        "case-verb-dat",
        mapOf(
            "die" to "die is the nominative or accusative feminine form. schmeckt and gefällt name the " +
                    "person who enjoys or likes, so the dative feminine der.",
            "den" to "den is the accusative masculine (or dative plural) form. This person is feminine, " +
                    "so der.",
            "dem" to "dem is the dative form for masculine or neuter nouns. This person is feminine — the " +
                    "dative feminine form is der.",
        ),
    ),
    "dat_f_gefallen_frau" to CaseEndingRuleContent(
        "case-verb-dat",
        mapOf(
            "die" to "die is the nominative or accusative feminine form. schmeckt and gefällt name the " +
                    "person who enjoys or likes, so the dative feminine der.",
            "den" to "den is the accusative masculine (or dative plural) form. This person is feminine, " +
                    "so der.",
            "dem" to "dem is the dative form for masculine or neuter nouns. This person is feminine — the " +
                    "dative feminine form is der.",
        ),
    ),
    "dat_f_aus_schule" to CaseEndingRuleContent(
        "prep-fixed-dat",
        mapOf(
            "die" to "die is the nominative or accusative feminine form. aus and zu take the dative, so " +
                    "the dative feminine form der.",
            "den" to "den is the accusative masculine (or dative plural) form. This noun is feminine " +
                    "dative, so der.",
            "dem" to "dem is the dative form for masculine or neuter nouns. Schule and Post are feminine, " +
                    "so der.",
        ),
    ),
    "dat_f_zu_post" to CaseEndingRuleContent(
        "prep-fixed-dat",
        mapOf(
            "die" to "die is the nominative or accusative feminine form. aus and zu take the dative, so " +
                    "the dative feminine form der.",
            "den" to "den is the accusative masculine (or dative plural) form. This noun is feminine " +
                    "dative, so der.",
            "dem" to "dem is the dative form for masculine or neuter nouns. Schule and Post are feminine, " +
                    "so der.",
        ),
    ),
    "dat_n_mit_kind" to CaseEndingRuleContent(
        "prep-fixed-dat",
        mapOf(
            "das" to "das is the nominative or accusative neuter form. mit and von take the dative, so " +
                    "dem.",
            "den" to "den is the accusative masculine (or dative plural) form. This noun is neuter " +
                    "dative, so dem.",
            "des" to "des is the genitive form for masculine and neuter nouns. mit and von take the " +
                    "dative, so dem.",
        ),
    ),
    "dat_n_von_konzert" to CaseEndingRuleContent(
        "prep-fixed-dat",
        mapOf(
            "das" to "das is the nominative or accusative neuter form. mit and von take the dative, so " +
                    "dem.",
            "den" to "den is the accusative masculine (or dative plural) form. This noun is neuter " +
                    "dative, so dem.",
            "des" to "des is the genitive form for masculine and neuter nouns. mit and von take the " +
                    "dative, so dem.",
        ),
    ),
    "dat_n_gehoeren_maedchen" to CaseEndingRuleContent(
        "case-verb-dat",
        mapOf(
            "das" to "das is the nominative or accusative neuter form. gehören does not make the girl a " +
                    "doer or an object — she is the owner, so dem.",
            "den" to "den is the accusative masculine (or dative plural) form. This receiver is a neuter " +
                    "singular, so dem.",
            "des" to "des is the genitive neuter form. gehören is about possession, but German names the " +
                    "owner with dem, not des.",
        ),
    ),
    "dat_pl_mit_kollegen" to CaseEndingRuleContent(
        "prep-fixed-dat",
        mapOf(
            "die" to "die is the nominative or accusative plural form. mit takes the dative, so the " +
                    "dative plural den.",
            "der" to "der is the dative or genitive feminine form. Kollegen is a plural receiver, so den.",
            "dem" to "dem is the dative singular form. Kollegen is plural, so den.",
        ),
    ),
    "dat_pl_antworten_schuelern" to CaseEndingRuleContent(
        "case-verb-dat",
        mapOf(
            "die" to "die is the nominative or accusative plural form. antwortet names the pupils as " +
                    "receivers, so the dative plural den.",
            "der" to "der is the dative or genitive feminine form. The pupils are plural receivers, so " +
                    "den.",
            "dem" to "dem is the dative singular form. Schülern is plural — its -n ending already shows " +
                    "the dative plural, so den.",
        ),
    ),
    "gen_m_trotz_regens" to CaseEndingRuleContent(
        "prep-fixed-gen",
        mapOf(
            "der" to "der is the nominative masculine form — the doer's form. The noun follows trotz, " +
                    "wegen, anstatt, infolge or unweit, which take the genitive, so des.",
            "den" to "den is the accusative masculine (or dative plural) form. These prepositions take " +
                    "the genitive, so des.",
            "dem" to "dem is the dative form for a masculine or neuter noun. These prepositions take the " +
                    "genitive, so des.",
        ),
    ),
    "gen_m_wegen_streiks" to CaseEndingRuleContent(
        "prep-fixed-gen",
        mapOf(
            "der" to "der is the nominative masculine form — the doer's form. The noun follows trotz, " +
                    "wegen, anstatt, infolge or unweit, which take the genitive, so des.",
            "den" to "den is the accusative masculine (or dative plural) form. These prepositions take " +
                    "the genitive, so des.",
            "dem" to "dem is the dative form for a masculine or neuter noun. These prepositions take the " +
                    "genitive, so des.",
        ),
    ),
    "gen_n_waehrend_konzerts" to CaseEndingRuleContent(
        "prep-fixed-gen",
        mapOf(
            "das" to "das is the nominative or accusative neuter form. während and jenseits take the " +
                    "genitive, so des.",
            "den" to "den is the accusative masculine (or dative plural) form. These prepositions take " +
                    "the genitive, so des.",
            "dem" to "dem is the dative form for masculine and neuter nouns. These prepositions take the " +
                    "genitive, so des.",
        ),
    ),
    "gen_m_anstatt_kaffees" to CaseEndingRuleContent(
        "prep-fixed-gen",
        mapOf(
            "der" to "der is the nominative masculine form — the doer's form. The noun follows trotz, " +
                    "wegen, anstatt, infolge or unweit, which take the genitive, so des.",
            "den" to "den is the accusative masculine (or dative plural) form. These prepositions take " +
                    "the genitive, so des.",
            "dem" to "dem is the dative form for a masculine or neuter noun. These prepositions take the " +
                    "genitive, so des.",
        ),
    ),
    "gen_f_ausserhalb_stadt" to CaseEndingRuleContent(
        "prep-fixed-gen",
        mapOf(
            "die" to "die is the nominative or accusative feminine form. außerhalb, innerhalb and " +
                    "anlässlich take the genitive, so the genitive feminine form der.",
            "den" to "den is the accusative masculine (or dative plural) form. These prepositions take " +
                    "the genitive, so der.",
            "dem" to "dem is the dative form for masculine or neuter nouns. These nouns are feminine, so " +
                    "der.",
        ),
    ),
    "gen_f_innerhalb_woche" to CaseEndingRuleContent(
        "prep-fixed-gen",
        mapOf(
            "die" to "die is the nominative or accusative feminine form. außerhalb, innerhalb and " +
                    "anlässlich take the genitive, so the genitive feminine form der.",
            "den" to "den is the accusative masculine (or dative plural) form. These prepositions take " +
                    "the genitive, so der.",
            "dem" to "dem is the dative form for masculine or neuter nouns. These nouns are feminine, so " +
                    "der.",
        ),
    ),
    "gen_f_anlaesslich_feier" to CaseEndingRuleContent(
        "prep-fixed-gen",
        mapOf(
            "die" to "die is the nominative or accusative feminine form. außerhalb, innerhalb and " +
                    "anlässlich take the genitive, so the genitive feminine form der.",
            "den" to "den is the accusative masculine (or dative plural) form. These prepositions take " +
                    "the genitive, so der.",
            "dem" to "dem is the dative form for masculine or neuter nouns. These nouns are feminine, so " +
                    "der.",
        ),
    ),
    "gen_n_unweit_bahnhofs" to CaseEndingRuleContent(
        "prep-fixed-gen",
        mapOf(
            "der" to "der is the nominative masculine form — the doer's form. The noun follows trotz, " +
                    "wegen, anstatt, infolge or unweit, which take the genitive, so des.",
            "den" to "den is the accusative masculine (or dative plural) form. These prepositions take " +
                    "the genitive, so des.",
            "dem" to "dem is the dative form for a masculine or neuter noun. These prepositions take the " +
                    "genitive, so des.",
        ),
    ),
    "gen_n_jenseits_dorfes" to CaseEndingRuleContent(
        "prep-fixed-gen",
        mapOf(
            "das" to "das is the nominative or accusative neuter form. während and jenseits take the " +
                    "genitive, so des.",
            "den" to "den is the accusative masculine (or dative plural) form. These prepositions take " +
                    "the genitive, so des.",
            "dem" to "dem is the dative form for masculine and neuter nouns. These prepositions take the " +
                    "genitive, so des.",
        ),
    ),
    "gen_m_infolge_unfalls" to CaseEndingRuleContent(
        "prep-fixed-gen",
        mapOf(
            "der" to "der is the nominative masculine form — the doer's form. The noun follows trotz, " +
                    "wegen, anstatt, infolge or unweit, which take the genitive, so des.",
            "den" to "den is the accusative masculine (or dative plural) form. These prepositions take " +
                    "the genitive, so des.",
            "dem" to "dem is the dative form for a masculine or neuter noun. These prepositions take the " +
                    "genitive, so des.",
        ),
    ),
    "gen_pl_trotz_probleme" to CaseEndingRuleContent(
        "prep-fixed-gen",
        mapOf(
            "die" to "die is the nominative or accusative plural form. trotz and wegen take the genitive " +
                    "here, so der.",
            "den" to "den is the accusative masculine (or dative plural) form. These nouns are genitive " +
                    "plural, so der.",
            "dem" to "dem is the dative singular form. These nouns are plural, so der.",
        ),
    ),
    "gen_pl_wegen_bauarbeiten" to CaseEndingRuleContent(
        "prep-fixed-gen",
        mapOf(
            "die" to "die is the nominative or accusative plural form. trotz and wegen take the genitive " +
                    "here, so der.",
            "den" to "den is the accusative masculine (or dative plural) form. These nouns are genitive " +
                    "plural, so der.",
            "dem" to "dem is the dative singular form. These nouns are plural, so der.",
        ),
    ),
)

/** Keyed by "<verb> <preposition>" exactly as in [DrillVerbPrepositions.PAIRS]. */
internal val VERB_PREP_RULE_CONTENT: Map<String, VerbPrepRuleContent> = mapOf(
    "warten auf" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "an" to "an belongs to pairs like *denken an*. Waiting for something is *warten auf* (auf " +
                    "den Bus).",
            "für" to "für belongs to pairs like *danken für*. The pair here is *warten auf*.",
            "über" to "über belongs to talk pairs like *sprechen über*. *warten* pairs with *auf*.",
        ),
        caseRuleId = "verb-prep-case-pair",
        caseMiss = mapOf(
            "Dativ" to "with warten, auf is fixed as accusative. The same auf is dative with other verbs " +
                    "(bestehen auf), so learn the case with the pair.",
        ),
    ),
    "denken an" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "auf" to "auf belongs to pairs like *warten auf* and *sich freuen auf*. Thinking of someone " +
                    "is *denken an*.",
            "in" to "in belongs to *sich verlieben in*. *denken* pairs with *an* (an meine Familie).",
            "von" to "von belongs to *träumen von*. The pair is *denken an*.",
        ),
        caseRuleId = "verb-prep-case-pair",
        caseMiss = mapOf(
            "Dativ" to "with denken, an takes the accusative.",
        ),
    ),
    "sich interessieren für" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "an" to "an belongs to pairs like *denken an* and *teilnehmen an*. Being interested in " +
                    "something is *sich interessieren für* (für moderne Kunst).",
            "über" to "über belongs to talk pairs like *sprechen über*. *sich interessieren* pairs with " +
                    "*für*.",
            "auf" to "auf belongs to pairs like *hoffen auf*. The pair is *sich interessieren für*.",
        ),
        caseRuleId = "prep-which-fixed",
        caseMiss = mapOf(
            "Dativ" to "the case comes from the preposition — für always takes the accusative, so *sich " +
                    "interessieren für* is accusative.",
        ),
    ),
    "teilnehmen an" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "auf" to "auf belongs to pairs like *sich freuen auf*. Taking part in something is " +
                    "*teilnehmen an* (an dem Deutschkurs).",
            "bei" to "bei belongs to *helfen bei*. *teilnehmen* pairs with *an*.",
            "in" to "in belongs to *sich verlieben in*. The pair is *teilnehmen an*.",
        ),
        caseRuleId = "verb-prep-case-pair",
        caseMiss = mapOf(
            "Akkusativ" to "with teilnehmen, an takes the dative. The same an is accusative with other verbs " +
                    "(denken an).",
        ),
    ),
    "Angst haben vor" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "an" to "an is not the fear pair. Fear of something is *Angst haben vor* (vor großen Hunden).",
            "von" to "von belongs to *träumen von*. The fear pair is *vor*.",
            "bei" to "bei belongs to *helfen bei*. *Angst haben* pairs with *vor*.",
        ),
        caseRuleId = "verb-prep-case-pair",
        caseMiss = mapOf(
            "Akkusativ" to "with Angst haben, vor takes the dative.",
        ),
    ),
    "sich freuen auf" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-meaning",
        gapMiss = mapOf(
            "über" to "*freuen über* is for joy about something that has already happened. Der Urlaub is " +
                    "still ahead, so *freuen auf* (auf den Urlaub).",
            "an" to "*freuen* pairs with *auf* or *über*, never with an. an belongs to pairs like " +
                    "*denken an*.",
            "für" to "für belongs to pairs like *sich interessieren für*. Looking forward is *freuen auf*.",
        ),
        caseRuleId = "verb-prep-case-pair",
        caseMiss = mapOf(
            "Dativ" to "with sich freuen (looking forward), auf takes the accusative. *sich freuen über* is " +
                    "the other pair.",
        ),
    ),
    "sich freuen über" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-meaning",
        gapMiss = mapOf(
            "auf" to "*freuen auf* is for looking forward to something still to come. Das Geschenk is " +
                    "already here, so *freuen über* (über das Geschenk).",
            "an" to "*freuen* has no an pair. Joy about something present is *freuen über*.",
            "für" to "für belongs to pairs like *danken für*. The pair here is *freuen über*.",
        ),
        caseRuleId = "verb-prep-case-pair",
        caseMiss = mapOf(
            "Dativ" to "with sich freuen (happy about something), über takes the accusative.",
        ),
    ),
    "bitten um" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "für" to "für belongs to pairs like *danken für*. Asking for something is *bitten um* (um " +
                    "Hilfe).",
            "nach" to "nach belongs to *fragen nach*. *bitten* pairs with *um*.",
            "zu" to "zu belongs to *einladen zu*. The pair is *bitten um*.",
        ),
        caseRuleId = "prep-which-fixed",
        caseMiss = mapOf(
            "Dativ" to "um always takes the accusative, so *bitten um* is accusative.",
        ),
    ),
    "danken für" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "an" to "an belongs to *denken an*. Thanking for something is *danken für* (für deine " +
                    "Unterstützung).",
            "um" to "um belongs to *bitten um*. *danken* pairs with *für*.",
            "zu" to "zu belongs to *gratulieren zu*. The pair is *danken für*.",
        ),
        caseRuleId = "prep-which-fixed",
        caseMiss = mapOf(
            "Dativ" to "für always takes the accusative, so *danken für* is accusative.",
        ),
    ),
    "hoffen auf" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "an" to "an belongs to *glauben an*. Hoping for something is *hoffen auf* (auf besseres " +
                    "Wetter).",
            "in" to "in belongs to *sich verlieben in*. *hoffen* pairs with *auf*.",
            "über" to "über belongs to *sprechen über*. The pair is *hoffen auf*.",
        ),
        caseRuleId = "verb-prep-case-pair",
        caseMiss = mapOf(
            "Dativ" to "with hoffen, auf takes the accusative.",
        ),
    ),
    "sich erinnern an" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "auf" to "auf belongs to *sich freuen auf*. Remembering something is *sich erinnern an* (an " +
                    "diesen Tag).",
            "in" to "in belongs to *sich verlieben in*. *sich erinnern* pairs with *an*.",
            "von" to "von belongs to *träumen von*. The memory pair is *sich erinnern an*.",
        ),
        caseRuleId = "verb-prep-case-pair",
        caseMiss = mapOf(
            "Dativ" to "with sich erinnern, an takes the accusative.",
        ),
    ),
    "glauben an" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "auf" to "auf belongs to *hoffen auf*. Believing in something is *glauben an* (an eine " +
                    "bessere Zukunft).",
            "in" to "in belongs to *sich verlieben in*. *glauben* pairs with *an*.",
            "zu" to "zu belongs to *einladen zu*. The pair is *glauben an*.",
        ),
        caseRuleId = "verb-prep-case-pair",
        caseMiss = mapOf(
            "Dativ" to "with glauben, an takes the accusative.",
        ),
    ),
    "achten auf" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "an" to "an belongs to *denken an*. Paying attention to something is *achten auf* (auf deine " +
                    "Gesundheit).",
            "über" to "über belongs to *sprechen über*. *achten* pairs with *auf*.",
            "in" to "in belongs to *sich verlieben in*. The pair is *achten auf*.",
        ),
        caseRuleId = "verb-prep-case-pair",
        caseMiss = mapOf(
            "Dativ" to "with achten, auf takes the accusative.",
        ),
    ),
    "sich vorbereiten auf" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "an" to "an belongs to *teilnehmen an*. Preparing for something is *sich vorbereiten auf* " +
                    "(auf die Prüfung).",
            "für" to "für belongs to *sich interessieren für*. The preparation pair is *auf*.",
            "vor" to "vor belongs to *Angst haben vor*. *sich vorbereiten* pairs with *auf*.",
        ),
        caseRuleId = "verb-prep-case-pair",
        caseMiss = mapOf(
            "Dativ" to "with sich vorbereiten, auf takes the accusative.",
        ),
    ),
    "sprechen über" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-meaning",
        gapMiss = mapOf(
            "auf" to "auf belongs to *warten auf* and *hoffen auf*. Speaking about a topic is *sprechen " +
                    "über* (über das Projekt).",
            "von" to "von belongs to *erzählen von* and *träumen von*. The topic of a talk takes über.",
            "an" to "an belongs to *denken an*. *sprechen* takes über for a topic and mit for a person.",
        ),
        caseRuleId = "verb-prep-case-pair",
        caseMiss = mapOf(
            "Dativ" to "with sprechen (about a topic), über takes the accusative.",
        ),
    ),
    "nachdenken über" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "an" to "an belongs to *denken an*. Thinking something over is *nachdenken über* (über " +
                    "deinen Vorschlag).",
            "auf" to "auf belongs to *warten auf*. *nachdenken* pairs with *über*.",
            "um" to "um belongs to *bitten um*. The pair is *nachdenken über*.",
        ),
        caseRuleId = "verb-prep-case-pair",
        caseMiss = mapOf(
            "Dativ" to "with nachdenken, über takes the accusative.",
        ),
    ),
    "sich ärgern über" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "auf" to "auf belongs to *sich freuen auf*. Being annoyed about something is *sich ärgern " +
                    "über* (über den Verkehr).",
            "an" to "an belongs to *denken an*. *sich ärgern* pairs with *über*.",
            "für" to "für belongs to *danken für*. The pair is *sich ärgern über*.",
        ),
        caseRuleId = "verb-prep-case-pair",
        caseMiss = mapOf(
            "Dativ" to "with sich ärgern, über takes the accusative.",
        ),
    ),
    "sich beschweren über" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "an" to "an belongs to *denken an*. Complaining about something is *sich beschweren über* " +
                    "(über den Kaffee).",
            "auf" to "auf belongs to *hoffen auf*. The complaint pair is *über*.",
            "zu" to "zu belongs to *gratulieren zu*. *sich beschweren* pairs with *über*.",
        ),
        caseRuleId = "verb-prep-case-pair",
        caseMiss = mapOf(
            "Dativ" to "with sich beschweren, über takes the accusative.",
        ),
    ),
    "sich kümmern um" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "für" to "*sorgen für* is the other caring pair, but *kümmern* goes with *um* (um die Katze).",
            "an" to "an belongs to *denken an*. Taking care of something is *sich kümmern um*.",
            "nach" to "nach belongs to *fragen nach*. The pair is *sich kümmern um*.",
        ),
        caseRuleId = "prep-which-fixed",
        caseMiss = mapOf(
            "Dativ" to "um always takes the accusative, so *sich kümmern um* is accusative.",
        ),
    ),
    "sich bewerben um" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "auf" to "auf belongs to *sich freuen auf*. Applying for something is *sich bewerben um* (um " +
                    "die Stelle).",
            "für" to "für belongs to *sich interessieren für*. The application pair is *um*.",
            "nach" to "nach belongs to *fragen nach*. *sich bewerben* pairs with *um*.",
        ),
        caseRuleId = "prep-which-fixed",
        caseMiss = mapOf(
            "Dativ" to "um always takes the accusative, so *sich bewerben um* is accusative.",
        ),
    ),
    "sich verlieben in" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "an" to "an belongs to *denken an*. Falling in love with someone is *sich verlieben in* (in " +
                    "seine Kollegin).",
            "auf" to "auf belongs to *warten auf*. *sich verlieben* pairs with *in*.",
            "zu" to "zu belongs to *gehören zu*. The pair is *sich verlieben in*.",
        ),
        caseRuleId = "verb-prep-case-pair",
        caseMiss = mapOf(
            "Dativ" to "with sich verlieben, in takes the accusative.",
        ),
    ),
    "passen zu" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "mit" to "mit belongs to *telefonieren mit*. Fitting to something is *passen zu* (zu der " +
                    "Hose).",
            "an" to "an belongs to *denken an*. *passen* pairs with *zu*.",
            "für" to "der Hose is dative, and für takes the accusative. The pair is *passen zu*.",
        ),
        caseRuleId = "prep-which-fixed",
        caseMiss = mapOf(
            "Akkusativ" to "zu always takes the dative, so *passen zu* is dative.",
        ),
    ),
    "gehören zu" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "an" to "an belongs to *glauben an*. Belonging to something is *gehören zu* (zu unserer " +
                    "Bibliothek).",
            "mit" to "mit belongs to *telefonieren mit*. *gehören* pairs with *zu*.",
            "bei" to "bei belongs to *helfen bei*. The pair is *gehören zu*.",
        ),
        caseRuleId = "prep-which-fixed",
        caseMiss = mapOf(
            "Akkusativ" to "zu always takes the dative, so *gehören zu* is dative.",
        ),
    ),
    "gratulieren zu" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "an" to "an belongs to *teilnehmen an*. Congratulating on something is *gratulieren zu* (zu " +
                    "deinem Geburtstag).",
            "für" to "deinem Geburtstag is dative, and für takes the accusative. *gratulieren* pairs with " +
                    "*zu*.",
            "bei" to "bei belongs to *helfen bei* and *sich bedanken bei*. The pair is *gratulieren zu*.",
        ),
        caseRuleId = "prep-which-fixed",
        caseMiss = mapOf(
            "Akkusativ" to "zu always takes the dative, so *gratulieren zu* is dative.",
        ),
    ),
    "einladen zu" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "in" to "in belongs to *sich verlieben in*. Inviting to something is *einladen zu* (zu der " +
                    "Party).",
            "an" to "an belongs to *teilnehmen an*. *einladen* pairs with *zu*.",
            "auf" to "auf belongs to *warten auf*. The pair is *einladen zu*.",
        ),
        caseRuleId = "prep-which-fixed",
        caseMiss = mapOf(
            "Akkusativ" to "zu always takes the dative, so *einladen zu* is dative.",
        ),
    ),
    "helfen bei" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "mit" to "mit belongs to *telefonieren mit*. Helping with something is *helfen bei* (bei den " +
                    "Hausaufgaben).",
            "in" to "in belongs to *sich verlieben in*. *helfen* pairs with *bei*.",
            "zu" to "zu belongs to *einladen zu*. The pair is *helfen bei*.",
        ),
        caseRuleId = "prep-which-fixed",
        caseMiss = mapOf(
            "Akkusativ" to "bei always takes the dative, so *helfen bei* is dative.",
        ),
    ),
    "anfangen mit" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "bei" to "bei belongs to *helfen bei*. Starting with something is *anfangen mit* (mit der " +
                    "Arbeit).",
            "in" to "in belongs to *sich verlieben in*. *anfangen* pairs with *mit*.",
            "zu" to "zu belongs to *einladen zu*. The pair is *anfangen mit*.",
        ),
        caseRuleId = "prep-which-fixed",
        caseMiss = mapOf(
            "Akkusativ" to "mit always takes the dative, so *anfangen mit* is dative.",
        ),
    ),
    "aufhören mit" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "von" to "von belongs to *träumen von*. Stopping with something is *aufhören mit* (mit dem " +
                    "Rauchen).",
            "bei" to "bei belongs to *helfen bei*. *aufhören* pairs with *mit*.",
            "zu" to "zu belongs to *gratulieren zu*. The pair is *aufhören mit*.",
        ),
        caseRuleId = "prep-which-fixed",
        caseMiss = mapOf(
            "Akkusativ" to "mit always takes the dative, so *aufhören mit* is dative.",
        ),
    ),
    "sprechen mit" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-meaning",
        gapMiss = mapOf(
            "bei" to "bei belongs to *helfen bei*. Speaking with a person is *sprechen mit* (mit dem " +
                    "Chef).",
            "zu" to "*sprechen zu* is addressing an audience, as in a speech. A conversation with a " +
                    "person is *sprechen mit* (mit dem Chef).",
            "von" to "*von jemandem sprechen* means mentioning someone. Talking with the person is " +
                    "*sprechen mit*; über is for the topic.",
        ),
        caseRuleId = "prep-which-fixed",
        caseMiss = mapOf(
            "Akkusativ" to "mit always takes the dative, so *sprechen mit* is dative.",
        ),
    ),
    "telefonieren mit" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "zu" to "zu belongs to *einladen zu*. Phoning someone is *telefonieren mit* (mit ihrer " +
                    "Großmutter).",
            "an" to "an belongs to *denken an*. *telefonieren* pairs with *mit*.",
            "bei" to "bei belongs to *helfen bei*. The pair is *telefonieren mit*.",
        ),
        caseRuleId = "prep-which-fixed",
        caseMiss = mapOf(
            "Akkusativ" to "mit always takes the dative, so *telefonieren mit* is dative.",
        ),
    ),
    "fragen nach" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "zu" to "zu belongs to *einladen zu*. Asking for information is *fragen nach* (nach dem Weg).",
            "für" to "dem Weg is dative, and für takes the accusative. *fragen* pairs with *nach*.",
            "um" to "um belongs to *bitten um*. The pair is *fragen nach*.",
        ),
        caseRuleId = "prep-which-fixed",
        caseMiss = mapOf(
            "Akkusativ" to "nach always takes the dative, so *fragen nach* is dative.",
        ),
    ),
    "suchen nach" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "für" to "dem verlorenen Schlüssel is dative, and für takes the accusative. *suchen* pairs " +
                    "with *nach* (nach dem Schlüssel).",
            "um" to "um belongs to *sich kümmern um*. Searching for something is *suchen nach*.",
            "zu" to "zu belongs to *gehören zu*. The pair is *suchen nach*.",
        ),
        caseRuleId = "prep-which-fixed",
        caseMiss = mapOf(
            "Akkusativ" to "nach always takes the dative, so *suchen nach* is dative.",
        ),
    ),
    "riechen nach" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "von" to "von belongs to *träumen von*. Smelling of something is *riechen nach* (nach " +
                    "frischem Kuchen).",
            "zu" to "zu belongs to *gratulieren zu*. *riechen* pairs with *nach*.",
            "mit" to "mit belongs to *telefonieren mit*. The pair is *riechen nach*.",
        ),
        caseRuleId = "prep-which-fixed",
        caseMiss = mapOf(
            "Akkusativ" to "nach always takes the dative, so *riechen nach* is dative.",
        ),
    ),
    "schmecken nach" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "von" to "von belongs to *träumen von*. Tasting of something is *schmecken nach* (nach süßen " +
                    "Erdbeeren).",
            "zu" to "zu belongs to *einladen zu*. *schmecken* pairs with *nach*.",
            "mit" to "mit belongs to *telefonieren mit*. The pair is *schmecken nach*.",
        ),
        caseRuleId = "prep-which-fixed",
        caseMiss = mapOf(
            "Akkusativ" to "nach always takes the dative, so *schmecken nach* is dative.",
        ),
    ),
    "sich sehnen nach" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "zu" to "zu belongs to *einladen zu*. Longing for something is *sich sehnen nach* (nach der " +
                    "Sonne).",
            "an" to "an belongs to *denken an*. *sich sehnen* pairs with *nach*.",
            "auf" to "auf belongs to *sich freuen auf*. The pair is *sich sehnen nach*.",
        ),
        caseRuleId = "prep-which-fixed",
        caseMiss = mapOf(
            "Akkusativ" to "nach always takes the dative, so *sich sehnen nach* is dative.",
        ),
    ),
    "träumen von" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "über" to "einer großen Reise is dative, and über takes the accusative here. Dreaming of " +
                    "something is *träumen von* (von der Reise).",
            "an" to "an belongs to *denken an*. *träumen* pairs with *von*.",
            "auf" to "auf belongs to *sich freuen auf*. The pair is *träumen von*.",
        ),
        caseRuleId = "prep-which-fixed",
        caseMiss = mapOf(
            "Akkusativ" to "von always takes the dative, so *träumen von* is dative.",
        ),
    ),
    "erzählen von" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "über" to "über takes the accusative, but ihrer Jugend is dative. *erzählen* pairs with *von* " +
                    "(von ihrer Jugend).",
            "an" to "an belongs to *denken an*. Telling about one's past is *erzählen von*.",
            "aus" to "aus belongs to *bestehen aus*. The pair is *erzählen von*.",
        ),
        caseRuleId = "prep-which-fixed",
        caseMiss = mapOf(
            "Akkusativ" to "von always takes the dative, so *erzählen von* is dative.",
        ),
    ),
    "abhängen von" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "an" to "an belongs to *liegen an*. Depending on something is *abhängen von* (von deiner " +
                    "Entscheidung).",
            "auf" to "auf belongs to *ankommen auf*. *abhängen* pairs with *von*.",
            "aus" to "aus belongs to *bestehen aus*. The pair is *abhängen von*.",
        ),
        caseRuleId = "prep-which-fixed",
        caseMiss = mapOf(
            "Akkusativ" to "von always takes the dative, so *abhängen von* is dative.",
        ),
    ),
    "berichten von" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "über" to "über takes the accusative, but der Unfallstelle is dative. Reporting from a place " +
                    "is *berichten von* (von der Unfallstelle).",
            "aus" to "*berichten aus* goes with a city or region (aus Berlin). From a spot such as die " +
                    "Unfallstelle, the pair is *berichten von*.",
            "bei" to "bei belongs to *helfen bei*. Reporting from a place is *berichten von*.",
        ),
        caseRuleId = "prep-which-fixed",
        caseMiss = mapOf(
            "Akkusativ" to "von always takes the dative, so *berichten von* is dative.",
        ),
    ),
    "warnen vor" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "von" to "von belongs to *träumen von*. Warning of something is *warnen vor* (vor starkem " +
                    "Sturm).",
            "gegen" to "gegen takes the accusative, but starkem Sturm is dative. *warnen* pairs with *vor*.",
            "für" to "für takes the accusative too, so it cannot fit starkem Sturm. The pair is *warnen " +
                    "vor*.",
        ),
        caseRuleId = "verb-prep-case-pair",
        caseMiss = mapOf(
            "Akkusativ" to "with warnen, vor takes the dative.",
        ),
    ),
    "schützen vor" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "von" to "von belongs to *träumen von*. Protecting against something is *schützen vor* (vor " +
                    "der Kälte).",
            "gegen" to "gegen takes the accusative, but der Kälte is dative. *schützen* pairs with *vor*.",
            "aus" to "aus belongs to *bestehen aus*. The pair is *schützen vor*.",
        ),
        caseRuleId = "verb-prep-case-pair",
        caseMiss = mapOf(
            "Akkusativ" to "with schützen, vor takes the dative.",
        ),
    ),
    "fliehen vor" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "von" to "von belongs to *träumen von*. Fleeing from something is *fliehen vor* (vor dem " +
                    "Feuer).",
            "aus" to "aus belongs to *bestehen aus*. *fliehen* pairs with *vor*.",
            "bei" to "bei belongs to *helfen bei*. The pair is *fliehen vor*.",
        ),
        caseRuleId = "verb-prep-case-pair",
        caseMiss = mapOf(
            "Akkusativ" to "with fliehen, vor takes the dative.",
        ),
    ),
    "leiden an" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-meaning",
        gapMiss = mapOf(
            "unter" to "*leiden unter* is mostly for conditions that weigh on you, like heat or noise. For " +
                    "a named illness the usual pair is *leiden an* (an einer Krankheit).",
            "von" to "von belongs to *träumen von*. Suffering from an illness is *leiden an*.",
            "bei" to "bei belongs to *helfen bei*. The illness pair is *leiden an*.",
        ),
        caseRuleId = "verb-prep-case-pair",
        caseMiss = mapOf(
            "Akkusativ" to "with leiden (suffering from an illness), an takes the dative.",
        ),
    ),
    "leiden unter" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-meaning",
        gapMiss = mapOf(
            "an" to "*leiden an* is for an illness you suffer from. Hitze is a condition around you, so " +
                    "*leiden unter* (unter der Hitze).",
            "von" to "von belongs to *träumen von*. Suffering under a condition is *leiden unter*.",
            "bei" to "bei belongs to *helfen bei*. The condition pair is *leiden unter*.",
        ),
        caseRuleId = "verb-prep-case-pair",
        caseMiss = mapOf(
            "Akkusativ" to "with leiden (suffering under a condition), unter takes the dative.",
        ),
    ),
    "zweifeln an" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "in" to "in belongs to *sich verlieben in*. Doubting something is *zweifeln an* (an seinen " +
                    "Absichten).",
            "auf" to "auf belongs to *hoffen auf*. *zweifeln* pairs with *an*.",
            "über" to "über takes the accusative, but seinen guten Absichten is dative. The pair is " +
                    "*zweifeln an*.",
        ),
        caseRuleId = "verb-prep-case-pair",
        caseMiss = mapOf(
            "Akkusativ" to "with zweifeln, an takes the dative.",
        ),
    ),
    "erkennen an" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "in" to "in belongs to *sich verlieben in*. Recognizing something by something is *erkennen " +
                    "an* (an dem Schnabel).",
            "auf" to "auf belongs to *achten auf*. *erkennen* pairs with *an*.",
            "von" to "von belongs to *abhängen von*. The pair is *erkennen an*.",
        ),
        caseRuleId = "verb-prep-case-pair",
        caseMiss = mapOf(
            "Akkusativ" to "with erkennen (recognizing by a feature), an takes the dative.",
        ),
    ),
    "liegen an" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "bei" to "bei belongs to *helfen bei*. The cause of something is *liegen an* (an schlechter " +
                    "Planung).",
            "in" to "in belongs to *sich verlieben in*. *liegen* pairs with *an*.",
            "auf" to "auf belongs to *ankommen auf*. The pair is *liegen an*.",
        ),
        caseRuleId = "verb-prep-case-pair",
        caseMiss = mapOf(
            "Akkusativ" to "with liegen (the cause of something), an takes the dative.",
        ),
    ),
    "sterben an" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "von" to "von belongs to *träumen von*. Dying of an illness is *sterben an* (an Infektionen).",
            "aus" to "aus belongs to *bestehen aus*. *sterben* pairs with *an*.",
            "durch" to "durch takes the accusative, but einfachen Infektionen is dative. The pair is " +
                    "*sterben an*.",
        ),
        caseRuleId = "verb-prep-case-pair",
        caseMiss = mapOf(
            "Akkusativ" to "with sterben (dying of an illness), an takes the dative.",
        ),
    ),
    "sich gewöhnen an" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "auf" to "auf belongs to *sich freuen auf*. Getting used to something is *sich gewöhnen an* " +
                    "(an das neue Leben).",
            "in" to "in belongs to *sich verlieben in*. *sich gewöhnen* pairs with *an*.",
            "zu" to "zu belongs to *einladen zu*. The pair is *sich gewöhnen an*.",
        ),
        caseRuleId = "verb-prep-case-pair",
        caseMiss = mapOf(
            "Dativ" to "with sich gewöhnen, an takes the accusative.",
        ),
    ),
    "verzichten auf" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "an" to "an belongs to *denken an*. Doing without something is *verzichten auf* (auf Zucker).",
            "von" to "von belongs to *träumen von*. *verzichten* pairs with *auf*.",
            "über" to "über belongs to *sprechen über*. The pair is *verzichten auf*.",
        ),
        caseRuleId = "verb-prep-case-pair",
        caseMiss = mapOf(
            "Dativ" to "with verzichten, auf takes the accusative.",
        ),
    ),
    "vertrauen auf" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "an" to "an belongs to *glauben an*. Trusting in something is *vertrauen auf* (auf deine " +
                    "Erfahrung).",
            "in" to "in belongs to *sich verlieben in*. *vertrauen* pairs with *auf*.",
            "zu" to "zu belongs to *gehören zu*. The pair is *vertrauen auf*.",
        ),
        caseRuleId = "verb-prep-case-pair",
        caseMiss = mapOf(
            "Dativ" to "with vertrauen, auf takes the accusative.",
        ),
    ),
    "reagieren auf" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "an" to "an belongs to *denken an*. Reacting to something is *reagieren auf* (auf die " +
                    "Beschwerden).",
            "für" to "für belongs to *sich interessieren für*. The pair is *reagieren auf*.",
            "über" to "über belongs to *sich beschweren über*. *reagieren* pairs with *auf*.",
        ),
        caseRuleId = "verb-prep-case-pair",
        caseMiss = mapOf(
            "Dativ" to "with reagieren, auf takes the accusative.",
        ),
    ),
    "antworten auf" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "an" to "an belongs to *denken an*. Answering something is *antworten auf* (auf meine " +
                    "E-Mails).",
            "für" to "für belongs to *danken für*. *antworten* pairs with *auf*.",
            "zu" to "zu belongs to *gratulieren zu*. The pair is *antworten auf*.",
        ),
        caseRuleId = "verb-prep-case-pair",
        caseMiss = mapOf(
            "Dativ" to "with antworten, auf takes the accusative.",
        ),
    ),
    "ankommen auf" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "an" to "an belongs to *teilnehmen an*. Depending on something is *ankommen auf* (auf " +
                    "regelmäßige Übung).",
            "in" to "in belongs to *sich verlieben in*. *ankommen* pairs with *auf*.",
            "zu" to "zu belongs to *einladen zu*. The pair is *ankommen auf*.",
        ),
        caseRuleId = "verb-prep-case-pair",
        caseMiss = mapOf(
            "Dativ" to "with ankommen (to depend on), auf takes the accusative.",
        ),
    ),
    "sich bedanken bei" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-meaning",
        gapMiss = mapOf(
            "an" to "an belongs to *denken an*. Thanking a person is *sich bedanken bei* (bei Ihnen).",
            "zu" to "zu belongs to *gratulieren zu*. The person you thank takes bei; the thing you thank " +
                    "for takes für.",
            "mit" to "mit belongs to *telefonieren mit*. *bedanken* pairs with bei for a person, für for " +
                    "a thing.",
        ),
        caseRuleId = "prep-which-fixed",
        caseMiss = mapOf(
            "Akkusativ" to "bei always takes the dative, so *sich bedanken bei* is dative.",
        ),
    ),
    "sich bedanken für" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-meaning",
        gapMiss = mapOf(
            "über" to "über belongs to *sprechen über* and *sich beschweren über*. Thanking for a thing is " +
                    "*sich bedanken für* (für die Einladung).",
            "an" to "an belongs to *denken an*. The thing you thank for takes für; the person takes bei.",
            "um" to "um belongs to *bitten um*. *bedanken* pairs with für for a thing, bei for a person.",
        ),
        caseRuleId = "prep-which-fixed",
        caseMiss = mapOf(
            "Dativ" to "für always takes the accusative, so *sich bedanken für* is accusative.",
        ),
    ),
    "sich konzentrieren auf" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "an" to "an belongs to *denken an*. Concentrating on something is *sich konzentrieren auf* " +
                    "(auf die Aufgabe).",
            "für" to "für belongs to *sich interessieren für*. The pair is *sich konzentrieren auf*.",
            "in" to "in belongs to *sich verlieben in*. *sich konzentrieren* pairs with *auf*.",
        ),
        caseRuleId = "verb-prep-case-pair",
        caseMiss = mapOf(
            "Dativ" to "with sich konzentrieren, auf takes the accusative.",
        ),
    ),
    "führen zu" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "an" to "an belongs to *liegen an*. Leading to something is *führen zu* (zu vielen " +
                    "Veränderungen).",
            "in" to "in belongs to *sich verlieben in*. *führen* pairs with *zu*.",
            "nach" to "nach belongs to *fragen nach*. The pair is *führen zu*.",
        ),
        caseRuleId = "prep-which-fixed",
        caseMiss = mapOf(
            "Akkusativ" to "zu always takes the dative, so *führen zu* is dative.",
        ),
    ),
    "protestieren gegen" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "für" to "für marks the side you support. Protesting against something is *protestieren " +
                    "gegen* (gegen die Steuern).",
            "auf" to "auf belongs to *warten auf*. *protestieren* pairs with *gegen*.",
            "wider" to "wider also means against, but it lives in fixed expressions. The everyday pair is " +
                    "*protestieren gegen*.",
        ),
        caseRuleId = "prep-which-fixed",
        caseMiss = mapOf(
            "Dativ" to "gegen always takes the accusative, so *protestieren gegen* is accusative.",
        ),
    ),
    "sich einigen auf" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-fixed",
        gapMiss = mapOf(
            "an" to "an belongs to *teilnehmen an*. Settling on one result is *sich einigen auf* (auf " +
                    "einen Kompromiss).",
            "über" to "*sich über etwas einigen* is agreeing on a topic in a discussion. Settling on one " +
                    "agreed result is *sich einigen auf* (auf einen fairen Kompromiss).",
            "für" to "für belongs to *danken für*. The pair is *sich einigen auf*.",
        ),
        caseRuleId = "verb-prep-case-pair",
        caseMiss = mapOf(
            "Dativ" to "with sich einigen (settling on one result), auf takes the accusative.",
        ),
    ),
    "bestehen aus" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-meaning",
        gapMiss = mapOf(
            "von" to "von belongs to *träumen von*. Saying what something is made of is *bestehen aus* " +
                    "(aus fünf Entwicklern).",
            "an" to "an belongs to *teilnehmen an*. The made-of pair is *bestehen aus*.",
            "in" to "*bestehen in* says what something consists in, as an idea (Das Problem besteht in " +
                    "…). The members a team is made of take *bestehen aus*.",
        ),
        caseRuleId = "prep-which-fixed",
        caseMiss = mapOf(
            "Akkusativ" to "aus always takes the dative, so *bestehen aus* is dative.",
        ),
    ),
    "bestehen auf" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-meaning",
        gapMiss = mapOf(
            "an" to "an belongs to *teilnehmen an*. Insisting on something is *bestehen auf* (auf seinem " +
                    "Recht).",
            "in" to "in belongs to *sich verlieben in*. *bestehen* pairs with auf here.",
            "zu" to "zu belongs to *einladen zu*. *bestehen* has two pairs: auf for insisting, aus for " +
                    "what something is made of.",
        ),
        caseRuleId = "verb-prep-case-pair",
        caseMiss = mapOf(
            "Akkusativ" to "with bestehen (insisting), auf takes the dative. The same auf is accusative with " +
                    "other verbs (warten auf).",
        ),
    ),
    "sich sorgen um" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-meaning",
        gapMiss = mapOf(
            "für" to "*sorgen für* is providing something. Worrying about something is *sich sorgen um* " +
                    "(um die Zukunft).",
            "über" to "über belongs to *sich ärgern über*. The worry pair is *sich sorgen um*.",
            "an" to "an belongs to *denken an*. *sorgen* has two pairs: *sich sorgen um* for worry, " +
                    "*sorgen für* for providing.",
        ),
        caseRuleId = "prep-which-fixed",
        caseMiss = mapOf(
            "Dativ" to "um always takes the accusative, so *sich sorgen um* is accusative.",
        ),
    ),
    "sorgen für" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-meaning",
        gapMiss = mapOf(
            "um" to "*sich sorgen um* is worrying about something. Providing something is *sorgen für* " +
                    "(für beste Stimmung).",
            "auf" to "auf belongs to *warten auf*. *sorgen* pairs with für here.",
            "an" to "an belongs to *denken an*. *sorgen* has two pairs: für for providing, *sich um* for " +
                    "worrying.",
        ),
        caseRuleId = "prep-which-fixed",
        caseMiss = mapOf(
            "Dativ" to "für always takes the accusative, so *sorgen für* is accusative.",
        ),
    ),
    "kämpfen für" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-meaning",
        gapMiss = mapOf(
            "um" to "*kämpfen um* is fighting to win something. Standing up for a value like freedom is " +
                    "*kämpfen für* (für ihre Freiheit).",
            "gegen" to "gegen means fighting against something, the opposite direction. Here they fight on " +
                    "freedom's side, so *kämpfen für*.",
            "auf" to "auf belongs to *warten auf*. *kämpfen* pairs with für or gegen.",
        ),
        caseRuleId = "prep-which-fixed",
        caseMiss = mapOf(
            "Dativ" to "für always takes the accusative, so *kämpfen für* is accusative.",
        ),
    ),
    "kämpfen gegen" to VerbPrepRuleContent(
        gapRuleId = "verb-prep-gap-meaning",
        gapMiss = mapOf(
            "für" to "*kämpfen für* is fighting on something's side. Die Krankheit is the opponent here, " +
                    "so *kämpfen gegen* (gegen die Krankheit).",
            "wider" to "wider also means against, but it lives in fixed expressions. The everyday pair is " +
                    "*kämpfen gegen*.",
            "um" to "*kämpfen um* is fighting to win something. Fighting an opponent is *kämpfen gegen*.",
        ),
        caseRuleId = "prep-which-fixed",
        caseMiss = mapOf(
            "Dativ" to "gegen always takes the accusative, so *kämpfen gegen* is accusative.",
        ),
    ),
)
