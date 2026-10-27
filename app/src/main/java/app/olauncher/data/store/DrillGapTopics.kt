package app.olauncher.data.store

/**
 * Item data for the 11 gap topics (#651), transcribed from
 * docs/design/drill-content/items/ (the reviewed #654 data, including its review fixes).
 *
 * Each item points to a help-card rule id in [DrillHelpContent]; the rule assignment
 * follows the coverage tables in docs/design/drill-content/, so every item maps
 * to exactly one rule and every rule text in code comes from the content files.
 *
 * [GapItemTemplate.note] is the item's miss reason: why the answer is right, shown in
 * the ✗ miss line after a wrong tap (design §3).
 *
 * One hint override: `werden` item 4 ("Wir ___ nächste Woche …", answer werden) cannot
 * use its rule's cue, because the cue itself contains the helper's infinitive "werden".
 * It falls back to the rule's flashcard-and-miss hint, which names no form.
 */
/** One gap-topic item, exactly as authored in docs/design/drill-content/items/. */
internal data class GapItemTemplate(
    val key: String,
    val prompt: String,
    val answer: String,
    val choices: List<String>,
    val ruleId: String,
    val note: String,
    val hintOverride: String? = null,
)

object DrillGapTopics {

    /** Builds the [DrillItem]s of one gap topic; items keep the authored order. */
    internal fun itemsOf(type: DrillType, templates: List<GapItemTemplate>): List<DrillItem> =
        templates.map { t ->
            val rule = DrillHelpContent.rule(t.ruleId)
                ?: error("Unknown help rule id ${t.ruleId} for gap item ${type.id}:${t.key}")
            DrillItem(
                id = DrillItemId(type, "gap:${t.key}"),
                prompt = t.prompt,
                answer = t.answer,
                choices = t.choices,
                hint = t.hintOverride ?: rule.cue,
                ruleId = t.ruleId,
                missReason = t.note,
            )
        }

    /** Builds the items of a gap topic, or null when [type] is not one. */
    fun items(type: DrillType): List<DrillItem>? = when (type) {
        DrillType.PASSIVE -> DrillPassive.items()
        DrillType.KONJUNKTIV_II -> DrillKonjunktivIi.items()
        DrillType.RELATIVE_PRONOUNS -> DrillRelativePronouns.items()
        DrillType.GENITIVE -> DrillGenitive.items()
        DrillType.N_DECLENSION -> DrillNDeclension.items()
        DrillType.IMPERATIVE -> DrillImperative.items()
        DrillType.WERDEN -> DrillWerden.items()
        DrillType.PRAETERITUM -> DrillPraeteritum.items()
        DrillType.INFINITIV_ZU -> DrillInfinitivZu.items()
        DrillType.MODAL_VERBS -> DrillModalVerbs.items()
        DrillType.KONJUNKTIV_I -> DrillKonjunktivI.items()
        else -> null
    }

    /** True when [type] is one of the #651 gap topics. */
    fun isGapTopic(type: DrillType): Boolean = items(type) != null

    /** All gap topics with their items, in DrillType order. */
    fun allTopics(): List<Pair<DrillType, List<DrillItem>>> = listOf(
        DrillType.PASSIVE, DrillType.KONJUNKTIV_II, DrillType.RELATIVE_PRONOUNS,
        DrillType.GENITIVE, DrillType.N_DECLENSION, DrillType.IMPERATIVE,
        DrillType.WERDEN, DrillType.PRAETERITUM, DrillType.INFINITIV_ZU,
        DrillType.MODAL_VERBS, DrillType.KONJUNKTIV_I,
    ).map { it to items(it).orEmpty() }
}

/** Passive (10 items, docs/design/drill-content/items/passive.md). */
object DrillPassive {
    private val TEMPLATES = listOf(
        GapItemTemplate(
            key = "1", prompt = "Das Auto ___ gerade repariert.", answer = "wird",
            choices = listOf("wird", "werde", "werden", "wurden"), ruleId = "passiv-aux-form",
            note = "\"gerade\" signals an action in progress, so the process passive needs werden " +
                "in the 3rd person singular present.",
        ),
        GapItemTemplate(
            key = "2", prompt = "Die Bücher ___ oft gelesen.", answer = "werden",
            choices = listOf("werden", "wird", "wurde", "worden"), ruleId = "passiv-aux-form",
            note = "A plural subject in the present tense passive takes werden, not wird.",
        ),
        GapItemTemplate(
            key = "3", prompt = "Der Brief ___ gestern von meiner Schwester geschrieben.", answer = "wurde",
            choices = listOf("wurde", "wird", "war", "worden"), ruleId = "passiv-aux-form",
            note = "\"gestern\" points to the past, so werden becomes wurde (simple past, singular).",
        ),
        GapItemTemplate(
            key = "4", prompt = "Die Tiere ___ letzte Woche vom Tierarzt untersucht.", answer = "wurden",
            choices = listOf("wurden", "wurde", "werden", "worden"), ruleId = "passiv-aux-form",
            note = "Past time marker plus a plural subject gives wurden, the plural of wurde.",
        ),
        GapItemTemplate(
            key = "5", prompt = "Die Wohnung ___ schon verkauft worden.", answer = "ist",
            choices = listOf("ist", "hat", "sind", "wird"), ruleId = "passiv-perfekt-sein",
            note = "The perfect passive is built with sein, never haben, in front of Partizip II + worden.",
        ),
        GapItemTemplate(
            key = "6", prompt = "Die Blumen ___ bereits gegossen worden.", answer = "sind",
            choices = listOf("sind", "haben", "wurden", "werden"), ruleId = "passiv-perfekt-sein",
            note = "Same perfect passive as singular, but the plural subject makes it sind.",
        ),
        GapItemTemplate(
            key = "7", prompt = "Die Suppe ist schon gekocht ___.", answer = "worden",
            choices = listOf("worden", "geworden", "werden", "war"), ruleId = "passiv-perfekt-partizip",
            note = "In the perfect passive werden takes the special form worden, not its normal " +
                "participle geworden.",
        ),
        GapItemTemplate(
            key = "8", prompt = "Der Text wird ___ dem Lehrer korrigiert.", answer = "von",
            choices = listOf("von", "mit", "durch", "bei"), ruleId = "passiv-agent",
            note = "The person doing the action is introduced with von plus dative, not with a " +
                "preposition of means or place.",
        ),
        GapItemTemplate(
            key = "9", prompt = "Das Lied wird von ___ Sängerin gesungen.", answer = "der",
            choices = listOf("der", "die", "dem", "den"), ruleId = "prep-fixed-dat",
            note = "von takes the dative, and the feminine dative article is der.",
        ),
        GapItemTemplate(
            key = "10", prompt = "Das Geschirr wird jetzt ___.", answer = "gewaschen",
            choices = listOf("gewaschen", "wäscht", "waschen", "gewaschte"), ruleId = "passiv-partizip-ii",
            note = "After wird the action word stands as Partizip II, never as a conjugated or " +
                "infinitive form.",
        ),
    )

