package app.olauncher.data.store

/**
 * Help-card content model (#651 design, docs/design/drill-help-card.md §3):
 *
 * The rule, table and example belong to a **rule**, not to each item. Items point to
 * a rule id ([DrillItem.ruleId]); the same rule can carry items of different gap
 * topics (for example `passiv-aux-form` in both the passive and the werden drill).
 *
 * Pre-answer content ([DrillHelpRule.rule], [DrillHelpRule.firstExposureRule] and
 * [DrillHelpRule.cue]) teaches the general pattern; the forms table shows the whole
 * contrast set with equal weight. Naming the item's answer is only allowed after an
 * answer (miss line) or in the flashcard state — guarded by DrillHelpContentTest and
 * the DrillContentAuditTest leak checks.
 */
data class DrillHelpRow(
    /** Left row label ("Nom", "Present", "sein", or "" when the row has none). */
    val label: String,
    /** The data cells, aligned with [DrillHelpTable.headers] after its label column. */
    val cells: List<String>,
)

data class DrillHelpTable(
    /** Column headers; the first entry is the label column's header (often empty). */
    val headers: List<String>,
    /** At most 3 rows (design §3: at most 3 rows × 4 data columns). */
    val rows: List<DrillHelpRow>,
) {
    /** All German text in the table: headers, row labels and cells. */
    fun allText(): String = headers.joinToString(" ") + " " +
        rows.joinToString(" ") { it.label + " " + it.cells.joinToString(" ") }
}

data class DrillHelpRule(
    /** Stable rule id, as authored in docs/design/drill-content/. */
    val id: String,
    /**
     * Short English name of the pattern (at most about 40 characters, sentence case).
     * It names the pattern, never an item's answer; it labels the ask-ai rule thread (#699).
     */
    val title: String,
    /** 1–2 plain English sentences (pre-answer), at most about 170 characters. */
    val rule: String,
    /**
     * Shown only on first exposure (flashcard and first tap): the one place a
     * one-case preposition may name its case before an answer (design §2).
     */
    val firstExposureRule: String? = null,
    val table: DrillHelpTable,
    /**
     * Example sentence with the changing form wrapped in `**…**` (bold+underline
     * when rendered). Shown in the flashcard state and after a miss, never before
     * a tap answer (design §3).
     */
    val example: String,
    /** The collapsed cue line of the later rungs (the item's `hint`). */
    val cue: String,
)

/**
 * Registry of the help-card rules: rule id → rule. The gap-topic rules (#651) and
 * the live drill-type rules (#699) share it; common rules appear once (for example
 * `passiv-aux-form`, used by both the passive and werden topics).
 */
object DrillHelpContent {

    private val rules: Map<String, DrillHelpRule> by lazy { allRules().associateBy { it.id } }

    /** The help content for [ruleId], or null when the item carries none. */
    fun rule(ruleId: String?): DrillHelpRule? = ruleId?.let { rules[it] }

    /** Every rule id in the content files that items point to. */
    fun ids(): Set<String> = rules.keys

    /**
     * A rule's chips as one-line facts of its forms table, for the Ask-AI miss
     * grounding (docs/design/ask-ai-chat.md §Chat screen: the card shows what the
     * AI sees). Each chip is one table row — its label and the cells it holds — so
     * the pattern travels with its meaning: `Dat: dem der dem den`, not the bare
     * column headers `m, f, n, pl`, which detached from the table say nothing and
     * for a which-case question suggest a gender decision that is not part of the
     * reasoning (#721).
     */
    fun ruleChips(ruleId: String?): List<String> {
        val table = rule(ruleId)?.table ?: return emptyList()
        return table.rows.map { row ->
            val cells = row.cells.filter { it.isNotBlank() }.joinToString(" ")
            when {
                row.label.isBlank() && cells.isBlank() -> ""
                row.label.isBlank() -> cells
                cells.isBlank() -> row.label
                else -> "${row.label}: $cells"
            }
        }.filter { it.isNotBlank() }
    }

    /** Every authored rule, gap topics and live topics together, in authored order. */
    internal fun allRules(): List<DrillHelpRule> =
        gapTopicRules() + DrillHelpContentLive.rules

    /**
     * Checks [ruleId] and passes it through. Item builders call it so a typo'd id
     * fails at build time instead of silently dropping the help card.
     */
    internal fun requireValidRuleId(ruleId: String, context: String): String {
        require(ruleId in rules) { "Unknown help rule id $ruleId for $context" }
        return ruleId
    }

