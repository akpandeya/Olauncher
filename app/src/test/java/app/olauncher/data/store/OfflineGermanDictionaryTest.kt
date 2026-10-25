package app.olauncher.data.store

import app.olauncher.data.store.WiktionaryApi.LookupResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineGermanDictionaryTest {
    private val gehen = OfflineGermanEntry(
        "gehen", "verb", "", "", "to go, to walk", "geht", "ging", "gegangen"
    )
    private val maedchen = OfflineGermanEntry("Mädchen", "noun", "das", "Mädchen", "girl", "", "", "")
    private val haus = OfflineGermanEntry("Haus", "noun", "das", "Häuser", "house, building", "", "", "")
    private val hund = OfflineGermanEntry("Hund", "noun", "der", "Hunde", "dog", "", "", "")
    private val katze = OfflineGermanEntry("Katze", "noun", "die", "Katzen", "cat", "", "", "")
    private val gehenNoun = OfflineGermanEntry("Gehen", "noun", "das", "", "walking", "", "", "")
    private val dictionary = OfflineGermanDictionary { form ->
        when (form) {
            "Gehen" -> listOf(gehenNoun)
            "gehen", "ging" -> listOf(gehen)
            "Mädchen" -> listOf(maedchen)
            "Haus", "Häuser" -> listOf(haus)
            "Hund", "Hunde" -> listOf(hund)
            "Katze", "Katzen" -> listOf(katze)
            else -> emptyList()
        }
    }

    @Test
    fun `verb and inflected word include base lemma and main forms`() {
        for (word in listOf("gehen", "ging")) {
            assertEquals(
                LookupResult.Found(
                    "gehen (verb) · geht · ging · gegangen — to go, to walk", "gehen",
                    baseForm = "gehen".takeIf { word == "ging" }
                ),
                dictionary.lookup(word, "plural")
            )
        }
    }

    @Test
    fun `noun lookups include gender and plural and normalize case article and unicode`() {
        assertEquals(
            LookupResult.Found("das Mädchen (noun) · Mehrzahl: Mädchen — girl", "Mädchen"),
            dictionary.lookup(" ma\u0308dchen ", "Mehrzahl")
        )
        assertEquals(
            LookupResult.Found("das Haus (noun) · plural: Häuser — house, building", "Haus"),
            dictionary.lookup("das Haus", "plural")
        )
        assertEquals("Haus", dictionary.lookup("Häuser", "plural")?.candidate)
        assertNull(dictionary.lookup("no-such-word", "plural"))
    }

    @Test
    fun `offline hits bypass online cache and network even without network consent`() {
        for (allowed in listOf(false, true)) {
            assertEquals(
                dictionary.lookup("ging", "plural"),
                GermanLookup.lookup(
                    "ging", allowed, { dictionary.lookup(it, "plural") },
                    { error("Cache must not hide an offline hit") },
                    { error("Network must not run on an offline hit") },
                    { _, _ -> error("Offline result must not replace the online cache") }
                )
            )
        }
    }

    @Test
    fun `offline miss without consent never reads cache or uses network`() {
        assertEquals(
            LookupResult.NotFound,
            GermanLookup.lookup(
                "unknown", false, { null },
                { error("No online cache without consent") },
                { error("No network without consent") },
                { _, _ -> error("No cache write without consent") }
            )
        )
    }

    @Test
    fun `offline miss falls back to existing cache first`() {
        val cached = LookupResult.Found("cached meaning", "unusual")
        val calls = mutableListOf<String>()
        assertEquals(cached, GermanLookup.lookup(
            "query", true,
            { calls.add("offline:$it"); null },
            { calls.add("cache:$it"); cached },
            { error("Cached entry must avoid network") },
            { _, _ -> error("Cached entry must not be written again") }
        ))
        assertEquals(listOf("offline:query", "cache:query"), calls)
    }

    @Test
    fun `offline and cache misses pass exact query and result to existing online cache writer`() {
        val result = LookupResult.Found("network-only meaning", "NetworkLemma")
        var written: Pair<String, LookupResult>? = null
        var networkCalled = false
        assertEquals(result, GermanLookup.lookup(
            "NetworkQuery", true, { null }, { null },
            { query ->
                assertEquals("NetworkQuery", query)
                networkCalled = true
                result
            },
            { query, value -> written = query to value }
        ))
        assertEquals("NetworkQuery" to result, written)
        assertTrue(networkCalled)
    }

    @Test
    fun `gender lookups extract noun gender and return null for verbs and misses`() {
        assertEquals("der", dictionary.gender("Hund"))
        assertEquals("der", dictionary.gender("der Hund"))
        assertEquals("die", dictionary.gender("Katze"))
        assertEquals("die", dictionary.gender("die Katze"))
        assertEquals("das", dictionary.gender("Mädchen"))
        assertEquals("das", dictionary.gender("Haus"))
        assertEquals("das", dictionary.gender("Häuser"))
        assertNull(dictionary.gender("gehen"))
        assertNull(dictionary.gender("ging"))
        assertNull(dictionary.gender("unknown"))
    }

    @Test
    fun `lowercase verb with a same-spelling noun gets no gender but the noun keeps it`() {
        assertNull(dictionary.gender("gehen"))
        assertEquals("das", dictionary.gender("Gehen"))
        assertEquals("das", OfflineGermanDictionary.genderOf("haus") { if (it == "Haus") listOf(haus) else emptyList() })
    }
}