    fun items(): List<DrillItem> =
        DrillGapTopics.itemsOf(DrillType.PASSIVE, TEMPLATES)
}

/** Konjunktiv II (10 items, docs/design/drill-content/items/konjunktiv-ii.md). */
object DrillKonjunktivIi {
    private val TEMPLATES = listOf(
        GapItemTemplate(
            key = "1", prompt = "Wenn ich Zeit ___, käme ich mit.", answer = "hätte",
            choices = listOf("hätte", "würde", "war", "habe"), ruleId = "konj2-wenn-unreal",
            note = "In an unreal \"wenn\" clause with haben, you need the Konjunktiv II form " +
                "\"hätte\"; \"habe\" is real present and \"war\" is past.",
        ),
        GapItemTemplate(
            key = "2", prompt = "Wenn ich reich ___, würde ich um die Welt reisen.", answer = "wäre",
            choices = listOf("wäre", "hätte", "bin", "würde"), ruleId = "konj2-wenn-unreal",
            note = "With \"sein\", the Konjunktiv II form is \"wäre\"; \"bin\" states a real fact, " +
                "which the unreal sentence forbids.",
        ),
        GapItemTemplate(
            key = "3", prompt = "Wenn ich du ___, würde ich das Angebot annehmen.", answer = "wäre",
            choices = listOf("wäre", "wärst", "hätte", "bin"), ruleId = "konj2-subject-agreement",
            note = "The verb agrees with \"ich\", so it is \"wäre\", not the du-form \"wärst\".",
        ),
        GapItemTemplate(
            key = "4", prompt = "Ich ___ gern wissen, wie spät es ist.", answer = "würde",
            choices = listOf("würde", "hätte", "war", "habe"), ruleId = "konj2-helper-infinitive",
            note = "For wishes with verbs other than haben and sein, German uses \"würde\" plus " +
                "the infinitive.",
        ),
        GapItemTemplate(
            key = "5", prompt = "Wenn du mir ___ würdest, wäre ich sehr dankbar.", answer = "helfen",
            choices = listOf("helfen", "hilfst", "geholfen", "half"), ruleId = "konj2-helper-infinitive",
            note = "\"würde\" is always followed by the plain infinitive, never by a finite or " +
                "participle form.",
        ),
        GapItemTemplate(
            key = "6", prompt = "Ich ___ gern mitgekommen, aber ich musste arbeiten.", answer = "wäre",
            choices = listOf("wäre", "hatte", "wurde", "bin"), ruleId = "konj2-past-wish",
            note = "\"wäre gern\" plus the participle expresses a wish about the past that did " +
                "not happen.",
        ),
        GapItemTemplate(
            key = "7", prompt = "Wenn ihr mehr Geld ___, würdet ihr ein neues Auto kaufen.", answer = "hättet",
            choices = listOf("hättet", "hätte", "hätten", "habt"), ruleId = "konj2-subject-agreement",
            note = "The form must match \"ihr\": \"hättet\"; \"hätte\" is for ich/er and \"hätten\" " +
                "for wir/sie.",
        ),
        GapItemTemplate(
            key = "8", prompt = "Ich ___ gern, wo sie wohnt.", answer = "wüsste",
            choices = listOf("wüsste", "weiß", "wusste", "würde"), ruleId = "konj2-single-form",
            note = "\"wissen\" has its own Konjunktiv II form \"wüsste\", used here for a polite, " +
                "soft curiosity.",
        ),
        GapItemTemplate(
            key = "9", prompt = "Ich ___ gern zu deiner Party, aber ich kann nicht.", answer = "käme",
            choices = listOf("käme", "komme", "kam", "würde"), ruleId = "konj2-single-form",
            note = "\"käme\" (Konjunktiv II of kommen) politely says you would come if you could; " +
                "\"komme\" states a real plan.",
        ),
        GapItemTemplate(
            key = "10", prompt = "Ich ___ gern einen Kaffee, bitte.", answer = "hätte",
            choices = listOf("hätte", "würde", "habe", "war"), ruleId = "konj2-polite-request",
            note = "\"hätte gern\" is the standard polite way to order or ask for something.",
        ),
    )

    fun items(): List<DrillItem> =
        DrillGapTopics.itemsOf(DrillType.KONJUNKTIV_II, TEMPLATES)
}

