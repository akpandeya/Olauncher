package app.olauncher.data.store

/**
 * Drill content for German adjective endings.
 *
 * Items give the article and ask for the adjective ending.
 * The article is resolved using [ArticleForms], and the correct ending
 * is resolved using [AdjectiveEndings].
 */
object DrillAdjectiveEndings {

    internal data class AdjectiveEndingTemplate(
        val key: String,
        val pattern: String,
        val declension: AdjectiveEndings.Declension,
        val case: ArticleForms.Case,
        val gender: ArticleForms.Gender,
        val kind: ArticleForms.Kind = when (declension) {
            AdjectiveEndings.Declension.WEAK -> ArticleForms.Kind.DEFINITE
            AdjectiveEndings.Declension.MIXED -> if (gender == ArticleForms.Gender.PLURAL) ArticleForms.Kind.KEIN else ArticleForms.Kind.INDEFINITE
            AdjectiveEndings.Declension.STRONG -> ArticleForms.Kind.DEFINITE
        },
        val trigger: String = "",
        val explicitChoices: List<String>? = null,
        val explicitHint: String? = null,
    )

    val ALL_ENDINGS = listOf("e", "en", "er", "es", "em")

    internal fun defaultChoices(answer: String): List<String> = when (answer) {
        "en" -> listOf("en", "e", "er", "em")
        "e" -> listOf("e", "en", "er", "es")
        "er" -> listOf("er", "es", "en", "e")
        "es" -> listOf("es", "er", "en", "e")
        "em" -> listOf("em", "en", "er", "es")
        else -> (listOf(answer) + ALL_ENDINGS).distinct().take(4)
    }

