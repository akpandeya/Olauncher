package app.olauncher.data.store

/**
 * Preposition drill content covering accusative, dative, genitive, and two-way (Wechsel) prepositions.
 */
object DrillPrepositions {

    internal data class ItemTemplate(
        val prompt: String,
        val answer: String,
        val choices: List<String>,
        val hint: String? = null,
    )

    internal data class PrepositionEntry(
        val preposition: String,
        val templates: List<ItemTemplate>,
    )

    // Which-case questions are answered with the case name itself, so they carry no
    // hint: any hint naming the case would reveal the answer.
    private val WHICH_CASE_CHOICES = listOf("Akkusativ", "Dativ", "Genitiv")

    internal val ENTRIES: List<PrepositionEntry> = listOf(
        // --- Accusative-only prepositions ---
        PrepositionEntry(
            preposition = "durch",
            templates = listOf(
                ItemTemplate(
                    prompt = "Welcher Fall folgt auf „durch“?",
                    answer = "Akkusativ",
                    choices = WHICH_CASE_CHOICES,
                ),
                ItemTemplate(
                    prompt = "Ich gehe durch ___ Park.",
                    answer = "den",
                    choices = listOf("den", "dem", "das", "des"),
                    hint = "durch + Akkusativ",
                ),
            ),
        ),
        PrepositionEntry(
            preposition = "für",
            templates = listOf(
                ItemTemplate(
                    prompt = "Welcher Fall folgt auf „für“?",
                    answer = "Akkusativ",
                    choices = WHICH_CASE_CHOICES,
                ),
                ItemTemplate(
                    prompt = "Das Geschenk ist für ___ Vater.",
                    answer = "den",
                    choices = listOf("den", "dem", "der", "des"),
                    hint = "für + Akkusativ",
                ),
            ),
        ),
        PrepositionEntry(
            preposition = "gegen",
            templates = listOf(
                ItemTemplate(
                    prompt = "Welcher Fall folgt auf „gegen“?",
                    answer = "Akkusativ",
                    choices = WHICH_CASE_CHOICES,
                ),
                ItemTemplate(
                    prompt = "Das Auto fährt gegen ___ Baum.",
                    answer = "den",
                    choices = listOf("den", "dem", "der", "des"),
                    hint = "gegen + Akkusativ",
                ),
            ),
        ),
        PrepositionEntry(
            preposition = "ohne",
            templates = listOf(
                ItemTemplate(
                    prompt = "Welcher Fall folgt auf „ohne“?",
                    answer = "Akkusativ",
                    choices = WHICH_CASE_CHOICES,
                ),
                ItemTemplate(
                    prompt = "Wir gehen ohne ___ Hund spazieren.",
                    answer = "den",
                    choices = listOf("den", "dem", "das", "des"),
                    hint = "ohne + Akkusativ",
                ),
            ),
        ),
        PrepositionEntry(
            preposition = "um",
            templates = listOf(
                ItemTemplate(
                    prompt = "Welcher Fall folgt auf „um“?",
                    answer = "Akkusativ",
                    choices = WHICH_CASE_CHOICES,
                ),
                ItemTemplate(
                    prompt = "Die Kinder laufen um ___ Tisch.",
                    answer = "den",
                    choices = listOf("den", "dem", "der", "des"),
                    hint = "um + Akkusativ",
                ),
            ),
        ),
        PrepositionEntry(
            preposition = "bis",
            templates = listOf(
                ItemTemplate(
                    prompt = "Welcher Fall folgt auf „bis“?",
                    answer = "Akkusativ",
                    choices = WHICH_CASE_CHOICES,
                ),
                // No sentence item: "bis" + Akkusativ appears naturally without an
                // article ("bis nächsten Montag"), and "bis den/des ..." is not
                // idiomatic German, so an article-gap sentence would teach a form
                // native speakers avoid.
            ),
        ),
        PrepositionEntry(
            preposition = "entlang",
            templates = listOf(
                ItemTemplate(
                    prompt = "Welcher Fall folgt auf „entlang“ (nachgestellt)?",
                    answer = "Akkusativ",
                    choices = WHICH_CASE_CHOICES,
                ),
                ItemTemplate(
                    prompt = "Wir spazieren ___ Fluss entlang.",
                    answer = "den",
                    choices = listOf("den", "dem", "der", "des"),
                    hint = "entlang + Akkusativ",
                ),
            ),
        ),

        // --- Dative-only prepositions ---
        PrepositionEntry(
            preposition = "aus",
            templates = listOf(
                ItemTemplate(
                    prompt = "Welcher Fall folgt auf „aus“?",
                    answer = "Dativ",
                    choices = WHICH_CASE_CHOICES,
                ),
                ItemTemplate(
                    prompt = "Er kommt aus ___ Haus.",
                    answer = "dem",
                    choices = listOf("dem", "den", "das", "des"),
                    hint = "aus + Dativ",
                ),
            ),
        ),
        PrepositionEntry(
            preposition = "bei",
            templates = listOf(
                ItemTemplate(
                    prompt = "Welcher Fall folgt auf „bei“?",
                    answer = "Dativ",
                    choices = WHICH_CASE_CHOICES,
                ),
                ItemTemplate(
                    prompt = "Sie wohnt bei ___ Eltern.",
                    answer = "den",
                    choices = listOf("den", "dem", "der", "die"),
                    hint = "bei + Dativ",
                ),
            ),
        ),
        PrepositionEntry(
            preposition = "mit",
            templates = listOf(
                ItemTemplate(
                    prompt = "Welcher Fall folgt auf „mit“?",
                    answer = "Dativ",
                    choices = WHICH_CASE_CHOICES,
                ),
                ItemTemplate(
                    prompt = "Ich fahre mit ___ Bus.",
                    answer = "dem",
                    choices = listOf("dem", "den", "der", "das"),
                    hint = "mit + Dativ",
                ),
            ),
        ),
        PrepositionEntry(
            preposition = "nach",
            templates = listOf(
                ItemTemplate(
                    prompt = "Welcher Fall folgt auf „nach“?",
                    answer = "Dativ",
                    choices = WHICH_CASE_CHOICES,
                ),
                ItemTemplate(
                    prompt = "Nach ___ Unterricht gehen wir essen.",
                    answer = "dem",
                    choices = listOf("dem", "den", "der", "das"),
                    hint = "nach + Dativ",
                ),
            ),
        ),
        PrepositionEntry(
            preposition = "seit",
            templates = listOf(
                ItemTemplate(
                    prompt = "Welcher Fall folgt auf „seit“?",
                    answer = "Dativ",
                    choices = WHICH_CASE_CHOICES,
                ),
                ItemTemplate(
                    prompt = "Seit ___ Unfall ist er vorsichtig.",
                    answer = "dem",
                    choices = listOf("dem", "den", "der", "das"),
                    hint = "seit + Dativ",
                ),
            ),
        ),
        PrepositionEntry(
            preposition = "von",
            templates = listOf(
                ItemTemplate(
                    prompt = "Welcher Fall folgt auf „von“?",
                    answer = "Dativ",
                    choices = WHICH_CASE_CHOICES,
                ),
                ItemTemplate(
                    prompt = "Das Paket ist von ___ Großeltern.",
                    answer = "den",
                    choices = listOf("den", "dem", "der", "des"),
                    hint = "von + Dativ",
                ),
            ),
        ),
        PrepositionEntry(
            preposition = "zu",
            templates = listOf(
                ItemTemplate(
                    prompt = "Welcher Fall folgt auf „zu“?",
                    answer = "Dativ",
                    choices = WHICH_CASE_CHOICES,
                ),
                ItemTemplate(
                    prompt = "Ich gehe zu ___ Arzt.",
                    answer = "dem",
                    choices = listOf("dem", "den", "der", "das"),
                    hint = "zu + Dativ",
                ),
            ),
        ),
        PrepositionEntry(
            preposition = "gegenüber",
            templates = listOf(
                ItemTemplate(
                    prompt = "Welcher Fall folgt auf „gegenüber“?",
                    answer = "Dativ",
                    choices = WHICH_CASE_CHOICES,
                ),
                ItemTemplate(
                    prompt = "Das Hotel liegt gegenüber ___ Park.",
                    answer = "dem",
                    choices = listOf("dem", "den", "der", "das"),
                    hint = "gegenüber + Dativ",
                ),
            ),
        ),

        // --- Genitive prepositions ---
        PrepositionEntry(
            preposition = "wegen",
            templates = listOf(
                ItemTemplate(
                    prompt = "Welcher Fall folgt auf „wegen“?",
                    answer = "Genitiv",
                    choices = WHICH_CASE_CHOICES,
                ),
                ItemTemplate(
                    prompt = "Wegen ___ Wetters bleiben wir hier.",
                    answer = "des",
                    choices = listOf("des", "dem", "den", "das"),
                    hint = "wegen + Genitiv",
                ),
            ),
        ),
        PrepositionEntry(
            preposition = "trotz",
            templates = listOf(
                ItemTemplate(
                    prompt = "Welcher Fall folgt auf „trotz“?",
                    answer = "Genitiv",
                    choices = WHICH_CASE_CHOICES,
                ),
                ItemTemplate(
                    prompt = "Trotz ___ Regens geht sie raus.",
                    answer = "des",
                    choices = listOf("des", "dem", "den", "das"),
                    hint = "trotz + Genitiv",
                ),
            ),
        ),
        PrepositionEntry(
            preposition = "während",
            templates = listOf(
                ItemTemplate(
                    prompt = "Welcher Fall folgt auf „während“?",
                    answer = "Genitiv",
                    choices = WHICH_CASE_CHOICES,
                ),
                ItemTemplate(
                    prompt = "Während ___ Fahrt lesen wir.",
                    answer = "der",
                    choices = listOf("der", "die", "den", "dem"),
                    hint = "während + Genitiv",
                ),
            ),
        ),
        PrepositionEntry(
            preposition = "statt",
            templates = listOf(
                ItemTemplate(
                    prompt = "Welcher Fall folgt auf „statt“?",
                    answer = "Genitiv",
                    choices = WHICH_CASE_CHOICES,
                ),
                ItemTemplate(
                    prompt = "Statt ___ Kaffees trinkt er Tee.",
                    answer = "des",
                    choices = listOf("des", "dem", "den", "das"),
                    hint = "statt + Genitiv",
                ),
            ),
        ),

        // --- Two-way prepositions (Wechselpräpositionen) ---
        // Each has one location (Dativ) and one direction (Akkusativ) sentence.
        PrepositionEntry(
            preposition = "an",
            templates = listOf(
                ItemTemplate(
                    prompt = "Das Bild hängt an ___ Wand.",
                    answer = "der",
                    choices = listOf("der", "die", "den", "dem"),
                    hint = "Wo? → location",
                ),
                ItemTemplate(
                    prompt = "Ich hänge das Bild an ___ Wand.",
                    answer = "die",
                    choices = listOf("die", "der", "den", "dem"),
                    hint = "Wohin? → movement",
                ),
            ),
        ),
        PrepositionEntry(
            preposition = "auf",
            templates = listOf(
                ItemTemplate(
                    prompt = "Das Buch liegt auf ___ Tisch.",
                    answer = "dem",
                    choices = listOf("dem", "den", "der", "das"),
                    hint = "Wo? → location",
                ),
                ItemTemplate(
                    prompt = "Ich lege das Buch auf ___ Tisch.",
                    answer = "den",
                    choices = listOf("den", "dem", "der", "das"),
                    hint = "Wohin? → movement",
                ),
            ),
        ),
        PrepositionEntry(
            preposition = "hinter",
            templates = listOf(
                ItemTemplate(
                    prompt = "Der Hund schläft hinter ___ Haus.",
                    answer = "dem",
                    choices = listOf("dem", "das", "den", "des"),
                    hint = "Wo? → location",
                ),
                ItemTemplate(
                    prompt = "Der Hund rennt hinter ___ Haus.",
                    answer = "das",
                    choices = listOf("das", "dem", "den", "des"),
                    hint = "Wohin? → movement",
                ),
            ),
        ),
        PrepositionEntry(
            preposition = "in",
            templates = listOf(
                ItemTemplate(
                    prompt = "Wir sitzen in ___ Garten.",
                    answer = "dem",
                    choices = listOf("dem", "den", "der", "das"),
                    hint = "Wo? → location",
                ),
                ItemTemplate(
                    prompt = "Wir gehen in ___ Garten.",
                    answer = "den",
                    choices = listOf("den", "dem", "der", "das"),
                    hint = "Wohin? → movement",
                ),
            ),
        ),
        PrepositionEntry(
            preposition = "neben",
            templates = listOf(
                ItemTemplate(
                    prompt = "Er steht neben ___ Fenster.",
                    answer = "dem",
                    choices = listOf("dem", "das", "den", "des"),
                    hint = "Wo? → location",
                ),
                ItemTemplate(
                    prompt = "Er stellt sich neben ___ Fenster.",
                    answer = "das",
                    choices = listOf("das", "dem", "den", "des"),
                    hint = "Wohin? → movement",
                ),
            ),
        ),
        PrepositionEntry(
            preposition = "über",
            templates = listOf(
                ItemTemplate(
                    prompt = "Die Lampe hängt über ___ Sofa.",
                    answer = "dem",
                    choices = listOf("dem", "den", "der", "das"),
                    hint = "Wo? → location",
                ),
                ItemTemplate(
                    prompt = "Er hängt die Lampe über ___ Sofa.",
                    answer = "das",
                    choices = listOf("das", "dem", "den", "der"),
                    hint = "Wohin? → movement",
                ),
            ),
        ),
        PrepositionEntry(
            preposition = "unter",
            templates = listOf(
                ItemTemplate(
                    prompt = "Die Katze schläft unter ___ Bett.",
                    answer = "dem",
                    choices = listOf("dem", "das", "den", "des"),
                    hint = "Wo? → location",
                ),
                ItemTemplate(
                    prompt = "Die Katze kriecht unter ___ Bett.",
                    answer = "das",
                    choices = listOf("das", "dem", "den", "des"),
                    hint = "Wohin? → movement",
                ),
            ),
        ),
        PrepositionEntry(
            preposition = "vor",
            templates = listOf(
                ItemTemplate(
                    prompt = "Das Auto steht vor ___ Garage.",
                    answer = "der",
                    choices = listOf("der", "die", "den", "dem"),
                    hint = "Wo? → location",
                ),
                ItemTemplate(
                    prompt = "Das Auto fährt vor ___ Garage.",
                    answer = "die",
                    choices = listOf("die", "der", "den", "dem"),
                    hint = "Wohin? → movement",
                ),
            ),
        ),
        PrepositionEntry(
            preposition = "zwischen",
            templates = listOf(
                ItemTemplate(
                    prompt = "Das Kind sitzt zwischen ___ Stühlen.",
                    answer = "den",
                    choices = listOf("den", "dem", "der", "die"),
                    hint = "Wo? → location",
                ),
                ItemTemplate(
                    prompt = "Das Kind setzt sich zwischen ___ Stühle.",
                    answer = "die",
                    choices = listOf("die", "den", "der", "dem"),
                    hint = "Wohin? → movement",
                ),
            ),
        ),
    )

