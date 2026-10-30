package app.olauncher.data.store

/**
 * Drill content for German case endings after articles.
 *
 * Items are short sentences where the case is determined by a verb, preposition,
 * or grammatical subject function.
 */
object DrillCaseEndings {

    internal data class CaseEndingTemplate(
        val key: String,
        val prompt: String,
        val trigger: String,
        val case: ArticleForms.Case,
        val gender: ArticleForms.Gender,
        val kind: ArticleForms.Kind = ArticleForms.Kind.DEFINITE,
        val explicitChoices: List<String>? = null,
        val explicitHint: String? = null,
    )

    private fun defaultChoices(
        kind: ArticleForms.Kind,
        gender: ArticleForms.Gender,
        answer: String,
    ): List<String> = when (kind) {
        ArticleForms.Kind.DEFINITE -> when (gender) {
            ArticleForms.Gender.MASCULINE -> listOf("der", "den", "dem", "des")
            ArticleForms.Gender.NEUTER -> listOf("das", "den", "dem", "des")
            ArticleForms.Gender.FEMININE -> listOf("die", "der", "den", "dem")
            ArticleForms.Gender.PLURAL -> listOf("die", "den", "der", "dem")
        }
        ArticleForms.Kind.INDEFINITE -> when (gender) {
            ArticleForms.Gender.MASCULINE -> listOf("ein", "einen", "einem", "eines")
            ArticleForms.Gender.NEUTER -> listOf("ein", "einem", "eines", "einen")
            ArticleForms.Gender.FEMININE -> listOf("eine", "einer", "einem", "einen")
            ArticleForms.Gender.PLURAL -> listOf("ein", "eine", "einen", "einem")
        }
        ArticleForms.Kind.KEIN -> when (gender) {
            ArticleForms.Gender.MASCULINE -> listOf("kein", "keinen", "keinem", "keines")
            ArticleForms.Gender.NEUTER -> listOf("kein", "keinem", "keines", "keinen")
            ArticleForms.Gender.FEMININE -> listOf("keine", "keiner", "keinem", "keinen")
            ArticleForms.Gender.PLURAL -> listOf("keine", "keinen", "keiner", "keinem")
        }
    }.let { list ->
        if (answer !in list) (listOf(answer) + list).distinct().take(4) else list
    }

