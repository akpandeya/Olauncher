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
        for (candidate in WiktionaryParser.candidates(normalized)) {
            val entries = findEntries(candidate)
            if (entries.isNotEmpty()) {
                return WiktionaryApi.LookupResult.Found(
                    entries.take(2).joinToString("; ") { it.meaning(pluralLabel) },
                    entries.first().lemma
                )
            }
        }
        return null
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
