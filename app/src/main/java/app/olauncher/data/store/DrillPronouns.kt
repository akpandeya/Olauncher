package app.olauncher.data.store

/**
 * Pronoun drill content covering personal, reflexive, and possessive pronouns and reflexive verbs.
 */
object DrillPronouns {

    enum class Category {
        PERSONAL,
        REFLEXIVE,
        POSSESSIVE,
    }

    internal data class ItemTemplate(
        val prompt: String,
        val answer: String,
        val choices: List<String>,
        val hint: String? = null,
        val person: String? = null,
        val grammaticalCase: String? = null,
        val verb: String? = null,
    )

    internal data class PronounEntry(
        val tag: String,
        val category: Category,
        val templates: List<ItemTemplate>,
    )

    // Recognized pronoun pools for distractor validation
    val PERSONAL_PRONOUNS: Set<String> = setOf(
        "ich", "mich", "mir",
        "du", "dich", "dir",
        "er", "ihn", "ihm",
        "sie", "ihr", "ihnen",
        "es",
        "wir", "uns",
        "euch",
        "Sie", "Ihnen",
        "Ich", "Mich", "Mir",
        "Du", "Dich", "Dir",
        "Er", "Ihn", "Ihm",
        "Sie", "Ihr", "Ihnen",
        "Es",
        "Wir", "Uns",
        "Euch",
    )

    val REFLEXIVE_PRONOUNS: Set<String> = setOf(
        "mich", "mir",
        "dich", "dir",
        "sich",
        "uns",
        "euch",
        "Mich", "Mir",
        "Dich", "Dir",
        "Sich",
        "Uns",
        "Euch",
    )

    val POSSESSIVE_PRONOUNS: Set<String> = setOf(
        "mein", "meine", "meinen", "meinem", "meiner", "meines",
        "dein", "deine", "deinen", "deinem", "deiner", "deines",
        "sein", "seine", "seinen", "seinem", "seiner", "seines",
        "ihr", "ihre", "ihren", "ihrem", "ihrer", "ihres",
        "unser", "unsere", "unseren", "unserem", "unserer", "unseres",
        "euer", "eure", "euren", "eurem", "eurer", "eures",
        "Ihr", "Ihre", "Ihren", "Ihrem", "Ihrer", "Ihres",
        "Mein", "Meine", "Meinen", "Meinem", "Meiner", "Meines",
        "Dein", "Deine", "Deinen", "Deinem", "Deiner", "Deines",
        "Sein", "Seine", "Seinen", "Seinem", "Seiner", "Seines",
        "Unser", "Unsere", "Unseren", "Unserem", "Unserer", "Unseres",
        "Euer", "Eure", "Euren", "Eurem", "Eurer", "Eures",
    )

