package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceWordMatchTest {

    private val lemmaMap = mapOf(
        "ging" to "gehen",
        "geht" to "gehen",
        "gehe" to "gehen",
        "Häuser" to "Haus",
        "Hauses" to "Haus",
        "Haus" to "Haus"
    )

    private val defaultLemmaOf: (String) -> String? = { form ->
        lemmaMap[form] ?: lemmaMap[form.lowercase()] ?: lemmaMap[form.replaceFirstChar { it.uppercase() }]
    }

    @Test
    fun `tokens splits punctuation and preserves umlauts and inner hyphens`() {
        assertEquals(
            listOf("Sie", "ging", "nach", "Hause", "schnell", "oder"),
            SentenceWordMatch.tokens("Sie ging nach Hause – schnell, oder?")
        )
        assertEquals(
            listOf("Baden-Württemberg", "wählt"),
            SentenceWordMatch.tokens("Baden-Württemberg wählt.")
        )
    }

    @Test
    fun `matches inflected forms articles stripped and rejects non-matching forms`() {
        val matcher = SentenceWordMatcher(defaultLemmaOf)
        assertTrue(matcher.matches("gehen", "Er ging gestern früh."))
        assertTrue(matcher.matches("gehen", "Ging er wirklich?"))
        assertTrue(matcher.matches("Haus", "Die Häuser sind alt."))
        assertTrue(matcher.matches("das Haus", "Die Häuser sind alt."))
        assertFalse(matcher.matches("Haus", "Er geht nach Hause."))
    }

    @Test
    fun `exact-form fallback with null lemma matches exact token but not other inflections`() {
        val matcher = SentenceWordMatcher { null }
        assertTrue(matcher.matches("Hund", "Der Hund bellt."))
        assertFalse(matcher.matches("Hund", "Die Hunde bellen."))
    }

    @Test
    fun `phrase matches with whitespace as substring word boundary but not concatenated`() {
        val matcher = SentenceWordMatcher(defaultLemmaOf)
        assertTrue(matcher.matches("zum Beispiel", "Das ist zum Beispiel gut."))
        assertFalse(matcher.matches("zum Beispiel", "Das ist zumBeispiel gut."))
    }

    @Test
    fun `short words under minimum chars never match`() {
        val matcher = SentenceWordMatcher(defaultLemmaOf)
        assertFalse(matcher.matches("ab", "Er fuhr ab."))
        assertFalse(matcher.matches("ab", "ab ab ab"))
    }

    @Test
    fun `memo matching 3 words against a sentence containing ging twice calls lemma function once for ging`() {
        var gingCalls = 0
        val matcher = SentenceWordMatcher { form ->
            if (form == "ging") gingCalls++
            lemmaMap[form] ?: lemmaMap[form.lowercase()] ?: lemmaMap[form.replaceFirstChar { it.uppercase() }]
        }
        val sentence = "Er ging und ging schnell."
        matcher.matches("Haus", sentence)
        matcher.matches("Katze", sentence)
        matcher.matches("Hund", sentence)
        assertEquals(1, gingCalls)
    }

    @Test
    fun `candidates returns newest first respecting maxPerWord and omits unmatched words`() {
        val matcher = SentenceWordMatcher(defaultLemmaOf)
        val words = listOf(
            Word(id = 101L, text = "gehen", dueDay = 1L, addedAt = 1L),
            Word(id = 102L, text = "Katze", dueDay = 1L, addedAt = 1L)
        )
        val sentences = listOf(
            RecentSentence(
                id = "s1",
                text = "Er ging nach Hause.",
                source = "src",
                url = "https://example.com/1",
                publishedAt = 1000L,
                fetchedAt = 1000L
            ),
            RecentSentence(
                id = "s2",
                text = "Er geht schnell.",
                source = "src",
                url = "https://example.com/2",
                publishedAt = 4000L,
                fetchedAt = 4000L
            ),
            RecentSentence(
                id = "s3",
                text = "Sie ging einkaufen.",
                source = "src",
                url = "https://example.com/3",
                publishedAt = 2000L,
                fetchedAt = 2000L
            ),
            RecentSentence(
                id = "s4",
                text = "Das Haus ist neu.",
                source = "src",
                url = "https://example.com/4",
                publishedAt = 3000L,
                fetchedAt = 3000L
            )
        )

        val result = matcher.candidates(words, sentences, maxPerWord = 2)

        assertEquals(setOf(101L), result.keys)
        assertEquals(listOf("s2", "s3"), result[101L]?.map { it.id })
    }

    @Test
    fun `candidates breaks ties in publishedAt with shorter text first`() {
        val matcher = SentenceWordMatcher(defaultLemmaOf)
        val words = listOf(Word(id = 201L, text = "gehen", dueDay = 1L, addedAt = 1L))
        val sentences = listOf(
            RecentSentence(
                id = "longer",
                text = "Er geht heute wirklich schnell durch die Stadt.",
                source = "src",
                url = "https://example.com/long",
                publishedAt = 5000L,
                fetchedAt = 5000L
            ),
            RecentSentence(
                id = "shorter",
                text = "Er geht.",
                source = "src",
                url = "https://example.com/short",
                publishedAt = 5000L,
                fetchedAt = 5000L
            )
        )

        val result = matcher.candidates(words, sentences, maxPerWord = 2)
        assertEquals(listOf("shorter", "longer"), result[201L]?.map { it.id })
    }
}