/** Relative pronouns (10 items, docs/design/drill-content/items/relative-pronouns.md). */
object DrillRelativePronouns {
    private val TEMPLATES = listOf(
        GapItemTemplate(
            key = "1", prompt = "Der Mann, ___ dort steht, ist mein Lehrer.", answer = "der",
            choices = listOf("der", "den", "dem", "dessen"), ruleId = "rel-agreement",
            note = "The relative pronoun refers to \"der Mann\" and is the subject of \"steht\", " +
                "so it takes masculine nominative: der.",
        ),
        GapItemTemplate(
            key = "2", prompt = "Die Frau, ___ ich gestern gesehen habe, ist Ärztin.", answer = "die",
            choices = listOf("die", "der", "den", "deren"), ruleId = "rel-agreement",
            note = "\"Die Frau\" is feminine and here the direct object of \"gesehen habe\", but " +
                "feminine accusative and nominative are both die.",
        ),
        GapItemTemplate(
            key = "3", prompt = "Das Buch, ___ ich gerade lese, ist sehr spannend.", answer = "das",
            choices = listOf("das", "dem", "den", "dessen"), ruleId = "rel-agreement",
            note = "\"Das Buch\" is neuter and the object of \"lese\", so the neuter accusative " +
                "form das fits.",
        ),
        GapItemTemplate(
            key = "4", prompt = "Der Film, ___ wir gestern gesehen haben, war langweilig.", answer = "den",
            choices = listOf("den", "der", "dem", "denen"), ruleId = "rel-agreement",
            note = "\"Der Film\" is masculine and the direct object of the relative clause, so " +
                "masculine accusative den is required.",
        ),
        GapItemTemplate(
            key = "5", prompt = "Der Mann, mit ___ ich spreche, ist mein Nachbar.", answer = "dem",
            choices = listOf("dem", "den", "der", "dessen"), ruleId = "rel-after-prep",
            note = "\"mit\" always takes the dative, and the masculine dative relative pronoun " +
                "is dem.",
        ),
        GapItemTemplate(
            key = "6", prompt = "Die Frau, von ___ ich viel gelernt habe, ist meine Chefin.", answer = "der",
            choices = listOf("der", "die", "den", "deren"), ruleId = "rel-after-prep",
            note = "\"von\" takes the dative, and for a feminine noun the dative relative " +
                "pronoun is der.",
        ),
        GapItemTemplate(
            key = "7", prompt = "Die Kinder, mit ___ sie im Park spielt, sind sehr nett.", answer = "denen",
            choices = listOf("denen", "den", "die", "deren"), ruleId = "rel-after-prep",
            note = "With a plural noun after a dative preposition like \"mit\", the relative " +
                "pronoun is the special form denen.",
        ),
        GapItemTemplate(
            key = "8", prompt = "Der Mann, ___ Auto in der Garage steht, ist Polizist.", answer = "dessen",
            choices = listOf("dessen", "dem", "den", "deren"), ruleId = "rel-genitive",
            note = "The gap shows possession (\"his car\"), so the genitive relative pronoun for " +
                "masculine/neuter is dessen.",
        ),
        GapItemTemplate(
            key = "9", prompt = "Die Bücher, ___ auf dem Tisch liegen, gehören mir.", answer = "die",
            choices = listOf("die", "den", "denen", "das"), ruleId = "rel-agreement",
            note = "\"Die Bücher\" is plural and the subject of \"liegen\", and the plural " +
                "nominative relative pronoun is die.",
        ),
        GapItemTemplate(
            key = "10", prompt = "Die Frau, ___ Sohn in meiner Klasse ist, kommt aus Köln.", answer = "deren",
            choices = listOf("deren", "die", "der", "denen"), ruleId = "rel-genitive",
            note = "The gap expresses possession for a feminine noun (\"her son\"), so the " +
                "genitive form is deren.",
        ),
    )

    fun items(): List<DrillItem> =
        DrillGapTopics.itemsOf(DrillType.RELATIVE_PRONOUNS, TEMPLATES)
}

/** Genitive (10 items, docs/design/drill-content/items/genitive.md). */
object DrillGenitive {
    private val TEMPLATES = listOf(
        GapItemTemplate(
            key = "1", prompt = "Das Auto ___ Vaters ist neu.", answer = "des",
            choices = listOf("des", "der", "dem", "den"), ruleId = "gen-article",
            note = "\"Vater\" is masculine, so the genitive article is \"des\".",
        ),
        GapItemTemplate(
            key = "2", prompt = "Das ist die Tasche ___ Mutter.", answer = "der",
            choices = listOf("der", "des", "dem", "die"), ruleId = "gen-article",
            note = "Feminine nouns take \"der\" in the genitive, and the noun itself gets no ending.",
        ),
        GapItemTemplate(
            key = "3", prompt = "Die Farbe ___ Blumen ist rot.", answer = "der",
            choices = listOf("der", "des", "die", "dem"), ruleId = "gen-article",
            note = "Plural nouns also take \"der\" in the genitive, not \"des\".",
        ),
        GapItemTemplate(
            key = "4", prompt = "Ich lese das Buch des ___.", answer = "Kindes",
            choices = listOf("Kindes", "Kinde", "Kind", "Kindern"), ruleId = "gen-noun-s",
            note = "Neuter \"Kind\" takes the fuller -es ending in the genitive.",
        ),
        GapItemTemplate(
            key = "5", prompt = "Das Ende des ___ war spannend.", answer = "Films",
            choices = listOf("Films", "Filme", "Filmen", "Film"), ruleId = "gen-noun-s",
            note = "Masculine \"Film\" adds just -s in the genitive.",
        ),
        GapItemTemplate(
            key = "6", prompt = "Das ist der Ball des ___.", answer = "Jungen",
            choices = listOf("Jungen", "Junges", "Junge", "Jungens"), ruleId = "gen-noun-weak",
            note = "Weak masculine nouns like \"Junge\" take -n/-en instead of -s in the genitive.",
        ),
        GapItemTemplate(
            key = "7", prompt = "___ Tages erzähle ich dir alles.", answer = "Eines",
            choices = listOf("Eines", "Einem", "Einer", "Ein"), ruleId = "gen-time-phrase",
            note = "\"Eines Tages\" is a fixed genitive time phrase meaning \"one day\".",
        ),
        GapItemTemplate(
            key = "8", prompt = "___ Schlüssel ist das?", answer = "Wessen",
            choices = listOf("Wessen", "Wer", "Wen", "Wem"), ruleId = "gen-question-whose",
            note = "\"Wessen\" is the genitive question word for \"whose\".",
        ),
        GapItemTemplate(
            key = "9", prompt = "___ altes Fahrrad steht in der Garage.", answer = "Vaters",
            choices = listOf("Vaters", "Vater", "Väter", "Vatern"), ruleId = "gen-kinship-s",
            note = "Male kinship nouns can show possession with bare -s and no article.",
        ),
        GapItemTemplate(
            key = "10", prompt = "Das sind die Bücher der ___.", answer = "Schüler",
            choices = listOf("Schüler", "Schülern", "Schülers", "Schüleres"), ruleId = "gen-noun-zero",
            note = "Genitive plural nouns stay unchanged; only the article becomes \"der\".",
        ),
    )

