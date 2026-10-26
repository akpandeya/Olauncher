package app.olauncher.data.store

/**
 * Drill content for German separable and inseparable verbs.
 */
object DrillSeparableVerbs {

    internal data class SeparableVerbEntry(
        val infinitive: String,
        val prefix: String,
        val isSeparable: Boolean,
        val participle: String,
        val participleDistractors: List<String>,
        val sentence: String? = null,
        val prefixChoices: List<String> = emptyList(),
        val meaning: String,
    )

    internal fun normalizeVerb(text: String): String =
        text.trim().lowercase().removePrefix("sich ").trim()

    internal val TABLE: List<SeparableVerbEntry> = listOf(
        // --- Separable prefixes (an-, auf-, aus-, ein-, mit-, vor-, zu-, zurück-, fern-) ---
        // an-
        SeparableVerbEntry(
            infinitive = "anrufen",
            prefix = "an",
            isSeparable = true,
            participle = "angerufen",
            participleDistractors = listOf("geanrufen", "angeruft", "geanruft"),
            sentence = "Ich rufe dich morgen ${DrillItem.GAP_MARKER}.",
            prefixChoices = listOf("an", "auf", "aus", "mit"),
            meaning = "to call",
        ),
        SeparableVerbEntry(
            infinitive = "ankommen",
            prefix = "an",
            isSeparable = true,
            participle = "angekommen",
            participleDistractors = listOf("geankommen", "angekommt", "geankommt"),
            sentence = "Der Zug kommt pünktlich ${DrillItem.GAP_MARKER}.",
            prefixChoices = listOf("an", "ab", "aus", "nach"),
            meaning = "to arrive",
        ),
        SeparableVerbEntry(
            infinitive = "anfangen",
            prefix = "an",
            isSeparable = true,
            participle = "angefangen",
            participleDistractors = listOf("geanfangen", "angefangt", "geanfangt"),
            sentence = "Der Deutschkurs fängt heute ${DrillItem.GAP_MARKER}.",
            prefixChoices = listOf("an", "auf", "ein", "vor"),
            meaning = "to begin",
        ),
        SeparableVerbEntry(
            infinitive = "anziehen",
            prefix = "an",
            isSeparable = true,
            participle = "angezogen",
            participleDistractors = listOf("geanzogen", "angezieht", "geanzieht"),
            sentence = "Er zieht eine warme Jacke ${DrillItem.GAP_MARKER}.",
            prefixChoices = listOf("an", "aus", "um", "vor"),
            meaning = "to put on / dress",
        ),
        // auf-
        SeparableVerbEntry(
            infinitive = "aufstehen",
            prefix = "auf",
            isSeparable = true,
            participle = "aufgestanden",
            participleDistractors = listOf("geaufstanden", "aufgesteht", "geaufsteht"),
            sentence = "Ich stehe jeden Morgen um sechs Uhr ${DrillItem.GAP_MARKER}.",
            prefixChoices = listOf("auf", "aus", "an", "ein"),
            meaning = "to get up",
        ),
        SeparableVerbEntry(
            infinitive = "aufhören",
            prefix = "auf",
            isSeparable = true,
            participle = "aufgehört",
            participleDistractors = listOf("geaufhört", "aufgehoren", "geaufhoren"),
            sentence = "Wann hört der Regen endlich ${DrillItem.GAP_MARKER}.",
            prefixChoices = listOf("auf", "an", "aus", "zu"),
            meaning = "to stop / cease",
        ),
        SeparableVerbEntry(
            infinitive = "aufmachen",
            prefix = "auf",
            isSeparable = true,
            participle = "aufgemacht",
            participleDistractors = listOf("geaufmacht", "aufgemachen", "geaufmachen"),
            sentence = "Er macht das Fenster ${DrillItem.GAP_MARKER}.",
            prefixChoices = listOf("auf", "zu", "an", "aus"),
            meaning = "to open",
        ),
        SeparableVerbEntry(
            infinitive = "aufräumen",
            prefix = "auf",
            isSeparable = true,
            participle = "aufgeräumt",
            participleDistractors = listOf("geaufräumt", "aufgeräumen", "geaufräumen"),
            sentence = "Am Samstag räumt sie die Wohnung ${DrillItem.GAP_MARKER}.",
            prefixChoices = listOf("auf", "aus", "ein", "ab"),
            meaning = "to tidy up",
        ),
        // aus-
        SeparableVerbEntry(
            infinitive = "aussteigen",
            prefix = "aus",
            isSeparable = true,
            participle = "ausgestiegen",
            participleDistractors = listOf("geausstiegen", "ausgesteigt", "geaussteigt"),
            sentence = "Wir steigen an der nächsten Station ${DrillItem.GAP_MARKER}.",
            prefixChoices = listOf("aus", "ein", "um", "ab"),
            meaning = "to get off / exit",
        ),
        SeparableVerbEntry(
            infinitive = "aussehen",
            prefix = "aus",
            isSeparable = true,
            participle = "ausgesehen",
            participleDistractors = listOf("geaussehen", "ausgeseht", "geausseht"),
            sentence = "Du siehst heute sehr gut ${DrillItem.GAP_MARKER}.",
            prefixChoices = listOf("aus", "an", "auf", "vor"),
            meaning = "to look / appear",
        ),
        SeparableVerbEntry(
            infinitive = "ausmachen",
            prefix = "aus",
            isSeparable = true,
            participle = "ausgemacht",
            participleDistractors = listOf("geausmacht", "ausgemachen", "geausmachen"),
            sentence = "Bitte mache das Licht ${DrillItem.GAP_MARKER}.",
            prefixChoices = listOf("aus", "an", "auf", "zu"),
            meaning = "to turn off",
        ),
        SeparableVerbEntry(
            infinitive = "ausfüllen",
            prefix = "aus",
            isSeparable = true,
            participle = "ausgefüllt",
            participleDistractors = listOf("geausfüllt", "ausgefüllen", "geausfüllen"),
            sentence = "Füllen Sie bitte dieses Formular ${DrillItem.GAP_MARKER}.",
            prefixChoices = listOf("aus", "ein", "an", "auf"),
            meaning = "to fill out",
        ),
        // ein-
        SeparableVerbEntry(
            infinitive = "einkaufen",
            prefix = "ein",
            isSeparable = true,
            participle = "eingekauft",
            participleDistractors = listOf("geeinkauft", "eingekaufen", "geeinkaufen"),
            sentence = "Wir kaufen heute im Supermarkt ${DrillItem.GAP_MARKER}.",
            prefixChoices = listOf("ein", "aus", "auf", "mit"),
            meaning = "to shop",
        ),
        SeparableVerbEntry(
            infinitive = "einladen",
            prefix = "ein",
            isSeparable = true,
            participle = "eingeladen",
            participleDistractors = listOf("geeinladen", "eingeladt", "geeinladt"),
            sentence = "Ich lade dich zu meiner Party ${DrillItem.GAP_MARKER}.",
            prefixChoices = listOf("ein", "aus", "vor", "zu"),
            meaning = "to invite",
        ),
        SeparableVerbEntry(
            infinitive = "einsteigen",
            prefix = "ein",
            isSeparable = true,
            participle = "eingestiegen",
            participleDistractors = listOf("geeinstiegen", "eingesteigt", "geeinsteigt"),
            sentence = "Alle Fahrgäste steigen bitte ${DrillItem.GAP_MARKER}.",
            prefixChoices = listOf("ein", "aus", "um", "auf"),
            meaning = "to board / get in",
        ),
        SeparableVerbEntry(
            infinitive = "einschlafen",
            prefix = "ein",
            isSeparable = true,
            participle = "eingeschlafen",
            participleDistractors = listOf("geeinschlafen", "eingeschlaft", "geeinschlaft"),
            sentence = "Das Baby schläft abends schnell ${DrillItem.GAP_MARKER}.",
            prefixChoices = listOf("ein", "aus", "auf", "durch"),
            meaning = "to fall asleep",
        ),
        // mit-
        SeparableVerbEntry(
            infinitive = "mitkommen",
            prefix = "mit",
            isSeparable = true,
            participle = "mitgekommen",
            participleDistractors = listOf("gemitkommen", "mitgekommt", "gemitkommt"),
            sentence = "Kommst du heute Abend ins Theater ${DrillItem.GAP_MARKER}.",
            prefixChoices = listOf("mit", "an", "aus", "nach"),
            meaning = "to come along",
        ),
        SeparableVerbEntry(
            infinitive = "mitbringen",
            prefix = "mit",
            isSeparable = true,
            participle = "mitgebracht",
            participleDistractors = listOf("gemitbracht", "mitgebringt", "gemitbringt"),
            sentence = "Bringst du einen Salat ${DrillItem.GAP_MARKER}.",
            prefixChoices = listOf("mit", "vor", "nach", "bei"),
            meaning = "to bring along",
        ),
        SeparableVerbEntry(
            infinitive = "mitnehmen",
            prefix = "mit",
            isSeparable = true,
            participle = "mitgenommen",
            participleDistractors = listOf("gemitnommen", "mitgenehmt", "gemitnehmt"),
            sentence = "Ich nehme einen Regenschirm ${DrillItem.GAP_MARKER}.",
            prefixChoices = listOf("mit", "ab", "auf", "vor"),
            meaning = "to take along",
        ),
        SeparableVerbEntry(
            infinitive = "mitmachen",
            prefix = "mit",
            isSeparable = true,
            participle = "mitgemacht",
            participleDistractors = listOf("gemitmacht", "mitgemachen", "gemitmachen"),
            sentence = "Wer macht bei diesem Projekt ${DrillItem.GAP_MARKER}.",
            prefixChoices = listOf("mit", "an", "auf", "aus"),
            meaning = "to participate",
        ),
        // vor-
        SeparableVerbEntry(
            infinitive = "vorstellen",
            prefix = "vor",
            isSeparable = true,
            participle = "vorgestellt",
            participleDistractors = listOf("gevorstellt", "vorgestellen", "gevorstellen"),
            sentence = "Ich stelle dir meine Schwester ${DrillItem.GAP_MARKER}.",
            prefixChoices = listOf("vor", "an", "aus", "ein"),
            meaning = "to introduce",
        ),
        SeparableVerbEntry(
            infinitive = "vorlesen",
            prefix = "vor",
            isSeparable = true,
            participle = "vorgelesen",
            participleDistractors = listOf("gevorlesen", "vorgelest", "gevorlest"),
            sentence = "Die Mutter liest den Kindern ein Märchen ${DrillItem.GAP_MARKER}.",
            prefixChoices = listOf("vor", "mit", "aus", "durch"),
            meaning = "to read aloud",
        ),
        SeparableVerbEntry(
            infinitive = "vorbereiten",
            prefix = "vor",
            isSeparable = true,
            participle = "vorbereitet",
            participleDistractors = listOf("gevorbereitet", "vorbereiten", "gevorbereiten"),
            sentence = "Wir bereiten die Präsentation sorgfältig ${DrillItem.GAP_MARKER}.",
            prefixChoices = listOf("vor", "auf", "zu", "aus"),
            meaning = "to prepare",
        ),
        SeparableVerbEntry(
            infinitive = "vorschlagen",
            prefix = "vor",
            isSeparable = true,
            participle = "vorgeschlagen",
            participleDistractors = listOf("gevorschlagen", "vorgeschlagt", "gevorschlagt"),
            sentence = "Was schlägst du für das Wochenende ${DrillItem.GAP_MARKER}.",
            prefixChoices = listOf("vor", "nach", "auf", "ein"),
            meaning = "to suggest",
        ),
        // zu-
        SeparableVerbEntry(
            infinitive = "zumachen",
            prefix = "zu",
            isSeparable = true,
            participle = "zugemacht",
            participleDistractors = listOf("gezumacht", "zugemachen", "gezumachen"),
            sentence = "Machst du bitte das Fenster ${DrillItem.GAP_MARKER}.",
            prefixChoices = listOf("zu", "auf", "an", "ab"),
            meaning = "to close",
        ),
        SeparableVerbEntry(
            infinitive = "zuhören",
            prefix = "zu",
            isSeparable = true,
            participle = "zugehört",
            participleDistractors = listOf("gezuhört", "zugehoren", "gezuhoren"),
            sentence = "Die Schüler hören der Lehrerin aufmerksam ${DrillItem.GAP_MARKER}.",
            prefixChoices = listOf("zu", "auf", "an", "mit"),
            meaning = "to listen to",
        ),
        SeparableVerbEntry(
            infinitive = "zunehmen",
            prefix = "zu",
            isSeparable = true,
            participle = "zugenommen",
            participleDistractors = listOf("gezunommen", "zugenehmt", "gezunehmt"),
            sentence = "Im Winter nimmt er meistens zwei Kilo ${DrillItem.GAP_MARKER}.",
            prefixChoices = listOf("zu", "ab", "auf", "ein"),
            meaning = "to gain weight / increase",
        ),
        SeparableVerbEntry(
            infinitive = "zugreifen",
            prefix = "zu",
            isSeparable = true,
            participle = "zugegriffen",
            participleDistractors = listOf("gezugriffen", "zugegreift", "gezugreift"),
            sentence = "Das Buffet ist eröffnet, bitte greifen Sie ${DrillItem.GAP_MARKER}.",
            prefixChoices = listOf("zu", "an", "auf", "ein"),
            meaning = "to help oneself",
        ),
        // zurück-
        SeparableVerbEntry(
            infinitive = "zurückkommen",
            prefix = "zurück",
            isSeparable = true,
            participle = "zurückgekommen",
            participleDistractors = listOf("gezurückgekommen", "zurückgekommt", "zurückkommen"),
            sentence = "Wann kommst du aus dem Urlaub ${DrillItem.GAP_MARKER}.",
            prefixChoices = listOf("zurück", "wieder", "vor", "nach"),
            meaning = "to come back",
        ),
        SeparableVerbEntry(
            infinitive = "zurückgeben",
            prefix = "zurück",
            isSeparable = true,
            participle = "zurückgegeben",
            participleDistractors = listOf("gezurückgegeben", "zurückgegebt", "zurückgeben"),
            sentence = "Ich gebe dir dein Buch morgen ${DrillItem.GAP_MARKER}.",
            prefixChoices = listOf("zurück", "weg", "her", "hin"),
            meaning = "to give back",
        ),
        SeparableVerbEntry(
            infinitive = "zurückfahren",
            prefix = "zurück",
            isSeparable = true,
            participle = "zurückgefahren",
            participleDistractors = listOf("gezurückgefahren", "zurückgefahrt", "zurückfahren"),
            sentence = "Wir fahren am Sonntag nach Hause ${DrillItem.GAP_MARKER}.",
            prefixChoices = listOf("zurück", "ab", "weg", "hin"),
            meaning = "to drive back",
        ),
        // fern-
        SeparableVerbEntry(
            infinitive = "fernsehen",
            prefix = "fern",
            isSeparable = true,
            participle = "ferngesehen",
            participleDistractors = listOf("gefernsehen", "ferngeseht", "gefernseht"),
            sentence = "Die Familie sieht am Abend gerne ${DrillItem.GAP_MARKER}.",
            prefixChoices = listOf("fern", "zu", "mit", "vor"),
            meaning = "to watch TV",
        ),

        // --- Inseparable controls (be-, ver-, er-, ent-, zer-, ge-, emp-, miss-) ---
        // be-
        SeparableVerbEntry(
            infinitive = "besuchen",
            prefix = "be",
            isSeparable = false,
            participle = "besucht",
            participleDistractors = listOf("gebesucht", "besuchet", "gebesuchet"),
            meaning = "to visit",
        ),
        SeparableVerbEntry(
            infinitive = "bezahlen",
            prefix = "be",
            isSeparable = false,
            participle = "bezahlt",
            participleDistractors = listOf("gebezahlt", "bezahlen", "gebezahlen"),
            meaning = "to pay",
        ),
        SeparableVerbEntry(
            infinitive = "bekommen",
            prefix = "be",
            isSeparable = false,
            participle = "bekommen",
            participleDistractors = listOf("gebekommen", "bekommt", "gebekommt"),
            meaning = "to get / receive",
        ),
        // ver-
        SeparableVerbEntry(
            infinitive = "verstehen",
            prefix = "ver",
            isSeparable = false,
            participle = "verstanden",
            participleDistractors = listOf("geverstanden", "versteht", "geversteht"),
            meaning = "to understand",
        ),
        SeparableVerbEntry(
            infinitive = "vergessen",
            prefix = "ver",
            isSeparable = false,
            participle = "vergessen",
            participleDistractors = listOf("gevergesst", "vergesst", "gevergeßt"),
            meaning = "to forget",
        ),
        SeparableVerbEntry(
            infinitive = "verlieren",
            prefix = "ver",
            isSeparable = false,
            participle = "verloren",
            participleDistractors = listOf("geverloren", "verliert", "geverliert"),
            meaning = "to lose",
        ),
        // er-
        SeparableVerbEntry(
            infinitive = "erklären",
            prefix = "er",
            isSeparable = false,
            participle = "erklärt",
            participleDistractors = listOf("geerklärt", "erkläret", "geerkläret"),
            meaning = "to explain",
        ),
        SeparableVerbEntry(
            infinitive = "erzählen",
            prefix = "er",
            isSeparable = false,
            participle = "erzählt",
            participleDistractors = listOf("geerzählt", "erzählet", "geerzählet"),
            meaning = "to tell / narrate",
        ),
        SeparableVerbEntry(
            infinitive = "erreichen",
            prefix = "er",
            isSeparable = false,
            participle = "erreicht",
            participleDistractors = listOf("geerreicht", "erreichet", "geerreichet"),
            meaning = "to reach",
        ),
        // ent-
        SeparableVerbEntry(
            infinitive = "entscheiden",
            prefix = "ent",
            isSeparable = false,
            participle = "entschieden",
            participleDistractors = listOf("geentschieden", "entscheidet", "geentscheidet"),
            meaning = "to decide",
        ),
        SeparableVerbEntry(
            infinitive = "entdecken",
            prefix = "ent",
            isSeparable = false,
            participle = "entdeckt",
            participleDistractors = listOf("geentdeckt", "entdecket", "geentdecket"),
            meaning = "to discover",
        ),
        SeparableVerbEntry(
            infinitive = "entschuldigen",
            prefix = "ent",
            isSeparable = false,
            participle = "entschuldigt",
            participleDistractors = listOf("geentschuldigt", "entschuldiget", "geentschuldiget"),
            meaning = "to excuse / apologize",
        ),
        // zer-
        SeparableVerbEntry(
            infinitive = "zerstören",
            prefix = "zer",
            isSeparable = false,
            participle = "zerstört",
            participleDistractors = listOf("gezerstört", "zerstöret", "gezerstöret"),
            meaning = "to destroy",
        ),
        SeparableVerbEntry(
            infinitive = "zerbrechen",
            prefix = "zer",
            isSeparable = false,
            participle = "zerbrochen",
            participleDistractors = listOf("gezerbrochen", "zerbrecht", "gezerbrecht"),
            meaning = "to shatter",
        ),
        // ge-
        SeparableVerbEntry(
            infinitive = "gehören",
            prefix = "ge",
            isSeparable = false,
            participle = "gehört",
            participleDistractors = listOf("gegehört", "gehöret", "gegehöret"),
            meaning = "to belong to",
        ),
        SeparableVerbEntry(
            infinitive = "gefallen",
            prefix = "ge",
            isSeparable = false,
            participle = "gefallen",
            participleDistractors = listOf("gegefallen", "gefällt", "gegefällt"),
            meaning = "to please / like",
        ),
        SeparableVerbEntry(
            infinitive = "gelingen",
            prefix = "ge",
            isSeparable = false,
            participle = "gelungen",
            participleDistractors = listOf("gegelungen", "gelingt", "gegelingt"),
            meaning = "to succeed",
        ),
        // emp-
        SeparableVerbEntry(
            infinitive = "empfehlen",
            prefix = "emp",
            isSeparable = false,
            participle = "empfohlen",
            participleDistractors = listOf("geempfohlen", "empfehlt", "geempfehlt"),
            meaning = "to recommend",
        ),
        SeparableVerbEntry(
            infinitive = "empfangen",
            prefix = "emp",
            isSeparable = false,
            participle = "empfangen",
            participleDistractors = listOf("geempfangen", "empfangt", "geempfangt"),
            meaning = "to receive / welcome",
        ),
        // miss-
        SeparableVerbEntry(
            infinitive = "misstrauen",
            prefix = "miss",
            isSeparable = false,
            participle = "misstraut",
            participleDistractors = listOf("gemisstraut", "misstrauet", "gemisstrauet"),
            meaning = "to distrust",
        ),
        SeparableVerbEntry(
            infinitive = "missverstehen",
            prefix = "miss",
            isSeparable = false,
            participle = "missverstanden",
            participleDistractors = listOf("gemissverstanden", "missversteht", "gemissversteht"),
            meaning = "to misunderstand",
        ),

        // --- Dual prefixes (über-, um-, unter-, durch-, wieder-) ---
        // Meaning is explicitly fixed in the hint for all dual prefix verbs
        // über-
        SeparableVerbEntry(
            infinitive = "übersetzen",
            prefix = "über",
            isSeparable = true,
            participle = "übergesetzt",
            participleDistractors = listOf("geübersetzt", "übergesetzen", "übersetzt"),
            sentence = "Die Fähre setzt ans andere Ufer ${DrillItem.GAP_MARKER}.",
            prefixChoices = listOf("über", "unter", "durch", "um"),
            meaning = "ans andere Ufer übersetzen (trennbar)",
        ),
        SeparableVerbEntry(
            infinitive = "überprüfen",
            prefix = "über",
            isSeparable = false,
            participle = "überprüft",
            participleDistractors = listOf("geüberprüft", "übergeprüft", "überprüfet"),
            meaning = "kontrollieren (untrennbar)",
        ),
        // um-
        SeparableVerbEntry(
            infinitive = "umziehen",
            prefix = "um",
            isSeparable = true,
            participle = "umgezogen",
            participleDistractors = listOf("geumgezogen", "umzieht", "umziehen"),
            sentence = "Meine Familie zieht nächsten Monat in eine neue Wohnung ${DrillItem.GAP_MARKER}.",
            prefixChoices = listOf("um", "ein", "aus", "nach"),
            meaning = "Wohnung wechseln (trennbar)",
        ),
        SeparableVerbEntry(
            infinitive = "umarmen",
            prefix = "um",
            isSeparable = false,
            participle = "umarmt",
            participleDistractors = listOf("geumarmt", "umgearmt", "umarmet"),
            meaning = "in die Arme schließen (untrennbar)",
        ),
        // unter-
        SeparableVerbEntry(
            infinitive = "untergehen",
            prefix = "unter",
            isSeparable = true,
            participle = "untergegangen",
            participleDistractors = listOf("geuntergangen", "untergeht", "untergehen"),
            sentence = "Die Sonne geht langsam im Westen ${DrillItem.GAP_MARKER}.",
            prefixChoices = listOf("unter", "über", "durch", "auf"),
            meaning = "sinken / versinken (trennbar)",
        ),
        SeparableVerbEntry(
            infinitive = "untersuchen",
            prefix = "unter",
            isSeparable = false,
            participle = "untersucht",
            participleDistractors = listOf("geuntersucht", "untergesucht", "untersuchet"),
            meaning = "medizinisch prüfen (untrennbar)",
        ),
        // durch-
        SeparableVerbEntry(
            infinitive = "durchlesen",
            prefix = "durch",
            isSeparable = true,
            participle = "durchgelesen",
            participleDistractors = listOf("gedurchgelesen", "durchgelest", "durchlesen"),
            sentence = "Ich lese den langen Vertrag aufmerksam ${DrillItem.GAP_MARKER}.",
            prefixChoices = listOf("durch", "vor", "mit", "aus"),
            meaning = "von Anfang bis Ende lesen (trennbar)",
        ),
        SeparableVerbEntry(
            infinitive = "durchqueren",
            prefix = "durch",
            isSeparable = false,
            participle = "durchquert",
            participleDistractors = listOf("gedurchquert", "durchgequert", "durchqueret"),
            meaning = "hindurchgehen (untrennbar)",
        ),
        // wieder-
        SeparableVerbEntry(
            infinitive = "wiedersehen",
            prefix = "wieder",
            isSeparable = true,
            participle = "wiedergesehen",
            participleDistractors = listOf("gewiedersehen", "wiedergeseht", "wiedersehen"),
            sentence = "Wir sehen uns am nächsten Wochenende ${DrillItem.GAP_MARKER}.",
            prefixChoices = listOf("wieder", "vor", "zu", "fern"),
            meaning = "noch einmal treffen (trennbar)",
        ),
        SeparableVerbEntry(
            infinitive = "wiederholen",
            prefix = "wieder",
            isSeparable = false,
            participle = "wiederholt",
            participleDistractors = listOf("gewiederholt", "wiedergeholt", "wiederholet"),
            meaning = "noch einmal sagen oder tun (untrennbar)",
        ),
    )