    /**
     * Builds drill items for the preposition tables.
     *
     * If a saved word equals a table preposition (case-insensitively), its items
     * use the word ID as their key; otherwise they use the stable table key `prep:<preposition>:<n>`.
     * Saved-word items match no authored help item, so they carry no rule id
     * (the help card stays hidden for them).
     */
    fun items(words: Collection<Word> = emptyList()): List<DrillItem> {
        val wordMap = words.associateBy { it.text.trim().lowercase() }
        return ENTRIES.flatMap { entry ->
            val matchingWord = wordMap[entry.preposition.lowercase()]
            entry.templates.mapIndexed { index, template ->
                val key = matchingWord?.let { "${it.id}" } ?: "prep:${entry.preposition}:$index"
                val content = if (matchingWord == null) PREPOSITION_RULE_CONTENT[key] else null
                val ruleId = content?.ruleId
                    ?.let { DrillHelpContent.requireValidRuleId(it, "prepositions item $key") }
                DrillItem(
                    id = DrillItemId(DrillType.PREPOSITIONS, key),
                    prompt = template.prompt,
                    answer = template.answer,
                    choices = template.choices,
                    hint = template.hint,
                    ruleId = ruleId,
                    missByChoice = content?.missByChoice ?: emptyMap(),
                )
            }
        }
    }
}