    internal val TEMPLATES: List<CaseEndingTemplate> = listOf(
        // ==========================================
        // 1. Nominativ (12 items)
        // ==========================================
        // Maskulin (der)
        CaseEndingTemplate(
            key = "nom_m_lehrer",
            prompt = "Dort steht ${DrillItem.GAP_MARKER} Lehrer.",
            trigger = "Subjekt",
            case = ArticleForms.Case.NOMINATIVE,
            gender = ArticleForms.Gender.MASCULINE,
        ),
        CaseEndingTemplate(
            key = "nom_m_tisch",
            prompt = "Auf dem Tisch liegt ${DrillItem.GAP_MARKER} Roman von Paul.",
            trigger = "Subjekt",
            case = ArticleForms.Case.NOMINATIVE,
            gender = ArticleForms.Gender.MASCULINE,
        ),
        CaseEndingTemplate(
            key = "nom_m_hund",
            prompt = "Hier schläft ${DrillItem.GAP_MARKER} Hund.",
            trigger = "Subjekt",
            case = ArticleForms.Case.NOMINATIVE,
            gender = ArticleForms.Gender.MASCULINE,
        ),
        // Feminin (die)
        CaseEndingTemplate(
            key = "nom_f_frau",
            prompt = "Dort wartet ${DrillItem.GAP_MARKER} Frau.",
            trigger = "Subjekt",
            case = ArticleForms.Case.NOMINATIVE,
            gender = ArticleForms.Gender.FEMININE,
        ),
        CaseEndingTemplate(
            key = "nom_f_loesung",
            prompt = "Das ist ${DrillItem.GAP_MARKER} beste Lösung.",
            trigger = "sein",
            case = ArticleForms.Case.NOMINATIVE,
            gender = ArticleForms.Gender.FEMININE,
        ),
        CaseEndingTemplate(
            key = "nom_f_aerztin",
            prompt = "Hier arbeitet ${DrillItem.GAP_MARKER} Ärztin.",
            trigger = "Subjekt",
            case = ArticleForms.Case.NOMINATIVE,
            gender = ArticleForms.Gender.FEMININE,
        ),
        // Neutrum (das)
        CaseEndingTemplate(
            key = "nom_n_buch",
            prompt = "Hier liegt ${DrillItem.GAP_MARKER} Buch.",
            trigger = "Subjekt",
            case = ArticleForms.Case.NOMINATIVE,
            gender = ArticleForms.Gender.NEUTER,
        ),
        CaseEndingTemplate(
            key = "nom_n_auto",
            prompt = "${DrillItem.GAP_MARKER} neue Auto steht in der Garage.",
            trigger = "Subjekt",
            case = ArticleForms.Case.NOMINATIVE,
            gender = ArticleForms.Gender.NEUTER,
        ),
        CaseEndingTemplate(
            key = "nom_n_kind",
            prompt = "Dort spielt ${DrillItem.GAP_MARKER} Kind.",
            trigger = "Subjekt",
            case = ArticleForms.Case.NOMINATIVE,
            gender = ArticleForms.Gender.NEUTER,
        ),
        // Plural (die)
        CaseEndingTemplate(
            key = "nom_pl_kinder",
            prompt = "Dort stehen ${DrillItem.GAP_MARKER} Kinder.",
            trigger = "Subjekt",
            case = ArticleForms.Case.NOMINATIVE,
            gender = ArticleForms.Gender.PLURAL,
        ),
        CaseEndingTemplate(
            key = "nom_pl_freunde",
            prompt = "Hier treffen sich ${DrillItem.GAP_MARKER} besten Freunde.",
            trigger = "Subjekt",
            case = ArticleForms.Case.NOMINATIVE,
            gender = ArticleForms.Gender.PLURAL,
        ),
        CaseEndingTemplate(
            key = "nom_pl_studenten",
            prompt = "Hier wohnen ${DrillItem.GAP_MARKER} Studenten.",
            trigger = "Subjekt",
            case = ArticleForms.Case.NOMINATIVE,
            gender = ArticleForms.Gender.PLURAL,
        ),

        // ==========================================
        // 2. Akkusativ (14 items)
        // ==========================================
        // Maskulin (den)
        CaseEndingTemplate(
            key = "akk_m_sehen_hund",
            prompt = "Ich sehe ${DrillItem.GAP_MARKER} Hund.",
            trigger = "sehen",
            case = ArticleForms.Case.ACCUSATIVE,
            gender = ArticleForms.Gender.MASCULINE,
        ),
        CaseEndingTemplate(
            key = "akk_m_kaufen_wagen",
            prompt = "Er kauft ${DrillItem.GAP_MARKER} Wagen.",
            trigger = "kaufen",
            case = ArticleForms.Case.ACCUSATIVE,
            gender = ArticleForms.Gender.MASCULINE,
        ),
        CaseEndingTemplate(
            key = "akk_m_besuchen_grossvater",
            prompt = "Wir besuchen ${DrillItem.GAP_MARKER} Großvater.",
            trigger = "besuchen",
            case = ArticleForms.Case.ACCUSATIVE,
            gender = ArticleForms.Gender.MASCULINE,
        ),
        CaseEndingTemplate(
            key = "akk_m_fuer_vater",
            prompt = "Das Geschenk ist für ${DrillItem.GAP_MARKER} Vater.",
            trigger = "für",
            case = ArticleForms.Case.ACCUSATIVE,
            gender = ArticleForms.Gender.MASCULINE,
        ),
        CaseEndingTemplate(
            key = "akk_m_ohne_schirm",
            prompt = "Er geht ohne ${DrillItem.GAP_MARKER} Schirm aus dem Haus.",
            trigger = "ohne",
            case = ArticleForms.Case.ACCUSATIVE,
            gender = ArticleForms.Gender.MASCULINE,
        ),
        CaseEndingTemplate(
            key = "akk_m_durch_tunnel",
            prompt = "Wir gehen durch ${DrillItem.GAP_MARKER} Tunnel.",
            trigger = "durch",
            case = ArticleForms.Case.ACCUSATIVE,
            gender = ArticleForms.Gender.MASCULINE,
        ),
        // Feminin (die)
        CaseEndingTemplate(
            key = "akk_f_kennen_frau",
            prompt = "Ich kenne ${DrillItem.GAP_MARKER} Frau schon lange.",
            trigger = "kennen",
            case = ArticleForms.Case.ACCUSATIVE,
            gender = ArticleForms.Gender.FEMININE,
        ),
        CaseEndingTemplate(
            key = "akk_f_lesen_zeitung",
            prompt = "Er liest ${DrillItem.GAP_MARKER} Zeitung jeden Morgen.",
            trigger = "lesen",
            case = ArticleForms.Case.ACCUSATIVE,
            gender = ArticleForms.Gender.FEMININE,
        ),
        CaseEndingTemplate(
            key = "akk_f_gegen_grippe",
            prompt = "Wir kämpfen gegen ${DrillItem.GAP_MARKER} Grippe.",
            trigger = "gegen",
            case = ArticleForms.Case.ACCUSATIVE,
            gender = ArticleForms.Gender.FEMININE,
        ),
        // Neutrum (das)
        CaseEndingTemplate(
            key = "akk_n_oeffnen_fenster",
            prompt = "Sie öffnet ${DrillItem.GAP_MARKER} Fenster.",
            trigger = "öffnen",
            case = ArticleForms.Case.ACCUSATIVE,
            gender = ArticleForms.Gender.NEUTER,
        ),
        CaseEndingTemplate(
            key = "akk_n_trinken_wasser",
            prompt = "Ich trinke ${DrillItem.GAP_MARKER} Wasser gern.",
            trigger = "trinken",
            case = ArticleForms.Case.ACCUSATIVE,
            gender = ArticleForms.Gender.NEUTER,
        ),
        CaseEndingTemplate(
            key = "akk_n_um_schloss",
            prompt = "Wir fahren um ${DrillItem.GAP_MARKER} Schloss.",
            trigger = "um",
            case = ArticleForms.Case.ACCUSATIVE,
            gender = ArticleForms.Gender.NEUTER,
        ),
        // Plural (die)
        CaseEndingTemplate(
            key = "akk_pl_zaehlen_tage",
            prompt = "Er zählt ${DrillItem.GAP_MARKER} Tage bis zum Urlaub.",
            trigger = "zählen",
            case = ArticleForms.Case.ACCUSATIVE,
            gender = ArticleForms.Gender.PLURAL,
        ),
        CaseEndingTemplate(
            key = "akk_pl_fuer_grosseltern",
            prompt = "Diese Blumen sind für ${DrillItem.GAP_MARKER} Großeltern.",
            trigger = "für",
            case = ArticleForms.Case.ACCUSATIVE,
            gender = ArticleForms.Gender.PLURAL,
        ),

        // ==========================================
        // 3. Dativ (14 items)
        // ==========================================
        // Maskulin (dem)
        CaseEndingTemplate(
            key = "dat_m_mit_bus",
            prompt = "Ich fahre mit ${DrillItem.GAP_MARKER} Bus zur Arbeit.",
            trigger = "mit",
            case = ArticleForms.Case.DATIVE,
            gender = ArticleForms.Gender.MASCULINE,
        ),
        CaseEndingTemplate(
            key = "dat_m_nach_unterricht",
            prompt = "Nach ${DrillItem.GAP_MARKER} Unterricht gehen wir essen.",
            trigger = "nach",
            case = ArticleForms.Case.DATIVE,
            gender = ArticleForms.Gender.MASCULINE,
        ),
        CaseEndingTemplate(
            key = "dat_m_bei_onkel",
            prompt = "Er wohnt bei ${DrillItem.GAP_MARKER} Onkel.",
            trigger = "bei",
            case = ArticleForms.Case.DATIVE,
            gender = ArticleForms.Gender.MASCULINE,
        ),
        CaseEndingTemplate(
            key = "dat_m_helfen_mann",
            prompt = "Ich helfe ${DrillItem.GAP_MARKER} Mann gerne.",
            trigger = "helfen",
            case = ArticleForms.Case.DATIVE,
            gender = ArticleForms.Gender.MASCULINE,
        ),
        CaseEndingTemplate(
            key = "dat_m_danken_arzt",
            prompt = "Wir danken ${DrillItem.GAP_MARKER} Arzt für die Hilfe.",
            trigger = "danken",
            case = ArticleForms.Case.DATIVE,
            gender = ArticleForms.Gender.MASCULINE,
        ),
        // Feminin (der)
        CaseEndingTemplate(
            key = "dat_f_schmecken_mutter",
            prompt = "Die Suppe schmeckt ${DrillItem.GAP_MARKER} Mutter gut.",
            trigger = "schmecken",
            case = ArticleForms.Case.DATIVE,
            gender = ArticleForms.Gender.FEMININE,
        ),
        CaseEndingTemplate(
            key = "dat_f_gefallen_frau",
            prompt = "Das Kleid gefällt ${DrillItem.GAP_MARKER} Frau sehr.",
            trigger = "gefallen",
            case = ArticleForms.Case.DATIVE,
            gender = ArticleForms.Gender.FEMININE,
        ),
        CaseEndingTemplate(
            key = "dat_f_aus_schule",
            prompt = "Sie kommt aus ${DrillItem.GAP_MARKER} Schule.",
            trigger = "aus",
            case = ArticleForms.Case.DATIVE,
            gender = ArticleForms.Gender.FEMININE,
        ),
        CaseEndingTemplate(
            key = "dat_f_zu_post",
            prompt = "Er geht zu ${DrillItem.GAP_MARKER} Post.",
            trigger = "zu",
            case = ArticleForms.Case.DATIVE,
            gender = ArticleForms.Gender.FEMININE,
        ),
        // Neutrum (dem)
        CaseEndingTemplate(
            key = "dat_n_mit_kind",
            prompt = "Ich spreche mit ${DrillItem.GAP_MARKER} Kind.",
            trigger = "mit",
            case = ArticleForms.Case.DATIVE,
            gender = ArticleForms.Gender.NEUTER,
        ),
        CaseEndingTemplate(
            key = "dat_n_von_konzert",
            prompt = "Sie kommt von ${DrillItem.GAP_MARKER} Konzert.",
            trigger = "von",
            case = ArticleForms.Case.DATIVE,
            gender = ArticleForms.Gender.NEUTER,
        ),
        CaseEndingTemplate(
            key = "dat_n_gehoeren_maedchen",
            prompt = "Das Buch gehört ${DrillItem.GAP_MARKER} Mädchen.",
            trigger = "gehören",
            case = ArticleForms.Case.DATIVE,
            gender = ArticleForms.Gender.NEUTER,
        ),
        // Plural (den)
        CaseEndingTemplate(
            key = "dat_pl_mit_kollegen",
            prompt = "Er spricht mit ${DrillItem.GAP_MARKER} Kollegen.",
            trigger = "mit",
            case = ArticleForms.Case.DATIVE,
            gender = ArticleForms.Gender.PLURAL,
        ),
        CaseEndingTemplate(
            key = "dat_pl_antworten_schuelern",
            prompt = "Der Lehrer antwortet ${DrillItem.GAP_MARKER} Schülern.",
            trigger = "antworten",
            case = ArticleForms.Case.DATIVE,
            gender = ArticleForms.Gender.PLURAL,
        ),

        // ==========================================
        // 4. Genitiv (12 items)
        //
        // Triggers are deliberately varied so no trigger is dense enough to
        // repeat within a shuffled round (see DrillContentAuditTest).
        // ==========================================
        // Maskulin (des)
        CaseEndingTemplate(
            key = "gen_m_trotz_regens",
            prompt = "Trotz ${DrillItem.GAP_MARKER} Regens gehen wir spazieren.",
            trigger = "trotz",
            case = ArticleForms.Case.GENITIVE,
            gender = ArticleForms.Gender.MASCULINE,
        ),
        CaseEndingTemplate(
            key = "gen_m_wegen_streiks",
            prompt = "Wegen ${DrillItem.GAP_MARKER} Streiks fährt heute kein Zug.",
            trigger = "wegen",
            case = ArticleForms.Case.GENITIVE,
            gender = ArticleForms.Gender.MASCULINE,
        ),
        // Neutrum (des)
        CaseEndingTemplate(
            key = "gen_n_waehrend_konzerts",
            prompt = "Während ${DrillItem.GAP_MARKER} Konzerts war es ruhig.",
            trigger = "während",
            case = ArticleForms.Case.GENITIVE,
            gender = ArticleForms.Gender.NEUTER,
        ),
        // Maskulin (des)
        CaseEndingTemplate(
            key = "gen_m_anstatt_kaffees",
            prompt = "Anstatt ${DrillItem.GAP_MARKER} Kaffees trinkt er Tee.",
            trigger = "anstatt",
            case = ArticleForms.Case.GENITIVE,
            gender = ArticleForms.Gender.MASCULINE,
        ),
        // Feminin (der)
        CaseEndingTemplate(
            key = "gen_f_ausserhalb_stadt",
            prompt = "Außerhalb ${DrillItem.GAP_MARKER} Stadt gibt es kaum Parkplätze.",
            trigger = "außerhalb",
            case = ArticleForms.Case.GENITIVE,
            gender = ArticleForms.Gender.FEMININE,
        ),
        CaseEndingTemplate(
            key = "gen_f_innerhalb_woche",
            prompt = "Innerhalb ${DrillItem.GAP_MARKER} Woche war alles erledigt.",
            trigger = "innerhalb",
            case = ArticleForms.Case.GENITIVE,
            gender = ArticleForms.Gender.FEMININE,
        ),
        CaseEndingTemplate(
            key = "gen_f_anlaesslich_feier",
            prompt = "Anlässlich ${DrillItem.GAP_MARKER} Feier backen wir Kuchen.",
            trigger = "anlässlich",
            case = ArticleForms.Case.GENITIVE,
            gender = ArticleForms.Gender.FEMININE,
        ),
        // Neutrum (des)
        CaseEndingTemplate(
            key = "gen_n_unweit_bahnhofs",
            prompt = "Unweit ${DrillItem.GAP_MARKER} Bahnhofs liegt ein kleines Café.",
            trigger = "unweit",
            case = ArticleForms.Case.GENITIVE,
            gender = ArticleForms.Gender.MASCULINE,
        ),
        CaseEndingTemplate(
            key = "gen_n_jenseits_dorfes",
            prompt = "Jenseits ${DrillItem.GAP_MARKER} Dorfes liegt eine kleine Kirche.",
            trigger = "jenseits",
            case = ArticleForms.Case.GENITIVE,
            gender = ArticleForms.Gender.NEUTER,
        ),
        // Maskulin (des)
        CaseEndingTemplate(
            key = "gen_m_infolge_unfalls",
            prompt = "Infolge ${DrillItem.GAP_MARKER} Unfalls wurde die Straße gesperrt.",
            trigger = "infolge",
            case = ArticleForms.Case.GENITIVE,
            gender = ArticleForms.Gender.MASCULINE,
        ),
        // Plural (der)
        CaseEndingTemplate(
            key = "gen_pl_trotz_probleme",
            prompt = "Trotz ${DrillItem.GAP_MARKER} Probleme geben wir nicht auf.",
            trigger = "trotz",
            case = ArticleForms.Case.GENITIVE,
            gender = ArticleForms.Gender.PLURAL,
        ),
        CaseEndingTemplate(
            key = "gen_pl_wegen_bauarbeiten",
            prompt = "Wegen ${DrillItem.GAP_MARKER} Bauarbeiten ist die Bahn langsam.",
            trigger = "wegen",
            case = ArticleForms.Case.GENITIVE,
            gender = ArticleForms.Gender.PLURAL,
        ),
    )