    internal val ENTRIES: List<PronounEntry> = listOf(
        // ====================================================================
        // 1. Personal Pronouns: Nom, Akk, Dat for every person (9 persons x 3 cases = 27 items)
        // ====================================================================
        PronounEntry(
            tag = "ich",
            category = Category.PERSONAL,
            templates = listOf(
                ItemTemplate(
                    prompt = "Heute habe ___ leider keine Zeit für Sport.",
                    answer = "ich",
                    choices = listOf("ich", "mich", "mir", "du"),
                    hint = "Nominativ (Subjekt)",
                    person = "1sg",
                    grammaticalCase = "Nom",
                ),
                ItemTemplate(
                    prompt = "Mein bester Freund besucht ___ heute Nachmittag.",
                    answer = "mich",
                    choices = listOf("mich", "mir", "ich", "dich"),
                    hint = "besuchen + Akkusativ",
                    person = "1sg",
                    grammaticalCase = "Akk",
                ),
                ItemTemplate(
                    prompt = "Kannst du ___ bitte bei der Aufgabe helfen?",
                    answer = "mir",
                    choices = listOf("mir", "mich", "ich", "dir"),
                    hint = "helfen + Dativ",
                    person = "1sg",
                    grammaticalCase = "Dat",
                ),
            ),
        ),
        PronounEntry(
            tag = "du",
            category = Category.PERSONAL,
            templates = listOf(
                ItemTemplate(
                    prompt = "Wann kommst ___ heute Abend nach Hause?",
                    answer = "du",
                    choices = listOf("du", "dich", "dir", "ihr"),
                    hint = "Nominativ (Subjekt)",
                    person = "2sg",
                    grammaticalCase = "Nom",
                ),
                ItemTemplate(
                    prompt = "Ich habe ___ gestern auf dem Markt gesehen.",
                    answer = "dich",
                    choices = listOf("dich", "dir", "du", "mich"),
                    hint = "sehen + Akkusativ",
                    person = "2sg",
                    grammaticalCase = "Akk",
                ),
                ItemTemplate(
                    prompt = "Wie schmeckt ___ das Essen im Restaurant?",
                    answer = "dir",
                    choices = listOf("dir", "dich", "du", "mir"),
                    hint = "schmecken + Dativ",
                    person = "2sg",
                    grammaticalCase = "Dat",
                ),
            ),
        ),
        PronounEntry(
            tag = "er",
            category = Category.PERSONAL,
            templates = listOf(
                ItemTemplate(
                    prompt = "Thomas ist müde, deshalb schläft ___ schon.",
                    answer = "er",
                    choices = listOf("er", "ihn", "ihm", "sie"),
                    hint = "Nominativ (Subjekt)",
                    person = "3sg_m",
                    grammaticalCase = "Nom",
                ),
                ItemTemplate(
                    prompt = "Herr Weber ist nicht da, wir rufen ___ später an.",
                    answer = "ihn",
                    choices = listOf("ihn", "ihm", "er", "sie"),
                    hint = "anrufen + Akkusativ",
                    person = "3sg_m",
                    grammaticalCase = "Akk",
                ),
                ItemTemplate(
                    prompt = "Paul ist zufrieden, der neue Pullover passt ___ wie angegossen.",
                    answer = "ihm",
                    choices = listOf("ihm", "ihn", "er", "ihr"),
                    hint = "passen + Dativ",
                    person = "3sg_m",
                    grammaticalCase = "Dat",
                ),
            ),
        ),
        PronounEntry(
            tag = "sie_sg",
            category = Category.PERSONAL,
            templates = listOf(
                ItemTemplate(
                    prompt = "Maria lernt viel, denn ___ möchte Ärztin werden.",
                    answer = "sie",
                    choices = listOf("sie", "ihr", "ihnen", "er"),
                    hint = "Nominativ (Subjekt)",
                    person = "3sg_f",
                    grammaticalCase = "Nom",
                ),
                ItemTemplate(
                    prompt = "Ich habe Anna lange nicht gesehen und besuche ___ morgen.",
                    answer = "sie",
                    choices = listOf("sie", "ihr", "ihnen", "ihn"),
                    hint = "besuchen + Akkusativ",
                    person = "3sg_f",
                    grammaticalCase = "Akk",
                ),
                ItemTemplate(
                    prompt = "Lisa ist stolz, das schöne Fahrrad gehört ___ ganz allein.",
                    answer = "ihr",
                    choices = listOf("ihr", "sie", "ihm", "ihnen"),
                    hint = "gehören + Dativ",
                    person = "3sg_f",
                    grammaticalCase = "Dat",
                ),
            ),
        ),
        PronounEntry(
            tag = "es",
            category = Category.PERSONAL,
            templates = listOf(
                ItemTemplate(
                    prompt = "Wo ist das Buch? ___ liegt auf dem Schreibtisch.",
                    answer = "Es",
                    choices = listOf("Es", "Er", "Sie", "Ihm"),
                    hint = "Nominativ (das Buch = Neutrum)",
                    person = "3sg_n",
                    grammaticalCase = "Nom",
                ),
                ItemTemplate(
                    prompt = "Das Bild ist wunderschön, ich kaufe ___ sofort.",
                    answer = "es",
                    choices = listOf("es", "ihn", "ihm", "sie"),
                    hint = "kaufen + Akkusativ (das Bild = Neutrum)",
                    person = "3sg_n",
                    grammaticalCase = "Akk",
                ),
                ItemTemplate(
                    prompt = "Dem Baby ist kalt, gib ___ bitte eine Decke!",
                    answer = "ihm",
                    choices = listOf("ihm", "es", "ihn", "ihr"),
                    hint = "geben + Dativ (das Baby)",
                    person = "3sg_n",
                    grammaticalCase = "Dat",
                ),
            ),
        ),
        PronounEntry(
            tag = "wir",
            category = Category.PERSONAL,
            templates = listOf(
                ItemTemplate(
                    prompt = "Meine Frau und ich haben Urlaub. Im Sommer fahren ___ gemeinsam nach Italien.",
                    answer = "wir",
                    choices = listOf("wir", "uns", "ihr", "sie"),
                    hint = "Nominativ (Subjekt)",
                    person = "1pl",
                    grammaticalCase = "Nom",
                ),
                ItemTemplate(
                    prompt = "Der Lehrer lobt ___ für unsere gute Arbeit.",
                    answer = "uns",
                    choices = listOf("uns", "wir", "euch", "sie"),
                    hint = "loben + Akkusativ",
                    person = "1pl",
                    grammaticalCase = "Akk",
                ),
                ItemTemplate(
                    prompt = "Wir sind zum ersten Mal in Prag. Der Reiseleiter zeigt ___ die historische Altstadt.",
                    answer = "uns",
                    choices = listOf("uns", "wir", "euch", "ihnen"),
                    hint = "zeigen + Dativ",
                    person = "1pl",
                    grammaticalCase = "Dat",
                ),
            ),
        ),
        PronounEntry(
            tag = "ihr",
            category = Category.PERSONAL,
            templates = listOf(
                ItemTemplate(
                    prompt = "Kinder, warum macht ___ so viel Lärm?",
                    answer = "ihr",
                    choices = listOf("ihr", "euch", "wir", "sie"),
                    hint = "Nominativ (Subjekt)",
                    person = "2pl",
                    grammaticalCase = "Nom",
                ),
                ItemTemplate(
                    prompt = "Kinder, wo wart ihr? Ich habe ___ überall im Park gesucht.",
                    answer = "euch",
                    choices = listOf("euch", "ihr", "uns", "sie"),
                    hint = "suchen + Akkusativ",
                    person = "2pl",
                    grammaticalCase = "Akk",
                ),
                ItemTemplate(
                    prompt = "Kinder, Oma schenkt ___ ein spannendes Brettspiel.",
                    answer = "euch",
                    choices = listOf("euch", "ihr", "uns", "ihnen"),
                    hint = "schenken + Dativ",
                    person = "2pl",
                    grammaticalCase = "Dat",
                ),
            ),
        ),
        PronounEntry(
            tag = "sie_pl",
            category = Category.PERSONAL,
            templates = listOf(
                ItemTemplate(
                    prompt = "Die Nachbarn sind freundlich, ___ helfen uns oft.",
                    answer = "sie",
                    choices = listOf("sie", "ihnen", "ihr", "wir"),
                    hint = "Nominativ (Subjekt)",
                    person = "3pl",
                    grammaticalCase = "Nom",
                ),
                ItemTemplate(
                    prompt = "Ich mag die Kollegen und lade ___ zum Essen ein.",
                    answer = "sie",
                    choices = listOf("sie", "ihnen", "ihn", "ihr"),
                    hint = "einladen + Akkusativ",
                    person = "3pl",
                    grammaticalCase = "Akk",
                ),
                ItemTemplate(
                    prompt = "Die Gäste kommen an, wir bieten ___ Kaffee an.",
                    answer = "ihnen",
                    choices = listOf("ihnen", "sie", "ihm", "ihr"),
                    hint = "anbieten + Dativ",
                    person = "3pl",
                    grammaticalCase = "Dat",
                ),
            ),
        ),
        PronounEntry(
            tag = "Sie_formal",
            category = Category.PERSONAL,
            templates = listOf(
                ItemTemplate(
                    prompt = "Frau Müller, kommen ___ morgen zum Treffen?",
                    answer = "Sie",
                    choices = listOf("Sie", "Ihnen", "du", "ihr"),
                    hint = "Nominativ (Höflichkeitsform)",
                    person = "formal",
                    grammaticalCase = "Nom",
                ),
                ItemTemplate(
                    prompt = "Herr Schneider, ich habe ___ gestern im Büro gesucht.",
                    answer = "Sie",
                    choices = listOf("Sie", "Ihnen", "ihn", "dich"),
                    hint = "suchen + Akkusativ (Höflichkeitsform)",
                    person = "formal",
                    grammaticalCase = "Akk",
                ),
                ItemTemplate(
                    prompt = "Darf ich ___ behilflich sein, Herr Direktor?",
                    answer = "Ihnen",
                    choices = listOf("Ihnen", "Sie", "ihm", "dir"),
                    hint = "behilflich sein + Dativ (Höflichkeitsform)",
                    person = "formal",
                    grammaticalCase = "Dat",
                ),
            ),
        ),

        // ====================================================================
        // 2. Reflexive Pronouns: Accusative vs Dative & Common Reflexive Verbs
        // ====================================================================

        // Direct contrast pairs: Akk vs Dat reflexive
        PronounEntry(
            tag = "waschen",
            category = Category.REFLEXIVE,
            templates = listOf(
                ItemTemplate(
                    prompt = "Ich gehe ins Bad und wasche ___.",
                    answer = "mich",
                    choices = listOf("mich", "mir", "dich", "sich"),
                    hint = "Akkusativ (kein weiteres Objekt)",
                    person = "1sg",
                    grammaticalCase = "Akk",
                    verb = "sich waschen",
                ),
                ItemTemplate(
                    prompt = "Vor dem Essen wasche ich ___ gründlich die Hände.",
                    answer = "mir",
                    choices = listOf("mir", "mich", "dir", "sich"),
                    hint = "Dativ (die Hände = Akkusativ-Objekt)",
                    person = "1sg",
                    grammaticalCase = "Dat",
                    verb = "sich waschen",
                ),
                ItemTemplate(
                    prompt = "Wäschst du ___ morgens immer kalt?",
                    answer = "dich",
                    choices = listOf("dich", "dir", "mich", "sich"),
                    hint = "Akkusativ (kein weiteres Objekt)",
                    person = "2sg",
                    grammaticalCase = "Akk",
                    verb = "sich waschen",
                ),
                ItemTemplate(
                    prompt = "Du wäschst ___ das Gesicht mit kaltem Wasser.",
                    answer = "dir",
                    choices = listOf("dir", "dich", "mir", "sich"),
                    hint = "Dativ (das Gesicht = Akkusativ-Objekt)",
                    person = "2sg",
                    grammaticalCase = "Dat",
                    verb = "sich waschen",
                ),
            ),
        ),
        PronounEntry(
            tag = "kämmen",
            category = Category.REFLEXIVE,
            templates = listOf(
                ItemTemplate(
                    prompt = "Ich habe es eilig, aber ich kämme ___ noch schnell.",
                    answer = "mich",
                    choices = listOf("mich", "mir", "dich", "sich"),
                    hint = "Akkusativ (kein weiteres Objekt)",
                    person = "1sg",
                    grammaticalCase = "Akk",
                    verb = "sich kämmen",
                ),
                ItemTemplate(
                    prompt = "Jeden Morgen kämme ich ___ die Haare vor dem Spiegel.",
                    answer = "mir",
                    choices = listOf("mir", "mich", "dir", "sich"),
                    hint = "Dativ (die Haare = Akkusativ-Objekt)",
                    person = "1sg",
                    grammaticalCase = "Dat",
                    verb = "sich kämmen",
                ),
            ),
        ),
        PronounEntry(
            tag = "anziehen",
            category = Category.REFLEXIVE,
            templates = listOf(
                ItemTemplate(
                    prompt = "Zieh ___ bitte schnell an, wir müssen los!",
                    answer = "dich",
                    choices = listOf("dich", "dir", "mich", "sich"),
                    hint = "Akkusativ (kein weiteres Objekt)",
                    person = "2sg",
                    grammaticalCase = "Akk",
                    verb = "sich anziehen",
                ),
                ItemTemplate(
                    prompt = "Zieh ___ lieber eine warme Jacke an!",
                    answer = "dir",
                    choices = listOf("dir", "dich", "mir", "sich"),
                    hint = "Dativ (eine warme Jacke = Akkusativ-Objekt)",
                    person = "2sg",
                    grammaticalCase = "Dat",
                    verb = "sich anziehen",
                ),
            ),
        ),
        PronounEntry(
            tag = "putzen",
            category = Category.REFLEXIVE,
            templates = listOf(
                ItemTemplate(
                    prompt = "Ich putze ___ für die große Party heraus.",
                    answer = "mich",
                    choices = listOf("mich", "mir", "dich", "sich"),
                    hint = "Akkusativ (sich herausputzen)",
                    person = "1sg",
                    grammaticalCase = "Akk",
                    verb = "sich herausputzen",
                ),
                ItemTemplate(
                    prompt = "Nach jeder Mahlzeit putze ich ___ die Zähne.",
                    answer = "mir",
                    choices = listOf("mir", "mich", "dir", "sich"),
                    hint = "Dativ (die Zähne = Akkusativ-Objekt)",
                    person = "1sg",
                    grammaticalCase = "Dat",
                    verb = "sich die Zähne putzen",
                ),
            ),
        ),

        // Common reflexive verbs with correct form per person (18+ distinct verbs)
        PronounEntry(
            tag = "sich freuen",
            category = Category.REFLEXIVE,
            templates = listOf(
                ItemTemplate(
                    prompt = "Ich freue ___ schon riesig auf das Wochenende.",
                    answer = "mich",
                    choices = listOf("mich", "mir", "dich", "sich"),
                    hint = "sich freuen auf + Akk (1. Person)",
                    person = "1sg",
                    grammaticalCase = "Akk",
                    verb = "sich freuen",
                ),
            ),
        ),
        PronounEntry(
            tag = "sich erinnern an",
            category = Category.REFLEXIVE,
            templates = listOf(
                ItemTemplate(
                    prompt = "Er erinnert ___ gern an die Zeit in Wien.",
                    answer = "sich",
                    choices = listOf("sich", "mich", "dich", "uns"),
                    hint = "erinnern an + Akk (3. Person)",
                    person = "3sg_m",
                    grammaticalCase = "Akk",
                    verb = "sich erinnern an",
                ),
            ),
        ),
        PronounEntry(
            tag = "sich vorstellen",
            category = Category.REFLEXIVE,
            templates = listOf(
                ItemTemplate(
                    prompt = "Kannst du ___ vorstellen, wie spannend das war?",
                    answer = "dir",
                    choices = listOf("dir", "dich", "mir", "sich"),
                    hint = "sich (Dat) etwas vorstellen (2. Person)",
                    person = "2sg",
                    grammaticalCase = "Dat",
                    verb = "sich vorstellen",
                ),
                ItemTemplate(
                    prompt = "Darf ich ___ kurz vorstellen? Mein Name ist Felix.",
                    answer = "mich",
                    choices = listOf("mich", "mir", "dich", "sich"),
                    hint = "sich vorstellen (Akk = sich selbst, 1. Person)",
                    person = "1sg",
                    grammaticalCase = "Akk",
                    verb = "sich vorstellen",
                ),
            ),
        ),
        PronounEntry(
            tag = "sich beeilen",
            category = Category.REFLEXIVE,
            templates = listOf(
                ItemTemplate(
                    prompt = "Wir müssen ___ beeilen, der Zug fährt gleich ab.",
                    answer = "uns",
                    choices = listOf("uns", "euch", "sich", "mich"),
                    hint = "sich beeilen (1. Person Plural)",
                    person = "1pl",
                    grammaticalCase = "Akk",
                    verb = "sich beeilen",
                ),
            ),
        ),
        PronounEntry(
            tag = "sich Sorgen machen",
            category = Category.REFLEXIVE,
            templates = listOf(
                ItemTemplate(
                    prompt = "Ich mache ___ große Sorgen um deine Zukunft.",
                    answer = "mir",
                    choices = listOf("mir", "mich", "dir", "sich"),
                    hint = "sich (Dat) Sorgen machen (1. Person)",
                    person = "1sg",
                    grammaticalCase = "Dat",
                    verb = "sich Sorgen machen",
                ),
                ItemTemplate(
                    prompt = "Mach ___ keine Sorgen, alles wird gut gehen!",
                    answer = "dir",
                    choices = listOf("dir", "dich", "mir", "sich"),
                    hint = "sich (Dat) Sorgen machen (2. Person)",
                    person = "2sg",
                    grammaticalCase = "Dat",
                    verb = "sich Sorgen machen",
                ),
            ),
        ),
        PronounEntry(
            tag = "sich interessieren für",
            category = Category.REFLEXIVE,
            templates = listOf(
                ItemTemplate(
                    prompt = "Lisa interessiert ___ sehr für moderne Kunst.",
                    answer = "sich",
                    choices = listOf("sich", "mich", "dich", "uns"),
                    hint = "interessieren für + Akk (3. Person)",
                    person = "3sg_f",
                    grammaticalCase = "Akk",
                    verb = "sich interessieren für",
                ),
            ),
        ),
        PronounEntry(
            tag = "sich fühlen",
            category = Category.REFLEXIVE,
            templates = listOf(
                ItemTemplate(
                    prompt = "Wie fühlst du ___ nach dem langen Lauf?",
                    answer = "dich",
                    choices = listOf("dich", "dir", "mich", "sich"),
                    hint = "sich fühlen (2. Person)",
                    person = "2sg",
                    grammaticalCase = "Akk",
                    verb = "sich fühlen",
                ),
            ),
        ),
        PronounEntry(
            tag = "sich setzen",
            category = Category.REFLEXIVE,
            templates = listOf(
                ItemTemplate(
                    prompt = "Setzt ___ bitte auf eure Plätze, der Film beginnt!",
                    answer = "euch",
                    choices = listOf("euch", "uns", "sich", "dich"),
                    hint = "sich setzen (2. Person Plural)",
                    person = "2pl",
                    grammaticalCase = "Akk",
                    verb = "sich setzen",
                ),
            ),
        ),
        PronounEntry(
            tag = "sich ärgern über",
            category = Category.REFLEXIVE,
            templates = listOf(
                ItemTemplate(
                    prompt = "Mein Kollege ärgert ___ über den verspäteten Zug.",
                    answer = "sich",
                    choices = listOf("sich", "mich", "uns", "euch"),
                    hint = "ärgern über + Akk (3. Person)",
                    person = "3sg_m",
                    grammaticalCase = "Akk",
                    verb = "sich ärgern über",
                ),
            ),
        ),
        PronounEntry(
            tag = "sich entscheiden für",
            category = Category.REFLEXIVE,
            templates = listOf(
                ItemTemplate(
                    prompt = "Wir haben ___ für das kleinere Apartment entschieden.",
                    answer = "uns",
                    choices = listOf("uns", "euch", "sich", "mich"),
                    hint = "sich entscheiden für + Akk (1. Person Plural)",
                    person = "1pl",
                    grammaticalCase = "Akk",
                    verb = "sich entscheiden für",
                ),
            ),
        ),
        PronounEntry(
            tag = "sich treffen mit",
            category = Category.REFLEXIVE,
            templates = listOf(
                ItemTemplate(
                    prompt = "Wann trefft ihr ___ heute mit euren Freunden?",
                    answer = "euch",
                    choices = listOf("euch", "uns", "sich", "dich"),
                    hint = "sich treffen mit + Dat (2. Person Plural)",
                    person = "2pl",
                    grammaticalCase = "Akk",
                    verb = "sich treffen mit",
                ),
            ),
        ),
        PronounEntry(
            tag = "sich ausruhen",
            category = Category.REFLEXIVE,
            templates = listOf(
                ItemTemplate(
                    prompt = "Nach der anstrengenden Schicht ruhe ich ___ auf dem Sofa aus.",
                    answer = "mich",
                    choices = listOf("mich", "mir", "dich", "sich"),
                    hint = "sich ausruhen (1. Person)",
                    person = "1sg",
                    grammaticalCase = "Akk",
                    verb = "sich ausruhen",
                ),
            ),
        ),
        PronounEntry(
            tag = "sich erkälten",
            category = Category.REFLEXIVE,
            templates = listOf(
                ItemTemplate(
                    prompt = "Zieh die Mütze auf, sonst erkältest du ___ noch!",
                    answer = "dich",
                    choices = listOf("dich", "dir", "mich", "sich"),
                    hint = "sich erkälten (2. Person)",
                    person = "2sg",
                    grammaticalCase = "Akk",
                    verb = "sich erkälten",
                ),
            ),
        ),
        PronounEntry(
            tag = "sich konzentrieren auf",
            category = Category.REFLEXIVE,
            templates = listOf(
                ItemTemplate(
                    prompt = "Bei dem lauten Straßenlärm kann ich ___ kaum konzentrieren.",
                    answer = "mich",
                    choices = listOf("mich", "mir", "dich", "sich"),
                    hint = "sich konzentrieren auf + Akk (1. Person)",
                    person = "1sg",
                    grammaticalCase = "Akk",
                    verb = "sich konzentrieren auf",
                ),
            ),
        ),
        PronounEntry(
            tag = "sich bedanken bei",
            category = Category.REFLEXIVE,
            templates = listOf(
                ItemTemplate(
                    prompt = "Wir bedanken ___ herzlich bei allen Helfern.",
                    answer = "uns",
                    choices = listOf("uns", "euch", "sich", "mich"),
                    hint = "sich bedanken bei + Dat (1. Person Plural)",
                    person = "1pl",
                    grammaticalCase = "Akk",
                    verb = "sich bedanken bei",
                ),
            ),
        ),
        PronounEntry(
            tag = "sich wünschen",
            category = Category.REFLEXIVE,
            templates = listOf(
                ItemTemplate(
                    prompt = "Was wünschst du ___ eigentlich zum Geburtstag?",
                    answer = "dir",
                    choices = listOf("dir", "dich", "mir", "sich"),
                    hint = "sich (Dat) etwas wünschen (2. Person)",
                    person = "2sg",
                    grammaticalCase = "Dat",
                    verb = "sich wünschen",
                ),
            ),
        ),
        PronounEntry(
            tag = "sich merken",
            category = Category.REFLEXIVE,
            templates = listOf(
                ItemTemplate(
                    prompt = "Diese wichtige PIN muss ich ___ gut merken.",
                    answer = "mir",
                    choices = listOf("mir", "mich", "dir", "sich"),
                    hint = "sich (Dat) etwas merken (1. Person)",
                    person = "1sg",
                    grammaticalCase = "Dat",
                    verb = "sich merken",
                ),
            ),
        ),
        PronounEntry(
            tag = "sich langweilen",
            category = Category.REFLEXIVE,
            templates = listOf(
                ItemTemplate(
                    prompt = "Die Kinder langweilen ___ bei der langen Autofahrt.",
                    answer = "sich",
                    choices = listOf("sich", "mich", "uns", "euch"),
                    hint = "langweilen (3. Person Plural)",
                    person = "3pl",
                    grammaticalCase = "Akk",
                    verb = "sich langweilen",
                ),
            ),
        ),
        PronounEntry(
            tag = "sich verletzen",
            category = Category.REFLEXIVE,
            templates = listOf(
                ItemTemplate(
                    prompt = "Er hat ___ beim Fußballspielen am Knie verletzt.",
                    answer = "sich",
                    choices = listOf("sich", "mich", "dich", "uns"),
                    hint = "verletzen (3. Person)",
                    person = "3sg_m",
                    grammaticalCase = "Akk",
                    verb = "sich verletzen",
                ),
            ),
        ),
        PronounEntry(
            tag = "sich gewöhnen an",
            category = Category.REFLEXIVE,
            templates = listOf(
                ItemTemplate(
                    prompt = "Wir haben ___ schnell an das neue Klima gewöhnt.",
                    answer = "uns",
                    choices = listOf("uns", "euch", "sich", "mich"),
                    hint = "sich gewöhnen an + Akk (1. Person Plural)",
                    person = "1pl",
                    grammaticalCase = "Akk",
                    verb = "sich gewöhnen an",
                ),
            ),
        ),

        // ====================================================================
        // 3. Possessives: Declension with Noun (Masc, Fem, Neut, Plur; Nom, Akk, Dat, Gen)
        //    Including mein, deinen, seinem, ihrer, unseres, and euer- with e-drop "eure"
        // ====================================================================
        PronounEntry(
            tag = "mein",
            category = Category.POSSESSIVE,
            templates = listOf(
                ItemTemplate(
                    prompt = "Das ist ___ neuer Kollege aus Hamburg.",
                    answer = "mein",
                    choices = listOf("mein", "meinen", "meinem", "meines"),
                    hint = "Maskulinum Nominativ",
                    grammaticalCase = "Nom",
                ),
                ItemTemplate(
                    prompt = "Ich habe ___ Schlüsselbund überall gesucht.",
                    answer = "meinen",
                    choices = listOf("meinen", "meinem", "mein", "meines"),
                    hint = "Maskulinum Akkusativ",
                    grammaticalCase = "Akk",
                ),
                ItemTemplate(
                    prompt = "Ich helfe ___ Vater bei der Gartenarbeit.",
                    answer = "meinem",
                    choices = listOf("meinem", "meinen", "mein", "meines"),
                    hint = "Maskulinum Dativ",
                    grammaticalCase = "Dat",
                ),
                ItemTemplate(
                    prompt = "Das Fahrrad ___ Bruders ist leider gestohlen worden.",
                    answer = "meines",
                    choices = listOf("meines", "meinem", "meinen", "mein"),
                    hint = "Maskulinum Genitiv",
                    grammaticalCase = "Gen",
                ),
                ItemTemplate(
                    prompt = "Das ist ___ Tante aus Berlin.",
                    answer = "meine",
                    choices = listOf("meine", "meiner", "mein", "meinen"),
                    hint = "Femininum Nominativ",
                    grammaticalCase = "Nom",
                ),
                ItemTemplate(
                    prompt = "Ich besuche ___ Schwester am Wochenende.",
                    answer = "meine",
                    choices = listOf("meine", "meiner", "mein", "meinen"),
                    hint = "Femininum Akkusativ",
                    grammaticalCase = "Akk",
                ),
                ItemTemplate(
                    prompt = "Ich schenke ___ Mutter einen schönen Blumenstrauß.",
                    answer = "meiner",
                    choices = listOf("meiner", "meine", "meinem", "meinen"),
                    hint = "Femininum Dativ",
                    grammaticalCase = "Dat",
                ),
                ItemTemplate(
                    prompt = "Wegen der Krankheit ___ Oma bin ich zu Hause geblieben.",
                    answer = "meiner",
                    choices = listOf("meiner", "meine", "meines", "meinem"),
                    hint = "Femininum Genitiv",
                    grammaticalCase = "Gen",
                ),
                ItemTemplate(
                    prompt = "Das ist ___ neues Fahrrad.",
                    answer = "mein",
                    choices = listOf("mein", "meine", "meinem", "meines"),
                    hint = "Neutrum Nominativ",
                    grammaticalCase = "Nom",
                ),
                ItemTemplate(
                    prompt = "Ich wasche ___ Auto jeden Samstag.",
                    answer = "mein",
                    choices = listOf("mein", "meine", "meinem", "meines"),
                    hint = "Neutrum Akkusativ",
                    grammaticalCase = "Akk",
                ),
                ItemTemplate(
                    prompt = "In ___ neuen Zimmer gibt es viel Licht.",
                    answer = "meinem",
                    choices = listOf("meinem", "meinen", "mein", "meines"),
                    hint = "Neutrum Dativ",
                    grammaticalCase = "Dat",
                ),
                ItemTemplate(
                    prompt = "Während ___ Urlaubs war das Wetter herrlich.",
                    answer = "meines",
                    choices = listOf("meines", "meinem", "mein", "meiner"),
                    hint = "Maskulinum Genitiv",
                    grammaticalCase = "Gen",
                ),
                ItemTemplate(
                    prompt = "Hier wohnen ___ besten Freunde.",
                    answer = "meine",
                    choices = listOf("meine", "meinen", "meiner", "mein"),
                    hint = "Plural Nominativ",
                    grammaticalCase = "Nom",
                ),
                ItemTemplate(
                    prompt = "Ich danke ___ Eltern für ihre große Unterstützung.",
                    answer = "meinen",
                    choices = listOf("meinen", "meinem", "meine", "meiner"),
                    hint = "Plural Dativ",
                    grammaticalCase = "Dat",
                ),
            ),
        ),
        PronounEntry(
            tag = "dein",
            category = Category.POSSESSIVE,
            templates = listOf(
                ItemTemplate(
                    prompt = "Ist das ___ Koffer dort an der Rezeption?",
                    answer = "dein",
                    choices = listOf("dein", "deinen", "deinem", "deines"),
                    hint = "Maskulinum Nominativ",
                    grammaticalCase = "Nom",
                ),
                ItemTemplate(
                    prompt = "Vergiss ___ Regenschirm nicht, es regnet!",
                    answer = "deinen",
                    choices = listOf("deinen", "deinem", "dein", "deines"),
                    hint = "Maskulinum Akkusativ",
                    grammaticalCase = "Akk",
                ),
                ItemTemplate(
                    prompt = "Kommst du gut mit ___ neuen Chef zurecht?",
                    answer = "deinem",
                    choices = listOf("deinem", "deinen", "dein", "deines"),
                    hint = "Maskulinum Dativ",
                    grammaticalCase = "Dat",
                ),
                ItemTemplate(
                    prompt = "Erzähl mir bitte mehr von ___ Urlaubsreise!",
                    answer = "deiner",
                    choices = listOf("deiner", "deine", "deinem", "deinen"),
                    hint = "Femininum Dativ",
                    grammaticalCase = "Dat",
                ),
            ),
        ),
        PronounEntry(
            tag = "sein",
            category = Category.POSSESSIVE,
            templates = listOf(
                ItemTemplate(
                    prompt = "Lukas zeigt uns stolz ___ neues Smartphone.",
                    answer = "sein",
                    choices = listOf("sein", "seine", "seinem", "seines"),
                    hint = "Neutrum Akkusativ",
                    grammaticalCase = "Akk",
                ),
                ItemTemplate(
                    prompt = "Paul sucht ___ verlorenen Ausweis.",
                    answer = "seinen",
                    choices = listOf("seinen", "seinem", "sein", "seines"),
                    hint = "Maskulinum Akkusativ",
                    grammaticalCase = "Akk",
                ),
            ),
        ),
        PronounEntry(
            tag = "ihr_poss",
            category = Category.POSSESSIVE,
            templates = listOf(
                ItemTemplate(
                    prompt = "Anna geht jeden Tag mit ___ Hund im Wald spazieren.",
                    answer = "ihrem",
                    choices = listOf("ihrem", "ihren", "ihr", "ihres"),
                    hint = "Maskulinum Dativ",
                    grammaticalCase = "Dat",
                ),
                ItemTemplate(
                    prompt = "Sie telefoniert gerade mit ___ besten Freundin.",
                    answer = "ihrer",
                    choices = listOf("ihrer", "ihre", "ihrem", "ihren"),
                    hint = "Femininum Dativ",
                    grammaticalCase = "Dat",
                ),
            ),
        ),
        PronounEntry(
            tag = "unser",
            category = Category.POSSESSIVE,
            templates = listOf(
                ItemTemplate(
                    prompt = "Das ist ___ neuer Deutschlehrer.",
                    answer = "unser",
                    choices = listOf("unser", "unsere", "unserem", "unseren"),
                    hint = "Maskulinum Nominativ",
                    grammaticalCase = "Nom",
                ),
                ItemTemplate(
                    prompt = "Hier ist ___ neue Wohnung.",
                    answer = "unsere",
                    choices = listOf("unsere", "unserer", "unser", "unserem"),
                    hint = "Femininum Nominativ",
                    grammaticalCase = "Nom",
                ),
                ItemTemplate(
                    prompt = "Wir haben ___ alten Wagen endlich verkauft.",
                    answer = "unseren",
                    choices = listOf("unseren", "unserem", "unser", "unseres"),
                    hint = "Maskulinum Akkusativ",
                    grammaticalCase = "Akk",
                ),
                ItemTemplate(
                    prompt = "Wir fühlen uns in ___ neuen Haus sehr wohl.",
                    answer = "unserem",
                    choices = listOf("unserem", "unseren", "unser", "unseres"),
                    hint = "Neutrum Dativ",
                    grammaticalCase = "Dat",
                ),
                ItemTemplate(
                    prompt = "In ___ Stadt gibt es viele Grünanlagen.",
                    answer = "unserer",
                    choices = listOf("unserer", "unsere", "unserem", "unseren"),
                    hint = "Femininum Dativ",
                    grammaticalCase = "Dat",
                ),
                ItemTemplate(
                    prompt = "Wegen des Umzugs ___ Nachbarn war es gestern laut.",
                    answer = "unseres",
                    choices = listOf("unseres", "unserem", "unseren", "unser"),
                    hint = "Maskulinum Genitiv",
                    grammaticalCase = "Gen",
                ),
            ),
        ),
        PronounEntry(
            tag = "euer",
            category = Category.POSSESSIVE,
            templates = listOf(
                ItemTemplate(
                    prompt = "Ist das ___ Wagen da drüben?",
                    answer = "euer",
                    choices = listOf("euer", "eure", "eurem", "euren"),
                    hint = "Maskulinum Nominativ",
                    grammaticalCase = "Nom",
                ),
                ItemTemplate(
                    prompt = "Wo arbeitet ___ Mutter eigentlich?",
                    answer = "eure",
                    choices = listOf("eure", "euer", "eurer", "eurem"),
                    hint = "Femininum Nominativ (e-drop)",
                    grammaticalCase = "Nom",
                ),
                ItemTemplate(
                    prompt = "Ich habe gestern ___ Einladung erhalten.",
                    answer = "eure",
                    choices = listOf("eure", "euer", "eurer", "eurem"),
                    hint = "Femininum Akkusativ (e-drop)",
                    grammaticalCase = "Akk",
                ),
                ItemTemplate(
                    prompt = "Sind das ___ Fahrräder vor der Tür?",
                    answer = "eure",
                    choices = listOf("eure", "euer", "euren", "eurer"),
                    hint = "Plural Nominativ (e-drop)",
                    grammaticalCase = "Nom",
                ),
                ItemTemplate(
                    prompt = "Habt ihr mit ___ neuen Lehrer schon gesprochen?",
                    answer = "eurem",
                    choices = listOf("eurem", "euren", "euer", "eures"),
                    hint = "Maskulinum Dativ",
                    grammaticalCase = "Dat",
                ),
                ItemTemplate(
                    prompt = "Passt auf ___ kleinen Bruder auf!",
                    answer = "euren",
                    choices = listOf("euren", "eurem", "euer", "eures"),
                    hint = "Maskulinum Akkusativ",
                    grammaticalCase = "Akk",
                ),
                ItemTemplate(
                    prompt = "Wie viele Schüler sind in ___ Klasse?",
                    answer = "eurer",
                    choices = listOf("eurer", "eure", "eurem", "euren"),
                    hint = "Femininum Dativ",
                    grammaticalCase = "Dat",
                ),
            ),
        ),
        PronounEntry(
            tag = "Ihr_poss",
            category = Category.POSSESSIVE,
            templates = listOf(
                ItemTemplate(
                    prompt = "Herr Meyer, ist das ___ Koffer?",
                    answer = "Ihr",
                    choices = listOf("Ihr", "Ihren", "Ihrem", "Ihres"),
                    hint = "Höflichkeitsform Maskulinum Nominativ",
                    grammaticalCase = "Nom",
                ),
                ItemTemplate(
                    prompt = "Frau Wagner, wie lautet ___ neue Adresse?",
                    answer = "Ihre",
                    choices = listOf("Ihre", "Ihrer", "Ihr", "Ihrem"),
                    hint = "Höflichkeitsform Femininum Nominativ",
                    grammaticalCase = "Nom",
                ),
                ItemTemplate(
                    prompt = "Guten Tag Herr Schmidt, haben Sie ___ Pass dabei?",
                    answer = "Ihren",
                    choices = listOf("Ihren", "Ihrem", "Ihr", "Ihres"),
                    hint = "Höflichkeitsform Maskulinum Akkusativ",
                    grammaticalCase = "Akk",
                ),
                ItemTemplate(
                    prompt = "Gefällt es Ihnen in ___ neuen Büro, Frau Klein?",
                    answer = "Ihrem",
                    choices = listOf("Ihrem", "Ihren", "Ihr", "Ihres"),
                    hint = "Höflichkeitsform Neutrum Dativ",
                    grammaticalCase = "Dat",
                ),
            ),
        ),
    )

    /**
     * Builds drill items for pronouns.
     *
     * If a saved word matches an entry tag (case-insensitively, with or without "sich "),
     * its items use the word ID as their key; otherwise they use the stable table key `pron:<tag>:<index>`.
     */
    fun items(words: Collection<Word> = emptyList()): List<DrillItem> {
        val wordMap = words.associateBy { it.text.trim().lowercase() }
        return ENTRIES.flatMap { entry ->
            val matchingWord = wordMap[entry.tag.lowercase()]
                ?: wordMap[entry.tag.removePrefix("sich ").lowercase()]
            entry.templates.mapIndexed { index, template ->
                val key = matchingWord?.let { "${it.id}" } ?: "pron:${entry.tag}:$index"
                DrillItem(
                    id = DrillItemId(DrillType.PRONOUNS, key),
                    prompt = template.prompt,
                    answer = template.answer,
                    choices = template.choices,
                    hint = template.hint,
                )
            }
        }
    }
}