    fun items(): List<DrillItem> =
        DrillGapTopics.itemsOf(DrillType.GENITIVE, TEMPLATES)
}

/** N-declension (10 items, docs/design/drill-content/items/n-declension.md). */
object DrillNDeclension {
    private val TEMPLATES = listOf(
        GapItemTemplate(
            key = "1", prompt = "Der ___ spielt nach der Schule Fußball.", answer = "Junge",
            choices = listOf("Junge", "Jungen", "Jung", "Jungs"), ruleId = "ndecl-weak-en",
            note = "The noun is the subject (nominative singular), the one case where a weak " +
                "masculine noun takes no ending.",
        ),
        GapItemTemplate(
            key = "2", prompt = "Ich kenne den ___ aus dem Sprachkurs.", answer = "Studenten",
            choices = listOf("Student", "Studenten", "Studente", "Studentes"), ruleId = "ndecl-weak-en",
            note = "\"den\" is accusative, so the weak noun Student adds -en.",
        ),
        GapItemTemplate(
            key = "3", prompt = "Wir helfen dem ___ am Wochenende im Garten.", answer = "Nachbarn",
            choices = listOf("Nachbar", "Nachbars", "Nachbarn", "Nachbaren"), ruleId = "ndecl-weak-en",
            note = "\"helfen\" takes the dative, and dem Nachbar becomes Nachbarn with the added -n.",
        ),
        GapItemTemplate(
            key = "4", prompt = "Das ist das Auto des ___.", answer = "Kollegen",
            choices = listOf("Kollege", "Kollegen", "Kolleges", "Kolleg"), ruleId = "ndecl-weak-en",
            note = "Genitive with \"des\", so the weak noun Kollege takes -en instead of the " +
                "strong -s ending.",
        ),
        GapItemTemplate(
            key = "5", prompt = "Grüßen Sie bitte den ___ von mir.", answer = "Herrn",
            choices = listOf("Herr", "Herrn", "Herren", "Herrs"), ruleId = "ndecl-weak-en",
            note = "Herr adds only -n (den Herrn); Herren is the plural, not the accusative singular.",
        ),
        GapItemTemplate(
            key = "6", prompt = "Der Kellner bringt dem ___ das Essen.", answer = "Kunden",
            choices = listOf("Kunde", "Kunden", "Kunds", "Kundes"), ruleId = "ndecl-weak-en",
            note = "The waiter brings something to someone, so \"dem\" marks dative and Kunde " +
                "becomes Kunden.",
        ),
        GapItemTemplate(
            key = "7", prompt = "Die ___ warten an der Haltestelle auf den Bus.", answer = "Menschen",
            choices = listOf("Mensch", "Menschen", "Menschens", "Menscher"), ruleId = "ndecl-weak-en",
            note = "Weak nouns keep the -en in the plural too, even in the nominative.",
        ),
        GapItemTemplate(
            key = "8", prompt = "Im Zoo fotografiert sie den ___ hinter dem Baum.", answer = "Affen",
            choices = listOf("Affe", "Affen", "Aff", "Affes"), ruleId = "ndecl-weak-en",
            note = "\"den\" is accusative, so Affe gets the extra -n.",
        ),
        GapItemTemplate(
            key = "9", prompt = "Der Pfleger gibt dem ___ jeden Tag frisches Fleisch.", answer = "Löwen",
            choices = listOf("Löwe", "Löwen", "Löwes", "Löwin"), ruleId = "ndecl-weak-en",
            note = "\"geben\" takes a dative object, so dem Löwe becomes dem Löwen.",
        ),
        GapItemTemplate(
            key = "10", prompt = "Ich habe den ___ der neuen Kollegin wieder vergessen.", answer = "Namen",
            choices = listOf("Name", "Namen", "Namens", "Names"), ruleId = "ndecl-weak-en",
            note = "\"den\" is accusative, so Name becomes Namen; Namens would only fit the genitive.",
        ),
    )

    fun items(): List<DrillItem> =
        DrillGapTopics.itemsOf(DrillType.N_DECLENSION, TEMPLATES)
}

