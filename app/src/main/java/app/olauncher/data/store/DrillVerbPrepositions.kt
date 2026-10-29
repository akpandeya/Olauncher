package app.olauncher.data.store

/**
 * Drill content for German verb plus preposition pairs and their governing cases.
 */
object DrillVerbPrepositions {

    internal data class VerbPrepositionPair(
        val verb: String,
        val preposition: String,
        val case: String,
        val sentence: String,
        val choices: List<String>,
    )

    private val CASE_CHOICES = listOf("Akkusativ", "Dativ")

    internal fun normalizeVerb(text: String): String =
        text.trim().lowercase().removePrefix("sich ").trim()

    internal val PAIRS: List<VerbPrepositionPair> = listOf(
        // 1. warten auf + Akkusativ
        VerbPrepositionPair(
            verb = "warten",
            preposition = "auf",
            case = "Akkusativ",
            sentence = "Ich warte ${DrillItem.GAP_MARKER} den Bus.",
            choices = listOf("auf", "an", "für", "über"),
        ),
        // 2. denken an + Akkusativ
        VerbPrepositionPair(
            verb = "denken",
            preposition = "an",
            case = "Akkusativ",
            sentence = "Ich denke oft ${DrillItem.GAP_MARKER} meine Familie.",
            choices = listOf("an", "auf", "in", "von"),
        ),
        // 3. sich interessieren für + Akkusativ
        VerbPrepositionPair(
            verb = "sich interessieren",
            preposition = "für",
            case = "Akkusativ",
            sentence = "Er interessiert sich ${DrillItem.GAP_MARKER} moderne Kunst.",
            choices = listOf("für", "an", "über", "auf"),
        ),
        // 4. teilnehmen an + Dativ
        VerbPrepositionPair(
            verb = "teilnehmen",
            preposition = "an",
            case = "Dativ",
            sentence = "Wir nehmen ${DrillItem.GAP_MARKER} dem Deutschkurs teil.",
            choices = listOf("an", "auf", "bei", "in"),
        ),
        // 5. Angst haben vor + Dativ
        VerbPrepositionPair(
            verb = "Angst haben",
            preposition = "vor",
            case = "Dativ",
            sentence = "Das Kind hat Angst ${DrillItem.GAP_MARKER} großen Hunden.",
            choices = listOf("vor", "an", "von", "bei"),
        ),
        // 6. sich freuen auf + Akkusativ
        VerbPrepositionPair(
            verb = "sich freuen",
            preposition = "auf",
            case = "Akkusativ",
            sentence = "Wir freuen uns schon ${DrillItem.GAP_MARKER} den Urlaub.",
            choices = listOf("auf", "über", "an", "für"),
        ),
        // 7. sich freuen über + Akkusativ
        VerbPrepositionPair(
            verb = "sich freuen",
            preposition = "über",
            case = "Akkusativ",
            sentence = "Sie freut sich sehr ${DrillItem.GAP_MARKER} das schöne Geschenk.",
            choices = listOf("über", "auf", "an", "für"),
        ),
        // 8. bitten um + Akkusativ
        VerbPrepositionPair(
            verb = "bitten",
            preposition = "um",
            case = "Akkusativ",
            sentence = "Darf ich dich ${DrillItem.GAP_MARKER} Hilfe bitten?",
            choices = listOf("um", "für", "nach", "zu"),
        ),
        // 9. danken für + Akkusativ
        VerbPrepositionPair(
            verb = "danken",
            preposition = "für",
            case = "Akkusativ",
            sentence = "Ich danke dir ${DrillItem.GAP_MARKER} deine Unterstützung.",
            choices = listOf("für", "an", "um", "zu"),
        ),
        // 10. hoffen auf + Akkusativ
        VerbPrepositionPair(
            verb = "hoffen",
            preposition = "auf",
            case = "Akkusativ",
            sentence = "Wir hoffen alle ${DrillItem.GAP_MARKER} besseres Wetter morgen.",
            choices = listOf("auf", "an", "in", "über"),
        ),
        // 11. sich erinnern an + Akkusativ
        VerbPrepositionPair(
            verb = "sich erinnern",
            preposition = "an",
            case = "Akkusativ",
            sentence = "Erinnert ihr euch noch ${DrillItem.GAP_MARKER} diesen Tag?",
            choices = listOf("an", "auf", "in", "von"),
        ),
        // 12. glauben an + Akkusativ
        VerbPrepositionPair(
            verb = "glauben",
            preposition = "an",
            case = "Akkusativ",
            sentence = "Sie glaubt fest ${DrillItem.GAP_MARKER} eine bessere Zukunft.",
            choices = listOf("an", "auf", "in", "zu"),
        ),
        // 13. achten auf + Akkusativ
        VerbPrepositionPair(
            verb = "achten",
            preposition = "auf",
            case = "Akkusativ",
            sentence = "Bitte achte gut ${DrillItem.GAP_MARKER} deine Gesundheit.",
            choices = listOf("auf", "an", "über", "in"),
        ),
        // 14. sich vorbereiten auf + Akkusativ
        VerbPrepositionPair(
            verb = "sich vorbereiten",
            preposition = "auf",
            case = "Akkusativ",
            sentence = "Die Schüler bereiten sich ${DrillItem.GAP_MARKER} die Prüfung vor.",
            choices = listOf("auf", "an", "für", "vor"),
        ),
        // 15. sprechen über + Akkusativ
        VerbPrepositionPair(
            verb = "sprechen",
            preposition = "über",
            case = "Akkusativ",
            sentence = "Wir haben lange ${DrillItem.GAP_MARKER} das neue Projekt gesprochen.",
            choices = listOf("über", "auf", "von", "an"),
        ),
        // 16. nachdenken über + Akkusativ
        VerbPrepositionPair(
            verb = "nachdenken",
            preposition = "über",
            case = "Akkusativ",
            sentence = "Ich muss gründlich ${DrillItem.GAP_MARKER} deinen Vorschlag nachdenken.",
            choices = listOf("über", "an", "auf", "um"),
        ),
        // 17. sich ärgern über + Akkusativ
        VerbPrepositionPair(
            verb = "sich ärgern",
            preposition = "über",
            case = "Akkusativ",
            sentence = "Er ärgert sich oft ${DrillItem.GAP_MARKER} den dichten Verkehr.",
            choices = listOf("über", "auf", "an", "für"),
        ),
        // 18. sich beschweren über + Akkusativ
        VerbPrepositionPair(
            verb = "sich beschweren",
            preposition = "über",
            case = "Akkusativ",
            sentence = "Der Gast beschwert sich ${DrillItem.GAP_MARKER} den kalten Kaffee.",
            choices = listOf("über", "an", "auf", "zu"),
        ),
        // 19. sich kümmern um + Akkusativ
        VerbPrepositionPair(
            verb = "sich kümmern",
            preposition = "um",
            case = "Akkusativ",
            sentence = "Wer kümmert sich am Wochenende ${DrillItem.GAP_MARKER} die Katze?",
            choices = listOf("um", "für", "an", "nach"),
        ),
        // 20. sich bewerben um + Akkusativ
        VerbPrepositionPair(
            verb = "sich bewerben",
            preposition = "um",
            case = "Akkusativ",
            sentence = "Sie bewirbt sich ${DrillItem.GAP_MARKER} die neue Stelle in Berlin.",
            choices = listOf("um", "auf", "für", "nach"),
        ),
        // 21. sich verlieben in + Akkusativ
        VerbPrepositionPair(
            verb = "sich verlieben",
            preposition = "in",
            case = "Akkusativ",
            sentence = "Er hat sich sofort ${DrillItem.GAP_MARKER} seine Kollegin verliebt.",
            choices = listOf("in", "an", "auf", "zu"),
        ),
        // 22. passen zu + Dativ
        VerbPrepositionPair(
            verb = "passen",
            preposition = "zu",
            case = "Dativ",
            sentence = "Diese Schuhe passen überhaupt nicht ${DrillItem.GAP_MARKER} der Hose.",
            choices = listOf("zu", "mit", "an", "für"),
        ),
        // 23. gehören zu + Dativ
        VerbPrepositionPair(
            verb = "gehören",
            preposition = "zu",
            case = "Dativ",
            sentence = "Dieses Buch gehört sicher ${DrillItem.GAP_MARKER} unserer Bibliothek.",
            choices = listOf("zu", "an", "mit", "bei"),
        ),
        // 24. gratulieren zu + Dativ
        VerbPrepositionPair(
            verb = "gratulieren",
            preposition = "zu",
            case = "Dativ",
            sentence = "Wir gratulieren dir ganz herzlich ${DrillItem.GAP_MARKER} deinem Geburtstag.",
            choices = listOf("zu", "an", "für", "bei"),
        ),
        // 25. einladen zu + Dativ
        VerbPrepositionPair(
            verb = "einladen",
            preposition = "zu",
            case = "Dativ",
            sentence = "Sie lädt ihre Freunde ${DrillItem.GAP_MARKER} der Party ein.",
            choices = listOf("zu", "in", "an", "auf"),
        ),
        // 26. helfen bei + Dativ
        VerbPrepositionPair(
            verb = "helfen",
            preposition = "bei",
            case = "Dativ",
            sentence = "Kannst du mir bitte ${DrillItem.GAP_MARKER} den Hausaufgaben helfen?",
            choices = listOf("bei", "mit", "in", "zu"),
        ),
        // 27. anfangen mit + Dativ
        VerbPrepositionPair(
            verb = "anfangen",
            preposition = "mit",
            case = "Dativ",
            sentence = "Wir fangen morgen früh ${DrillItem.GAP_MARKER} der Arbeit an.",
            choices = listOf("mit", "bei", "in", "zu"),
        ),
        // 28. aufhören mit + Dativ
        VerbPrepositionPair(
            verb = "aufhören",
            preposition = "mit",
            case = "Dativ",
            sentence = "Er möchte endlich ${DrillItem.GAP_MARKER} dem Rauchen aufhören.",
            choices = listOf("mit", "von", "bei", "zu"),
        ),
        // 29. sprechen mit + Dativ
        VerbPrepositionPair(
            verb = "sprechen",
            preposition = "mit",
            case = "Dativ",
            sentence = "Ich habe gestern kurz ${DrillItem.GAP_MARKER} dem Chef gesprochen.",
            choices = listOf("mit", "bei", "zu", "von"),
        ),
        // 30. telefonieren mit + Dativ
        VerbPrepositionPair(
            verb = "telefonieren",
            preposition = "mit",
            case = "Dativ",
            sentence = "Sie telefoniert gerade ${DrillItem.GAP_MARKER} ihrer Großmutter.",
            choices = listOf("mit", "zu", "an", "bei"),
        ),
        // 31. fragen nach + Dativ
        VerbPrepositionPair(
            verb = "fragen",
            preposition = "nach",
            case = "Dativ",
            sentence = "Der Tourist fragt höflich ${DrillItem.GAP_MARKER} dem Weg zum Bahnhof.",
            choices = listOf("nach", "zu", "für", "um"),
        ),
        // 32. suchen nach + Dativ
        VerbPrepositionPair(
            verb = "suchen",
            preposition = "nach",
            case = "Dativ",
            sentence = "Seit zwei Stunden suchen wir ${DrillItem.GAP_MARKER} dem verlorenen Schlüssel.",
            choices = listOf("nach", "für", "um", "zu"),
        ),
        // 33. riechen nach + Dativ
        VerbPrepositionPair(
            verb = "riechen",
            preposition = "nach",
            case = "Dativ",
            sentence = "In der ganzen Küche riecht es ${DrillItem.GAP_MARKER} frischem Kuchen.",
            choices = listOf("nach", "von", "zu", "mit"),
        ),
        // 34. schmecken nach + Dativ
        VerbPrepositionPair(
            verb = "schmecken",
            preposition = "nach",
            case = "Dativ",
            sentence = "Dieser Tee schmeckt intensiv ${DrillItem.GAP_MARKER} süßen Erdbeeren.",
            choices = listOf("nach", "von", "zu", "mit"),
        ),
        // 35. sich sehnen nach + Dativ
        VerbPrepositionPair(
            verb = "sich sehnen",
            preposition = "nach",
            case = "Dativ",
            sentence = "Im kalten Winter sehnen wir uns ${DrillItem.GAP_MARKER} der Sonne.",
            choices = listOf("nach", "zu", "an", "auf"),
        ),
        // 36. träumen von + Dativ
        VerbPrepositionPair(
            verb = "träumen",
            preposition = "von",
            case = "Dativ",
            sentence = "Er träumt schon lange ${DrillItem.GAP_MARKER} einer großen Reise.",
            choices = listOf("von", "über", "an", "auf"),
        ),
        // 37. erzählen von + Dativ
        VerbPrepositionPair(
            verb = "erzählen",
            preposition = "von",
            case = "Dativ",
            sentence = "Oma erzählt gern ${DrillItem.GAP_MARKER} ihrer Jugend.",
            choices = listOf("von", "über", "an", "aus"),
        ),
        // 38. abhängen von + Dativ
        VerbPrepositionPair(
            verb = "abhängen",
            preposition = "von",
            case = "Dativ",
            sentence = "Das Ergebnis hängt ganz ${DrillItem.GAP_MARKER} deiner Entscheidung ab.",
            choices = listOf("von", "an", "auf", "aus"),
        ),
        // 39. berichten von + Dativ
        VerbPrepositionPair(
            verb = "berichten",
            preposition = "von",
            case = "Dativ",
            sentence = "Die Reporterin berichtet live ${DrillItem.GAP_MARKER} der Unfallstelle.",
            choices = listOf("von", "über", "aus", "bei"),
        ),
        // 40. warnen vor + Dativ
        VerbPrepositionPair(
            verb = "warnen",
            preposition = "vor",
            case = "Dativ",
            sentence = "Der Wetterbericht warnt ${DrillItem.GAP_MARKER} starkem Sturm.",
            choices = listOf("vor", "von", "gegen", "für"),
        ),
        // 41. schützen vor + Dativ
        VerbPrepositionPair(
            verb = "schützen",
            preposition = "vor",
            case = "Dativ",
            sentence = "Diese warme Jacke schützt gut ${DrillItem.GAP_MARKER} der Kälte.",
            choices = listOf("vor", "von", "gegen", "aus"),
        ),
        // 42. fliehen vor + Dativ
        VerbPrepositionPair(
            verb = "fliehen",
            preposition = "vor",
            case = "Dativ",
            sentence = "Die Tiere fliehen ${DrillItem.GAP_MARKER} dem Feuer.",
            choices = listOf("vor", "von", "aus", "bei"),
        ),
        // 43. leiden an + Dativ
        VerbPrepositionPair(
            verb = "leiden",
            preposition = "an",
            case = "Dativ",
            sentence = "Der Patient leidet seit Jahren ${DrillItem.GAP_MARKER} einer seltenen Krankheit.",
            choices = listOf("an", "unter", "von", "bei"),
        ),
        // 44. leiden unter + Dativ
        VerbPrepositionPair(
            verb = "leiden",
            preposition = "unter",
            case = "Dativ",
            sentence = "Viele Menschen leiden im Sommer ${DrillItem.GAP_MARKER} der großen Hitze.",
            choices = listOf("unter", "an", "von", "bei"),
        ),
        // 45. zweifeln an + Dativ
        VerbPrepositionPair(
            verb = "zweifeln",
            preposition = "an",
            case = "Dativ",
            sentence = "Ich zweifle nicht mehr ${DrillItem.GAP_MARKER} seinen guten Absichten.",
            choices = listOf("an", "in", "auf", "über"),
        ),
        // 46. erkennen an + Dativ
        VerbPrepositionPair(
            verb = "erkennen",
            preposition = "an",
            case = "Dativ",
            sentence = "Man erkennt den Vogel leicht ${DrillItem.GAP_MARKER} seinem roten Schnabel.",
            choices = listOf("an", "in", "auf", "von"),
        ),
        // 47. liegen an + Dativ
        VerbPrepositionPair(
            verb = "liegen",
            preposition = "an",
            case = "Dativ",
            sentence = "Der Misserfolg lag leider nur ${DrillItem.GAP_MARKER} schlechter Planung.",
            choices = listOf("an", "bei", "in", "auf"),
        ),
        // 48. sterben an + Dativ
        VerbPrepositionPair(
            verb = "sterben",
            preposition = "an",
            case = "Dativ",
            sentence = "Früher starben viele Menschen ${DrillItem.GAP_MARKER} einfachen Infektionen.",
            choices = listOf("an", "von", "aus", "durch"),
        ),
        // 49. sich gewöhnen an + Akkusativ
        VerbPrepositionPair(
            verb = "sich gewöhnen",
            preposition = "an",
            case = "Akkusativ",
            sentence = "Man gewöhnt sich schnell ${DrillItem.GAP_MARKER} das neue Leben in der Stadt.",
            choices = listOf("an", "auf", "in", "zu"),
        ),
        // 50. verzichten auf + Akkusativ
        VerbPrepositionPair(
            verb = "verzichten",
            preposition = "auf",
            case = "Akkusativ",
            sentence = "Er verzichtet während der Fastenzeit ${DrillItem.GAP_MARKER} Zucker.",
            choices = listOf("auf", "an", "von", "über"),
        ),
        // 51. vertrauen auf + Akkusativ
        VerbPrepositionPair(
            verb = "vertrauen",
            preposition = "auf",
            case = "Akkusativ",
            sentence = "Wir vertrauen ganz fest ${DrillItem.GAP_MARKER} deine Erfahrung.",
            choices = listOf("auf", "an", "in", "zu"),
        ),
        // 52. reagieren auf + Akkusativ
        VerbPrepositionPair(
            verb = "reagieren",
            preposition = "auf",
            case = "Akkusativ",
            sentence = "Die Firma reagiert schnell ${DrillItem.GAP_MARKER} die Beschwerden der Kunden.",
            choices = listOf("auf", "an", "für", "über"),
        ),
        // 53. antworten auf + Akkusativ
        VerbPrepositionPair(
            verb = "antworten",
            preposition = "auf",
            case = "Akkusativ",
            sentence = "Er antwortet leider fast nie ${DrillItem.GAP_MARKER} meine E-Mails.",
            choices = listOf("auf", "an", "für", "zu"),
        ),
        // 54. ankommen auf + Akkusativ
        VerbPrepositionPair(
            verb = "ankommen",
            preposition = "auf",
            case = "Akkusativ",
            sentence = "Es kommt beim Lernen ${DrillItem.GAP_MARKER} regelmäßige Übung an.",
            choices = listOf("auf", "an", "in", "zu"),
        ),
        // 55. sich bedanken bei + Dativ
        VerbPrepositionPair(
            verb = "sich bedanken",
            preposition = "bei",
            case = "Dativ",
            sentence = "Ich möchte mich noch herzlich ${DrillItem.GAP_MARKER} Ihnen bedanken.",
            choices = listOf("bei", "an", "zu", "mit"),
        ),
        // 56. sich bedanken für + Akkusativ
        VerbPrepositionPair(
            verb = "sich bedanken",
            preposition = "für",
            case = "Akkusativ",
            sentence = "Wir bedanken uns ${DrillItem.GAP_MARKER} die freundliche Einladung.",
            choices = listOf("für", "über", "an", "um"),
        ),
        // 57. sich konzentrieren auf + Akkusativ
        VerbPrepositionPair(
            verb = "sich konzentrieren",
            preposition = "auf",
            case = "Akkusativ",
            sentence = "Bitte konzentriert euch jetzt voll ${DrillItem.GAP_MARKER} die Aufgabe.",
            choices = listOf("auf", "an", "für", "in"),
        ),
        // 58. führen zu + Dativ
        VerbPrepositionPair(
            verb = "führen",
            preposition = "zu",
            case = "Dativ",
            sentence = "Diese schwere Krise führte ${DrillItem.GAP_MARKER} vielen Veränderungen.",
            choices = listOf("zu", "an", "in", "nach"),
        ),
        // 59. protestieren gegen + Akkusativ
        VerbPrepositionPair(
            verb = "protestieren",
            preposition = "gegen",
            case = "Akkusativ",
            sentence = "Die Bürger protestieren laut ${DrillItem.GAP_MARKER} die neuen Steuern.",
            choices = listOf("gegen", "für", "auf", "wider"),
        ),
        // 60. sich einigen auf + Akkusativ
        VerbPrepositionPair(
            verb = "sich einigen",
            preposition = "auf",
            case = "Akkusativ",
            sentence = "Die Partner einigten sich schnell ${DrillItem.GAP_MARKER} einen fairen Kompromiss.",
            choices = listOf("auf", "an", "über", "für"),
        ),
        // 61. bestehen aus + Dativ
        VerbPrepositionPair(
            verb = "bestehen",
            preposition = "aus",
            case = "Dativ",
            sentence = "Das Team besteht ${DrillItem.GAP_MARKER} fünf erfahrenen Entwicklern.",
            choices = listOf("aus", "von", "an", "in"),
        ),
        // 62. bestehen auf + Dativ
        VerbPrepositionPair(
            verb = "bestehen",
            preposition = "auf",
            case = "Dativ",
            sentence = "Er besteht stur ${DrillItem.GAP_MARKER} seinem vertraglichen Recht.",
            choices = listOf("auf", "an", "in", "zu"),
        ),
        // 63. sich sorgen um + Akkusativ
        VerbPrepositionPair(
            verb = "sich sorgen",
            preposition = "um",
            case = "Akkusativ",
            sentence = "Die Eltern sorgen sich ${DrillItem.GAP_MARKER} die Zukunft ihrer Kinder.",
            choices = listOf("um", "für", "über", "an"),
        ),
        // 64. sorgen für + Akkusativ
        VerbPrepositionPair(
            verb = "sorgen",
            preposition = "für",
            case = "Akkusativ",
            sentence = "Gute Musik sorgt immer ${DrillItem.GAP_MARKER} beste Stimmung.",
            choices = listOf("für", "um", "auf", "an"),
        ),
        // 65. kämpfen für + Akkusativ
        VerbPrepositionPair(
            verb = "kämpfen",
            preposition = "für",
            case = "Akkusativ",
            sentence = "Die Menschen kämpfen mutig ${DrillItem.GAP_MARKER} ihre Freiheit.",
            choices = listOf("für", "um", "gegen", "auf"),
        ),
        // 66. kämpfen gegen + Akkusativ
        VerbPrepositionPair(
            verb = "kämpfen",
            preposition = "gegen",
            case = "Akkusativ",
            sentence = "Der Arzt kämpft jeden Tag ${DrillItem.GAP_MARKER} die Krankheit.",
            choices = listOf("gegen", "für", "wider", "um"),
        ),
    )