    internal val ENTRIES = TABLE

    var defaultPresentLookup: ((String) -> String?)? = null

    /**
     * Builds drill items for separable and inseparable verbs.
     *
     * Three item kinds are produced:
     * 1. trennbar or untrennbar choice
     * 2. Partizip II selection against distractors (e.g. angerufen vs geanrufen)
     * 3. prefix at the end of a main clause (separable verbs only)
     *
     * If a saved verb is supplied, its offline dictionary `present` form determines if it
     * counts as separable: a present form with a space (e.g. "ruft an") counts as separable
     * and produces items keyed by word ID. Inseparable verbs (e.g. "besucht") do not count as
     * separable and are not keyed by word ID.
     */
    fun items(
        words: Collection<Word> = emptyList(),
        presentLookup: ((String) -> String?)? = null,
    ): List<DrillItem> {
        val resolver = presentLookup ?: defaultPresentLookup
        val wordMapByExact = words.associateBy { it.text.trim().lowercase() }
        val wordMapByNormalized = words.associateBy { normalizeVerb(it.text) }

        val handledWordIds = mutableSetOf<Long>()

        val tableItems = TABLE.flatMap { entry ->
            val matchingWord = wordMapByExact[entry.infinitive.trim().lowercase()]
                ?: wordMapByNormalized[normalizeVerb(entry.infinitive)]

            val countsAsSeparable = if (matchingWord != null) {
                val dictPresent = resolver?.invoke(matchingWord.text) ?: resolver?.invoke(entry.infinitive)
                if (dictPresent != null) {
                    dictPresent.contains(' ')
                } else {
                    entry.isSeparable
                }
            } else {
                false
            }

            val isKeyedByWordId = matchingWord != null && countsAsSeparable
            if (isKeyedByWordId) {
                handledWordIds.add(matchingWord!!.id)
            }

            val kind1Key = if (isKeyedByWordId) "${matchingWord!!.id}" else "sep:${entry.infinitive}:0"
            val kind2Key = if (isKeyedByWordId) "${matchingWord!!.id}" else "sep:${entry.infinitive}:1"
            val kind3Key = if (isKeyedByWordId) "${matchingWord!!.id}" else "sep:${entry.infinitive}:2"

            val hint = "${entry.infinitive} (${entry.meaning})"

            val itemsForEntry = mutableListOf<DrillItem>()

            // Kind 1: trennbar or untrennbar
            itemsForEntry.add(
                DrillItem(
                    id = DrillItemId(DrillType.SEPARABLE_VERBS, kind1Key),
                    prompt = "Das Verb „${entry.infinitive}“ ist ${DrillItem.GAP_MARKER}.",
                    answer = if (entry.isSeparable) "trennbar" else "untrennbar",
                    choices = listOf("trennbar", "untrennbar"),
                    hint = hint,
                )
            )

            // Kind 2: Partizip II
            val pChoices = (listOf(entry.participle) + entry.participleDistractors).distinct()
            itemsForEntry.add(
                DrillItem(
                    id = DrillItemId(DrillType.SEPARABLE_VERBS, kind2Key),
                    prompt = "Partizip II von „${entry.infinitive}“: ${DrillItem.GAP_MARKER}",
                    answer = entry.participle,
                    choices = pChoices,
                    hint = hint,
                )
            )

            // Kind 3: prefix at the end of a main clause (separable only)
            if (entry.isSeparable && entry.sentence != null) {
                itemsForEntry.add(
                    DrillItem(
                        id = DrillItemId(DrillType.SEPARABLE_VERBS, kind3Key),
                        prompt = entry.sentence,
                        answer = entry.prefix,
                        choices = entry.prefixChoices,
                        hint = hint,
                    )
                )
            }

            itemsForEntry
        }

        // Saved verbs not matching any table entry whose dictionary present form contains a space
        val extraSavedItems = mutableListOf<DrillItem>()
        if (resolver != null) {
            for (word in words) {
                if (word.id in handledWordIds) continue
                val clean = normalizeVerb(word.text)
                if (clean.isBlank()) continue
                val present = resolver(word.text) ?: resolver(clean) ?: continue
                if (!present.contains(' ')) continue

                val tokens = present.trim().split(Regex("\\s+"))
                if (tokens.size < 2) continue
                val prefix = tokens.last()
                val stem = tokens.dropLast(1).joinToString(" ")

                handledWordIds.add(word.id)
                val hint = "${word.text.trim()} ($present)"

                // Kind 1: trennbar
                extraSavedItems.add(
                    DrillItem(
                        id = DrillItemId(DrillType.SEPARABLE_VERBS, "${word.id}"),
                        prompt = "Das Verb „${word.text.trim()}“ ist ${DrillItem.GAP_MARKER}.",
                        answer = "trennbar",
                        choices = listOf("trennbar", "untrennbar"),
                        hint = hint,
                    )
                )

                // Kind 3: prefix at the end of a main clause
                val prefixOptions = (listOf(prefix, "an", "auf", "aus", "ein").distinct()).take(4)
                extraSavedItems.add(
                    DrillItem(
                        id = DrillItemId(DrillType.SEPARABLE_VERBS, "${word.id}"),
                        prompt = "Er $stem ${DrillItem.GAP_MARKER}.",
                        answer = prefix,
                        choices = prefixOptions,
                        hint = hint,
                    )
                )
            }
        }

        return tableItems + extraSavedItems
    }
}