/** Imperative (10 items, docs/design/drill-content/items/imperative.md). */
object DrillImperative {
    private val TEMPLATES = listOf(
        GapItemTemplate(
            key = "1", prompt = "___ bitte die Tür zu, Paul!", answer = "Mach",
            choices = listOf("Mach", "Macht", "Machst", "Machen"), ruleId = "imp-person",
            note = "The du imperative drops the -st ending, and the separable prefix zu goes to " +
                "the end of the sentence.",
        ),
        GapItemTemplate(
            key = "2", prompt = "___ mir bitte das Salz!", answer = "Gib",
            choices = listOf("Gibst", "Gib", "Geb", "Gibt"), ruleId = "imp-du-vowel-change",
            note = "The du imperative of geben uses the changed stem gib and drops the ending " +
                "(no -st).",
        ),
        GapItemTemplate(
            key = "3", prompt = "___ die E-Mail noch einmal, Anna!", answer = "Lies",
            choices = listOf("Lies", "Lest", "Les", "Liest"), ruleId = "imp-du-vowel-change",
            note = "lesen changes e to ie, so the du imperative is Lies, not Les.",
        ),
        GapItemTemplate(
            key = "4", prompt = "___ eure Schuhe an!", answer = "Zieht",
            choices = listOf("Zieht", "Zieh", "Ziehen", "Ziehst"), ruleId = "imp-person",
            note = "The ihr imperative takes the stem plus -t, and the separable prefix an lands " +
                "at the end.",
        ),
        GapItemTemplate(
            key = "5", prompt = "___ bitte leise, Kinder, das Baby schläft!", answer = "Seid",
            choices = listOf("Seid", "Sei", "Sind", "Sein"), ruleId = "imp-sein",
            note = "The ihr imperative of sein is Seid (stem plus -t).",
        ),
        GapItemTemplate(
            key = "6", prompt = "___ bitte vorsichtig mit dem Glas, Max!", answer = "Sei",
            choices = listOf("Sei", "Bist", "Seid", "Seist"), ruleId = "imp-sein",
            note = "The du imperative of sein is Sei; it drops the verb ending entirely.",
        ),
        GapItemTemplate(
            key = "7", prompt = "___ Sie bitte Platz!", answer = "Nehmen",
            choices = listOf("Nehmen", "Nimm", "Nehmt", "Nimmt"), ruleId = "imp-sie-infinitive",
            note = "The Sie imperative uses the infinitive form, so Nehmen Sie means \"please take\".",
        ),
        GapItemTemplate(
            key = "8", prompt = "___ Sie bitte etwas Geduld!", answer = "Haben",
            choices = listOf("Haben", "Hab", "Habt", "Hast"), ruleId = "imp-sie-infinitive",
            note = "With Sie, the imperative of haben is the infinitive: Haben Sie Geduld.",
        ),
        GapItemTemplate(
            key = "9", prompt = "___ Sie das Formular bitte ganz aus!", answer = "Füllen",
            choices = listOf("Füllen", "Füll", "Füllt", "Füllst"), ruleId = "imp-sie-infinitive",
            note = "In the Sie imperative the verb stays in the infinitive form and the prefix " +
                "aus moves to the end.",
        ),
        GapItemTemplate(
            key = "10", prompt = "___ nicht so schnell, Kinder, wir haben Zeit!", answer = "Esst",
            choices = listOf("Esst", "Iss", "Isst", "Ess"), ruleId = "imp-person",
            note = "The ihr imperative keeps the normal stem and adds -t (Esst), while du would " +
                "change the vowel to Iss.",
        ),
    )

    fun items(): List<DrillItem> =
        DrillGapTopics.itemsOf(DrillType.IMPERATIVE, TEMPLATES)
}

/** werden (10 items, docs/design/drill-content/items/werden.md). */
object DrillWerden {
    private val TEMPLATES = listOf(
        GapItemTemplate(
            key = "1", prompt = "Er ___ Arzt.", answer = "wird",
            choices = listOf("wird", "werde", "wirst", "werdet"), ruleId = "werden-conjugation",
            note = "\"Er\" is third person singular, so werden takes the form \"wird\"; here it " +
                "is the full verb meaning \"to become\".",
        ),
        GapItemTemplate(
            key = "2", prompt = "Du ___ bestimmt müde.", answer = "wirst",
            choices = listOf("wirst", "werde", "wird", "werden"), ruleId = "werden-conjugation",
            note = "\"Du\" always takes \"wirst\"; werden is the full verb linking the subject to " +
                "the adjective \"müde\".",
        ),
        GapItemTemplate(
            key = "3", prompt = "Ich ___ dir morgen beim Umzug helfen.", answer = "werde",
            choices = listOf("werde", "wirst", "wird", "werden"), ruleId = "futur-helper-form",
            note = "This is the future tense: werden (here \"werde\" for ich) plus the infinitive " +
                "\"helfen\" at the end.",
        ),
        GapItemTemplate(
            key = "4", prompt = "Wir ___ nächste Woche nach Berlin ziehen.", answer = "werden",
            choices = listOf("werden", "werdet", "werde", "wirst"), ruleId = "futur-helper-form",
            note = "Future tense with \"wir\": werden conjugates as \"werden\", and the infinitive " +
                "\"ziehen\" goes to the end.",
            // The rule's cue names the helper's infinitive ("werden") — it all but gives
            // away these items' answers (werden / Werdet). Use the rule's post-answer
            // hint instead, which names no form.
            hintOverride = "An infinitive at the end (helfen, ziehen, kommen) is the future " +
                "marker; then match the subject.",
        ),
        GapItemTemplate(
            key = "5", prompt = "___ ihr am Samstag kommen?", answer = "Werdet",
            choices = listOf("Werdet", "Werden", "Wirst", "Wird"), ruleId = "futur-helper-form",
            note = "In a future-tense question the verb moves to the front; \"ihr\" takes " +
                "\"werdet\", giving \"Werdet ihr ... kommen?\".",
            hintOverride = "An infinitive at the end (helfen, ziehen, kommen) is the future " +
                "marker; then match the subject.",
        ),
        GapItemTemplate(
            key = "6", prompt = "Das Haus ___ im Moment renoviert.", answer = "wird",
            choices = listOf("wird", "werde", "werden", "wurde"), ruleId = "passiv-aux-form",
            note = "Passive voice: werden (\"wird\" for das Haus) plus the past participle " +
                "\"renoviert\" says the action is being done to the house.",
        ),
        GapItemTemplate(
            key = "7", prompt = "Das Auto ___ letzte Woche repariert.", answer = "wurde",
            choices = listOf("wurde", "wurden", "würde", "wird"), ruleId = "passiv-aux-form",
            note = "Past passive with a singular subject uses \"wurde\"; \"letzte Woche\" signals " +
                "the past.",
        ),
        GapItemTemplate(
            key = "8", prompt = "Die E-Mails ___ gestern verschickt.", answer = "wurden",
            choices = listOf("wurden", "wurde", "werden", "würden"), ruleId = "passiv-aux-form",
            note = "Past passive with a plural subject uses \"wurden\", the plural form of wurde.",
        ),
        GapItemTemplate(
            key = "9", prompt = "Das Problem ist heute gelöst ___.", answer = "worden",
            choices = listOf("worden", "geworden", "gewordet", "wurde"), ruleId = "passiv-perfekt-partizip",
            note = "In the passive perfect, werden's participle is \"worden\" (ist ... gelöst " +
                "worden); \"geworden\" is only used in active sentences.",
        ),
        GapItemTemplate(
            key = "10", prompt = "An deiner Stelle ___ ich das Angebot annehmen.", answer = "würde",
            choices = listOf("würde", "werde", "wirst", "wurde"), ruleId = "werden-subjunctive-ii",
            note = "This is subjunctive II: \"würde\" plus the infinitive expresses what you " +
                "would do; \"werde\" would wrongly state a real future plan.",
        ),
    )