    internal val ENTRIES = PAIRS

    /**
     * Builds drill items for verb plus preposition pairs.
     *
     * Produces two kinds of items for each pair:
     * 1. A gap sentence where the user picks the preposition among 4 choices.
     * 2. A case question where the user picks the governing case (Akkusativ or Dativ).
     *
     * If a saved word matches the table verb (trimmed, case-insensitively, leading "sich " ignored),
     * the item key is the saved word's ID. Otherwise, it uses the stable table key
     * `verbprep:<verb>:<preposition>:<index>`. Saved-word items match no authored help item,
     * so they carry no rule id (the help card stays hidden for them).
     */
    fun items(words: Collection<Word> = emptyList()): List<DrillItem> {
        val wordMapByExact = words.associateBy { it.text.trim().lowercase() }
        val wordMapByNormalized = words.associateBy { normalizeVerb(it.text) }

        return PAIRS.flatMap { pair ->
            val matchingWord = wordMapByExact[pair.verb.trim().lowercase()]
                ?: wordMapByNormalized[normalizeVerb(pair.verb)]
            val hasSavedWord = matchingWord != null

            val gapKey = matchingWord?.let { "${it.id}" }
                ?: "verbprep:${pair.verb}:${pair.preposition}:0"
            val caseKey = matchingWord?.let { "${it.id}" }
                ?: "verbprep:${pair.verb}:${pair.preposition}:1"

            val content = VERB_PREP_RULE_CONTENT["${pair.verb} ${pair.preposition}"]
                ?: error("Missing verb + preposition rule content for ${pair.verb} ${pair.preposition}")
            val gapRuleId = content.gapRuleId
                .let { DrillHelpContent.requireValidRuleId(it, "verb prepositions item $gapKey") }
            val caseRuleId = content.caseRuleId
                .let { DrillHelpContent.requireValidRuleId(it, "verb prepositions item $caseKey") }

            // Gap hint names the verb and its case but never the preposition (the gap answer).
            // The case item's answer IS the case name, so it carries no hint at all.
            val gapHint = "${pair.verb} + ${pair.case}"

            listOf(
                DrillItem(
                    id = DrillItemId(DrillType.VERB_PREPOSITIONS, gapKey),
                    prompt = pair.sentence,
                    answer = pair.preposition,
                    choices = pair.choices,
                    hint = gapHint,
                    ruleId = if (hasSavedWord) null else gapRuleId,
                    missByChoice = if (hasSavedWord) emptyMap() else content.gapMiss,
                ),
                DrillItem(
                    id = DrillItemId(DrillType.VERB_PREPOSITIONS, caseKey),
                    prompt = "Welcher Fall folgt auf „${pair.verb} ${pair.preposition}“?",
                    answer = pair.case,
                    choices = CASE_CHOICES,
                    ruleId = if (hasSavedWord) null else caseRuleId,
                    missByChoice = if (hasSavedWord) emptyMap() else content.caseMiss,
                ),
            )
        }
    }
}