    internal val TEMPLATES: List<AdjectiveEndingTemplate> = listOf(
        // ==========================================
        // 1. Weak Declension (Schwache Deklination) - 32 items
        // ==========================================

        // Nominativ Maskulinum (der ... -e)
        AdjectiveEndingTemplate(
            key = "nom_weak_m_mann",
            pattern = "{Art} alt${DrillItem.GAP_MARKER} Mann geht im Park spazieren.",
            declension = AdjectiveEndings.Declension.WEAK,
            case = ArticleForms.Case.NOMINATIVE,
            gender = ArticleForms.Gender.MASCULINE,
            trigger = "gehen",
        ),
        AdjectiveEndingTemplate(
            key = "nom_weak_m_hund",
            pattern = "{Art} klein${DrillItem.GAP_MARKER} Hund schläft im Garten.",
            declension = AdjectiveEndings.Declension.WEAK,
            case = ArticleForms.Case.NOMINATIVE,
            gender = ArticleForms.Gender.MASCULINE,
            trigger = "schlafen",
        ),

        // Nominativ Femininum (die ... -e)
        AdjectiveEndingTemplate(
            key = "nom_weak_f_frau",
            pattern = "{Art} jung${DrillItem.GAP_MARKER} Frau liest ein Buch.",
            declension = AdjectiveEndings.Declension.WEAK,
            case = ArticleForms.Case.NOMINATIVE,
            gender = ArticleForms.Gender.FEMININE,
            trigger = "lesen",
        ),
        AdjectiveEndingTemplate(
            key = "nom_weak_f_katze",
            pattern = "{Art} weiß${DrillItem.GAP_MARKER} Katze sitzt auf dem Dach.",
            declension = AdjectiveEndings.Declension.WEAK,
            case = ArticleForms.Case.NOMINATIVE,
            gender = ArticleForms.Gender.FEMININE,
            trigger = "sitzen",
        ),

        // Nominativ Neutrum (das ... -e)
        AdjectiveEndingTemplate(
            key = "nom_weak_n_kind",
            pattern = "{Art} klein${DrillItem.GAP_MARKER} Kind spielt mit dem Ball.",
            declension = AdjectiveEndings.Declension.WEAK,
            case = ArticleForms.Case.NOMINATIVE,
            gender = ArticleForms.Gender.NEUTER,
            trigger = "spielen",
        ),
        AdjectiveEndingTemplate(
            key = "nom_weak_n_haus",
            pattern = "{Art} neu${DrillItem.GAP_MARKER} Haus hat einen großen Garten.",
            declension = AdjectiveEndings.Declension.WEAK,
            case = ArticleForms.Case.NOMINATIVE,
            gender = ArticleForms.Gender.NEUTER,
            trigger = "haben",
        ),

        // Nominativ Plural (die ... -en)
        AdjectiveEndingTemplate(
            key = "nom_weak_pl_kinder",
            pattern = "{Art} fleißig${DrillItem.GAP_MARKER} Kinder lernen Deutsch.",
            declension = AdjectiveEndings.Declension.WEAK,
            case = ArticleForms.Case.NOMINATIVE,
            gender = ArticleForms.Gender.PLURAL,
            trigger = "lernen",
        ),
        AdjectiveEndingTemplate(
            key = "nom_weak_pl_baeume",
            pattern = "{Art} hoch${DrillItem.GAP_MARKER} Bäume stehen am Fluss.",
            declension = AdjectiveEndings.Declension.WEAK,
            case = ArticleForms.Case.NOMINATIVE,
            gender = ArticleForms.Gender.PLURAL,
            trigger = "stehen",
        ),

        // Akkusativ Maskulinum (den ... -en)
        AdjectiveEndingTemplate(
            key = "akk_weak_m_lehrer",
            pattern = "Ich kenne {art} nett${DrillItem.GAP_MARKER} Lehrer gut.",
            declension = AdjectiveEndings.Declension.WEAK,
            case = ArticleForms.Case.ACCUSATIVE,
            gender = ArticleForms.Gender.MASCULINE,
            trigger = "kennen",
        ),
        AdjectiveEndingTemplate(
            key = "akk_weak_m_kuchen",
            pattern = "Er isst {art} lecker${DrillItem.GAP_MARKER} Kuchen ganz auf.",
            declension = AdjectiveEndings.Declension.WEAK,
            case = ArticleForms.Case.ACCUSATIVE,
            gender = ArticleForms.Gender.MASCULINE,
            trigger = "essen",
        ),

        // Akkusativ Femininum (die ... -e)
        AdjectiveEndingTemplate(
            key = "akk_weak_f_stadt",
            pattern = "Wir besuchen {art} alt${DrillItem.GAP_MARKER} Stadt jedes Jahr.",
            declension = AdjectiveEndings.Declension.WEAK,
            case = ArticleForms.Case.ACCUSATIVE,
            gender = ArticleForms.Gender.FEMININE,
            trigger = "besuchen",
        ),
        AdjectiveEndingTemplate(
            key = "akk_weak_f_blume",
            pattern = "Sie gießt {art} schön${DrillItem.GAP_MARKER} Blume auf dem Balkon.",
            declension = AdjectiveEndings.Declension.WEAK,
            case = ArticleForms.Case.ACCUSATIVE,
            gender = ArticleForms.Gender.FEMININE,
            trigger = "gießen",
        ),

        // Akkusativ Neutrum (das ... -e)
        AdjectiveEndingTemplate(
            key = "akk_weak_n_buch",
            pattern = "Er kauft {art} spannend${DrillItem.GAP_MARKER} Buch im Laden.",
            declension = AdjectiveEndings.Declension.WEAK,
            case = ArticleForms.Case.ACCUSATIVE,
            gender = ArticleForms.Gender.NEUTER,
            trigger = "kaufen",
        ),
        AdjectiveEndingTemplate(
            key = "akk_weak_n_auto",
            pattern = "Sie verkaufen {art} rot${DrillItem.GAP_MARKER} Auto bald.",
            declension = AdjectiveEndings.Declension.WEAK,
            case = ArticleForms.Case.ACCUSATIVE,
            gender = ArticleForms.Gender.NEUTER,
            trigger = "verkaufen",
        ),

        // Akkusativ Plural (die ... -en)
        AdjectiveEndingTemplate(
            key = "akk_weak_pl_briefe",
            pattern = "Wir lesen {art} wichtig${DrillItem.GAP_MARKER} Briefe sorgfältig.",
            declension = AdjectiveEndings.Declension.WEAK,
            case = ArticleForms.Case.ACCUSATIVE,
            gender = ArticleForms.Gender.PLURAL,
            trigger = "lesen",
        ),
        AdjectiveEndingTemplate(
            key = "akk_weak_pl_gaeste",
            pattern = "Sie begrüßt {art} neu${DrillItem.GAP_MARKER} Gäste herzlich.",
            declension = AdjectiveEndings.Declension.WEAK,
            case = ArticleForms.Case.ACCUSATIVE,
            gender = ArticleForms.Gender.PLURAL,
            trigger = "begrüßen",
        ),

        // Dativ Maskulinum (dem ... -en)
        AdjectiveEndingTemplate(
            key = "dat_weak_m_bruder",
            pattern = "Ich helfe {art} groß${DrillItem.GAP_MARKER} Bruder beim Umzug.",
            declension = AdjectiveEndings.Declension.WEAK,
            case = ArticleForms.Case.DATIVE,
            gender = ArticleForms.Gender.MASCULINE,
            trigger = "helfen",
        ),
        AdjectiveEndingTemplate(
            key = "dat_weak_m_tisch",
            pattern = "Die Lampe steht auf {art} rund${DrillItem.GAP_MARKER} Tisch.",
            declension = AdjectiveEndings.Declension.WEAK,
            case = ArticleForms.Case.DATIVE,
            gender = ArticleForms.Gender.MASCULINE,
            trigger = "stehen",
        ),

        // Dativ Femininum (der ... -en)
        AdjectiveEndingTemplate(
            key = "dat_weak_f_mutter",
            pattern = "Er dankt {art} lieb${DrillItem.GAP_MARKER} Mutter für alles.",
            declension = AdjectiveEndings.Declension.WEAK,
            case = ArticleForms.Case.DATIVE,
            gender = ArticleForms.Gender.FEMININE,
            trigger = "danken",
        ),
        AdjectiveEndingTemplate(
            key = "dat_weak_f_strasse",
            pattern = "Wir wohnen in {art} ruhig${DrillItem.GAP_MARKER} Straße.",
            declension = AdjectiveEndings.Declension.WEAK,
            case = ArticleForms.Case.DATIVE,
            gender = ArticleForms.Gender.FEMININE,
            trigger = "wohnen",
        ),

        // Dativ Neutrum (dem ... -en)
        AdjectiveEndingTemplate(
            key = "dat_weak_n_kind",
            pattern = "Sie gibt {art} krank${DrillItem.GAP_MARKER} Kind warme Milch.",
            declension = AdjectiveEndings.Declension.WEAK,
            case = ArticleForms.Case.DATIVE,
            gender = ArticleForms.Gender.NEUTER,
            trigger = "geben",
        ),
        AdjectiveEndingTemplate(
            key = "dat_weak_n_dorf",
            pattern = "Er wohnt in {art} klein${DrillItem.GAP_MARKER} Dorf in den Bergen.",
            declension = AdjectiveEndings.Declension.WEAK,
            case = ArticleForms.Case.DATIVE,
            gender = ArticleForms.Gender.NEUTER,
            trigger = "wohnen",
        ),

        // Dativ Plural (den ... -en)
        AdjectiveEndingTemplate(
            key = "dat_weak_pl_studenten",
            pattern = "Der Professor antwortet {art} interessiert${DrillItem.GAP_MARKER} Studenten.",
            declension = AdjectiveEndings.Declension.WEAK,
            case = ArticleForms.Case.DATIVE,
            gender = ArticleForms.Gender.PLURAL,
            trigger = "antworten",
        ),
        AdjectiveEndingTemplate(
            key = "dat_weak_pl_haeusern",
            pattern = "Zwischen {art} bunt${DrillItem.GAP_MARKER} Häusern liegt ein Spielplatz.",
            declension = AdjectiveEndings.Declension.WEAK,
            case = ArticleForms.Case.DATIVE,
            gender = ArticleForms.Gender.PLURAL,
            trigger = "liegen",
        ),

        // Genitiv Maskulinum (des ... -en)
        AdjectiveEndingTemplate(
            key = "gen_weak_m_vaters",
            pattern = "Das ist das Fahrrad {art} alt${DrillItem.GAP_MARKER} Vaters.",
            declension = AdjectiveEndings.Declension.WEAK,
            case = ArticleForms.Case.GENITIVE,
            gender = ArticleForms.Gender.MASCULINE,
            trigger = "sein",
        ),
        AdjectiveEndingTemplate(
            key = "gen_weak_m_waldes",
            pattern = "Am Rande {art} dunkel${DrillItem.GAP_MARKER} Waldes steht eine Hütte.",
            declension = AdjectiveEndings.Declension.WEAK,
            case = ArticleForms.Case.GENITIVE,
            gender = ArticleForms.Gender.MASCULINE,
            trigger = "stehen",
        ),

        // Genitiv Femininum (der ... -en)
        AdjectiveEndingTemplate(
            key = "gen_weak_f_freundin",
            pattern = "Das ist das Auto {art} gut${DrillItem.GAP_MARKER} Freundin.",
            declension = AdjectiveEndings.Declension.WEAK,
            case = ArticleForms.Case.GENITIVE,
            gender = ArticleForms.Gender.FEMININE,
            trigger = "sein",
        ),
        AdjectiveEndingTemplate(
            key = "gen_weak_f_schuelerin",
            pattern = "Die Noten {art} fleißig${DrillItem.GAP_MARKER} Schülerin sind hervorragend.",
            declension = AdjectiveEndings.Declension.WEAK,
            case = ArticleForms.Case.GENITIVE,
            gender = ArticleForms.Gender.FEMININE,
            trigger = "sein",
        ),

        // Genitiv Neutrum (des ... -en)
        AdjectiveEndingTemplate(
            key = "gen_weak_n_hauses",
            pattern = "Das Dach {art} modern${DrillItem.GAP_MARKER} Hauses ist flach.",
            declension = AdjectiveEndings.Declension.WEAK,
            case = ArticleForms.Case.GENITIVE,
            gender = ArticleForms.Gender.NEUTER,
            trigger = "sein",
        ),
        AdjectiveEndingTemplate(
            key = "gen_weak_n_maedchens",
            pattern = "Das Lächeln {art} klein${DrillItem.GAP_MARKER} Mädchens ist bezaubernd.",
            declension = AdjectiveEndings.Declension.WEAK,
            case = ArticleForms.Case.GENITIVE,
            gender = ArticleForms.Gender.NEUTER,
            trigger = "sein",
        ),

        // Genitiv Plural (der ... -en)
        AdjectiveEndingTemplate(
            key = "gen_weak_pl_eltern",
            pattern = "Das Haus {art} nett${DrillItem.GAP_MARKER} Eltern liegt am See.",
            declension = AdjectiveEndings.Declension.WEAK,
            case = ArticleForms.Case.GENITIVE,
            gender = ArticleForms.Gender.PLURAL,
            trigger = "liegen",
        ),
        AdjectiveEndingTemplate(
            key = "gen_weak_pl_kinder",
            pattern = "Die Stimmen {art} fröhlich${DrillItem.GAP_MARKER} Kinder hallen im Flur.",
            declension = AdjectiveEndings.Declension.WEAK,
            case = ArticleForms.Case.GENITIVE,
            gender = ArticleForms.Gender.PLURAL,
            trigger = "hallen",
        ),

        // ==========================================
        // 2. Mixed Declension (Gemischte Deklination) - 32 items
        // ==========================================

        // Nominativ Maskulinum (ein ... -er)
        AdjectiveEndingTemplate(
            key = "nom_mixed_m_hund",
            pattern = "Dort läuft {art} treu${DrillItem.GAP_MARKER} Hund über die Wiese.",
            declension = AdjectiveEndings.Declension.MIXED,
            case = ArticleForms.Case.NOMINATIVE,
            gender = ArticleForms.Gender.MASCULINE,
            trigger = "laufen",
        ),
        AdjectiveEndingTemplate(
            key = "nom_mixed_m_apfel",
            pattern = "Hier liegt {art} rot${DrillItem.GAP_MARKER} Apfel auf dem Tisch.",
            declension = AdjectiveEndings.Declension.MIXED,
            case = ArticleForms.Case.NOMINATIVE,
            gender = ArticleForms.Gender.MASCULINE,
            trigger = "liegen",
        ),

        // Nominativ Femininum (eine ... -e)
        AdjectiveEndingTemplate(
            key = "nom_mixed_f_katze",
            pattern = "Dort schläft {art} schwarz${DrillItem.GAP_MARKER} Katze auf dem Sofa.",
            declension = AdjectiveEndings.Declension.MIXED,
            case = ArticleForms.Case.NOMINATIVE,
            gender = ArticleForms.Gender.FEMININE,
            trigger = "schlafen",
        ),
        AdjectiveEndingTemplate(
            key = "nom_mixed_f_uhr",
            pattern = "Das ist {art} teur${DrillItem.GAP_MARKER} Uhr aus der Schweiz.",
            declension = AdjectiveEndings.Declension.MIXED,
            case = ArticleForms.Case.NOMINATIVE,
            gender = ArticleForms.Gender.FEMININE,
            trigger = "sein",
        ),

        // Nominativ Neutrum (ein ... -es)
        AdjectiveEndingTemplate(
            key = "nom_mixed_n_buch",
            pattern = "Das ist {art} spannend${DrillItem.GAP_MARKER} Buch über Geschichte.",
            declension = AdjectiveEndings.Declension.MIXED,
            case = ArticleForms.Case.NOMINATIVE,
            gender = ArticleForms.Gender.NEUTER,
            trigger = "sein",
        ),
        AdjectiveEndingTemplate(
            key = "nom_mixed_n_kind",
            pattern = "Dort lacht {art} fröhlich${DrillItem.GAP_MARKER} Kind.",
            declension = AdjectiveEndings.Declension.MIXED,
            case = ArticleForms.Case.NOMINATIVE,
            gender = ArticleForms.Gender.NEUTER,
            trigger = "lachen",
        ),

        // Nominativ Plural (keine ... -en)
        AdjectiveEndingTemplate(
            key = "nom_mixed_pl_wolken",
            pattern = "Heute sind {art} dunkel${DrillItem.GAP_MARKER} Wolken am Himmel.",
            declension = AdjectiveEndings.Declension.MIXED,
            case = ArticleForms.Case.NOMINATIVE,
            gender = ArticleForms.Gender.PLURAL,
            trigger = "sein",
        ),
        AdjectiveEndingTemplate(
            key = "nom_mixed_pl_fehler",
            pattern = "Das sind {art} schwer${DrillItem.GAP_MARKER} Fehler im Text.",
            declension = AdjectiveEndings.Declension.MIXED,
            case = ArticleForms.Case.NOMINATIVE,
            gender = ArticleForms.Gender.PLURAL,
            trigger = "sein",
        ),

        // Akkusativ Maskulinum (einen ... -en)
        AdjectiveEndingTemplate(
            key = "akk_mixed_m_freund",
            pattern = "Ich habe {art} treu${DrillItem.GAP_MARKER} Freund gefunden.",
            declension = AdjectiveEndings.Declension.MIXED,
            case = ArticleForms.Case.ACCUSATIVE,
            gender = ArticleForms.Gender.MASCULINE,
            trigger = "finden",
        ),
        AdjectiveEndingTemplate(
            key = "akk_mixed_m_tee",
            pattern = "Er trinkt {art} heiß${DrillItem.GAP_MARKER} Tee mit Zitrone.",
            declension = AdjectiveEndings.Declension.MIXED,
            case = ArticleForms.Case.ACCUSATIVE,
            gender = ArticleForms.Gender.MASCULINE,
            trigger = "trinken",
        ),

        // Akkusativ Femininum (eine ... -e)
        AdjectiveEndingTemplate(
            key = "akk_mixed_f_suppe",
            pattern = "Sie kocht {art} lecker${DrillItem.GAP_MARKER} Suppe zum Abendessen.",
            declension = AdjectiveEndings.Declension.MIXED,
            case = ArticleForms.Case.ACCUSATIVE,
            gender = ArticleForms.Gender.FEMININE,
            trigger = "kochen",
        ),
        AdjectiveEndingTemplate(
            key = "akk_mixed_f_frage",
            pattern = "Er stellt {art} schwierig${DrillItem.GAP_MARKER} Frage im Unterricht.",
            declension = AdjectiveEndings.Declension.MIXED,
            case = ArticleForms.Case.ACCUSATIVE,
            gender = ArticleForms.Gender.FEMININE,
            trigger = "stellen",
        ),

        // Akkusativ Neutrum (ein ... -es)
        AdjectiveEndingTemplate(
            key = "akk_mixed_n_auto",
            pattern = "Wir kaufen {art} schnell${DrillItem.GAP_MARKER} Auto für die Reise.",
            declension = AdjectiveEndings.Declension.MIXED,
            case = ArticleForms.Case.ACCUSATIVE,
            gender = ArticleForms.Gender.NEUTER,
            trigger = "kaufen",
        ),
        AdjectiveEndingTemplate(
            key = "akk_mixed_n_kleid",
            pattern = "Sie trägt {art} blau${DrillItem.GAP_MARKER} Kleid zur Feier.",
            declension = AdjectiveEndings.Declension.MIXED,
            case = ArticleForms.Case.ACCUSATIVE,
            gender = ArticleForms.Gender.NEUTER,
            trigger = "tragen",
        ),

        // Akkusativ Plural (keine ... -en)
        AdjectiveEndingTemplate(
            key = "akk_mixed_pl_probleme",
            pattern = "Wir sehen {art} groß${DrillItem.GAP_MARKER} Probleme bei diesem Plan.",
            declension = AdjectiveEndings.Declension.MIXED,
            case = ArticleForms.Case.ACCUSATIVE,
            gender = ArticleForms.Gender.PLURAL,
            trigger = "sehen",
        ),
        AdjectiveEndingTemplate(
            key = "akk_mixed_pl_briefe",
            pattern = "Er schreibt {art} lang${DrillItem.GAP_MARKER} Briefe mehr.",
            declension = AdjectiveEndings.Declension.MIXED,
            case = ArticleForms.Case.ACCUSATIVE,
            gender = ArticleForms.Gender.PLURAL,
            trigger = "schreiben",
        ),

        // Dativ Maskulinum (einem ... -en)
        AdjectiveEndingTemplate(
            key = "dat_mixed_m_hund",
            pattern = "mit {art} klein${DrillItem.GAP_MARKER} Hund",
            declension = AdjectiveEndings.Declension.MIXED,
            case = ArticleForms.Case.DATIVE,
            gender = ArticleForms.Gender.MASCULINE,
            trigger = "mit",
        ),
        AdjectiveEndingTemplate(
            key = "dat_mixed_m_freund",
            pattern = "Sie spricht mit {art} gut${DrillItem.GAP_MARKER} Freund über alles.",
            declension = AdjectiveEndings.Declension.MIXED,
            case = ArticleForms.Case.DATIVE,
            gender = ArticleForms.Gender.MASCULINE,
            trigger = "mit",
        ),

        // Dativ Femininum (einer ... -en)
        AdjectiveEndingTemplate(
            key = "dat_mixed_f_tasse",
            pattern = "Er trinkt aus {art} weiß${DrillItem.GAP_MARKER} Tasse seinen Kaffee.",
            declension = AdjectiveEndings.Declension.MIXED,
            case = ArticleForms.Case.DATIVE,
            gender = ArticleForms.Gender.FEMININE,
            trigger = "aus",
        ),
        AdjectiveEndingTemplate(
            key = "dat_mixed_f_freundin",
            pattern = "Ich begegne {art} alt${DrillItem.GAP_MARKER} Freundin am Bahnhof.",
            declension = AdjectiveEndings.Declension.MIXED,
            case = ArticleForms.Case.DATIVE,
            gender = ArticleForms.Gender.FEMININE,
            trigger = "begegnen",
        ),

        // Dativ Neutrum (einem ... -en)
        AdjectiveEndingTemplate(
            key = "dat_mixed_n_dorf",
            pattern = "Sie wohnen in {art} ruhig${DrillItem.GAP_MARKER} Dorf im Süden.",
            declension = AdjectiveEndings.Declension.MIXED,
            case = ArticleForms.Case.DATIVE,
            gender = ArticleForms.Gender.NEUTER,
            trigger = "in",
        ),
        AdjectiveEndingTemplate(
            key = "dat_mixed_n_hotel",
            pattern = "Wir übernachten in {art} schön${DrillItem.GAP_MARKER} Hotel in Berlin.",
            declension = AdjectiveEndings.Declension.MIXED,
            case = ArticleForms.Case.DATIVE,
            gender = ArticleForms.Gender.NEUTER,
            trigger = "in",
        ),

        // Dativ Plural (keinen ... -en)
        AdjectiveEndingTemplate(
            key = "dat_mixed_pl_leuten",
            pattern = "Er vertraut {art} fremd${DrillItem.GAP_MARKER} Leuten so schnell.",
            declension = AdjectiveEndings.Declension.MIXED,
            case = ArticleForms.Case.DATIVE,
            gender = ArticleForms.Gender.PLURAL,
            trigger = "vertrauen",
        ),
        AdjectiveEndingTemplate(
            key = "dat_mixed_pl_tieren",
            pattern = "Sie schadet {art} wild${DrillItem.GAP_MARKER} Tieren im Wald.",
            declension = AdjectiveEndings.Declension.MIXED,
            case = ArticleForms.Case.DATIVE,
            gender = ArticleForms.Gender.PLURAL,
            trigger = "schaden",
        ),

        // Genitiv Maskulinum (eines ... -en)
        AdjectiveEndingTemplate(
            key = "gen_mixed_m_tages",
            pattern = "Eines schön${DrillItem.GAP_MARKER} Tages reisen wir nach Rom.",
            declension = AdjectiveEndings.Declension.MIXED,
            case = ArticleForms.Case.GENITIVE,
            gender = ArticleForms.Gender.MASCULINE,
            trigger = "reisen",
        ),
        AdjectiveEndingTemplate(
            key = "gen_mixed_m_lehrers",
            pattern = "Das ist das Buch {art} klug${DrillItem.GAP_MARKER} Lehrers.",
            declension = AdjectiveEndings.Declension.MIXED,
            case = ArticleForms.Case.GENITIVE,
            gender = ArticleForms.Gender.MASCULINE,
            trigger = "sein",
        ),

        // Genitiv Femininum (einer ... -en)
        AdjectiveEndingTemplate(
            key = "gen_mixed_f_blume",
            pattern = "Der Duft {art} frisch${DrillItem.GAP_MARKER} Blume erfüllt den Raum.",
            declension = AdjectiveEndings.Declension.MIXED,
            case = ArticleForms.Case.GENITIVE,
            gender = ArticleForms.Gender.FEMININE,
            trigger = "erfüllen",
        ),
        AdjectiveEndingTemplate(
            key = "gen_mixed_f_woche",
            pattern = "Innerhalb {art} kurz${DrillItem.GAP_MARKER} Woche war alles erledigt.",
            declension = AdjectiveEndings.Declension.MIXED,
            case = ArticleForms.Case.GENITIVE,
            gender = ArticleForms.Gender.FEMININE,
            trigger = "innerhalb",
        ),

        // Genitiv Neutrum (eines ... -en)
        AdjectiveEndingTemplate(
            key = "gen_mixed_n_hauses",
            pattern = "Wegen {art} alt${DrillItem.GAP_MARKER} Hauses gab es viel Streit.",
            declension = AdjectiveEndings.Declension.MIXED,
            case = ArticleForms.Case.GENITIVE,
            gender = ArticleForms.Gender.NEUTER,
            trigger = "wegen",
        ),
        AdjectiveEndingTemplate(
            key = "gen_mixed_n_kindes",
            pattern = "Die Zukunft {art} krank${DrillItem.GAP_MARKER} Kindes liegt uns am Herzen.",
            declension = AdjectiveEndings.Declension.MIXED,
            case = ArticleForms.Case.GENITIVE,
            gender = ArticleForms.Gender.NEUTER,
            trigger = "liegen",
        ),

        // Genitiv Plural (keiner ... -en)
        AdjectiveEndingTemplate(
            key = "gen_mixed_pl_zeugen",
            pattern = "Trotz {art} sicher${DrillItem.GAP_MARKER} Zeugen wurde der Fall gelöst.",
            declension = AdjectiveEndings.Declension.MIXED,
            case = ArticleForms.Case.GENITIVE,
            gender = ArticleForms.Gender.PLURAL,
            trigger = "trotz",
        ),
        AdjectiveEndingTemplate(
            key = "gen_mixed_pl_buecher",
            pattern = "Mangels {art} gut${DrillItem.GAP_MARKER} Bücher las er alte Zeitungen.",
            declension = AdjectiveEndings.Declension.MIXED,
            case = ArticleForms.Case.GENITIVE,
            gender = ArticleForms.Gender.PLURAL,
            trigger = "mangels",
        ),
    )

