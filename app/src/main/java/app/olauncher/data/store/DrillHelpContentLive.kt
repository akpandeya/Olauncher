package app.olauncher.data.store

/**
 * Help-card rules of the live (words-driven) drill types (#699), transcribed from
 * docs/design/drill-content/prepositions.md, case-endings.md and
 * verb-prepositions.md. They share the registry with the gap-topic rules in
 * [DrillHelpContent]; only `prep-fixed-dat` is authored there, because a gap-topic
 * item (the passive's von + dative) also points at it, and it is not repeated here.
 *
 * Same leak rules as the gap topics (docs/design/drill-help-card.md §3): the
 * pre-answer prose — rule, first-exposure rule, cue and title — names no item's
 * answer. For the which-case items the case name is the answer, so none of their
 * fields names a case; the forms tables show the whole contrast set with equal weight.
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
    )
}
