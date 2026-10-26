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
    fun candidateForms(noun: String): List<String> {
        val stem = noun.trim()
        val umlautStem = applyUmlaut(stem)
        val candidates = linkedSetOf<String>()

        // 1. -e
        val formE = if (stem.endsWith("e")) stem else stem + "e"
        candidates += formE

        // 2. umlaut plus -e
        if (umlautStem != null) {
            val formUmlautE = if (umlautStem.endsWith("e")) umlautStem else umlautStem + "e"
            candidates += formUmlautE
        }

        // 3. -er
        candidates += stem + "er"

        // 4. umlaut plus -er
        if (umlautStem != null) {
            candidates += umlautStem + "er"
        }

        // 5. -n
        candidates += if (stem.endsWith("e") || stem.endsWith("el") || stem.endsWith("er")) {
            stem + "n"
        } else {
            stem + "n"
        }

        // 6. -en
        candidates += if (stem.endsWith("e")) {
            stem + "n"
        } else {
            stem + "en"
        }

        // 7. -s
        candidates += stem + "s"

        // 8. no change
        candidates += stem

        // Umlaut without suffix (e.g. Apfel -> Äpfel)
        if (umlautStem != null) {
            candidates += umlautStem
        }

        return candidates.toList()
    }

    /**
     * Builds exactly 4 choice options containing [answer] and 3 distinct distractors
     * generated from plural patterns, none of which equals [answer].
     */
    fun buildChoices(noun: String, answer: String): List<String> {
        val candidates = candidateForms(noun)
            .filter { it != answer }
            .distinct()

        val distractors = candidates.take(3).toMutableList()
        if (distractors.size < 3) {
            val fallbacks = listOf("e", "en", "er", "s", "n")
            for (fb in fallbacks) {
                val candidate = noun + fb
                if (candidate != answer && candidate !in distractors) {
                    distractors += candidate
                    if (distractors.size == 3) break
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
                DrillItem(
                    id = DrillItemId(DrillType.PLURALS, "plur:${entry.noun.lowercase()}"),
                    prompt = "${entry.gender} ${entry.noun} → die ${DrillItem.GAP_MARKER}",
                    answer = entry.plural,
                    choices = buildChoices(entry.noun, entry.plural),
                    hint = entry.pattern.hint,
                )
            }

        return savedItems + tableItems
    }
}
