package app.olauncher.data.store

/**
 * Help-card rules of the live (words-driven) drill types (#699), transcribed from
 * docs/design/drill-content/prepositions.md, case-endings.md, verb-prepositions.md,
 * adjective-endings.md, articles.md and plurals.md. They share the registry with the
 * gap-topic rules in [DrillHelpContent]; only `prep-fixed-dat` is authored there,
 * because a gap-topic item (the passive's von + dative) also points at it, and it
 * is not repeated here.
 *
 * Same leak rules as the gap topics (docs/design/drill-help-card.md §3): the
 * pre-answer prose — rule, first-exposure rule, cue and title — names no item's
 * answer. For the which-case items the case name is the answer, so none of their
 * fields names a case. The adjective-endings items answer with the ending (their
 * item hints name the case too), so their titles may name the declension and case —
 * which keeps them unique per rule. The forms tables show the whole contrast set
 * with equal weight. All adjective-endings, articles and plurals rules omit the
 * first-exposure rule, as their content files author it.
 */
internal object DrillHelpContentLive {

    val rules: List<DrillHelpRule> = listOf(
        // -----------------------------------------------------------------
        // prepositions.md
        // -----------------------------------------------------------------
        DrillHelpRule(
            id = "prep-which-fixed",
            title = "Prepositions with one case",
            rule = "This preposition is one of those that always take the same case, whatever " +
                "the sentence says. Learn which one it takes.",
            table = DrillHelpTable(
                headers = listOf("", "m", "f", "n", "pl"),
                rows = listOf(
                    DrillHelpRow("Akk", listOf("den", "die", "das", "die")),
                    DrillHelpRow("Dat", listOf("dem", "der", "dem", "den")),
                    DrillHelpRow("Gen", listOf("des", "der", "des", "der")),
                ),
            ),
            example = "Ich fahre mit **dem** Bus.",
            cue = "Fixed case. Recall which one this preposition takes.",
        ),
        DrillHelpRule(
            id = "prep-fixed-acc",
            title = "Prepositions with the accusative",
            rule = "This preposition is one of those that always take the same case, whatever " +
                "the sentence says. Learn which one it takes.",
            firstExposureRule = "durch, für, gegen, ohne, um and entlang always take the accusative.",
            table = DrillHelpTable(
                headers = listOf("", "m", "f", "n", "pl"),
                rows = listOf(
                    DrillHelpRow("Akk", listOf("den", "die", "das", "die")),
                    DrillHelpRow("Dat", listOf("dem", "der", "dem", "den")),
                    DrillHelpRow("Gen", listOf("des", "der", "des", "der")),
                ),
            ),
            example = "Ich gehe durch **den** Park.",
            cue = "This preposition keeps one case. Recall it, then the noun's gender.",
        ),
        DrillHelpRule(
            id = "prep-fixed-gen",
            title = "Prepositions with the genitive",
            rule = "This preposition is one of those that always take the same case, whatever " +
                "the sentence says. Learn which one it takes.",
            firstExposureRule = "wegen, trotz, während, (an)statt, innerhalb, außerhalb, " +
                "anlässlich, unweit, jenseits and infolge take the genitive.",
            table = DrillHelpTable(
                headers = listOf("", "m", "f", "n", "pl"),
                rows = listOf(
                    DrillHelpRow("Akk", listOf("den", "die", "das", "die")),
                    DrillHelpRow("Dat", listOf("dem", "der", "dem", "den")),
                    DrillHelpRow("Gen", listOf("des", "der", "des", "der")),
                ),
            ),
            example = "Wegen **des** Wetters bleiben wir hier.",
            cue = "This preposition keeps one case. Recall it, then the noun's gender.",
        ),
        DrillHelpRule(
            id = "prep-two-way",
            title = "Two-way prepositions",
            rule = "This is a two-way preposition. Wohin? means movement to a place; Wo? means " +
                "a fixed position. The verb tells you which one the sentence describes.",
            firstExposureRule = "an, auf, hinter, in, neben, über, unter, vor and zwischen take " +
                "the accusative after Wohin? and the dative after Wo?.",
            table = DrillHelpTable(
                headers = listOf("", "m", "f", "n", "pl"),
                rows = listOf(
                    DrillHelpRow("Wohin? → Akk", listOf("den", "die", "das", "die")),
                    DrillHelpRow("Wo? → Dat", listOf("dem", "der", "dem", "den")),
                ),
            ),
            example = "Ich lege das Buch auf **den** Tisch.",
            cue = "Ask Wohin? (movement) or Wo? (fixed position) — the verb decides.",
        ),

        // -----------------------------------------------------------------
        // case-endings.md
        // -----------------------------------------------------------------
        DrillHelpRule(
            id = "case-subject-nom",
            title = "Nouns as the subject",
            rule = "The article shows the noun's job: the doer (Wer?), the thing acted on " +
                "(Wen?) or the receiver (Wem?). Decide this noun's job, then its gender.",
            table = DrillHelpTable(
                headers = listOf("", "Wer? → Nom", "Wen? → Akk", "Wem? → Dat", "Wessen? → Gen"),
                rows = listOf(
                    DrillHelpRow("m · n", listOf("der · das", "den · das", "dem", "des")),
                    DrillHelpRow("f", listOf("die", "die", "der", "der")),
                    DrillHelpRow("pl", listOf("die", "die", "den", "der")),
                ),
            ),
            example = "Hier schläft de**r** Hund.",
            cue = "Ask Wer?, Wen? or Wem? for this noun, then recall its gender.",
        ),
        DrillHelpRule(
            id = "case-sein-nom",
            title = "The noun after sein",
            rule = "The article shows the noun's job: the doer (Wer?), the thing acted on " +
                "(Wen?) or the receiver (Wem?). Decide this noun's job, then its gender.",
            table = DrillHelpTable(
                headers = listOf("", "Wer? → Nom", "Wen? → Akk", "Wem? → Dat", "Wessen? → Gen"),
                rows = listOf(
                    DrillHelpRow("m · n", listOf("der · das", "den · das", "dem", "des")),
                    DrillHelpRow("f", listOf("die", "die", "der", "der")),
                    DrillHelpRow("pl", listOf("die", "die", "den", "der")),
                ),
            ),
            example = "Das ist di**e** beste Lösung.",
            cue = "Ask what the noun after sein refers to, then recall its gender.",
        ),
        DrillHelpRule(
            id = "case-verb-acc",
            title = "The direct object",
            rule = "The article shows the noun's job: the doer (Wer?), the thing acted on " +
                "(Wen?) or the receiver (Wem?). Decide this noun's job, then its gender.",
            table = DrillHelpTable(
                headers = listOf("", "Wer? → Nom", "Wen? → Akk", "Wem? → Dat", "Wessen? → Gen"),
                rows = listOf(
                    DrillHelpRow("m · n", listOf("der · das", "den · das", "dem", "des")),
                    DrillHelpRow("f", listOf("die", "die", "der", "der")),
                    DrillHelpRow("pl", listOf("die", "die", "den", "der")),
                ),
            ),
            example = "Wir besuchen de**n** Großvater.",
            cue = "Ask Wer?, Wen? or Wem? for this noun, then recall its gender.",
        ),
        DrillHelpRule(
            id = "case-verb-dat",
            title = "The receiver after certain verbs",
            rule = "The article shows the noun's job: the doer (Wer?), the thing acted on " +
                "(Wen?) or the receiver (Wem?). Decide this noun's job, then its gender.",
            table = DrillHelpTable(
                headers = listOf("", "Wer? → Nom", "Wen? → Akk", "Wem? → Dat", "Wessen? → Gen"),
                rows = listOf(
                    DrillHelpRow("m · n", listOf("der · das", "den · das", "dem", "des")),
                    DrillHelpRow("f", listOf("die", "die", "der", "der")),
                    DrillHelpRow("pl", listOf("die", "die", "den", "der")),
                ),
            ),
            example = "Ich helfe de**m** Mann gerne.",
            cue = "Ask Wer?, Wen? or Wem? for this noun, then recall its gender.",
        ),

        // -----------------------------------------------------------------
        // verb-prepositions.md
        // -----------------------------------------------------------------
        DrillHelpRule(
            id = "verb-prep-gap-fixed",
            title = "Verbs with a fixed preposition",
            rule = "Many German verbs take one fixed preposition. It belongs to the verb, not " +
                "to the meaning of the sentence, so learn each verb with its preposition as one unit.",
            table = DrillHelpTable(
                headers = listOf("verb", "rechnen", "sich streiten", "sich fürchten", "duften"),
                rows = listOf(
                    DrillHelpRow("preposition", listOf("mit", "um", "vor", "nach")),
                ),
            ),
            example = "Am Sonntag streiten die Kinder **um** die Fernbedienung.",
            cue = "Verb and preposition are one unit — recall the whole pair.",
        ),
        DrillHelpRule(
            id = "verb-prep-gap-meaning",
            title = "Verbs with two pairs",
            rule = "Some verbs have two fixed pairs, each pair with its own meaning. Read what " +
                "the sentence means, then recall the pair that matches it. Learn both pairs together.",
            table = DrillHelpTable(
                headers = listOf("", "die Sache (the thing)", "die Person (the person)"),
                rows = listOf(
                    DrillHelpRow("sich entschuldigen …", listOf("für die Verspätung", "bei dem Lehrer")),
                ),
            ),
            example = "Sie entschuldigt sich **bei** dem Lehrer **für** die Verspätung.",
            cue = "Two pairs, two meanings — pick the one this sentence means.",
        ),
        DrillHelpRule(
            id = "verb-prep-case-pair",
            title = "When the verb picks the case",
            rule = "Some prepositions can take either case on their own. In a verb pair, the " +
                "verb fixes which one follows. Learn verb, preposition and case together as one unit.",
            table = DrillHelpTable(
                headers = listOf("", "Akkusativ", "Dativ"),
                rows = listOf(
                    DrillHelpRow("hinweisen auf", listOf("auf den Fehler", "—")),
                    DrillHelpRow("beruhen auf", listOf("—", "auf alten Daten")),
                ),
            ),
            example = "Der Bericht beruht **auf** alte**n** Daten.",
            cue = "Same preposition, different verb — the verb decides. Recall the whole trio.",
        ),

        // -----------------------------------------------------------------
        // adjective-endings.md
        // -----------------------------------------------------------------
        DrillHelpRule(
            id = "adj-weak-nom",
            title = "The weak declension: nominative",
            rule = "The definite article already shows case and gender, so the adjective adds " +
                "only -e or -en, never -er, -es or -em. Find the article in the table.",
            table = DrillHelpTable(
                headers = listOf("", "m", "f", "n", "pl"),
                rows = listOf(
                    DrillHelpRow("Nom", listOf("der …-e", "die …-e", "das …-e", "die …-en")),
                    DrillHelpRow("Akk", listOf("den …-en", "die …-e", "das …-e", "die …-en")),
                    DrillHelpRow("Dat · Gen", listOf("dem · des …-en", "der …-en", "dem · des …-en", "den · der …-en")),
                ),
            ),
            example = "Die weiß**e** Katze sitzt auf dem Dach.",
            cue = "Find the article in the table: which row and column is it in?",
        ),
        DrillHelpRule(
            id = "adj-weak-akk",
            title = "The weak declension: accusative",
            rule = "The definite article already shows case and gender, so the adjective adds " +
                "only -e or -en, never -er, -es or -em. Find the article in the table.",
            table = DrillHelpTable(
                headers = listOf("", "m", "f", "n", "pl"),
                rows = listOf(
                    DrillHelpRow("Nom", listOf("der …-e", "die …-e", "das …-e", "die …-en")),
                    DrillHelpRow("Akk", listOf("den …-en", "die …-e", "das …-e", "die …-en")),
                    DrillHelpRow("Dat · Gen", listOf("dem · des …-en", "der …-en", "dem · des …-en", "den · der …-en")),
                ),
            ),
            example = "Ich kenne den nett**en** Lehrer gut.",
            cue = "Find the article in the table: which row and column is it in?",
        ),
        DrillHelpRule(
            id = "adj-weak-dat",
            title = "The weak declension: dative",
            rule = "The definite article already shows case and gender, so the adjective adds " +
                "only -e or -en, never -er, -es or -em. Find the article in the table.",
            table = DrillHelpTable(
                headers = listOf("", "m", "f", "n", "pl"),
                rows = listOf(
                    DrillHelpRow("Nom", listOf("der …-e", "die …-e", "das …-e", "die …-en")),
                    DrillHelpRow("Akk", listOf("den …-en", "die …-e", "das …-e", "die …-en")),
                    DrillHelpRow("Dat · Gen", listOf("dem · des …-en", "der …-en", "dem · des …-en", "den · der …-en")),
                ),
            ),
            example = "Ich helfe dem groß**en** Bruder beim Umzug.",
            cue = "Find the article in the table: which row and column is it in?",
        ),
        DrillHelpRule(
            id = "adj-weak-gen",
            title = "The weak declension: genitive",
            rule = "The definite article already shows case and gender, so the adjective adds " +
                "only -e or -en, never -er, -es or -em. Find the article in the table.",
            table = DrillHelpTable(
                headers = listOf("", "m", "f", "n", "pl"),
                rows = listOf(
                    DrillHelpRow("Nom", listOf("der …-e", "die …-e", "das …-e", "die …-en")),
                    DrillHelpRow("Akk", listOf("den …-en", "die …-e", "das …-e", "die …-en")),
                    DrillHelpRow("Dat · Gen", listOf("dem · des …-en", "der …-en", "dem · des …-en", "den · der …-en")),
                ),
            ),
            example = "Das ist das Fahrrad des alt**en** Vaters.",
            cue = "Find the article in the table: which row and column is it in?",
        ),
        DrillHelpRule(
            id = "adj-mixed-nom",
            title = "The mixed declension: nominative",
            rule = "ein, eine and keine show the case only partly. Where the article has an " +
                "ending, the adjective adds -e or -en; where it has none, the adjective carries " +
                "the signal.",
            table = DrillHelpTable(
                headers = listOf("", "m", "f", "n", "pl"),
                rows = listOf(
                    DrillHelpRow("Nom", listOf("ein …-er", "eine …-e", "ein …-es", "keine …-en")),
                    DrillHelpRow("Akk", listOf("einen …-en", "eine …-e", "ein …-es", "keine …-en")),
                    DrillHelpRow("Dat · Gen", listOf("einem · eines …-en", "einer …-en", "einem · eines …-en", "keinen · keiner …-en")),
                ),
            ),
            example = "Dort läuft ein treu**er** Hund über die Wiese.",
            cue = "Does the article have an ending of its own? If not, the adjective carries the signal.",
        ),
        DrillHelpRule(
            id = "adj-mixed-akk",
            title = "The mixed declension: accusative",
            rule = "ein, eine and keine show the case only partly. Where the article has an " +
                "ending, the adjective adds -e or -en; where it has none, the adjective carries " +
                "the signal.",
            table = DrillHelpTable(
                headers = listOf("", "m", "f", "n", "pl"),
                rows = listOf(
                    DrillHelpRow("Nom", listOf("ein …-er", "eine …-e", "ein …-es", "keine …-en")),
                    DrillHelpRow("Akk", listOf("einen …-en", "eine …-e", "ein …-es", "keine …-en")),
                    DrillHelpRow("Dat · Gen", listOf("einem · eines …-en", "einer …-en", "einem · eines …-en", "keinen · keiner …-en")),
                ),
            ),
            example = "Er trinkt einen heiß**en** Tee mit Zitrone.",
            cue = "Does the article have an ending of its own? If not, the adjective carries the signal.",
        ),
        DrillHelpRule(
            id = "adj-mixed-dat",
            title = "The mixed declension: dative",
            rule = "ein, eine and keine show the case only partly. Where the article has an " +
                "ending, the adjective adds -e or -en; where it has none, the adjective carries " +
                "the signal.",
            table = DrillHelpTable(
                headers = listOf("", "m", "f", "n", "pl"),
                rows = listOf(
                    DrillHelpRow("Nom", listOf("ein …-er", "eine …-e", "ein …-es", "keine …-en")),
                    DrillHelpRow("Akk", listOf("einen …-en", "eine …-e", "ein …-es", "keine …-en")),
                    DrillHelpRow("Dat · Gen", listOf("einem · eines …-en", "einer …-en", "einem · eines …-en", "keinen · keiner …-en")),
                ),
            ),
            example = "Sie spricht mit einem gut**en** Freund über alles.",
            cue = "Does the article have an ending of its own? If not, the adjective carries the signal.",
        ),
        DrillHelpRule(
            id = "adj-mixed-gen",
            title = "The mixed declension: genitive",
            rule = "ein, eine and keine show the case only partly. Where the article has an " +
                "ending, the adjective adds -e or -en; where it has none, the adjective carries " +
                "the signal.",
            table = DrillHelpTable(
                headers = listOf("", "m", "f", "n", "pl"),
                rows = listOf(
                    DrillHelpRow("Nom", listOf("ein …-er", "eine …-e", "ein …-es", "keine …-en")),
                    DrillHelpRow("Akk", listOf("einen …-en", "eine …-e", "ein …-es", "keine …-en")),
                    DrillHelpRow("Dat · Gen", listOf("einem · eines …-en", "einer …-en", "einem · eines …-en", "keinen · keiner …-en")),
                ),
            ),
            example = "Das ist das Buch eines klug**en** Lehrers.",
            cue = "Does the article have an ending of its own? If not, the adjective carries the signal.",
        ),

        // -----------------------------------------------------------------
        // articles.md (all 72 built-in article items; one rule per noun)
        // -----------------------------------------------------------------
        DrillHelpRule(
            id = "art-gender-suffix-ung",
            title = "Nouns ending in -ung",
            rule = "Every German noun has one fixed gender, and the article must match it. " +
                "Learn each noun together with its article.",
            table = DrillHelpTable(
                headers = listOf("", "m", "f", "n", "pl"),
                rows = listOf(DrillHelpRow("Nom", listOf("der", "die", "das", "die"))),
            ),
            example = "**Die** Wohnung ist hell.",
            cue = "Look at the -ung ending.",
        ),
        DrillHelpRule(
            id = "art-gender-suffix-heit",
            title = "Nouns ending in -heit",
            rule = "Every German noun has one fixed gender, and the article must match it. " +
                "Learn each noun together with its article.",
            table = DrillHelpTable(
                headers = listOf("", "m", "f", "n", "pl"),
                rows = listOf(DrillHelpRow("Nom", listOf("der", "die", "das", "die"))),
            ),
            example = "**Die** Freiheit ist wichtig.",
            cue = "Look at the -heit ending.",
        ),
        DrillHelpRule(
            id = "art-gender-suffix-keit",
            title = "Nouns ending in -keit",
            rule = "Every German noun has one fixed gender, and the article must match it. " +
                "Learn each noun together with its article.",
            table = DrillHelpTable(
                headers = listOf("", "m", "f", "n", "pl"),
                rows = listOf(DrillHelpRow("Nom", listOf("der", "die", "das", "die"))),
            ),
            example = "**Die** Möglichkeit ist da.",
            cue = "Look at the -keit ending.",
        ),
        DrillHelpRule(
            id = "art-gender-suffix-schaft",
            title = "Nouns ending in -schaft",
            rule = "Every German noun has one fixed gender, and the article must match it. " +
                "Learn each noun together with its article.",
            table = DrillHelpTable(
                headers = listOf("", "m", "f", "n", "pl"),
                rows = listOf(DrillHelpRow("Nom", listOf("der", "die", "das", "die"))),
            ),
            example = "**Die** Landschaft ist schön.",
            cue = "Look at the -schaft ending.",
        ),
        DrillHelpRule(
            id = "art-gender-suffix-ion",
            title = "Nouns ending in -ion",
            rule = "Every German noun has one fixed gender, and the article must match it. " +
                "Learn each noun together with its article.",
            table = DrillHelpTable(
                headers = listOf("", "m", "f", "n", "pl"),
                rows = listOf(DrillHelpRow("Nom", listOf("der", "die", "das", "die"))),
            ),
            example = "**Die** Situation ist schwierig.",
            cue = "Look at the -ion ending.",
        ),
        DrillHelpRule(
            id = "art-gender-suffix-taet",
            title = "Nouns ending in -tät",
            rule = "Every German noun has one fixed gender, and the article must match it. " +
                "Learn each noun together with its article.",
            table = DrillHelpTable(
                headers = listOf("", "m", "f", "n", "pl"),
                rows = listOf(DrillHelpRow("Nom", listOf("der", "die", "das", "die"))),
            ),
            example = "**Die** Universität ist alt.",
            cue = "Look at the -tät ending.",
        ),
        DrillHelpRule(
            id = "art-gender-suffix-ei",
            title = "Nouns ending in -ei",
            rule = "Every German noun has one fixed gender, and the article must match it. " +
                "Learn each noun together with its article.",
            table = DrillHelpTable(
                headers = listOf("", "m", "f", "n", "pl"),
                rows = listOf(DrillHelpRow("Nom", listOf("der", "die", "das", "die"))),
            ),
            example = "**Die** Bäckerei ist offen.",
            cue = "Look at the -ei ending.",
        ),
        DrillHelpRule(
            id = "art-gender-suffix-e",
            title = "Nouns ending in -e",
            rule = "Every German noun has one fixed gender, and the article must match it. " +
                "Learn each noun together with its article.",
            table = DrillHelpTable(
                headers = listOf("", "m", "f", "n", "pl"),
                rows = listOf(DrillHelpRow("Nom", listOf("der", "die", "das", "die"))),
            ),
            example = "**Die** Sonne ist warm.",
            cue = "Look at the -e ending.",
        ),
        DrillHelpRule(
            id = "art-gender-suffix-ismus",
            title = "Nouns ending in -ismus",
            rule = "Every German noun has one fixed gender, and the article must match it. " +
                "Learn each noun together with its article.",
            table = DrillHelpTable(
                headers = listOf("", "m", "f", "n", "pl"),
                rows = listOf(DrillHelpRow("Nom", listOf("der", "die", "das", "die"))),
            ),
            example = "**Der** Optimismus hilft.",
            cue = "Look at the -ismus ending.",
        ),
        DrillHelpRule(
            id = "art-gender-suffix-ling",
            title = "Nouns ending in -ling",
            rule = "Every German noun has one fixed gender, and the article must match it. " +
                "Learn each noun together with its article.",
            table = DrillHelpTable(
                headers = listOf("", "m", "f", "n", "pl"),
                rows = listOf(DrillHelpRow("Nom", listOf("der", "die", "das", "die"))),
            ),
            example = "**Der** Frühling beginnt.",
            cue = "Look at the -ling ending.",
        ),
        DrillHelpRule(
            id = "art-gender-suffix-ment",
            title = "Nouns ending in -ment",
            rule = "Every German noun has one fixed gender, and the article must match it. " +
                "Learn each noun together with its article.",
            table = DrillHelpTable(
                headers = listOf("", "m", "f", "n", "pl"),
                rows = listOf(DrillHelpRow("Nom", listOf("der", "die", "das", "die"))),
            ),
            example = "**Das** Dokument ist lang.",
            cue = "Look at the -ment ending.",
        ),
        DrillHelpRule(
            id = "art-gender-suffix-chen",
            title = "Nouns ending in -chen",
            rule = "Every German noun has one fixed gender, and the article must match it. " +
                "Learn each noun together with its article.",
            table = DrillHelpTable(
                headers = listOf("", "m", "f", "n", "pl"),
                rows = listOf(DrillHelpRow("Nom", listOf("der", "die", "das", "die"))),
            ),
            example = "**Das** Mädchen liest.",
            cue = "Look at the -chen ending.",
        ),
        DrillHelpRule(
            id = "art-gender-suffix-lein",
            title = "Nouns ending in -lein",
            rule = "Every German noun has one fixed gender, and the article must match it. " +
                "Learn each noun together with its article.",
            table = DrillHelpTable(
                headers = listOf("", "m", "f", "n", "pl"),
                rows = listOf(DrillHelpRow("Nom", listOf("der", "die", "das", "die"))),
            ),
            example = "**Das** Fräulein wartet.",
            cue = "Look at the -lein ending.",
        ),
        DrillHelpRule(
            id = "art-gender-suffix-um",
            title = "Nouns ending in -um",
            rule = "Every German noun has one fixed gender, and the article must match it. " +
                "Learn each noun together with its article.",
            table = DrillHelpTable(
                headers = listOf("", "m", "f", "n", "pl"),
                rows = listOf(DrillHelpRow("Nom", listOf("der", "die", "das", "die"))),
            ),
            example = "**Das** Museum ist neu.",
            cue = "Look at the -um ending.",
        ),
        DrillHelpRule(
            id = "art-gender-suffix-exception",
            title = "Endings that break the gender rule",
            rule = "Every German noun has one fixed gender, and the article must match it. " +
                "Learn each noun together with its article.",
            table = DrillHelpTable(
                headers = listOf("", "m", "f", "n", "pl"),
                rows = listOf(DrillHelpRow("Nom", listOf("der", "die", "das", "die"))),
            ),
            example = "**Der** Baum ist alt.",
            cue = "Learn the article together with the noun.",
        ),
        DrillHelpRule(
            id = "art-gender-natural",
            title = "People nouns and their article",
            rule = "Every German noun has one fixed gender, and the article must match it. " +
                "Learn each noun together with its article.",
            table = DrillHelpTable(
                headers = listOf("", "m", "f", "n", "pl"),
                rows = listOf(DrillHelpRow("Nom", listOf("der", "die", "das", "die"))),
            ),
            example = "**Der** Vater ist hier.",
            cue = "Think about the person the noun names.",
        ),
        DrillHelpRule(
            id = "art-gender-fixed",
            title = "Nouns with no gender pattern",
            rule = "Every German noun has one fixed gender, and the article must match it. " +
                "Learn each noun together with its article.",
            table = DrillHelpTable(
                headers = listOf("", "m", "f", "n", "pl"),
                rows = listOf(DrillHelpRow("Nom", listOf("der", "die", "das", "die"))),
            ),
            example = "**Das** Haus ist groß.",
            cue = "Learn the article together with the noun.",
        ),

        // -----------------------------------------------------------------
        // plurals.md
        // -----------------------------------------------------------------
        DrillHelpRule(
            id = "plural-e",
            title = "One noun, one plural (pattern 1)",
            rule = "Each noun has one fixed plural: an ending (-e, -er, -(e)n, -s or none), " +
                "sometimes with a vowel change (¨). Learn the plural with the noun.",
            table = DrillHelpTable(
                headers = listOf("", "-e", "-er", "-(e)n"),
                rows = listOf(
                    DrillHelpRow("vowel kept", listOf("der Weg → die Wege", "das Nest → die Nester", "die Blume → die Blumen · die Übung → die Übungen")),
                    DrillHelpRow("vowel ¨", listOf("der Fuß → die Füße", "das Lamm → die Lämmer", "—")),
                    DrillHelpRow("other", listOf("-s: das Taxi → die Taxis", "no ending: das Messer → die Messer", "¨ only: der Apfel → die Äpfel")),
                ),
            ),
            example = "Es gibt viele Weg**e** nach Rom.",
            cue = "Recall this noun's plural pattern: the ending, and any vowel change.",
        ),
        DrillHelpRule(
            id = "plural-umlaut-e",
            title = "One noun, one plural (pattern 2)",
            rule = "Each noun has one fixed plural: an ending (-e, -er, -(e)n, -s or none), " +
                "sometimes with a vowel change (¨). Learn the plural with the noun.",
            table = DrillHelpTable(
                headers = listOf("", "-e", "-er", "-(e)n"),
                rows = listOf(
                    DrillHelpRow("vowel kept", listOf("der Weg → die Wege", "das Nest → die Nester", "die Blume → die Blumen · die Übung → die Übungen")),
                    DrillHelpRow("vowel ¨", listOf("der Fuß → die Füße", "das Lamm → die Lämmer", "—")),
                    DrillHelpRow("other", listOf("-s: das Taxi → die Taxis", "no ending: das Messer → die Messer", "¨ only: der Apfel → die Äpfel")),
                ),
            ),
            example = "Meine F**ü**ß**e** sind müde.",
            cue = "Recall this noun's plural pattern: the ending, and any vowel change.",
        ),
        DrillHelpRule(
            id = "plural-er",
            title = "One noun, one plural (pattern 3)",
            rule = "Each noun has one fixed plural: an ending (-e, -er, -(e)n, -s or none), " +
                "sometimes with a vowel change (¨). Learn the plural with the noun.",
            table = DrillHelpTable(
                headers = listOf("", "-e", "-er", "-(e)n"),
                rows = listOf(
                    DrillHelpRow("vowel kept", listOf("der Weg → die Wege", "das Nest → die Nester", "die Blume → die Blumen · die Übung → die Übungen")),
                    DrillHelpRow("vowel ¨", listOf("der Fuß → die Füße", "das Lamm → die Lämmer", "—")),
                    DrillHelpRow("other", listOf("-s: das Taxi → die Taxis", "no ending: das Messer → die Messer", "¨ only: der Apfel → die Äpfel")),
                ),
            ),
            example = "Die Vögel bauen neue Nest**er**.",
            cue = "Recall this noun's plural pattern: the ending, and any vowel change.",
        ),
        DrillHelpRule(
            id = "plural-umlaut-er",
            title = "One noun, one plural (pattern 4)",
            rule = "Each noun has one fixed plural: an ending (-e, -er, -(e)n, -s or none), " +
                "sometimes with a vowel change (¨). Learn the plural with the noun.",
            table = DrillHelpTable(
                headers = listOf("", "-e", "-er", "-(e)n"),
                rows = listOf(
                    DrillHelpRow("vowel kept", listOf("der Weg → die Wege", "das Nest → die Nester", "die Blume → die Blumen · die Übung → die Übungen")),
                    DrillHelpRow("vowel ¨", listOf("der Fuß → die Füße", "das Lamm → die Lämmer", "—")),
                    DrillHelpRow("other", listOf("-s: das Taxi → die Taxis", "no ending: das Messer → die Messer", "¨ only: der Apfel → die Äpfel")),
                ),
            ),
            example = "Auf der Wiese springen viele L**ä**mm**er**.",
            cue = "Recall this noun's plural pattern: the ending, and any vowel change.",
        ),
        DrillHelpRule(
            id = "plural-n",
            title = "One noun, one plural (pattern 5)",
            rule = "Each noun has one fixed plural: an ending (-e, -er, -(e)n, -s or none), " +
                "sometimes with a vowel change (¨). Learn the plural with the noun.",
            table = DrillHelpTable(
                headers = listOf("", "-e", "-er", "-(e)n"),
                rows = listOf(
                    DrillHelpRow("vowel kept", listOf("der Weg → die Wege", "das Nest → die Nester", "die Blume → die Blumen · die Übung → die Übungen")),
                    DrillHelpRow("vowel ¨", listOf("der Fuß → die Füße", "das Lamm → die Lämmer", "—")),
                    DrillHelpRow("other", listOf("-s: das Taxi → die Taxis", "no ending: das Messer → die Messer", "¨ only: der Apfel → die Äpfel")),
                ),
            ),
            example = "Im Garten blühen viele Blume**n**.",
            cue = "Look at how the singular ends.",
        ),
        DrillHelpRule(
            id = "plural-en",
            title = "One noun, one plural (pattern 6)",
            rule = "Each noun has one fixed plural: an ending (-e, -er, -(e)n, -s or none), " +
                "sometimes with a vowel change (¨). Learn the plural with the noun.",
            table = DrillHelpTable(
                headers = listOf("", "-e", "-er", "-(e)n"),
                rows = listOf(
                    DrillHelpRow("vowel kept", listOf("der Weg → die Wege", "das Nest → die Nester", "die Blume → die Blumen · die Übung → die Übungen")),
                    DrillHelpRow("vowel ¨", listOf("der Fuß → die Füße", "das Lamm → die Lämmer", "—")),
                    DrillHelpRow("other", listOf("-s: das Taxi → die Taxis", "no ending: das Messer → die Messer", "¨ only: der Apfel → die Äpfel")),
                ),
            ),
            example = "Die Schüler schreiben heute viele Übung**en**.",
            cue = "Recall this noun's plural pattern: the ending, and any vowel change.",
        ),
        DrillHelpRule(
            id = "plural-s",
            title = "One noun, one plural (pattern 7)",
            rule = "Each noun has one fixed plural: an ending (-e, -er, -(e)n, -s or none), " +
                "sometimes with a vowel change (¨). Learn the plural with the noun.",
            table = DrillHelpTable(
                headers = listOf("", "-e", "-er", "-(e)n"),
                rows = listOf(
                    DrillHelpRow("vowel kept", listOf("der Weg → die Wege", "das Nest → die Nester", "die Blume → die Blumen · die Übung → die Übungen")),
                    DrillHelpRow("vowel ¨", listOf("der Fuß → die Füße", "das Lamm → die Lämmer", "—")),
                    DrillHelpRow("other", listOf("-s: das Taxi → die Taxis", "no ending: das Messer → die Messer", "¨ only: der Apfel → die Äpfel")),
                ),
            ),
            example = "Am Bahnhof warten viele Taxi**s**.",
            cue = "Think about where this word comes from.",
        ),
        DrillHelpRule(
            id = "plural-no-change",
            title = "One noun, one plural (pattern 8)",
            rule = "Each noun has one fixed plural: an ending (-e, -er, -(e)n, -s or none), " +
                "sometimes with a vowel change (¨). Learn the plural with the noun.",
            table = DrillHelpTable(
                headers = listOf("", "-e", "-er", "-(e)n"),
                rows = listOf(
                    DrillHelpRow("vowel kept", listOf("der Weg → die Wege", "das Nest → die Nester", "die Blume → die Blumen · die Übung → die Übungen")),
                    DrillHelpRow("vowel ¨", listOf("der Fuß → die Füße", "das Lamm → die Lämmer", "—")),
                    DrillHelpRow("other", listOf("-s: das Taxi → die Taxis", "no ending: das Messer → die Messer", "¨ only: der Apfel → die Äpfel")),
                ),
            ),
            example = "Im Büro stehen viele Computer.",
            cue = "Recall this noun's plural pattern: the ending, and any vowel change.",
        ),
    )
}