    /**
     * Builds the drill items for German adjective endings.
     *
     * If saved [words] match any trigger word, the item ID incorporates
     * the word ID to link with user vocabulary.
     */
    fun items(
        words: Collection<Word> = emptyList(),
        genderLookup: ((String) -> String?)? = null,
    ): List<DrillItem> {
        val wordMap = words.associateBy { it.text.trim().lowercase() }

        return TEMPLATES.map { template ->
            val article = ArticleForms.form(template.kind, template.case, template.gender)
                ?: error("Missing article form for ${template.kind} ${template.case} ${template.gender}")

            val prompt = template.pattern
                .replace("{Art}", article.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() })
                .replace("{art}", article)

            val answer = AdjectiveEndings.ending(template.declension, template.case, template.gender)

            val choices = template.explicitChoices ?: defaultChoices(answer)

            val hint = template.explicitHint ?: "${template.declension.displayName}: ${template.case.displayName}"

            val matchingWord = if (template.trigger.isNotBlank()) wordMap[template.trigger.lowercase()] else null
            val idKey = if (matchingWord != null) {
                "${matchingWord.id}:${template.key}"
            } else {
                "adj:${template.key}"
            }

            DrillItem(
                id = DrillItemId(DrillType.ADJECTIVE_ENDINGS, idKey),
                prompt = prompt,
                answer = answer,
                choices = choices,
                hint = hint,
            )
        }
    }
}
