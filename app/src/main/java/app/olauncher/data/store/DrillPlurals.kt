package app.olauncher.data.store

import app.olauncher.ui.words.NounArticle

/**
 * Drill content for German noun plurals.
 */
object DrillPlurals {

    enum class PluralPattern(val hint: String) {
        E("-e"),
        UMLAUT_E("¨-e"),
        ER("-er"),
        UMLAUT_ER("¨-er"),
        N("-n"),
        EN("-en"),
        S("-s"),
        NO_CHANGE("—"),
    }

    data class PluralEntry(
        val noun: String,
        val gender: String,
        val plural: String,
        val pattern: PluralPattern,
    )

    /**
     * Replaces the last umlautable vowel or vowel combination in [word] with its umlaut.
     * German umlauts: a -> ä, o -> ö, u -> ü, au -> äu (preserving case).
     */
    fun applyUmlaut(word: String): String? {
        val trimmed = word.trim()
        for (i in trimmed.length - 1 downTo 0) {
            if (i > 0 && trimmed.substring(i - 1, i + 1).equals("au", ignoreCase = true)) {
                val replacement = if (trimmed[i - 1].isUpperCase()) "Äu" else "äu"
                return trimmed.substring(0, i - 1) + replacement + trimmed.substring(i + 1)
            }
            val ch = trimmed[i]
            val umlaut = when (ch) {
                'a' -> 'ä'
                'A' -> 'Ä'
                'o' -> 'ö'
                'O' -> 'Ö'
                'u' -> 'ü'
                'U' -> 'Ü'
                else -> null
            }
            if (umlaut != null) {
                return trimmed.substring(0, i) + umlaut + trimmed.substring(i + 1)
            }
        }
        return null
    }

    /**
     * Generates plausible plural forms for [noun] using the 8 plural patterns:
     * (-e, umlaut plus -e, -er, umlaut plus -er, -n, -en, -s, no change).
     */
    fun candidateForms(noun: String): List<String> =
        candidatePatternForms(noun).map { it.first }

    /**
     * The pattern slot of each generated candidate form, most plausible first.
     *
     * Mirrors the exact construction order [buildChoices] takes the wrong distractors
     * from, so the help content can key each wrong choice to its authored miss line
     * (docs/design/drill-content/plurals.md). The label is the pattern slot the form
     * fills: "-e", "umlaut-e", "-er", "umlaut-er", "-n", "-en", "-s", "no-change" and
     * "umlaut" (the umlauted stem alone). Where one form fits two slots the first in
     * construction order wins, matching the first-wins keeps of the choice builder.
     */
    internal fun candidatePatternForms(noun: String): List<Pair<String, String>> {
        val stem = noun.trim()
        val umlautStem = applyUmlaut(stem)
        val umlautable = umlautStem != null
        val stemEndsWithE = stem.endsWith("e")
        val umlautE = umlautStem?.let { if (it.endsWith("e")) it else it + "e" }

        val formsWithSlots = mutableListOf<Pair<String, String>>()
        fun slot(form: String, label: String) {
            formsWithSlots += form to label
        }

        // 1. -e (the bare stem when it already ends in -e: the unchanged form)
        slot(if (stemEndsWithE) stem else stem + "e", if (stemEndsWithE) "no-change" else "-e")
        // 2. umlaut plus -e
        if (umlautable) slot(umlautE!!, "umlaut-e")
        // 3. -er
        slot(stem + "er", "-er")
        // 4. umlaut plus -er
        if (umlautable) slot(umlautStem + "er", "umlaut-er")
        // 5. -n
        slot(stem + "n", "-n")
        // 6. -en (repeats the -n form on nouns that already end in -e)
        slot(if (stemEndsWithE) stem + "n" else stem + "en", "-en")
        // 7. -s
        slot(stem + "s", "-s")
        // 8. no change
        slot(stem, "no-change")
        // 9. umlaut without suffix (e.g. Apfel -> Äpfel)
        if (umlautable) slot(umlautStem, "umlaut")

        val seen = mutableSetOf<String>()
        return formsWithSlots.filter { seen.add(it.first) }
    }

    /** The number of wrong choices an item carries alongside its answer. */
    internal const val WRONG_CHOICE_COUNT = 3