    private fun gapTopicRules(): List<DrillHelpRule> = listOf(
        // -----------------------------------------------------------------
        // passive.md (family passiv) — also serves werden items 6-9
        // -----------------------------------------------------------------
        DrillHelpRule(
            id = "passiv-aux-form",
            title = "The passive: the helper verb",
            rule = "A passive says the action happens to the subject. Its helper matches the subject " +
                "in number and carries the tense. sein with a participle only describes a state.",
            table = DrillHelpTable(
                headers = listOf("", "ich", "er/sie/es", "plural"),
                rows = listOf(
                    DrillHelpRow("Present", listOf("werde", "wird", "werden")),
                    DrillHelpRow("Past", listOf("wurde", "wurde", "wurden")),
                ),
            ),
            example = "Die Bücher **werden** oft gelesen.",
            cue = "The helper matches the subject in number and the time word in tense, like any verb.",
        ),
        DrillHelpRule(
            id = "passiv-perfekt-sein",
            title = "The perfect passive",
            rule = "The perfect passive is built with a form of sein, never haben, plus Partizip II " +
                "and worden. The sein form matches its subject: one thing or several.",
            table = DrillHelpTable(
                headers = listOf("", "singular", "plural"),
                rows = listOf(
                    DrillHelpRow("sein, present", listOf("ist", "sind")),
                    DrillHelpRow("haben, present", listOf("hat", "haben")),
                ),
            ),
            example = "Die Wohnung **ist** schon verkauft worden.",
            cue = "Perfect passive: a form of sein + Partizip II + worden. haben never appears.",
        ),
        DrillHelpRule(
            id = "passiv-perfekt-partizip",
            title = "The participle in the perfect passive",
            rule = "In the perfect passive the helper verb has its own special participle. The " +
                "participle it uses as an ordinary full verb is a different word. The table shows both.",
            table = DrillHelpTable(
                headers = listOf("", "as the passive helper", "as a full verb"),
                rows = listOf(
                    DrillHelpRow("Perfect", listOf("ist repariert worden", "ist müde geworden")),
                ),
            ),
            example = "Die Suppe ist schon gekocht **worden**.",
            cue = "Perfect passive: sein + Partizip II + the helper's special participle, not its full-verb form.",
        ),
        DrillHelpRule(
            id = "passiv-agent",
            title = "Passive: doer, means and tool",
            rule = "A passive can tell who did the action and how. The doer, the means or cause and " +
                "the tool each take their own preposition. Match each noun to its role.",
            table = DrillHelpTable(
                headers = listOf("", "who did it", "means or cause", "tool", "place"),
                rows = listOf(DrillHelpRow("", listOf("von", "durch", "mit", "bei"))),
            ),
            example = "Der Text wird **von** dem Lehrer korrigiert.",
            cue = "Is the noun the doer, the means or the tool? Each role takes its own preposition.",
        ),
        DrillHelpRule(
            id = "prep-fixed-dat",
            title = "Prepositions with the dative",
            rule = "This preposition is one of those that always take the same case, whatever the " +
                "sentence says. Learn which one it takes.",
            firstExposureRule = "aus, bei, mit, nach, seit, von, zu and gegenüber always take the dative.",
            table = DrillHelpTable(
                headers = listOf("", "m", "f", "n", "pl"),
                rows = listOf(
                    DrillHelpRow("Akk", listOf("den", "die", "das", "die")),
                    DrillHelpRow("Dat", listOf("dem", "der", "dem", "den")),
                    DrillHelpRow("Gen", listOf("des", "der", "des", "der")),
                ),
            ),
            example = "Ich fahre mit **dem** Bus.",
            cue = "This preposition keeps one case. Recall it, then the noun's gender.",
        ),
        DrillHelpRule(
            id = "passiv-partizip-ii",
            title = "The action word in the passive",
            rule = "The helper of a passive carries the person and the tense. The action word after " +
                "it never conjugates: it stands as the Partizip II, the form the perfect also uses.",
            table = DrillHelpTable(
                headers = listOf("", "conjugated", "infinitive", "Partizip II"),
                rows = listOf(
                    DrillHelpRow("trinken", listOf("trinkt", "trinken", "getrunken")),
                    DrillHelpRow("spülen", listOf("spült", "spülen", "gespült")),
                ),
            ),
            example = "Das Geschirr wird jetzt **gewaschen**.",
            cue = "The helper carries person and tense; the action word after it stays the Partizip II.",
        ),

        // -----------------------------------------------------------------
        // werden.md
        // -----------------------------------------------------------------
        DrillHelpRule(
            id = "werden-conjugation",
            title = "The verb werden in the present",
            rule = "The verb werden means \"to become\". Like other German verbs, its present ending " +
                "changes to match the person of the subject.",
            table = DrillHelpTable(
                headers = listOf("", "ich", "du", "dritte Person"),
                rows = listOf(
                    DrillHelpRow("Singular", listOf("werde", "wirst", "wird (er · sie · es)")),
                    DrillHelpRow("Plural", listOf("werden (wir)", "werdet (ihr)", "werden (sie · Sie)")),
                ),
            ),
            example = "Er **wird** Arzt.",
            cue = "The subject pronoun picks the form: ich, du, er, wir, ihr, sie.",
        ),
        DrillHelpRule(
            id = "futur-helper-form",
            title = "The future tense",
            rule = "To talk about the future, German pairs a subject-matched form of werden with the " +
                "infinitive at the end of the sentence.",
            table = DrillHelpTable(
                headers = listOf("", "ich", "du", "dritte Person"),
                rows = listOf(
                    DrillHelpRow("Singular", listOf("werde", "wirst", "wird (er · sie · es)")),
                    DrillHelpRow("Plural", listOf("werden (wir)", "werdet (ihr)", "werden (sie · Sie)")),
                ),
            ),
            example = "Ich **werde** dir morgen beim Umzug helfen.",
            cue = "Infinitive at the end + werden form matched to the subject = future.",
        ),
        DrillHelpRule(
            id = "werden-subjunctive-ii",
            title = "Real or imagined situations",
            rule = "German uses one werden form for what would happen in an imagined situation, and " +
                "another for a real plan. The sentence decides which fits.",
            table = DrillHelpTable(
                headers = listOf("", "real plan", "imagined"),
                rows = listOf(
                    DrillHelpRow("with ich", listOf("Ich werde es kaufen.", "Ich würde es kaufen.")),
                ),
            ),
            example = "An deiner Stelle **würde** ich das Angebot annehmen.",
            cue = "Is this a real plan or an imagined situation? Each takes its own werden form.",
        ),

        // -----------------------------------------------------------------
        // konjunktiv-ii.md
        // -----------------------------------------------------------------
        DrillHelpRule(
            id = "konj2-wenn-unreal",
            title = "Unreal wenn-clauses",
            rule = "Wenn can open an unreal condition: something imagined, not real. The normal " +
                "present and past state facts, so German uses its Konjunktiv II forms here instead.",
            table = DrillHelpTable(
                headers = listOf("", "haben", "sein", "most other verbs"),
                rows = listOf(
                    DrillHelpRow("Konjunktiv II", listOf("hätte", "wäre", "würde + infinitive")),
                ),
            ),
            example = "Wenn ich Zeit **hätte**, käme ich mit.",
            cue = "Imagined wenn-clause → Konjunktiv II, not the real present or past.",
        ),
        DrillHelpRule(
            id = "konj2-subject-agreement",
            title = "The unreal form matches the subject",
            rule = "An unreal form still has to match its subject, like every German verb form. Find " +
                "the subject — ich, du, ihr — and take that person's form.",
            table = DrillHelpTable(
                headers = listOf("", "ich / er", "du", "ihr", "wir / sie"),
                rows = listOf(
                    DrillHelpRow("haben", listOf("hätte", "hättest", "hättet", "hätten")),
                    DrillHelpRow("sein", listOf("wäre", "wärst", "wärt", "wären")),
                ),
            ),
            example = "Wenn ihr mehr Geld **hättet**, würdet ihr ein neues Auto kaufen.",
            cue = "Check who the subject is, then take that person's unreal form.",
        ),
        DrillHelpRule(
            id = "konj2-helper-infinitive",
            title = "The two-word unreal form",
            rule = "For verbs besides haben and sein, German builds the unreal form from two words: " +
                "helper plus dictionary form — no ending, no past, no participle.",
            table = DrillHelpTable(
                headers = listOf("", "haben", "sein", "most other verbs"),
                rows = listOf(
                    DrillHelpRow("Konjunktiv II", listOf("hätte", "wäre", "würde + infinitive")),
                ),
            ),
            example = "Wenn du mir **helfen** würdest, wäre ich sehr dankbar.",
            cue = "Other verb → two-word unreal form: helper plus dictionary form.",
        ),
        DrillHelpRule(
            id = "konj2-past-wish",
            title = "Wishes about the past",
            rule = "An unreal form plus a participle wishes the past had gone differently — something " +
                "that did not happen. The real past and present forms do not fit.",
            table = DrillHelpTable(
                headers = listOf("", "Konjunktiv II", "present", "past"),
                rows = listOf(
                    DrillHelpRow("sein", listOf("wäre", "bin · ist", "war")),
                    DrillHelpRow("haben", listOf("hätte", "habe · hat", "hatte")),
                    DrillHelpRow("werden", listOf("würde", "werde · wird", "wurde")),
                ),
            ),
            example = "Ich **wäre** gern mitgekommen, aber ich musste arbeiten.",
            cue = "A past that did not happen → unreal form plus participle, not the real past.",
        ),
        DrillHelpRule(
            id = "konj2-single-form",
            title = "Verbs with their own unreal form",
            rule = "Many common verbs have their own single unreal form besides the two-word form. " +
                "With gern, it makes a wish sound soft and polite.",
            table = DrillHelpTable(
                headers = listOf("", "kommen", "gehen", "wissen"),
                rows = listOf(
                    DrillHelpRow("Konjunktiv II", listOf("käme", "ginge", "wüsste")),
                    DrillHelpRow("present", listOf("komme", "gehe", "weiß")),
                    DrillHelpRow("past", listOf("kam", "ging", "wusste")),
                ),
            ),
            example = "Ich **wüsste** gern, wo sie wohnt.",
            cue = "Wish with gern → the verb's own single unreal form, not present or past.",
        ),
        DrillHelpRule(
            id = "konj2-polite-request",
            title = "Polite requests",
            rule = "To order or ask for something politely, German phrases it as a wish: the unreal " +
                "form plus gern. The real present or past states a fact instead.",
            table = DrillHelpTable(
                headers = listOf("", "Konjunktiv II", "present", "past"),
                rows = listOf(
                    DrillHelpRow("haben", listOf("hätte", "habe", "hatte")),
                    DrillHelpRow("sein", listOf("wäre", "bin", "war")),
                    DrillHelpRow("werden", listOf("würde", "werde", "wurde")),
                ),
            ),
            example = "Ich **hätte** gern einen Kaffee, bitte.",
            cue = "Polite order → unreal form plus gern, not the real present or past.",
        ),

        // -----------------------------------------------------------------
        // relative-pronouns.md
        // -----------------------------------------------------------------
        DrillHelpRule(
            id = "rel-agreement",
            title = "Relative pronouns: agreement",
            rule = "A relative pronoun points back to a noun. Its gender and number copy that noun, " +
                "but its case is set by its own role inside the relative clause.",
            table = DrillHelpTable(
                headers = listOf("", "m", "f", "n", "pl"),
                rows = listOf(
                    DrillHelpRow("Nom", listOf("der", "die", "das", "die")),
                    DrillHelpRow("Akk", listOf("den", "die", "das", "die")),
                    DrillHelpRow("Dat", listOf("dem", "der", "dem", "denen")),
                ),
            ),
            example = "Das Kind, **das** dort spielt, ist fünf Jahre alt.",
            cue = "Gender and number come from the noun; the case comes from the pronoun's own role in the clause.",
        ),
        DrillHelpRule(
            id = "rel-after-prep",
            title = "Relative pronouns after a preposition",
            rule = "A preposition in front of the gap decides the pronoun's case, exactly as with " +
                "articles. Gender and number still copy the noun it refers to.",
            firstExposureRule = "mit and von always take the dative, so the relative pronoun after " +
                "them uses the dative row, matching the noun's gender and number.",
            table = DrillHelpTable(
                headers = listOf("", "m", "f", "n", "pl"),
                rows = listOf(
                    DrillHelpRow("Akk", listOf("den", "die", "das", "die")),
                    DrillHelpRow("Dat", listOf("dem", "der", "dem", "denen")),
                    DrillHelpRow("Gen", listOf("dessen", "deren", "dessen", "deren")),
                ),
            ),
            example = "Der Kollege, mit **dem** ich arbeite, kommt aus Bonn.",
            cue = "After a preposition, the preposition — not the clause — decides the pronoun's form.",
        ),
        DrillHelpRule(
            id = "rel-genitive",
            title = "Possessive relative pronouns",
            rule = "When a noun sits right after the gap, the clause shows possession (his car, her " +
                "son). The pronoun then takes a special possessive form, matching the noun it refers to.",
            table = DrillHelpTable(
                headers = listOf("", "m", "f", "n", "pl"),
                rows = listOf(
                    DrillHelpRow("possession", listOf("dessen", "deren", "dessen", "deren")),
                ),
            ),
            example = "Der Mann, **dessen** Auto rot ist, ist mein Nachbar.",
            cue = "A noun right after the gap means possession: use the special possessive form.",
        ),

        // -----------------------------------------------------------------
        // genitive.md
        // -----------------------------------------------------------------
        DrillHelpRule(
            id = "gen-article",
            title = "The owner's article",
            rule = "For possession, the owner's article takes a special form that depends on the " +
                "owner's gender. The owner noun often adds an ending too.",
            table = DrillHelpTable(
                headers = listOf("", "m", "f", "n", "pl"),
                rows = listOf(
                    DrillHelpRow("Wessen?", listOf("des", "der", "des", "der")),
                ),
            ),
            example = "Das ist die Farbe **des** Meeres.",
            cue = "Wessen? The owner's article follows its gender.",
        ),
        DrillHelpRule(
            id = "gen-noun-s",
            title = "Owner nouns ending in -s",
            rule = "After des, the owner noun adds -s or -es. Many one-syllable nouns take -es " +
                "(des Mannes); longer nouns take plain -s (des Lehrers).",
            table = DrillHelpTable(
                headers = listOf("", "longer nouns", "many one-syllable nouns"),
                rows = listOf(
                    DrillHelpRow("with des", listOf("des Lehrers", "des Mannes")),
                ),
            ),
            example = "Ich lese den Anfang des Buch**es**.",
            cue = "After des the noun adds -s — or the fuller -es when the word is short.",
        ),
        DrillHelpRule(
            id = "gen-noun-weak",
            title = "Weak nouns in the genitive",
            rule = "Some nouns never take the -s owner ending. These \"weak\" nouns — many are words " +
                "for people or animals — add -n or -en instead after des.",
            table = DrillHelpTable(
                headers = listOf("", "most nouns", "weak nouns"),
                rows = listOf(
                    DrillHelpRow("with des", listOf("des Films", "des Menschen")),
                ),
            ),
            example = "Das ist das Auto des Nachbar**n**.",
            cue = "Is this a weak noun? Weak nouns never take the -s owner ending.",
        ),
        DrillHelpRule(
            id = "gen-time-phrase",
            title = "Fixed time phrases",
            rule = "Some time phrases stand in a fixed case without any preposition. Learn each one " +
                "as a whole phrase, article form included.",
            table = DrillHelpTable(
                headers = listOf("", "Nom", "Gen", "Dat"),
                rows = listOf(
                    DrillHelpRow("m · n", listOf("ein", "eines", "einem")),
                    DrillHelpRow("f", listOf("eine", "einer", "einer")),
                ),
            ),
            example = "**Eines** Tages erzähle ich dir alles.",
            cue = "A fixed time phrase: recall it as a whole, article included.",
        ),
        DrillHelpRule(
            id = "gen-question-whose",
            title = "The question word for 'whose'",
            rule = "German has a separate question word for \"whose\". The other who-words ask who " +
                "(wer), whom (wen) and to whom (wem); the whose-word asks for the owner.",
            table = DrillHelpTable(
                headers = listOf("", "wer", "wen", "wem", "wessen"),
                rows = listOf(
                    DrillHelpRow("asks", listOf("who (subject)", "whom (object)", "to whom", "whose")),
                ),
            ),
            example = "**Wessen** Schlüssel ist das?",
            cue = "\"Whose\" has its own question word — not wer, wen or wem.",
        ),
        DrillHelpRule(
            id = "gen-kinship-s",
            title = "Kinship nouns as owners",
            rule = "Kinship nouns and names can mark the owner the short way: no article, just an " +
                "added -s — like an English apostrophe-s. The rest of the noun stays as is.",
            table = DrillHelpTable(
                headers = listOf("", "with article", "without article"),
                rows = listOf(
                    DrillHelpRow("owner", listOf("des Opas", "Opas")),
                ),
            ),
            example = "Vater**s** altes Fahrrad steht in der Garage.",
            cue = "No article here: this owner works like a name and takes bare -s.",
        ),
        DrillHelpRule(
            id = "gen-noun-zero",
            title = "Owner noun endings",
            rule = "After the owner article, a noun's ending depends on its group: many add -(e)s, " +
                "weak ones add -n or -en, and plural nouns stay unchanged.",
            table = DrillHelpTable(
                headers = listOf("", "-(e)s nouns", "weak nouns", "plurals"),
                rows = listOf(
                    DrillHelpRow("owner form", listOf("des Films", "des Menschen", "der Bücher")),
                ),
            ),
            example = "Das sind die Bücher der **Schüler**.",
            cue = "Plural owners stay unchanged; only the article changes.",
        ),

        // -----------------------------------------------------------------
        // n-declension.md
        // -----------------------------------------------------------------
        DrillHelpRule(
            id = "ndecl-weak-en",
            title = "Weak nouns",
            rule = "Some German nouns, called weak, add -(e)n in every form except the bare singular " +
                "used for the sentence's subject. Learn these nouns with their endings.",
            table = DrillHelpTable(
                headers = listOf("", "Nom", "Akk", "Dat", "Gen"),
                rows = listOf(
                    DrillHelpRow("Singular", listOf("der Bär", "den Bären", "dem Bären", "des Bären")),
                    DrillHelpRow("Plural", listOf("die Bären", "die Bären", "den Bären", "der Bären")),
                ),
            ),
            example = "Wir helfen dem Nachbar**n** am Wochenende im Garten.",
            cue = "Weak noun: every form takes -(e)n except the bare subject singular — the plural too.",
        ),

        // -----------------------------------------------------------------
        // imperative.md
        // -----------------------------------------------------------------
        DrillHelpRule(
            id = "imp-person",
            title = "Imperatives: du, ihr, Sie",
            rule = "German has one imperative per way of addressing: du drops the -st and keeps the " +
                "stem, ihr takes stem + t, Sie uses the infinitive. Decide whom it addresses.",
            table = DrillHelpTable(
                headers = listOf("", "du", "ihr", "Sie"),
                rows = listOf(
                    DrillHelpRow("kommen", listOf("Komm!", "Kommt!", "Kommen Sie!")),
                    DrillHelpRow("helfen", listOf("Hilf!", "Helft!", "Helfen Sie!")),
                ),
            ),
            example = "**Zieht** eure Schuhe an!",
            cue = "Whom are you addressing: one person (du), several (ihr) or formally (Sie)?",
        ),
        DrillHelpRule(
            id = "imp-du-vowel-change",
            title = "The du imperative changes the vowel",
            rule = "Imperatives match the person you address. In the du imperative, many strong verbs " +
                "change the stem vowel (e → i or ie); ihr and Sie keep the normal stem.",
            table = DrillHelpTable(
                headers = listOf("", "du", "ihr", "Sie"),
                rows = listOf(
                    DrillHelpRow("sprechen", listOf("Sprich!", "Sprecht!", "Sprechen Sie!")),
                    DrillHelpRow("sehen", listOf("Sieh!", "Seht!", "Sehen Sie!")),
                ),
            ),
            example = "**Gib** mir bitte das Salz!",
            cue = "Whom are you addressing? Strong verbs change the stem vowel only for du.",
        ),
        DrillHelpRule(
            id = "imp-sein",
            title = "The imperative of sein",
            rule = "The verb sein has its own imperative forms, one for each way of addressing. " +
                "Decide whom the sentence addresses.",
            table = DrillHelpTable(
                headers = listOf("", "du", "ihr", "Sie"),
                rows = listOf(
                    DrillHelpRow("Present", listOf("bist", "seid", "sind")),
                    DrillHelpRow("Imperative", listOf("Sei!", "Seid!", "Seien Sie!")),
                ),
            ),
            example = "**Sei** bitte vorsichtig mit dem Glas!",
            cue = "sein has its own imperative forms — check whom you are addressing.",
        ),
        DrillHelpRule(
            id = "imp-sie-infinitive",
            title = "The formal Sie imperative",
            rule = "German has an imperative form for each person addressed: du, ihr, or formal Sie. " +
                "This sentence addresses Sie formally, so recall the form that goes with it.",
            firstExposureRule = "The formal Sie imperative is the infinitive, the dictionary form of " +
                "the verb: Kommen Sie bitte!",
            table = DrillHelpTable(
                headers = listOf("", "du", "ihr", "Sie"),
                rows = listOf(
                    DrillHelpRow("kommen", listOf("Komm!", "Kommt!", "Kommen Sie!")),
                    DrillHelpRow("warten", listOf("Warte!", "Wartet!", "Warten Sie!")),
                ),
            ),
            example = "**Nehmen** Sie bitte Platz!",
            cue = "This sentence uses formal address; recall its imperative form.",
        ),

        // -----------------------------------------------------------------
        // praeteritum.md
        // -----------------------------------------------------------------
        DrillHelpRule(
            id = "praet-sein",
            title = "The simple past of sein",
            rule = "The verb sein has its own simple past forms, not the regular -te pattern. Each " +
                "person has its own form — pick the one that matches the subject.",
            table = DrillHelpTable(
                headers = listOf("", "ich · er · sie · es", "du", "ihr", "wir · sie Pl"),
                rows = listOf(
                    DrillHelpRow("sein", listOf("war", "warst", "wart", "waren")),
                    DrillHelpRow("machen (regular)", listOf("machte", "machtest", "machtet", "machten")),
                ),
            ),
            example = "Letzte Woche **wart** ihr im Urlaub.",
            cue = "sein has its own simple past forms, one per person — never the regular -te pattern.",
        ),
        DrillHelpRule(
            id = "praet-haben",
            title = "The simple past of haben",
            rule = "The verb haben has its own simple past forms, one per person. It does not attach " +
                "the regular -te to its present stem.",
            table = DrillHelpTable(
                headers = listOf("", "ich · er · sie · es", "du", "ihr", "wir · sie Pl"),
                rows = listOf(
                    DrillHelpRow("haben", listOf("hatte", "hattest", "hattet", "hatten")),
                    DrillHelpRow("machen (regular)", listOf("machte", "machtest", "machtet", "machten")),
                ),
            ),
            example = "Gestern **hatte** ich kein Geld dabei.",
            cue = "haben has its own simple past stem; it never takes the regular -te pattern.",
        ),
        DrillHelpRule(
            id = "praet-modal",
            title = "Modal verbs in the simple past",
            rule = "Modal verbs build the simple past the regular way, with -te, and any umlaut " +
                "drops. The ending still matches the subject's person.",
            table = DrillHelpTable(
                headers = listOf("", "ich · er · sie · es", "du", "ihr", "wir · sie Pl"),
                rows = listOf(
                    DrillHelpRow("können", listOf("konnte", "konntest", "konntet", "konnten")),
                    DrillHelpRow("müssen", listOf("musste", "musstest", "musstet", "mussten")),
                    DrillHelpRow("wollen", listOf("wollte", "wolltest", "wolltet", "wollten")),
                ),
            ),
            example = "Als Kind **durfte** ich nicht lange fernsehen.",
            cue = "Modals build the simple past with plain -te and drop the umlaut.",
        ),
        DrillHelpRule(
            id = "praet-strong-vowel",
            title = "Strong verbs in the simple past",
            rule = "Strong verbs change their stem vowel in the simple past instead of taking the " +
                "regular -te. The ending still matches the subject's person.",
            table = DrillHelpTable(
                headers = listOf("", "gehen", "sehen", "geben", "fahren"),
                rows = listOf(
                    DrillHelpRow("ich · er · sie · es", listOf("ging", "sah", "gab", "fuhr")),
                    DrillHelpRow("ihr", listOf("gingt", "saht", "gabt", "fuhrt")),
                    DrillHelpRow("wir · sie Pl", listOf("gingen", "sahen", "gaben", "fuhren")),
                ),
            ),
            example = "Wir **kamen** gestern sehr spät nach Hause.",
            cue = "Strong verbs change the stem vowel; the person's ending is regular.",
        ),

        // -----------------------------------------------------------------
        // infinitiv-zu.md
        // -----------------------------------------------------------------
        DrillHelpRule(
            id = "infzu-bare-vs-linked",
            title = "Bare or linked infinitive",
            rule = "A second verb joins its trigger in one fixed way: some triggers keep the bare " +
                "infinitive, others add a linking word right before it. Learn each trigger's way.",
            table = DrillHelpTable(
                headers = listOf("", "Modalverb", "kein Modalverb"),
                rows = listOf(
                    DrillHelpRow("zweites Verb", listOf("kommen", "zu kommen")),
                ),
            ),
            example = "Ich habe keine Lust, heute **zu** arbeiten.",
            cue = "Is the first verb a modal, or another trigger? That decides the infinitive's form.",
        ),
        DrillHelpRule(
            id = "infzu-separable-split",
            title = "Separable verbs: where zu goes",
            rule = "Separable verbs split: the prefix comes off the front. When zu joins their " +
                "infinitive, it slides in between the prefix and the stem: auf·zu·hören.",
            table = DrillHelpTable(
                headers = listOf("", "nicht trennbar", "trennbar"),
                rows = listOf(
                    DrillHelpRow("Muster", listOf("zu kommen", "ab·zu·holen")),
                ),
            ),
            example = "Vergiss nicht, das Licht aus**zu**machen, wenn du ins Bett gehst.",
            cue = "Separable prefix? The linking word slides in between prefix and stem.",
        ),
        DrillHelpRule(
            id = "infzu-clause-meaning",
            title = "Purpose and what did not happen",
            rule = "A second clause can be built with ... zu and a leading word. um ... zu names the " +
                "goal; ohne ... zu says it did not happen. Pick what the sentence means.",
            table = DrillHelpTable(
                headers = listOf("", "Ziel", "nicht geschehen"),
                rows = listOf(
                    DrillHelpRow("", listOf("um ... zu kommen", "ohne ... zu kommen")),
                ),
            ),
            example = "Er ging weg, **ohne** ein Wort zu sagen.",
            cue = "What does the second clause say: the goal, or something that did not happen?",
        ),

        // -----------------------------------------------------------------
        // modal-verbs.md
        // -----------------------------------------------------------------
        DrillHelpRule(
            id = "modal-meaning",
            title = "What the modals mean",
            rule = "Each modal adds its own meaning: allowed, able, has to, or likes. Read what the " +
                "sentence means to pick the modal, then match its form to the subject.",
            table = DrillHelpTable(
                headers = listOf("", "dürfen", "können", "müssen", "mögen"),
                rows = listOf(
                    DrillHelpRow("means", listOf("allowed", "able", "has to", "likes")),
                    DrillHelpRow("ich · er · man", listOf("darf", "kann", "muss", "mag")),
                    DrillHelpRow(
                        "du · ihr · wir/Sie",
                        listOf(
                            "darfst · dürft · dürfen",
                            "kannst · könnt · können",
                            "musst · müsst · müssen",
                            "magst · mögt · mögen",
                        ),
                    ),
                ),
            ),
            example = "Du **darfst** hier nicht rauchen.",
            cue = "What does the sentence mean: allowed, able, has to, or likes? Then match the subject.",
        ),
        DrillHelpRule(
            id = "modal-person-form",
            title = "Modals: one ending per person",
            rule = "Modal verbs are irregular in the singular: ich and er share one short form with " +
                "no ending, and du adds -st. ihr adds -t, and wir and Sie use the infinitive.",
            table = DrillHelpTable(
                headers = listOf("", "ich · er · sie · es", "du", "ihr", "wir · Sie"),
                rows = listOf(
                    DrillHelpRow("können", listOf("kann", "kannst", "könnt", "können")),
                    DrillHelpRow("sollen", listOf("soll", "sollst", "sollt", "sollen")),
                    DrillHelpRow("wollen", listOf("will", "willst", "wollt", "wollen")),
                ),
            ),
            example = "Der Arzt sagt, ich **soll** mehr Wasser trinken.",
            cue = "Find the subject, then take that person's form.",
        ),

        // -----------------------------------------------------------------
        // konjunktiv-i.md
        // -----------------------------------------------------------------
        DrillHelpRule(
            id = "konj1-present",
            title = "Konjunktiv I in reported speech",
            rule = "In formal reported speech after sagt or meint, the verb shifts from the normal " +
                "present into Konjunktiv I: most verbs take stem + -e; sein has its own form.",
            table = DrillHelpTable(
                headers = listOf("", "Konjunktiv I", "Present", "Past", "Konjunktiv II"),
                rows = listOf(
                    DrillHelpRow("gehen", listOf("gehe", "geht", "ging", "ginge")),
                    DrillHelpRow("lesen", listOf("lese", "liest", "las", "läse")),
                    DrillHelpRow("sollen", listOf("solle", "soll", "sollte", "sollte")),
                ),
            ),
            example = "Sie sagt, sie arbeit**e** heute bis acht.",
            cue = "Reported after sagt or meint, the verb takes Konjunktiv I instead of the normal present.",
        ),
        DrillHelpRule(
            id = "konj1-switch",
            title = "When Konjunktiv I looks like the present",
            rule = "In reported speech, if the Konjunktiv I form would look exactly like the normal " +
                "present, German uses Konjunktiv II instead, so the reported form stays distinct.",
            table = DrillHelpTable(
                headers = listOf("", "Konjunktiv I", "Present", "Past", "Konjunktiv II"),
                rows = listOf(
                    DrillHelpRow("können", listOf("können", "können", "konnten", "könnten")),
                    DrillHelpRow("haben", listOf("haben", "haben", "hatten", "hätten")),
                ),
            ),
            example = "Die Eltern sagen, sie müss**ten** am Wochenende länger schlafen.",
            cue = "If the Konjunktiv I would look like the normal present, reported speech uses Konjunktiv II.",
        ),
        DrillHelpRule(
            id = "konj1-perfect",
            title = "Konjunktiv I in the perfect",
            rule = "A reported past event keeps the perfect with its participle, but the helper " +
                "shifts into Konjunktiv I, built like any verb: stem + -e.",
            table = DrillHelpTable(
                headers = listOf("", "Konjunktiv I", "Present", "Past", "Konjunktiv II"),
                rows = listOf(
                    DrillHelpRow("machen", listOf("mache", "macht", "machte", "machte")),
                ),
            ),
            example = "Er behauptet, er hab**e** das Buch schon gelesen.",
            cue = "A reported perfect puts its helper into Konjunktiv I and keeps the participle.",
        ),
        DrillHelpRule(
            id = "konj1-future",
            title = "Konjunktiv I in the future",
            rule = "A reported future keeps the infinitive at the end, but its helper shifts into " +
                "Konjunktiv I, built like any verb: stem + -e.",
            table = DrillHelpTable(
                headers = listOf("", "Konjunktiv I", "Present", "Past", "Konjunktiv II"),
                rows = listOf(
                    DrillHelpRow("machen", listOf("mache", "macht", "machte", "machte")),
                ),
            ),
            example = "Sie sagt, er werd**e** dich morgen anrufen.",
            cue = "A reported future puts its helper into Konjunktiv I and keeps the infinitive at the end.",
        ),
    )
}
