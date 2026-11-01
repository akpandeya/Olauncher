package app.olauncher.data.store

import java.text.Normalizer

data class OfflineGermanEntry(
    val lemma: String,
    val pos: String,
    val gender: String,
    val plural: String,
    val gloss: String,
    val present: String,
    val past: String,
    val participle: String
) {
    fun meaning(pluralLabel: String): String {
        val headword = listOf(gender, lemma).filter { it.isNotBlank() }.joinToString(" ")
        val details = if (pos == "verb") {
            listOf(present, past, participle).filter { it.isNotBlank() }
        } else {
            listOfNotNull(plural.takeIf { it.isNotBlank() }?.let { "$pluralLabel: $it" })
        }
        return (listOf("$headword ($pos)") + details).joinToString(" · ") + " — $gloss"
    }
}

class OfflineGermanDictionary(private val findEntries: (String) -> List<OfflineGermanEntry>) {
    fun lookup(text: String, pluralLabel: String): WiktionaryApi.LookupResult.Found? {
        val normalized = Normalizer.normalize(text.trim(), Normalizer.Form.NFC)
        val candidates = WiktionaryParser.candidates(normalized)
        for (candidate in candidates) {
            val entries = findEntries(candidate)
            if (entries.isNotEmpty()) {
                val lemma = entries.first().lemma
                // The form table maps inflections to their lemma entry; flag that so it is saved instead.
                val baseForm = lemma.takeIf { l -> candidates.none { it.equals(l, ignoreCase = true) } }
                return WiktionaryApi.LookupResult.Found(
                    entries.take(2).joinToString("; ") { it.meaning(pluralLabel) },
                    lemma,
                    baseForm
                )
            }
        }
        return null
    }

    /**
     * Noun gender for [text], or null. The first spelling that has any entry decides: a verb such
     * as "gehen" must not fall through to the noun "Gehen" (#515). Shared with [OfflineGermanStore].
     */
    fun gender(text: String): String? = genderOf(text, findEntries)

    /**
     * Noun plural for [text], or null.
     */
    fun plural(text: String): String? = pluralOf(text, findEntries)

    fun lemma(text: String): String? = lemmaOf(text, findEntries)

    companion object {
        fun lemmaOf(text: String, findEntries: (String) -> List<OfflineGermanEntry>): String? {
            val clean = text.trim()
            if (clean.isEmpty()) return null
            val normalized = Normalizer.normalize(clean, Normalizer.Form.NFC)
            for (candidate in WiktionaryParser.candidates(normalized)) {
                val entries = findEntries(candidate)
                if (entries.isNotEmpty()) return entries.first().lemma
            }
            return null
        }

        fun genderOf(text: String, findEntries: (String) -> List<OfflineGermanEntry>): String? {
            val normalized = Normalizer.normalize(text.trim(), Normalizer.Form.NFC)
            for (candidate in WiktionaryParser.candidates(normalized)) {
                val entries = findEntries(candidate)
                if (entries.isEmpty()) continue
                return entries.firstOrNull { it.gender.isNotBlank() }?.gender
            }
            return null
        }

        fun pluralOf(text: String, findEntries: (String) -> List<OfflineGermanEntry>): String? {
            val normalized = Normalizer.normalize(text.trim(), Normalizer.Form.NFC)
            for (candidate in WiktionaryParser.candidates(normalized)) {
                val entries = findEntries(candidate)
                if (entries.isEmpty()) continue
                val plural = entries.firstOrNull { it.plural.isNotBlank() }?.plural
                if (!plural.isNullOrBlank()) return plural
            }
            return null
        }
    }
}

/** Keeps offline hits independent of network consent and of stale online cache entries. */
object GermanLookup {
    fun lookup(
        text: String,
        onlineAllowed: Boolean,
        offline: (String) -> WiktionaryApi.LookupResult.Found?,
        cached: (String) -> WiktionaryApi.LookupResult?,
        online: (String) -> WiktionaryApi.LookupResult,
        cache: (String, WiktionaryApi.LookupResult) -> Unit
    ): WiktionaryApi.LookupResult {
        offline(text)?.let { return it }
        if (!onlineAllowed) return WiktionaryApi.LookupResult.NotFound
        return cached(text) ?: online(text).also { cache(text, it) }
    }
}