    /**
     * Builds exactly 4 choice options containing [answer] and 3 distinct distractors
     * generated from plural patterns, none of which equals [answer].
     */
    fun buildChoices(noun: String, answer: String): List<String> {
        val candidates = candidatePatternForms(noun)
            .map { it.first }
            .filter { it != answer }

        val distractors = candidates.take(WRONG_CHOICE_COUNT).toMutableList()
        if (distractors.size < WRONG_CHOICE_COUNT) {
            val fallbacks = listOf("e", "en", "er", "s", "n")
            for (fb in fallbacks) {
                val candidate = noun + fb
                if (candidate != answer && candidate !in distractors) {
                    distractors += candidate
                    if (distractors.size == WRONG_CHOICE_COUNT) break
                }
            }
        }
        return listOf(answer) + distractors
    }

    val TABLE_ENTRIES: List<PluralEntry> = listOf(
        // Pattern 1: -e (no umlaut)
        PluralEntry("Tag", "der", "Tage", PluralPattern.E),
        PluralEntry("Hund", "der", "Hunde", PluralPattern.E),
        PluralEntry("Tisch", "der", "Tische", PluralPattern.E),
        PluralEntry("Monat", "der", "Monate", PluralPattern.E),
        PluralEntry("Freund", "der", "Freunde", PluralPattern.E),
        PluralEntry("Jahr", "das", "Jahre", PluralPattern.E),
        PluralEntry("Schuh", "der", "Schuhe", PluralPattern.E),
        PluralEntry("Boot", "das", "Boote", PluralPattern.E),

        // Pattern 2: umlaut plus -e
        PluralEntry("Hand", "die", "Hände", PluralPattern.UMLAUT_E),
        PluralEntry("Stadt", "die", "Städte", PluralPattern.UMLAUT_E),
        PluralEntry("Nacht", "die", "Nächte", PluralPattern.UMLAUT_E),
        PluralEntry("Stuhl", "der", "Stühle", PluralPattern.UMLAUT_E),
        PluralEntry("Baum", "der", "Bäume", PluralPattern.UMLAUT_E),
        PluralEntry("Ball", "der", "Bälle", PluralPattern.UMLAUT_E),
        PluralEntry("Wand", "die", "Wände", PluralPattern.UMLAUT_E),
        PluralEntry("Zug", "der", "Züge", PluralPattern.UMLAUT_E),

        // Pattern 3: -er (no umlaut)
        PluralEntry("Kind", "das", "Kinder", PluralPattern.ER),
        PluralEntry("Bild", "das", "Bilder", PluralPattern.ER),
        PluralEntry("Kleid", "das", "Kleider", PluralPattern.ER),
        PluralEntry("Licht", "das", "Lichter", PluralPattern.ER),
        PluralEntry("Ei", "das", "Eier", PluralPattern.ER),
        PluralEntry("Feld", "das", "Felder", PluralPattern.ER),
        PluralEntry("Lied", "das", "Lieder", PluralPattern.ER),

        // Pattern 4: umlaut plus -er
        PluralEntry("Haus", "das", "Häuser", PluralPattern.UMLAUT_ER),
        PluralEntry("Buch", "das", "Bücher", PluralPattern.UMLAUT_ER),
        PluralEntry("Mann", "der", "Männer", PluralPattern.UMLAUT_ER),
        PluralEntry("Rad", "das", "Räder", PluralPattern.UMLAUT_ER),
        PluralEntry("Wald", "der", "Wälder", PluralPattern.UMLAUT_ER),
        PluralEntry("Glas", "das", "Gläser", PluralPattern.UMLAUT_ER),
        PluralEntry("Dorf", "das", "Dörfer", PluralPattern.UMLAUT_ER),
        PluralEntry("Blatt", "das", "Blätter", PluralPattern.UMLAUT_ER),

        // Pattern 5: -n
        PluralEntry("Katze", "die", "Katzen", PluralPattern.N),
        PluralEntry("Straße", "die", "Straßen", PluralPattern.N),
        PluralEntry("Schule", "die", "Schulen", PluralPattern.N),
        PluralEntry("Frage", "die", "Fragen", PluralPattern.N),
        PluralEntry("Reise", "die", "Reisen", PluralPattern.N),
        PluralEntry("Lampe", "die", "Lampen", PluralPattern.N),
        PluralEntry("Tasche", "die", "Taschen", PluralPattern.N),
        PluralEntry("Auge", "das", "Augen", PluralPattern.N),

        // Pattern 6: -en
        PluralEntry("Frau", "die", "Frauen", PluralPattern.EN),
        PluralEntry("Zeit", "die", "Zeiten", PluralPattern.EN),
        PluralEntry("Zeitung", "die", "Zeitungen", PluralPattern.EN),
        PluralEntry("Wohnung", "die", "Wohnungen", PluralPattern.EN),
        PluralEntry("Bett", "das", "Betten", PluralPattern.EN),
        PluralEntry("Hemd", "das", "Hemden", PluralPattern.EN),
        PluralEntry("Ohr", "das", "Ohren", PluralPattern.EN),
        PluralEntry("Mensch", "der", "Menschen", PluralPattern.EN),

        // Pattern 7: -s
        PluralEntry("Auto", "das", "Autos", PluralPattern.S),
        PluralEntry("Foto", "das", "Fotos", PluralPattern.S),
        PluralEntry("Radio", "das", "Radios", PluralPattern.S),
        PluralEntry("Sofa", "das", "Sofas", PluralPattern.S),
        PluralEntry("Chef", "der", "Chefs", PluralPattern.S),
        PluralEntry("Hotel", "das", "Hotels", PluralPattern.S),
        PluralEntry("Park", "der", "Parks", PluralPattern.S),
        PluralEntry("Kino", "das", "Kinos", PluralPattern.S),

        // Pattern 8: no change
        PluralEntry("Fenster", "das", "Fenster", PluralPattern.NO_CHANGE),
        PluralEntry("Zimmer", "das", "Zimmer", PluralPattern.NO_CHANGE),
        PluralEntry("Mädchen", "das", "Mädchen", PluralPattern.NO_CHANGE),
        PluralEntry("Brötchen", "das", "Brötchen", PluralPattern.NO_CHANGE),
        PluralEntry("Wagen", "der", "Wagen", PluralPattern.NO_CHANGE),
        PluralEntry("Schlüssel", "der", "Schlüssel", PluralPattern.NO_CHANGE),
        PluralEntry("Gebäude", "das", "Gebäude", PluralPattern.NO_CHANGE),
        PluralEntry("Lehrer", "der", "Lehrer", PluralPattern.NO_CHANGE),
    )

