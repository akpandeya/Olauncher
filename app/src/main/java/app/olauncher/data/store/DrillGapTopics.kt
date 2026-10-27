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
 * the ✗ miss line after a wrong tap (design §3). [GapItemTemplate.missByChoice] carries
 * one reason per wrong choice — why that exact choice is wrong — transcribed from the
 * "miss lines" sections of the topic design files (docs/design/drill-content/);
 * a choice without its own entry falls back to the item's [GapItemTemplate.note].
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
    val missByChoice: Map<String, String> = emptyMap(),
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
                missByChoice = t.missByChoice,
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
            missByChoice = mapOf(
                "werde" to "the ich form. Das Auto is a third-person subject, so wird.",
                "werden" to "the plural present form. Das Auto is one single thing, so wird.",
                "wurden" to "the past plural form. Das Auto is one thing, and gerade shows the " +
                    "repair is going on now, so wird.",
            ),
        ),
        GapItemTemplate(
            key = "2", prompt = "Die Bücher ___ oft gelesen.", answer = "werden",
            choices = listOf("werden", "wird", "wurde", "worden"), ruleId = "passiv-aux-form",
            note = "A plural subject in the present tense passive takes werden, not wird.",
            missByChoice = mapOf(
                "wird" to "the singular present form. Die Bücher is plural, so werden.",
                "wurde" to "the singular past form. oft is a habit in the present, so werden.",
                "worden" to "a participle that stands beside a form of sein, not a finite verb. " +
                    "The present helper werden is needed.",
            ),
        ),
        GapItemTemplate(
            key = "3", prompt = "Der Brief ___ gestern von meiner Schwester geschrieben.", answer = "wurde",
            choices = listOf("wurde", "wird", "war", "worden"), ruleId = "passiv-aux-form",
            note = "\"gestern\" points to the past, so werden becomes wurde (simple past, singular).",
            missByChoice = mapOf(
                "wird" to "the present form. gestern points to the past, so wurde.",
                "war" to "sein in the past only describes a finished state. von meiner Schwester " +
                    "names who did the writing, so this is the action: the process passive wurde.",
                "worden" to "a participle for the perfect passive. A finite past helper is " +
                    "needed, so wurde.",
            ),
        ),
        GapItemTemplate(
            key = "4", prompt = "Die Tiere ___ letzte Woche vom Tierarzt untersucht.", answer = "wurden",
            choices = listOf("wurden", "wurde", "werden", "worden"), ruleId = "passiv-aux-form",
            note = "Past time marker plus a plural subject gives wurden, the plural of wurde.",
            missByChoice = mapOf(
                "wurde" to "the singular past form. Die Tiere is plural, so wurden.",
                "werden" to "the present form. letzte Woche points to the past, so wurden.",
                "worden" to "a participle, not a finite verb. The past plural form wurden fits.",
            ),
        ),
        GapItemTemplate(
            key = "5", prompt = "Die Wohnung ___ schon verkauft worden.", answer = "ist",
            choices = listOf("ist", "hat", "sind", "wird"), ruleId = "passiv-perfekt-sein",
            note = "The perfect passive is built with sein, never haben, in front of Partizip II + worden.",
            missByChoice = mapOf(
                "hat" to "haben never builds a passive. The perfect passive stands on sein, so " +
                    "ist.",
                "sind" to "the plural form. Die Wohnung is one thing, so ist.",
                "wird" to "the present passive helper. With schon … worden the perfect is " +
                    "needed, so ist.",
            ),
        ),
        GapItemTemplate(
            key = "6", prompt = "Die Blumen ___ bereits gegossen worden.", answer = "sind",
            choices = listOf("sind", "haben", "wurden", "werden"), ruleId = "passiv-perfekt-sein",
            note = "Same perfect passive as singular, but the plural subject makes it sind.",
            missByChoice = mapOf(
                "haben" to "the plural of haben, which never builds a passive. Die Blumen take " +
                    "sind.",
                "wurden" to "a finite past helper. worden needs a form of sein beside it, so sind.",
                "werden" to "the present plural helper. With bereits … worden the perfect needs " +
                    "sind.",
            ),
        ),
        GapItemTemplate(
            key = "7", prompt = "Die Suppe ist schon gekocht ___.", answer = "worden",
            choices = listOf("worden", "geworden", "werden", "war"), ruleId = "passiv-perfekt-partizip",
            note = "In the perfect passive werden takes the special form worden, not its normal " +
                "participle geworden.",
            missByChoice = mapOf(
                "geworden" to "the participle werden uses as a full verb (Er ist müde geworden). " +
                    "Inside the passive it is worden.",
                "werden" to "an infinitive. After ist + gekocht the passive participle worden " +
                    "completes the perfect.",
                "war" to "a finite past of sein. The sentence already has ist; the missing word " +
                    "is the participle worden.",
            ),
        ),
        GapItemTemplate(
            key = "8", prompt = "Der Text wird ___ dem Lehrer korrigiert.", answer = "von",
            choices = listOf("von", "mit", "durch", "bei"), ruleId = "passiv-agent",
            note = "The person doing the action is introduced with von plus dative, not with a " +
                "preposition of means or place.",
            missByChoice = mapOf(
                "mit" to "names the tool the doer used, not the doer. Der Lehrer is the person " +
                    "doing it, so von dem Lehrer.",
                "durch" to "names the means or the cause, and takes the accusative. A person " +
                    "doing the action takes von dem.",
                "bei" to "marks being at a person or place, not doing the action. The doer takes " +
                    "von dem.",
            ),
        ),
        GapItemTemplate(
            key = "9", prompt = "Das Lied wird von ___ Sängerin gesungen.", answer = "der",
            choices = listOf("der", "die", "dem", "den"), ruleId = "prep-fixed-dat",
            note = "von takes the dative, and the feminine dative article is der.",
            missByChoice = mapOf(
                "die" to "die is the nominative or accusative feminine form. von takes the " +
                    "dative, so the dative feminine form der.",
                "dem" to "dem is the dative form for masculine or neuter nouns. Sängerin is " +
                    "feminine, so der.",
                "den" to "den is the accusative masculine (or dative plural) form. Sängerin is " +
                    "feminine dative, so der.",
            ),
        ),
        GapItemTemplate(
            key = "10", prompt = "Das Geschirr wird jetzt ___.", answer = "gewaschen",
            choices = listOf("gewaschen", "wäscht", "waschen", "gewaschte"), ruleId = "passiv-partizip-ii",
            note = "After wird the action word stands as Partizip II, never as a conjugated or " +
                "infinitive form.",
            missByChoice = mapOf(
                "wäscht" to "the conjugated present form. After the helper the action word is " +
                    "not conjugated, so gewaschen.",
                "waschen" to "the infinitive. The helper already carries the tense, so the " +
                    "Partizip II gewaschen.",
                "gewaschte" to "not a German form. waschen is a strong verb, so its participle " +
                    "ends in -en, never -t: gewaschen.",
            ),
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
            missByChoice = mapOf(
                "würde" to "würde needs an infinitive after it, and Zeit is a noun, not a verb. " +
                    "The unreal form of haben is hätte.",
                "war" to "war is the real past. The condition is imagined, not real, so hätte.",
                "habe" to "habe is the real present. The imagined condition takes hätte.",
            ),
        ),
        GapItemTemplate(
            key = "2", prompt = "Wenn ich reich ___, würde ich um die Welt reisen.", answer = "wäre",
            choices = listOf("wäre", "hätte", "bin", "würde"), ruleId = "konj2-wenn-unreal",
            note = "With \"sein\", the Konjunktiv II form is \"wäre\"; \"bin\" states a real fact, " +
                "which the unreal sentence forbids.",
            missByChoice = mapOf(
                "hätte" to "hätte is the unreal form of haben. The verb here is sein, so wäre.",
                "bin" to "bin states a real fact. The condition is imagined, so wäre.",
                "würde" to "würde needs an infinitive after it, but reich is not a verb. The " +
                    "unreal form of sein is wäre.",
            ),
        ),
        GapItemTemplate(
            key = "3", prompt = "Wenn ich du ___, würde ich das Angebot annehmen.", answer = "wäre",
            choices = listOf("wäre", "wärst", "hätte", "bin"), ruleId = "konj2-subject-agreement",
            note = "The verb agrees with \"ich\", so it is \"wäre\", not the du-form \"wärst\".",
            missByChoice = mapOf(
                "wärst" to "wärst is the du-form. The subject is ich, so wäre.",
                "hätte" to "hätte is the unreal form of haben. The verb here is sein, so wäre.",
                "bin" to "bin is the real present and states a fact. The imagined sentence takes " +
                    "wäre.",
            ),
        ),
        GapItemTemplate(
            key = "4", prompt = "Ich ___ gern wissen, wie spät es ist.", answer = "würde",
            choices = listOf("würde", "hätte", "war", "habe"), ruleId = "konj2-helper-infinitive",
            note = "For wishes with verbs other than haben and sein, German uses \"würde\" plus " +
                "the infinitive.",
            missByChoice = mapOf(
                "hätte" to "hätte is the single unreal form, but only of haben. wissen is " +
                    "another verb, so its unreal form here is two words: würde wissen.",
                "war" to "war is the real past of sein. The wish needs würde wissen.",
                "habe" to "habe is the real present of haben. The wish needs würde wissen.",
            ),
        ),
        GapItemTemplate(
            key = "5", prompt = "Wenn du mir ___ würdest, wäre ich sehr dankbar.", answer = "helfen",
            choices = listOf("helfen", "hilfst", "geholfen", "half"), ruleId = "konj2-helper-infinitive",
            note = "\"würde\" is always followed by the plain infinitive, never by a finite or " +
                "participle form.",
            missByChoice = mapOf(
                "hilfst" to "hilfst is the present with its ending. After the helper, the verb " +
                    "stays plain: helfen.",
                "geholfen" to "geholfen is the participle. After the helper comes the dictionary " +
                    "form: helfen.",
                "half" to "half is the simple past. After the helper comes the dictionary form: " +
                    "helfen.",
            ),
        ),
        GapItemTemplate(
            key = "6", prompt = "Ich ___ gern mitgekommen, aber ich musste arbeiten.", answer = "wäre",
            choices = listOf("wäre", "hatte", "wurde", "bin"), ruleId = "konj2-past-wish",
            note = "\"wäre gern\" plus the participle expresses a wish about the past that did " +
                "not happen.",
            missByChoice = mapOf(
                "hatte" to "hatte is the real past of haben, and mitkommen builds its past with " +
                    "sein. The unreal past wish is wäre plus the participle.",
                "wurde" to "wurde is the real past of werden. The wish uses the unreal form of " +
                    "sein: wäre.",
                "bin" to "bin is the real present. The wish is about a past that did not happen: " +
                    "wäre.",
            ),
        ),
        GapItemTemplate(
            key = "7", prompt = "Wenn ihr mehr Geld ___, würdet ihr ein neues Auto kaufen.", answer = "hättet",
            choices = listOf("hättet", "hätte", "hätten", "habt"), ruleId = "konj2-subject-agreement",
            note = "The form must match \"ihr\": \"hättet\"; \"hätte\" is for ich/er and \"hätten\" " +
                "for wir/sie.",
            missByChoice = mapOf(
                "hätte" to "hätte is the ich/er form. The subject is ihr, so hättet.",
                "hätten" to "hätten is the wir/sie form. The subject is ihr, so hättet.",
                "habt" to "habt is the real present. The imagined condition takes hättet.",
            ),
        ),
        GapItemTemplate(
            key = "8", prompt = "Ich ___ gern, wo sie wohnt.", answer = "wüsste",
            choices = listOf("wüsste", "weiß", "wusste", "würde"), ruleId = "konj2-single-form",
            note = "\"wissen\" has its own Konjunktiv II form \"wüsste\", used here for a polite, " +
                "soft curiosity.",
            missByChoice = mapOf(
                "weiß" to "weiß is the real present — it states what you know. The soft wish " +
                    "uses wüsste.",
                "wusste" to "wusste is the real past. The wish uses wüsste.",
                "würde" to "würde needs the verb as a second word (würde wissen). wissen has its " +
                    "own single form: wüsste.",
            ),
        ),
        GapItemTemplate(
            key = "9", prompt = "Ich ___ gern zu deiner Party, aber ich kann nicht.", answer = "käme",
            choices = listOf("käme", "komme", "kam", "würde"), ruleId = "konj2-single-form",
            note = "\"käme\" (Konjunktiv II of kommen) politely says you would come if you could; " +
                "\"komme\" states a real plan.",
            missByChoice = mapOf(
                "komme" to "komme is the real present — it states a plan. The polite wish uses " +
                    "käme.",
                "kam" to "kam is the real past. The wish uses käme.",
                "würde" to "würde needs an infinitive after it, and there is none here. kommen " +
                    "has its own single form: käme.",
            ),
        ),
        GapItemTemplate(
            key = "10", prompt = "Ich ___ gern einen Kaffee, bitte.", answer = "hätte",
            choices = listOf("hätte", "würde", "habe", "war"), ruleId = "konj2-polite-request",
            note = "\"hätte gern\" is the standard polite way to order or ask for something.",
            missByChoice = mapOf(
                "würde" to "würde needs an infinitive after it, but einen Kaffee is not a verb. " +
                    "The polite order is hätte gern.",
                "habe" to "habe is the real present — it states a fact. The polite order is " +
                    "hätte gern.",
                "war" to "war is the real past of sein. The polite order is hätte gern.",
            ),
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
            missByChoice = mapOf(
                "den" to "the object form for a masculine noun; the pronoun is the subject of " +
                    "*steht*, so der.",
                "dem" to "the dative form; *steht* needs no object, so der.",
                "dessen" to "shows possession; the clause has a plain subject, so der.",
            ),
        ),
        GapItemTemplate(
            key = "2", prompt = "Die Frau, ___ ich gestern gesehen habe, ist Ärztin.", answer = "die",
            choices = listOf("die", "der", "den", "deren"), ruleId = "rel-agreement",
            note = "\"Die Frau\" is feminine and here the direct object of \"gesehen habe\", but " +
                "feminine accusative and nominative are both die.",
            missByChoice = mapOf(
                "der" to "feminine dative or masculine subject — neither fits; *gesehen habe* " +
                    "takes a direct object, so die.",
                "den" to "the masculine accusative form; Frau is feminine, so die.",
                "deren" to "shows possession; the clause has a direct object, so die.",
            ),
        ),
        GapItemTemplate(
            key = "3", prompt = "Das Buch, ___ ich gerade lese, ist sehr spannend.", answer = "das",
            choices = listOf("das", "dem", "den", "dessen"), ruleId = "rel-agreement",
            note = "\"Das Buch\" is neuter and the object of \"lese\", so the neuter accusative " +
                "form das fits.",
            missByChoice = mapOf(
                "dem" to "the dative form; *lese* takes a direct object, so das.",
                "den" to "the masculine accusative form; Buch is neuter, so das.",
                "dessen" to "shows possession; the clause has a direct object, so das.",
            ),
        ),
        GapItemTemplate(
            key = "4", prompt = "Der Film, ___ wir gestern gesehen haben, war langweilig.", answer = "den",
            choices = listOf("den", "der", "dem", "denen"), ruleId = "rel-agreement",
            note = "\"Der Film\" is masculine and the direct object of the relative clause, so " +
                "masculine accusative den is required.",
            missByChoice = mapOf(
                "der" to "the subject form; the pronoun is the object of *gesehen*, so den.",
                "dem" to "the dative form; there is no preposition here, so den.",
                "denen" to "the plural dative form; Film is singular and the object of " +
                    "*gesehen*, so den.",
            ),
        ),
        GapItemTemplate(
            key = "5", prompt = "Der Mann, mit ___ ich spreche, ist mein Nachbar.", answer = "dem",
            choices = listOf("dem", "den", "der", "dessen"), ruleId = "rel-after-prep",
            note = "\"mit\" always takes the dative, and the masculine dative relative pronoun " +
                "is dem.",
            missByChoice = mapOf(
                "den" to "the accusative form; mit needs the dative, so dem.",
                "der" to "in the dative row der belongs to a feminine noun; Mann is masculine, " +
                    "so dem.",
                "dessen" to "shows possession; mit needs the plain dative form dem.",
            ),
        ),
        GapItemTemplate(
            key = "6", prompt = "Die Frau, von ___ ich viel gelernt habe, ist meine Chefin.", answer = "der",
            choices = listOf("der", "die", "den", "deren"), ruleId = "rel-after-prep",
            note = "\"von\" takes the dative, and for a feminine noun the dative relative " +
                "pronoun is der.",
            missByChoice = mapOf(
                "die" to "the subject or direct-object form; von needs the dative, so der.",
                "den" to "the masculine accusative form; Frau is feminine, so der.",
                "deren" to "shows possession; von needs the plain dative form der.",
            ),
        ),
        GapItemTemplate(
            key = "7", prompt = "Die Kinder, mit ___ sie im Park spielt, sind sehr nett.", answer = "denen",
            choices = listOf("denen", "den", "die", "deren"), ruleId = "rel-after-prep",
            note = "With a plural noun after a dative preposition like \"mit\", the relative " +
                "pronoun is the special form denen.",
            missByChoice = mapOf(
                "den" to "after mit the plural relative pronoun is denen — den is only the " +
                    "plural article form, not the relative pronoun.",
                "die" to "the subject or direct-object form; mit needs the dative, so denen.",
                "deren" to "shows possession; mit needs the plain dative form denen.",
            ),
        ),
        GapItemTemplate(
            key = "8", prompt = "Der Mann, ___ Auto in der Garage steht, ist Polizist.", answer = "dessen",
            choices = listOf("dessen", "dem", "den", "deren"), ruleId = "rel-genitive",
            note = "The gap shows possession (\"his car\"), so the genitive relative pronoun for " +
                "masculine/neuter is dessen.",
            missByChoice = mapOf(
                "dem" to "the dative form used after prepositions; Auto shows possession, so " +
                    "dessen.",
                "den" to "the masculine accusative form; Auto shows possession, so dessen.",
                "deren" to "the possessive form for a feminine or plural noun; Mann is " +
                    "masculine, so dessen.",
            ),
        ),
        GapItemTemplate(
            key = "9", prompt = "Die Bücher, ___ auf dem Tisch liegen, gehören mir.", answer = "die",
            choices = listOf("die", "den", "denen", "das"), ruleId = "rel-agreement",
            note = "\"Die Bücher\" is plural and the subject of \"liegen\", and the plural " +
                "nominative relative pronoun is die.",
            missByChoice = mapOf(
                "den" to "the masculine accusative form; Bücher is plural and the pronoun is the " +
                    "subject of *liegen*, so die.",
                "denen" to "the plural dative form; the pronoun is the subject of *liegen*, so " +
                    "die.",
                "das" to "the neuter singular form; Bücher is plural, so die.",
            ),
        ),
        GapItemTemplate(
            key = "10", prompt = "Die Frau, ___ Sohn in meiner Klasse ist, kommt aus Köln.", answer = "deren",
            choices = listOf("deren", "die", "der", "denen"), ruleId = "rel-genitive",
            note = "The gap expresses possession for a feminine noun (\"her son\"), so the " +
                "genitive form is deren.",
            missByChoice = mapOf(
                "die" to "the subject or direct-object form; Sohn shows possession, so deren.",
                "der" to "the masculine subject or feminine dative form; Sohn shows possession, " +
                    "so deren.",
                "denen" to "the plural dative form used after prepositions; Sohn shows " +
                    "possession, so deren.",
            ),
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
            missByChoice = mapOf(
                "der" to "feminine or plural owner. Vater is masculine, so des.",
                "dem" to "dative. The owner here is genitive, so des.",
                "den" to "accusative masculine. The owner here is genitive, so des.",
            ),
        ),
        GapItemTemplate(
            key = "2", prompt = "Das ist die Tasche ___ Mutter.", answer = "der",
            choices = listOf("der", "des", "dem", "die"), ruleId = "gen-article",
            note = "Feminine nouns take \"der\" in the genitive, and the noun itself gets no ending.",
            missByChoice = mapOf(
                "des" to "masculine or neuter owner. Mutter is feminine, so der.",
                "dem" to "dative. The owner here is genitive, so der.",
                "die" to "nominative or accusative. The owner here is genitive, so der.",
            ),
        ),
        GapItemTemplate(
            key = "3", prompt = "Die Farbe ___ Blumen ist rot.", answer = "der",
            choices = listOf("der", "des", "die", "dem"), ruleId = "gen-article",
            note = "Plural nouns also take \"der\" in the genitive, not \"des\".",
            missByChoice = mapOf(
                "des" to "masculine or neuter singular owner. Blumen is plural, so der.",
                "die" to "nominative or accusative plural. The owner here is genitive, so der.",
                "dem" to "dative. The owner here is genitive, so der.",
            ),
        ),
        GapItemTemplate(
            key = "4", prompt = "Ich lese das Buch des ___.", answer = "Kindes",
            choices = listOf("Kindes", "Kinde", "Kind", "Kindern"), ruleId = "gen-noun-s",
            note = "Neuter \"Kind\" takes the fuller -es ending in the genitive.",
            missByChoice = mapOf(
                "Kinde" to "an old dative form (dem Kinde). After des the noun takes -es: Kindes.",
                "Kind" to "no ending. After des this noun adds -es, so Kindes.",
                "Kindern" to "dative plural. One child owns the book, so Kindes.",
            ),
        ),
        GapItemTemplate(
            key = "5", prompt = "Das Ende des ___ war spannend.", answer = "Films",
            choices = listOf("Films", "Filme", "Filmen", "Film"), ruleId = "gen-noun-s",
            note = "Masculine \"Film\" adds just -s in the genitive.",
            missByChoice = mapOf(
                "Filme" to "that is the plural. One film owns the ending, so Films.",
                "Filmen" to "dative plural. One film takes the owner -s, so Films.",
                "Film" to "no ending. After des this noun adds -s, so Films.",
            ),
        ),
        GapItemTemplate(
            key = "6", prompt = "Das ist der Ball des ___.", answer = "Jungen",
            choices = listOf("Jungen", "Junges", "Junge", "Jungens"), ruleId = "gen-noun-weak",
            note = "Weak masculine nouns like \"Junge\" take -n/-en instead of -s in the genitive.",
            missByChoice = mapOf(
                "Junges" to "weak nouns never take -s. Junge is weak and adds -n, so Jungen.",
                "Junge" to "no ending. After des, weak nouns add -n, so Jungen.",
                "Jungens" to "weak nouns never take -s. Junge adds just -n, so Jungen.",
            ),
        ),
        GapItemTemplate(
            key = "7", prompt = "___ Tages erzähle ich dir alles.", answer = "Eines",
            choices = listOf("Eines", "Einem", "Einer", "Ein"), ruleId = "gen-time-phrase",
            note = "\"Eines Tages\" is a fixed genitive time phrase meaning \"one day\".",
            missByChoice = mapOf(
                "Einem" to "dative. The set phrase stands in the genitive, so Eines.",
                "Einer" to "feminine genitive or dative. Tag is masculine, so Eines.",
                "Ein" to "nominative or accusative. The set phrase is genitive, so Eines.",
            ),
        ),
        GapItemTemplate(
            key = "8", prompt = "___ Schlüssel ist das?", answer = "Wessen",
            choices = listOf("Wessen", "Wer", "Wen", "Wem"), ruleId = "gen-question-whose",
            note = "\"Wessen\" is the genitive question word for \"whose\".",
            missByChoice = mapOf(
                "Wer" to "asks for the subject. For the owner, German uses Wessen.",
                "Wen" to "asks for a direct object. For the owner, German uses Wessen.",
                "Wem" to "asks for an indirect object. For the owner, German uses Wessen.",
            ),
        ),
        GapItemTemplate(
            key = "9", prompt = "___ altes Fahrrad steht in der Garage.", answer = "Vaters",
            choices = listOf("Vaters", "Vater", "Väter", "Vatern"), ruleId = "gen-kinship-s",
            note = "Male kinship nouns can show possession with bare -s and no article.",
            missByChoice = mapOf(
                "Vater" to "no ending. Without an article, the kinship owner adds -s, so Vaters.",
                "Väter" to "that is the plural. One owner takes Vaters.",
                "Vatern" to "not a German form. Kinship owners take bare -s, so Vaters.",
            ),
        ),
        GapItemTemplate(
            key = "10", prompt = "Das sind die Bücher der ___.", answer = "Schüler",
            choices = listOf("Schüler", "Schülern", "Schülers", "Schüleres"), ruleId = "gen-noun-zero",
            note = "Genitive plural nouns stay unchanged; only the article becomes \"der\".",
            missByChoice = mapOf(
                "Schülern" to "dative plural. Genitive plural nouns stay unchanged, so der " +
                    "Schüler.",
                "Schülers" to "genitive singular (des Schülers). The owners here are plural, so " +
                    "der Schüler.",
                "Schüleres" to "not a German form. Plural owners stay unchanged, so der Schüler.",
            ),
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
            missByChoice = mapOf(
                "Jungen" to "the -n form is for every use except the subject; the boy is the one " +
                    "playing, so Junge.",
                "Jung" to "that is not the noun; the noun is der Junge.",
                "Jungs" to "that is the informal plural; the sentence is about one boy, so Junge.",
            ),
        ),
        GapItemTemplate(
            key = "2", prompt = "Ich kenne den ___ aus dem Sprachkurs.", answer = "Studenten",
            choices = listOf("Student", "Studenten", "Studente", "Studentes"), ruleId = "ndecl-weak-en",
            note = "\"den\" is accusative, so the weak noun Student adds -en.",
            missByChoice = mapOf(
                "Student" to "the bare form is only for the subject; after den the weak noun " +
                    "adds -en: Studenten.",
                "Studente" to "weak nouns add -en, not -e: Studenten.",
                "Studentes" to "weak nouns never take -s: Studenten.",
            ),
        ),
        GapItemTemplate(
            key = "3", prompt = "Wir helfen dem ___ am Wochenende im Garten.", answer = "Nachbarn",
            choices = listOf("Nachbar", "Nachbars", "Nachbarn", "Nachbaren"), ruleId = "ndecl-weak-en",
            note = "\"helfen\" takes the dative, and dem Nachbar becomes Nachbarn with the added -n.",
            missByChoice = mapOf(
                "Nachbar" to "the bare form is only for the subject; after dem the weak noun " +
                    "takes -n: Nachbarn.",
                "Nachbars" to "weak nouns take -n, not -s: Nachbarn.",
                "Nachbaren" to "Nachbar adds just -n, not -en: Nachbarn.",
            ),
        ),
        GapItemTemplate(
            key = "4", prompt = "Das ist das Auto des ___.", answer = "Kollegen",
            choices = listOf("Kollege", "Kollegen", "Kolleges", "Kolleg"), ruleId = "ndecl-weak-en",
            note = "Genitive with \"des\", so the weak noun Kollege takes -en instead of the " +
                "strong -s ending.",
            missByChoice = mapOf(
                "Kollege" to "the bare form is only for the subject; after des the weak noun " +
                    "adds -n: Kollegen.",
                "Kolleges" to "weak nouns add -n, not -s, even here: Kollegen.",
                "Kolleg" to "that is not the noun; the noun is der Kollege, here des Kollegen.",
            ),
        ),
        GapItemTemplate(
            key = "5", prompt = "Grüßen Sie bitte den ___ von mir.", answer = "Herrn",
            choices = listOf("Herr", "Herrn", "Herren", "Herrs"), ruleId = "ndecl-weak-en",
            note = "Herr adds only -n (den Herrn); Herren is the plural, not the accusative singular.",
            missByChoice = mapOf(
                "Herr" to "after den, Herr adds -n: Herrn.",
                "Herren" to "that is the plural; one person is meant, so Herrn.",
                "Herrs" to "weak nouns take no -s: Herrn.",
            ),
        ),
        GapItemTemplate(
            key = "6", prompt = "Der Kellner bringt dem ___ das Essen.", answer = "Kunden",
            choices = listOf("Kunde", "Kunden", "Kunds", "Kundes"), ruleId = "ndecl-weak-en",
            note = "The waiter brings something to someone, so \"dem\" marks dative and Kunde " +
                "becomes Kunden.",
            missByChoice = mapOf(
                "Kunde" to "the bare form is only for the subject; after dem the weak noun adds " +
                    "-n: Kunden.",
                "Kunds" to "weak nouns take -n, not -s: Kunden.",
                "Kundes" to "there is no -s form here; it is dem Kunden.",
            ),
        ),
        GapItemTemplate(
            key = "7", prompt = "Die ___ warten an der Haltestelle auf den Bus.", answer = "Menschen",
            choices = listOf("Mensch", "Menschen", "Menschens", "Menscher"), ruleId = "ndecl-weak-en",
            note = "Weak nouns keep the -en in the plural too, even in the nominative.",
            missByChoice = mapOf(
                "Mensch" to "singular; the verb warten shows several people, so Menschen.",
                "Menschens" to "the plural of a weak noun never takes -s: Menschen.",
                "Menscher" to "not a form of Mensch; the plural is Menschen.",
            ),
        ),
        GapItemTemplate(
            key = "8", prompt = "Im Zoo fotografiert sie den ___ hinter dem Baum.", answer = "Affen",
            choices = listOf("Affe", "Affen", "Aff", "Affes"), ruleId = "ndecl-weak-en",
            note = "\"den\" is accusative, so Affe gets the extra -n.",
            missByChoice = mapOf(
                "Affe" to "the bare form is only for the subject; after den the weak noun adds " +
                    "-n: Affen.",
                "Aff" to "that is not the noun; the noun is der Affe, here den Affen.",
                "Affes" to "weak nouns never take -s: Affen.",
            ),
        ),
        GapItemTemplate(
            key = "9", prompt = "Der Pfleger gibt dem ___ jeden Tag frisches Fleisch.", answer = "Löwen",
            choices = listOf("Löwe", "Löwen", "Löwes", "Löwin"), ruleId = "ndecl-weak-en",
            note = "\"geben\" takes a dative object, so dem Löwe becomes dem Löwen.",
            missByChoice = mapOf(
                "Löwe" to "the bare form is only for the subject; after dem the weak noun adds " +
                    "-n: Löwen.",
                "Löwes" to "weak nouns take -n, not -s: Löwen.",
                "Löwin" to "the female lion is feminine (der Löwin after a dative). dem shows a " +
                    "masculine noun, der Löwe, so dem Löwen.",
            ),
        ),
        GapItemTemplate(
            key = "10", prompt = "Ich habe den ___ der neuen Kollegin wieder vergessen.", answer = "Namen",
            choices = listOf("Name", "Namen", "Namens", "Names"), ruleId = "ndecl-weak-en",
            note = "\"den\" is accusative, so Name becomes Namen; Namens would only fit the genitive.",
            missByChoice = mapOf(
                "Name" to "the bare form is only for the subject; after den the weak noun adds " +
                    "-n: Namen.",
                "Namens" to "that -s form belongs after des; after den it is Namen.",
                "Names" to "not a form of Name; here den Namen.",
            ),
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
            missByChoice = mapOf(
                "Macht" to "the ihr form, for several people. Paul is one person you know, so " +
                    "Mach.",
                "Machst" to "the du present tense, not a command. The du imperative drops the " +
                    "-st: Mach.",
                "Machen" to "the formal form, which always needs Sie after it. Paul is addressed " +
                    "informally, so Mach.",
            ),
        ),
        GapItemTemplate(
            key = "2", prompt = "___ mir bitte das Salz!", answer = "Gib",
            choices = listOf("Gibst", "Gib", "Geb", "Gibt"), ruleId = "imp-du-vowel-change",
            note = "The du imperative of geben uses the changed stem gib and drops the ending " +
                "(no -st).",
            missByChoice = mapOf(
                "Gibst" to "the du present tense, not a command. The du imperative drops the " +
                    "-st: Gib.",
                "Geb" to "geben is a strong verb, so its stem vowel changes in the du " +
                    "imperative: Gib.",
                "Gibt" to "the er/sie/es present tense (er gibt), not a command. The du " +
                    "imperative is Gib.",
            ),
        ),
        GapItemTemplate(
            key = "3", prompt = "___ die E-Mail noch einmal, Anna!", answer = "Lies",
            choices = listOf("Lies", "Lest", "Les", "Liest"), ruleId = "imp-du-vowel-change",
            note = "lesen changes e to ie, so the du imperative is Lies, not Les.",
            missByChoice = mapOf(
                "Lest" to "the ihr form. Anna is one person, so Lies.",
                "Les" to "lesen changes e to ie in the du imperative, so the stem is not Les: " +
                    "Lies.",
                "Liest" to "the du present tense. The imperative drops the -st: Lies.",
            ),
        ),
        GapItemTemplate(
            key = "4", prompt = "___ eure Schuhe an!", answer = "Zieht",
            choices = listOf("Zieht", "Zieh", "Ziehen", "Ziehst"), ruleId = "imp-person",
            note = "The ihr imperative takes the stem plus -t, and the separable prefix an lands " +
                "at the end.",
            missByChoice = mapOf(
                "Zieh" to "the du form. eure shows several people are addressed, so Zieht.",
                "Ziehen" to "the formal form, which needs Sie after it. eure shows informal " +
                    "plural address, so Zieht.",
                "Ziehst" to "the du present tense, not a command. The ihr imperative is stem + " +
                    "t: Zieht.",
            ),
        ),
        GapItemTemplate(
            key = "5", prompt = "___ bitte leise, Kinder, das Baby schläft!", answer = "Seid",
            choices = listOf("Seid", "Sei", "Sind", "Sein"), ruleId = "imp-sein",
            note = "The ihr imperative of sein is Seid (stem plus -t).",
            missByChoice = mapOf(
                "Sei" to "the du imperative of sein. Kinder are several people, so Seid.",
                "Sind" to "the present tense, not an imperative. The ihr imperative of sein is " +
                    "Seid.",
                "Sein" to "the infinitive. The ihr imperative of sein is Seid.",
            ),
        ),
        GapItemTemplate(
            key = "6", prompt = "___ bitte vorsichtig mit dem Glas, Max!", answer = "Sei",
            choices = listOf("Sei", "Bist", "Seid", "Seist"), ruleId = "imp-sein",
            note = "The du imperative of sein is Sei; it drops the verb ending entirely.",
            missByChoice = mapOf(
                "Bist" to "the du present tense. The du imperative of sein drops the ending: Sei.",
                "Seid" to "the ihr form. Max is one person, so Sei.",
                "Seist" to "not a German form. The du imperative of sein is Sei.",
            ),
        ),
        GapItemTemplate(
            key = "7", prompt = "___ Sie bitte Platz!", answer = "Nehmen",
            choices = listOf("Nehmen", "Nimm", "Nehmt", "Nimmt"), ruleId = "imp-sie-infinitive",
            note = "The Sie imperative uses the infinitive form, so Nehmen Sie means \"please take\".",
            missByChoice = mapOf(
                "Nimm" to "the du imperative. Sie marks formal address, which uses the " +
                    "infinitive: Nehmen.",
                "Nehmt" to "the ihr form. Formal address uses the infinitive: Nehmen.",
                "Nimmt" to "the present tense (er/sie/es). The Sie imperative is the infinitive: " +
                    "Nehmen.",
            ),
        ),
        GapItemTemplate(
            key = "8", prompt = "___ Sie bitte etwas Geduld!", answer = "Haben",
            choices = listOf("Haben", "Hab", "Habt", "Hast"), ruleId = "imp-sie-infinitive",
            note = "With Sie, the imperative of haben is the infinitive: Haben Sie Geduld.",
            missByChoice = mapOf(
                "Hab" to "the du imperative of haben. Sie is formal, so the infinitive: Haben.",
                "Habt" to "the ihr form. Formal address uses the infinitive: Haben.",
                "Hast" to "the du present tense. The Sie imperative is the infinitive: Haben.",
            ),
        ),
        GapItemTemplate(
            key = "9", prompt = "___ Sie das Formular bitte ganz aus!", answer = "Füllen",
            choices = listOf("Füllen", "Füll", "Füllt", "Füllst"), ruleId = "imp-sie-infinitive",
            note = "In the Sie imperative the verb stays in the infinitive form and the prefix " +
                "aus moves to the end.",
            missByChoice = mapOf(
                "Füll" to "the du imperative. Sie is formal, so the infinitive: Füllen.",
                "Füllt" to "the ihr form. Formal address uses the infinitive: Füllen.",
                "Füllst" to "the du present tense. The Sie imperative is the infinitive: Füllen.",
            ),
        ),
        GapItemTemplate(
            key = "10", prompt = "___ nicht so schnell, Kinder, wir haben Zeit!", answer = "Esst",
            choices = listOf("Esst", "Iss", "Isst", "Ess"), ruleId = "imp-person",
            note = "The ihr imperative keeps the normal stem and adds -t (Esst), while du would " +
                "change the vowel to Iss.",
            missByChoice = mapOf(
                "Iss" to "the du imperative, with its vowel change. Kinder are several people, " +
                    "so Esst.",
                "Isst" to "the present tense. The ihr imperative keeps the stem and adds -t: Esst.",
                "Ess" to "not a form. du changes the vowel (Iss), and ihr adds -t to the plain " +
                    "stem: Esst.",
            ),
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
            missByChoice = mapOf(
                "werde" to "the ich form. Er is third person, so wird.",
                "wirst" to "the du form. Er takes the third-person form, so wird.",
                "werdet" to "the ihr form. Er is one person you talk about, not a group you " +
                    "address, so wird.",
            ),
        ),
        GapItemTemplate(
            key = "2", prompt = "Du ___ bestimmt müde.", answer = "wirst",
            choices = listOf("wirst", "werde", "wird", "werden"), ruleId = "werden-conjugation",
            note = "\"Du\" always takes \"wirst\"; werden is the full verb linking the subject to " +
                "the adjective \"müde\".",
            missByChoice = mapOf(
                "werde" to "the ich form. Du has its own form, so wirst.",
                "wird" to "the er/sie/es form. Du takes wirst.",
                "werden" to "the wir/sie form. Du takes wirst.",
            ),
        ),
        GapItemTemplate(
            key = "3", prompt = "Ich ___ dir morgen beim Umzug helfen.", answer = "werde",
            choices = listOf("werde", "wirst", "wird", "werden"), ruleId = "futur-helper-form",
            note = "This is the future tense: werden (here \"werde\" for ich) plus the infinitive " +
                "\"helfen\" at the end.",
            missByChoice = mapOf(
                "wirst" to "the du form. Ich takes werde.",
                "wird" to "the er/sie/es form. Ich takes werde.",
                "werden" to "the wir/sie form. Ich takes werde.",
            ),
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
            missByChoice = mapOf(
                "werdet" to "the ihr form. Wir takes werden.",
                "werde" to "the ich form. Wir takes werden.",
                "wirst" to "the du form. Wir takes werden.",
            ),
        ),
        GapItemTemplate(
            key = "5", prompt = "___ ihr am Samstag kommen?", answer = "Werdet",
            choices = listOf("Werdet", "Werden", "Wirst", "Wird"), ruleId = "futur-helper-form",
            note = "In a future-tense question the verb moves to the front; \"ihr\" takes " +
                "\"werdet\", giving \"Werdet ihr ... kommen?\".",
            hintOverride = "An infinitive at the end (helfen, ziehen, kommen) is the future " +
                "marker; then match the subject.",
            missByChoice = mapOf(
                "Werden" to "the wir/sie form. ihr takes werdet.",
                "Wirst" to "the du form. ihr takes werdet.",
                "Wird" to "the er/sie/es form. ihr takes werdet.",
            ),
        ),
        GapItemTemplate(
            key = "6", prompt = "Das Haus ___ im Moment renoviert.", answer = "wird",
            choices = listOf("wird", "werde", "werden", "wurde"), ruleId = "passiv-aux-form",
            note = "Passive voice: werden (\"wird\" for das Haus) plus the past participle " +
                "\"renoviert\" says the action is being done to the house.",
            missByChoice = mapOf(
                "werde" to "the ich form. Das Haus is a third-person subject, so wird.",
                "werden" to "the plural form. Das Haus is one thing, so wird.",
                "wurde" to "past. im Moment says it is happening now, so wird.",
            ),
        ),
        GapItemTemplate(
            key = "7", prompt = "Das Auto ___ letzte Woche repariert.", answer = "wurde",
            choices = listOf("wurde", "wurden", "würde", "wird"), ruleId = "passiv-aux-form",
            note = "Past passive with a singular subject uses \"wurde\"; \"letzte Woche\" signals " +
                "the past.",
            missByChoice = mapOf(
                "wurden" to "plural. Das Auto is one thing, so wurde.",
                "würde" to "the would-form for imagined situations. This is a real past event, " +
                    "so wurde.",
                "wird" to "present. letzte Woche signals the past, so wurde.",
            ),
        ),
        GapItemTemplate(
            key = "8", prompt = "Die E-Mails ___ gestern verschickt.", answer = "wurden",
            choices = listOf("wurden", "wurde", "werden", "würden"), ruleId = "passiv-aux-form",
            note = "Past passive with a plural subject uses \"wurden\", the plural form of wurde.",
            missByChoice = mapOf(
                "wurde" to "singular. E-Mails is plural, so wurden.",
                "werden" to "present. gestern signals the past, so wurden.",
                "würden" to "the would-form. This is a real past event, so wurden.",
            ),
        ),
        GapItemTemplate(
            key = "9", prompt = "Das Problem ist heute gelöst ___.", answer = "worden",
            choices = listOf("worden", "geworden", "gewordet", "wurde"), ruleId = "passiv-perfekt-partizip",
            note = "In the passive perfect, werden's participle is \"worden\" (ist ... gelöst " +
                "worden); \"geworden\" is only used in active sentences.",
            missByChoice = mapOf(
                "geworden" to "the participle werden uses as a full verb (Er ist Arzt geworden). " +
                    "gelöst makes this passive, so worden.",
                "gewordet" to "not a form of werden. The passive perfect participle is worden.",
                "wurde" to "simple past, not the perfect. ist … gelöst needs worden.",
            ),
        ),
        GapItemTemplate(
            key = "10", prompt = "An deiner Stelle ___ ich das Angebot annehmen.", answer = "würde",
            choices = listOf("würde", "werde", "wirst", "wurde"), ruleId = "werden-subjunctive-ii",
            note = "This is subjunctive II: \"würde\" plus the infinitive expresses what you " +
                "would do; \"werde\" would wrongly state a real future plan.",
            missByChoice = mapOf(
                "werde" to "states a real future plan. An deiner Stelle is imagined advice, so " +
                    "würde.",
                "wirst" to "the du form. The subject is ich, and the imagined form is würde.",
                "wurde" to "the simple past of werden, without the umlaut. An imagined choice " +
                    "needs würde.",
            ),
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
            missByChoice = mapOf(
                "warst" to "the du form of sein. The speaker is one person (ich), so war.",
                "waren" to "the wir/sie plural form. The speaker is one person, so war.",
                "hatte" to "the simple past of haben. This sentence needs sein, so war.",
            ),
        ),
        GapItemTemplate(
            key = "2", prompt = "Gestern ___ ich keine Zeit für Sport.", answer = "hatte",
            choices = listOf("hatte", "habte", "haben", "hatten"), ruleId = "praet-haben",
            note = "\"haben\" has the irregular simple past \"hatte\" for \"ich\"; \"habte\" " +
                "wrongly treats it like a regular verb.",
            missByChoice = mapOf(
                "habte" to "treats haben like a regular verb. Its simple past for one speaker is " +
                    "hatte.",
                "haben" to "the present tense (also the infinitive). The past for one speaker is " +
                    "hatte.",
                "hatten" to "the wir/sie plural form. The speaker is one person, so hatte.",
            ),
        ),
        GapItemTemplate(
            key = "3", prompt = "Ihr ___ gestern sehr müde.", answer = "wart",
            choices = listOf("wart", "wartet", "waren", "war"), ruleId = "praet-sein",
            note = "\"sein\" in the simple past with \"ihr\" is \"wart\"; \"wartet\" would be the " +
                "regular-verb ending.",
            missByChoice = mapOf(
                "wartet" to "mixes sein with a regular verb's -et ending. The ihr form of sein " +
                    "is wart.",
                "waren" to "the wir/sie plural form. Ihr takes wart.",
                "war" to "the ich/er/sie/es form. Ihr takes wart.",
            ),
        ),
        GapItemTemplate(
            key = "4", prompt = "Mit sechs Jahren ___ ich noch nicht schwimmen.", answer = "konnte",
            choices = listOf("konnte", "konntest", "konnten", "kann"), ruleId = "praet-modal",
            note = "Modals form the simple past regularly, so \"können\" becomes \"konnte\" for " +
                "\"ich\"; \"kann\" is present tense.",
            missByChoice = mapOf(
                "konntest" to "the du form. The subject is ich, so konnte.",
                "konnten" to "the wir/sie plural form. The subject is ich, so konnte.",
                "kann" to "present tense. The sentence is about age six, in the past, so konnte.",
            ),
        ),
        GapItemTemplate(
            key = "5", prompt = "Ich ___ gestern leider länger arbeiten.", answer = "musste",
            choices = listOf("musste", "mussten", "musstet", "muss"), ruleId = "praet-modal",
            note = "\"müssen\" drops its umlaut in the simple past, giving \"musste\" for \"ich\"; " +
                "\"muss\" is present tense.",
            missByChoice = mapOf(
                "mussten" to "the wir/sie plural form. The subject is ich, so musste.",
                "musstet" to "the ihr form. The subject is ich, so musste.",
                "muss" to "present tense. Gestern is past, so musste.",
            ),
        ),
        GapItemTemplate(
            key = "6", prompt = "Als Kind ___ ich immer Eis zum Frühstück.", answer = "wollte",
            choices = listOf("wollte", "wollten", "wolltet", "will"), ruleId = "praet-modal",
            note = "\"wollen\" is a regular simple past \"wollte\" for \"ich\"; the other choices " +
                "are other persons or the present \"will\".",
            missByChoice = mapOf(
                "wollten" to "the wir/sie plural form. The subject is ich, so wollte.",
                "wolltet" to "the ihr form. The subject is ich, so wollte.",
                "will" to "present tense. Als Kind is past, so wollte.",
            ),
        ),
        GapItemTemplate(
            key = "7", prompt = "Gestern ___ ich nur kurz beim Supermarkt vorbei.", answer = "ging",
            choices = listOf("ging", "gingst", "gingen", "gehe"), ruleId = "praet-strong-vowel",
            note = "\"gehen\" is a strong verb with the past stem \"ging\" for \"ich\"; \"gehe\" " +
                "is present tense.",
            missByChoice = mapOf(
                "gingst" to "the du form. The subject is ich, so ging.",
                "gingen" to "the wir/sie plural form. The subject is ich, so ging.",
                "gehe" to "present tense. Gestern is past, so ging.",
            ),
        ),
        GapItemTemplate(
            key = "8", prompt = "Anna ___ den Film schon letzte Woche.", answer = "sah",
            choices = listOf("sah", "sahen", "sieht", "seht"), ruleId = "praet-strong-vowel",
            note = "\"sehen\" changes its vowel in the simple past, \"ich/sie sah\"; \"sieht\" is " +
                "present and \"sahen\" is sie (plural).",
            missByChoice = mapOf(
                "sahen" to "the form for wir, sie (they) and Sie. Anna is one person, so sah.",
                "sieht" to "present tense. Letzte Woche is past, so sah.",
                "seht" to "the present ihr form. The subject is one person, so sah.",
            ),
        ),
        GapItemTemplate(
            key = "9", prompt = "Es ___ gestern ein großes Konzert in der Stadt.", answer = "gab",
            choices = listOf("gab", "gaben", "gibt", "gabt"), ruleId = "praet-strong-vowel",
            note = "\"geben\" is a strong verb; with \"es\" the simple past is \"gab\", while " +
                "\"gibt\" is present tense.",
            missByChoice = mapOf(
                "gaben" to "the wir/sie plural form. The subject is es, so gab.",
                "gibt" to "present tense. Gestern is past, so gab.",
                "gabt" to "the ihr form. The subject is es, so gab.",
            ),
        ),
        GapItemTemplate(
            key = "10", prompt = "Wir ___ letztes Jahr mit dem Zug nach Italien.", answer = "fuhren",
            choices = listOf("fuhren", "fuhr", "fuhrt", "fahren"), ruleId = "praet-strong-vowel",
            note = "\"fahren\" is a strong verb, so \"wir\" takes \"fuhren\" with the -en ending; " +
                "\"fahren\" is present tense.",
            missByChoice = mapOf(
                "fuhr" to "the ich/er/sie/es form. The subject is wir, so fuhren.",
                "fuhrt" to "the ihr form. The subject is wir, so fuhren.",
                "fahren" to "present tense. Letztes Jahr is past, so fuhren.",
            ),
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
            missByChoice = mapOf(
                "um" to "um starts a purpose clause (um ... zu). Here the infinitive only takes " +
                    "the linking word: zu.",
                "zum" to "zum is zu + dem and goes before nouns. Before an infinitive it is " +
                    "plain zu.",
                "für" to "für is a preposition before nouns. Before an infinitive stands the " +
                    "linking word zu.",
            ),
        ),
        GapItemTemplate(
            key = "2", prompt = "Ich versuche, dich heute Abend ___ anrufen.", answer = "anzurufen",
            choices = listOf("anzurufen", "zu anrufen", "anrufen zu", "anrufen"), ruleId = "infzu-separable-split",
            note = "\"Anrufen\" is separable, so zu slides between the prefix and the stem: " +
                "an·zu·rufen.",
            missByChoice = mapOf(
                "zu anrufen" to "anrufen is separable, so zu slides between prefix and stem: " +
                    "anzurufen.",
                "anrufen zu" to "the linking word stands before the stem, never after the verb: " +
                    "anzurufen.",
                "anrufen" to "versuchen takes zu + infinitive, so the bare form is incomplete: " +
                    "anzurufen.",
            ),
        ),
        GapItemTemplate(
            key = "3", prompt = "Ich fahre nach Berlin, ___ meine Freunde zu besuchen.", answer = "um",
            choices = listOf("um", "zum", "damit", "für"), ruleId = "infzu-clause-meaning",
            note = "\"Um ... zu\" expresses purpose: the trip happens in order to visit the friends.",
            missByChoice = mapOf(
                "zum" to "zum is zu + dem and goes before nouns. A purpose clause with an " +
                    "infinitive starts with um.",
                "damit" to "damit also expresses purpose, but with its own conjugated verb. With " +
                    "an infinitive clause use um.",
                "für" to "für is a preposition before nouns. The purpose clause starts with um.",
            ),
        ),
        GapItemTemplate(
            key = "4", prompt = "Er ging weg, ___ ein Wort zu sagen.", answer = "ohne",
            choices = listOf("ohne", "um", "mit", "zu"), ruleId = "infzu-clause-meaning",
            note = "\"Ohne ... zu\" means the second action did not happen: he left without " +
                "saying a word.",
            missByChoice = mapOf(
                "um" to "um ... zu marks a goal. He left without saying a word: ohne.",
                "mit" to "mit is a preposition before nouns. \"Without doing\" is ohne ... zu.",
                "zu" to "zu alone links the verb. \"Without doing\" needs ohne before it: ohne.",
            ),
        ),
        GapItemTemplate(
            key = "5", prompt = "Ich habe angefangen, jeden Morgen ___.", answer = "zu joggen",
            choices = listOf("zu joggen", "joggen zu", "joggen", "zum Joggen"), ruleId = "infzu-bare-vs-linked",
            note = "\"Anfangen\" requires zu + infinitive, and \"joggen\" is not separable, so zu " +
                "sits directly before it.",
            missByChoice = mapOf(
                "joggen zu" to "the linking word stands directly before the verb, never after " +
                    "it: zu joggen.",
                "joggen" to "anfangen takes the linked infinitive, so the bare form is " +
                    "incomplete: zu joggen.",
                "zum Joggen" to "zum goes before a noun (das Joggen). Here anfangen takes the " +
                    "infinitive: zu joggen.",
            ),
        ),
        GapItemTemplate(
            key = "6", prompt = "Wir haben vor, am Samstag in der Stadt ___.", answer = "einzukaufen",
            choices = listOf("einzukaufen", "zu einkaufen", "einkaufen zu", "einkaufen"), ruleId = "infzu-separable-split",
            note = "\"Vorhaben\" takes zu + infinitive; \"einkaufen\" is separable, giving " +
                "ein·zu·kaufen.",
            missByChoice = mapOf(
                "zu einkaufen" to "einkaufen is separable, so zu slides between prefix and stem: " +
                    "einzukaufen.",
                "einkaufen zu" to "the linking word stands before the stem, never after the " +
                    "verb: einzukaufen.",
                "einkaufen" to "vorhaben (Wir haben vor ...) takes zu + infinitive: einzukaufen.",
            ),
        ),
        GapItemTemplate(
            key = "7", prompt = "Es ist wichtig, jeden Tag ein bisschen Deutsch ___ sprechen.", answer = "zu",
            choices = listOf("zu", "um", "ohne", "für"), ruleId = "infzu-bare-vs-linked",
            note = "After an adjective like \"wichtig\" the infinitive construction also needs zu.",
            missByChoice = mapOf(
                "um" to "um starts a purpose clause (um ... zu). Here the infinitive only takes " +
                    "the linking word: zu.",
                "ohne" to "ohne ... zu says an action did not happen. Here the infinitive needs " +
                    "the linking word: zu.",
                "für" to "für is a preposition before nouns. Before an infinitive stands the " +
                    "linking word zu.",
            ),
        ),
        GapItemTemplate(
            key = "8", prompt = "Ich muss am Wochenende für die Prüfung ___.", answer = "lernen",
            choices = listOf("lernen", "zu lernen", "lernen zu", "zum Lernen"), ruleId = "infzu-bare-vs-linked",
            note = "Modal verbs like \"müssen\" take the bare infinitive — no zu.",
            missByChoice = mapOf(
                "zu lernen" to "müssen takes the bare infinitive, with no linking word: lernen.",
                "lernen zu" to "müssen takes the bare infinitive — and the linking word never " +
                    "follows the verb: lernen.",
                "zum Lernen" to "zum goes before nouns. After müssen the infinitive stays bare: " +
                    "lernen.",
            ),
        ),
        GapItemTemplate(
            key = "9", prompt = "Vergiss nicht, das Licht ___, wenn du ins Bett gehst.", answer = "auszumachen",
            choices = listOf("auszumachen", "zu ausmachen", "ausmachen zu", "ausmachen"), ruleId = "infzu-separable-split",
            note = "\"Ausmachen\" is separable, so zu goes between the prefix and the stem: " +
                "aus·zu·machen.",
            missByChoice = mapOf(
                "zu ausmachen" to "ausmachen is separable, so zu slides between prefix and stem: " +
                    "auszumachen.",
                "ausmachen zu" to "the linking word stands before the stem, never after the " +
                    "verb: auszumachen.",
                "ausmachen" to "vergessen takes zu + infinitive, so the bare form is incomplete: " +
                    "auszumachen.",
            ),
        ),
        GapItemTemplate(
            key = "10", prompt = "Wir nehmen ein Taxi, ___ den Zug nicht zu verpassen.", answer = "um",
            choices = listOf("um", "ohne", "für", "zu"), ruleId = "infzu-clause-meaning",
            note = "\"Um ... zu\" gives the reason for the action: taking the taxi in order not " +
                "to miss the train.",
            missByChoice = mapOf(
                "ohne" to "ohne ... zu says the action does not happen. Not missing the train is " +
                    "the goal: um.",
                "für" to "für goes before nouns. The purpose word before a zu-clause is um.",
                "zu" to "zu alone links the verb. This clause needs the purpose word before it: " +
                    "um.",
            ),
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
            missByChoice = mapOf(
                "dürft" to "that is the ihr form. The subject is du, so darfst.",
                "kannst" to "können is about ability. This sentence is about permission, so " +
                    "darfst.",
                "muss" to "müssen is about obligation. This sentence is about permission, so " +
                    "darfst.",
            ),
        ),
        GapItemTemplate(
            key = "2", prompt = "Ich ___ sehr gut schwimmen.", answer = "kann",
            choices = listOf("kann", "kannst", "muss", "darf"), ruleId = "modal-meaning",
            note = "\"können\" is about ability, and with \"ich\" the form is \"kann\".",
            missByChoice = mapOf(
                "kannst" to "that is the du form. The subject is ich, which shares its form with " +
                    "er, so kann.",
                "muss" to "müssen is about obligation. Swimming well is an ability, so kann.",
                "darf" to "dürfen is about permission. This sentence is about ability, so kann.",
            ),
        ),
        GapItemTemplate(
            key = "3", prompt = "Er ___ um acht Uhr im Büro sein, der Chef ist sehr streng.", answer = "muss",
            choices = listOf("muss", "musst", "müsst", "kann"), ruleId = "modal-meaning",
            note = "\"müssen\" expresses obligation, and with \"er\" the form is \"muss\".",
            missByChoice = mapOf(
                "musst" to "that is the du form. The subject is er, so muss.",
                "müsst" to "that is the ihr form. The subject is er, so muss.",
                "kann" to "können is about ability. A strict boss makes this an obligation, so " +
                    "muss.",
            ),
        ),
        GapItemTemplate(
            key = "4", prompt = "Wir ___ morgen früh aufstehen, der Zug fährt um sechs Uhr.", answer = "müssen",
            choices = listOf("müssen", "musst", "müsst", "dürfen"), ruleId = "modal-meaning",
            note = "The early train forces it, so it is an obligation, and \"wir\" takes \"müssen\".",
            missByChoice = mapOf(
                "musst" to "that is the du form. The subject is wir, which takes the infinitive " +
                    "form, so müssen.",
                "müsst" to "that is the ihr form. The subject is wir, so müssen.",
                "dürfen" to "dürfen is about permission. The early train forces it — an " +
                    "obligation, so müssen.",
            ),
        ),
        GapItemTemplate(
            key = "5", prompt = "Entschuldigung, ___ Sie mir bitte helfen?", answer = "Können",
            choices = listOf("Können", "Kann", "Kannst", "Könnt"), ruleId = "modal-person-form",
            note = "Formal \"Sie\" uses the infinitive form, so the polite request is " +
                "\"Können Sie ... helfen?\".",
            missByChoice = mapOf(
                "Kann" to "that is the ich/er form. Formal Sie takes the infinitive form, so " +
                    "Können.",
                "Kannst" to "that is the du form. Formal Sie takes the infinitive form, so Können.",
                "Könnt" to "that is the ihr form. Formal Sie takes the infinitive form, so Können.",
            ),
        ),
        GapItemTemplate(
            key = "6", prompt = "Man ___ in der Bibliothek nicht laut sprechen.", answer = "darf",
            choices = listOf("darf", "darfst", "dürft", "dürfen"), ruleId = "modal-meaning",
            note = "Public rules use \"dürfen\": \"man darf nicht\" means it is not allowed, and " +
                "\"man\" takes \"darf\".",
            missByChoice = mapOf(
                "darfst" to "that is the du form. The subject is man, which takes the same form " +
                    "as er, so darf.",
                "dürft" to "that is the ihr form. The subject is man, so darf.",
                "dürfen" to "that is the wir/Sie form. The subject is man, so darf.",
            ),
        ),
        GapItemTemplate(
            key = "7", prompt = "Der Arzt sagt, ich ___ mehr Wasser trinken.", answer = "soll",
            choices = listOf("soll", "sollst", "sollt", "sollen"), ruleId = "modal-person-form",
            note = "\"sollen\" is for advice or instructions from someone else, and with \"ich\" " +
                "the form is \"soll\".",
            missByChoice = mapOf(
                "sollst" to "that is the du form. The subject is ich, which shares its form with " +
                    "er, so soll.",
                "sollt" to "that is the ihr form. The subject is ich, so soll.",
                "sollen" to "that is the wir/Sie form. The subject is ich, so soll.",
            ),
        ),
        GapItemTemplate(
            key = "8", prompt = "Ich ___ keinen Fisch, der schmeckt mir nicht.", answer = "mag",
            choices = listOf("mag", "magst", "mögt", "muss"), ruleId = "modal-meaning",
            note = "\"mögen\" expresses liking, so \"ich mag keinen Fisch\" means \"I don't like " +
                "fish\".",
            missByChoice = mapOf(
                "magst" to "that is the du form. The subject is ich, which shares its form with " +
                    "er, so mag.",
                "mögt" to "that is the ihr form. The subject is ich, so mag.",
                "muss" to "müssen is about obligation. This sentence is about liking, so mag.",
            ),
        ),
        GapItemTemplate(
            key = "9", prompt = "Ich ___ einen Hund haben, aber meine Wohnung ist zu klein.", answer = "will",
            choices = listOf("will", "willst", "wollt", "wollen"), ruleId = "modal-person-form",
            note = "\"wollen\" expresses wanting, and with \"ich\" the form is \"will\".",
            missByChoice = mapOf(
                "willst" to "that is the du form. The subject is ich, which shares its form with " +
                    "er, so will.",
                "wollt" to "that is the ihr form. The subject is ich, so will.",
                "wollen" to "that is the wir/Sie form. The subject is ich, so will.",
            ),
        ),
        GapItemTemplate(
            key = "10", prompt = "___ ihr mich am Wochenende besuchen?", answer = "Könnt",
            choices = listOf("Könnt", "Können", "Kannst", "Kann"), ruleId = "modal-person-form",
            note = "Asking whether \"you all\" are able to do it uses \"können\", and \"ihr\" " +
                "takes the form \"könnt\".",
            missByChoice = mapOf(
                "Können" to "that is the wir/Sie form. The subject is ihr, so Könnt.",
                "Kannst" to "that is the du form. The subject is ihr, so Könnt.",
                "Kann" to "that is the ich/er form. The subject is ihr, so Könnt.",
            ),
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
            missByChoice = mapOf(
                "ist" to "the normal present. Reported after sagt, sein takes its Konjunktiv I " +
                    "form, so sei.",
                "war" to "the past. The state is reported as present, so sei.",
                "wäre" to "Konjunktiv II. It steps in only when the Konjunktiv I matches the " +
                    "normal present; sei does not, so sei.",
            ),
        ),
        GapItemTemplate(
            key = "2", prompt = "Die Eltern sagen, sie ___ am Wochenende länger schlafen.", answer = "müssten",
            choices = listOf("müssten", "müssen", "mussten", "müsset"), ruleId = "konj1-switch",
            note = "B2 — with plural \"sie\", the Konjunktiv I form \"müssen\" looks identical to " +
                "the normal present, so German switches to Konjunktiv II \"müssten\".",
            missByChoice = mapOf(
                "müssen" to "the normal present. With sie the Konjunktiv I is also müssen, so " +
                    "German switches to Konjunktiv II, müssten.",
                "mussten" to "the past. The report is present, and since the Konjunktiv I " +
                    "matches the present, Konjunktiv II müssten steps in.",
            ),
        ),
        GapItemTemplate(
            key = "3", prompt = "Sie meint, das Konzert ___ um acht Uhr.", answer = "beginne",
            choices = listOf("beginne", "beginnt", "begann", "begänne"), ruleId = "konj1-present",
            note = "B2 — indirect speech after \"meint\" puts the reported verb into Konjunktiv " +
                "I: \"beginnt\" becomes \"beginne\".",
            missByChoice = mapOf(
                "beginnt" to "the normal present. Reported after meint, the verb takes " +
                    "Konjunktiv I, so beginne.",
                "begann" to "the past. The starting time is reported as present, so beginne.",
                "begänne" to "Konjunktiv II. It steps in only when the Konjunktiv I matches the " +
                    "normal present; beginne does not, so beginne.",
            ),
        ),
        GapItemTemplate(
            key = "4", prompt = "Er sagt, er ___ nächsten Monat nach Berlin.", answer = "komme",
            choices = listOf("komme", "kommt", "kam", "käme"), ruleId = "konj1-present",
            note = "B2 — \"komme\" is the Konjunktiv I form that reports the present \"kommt\" " +
                "after \"Er sagt\".",
            missByChoice = mapOf(
                "kommt" to "the normal present. Reported after sagt, the verb takes Konjunktiv " +
                    "I, so komme.",
                "kam" to "the past. The plan is reported as present, so komme.",
                "käme" to "Konjunktiv II. It steps in only when the Konjunktiv I matches the " +
                    "normal present; komme does not, so komme.",
            ),
        ),
        GapItemTemplate(
            key = "5", prompt = "Im Radio sagt man, das Wetter ___ bis Freitag gut.", answer = "bleibe",
            choices = listOf("bleibe", "bleibt", "blieb", "bliebe"), ruleId = "konj1-present",
            note = "B2 — a reported present stays present but shifts to Konjunktiv I, so " +
                "\"bleibt\" becomes \"bleibe\".",
            missByChoice = mapOf(
                "bleibt" to "the normal present. Reported after sagt man, the verb takes " +
                    "Konjunktiv I, so bleibe.",
                "blieb" to "the past. The forecast is stated as present, so bleibe.",
                "bliebe" to "Konjunktiv II. It steps in only when the Konjunktiv I matches the " +
                    "normal present; bleibe does not, so bleibe.",
            ),
        ),
        GapItemTemplate(
            key = "6", prompt = "Er sagt, er ___ sehr gut kochen.", answer = "könne",
            choices = listOf("könne", "kann", "konnte", "könnte"), ruleId = "konj1-present",
            note = "B2 — modal verbs also take Konjunktiv I in indirect speech; \"könne\" " +
                "reports \"kann\".",
            missByChoice = mapOf(
                "kann" to "the normal present. Reported after sagt, the modal takes Konjunktiv " +
                    "I, so könne.",
                "konnte" to "the past. The claim is about the present, so könne.",
                "könnte" to "Konjunktiv II. It steps in only when the Konjunktiv I matches the " +
                    "normal present; er könne does not, so könne.",
            ),
        ),
        GapItemTemplate(
            key = "7", prompt = "Er behauptet, er ___ das Buch schon gelesen.", answer = "habe",
            choices = listOf("habe", "hat", "hatte", "hätte"), ruleId = "konj1-perfect",
            note = "B2 — a reported perfect tense uses Konjunktiv I \"habe\" plus the participle " +
                "instead of \"hat ... gelesen\".",
            missByChoice = mapOf(
                "hat" to "the normal perfect. Reported after behauptet, haben takes Konjunktiv " +
                    "I, so habe das Buch schon gelesen.",
                "hatte" to "the past form of haben. The report is a perfect with Konjunktiv I, " +
                    "so habe das Buch schon gelesen.",
                "hätte" to "Konjunktiv II. It steps in only when the Konjunktiv I matches the " +
                    "normal present; er habe does not, so habe.",
            ),
        ),
        GapItemTemplate(
            key = "8", prompt = "Sie sagt, er ___ dich morgen anrufen.", answer = "werde",
            choices = listOf("werde", "wird", "wurde", "würde"), ruleId = "konj1-future",
            note = "B2 — a reported future uses Konjunktiv I \"werde\" plus the infinitive, the " +
                "reported form of \"wird\".",
            missByChoice = mapOf(
                "wird" to "the normal future. Reported after sagt, werden takes Konjunktiv I, so " +
                    "werde dich anrufen.",
                "wurde" to "the past of werden. The report is a future with Konjunktiv I, so " +
                    "werde.",
                "würde" to "Konjunktiv II. It steps in only when the Konjunktiv I matches the " +
                    "normal present; er werde does not, so werde.",
            ),
        ),
        GapItemTemplate(
            key = "9", prompt = "Er sagt, er ___ die Antwort nicht.", answer = "wisse",
            choices = listOf("wisse", "weiß", "wusste", "wüsste"), ruleId = "konj1-present",
            note = "B2 — \"wisse\" is the Konjunktiv I of \"wissen\" used to report \"weiß\" in " +
                "indirect speech.",
            missByChoice = mapOf(
                "weiß" to "the normal present. Reported after sagt, the verb takes Konjunktiv I, " +
                    "so wisse.",
                "wusste" to "the past. The statement is about the present, so wisse.",
                "wüsste" to "Konjunktiv II. It steps in only when the Konjunktiv I matches the " +
                    "normal present; wisse does not, so wisse.",
            ),
        ),
        GapItemTemplate(
            key = "10", prompt = "Sie sagt, ihr Vater ___ jeden Morgen mit dem Fahrrad.", answer = "fahre",
            choices = listOf("fahre", "fährt", "fuhr", "führe"), ruleId = "konj1-present",
            note = "B2 — most verbs build Konjunktiv I regularly from the stem, so the reported " +
                "present of \"fährt\" is \"fahre\".",
            missByChoice = mapOf(
                "fährt" to "the normal present. Reported after sagt, the verb takes Konjunktiv " +
                    "I, so fahre.",
                "fuhr" to "the past. The habit is reported as present, so fahre.",
                "führe" to "Konjunktiv II. It steps in only when the Konjunktiv I matches the " +
                    "normal present; fahre does not, so fahre.",
            ),
        ),
    )

    fun items(): List<DrillItem> =
        DrillGapTopics.itemsOf(DrillType.KONJUNKTIV_I, TEMPLATES)
}
