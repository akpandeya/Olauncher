package app.olauncher.data.store

import app.olauncher.ui.words.NounArticle

/**
 * Drill content for German noun genders and articles (der / die / das).
 */
object DrillArticles {

    val CHOICES: List<String> = listOf("der", "die", "das")

    internal data class GenderRule(
        val suffix: String,
        val gender: String,
        val hint: String,
    )

    internal data class ArticleEntry(
        val noun: String,
        val gender: String,
    )

    // Rule hints name the suffix and the gender but never the article itself,
    // because the article (der / die / das) is the item's answer.
    internal val RULES: List<GenderRule> = listOf(
        GenderRule("schaft", "die", "-schaft → feminin"),
        GenderRule("ismus", "der", "-ismus → maskulin"),
        GenderRule("keit", "die", "-keit → feminin"),
        GenderRule("heit", "die", "-heit → feminin"),
        GenderRule("ment", "das", "-ment → neutral"),
        GenderRule("chen", "das", "-chen → neutral"),
        GenderRule("lein", "das", "-lein → neutral"),
        GenderRule("ling", "der", "-ling → maskulin"),
        GenderRule("tät", "die", "-tät → feminin"),
        GenderRule("ung", "die", "-ung → feminin"),
        GenderRule("ion", "die", "-ion → feminin"),
        GenderRule("ei", "die", "-ei → feminin"),
        GenderRule("um", "das", "-um → neutral"),
    ).sortedByDescending { it.suffix.length }

    /**
     * Finds the gender rule hint for [noun] if one applies.
     *
     * A noun whose gender contradicts a matching rule (such as "Sprung" or "Baum")
     * is an exception and must never carry that rule's hint.
     */
    fun ruleHintFor(noun: String, actualGender: String): String? {
        val clean = noun.trim().lowercase()
        val canonical = NounArticle.canonicalGender(actualGender) ?: return null
        for (rule in RULES) {
            if (clean.length > rule.suffix.length && clean.endsWith(rule.suffix)) {
                return if (rule.gender == canonical) rule.hint else null
            }
        }
        return null
    }