    var defaultPluralLookup: ((String) -> String?)? = null
    var defaultGenderLookup: ((String) -> String?)? = null

    /**
     * Builds drill items for plurals.
     *
     * Saved nouns with a known plural from the dictionary yield items keyed by word id.
     * Table nouns not present among the saved nouns fill the rest.
     */
    fun items(
        words: Collection<Word> = emptyList(),
        pluralLookup: ((String) -> String?)? = null,
        genderLookup: ((String) -> String?)? = null,
    ): List<DrillItem> {
        val pResolver = pluralLookup ?: defaultPluralLookup
        val gResolver = genderLookup ?: defaultGenderLookup
        val savedItems = mutableListOf<DrillItem>()
        val seenNouns = mutableSetOf<String>()

        for (word in words) {
            val noun = NounArticle.stripLeadingArticle(word.text).trim()
            if (noun.isBlank()) continue

            val rawPlural = pResolver?.invoke(noun) ?: pResolver?.invoke(word.text) ?: continue
            val trimmedPlural = rawPlural.trim()
            if (trimmedPlural.isBlank() || trimmedPlural == "-") continue

            val nounKey = noun.lowercase()
            if (!seenNouns.add(nounKey)) continue

            val dictGender = gResolver?.invoke(noun) ?: gResolver?.invoke(word.text)
            val gender = NounArticle.resolveGender(word.text, dictGender)

            val displayNoun = noun.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            val cleanPlural = trimmedPlural
                .split('/', ',')
                .first()
                .trim()
                .removePrefix("die ")
                .trim()
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

            val prompt = if (gender != null) {
                "$gender $displayNoun → die ${DrillItem.GAP_MARKER}"
            } else {
                "$displayNoun → die ${DrillItem.GAP_MARKER}"
            }

            val choices = buildChoices(displayNoun, cleanPlural)

            savedItems += DrillItem(
                id = DrillItemId(DrillType.PLURALS, "${word.id}"),
                prompt = prompt,
                answer = cleanPlural,
                choices = choices,
            )
        }

        val tableItems = TABLE_ENTRIES
            .filter { it.noun.lowercase() !in seenNouns }
            .map { entry ->
                val content = PLURAL_RULE_CONTENT_BY_NOUN.getValue(entry.noun.lowercase())
                DrillItem(
                    id = DrillItemId(DrillType.PLURALS, "plur:${entry.noun.lowercase()}"),
                    prompt = "${entry.gender} ${entry.noun} → die ${DrillItem.GAP_MARKER}",
                    answer = entry.plural,
                    choices = buildChoices(entry.noun, entry.plural),
                    hint = entry.pattern.hint,
                    ruleId = DrillHelpContent.requireValidRuleId(
                        content.ruleId,
                        "plurals item plur:${entry.noun.lowercase()}",
                    ),
                    missByChoice = content.missByChoiceFor(entry.noun, entry.plural),
                )
            }

        return savedItems + tableItems
    }
}
