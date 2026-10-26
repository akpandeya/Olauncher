package app.olauncher.data.store

/**
 * Drill content for German word order.
 *
 * Covers:
 * - Verb second in main clauses (including time-first inversion)
 * - Verb last after subjunctions (weil, dass, wenn, ob)
 * - Separable prefix at the end
 * - Modal plus infinitive at the end
 * - Perfekt bracket (Hilfsverb in position 2, Partizip II at the end)
 * - TeKaMoLo order (Temporal, Kausal, Modal, Lokal)
 * - Verb-first yes/no questions (Entscheidungsfragen)
 */
object DrillWordOrder {

    enum class Category(val displayName: String) {
        VERB_SECOND("Verb second in main clauses"),
        VERB_LAST_SUBORDINATE("Verb last after subjunctions"),
        SEPARABLE_VERB("Separable prefix at the end"),
        MODAL_INFINITIVE("Modal plus infinitive at the end"),
        PERFEKT_BRACKET("Perfekt bracket"),
        TEKAMOLO("TeKaMoLo order"),
        YES_NO_QUESTION("Verb-first yes/no questions"),
    }

    data class Entry(
        val key: String,
        val category: Category,
        val item: WordOrderItem,
    )

    val ENTRIES: List<Entry> = listOf(
        // ==========================================
        // 1. Verb second in main clauses (with inversion) - 6 items
        // ==========================================
        Entry(
            key = "v2_kino",
            category = Category.VERB_SECOND,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "v2_kino"),
                tiles = listOf("Ich", "gehe", "morgen", "ins", "Kino"),
                accepted = listOf(
                    listOf("Ich", "gehe", "morgen", "ins", "Kino"),
                    listOf("Morgen", "gehe", "ich", "ins", "Kino"),
                ),
                hint = "Position 2: Konjugiertes Verb (Inversion bei Zeitangabe an Position 1)",
            ),
        ),
        Entry(
            key = "v2_kaffee",
            category = Category.VERB_SECOND,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "v2_kaffee"),
                tiles = listOf("Wir", "trinken", "heute", "einen", "Kaffee"),
                accepted = listOf(
                    listOf("Wir", "trinken", "heute", "einen", "Kaffee"),
                    listOf("Heute", "trinken", "wir", "einen", "Kaffee"),
                ),
                hint = "Position 2: Konjugiertes Verb (Inversion bei Zeitangabe an Position 1)",
            ),
        ),
        Entry(
            key = "v2_buch",
            category = Category.VERB_SECOND,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "v2_buch"),
                tiles = listOf("Er", "kauft", "am", "Nachmittag", "ein", "Buch"),
                accepted = listOf(
                    listOf("Er", "kauft", "am", "Nachmittag", "ein", "Buch"),
                    listOf("Am", "Nachmittag", "kauft", "er", "ein", "Buch"),
                ),
                hint = "Position 2: Konjugiertes Verb (Inversion bei Zeitangabe an Position 1)",
            ),
        ),
        Entry(
            key = "v2_garten",
            category = Category.VERB_SECOND,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "v2_garten"),
                tiles = listOf("Sie", "spielen", "jetzt", "im", "Garten"),
                accepted = listOf(
                    listOf("Sie", "spielen", "jetzt", "im", "Garten"),
                    listOf("Jetzt", "spielen", "sie", "im", "Garten"),
                ),
                hint = "Position 2: Konjugiertes Verb (Inversion bei Zeitangabe an Position 1)",
            ),
        ),
        Entry(
            key = "v2_deutsch",
            category = Category.VERB_SECOND,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "v2_deutsch"),
                tiles = listOf("Ich", "lerne", "am", "Wochenende", "Deutsch"),
                accepted = listOf(
                    listOf("Ich", "lerne", "am", "Wochenende", "Deutsch"),
                    listOf("Am", "Wochenende", "lerne", "ich", "Deutsch"),
                ),
                hint = "Position 2: Konjugiertes Verb (Inversion bei Zeitangabe an Position 1)",
            ),
        ),
        Entry(
            key = "v2_hause",
            category = Category.VERB_SECOND,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "v2_hause"),
                tiles = listOf("Wir", "fahren", "bald", "nach", "Hause"),
                accepted = listOf(
                    listOf("Wir", "fahren", "bald", "nach", "Hause"),
                    listOf("Bald", "fahren", "wir", "nach", "Hause"),
                ),
                hint = "Position 2: Konjugiertes Verb (Inversion bei Zeitangabe an Position 1)",
            ),
        ),

        // ==========================================
        // 2. Verb last after subjunctions (weil, dass, wenn, ob) - 12 items
        // ==========================================
        // weil (3 items)
        Entry(
            key = "sub_weil_krank",
            category = Category.VERB_LAST_SUBORDINATE,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "sub_weil_krank"),
                tiles = listOf("Er", "bleibt", "im", "Bett", "weil", "er", "krank", "ist"),
                accepted = listOf(
                    listOf("Er", "bleibt", "im", "Bett", "weil", "er", "krank", "ist"),
                ),
                hint = "Nebensatz: Konjugiertes Verb am Satzende nach weil",
            ),
        ),
        Entry(
            key = "sub_weil_bus",
            category = Category.VERB_LAST_SUBORDINATE,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "sub_weil_bus"),
                tiles = listOf("Ich", "komme", "später", "weil", "der", "Bus", "Verspätung", "hat"),
                accepted = listOf(
                    listOf("Ich", "komme", "später", "weil", "der", "Bus", "Verspätung", "hat"),
                ),
                hint = "Nebensatz: Konjugiertes Verb am Satzende nach weil",
            ),
        ),
        Entry(
            key = "sub_weil_regen",
            category = Category.VERB_LAST_SUBORDINATE,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "sub_weil_regen"),
                tiles = listOf("Wir", "bleiben", "drinnen", "weil", "es", "heute", "regnet"),
                accepted = listOf(
                    listOf("Wir", "bleiben", "drinnen", "weil", "es", "heute", "regnet"),
                ),
                hint = "Nebensatz: Konjugiertes Verb am Satzende nach weil",
            ),
        ),
        // dass (3 items)
        Entry(
            key = "sub_dass_kommen",
            category = Category.VERB_LAST_SUBORDINATE,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "sub_dass_kommen"),
                tiles = listOf("Ich", "weiß", "dass", "du", "heute", "kommst"),
                accepted = listOf(
                    listOf("Ich", "weiß", "dass", "du", "heute", "kommst"),
                ),
                hint = "Nebensatz: Konjugiertes Verb am Satzende nach dass",
            ),
        ),
        Entry(
            key = "sub_dass_zeit",
            category = Category.VERB_LAST_SUBORDINATE,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "sub_dass_zeit"),
                tiles = listOf("Er", "sagt", "dass", "er", "keine", "Zeit", "hat"),
                accepted = listOf(
                    listOf("Er", "sagt", "dass", "er", "keine", "Zeit", "hat"),
                ),
                hint = "Nebensatz: Konjugiertes Verb am Satzende nach dass",
            ),
        ),
        Entry(
            key = "sub_dass_wetter",
            category = Category.VERB_LAST_SUBORDINATE,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "sub_dass_wetter"),
                tiles = listOf("Wir", "hoffen", "dass", "das", "Wetter", "morgen", "schön", "wird"),
                accepted = listOf(
                    listOf("Wir", "hoffen", "dass", "das", "Wetter", "morgen", "schön", "wird"),
                ),
                hint = "Nebensatz: Konjugiertes Verb am Satzende nach dass",
            ),
        ),
        // wenn (3 items)
        Entry(
            key = "sub_wenn_sonne",
            category = Category.VERB_LAST_SUBORDINATE,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "sub_wenn_sonne"),
                tiles = listOf("Wir", "fahren", "an", "den", "See", "wenn", "die", "Sonne", "scheint"),
                accepted = listOf(
                    listOf("Wir", "fahren", "an", "den", "See", "wenn", "die", "Sonne", "scheint"),
                ),
                hint = "Nebensatz: Konjugiertes Verb am Satzende nach wenn",
            ),
        ),
        Entry(
            key = "sub_wenn_anrufen",
            category = Category.VERB_LAST_SUBORDINATE,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "sub_wenn_anrufen"),
                tiles = listOf("Ich", "rufe", "dich", "an", "wenn", "ich", "Zeit", "habe"),
                accepted = listOf(
                    listOf("Ich", "rufe", "dich", "an", "wenn", "ich", "Zeit", "habe"),
                ),
                hint = "Nebensatz: Konjugiertes Verb am Satzende nach wenn",
            ),
        ),
        Entry(
            key = "sub_wenn_hilfe",
            category = Category.VERB_LAST_SUBORDINATE,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "sub_wenn_hilfe"),
                tiles = listOf("Du", "kannst", "mich", "fragen", "wenn", "du", "Hilfe", "brauchst"),
                accepted = listOf(
                    listOf("Du", "kannst", "mich", "fragen", "wenn", "du", "Hilfe", "brauchst"),
                ),
                hint = "Nebensatz: Konjugiertes Verb am Satzende nach wenn",
            ),
        ),
        // ob (3 items)
        Entry(
            key = "sub_ob_kommen",
            category = Category.VERB_LAST_SUBORDINATE,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "sub_ob_kommen"),
                tiles = listOf("Ich", "weiß", "nicht", "ob", "er", "heute", "kommt"),
                accepted = listOf(
                    listOf("Ich", "weiß", "nicht", "ob", "er", "heute", "kommt"),
                ),
                hint = "Nebensatz: Konjugiertes Verb am Satzende nach ob",
            ),
        ),
        Entry(
            key = "sub_ob_zeit",
            category = Category.VERB_LAST_SUBORDINATE,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "sub_ob_zeit"),
                tiles = listOf("Sie", "fragt", "ob", "wir", "morgen", "Zeit", "haben"),
                accepted = listOf(
                    listOf("Sie", "fragt", "ob", "wir", "morgen", "Zeit", "haben"),
                ),
                hint = "Nebensatz: Konjugiertes Verb am Satzende nach ob",
            ),
        ),
        Entry(
            key = "sub_ob_tuer",
            category = Category.VERB_LAST_SUBORDINATE,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "sub_ob_tuer"),
                tiles = listOf("Er", "prüft", "ob", "die", "Tür", "geschlossen", "ist"),
                accepted = listOf(
                    listOf("Er", "prüft", "ob", "die", "Tür", "geschlossen", "ist"),
                ),
                hint = "Nebensatz: Konjugiertes Verb am Satzende nach ob",
            ),
        ),

        // ==========================================
        // 3. Separable prefix at the end - 6 items
        // ==========================================
        Entry(
            key = "sep_aufstehen",
            category = Category.SEPARABLE_VERB,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "sep_aufstehen"),
                tiles = listOf("Ich", "stehe", "jeden", "Morgen", "um", "sieben", "Uhr", "auf"),
                accepted = listOf(
                    listOf("Ich", "stehe", "jeden", "Morgen", "um", "sieben", "Uhr", "auf"),
                    listOf("Jeden", "Morgen", "stehe", "ich", "um", "sieben", "Uhr", "auf"),
                ),
                hint = "Trennbare Verben: Konjugiertes Verb auf Position 2, Präfix am Satzende",
            ),
        ),
        Entry(
            key = "sep_anfangen",
            category = Category.SEPARABLE_VERB,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "sep_anfangen"),
                tiles = listOf("Der", "Film", "fängt", "um", "zwanzig", "Uhr", "an"),
                accepted = listOf(
                    listOf("Der", "Film", "fängt", "um", "zwanzig", "Uhr", "an"),
                    listOf("Um", "zwanzig", "Uhr", "fängt", "der", "Film", "an"),
                ),
                hint = "Trennbare Verben: Konjugiertes Verb auf Position 2, Präfix am Satzende",
            ),
        ),
        Entry(
            key = "sep_einkaufen",
            category = Category.SEPARABLE_VERB,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "sep_einkaufen"),
                tiles = listOf("Wir", "kaufen", "am", "Samstag", "im", "Supermarkt", "ein"),
                accepted = listOf(
                    listOf("Wir", "kaufen", "am", "Samstag", "im", "Supermarkt", "ein"),
                    listOf("Am", "Samstag", "kaufen", "wir", "im", "Supermarkt", "ein"),
                ),
                hint = "Trennbare Verben: Konjugiertes Verb auf Position 2, Präfix am Satzende",
            ),
        ),
        Entry(
            key = "sep_anrufen",
            category = Category.SEPARABLE_VERB,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "sep_anrufen"),
                tiles = listOf("Er", "ruft", "seine", "Mutter", "heute", "Abend", "an"),
                accepted = listOf(
                    listOf("Er", "ruft", "seine", "Mutter", "heute", "Abend", "an"),
                    listOf("Er", "ruft", "heute", "Abend", "seine", "Mutter", "an"),
                    listOf("Heute", "Abend", "ruft", "er", "seine", "Mutter", "an"),
                ),
                hint = "Trennbare Verben: Konjugiertes Verb auf Position 2, Präfix am Satzende",
            ),
        ),
        Entry(
            key = "sep_abfahren",
            category = Category.SEPARABLE_VERB,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "sep_abfahren"),
                tiles = listOf("Der", "Zug", "fährt", "pünktlich", "in", "München", "ab"),
                accepted = listOf(
                    listOf("Der", "Zug", "fährt", "pünktlich", "in", "München", "ab"),
                    listOf("Pünktlich", "fährt", "der", "Zug", "in", "München", "ab"),
                ),
                hint = "Trennbare Verben: Konjugiertes Verb auf Position 2, Präfix am Satzende",
            ),
        ),
        Entry(
            key = "sep_einschalten",
            category = Category.SEPARABLE_VERB,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "sep_einschalten"),
                tiles = listOf("Sie", "schaltet", "am", "Abend", "den", "Fernseher", "ein"),
                accepted = listOf(
                    listOf("Sie", "schaltet", "am", "Abend", "den", "Fernseher", "ein"),
                    listOf("Sie", "schaltet", "den", "Fernseher", "am", "Abend", "ein"),
                    listOf("Am", "Abend", "schaltet", "sie", "den", "Fernseher", "ein"),
                ),
                hint = "Trennbare Verben: Konjugiertes Verb auf Position 2, Präfix am Satzende",
            ),
        ),

        // ==========================================
        // 4. Modal plus infinitive at the end - 6 items
        // ==========================================
        Entry(
            key = "mod_sprechen",
            category = Category.MODAL_INFINITIVE,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "mod_sprechen"),
                tiles = listOf("Ich", "kann", "sehr", "gut", "Deutsch", "sprechen"),
                accepted = listOf(
                    listOf("Ich", "kann", "sehr", "gut", "Deutsch", "sprechen"),
                    listOf("Deutsch", "kann", "ich", "sehr", "gut", "sprechen"),
                ),
                hint = "Modalverb: Konjugiertes Modalverb auf Position 2, Infinitiv am Satzende",
            ),
        ),
        Entry(
            key = "mod_lernen",
            category = Category.MODAL_INFINITIVE,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "mod_lernen"),
                tiles = listOf("Wir", "müssen", "heute", "für", "die", "Prüfung", "lernen"),
                accepted = listOf(
                    listOf("Wir", "müssen", "heute", "für", "die", "Prüfung", "lernen"),
                    listOf("Heute", "müssen", "wir", "für", "die", "Prüfung", "lernen"),
                ),
                hint = "Modalverb: Konjugiertes Modalverb auf Position 2, Infinitiv am Satzende",
            ),
        ),
        Entry(
            key = "mod_fotografieren",
            category = Category.MODAL_INFINITIVE,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "mod_fotografieren"),
                tiles = listOf("Du", "darfst", "hier", "im", "Museum", "nicht", "fotografieren"),
                accepted = listOf(
                    listOf("Du", "darfst", "hier", "im", "Museum", "nicht", "fotografieren"),
                    listOf("Hier", "im", "Museum", "darfst", "du", "nicht", "fotografieren"),
                ),
                hint = "Modalverb: Konjugiertes Modalverb auf Position 2, Infinitiv am Satzende",
            ),
        ),
        Entry(
            key = "mod_besuchen",
            category = Category.MODAL_INFINITIVE,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "mod_besuchen"),
                tiles = listOf("Er", "will", "morgen", "seine", "Großeltern", "besuchen"),
                accepted = listOf(
                    listOf("Er", "will", "morgen", "seine", "Großeltern", "besuchen"),
                    listOf("Morgen", "will", "er", "seine", "Großeltern", "besuchen"),
                ),
                hint = "Modalverb: Konjugiertes Modalverb auf Position 2, Infinitiv am Satzende",
            ),
        ),
        Entry(
            key = "mod_nehmen",
            category = Category.MODAL_INFINITIVE,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "mod_nehmen"),
                tiles = listOf("Sie", "soll", "dreimal", "täglich", "diese", "Medizin", "nehmen"),
                accepted = listOf(
                    listOf("Sie", "soll", "dreimal", "täglich", "diese", "Medizin", "nehmen"),
                    listOf("Dreimal", "täglich", "soll", "sie", "diese", "Medizin", "nehmen"),
                ),
                hint = "Modalverb: Konjugiertes Modalverb auf Position 2, Infinitiv am Satzende",
            ),
        ),
        Entry(
            key = "mod_ausflug",
            category = Category.MODAL_INFINITIVE,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "mod_ausflug"),
                tiles = listOf("Wir", "möchten", "am", "Wochenende", "einen", "Ausflug", "machen"),
                accepted = listOf(
                    listOf("Wir", "möchten", "am", "Wochenende", "einen", "Ausflug", "machen"),
                    listOf("Am", "Wochenende", "möchten", "wir", "einen", "Ausflug", "machen"),
                ),
                hint = "Modalverb: Konjugiertes Modalverb auf Position 2, Infinitiv am Satzende",
            ),
        ),

        // ==========================================
        // 5. Perfekt bracket (Satzklammer) - 6 items
        // ==========================================
        Entry(
            key = "perf_buch",
            category = Category.PERFEKT_BRACKET,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "perf_buch"),
                tiles = listOf("Ich", "habe", "gestern", "ein", "spannendes", "Buch", "gelesen"),
                accepted = listOf(
                    listOf("Ich", "habe", "gestern", "ein", "spannendes", "Buch", "gelesen"),
                    listOf("Gestern", "habe", "ich", "ein", "spannendes", "Buch", "gelesen"),
                ),
                hint = "Perfekt: Hilfsverb auf Position 2, Partizip II am Satzende (Satzklammer)",
            ),
        ),
        Entry(
            key = "perf_zug",
            category = Category.PERFEKT_BRACKET,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "perf_zug"),
                tiles = listOf("Wir", "sind", "gestern", "mit", "dem", "Zug", "gefahren"),
                accepted = listOf(
                    listOf("Wir", "sind", "gestern", "mit", "dem", "Zug", "gefahren"),
                    listOf("Gestern", "sind", "wir", "mit", "dem", "Zug", "gefahren"),
                ),
                hint = "Perfekt: Hilfsverb auf Position 2, Partizip II am Satzende (Satzklammer)",
            ),
        ),
        Entry(
            key = "perf_aufgaben",
            category = Category.PERFEKT_BRACKET,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "perf_aufgaben"),
                tiles = listOf("Er", "hat", "seine", "Hausaufgaben", "schon", "fertig", "gemacht"),
                accepted = listOf(
                    listOf("Er", "hat", "seine", "Hausaufgaben", "schon", "fertig", "gemacht"),
                    listOf("Er", "hat", "schon", "seine", "Hausaufgaben", "fertig", "gemacht"),
                ),
                hint = "Perfekt: Hilfsverb auf Position 2, Partizip II am Satzende (Satzklammer)",
            ),
        ),
        Entry(
            key = "perf_aufgestanden",
            category = Category.PERFEKT_BRACKET,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "perf_aufgestanden"),
                tiles = listOf("Sie", "ist", "heute", "Morgen", "sehr", "früh", "aufgestanden"),
                accepted = listOf(
                    listOf("Sie", "ist", "heute", "Morgen", "sehr", "früh", "aufgestanden"),
                    listOf("Heute", "Morgen", "ist", "sie", "sehr", "früh", "aufgestanden"),
                ),
                hint = "Perfekt: Hilfsverb auf Position 2, Partizip II am Satzende (Satzklammer)",
            ),
        ),
        Entry(
            key = "perf_gearbeitet",
            category = Category.PERFEKT_BRACKET,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "perf_gearbeitet"),
                tiles = listOf("Ich", "habe", "den", "ganzen", "Tag", "fleißig", "gearbeitet"),
                accepted = listOf(
                    listOf("Ich", "habe", "den", "ganzen", "Tag", "fleißig", "gearbeitet"),
                    listOf("Den", "ganzen", "Tag", "habe", "ich", "fleißig", "gearbeitet"),
                ),
                hint = "Perfekt: Hilfsverb auf Position 2, Partizip II am Satzende (Satzklammer)",
            ),
        ),
        Entry(
            key = "perf_wohnung",
            category = Category.PERFEKT_BRACKET,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "perf_wohnung"),
                tiles = listOf("Wir", "haben", "letzte", "Woche", "eine", "neue", "Wohnung", "gefunden"),
                accepted = listOf(
                    listOf("Wir", "haben", "letzte", "Woche", "eine", "neue", "Wohnung", "gefunden"),
                    listOf("Letzte", "Woche", "haben", "wir", "eine", "neue", "Wohnung", "gefunden"),
                ),
                hint = "Perfekt: Hilfsverb auf Position 2, Partizip II am Satzende (Satzklammer)",
            ),
        ),

        // ==========================================
        // 6. TeKaMoLo order (Temporal, Kausal, Modal, Lokal) - 6 items
        // ==========================================
        Entry(
            key = "tekamolo_fahrrad",
            category = Category.TEKAMOLO,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "tekamolo_fahrrad"),
                tiles = listOf("Ich", "fahre", "morgen", "mit", "dem", "Fahrrad", "zur", "Arbeit"),
                accepted = listOf(
                    listOf("Ich", "fahre", "morgen", "mit", "dem", "Fahrrad", "zur", "Arbeit"),
                    listOf("Morgen", "fahre", "ich", "mit", "dem", "Fahrrad", "zur", "Arbeit"),
                ),
                hint = "TeKaMoLo: Temporal vor Modal vor Lokal (Wann? Wie? Wohin?)",
            ),
        ),
        Entry(
            key = "tekamolo_hause",
            category = Category.TEKAMOLO,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "tekamolo_hause"),
                tiles = listOf("Er", "fährt", "heute", "freiwillig", "nach", "Hause"),
                accepted = listOf(
                    listOf("Er", "fährt", "heute", "freiwillig", "nach", "Hause"),
                    listOf("Heute", "fährt", "er", "freiwillig", "nach", "Hause"),
                ),
                hint = "TeKaMoLo: Temporal vor Modal vor Lokal (Wann? Wie? Wohin?)",
            ),
        ),
        Entry(
            key = "tekamolo_restaurant",
            category = Category.TEKAMOLO,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "tekamolo_restaurant"),
                tiles = listOf("Wir", "gehen", "am", "Abend", "gerne", "ins", "Restaurant"),
                accepted = listOf(
                    listOf("Wir", "gehen", "am", "Abend", "gerne", "ins", "Restaurant"),
                    listOf("Am", "Abend", "gehen", "wir", "gerne", "ins", "Restaurant"),
                ),
                hint = "TeKaMoLo: Temporal vor Modal vor Lokal (Wann? Wie? Wohin?)",
            ),
        ),
        Entry(
            key = "tekamolo_italien",
            category = Category.TEKAMOLO,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "tekamolo_italien"),
                tiles = listOf("Sie", "reist", "im", "Sommer", "mit", "dem", "Zug", "nach", "Italien"),
                accepted = listOf(
                    listOf("Sie", "reist", "im", "Sommer", "mit", "dem", "Zug", "nach", "Italien"),
                    listOf("Im", "Sommer", "reist", "sie", "mit", "dem", "Zug", "nach", "Italien"),
                ),
                hint = "TeKaMoLo: Temporal vor Modal vor Lokal (Wann? Wie? Wohin?)",
            ),
        ),
        Entry(
            key = "tekamolo_berlin",
            category = Category.TEKAMOLO,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "tekamolo_berlin"),
                tiles = listOf("Ich", "fliege", "nächste", "Woche", "geschäftlich", "nach", "Berlin"),
                accepted = listOf(
                    listOf("Ich", "fliege", "nächste", "Woche", "geschäftlich", "nach", "Berlin"),
                    listOf("Nächste", "Woche", "fliege", "ich", "geschäftlich", "nach", "Berlin"),
                ),
                hint = "TeKaMoLo: Temporal vor Modal vor Lokal (Wann? Wie? Wohin?)",
            ),
        ),
        Entry(
            key = "tekamolo_park",
            category = Category.TEKAMOLO,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "tekamolo_park"),
                tiles = listOf("Er", "joggt", "jeden", "Morgen", "schnell", "durch", "den", "Park"),
                accepted = listOf(
                    listOf("Er", "joggt", "jeden", "Morgen", "schnell", "durch", "den", "Park"),
                    listOf("Jeden", "Morgen", "joggt", "er", "schnell", "durch", "den", "Park"),
                ),
                hint = "TeKaMoLo: Temporal vor Modal vor Lokal (Wann? Wie? Wohin?)",
            ),
        ),

        // ==========================================
        // 7. Verb-first yes/no questions - 6 items
        // ==========================================
        Entry(
            key = "q_party",
            category = Category.YES_NO_QUESTION,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "q_party"),
                tiles = listOf("Kommst", "du", "heute", "Abend", "zur", "Party"),
                accepted = listOf(
                    listOf("Kommst", "du", "heute", "Abend", "zur", "Party"),
                ),
                hint = "Ja/Nein-Frage: Konjugiertes Verb auf Position 1",
            ),
        ),
        Entry(
            key = "q_hausaufgaben",
            category = Category.YES_NO_QUESTION,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "q_hausaufgaben"),
                tiles = listOf("Hast", "du", "deine", "Hausaufgaben", "schon", "gemacht"),
                accepted = listOf(
                    listOf("Hast", "du", "deine", "Hausaufgaben", "schon", "gemacht"),
                    listOf("Hast", "du", "schon", "deine", "Hausaufgaben", "gemacht"),
                ),
                hint = "Ja/Nein-Frage: Konjugiertes Verb auf Position 1",
            ),
        ),
        Entry(
            key = "q_kino",
            category = Category.YES_NO_QUESTION,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "q_kino"),
                tiles = listOf("Gehen", "wir", "morgen", "zusammen", "ins", "Kino"),
                accepted = listOf(
                    listOf("Gehen", "wir", "morgen", "zusammen", "ins", "Kino"),
                    listOf("Gehen", "wir", "zusammen", "morgen", "ins", "Kino"),
                ),
                hint = "Ja/Nein-Frage: Konjugiertes Verb auf Position 1",
            ),
        ),
        Entry(
            key = "q_helfen",
            category = Category.YES_NO_QUESTION,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "q_helfen"),
                tiles = listOf("Kannst", "du", "mir", "bitte", "helfen"),
                accepted = listOf(
                    listOf("Kannst", "du", "mir", "bitte", "helfen"),
                    listOf("Kannst", "du", "bitte", "mir", "helfen"),
                ),
                hint = "Ja/Nein-Frage: Konjugiertes Verb auf Position 1",
            ),
        ),
        Entry(
            key = "q_kaffee",
            category = Category.YES_NO_QUESTION,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "q_kaffee"),
                tiles = listOf("Trinkst", "du", "morgens", "gerne", "Kaffee"),
                accepted = listOf(
                    listOf("Trinkst", "du", "morgens", "gerne", "Kaffee"),
                    listOf("Trinkst", "du", "gerne", "morgens", "Kaffee"),
                ),
                hint = "Ja/Nein-Frage: Konjugiertes Verb auf Position 1",
            ),
        ),
        Entry(
            key = "q_hamburg",
            category = Category.YES_NO_QUESTION,
            item = WordOrderItem(
                id = DrillItemId(DrillType.WORD_ORDER, "q_hamburg"),
                tiles = listOf("Fährst", "du", "am", "Wochenende", "nach", "Hamburg"),
                accepted = listOf(
                    listOf("Fährst", "du", "am", "Wochenende", "nach", "Hamburg"),
                ),
                hint = "Ja/Nein-Frage: Konjugiertes Verb auf Position 1",
            ),
        ),
    )

    /** Complete table of word order drill items. */
    val TABLE: List<WordOrderItem> = ENTRIES.map { it.item }

    /** Builds the word order items for saved [words], linking word IDs when matched. */
    fun items(words: Collection<Word> = emptyList()): List<WordOrderItem> {
        if (words.isEmpty()) return TABLE
        val wordMap = words.associateBy { it.text.trim().lowercase() }
        return TABLE.map { item ->
            val matchingWord = item.tiles.firstNotNullOfOrNull { tile ->
                wordMap[tile.lowercase()]
            }
            if (matchingWord != null) {
                item.copy(id = DrillItemId(DrillType.WORD_ORDER, "${matchingWord.id}:${item.id.key}"))
            } else {
                item
            }
        }
    }
}