    fun items(): List<DrillItem> =
        DrillGapTopics.itemsOf(DrillType.WERDEN, TEMPLATES)
}

/** Präteritum (10 items, docs/design/drill-content/items/praeteritum.md). */
object DrillPraeteritum {
    private val TEMPLATES = listOf(
        GapItemTemplate(
            key = "1", prompt = "Als Kind ___ ich oft krank.", answer = "war",
            choices = listOf("war", "warst", "waren", "hatte"), ruleId = "praet-sein",
            note = "Simple past of \"sein\" for \"ich\" is \"war\"; the other forms belong to " +
                "du/wir or to \"haben\".",
        ),
        GapItemTemplate(
            key = "2", prompt = "Gestern ___ ich keine Zeit für Sport.", answer = "hatte",
            choices = listOf("hatte", "habte", "haben", "hatten"), ruleId = "praet-haben",
            note = "\"haben\" has the irregular simple past \"hatte\" for \"ich\"; \"habte\" " +
                "wrongly treats it like a regular verb.",
        ),
        GapItemTemplate(
            key = "3", prompt = "Ihr ___ gestern sehr müde.", answer = "wart",
            choices = listOf("wart", "wartet", "waren", "war"), ruleId = "praet-sein",
            note = "\"sein\" in the simple past with \"ihr\" is \"wart\"; \"wartet\" would be the " +
                "regular-verb ending.",
        ),
        GapItemTemplate(
            key = "4", prompt = "Mit sechs Jahren ___ ich noch nicht schwimmen.", answer = "konnte",
            choices = listOf("konnte", "konntest", "konnten", "kann"), ruleId = "praet-modal",
            note = "Modals form the simple past regularly, so \"können\" becomes \"konnte\" for " +
                "\"ich\"; \"kann\" is present tense.",
        ),
        GapItemTemplate(
            key = "5", prompt = "Ich ___ gestern leider länger arbeiten.", answer = "musste",
            choices = listOf("musste", "mussten", "musstet", "muss"), ruleId = "praet-modal",
            note = "\"müssen\" drops its umlaut in the simple past, giving \"musste\" for \"ich\"; " +
                "\"muss\" is present tense.",
        ),
        GapItemTemplate(
            key = "6", prompt = "Als Kind ___ ich immer Eis zum Frühstück.", answer = "wollte",
            choices = listOf("wollte", "wollten", "wolltet", "will"), ruleId = "praet-modal",
            note = "\"wollen\" is a regular simple past \"wollte\" for \"ich\"; the other choices " +
                "are other persons or the present \"will\".",
        ),
        GapItemTemplate(
            key = "7", prompt = "Gestern ___ ich nur kurz beim Supermarkt vorbei.", answer = "ging",
            choices = listOf("ging", "gingst", "gingen", "gehe"), ruleId = "praet-strong-vowel",
            note = "\"gehen\" is a strong verb with the past stem \"ging\" for \"ich\"; \"gehe\" " +
                "is present tense.",
        ),
        GapItemTemplate(
            key = "8", prompt = "Anna ___ den Film schon letzte Woche.", answer = "sah",
            choices = listOf("sah", "sahen", "sieht", "seht"), ruleId = "praet-strong-vowel",
            note = "\"sehen\" changes its vowel in the simple past, \"ich/sie sah\"; \"sieht\" is " +
                "present and \"sahen\" is sie (plural).",
        ),
        GapItemTemplate(
            key = "9", prompt = "Es ___ gestern ein großes Konzert in der Stadt.", answer = "gab",
            choices = listOf("gab", "gaben", "gibt", "gabt"), ruleId = "praet-strong-vowel",
            note = "\"geben\" is a strong verb; with \"es\" the simple past is \"gab\", while " +
                "\"gibt\" is present tense.",
        ),
        GapItemTemplate(
            key = "10", prompt = "Wir ___ letztes Jahr mit dem Zug nach Italien.", answer = "fuhren",
            choices = listOf("fuhren", "fuhr", "fuhrt", "fahren"), ruleId = "praet-strong-vowel",
            note = "\"fahren\" is a strong verb, so \"wir\" takes \"fuhren\" with the -en ending; " +
                "\"fahren\" is present tense.",
        ),
    )

    fun items(): List<DrillItem> =
        DrillGapTopics.itemsOf(DrillType.PRAETERITUM, TEMPLATES)
}