    internal val TABLE_ENTRIES: List<ArticleEntry> = listOf(
        // Masculine (der)
        ArticleEntry("Mann", "der"),
        ArticleEntry("Tag", "der"),
        ArticleEntry("Tisch", "der"),
        ArticleEntry("Hund", "der"),
        ArticleEntry("Stuhl", "der"),
        ArticleEntry("Wagen", "der"),
        ArticleEntry("Monat", "der"),
        ArticleEntry("Vater", "der"),
        ArticleEntry("Bruder", "der"),
        ArticleEntry("Freund", "der"),
        ArticleEntry("Apfel", "der"),
        ArticleEntry("Baum", "der"), // ends in "um", breaks -um rule
        ArticleEntry("Schlüssel", "der"),
        ArticleEntry("Garten", "der"),
        ArticleEntry("Kaffee", "der"),
        ArticleEntry("Tee", "der"),
        ArticleEntry("Wald", "der"),
        ArticleEntry("Bahnhof", "der"),
        ArticleEntry("Platz", "der"),
        ArticleEntry("Schmetterling", "der"), // -ling: der
        ArticleEntry("Frühling", "der"), // -ling: der
        ArticleEntry("Optimismus", "der"), // -ismus: der
        ArticleEntry("Tourismus", "der"), // -ismus: der
        ArticleEntry("Sprung", "der"), // ends in "ung", breaks -ung rule
        ArticleEntry("Moment", "der"), // ends in "ment", breaks -ment rule

        // Feminine (die)
        ArticleEntry("Frau", "die"),
        ArticleEntry("Nacht", "die"),
        ArticleEntry("Sonne", "die"),
        ArticleEntry("Stadt", "die"),
        ArticleEntry("Straße", "die"),
        ArticleEntry("Katze", "die"),
        ArticleEntry("Schule", "die"),
        ArticleEntry("Mutter", "die"),
        ArticleEntry("Schwester", "die"),
        ArticleEntry("Hand", "die"),
        ArticleEntry("Zeit", "die"),
        ArticleEntry("Frage", "die"),
        ArticleEntry("Reise", "die"),
        ArticleEntry("Zeitung", "die"), // -ung: die
        ArticleEntry("Wohnung", "die"), // -ung: die
        ArticleEntry("Hoffnung", "die"), // -ung: die
        ArticleEntry("Freiheit", "die"), // -heit: die
        ArticleEntry("Gesundheit", "die"), // -heit: die
        ArticleEntry("Möglichkeit", "die"), // -keit: die
        ArticleEntry("Schnelligkeit", "die"), // -keit: die
        ArticleEntry("Freundschaft", "die"), // -schaft: die
        ArticleEntry("Landschaft", "die"), // -schaft: die
        ArticleEntry("Station", "die"), // -ion: die
        ArticleEntry("Situation", "die"), // -ion: die
        ArticleEntry("Universität", "die"), // -tät: die
        ArticleEntry("Bäckerei", "die"), // -ei: die

        // Neuter (das)
        ArticleEntry("Kind", "das"),
        ArticleEntry("Haus", "das"),
        ArticleEntry("Auto", "das"),
        ArticleEntry("Buch", "das"),
        ArticleEntry("Bild", "das"),
        ArticleEntry("Fenster", "das"),
        ArticleEntry("Wasser", "das"),
        ArticleEntry("Zimmer", "das"),
        ArticleEntry("Jahr", "das"),
        ArticleEntry("Geld", "das"),
        ArticleEntry("Bett", "das"),
        ArticleEntry("Brot", "das"),
        ArticleEntry("Kleid", "das"),
        ArticleEntry("Mädchen", "das"), // -chen: das
        ArticleEntry("Brötchen", "das"), // -chen: das
        ArticleEntry("Fräulein", "das"), // -lein: das
        ArticleEntry("Dokument", "das"), // -ment: das
        ArticleEntry("Instrument", "das"), // -ment: das
        ArticleEntry("Museum", "das"), // -um: das
        ArticleEntry("Zentrum", "das"), // -um: das
        ArticleEntry("Spiel", "das"),
    )

    var defaultGenderLookup: ((String) -> String?)? = null

    /**
     * Builds drill items for articles.
     *
     * Saved nouns with a known gender (via offline dictionary or leading article in saved text)
     * yield items keyed by word id. Table nouns not present among the saved nouns fill the rest.
     */
    fun items(
        words: Collection<Word> = emptyList(),
        genderLookup: ((String) -> String?)? = null,
    ): List<DrillItem> {
        val resolver = genderLookup ?: defaultGenderLookup
        val savedItems = mutableListOf<DrillItem>()
        val seenNouns = mutableSetOf<String>()

        for (word in words) {
            val dictGender = resolver?.invoke(word.text)
            val gender = NounArticle.resolveGender(word.text, dictGender) ?: continue
            val noun = NounArticle.stripLeadingArticle(word.text).trim()
            if (noun.isBlank()) continue
            val nounKey = noun.lowercase()
            if (!seenNouns.add(nounKey)) continue

            val displayNoun = noun.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            val hint = ruleHintFor(noun, gender)
            savedItems += DrillItem(
                id = DrillItemId(DrillType.ARTICLES, "${word.id}"),
                prompt = "${DrillItem.GAP_MARKER} $displayNoun",
                answer = gender,
                choices = CHOICES,
                hint = hint,
            )
        }

        val tableItems = TABLE_ENTRIES
            .filter { it.noun.lowercase() !in seenNouns }
            .map { entry ->
                DrillItem(
                    id = DrillItemId(DrillType.ARTICLES, "art:${entry.noun.lowercase()}"),
                    prompt = "${DrillItem.GAP_MARKER} ${entry.noun}",
                    answer = entry.gender,
                    choices = CHOICES,
                    hint = ruleHintFor(entry.noun, entry.gender),
                )
            }

        return savedItems + tableItems
    }
}