    /**
     * Builds the drill items for German case endings.
     *
     * If saved [words] match any trigger word or noun, the item ID incorporates
     * the word ID to link with user vocabulary. Saved-word items match no authored
     * help item, so they carry no rule id (the help card stays hidden for them).
     */
    fun items(
        words: Collection<Word> = emptyList(),
        genderLookup: ((String) -> String?)? = null,
    ): List<DrillItem> {
        val wordMap = words.associateBy { it.text.trim().lowercase() }

        return TEMPLATES.map { template ->
            val answer = ArticleForms.form(template.kind, template.case, template.gender)
                ?: error("Missing article form for ${template.kind} ${template.case} ${template.gender}")

            val choices = template.explicitChoices
                ?: defaultChoices(template.kind, template.gender, answer)

            val hint = template.explicitHint ?: when {
                template.trigger == "Subjekt" -> "Subjekt: ${template.case.displayName}"
                else -> "${template.trigger} + ${template.case.displayName}"
            }

            val matchingWord = wordMap[template.trigger.lowercase()]
            val idKey = if (matchingWord != null) {
                "${matchingWord.id}:${template.key}"
            } else {
                "case:${template.key}"
            }

            val content = if (matchingWord == null) CASE_ENDING_RULE_CONTENT[template.key] else null
            val ruleId = content?.ruleId
                ?.let { DrillHelpContent.requireValidRuleId(it, "case endings item ${template.key}") }

            DrillItem(
                id = DrillItemId(DrillType.CASE_ENDINGS, idKey),
                prompt = template.prompt,
                answer = answer,
                choices = choices,
                hint = hint,
                ruleId = ruleId,
                missByChoice = content?.missByChoice ?: emptyMap(),
                helpCell = helpCell(ruleId, template.case, template.gender),
            )
        }
    }
}