/** Infinitiv mit zu (10 items, docs/design/drill-content/items/infinitiv-zu.md). */
object DrillInfinitivZu {
    private val TEMPLATES = listOf(
        GapItemTemplate(
            key = "1", prompt = "Ich habe keine Lust, heute ___ arbeiten.", answer = "zu",
            choices = listOf("zu", "um", "zum", "für"), ruleId = "infzu-bare-vs-linked",
            note = "After \"keine Lust haben\" the following verb needs zu before the infinitive.",
        ),
        GapItemTemplate(
            key = "2", prompt = "Ich versuche, dich heute Abend ___ anrufen.", answer = "anzurufen",
            choices = listOf("anzurufen", "zu anrufen", "anrufen zu", "anrufen"), ruleId = "infzu-separable-split",
            note = "\"Anrufen\" is separable, so zu slides between the prefix and the stem: " +
                "an·zu·rufen.",
        ),
        GapItemTemplate(
            key = "3", prompt = "Ich fahre nach Berlin, ___ meine Freunde zu besuchen.", answer = "um",
            choices = listOf("um", "zum", "damit", "für"), ruleId = "infzu-clause-meaning",
            note = "\"Um ... zu\" expresses purpose: the trip happens in order to visit the friends.",
        ),
        GapItemTemplate(
            key = "4", prompt = "Er ging weg, ___ ein Wort zu sagen.", answer = "ohne",
            choices = listOf("ohne", "um", "mit", "zu"), ruleId = "infzu-clause-meaning",
            note = "\"Ohne ... zu\" means the second action did not happen: he left without " +
                "saying a word.",
        ),
        GapItemTemplate(
            key = "5", prompt = "Ich habe angefangen, jeden Morgen ___.", answer = "zu joggen",
            choices = listOf("zu joggen", "joggen zu", "joggen", "zum Joggen"), ruleId = "infzu-bare-vs-linked",
            note = "\"Anfangen\" requires zu + infinitive, and \"joggen\" is not separable, so zu " +
                "sits directly before it.",
        ),
        GapItemTemplate(
            key = "6", prompt = "Wir haben vor, am Samstag in der Stadt ___.", answer = "einzukaufen",
            choices = listOf("einzukaufen", "zu einkaufen", "einkaufen zu", "einkaufen"), ruleId = "infzu-separable-split",
            note = "\"Vorhaben\" takes zu + infinitive; \"einkaufen\" is separable, giving " +
                "ein·zu·kaufen.",
        ),
        GapItemTemplate(
            key = "7", prompt = "Es ist wichtig, jeden Tag ein bisschen Deutsch ___ sprechen.", answer = "zu",
            choices = listOf("zu", "um", "ohne", "für"), ruleId = "infzu-bare-vs-linked",
            note = "After an adjective like \"wichtig\" the infinitive construction also needs zu.",
        ),
        GapItemTemplate(
            key = "8", prompt = "Ich muss am Wochenende für die Prüfung ___.", answer = "lernen",
            choices = listOf("lernen", "zu lernen", "lernen zu", "zum Lernen"), ruleId = "infzu-bare-vs-linked",
            note = "Modal verbs like \"müssen\" take the bare infinitive — no zu.",
        ),
        GapItemTemplate(
            key = "9", prompt = "Vergiss nicht, das Licht ___, wenn du ins Bett gehst.", answer = "auszumachen",
            choices = listOf("auszumachen", "zu ausmachen", "ausmachen zu", "ausmachen"), ruleId = "infzu-separable-split",
            note = "\"Ausmachen\" is separable, so zu goes between the prefix and the stem: " +
                "aus·zu·machen.",
        ),
        GapItemTemplate(
            key = "10", prompt = "Wir nehmen ein Taxi, ___ den Zug nicht zu verpassen.", answer = "um",
            choices = listOf("um", "ohne", "für", "zu"), ruleId = "infzu-clause-meaning",
            note = "\"Um ... zu\" gives the reason for the action: taking the taxi in order not " +
                "to miss the train.",
        ),
    )

    fun items(): List<DrillItem> =
        DrillGapTopics.itemsOf(DrillType.INFINITIV_ZU, TEMPLATES)
}

/** Modal verbs (10 items, docs/design/drill-content/items/modal-verbs.md). */
object DrillModalVerbs {
    private val TEMPLATES = listOf(
        GapItemTemplate(
            key = "1", prompt = "Du ___ hier nicht rauchen.", answer = "darfst",
            choices = listOf("darfst", "dürft", "kannst", "muss"), ruleId = "modal-meaning",
            note = "\"dürfen\" with \"nicht\" means it is not allowed — here \"du\" takes the " +
                "form \"darfst\".",
        ),
        GapItemTemplate(
            key = "2", prompt = "Ich ___ sehr gut schwimmen.", answer = "kann",
            choices = listOf("kann", "kannst", "muss", "darf"), ruleId = "modal-meaning",
            note = "\"können\" is about ability, and with \"ich\" the form is \"kann\".",
        ),
        GapItemTemplate(
            key = "3", prompt = "Er ___ um acht Uhr im Büro sein, der Chef ist sehr streng.", answer = "muss",
            choices = listOf("muss", "musst", "müsst", "kann"), ruleId = "modal-meaning",
            note = "\"müssen\" expresses obligation, and with \"er\" the form is \"muss\".",
        ),
        GapItemTemplate(
            key = "4", prompt = "Wir ___ morgen früh aufstehen, der Zug fährt um sechs Uhr.", answer = "müssen",
            choices = listOf("müssen", "musst", "müsst", "dürfen"), ruleId = "modal-meaning",
            note = "The early train forces it, so it is an obligation, and \"wir\" takes \"müssen\".",
        ),
        GapItemTemplate(
            key = "5", prompt = "Entschuldigung, ___ Sie mir bitte helfen?", answer = "Können",
            choices = listOf("Können", "Kann", "Kannst", "Könnt"), ruleId = "modal-person-form",
            note = "Formal \"Sie\" uses the infinitive form, so the polite request is " +
                "\"Können Sie ... helfen?\".",
        ),
        GapItemTemplate(
            key = "6", prompt = "Man ___ in der Bibliothek nicht laut sprechen.", answer = "darf",
            choices = listOf("darf", "darfst", "dürft", "dürfen"), ruleId = "modal-meaning",
            note = "Public rules use \"dürfen\": \"man darf nicht\" means it is not allowed, and " +
                "\"man\" takes \"darf\".",
        ),
        GapItemTemplate(
            key = "7", prompt = "Der Arzt sagt, ich ___ mehr Wasser trinken.", answer = "soll",
            choices = listOf("soll", "sollst", "sollt", "sollen"), ruleId = "modal-person-form",
            note = "\"sollen\" is for advice or instructions from someone else, and with \"ich\" " +
                "the form is \"soll\".",
        ),
        GapItemTemplate(
            key = "8", prompt = "Ich ___ keinen Fisch, der schmeckt mir nicht.", answer = "mag",
            choices = listOf("mag", "magst", "mögt", "muss"), ruleId = "modal-meaning",
            note = "\"mögen\" expresses liking, so \"ich mag keinen Fisch\" means \"I don't like " +
                "fish\".",
        ),
        GapItemTemplate(
            key = "9", prompt = "Ich ___ einen Hund haben, aber meine Wohnung ist zu klein.", answer = "will",
            choices = listOf("will", "willst", "wollt", "wollen"), ruleId = "modal-person-form",
            note = "\"wollen\" expresses wanting, and with \"ich\" the form is \"will\".",
        ),
        GapItemTemplate(
            key = "10", prompt = "___ ihr mich am Wochenende besuchen?", answer = "Könnt",
            choices = listOf("Könnt", "Können", "Kannst", "Kann"), ruleId = "modal-person-form",
            note = "Asking whether \"you all\" are able to do it uses \"können\", and \"ihr\" " +
                "takes the form \"könnt\".",
        ),
    )

    fun items(): List<DrillItem> =
        DrillGapTopics.itemsOf(DrillType.MODAL_VERBS, TEMPLATES)
}

/** Konjunktiv I, B2 (10 items, docs/design/drill-content/items/konjunktiv-i.md). */
object DrillKonjunktivI {
    private val TEMPLATES = listOf(
        GapItemTemplate(
            key = "1", prompt = "Sie sagt, er ___ krank.", answer = "sei",
            choices = listOf("sei", "ist", "war", "wäre"), ruleId = "konj1-present",
            note = "B2 — after \"Sie sagt\", reported speech uses Konjunktiv I \"sei\" instead of " +
                "the normal present \"ist\".",
        ),
        GapItemTemplate(
            key = "2", prompt = "Die Eltern sagen, sie ___ am Wochenende länger schlafen.", answer = "müssten",
            choices = listOf("müssten", "müssen", "mussten", "müsset"), ruleId = "konj1-switch",
            note = "B2 — with plural \"sie\", the Konjunktiv I form \"müssen\" looks identical to " +
                "the normal present, so German switches to Konjunktiv II \"müssten\".",
        ),
        GapItemTemplate(
            key = "3", prompt = "Sie meint, das Konzert ___ um acht Uhr.", answer = "beginne",
            choices = listOf("beginne", "beginnt", "begann", "begänne"), ruleId = "konj1-present",
            note = "B2 — indirect speech after \"meint\" puts the reported verb into Konjunktiv " +
                "I: \"beginnt\" becomes \"beginne\".",
        ),
        GapItemTemplate(
            key = "4", prompt = "Er sagt, er ___ nächsten Monat nach Berlin.", answer = "komme",
            choices = listOf("komme", "kommt", "kam", "käme"), ruleId = "konj1-present",
            note = "B2 — \"komme\" is the Konjunktiv I form that reports the present \"kommt\" " +
                "after \"Er sagt\".",
        ),
        GapItemTemplate(
            key = "5", prompt = "Im Radio sagt man, das Wetter ___ bis Freitag gut.", answer = "bleibe",
            choices = listOf("bleibe", "bleibt", "blieb", "bliebe"), ruleId = "konj1-present",
            note = "B2 — a reported present stays present but shifts to Konjunktiv I, so " +
                "\"bleibt\" becomes \"bleibe\".",
        ),
        GapItemTemplate(
            key = "6", prompt = "Er sagt, er ___ sehr gut kochen.", answer = "könne",
            choices = listOf("könne", "kann", "konnte", "könnte"), ruleId = "konj1-present",
            note = "B2 — modal verbs also take Konjunktiv I in indirect speech; \"könne\" " +
                "reports \"kann\".",
        ),
        GapItemTemplate(
            key = "7", prompt = "Er behauptet, er ___ das Buch schon gelesen.", answer = "habe",
            choices = listOf("habe", "hat", "hatte", "hätte"), ruleId = "konj1-perfect",
            note = "B2 — a reported perfect tense uses Konjunktiv I \"habe\" plus the participle " +
                "instead of \"hat ... gelesen\".",
        ),
        GapItemTemplate(
            key = "8", prompt = "Sie sagt, er ___ dich morgen anrufen.", answer = "werde",
            choices = listOf("werde", "wird", "wurde", "würde"), ruleId = "konj1-future",
            note = "B2 — a reported future uses Konjunktiv I \"werde\" plus the infinitive, the " +
                "reported form of \"wird\".",
        ),
        GapItemTemplate(
            key = "9", prompt = "Er sagt, er ___ die Antwort nicht.", answer = "wisse",
            choices = listOf("wisse", "weiß", "wusste", "wüsste"), ruleId = "konj1-present",
            note = "B2 — \"wisse\" is the Konjunktiv I of \"wissen\" used to report \"weiß\" in " +
                "indirect speech.",
        ),
        GapItemTemplate(
            key = "10", prompt = "Sie sagt, ihr Vater ___ jeden Morgen mit dem Fahrrad.", answer = "fahre",
            choices = listOf("fahre", "fährt", "fuhr", "führe"), ruleId = "konj1-present",
            note = "B2 — most verbs build Konjunktiv I regularly from the stem, so the reported " +
                "present of \"fährt\" is \"fahre\".",
        ),
    )

    fun items(): List<DrillItem> =
        DrillGapTopics.itemsOf(DrillType.KONJUNKTIV_I, TEMPLATES)
}
