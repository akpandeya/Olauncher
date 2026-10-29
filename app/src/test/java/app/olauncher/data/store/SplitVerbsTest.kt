package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitVerbsTest {

    /** Distinctive, non-default lemmas so a hard-coded or missing lookup cannot pass. */
    private val aufnehmenLemmas = listOf("xaufnehmenx")
    private val anrufenLemmas = listOf("xanrufenx")

    /** Cuts [text] into boxless words of one node, in reading order. */
    private fun words(text: String, nodeId: Int = 1): List<ScreenWord> {
        val out = ArrayList<ScreenWord>()
        var i = 0
        while (i < text.length) {
            while (i < text.length && text[i].isWhitespace()) i++
            val start = i
            while (i < text.length && !text[i].isWhitespace()) i++
            if (start < i) out += ScreenWord(text.substring(start, i), null, nodeId, start until i)
        }
        return out
    }

    private fun word(words: List<ScreenWord>, text: String): ScreenWord =
        words.first { it.text == text }

    @Test
    fun `clause-final particle joins the nearest preceding verb`() {
        val sentence = words("Ich nehme das Paket morgen auf.")
        val queries = mutableListOf<String>()
        val match = SplitVerbs.find(sentence, word(sentence, "auf.")) { query ->
            queries += query; if (query == "nehme auf") aufnehmenLemmas else emptyList()
        }

        assertEquals(SplitVerbs.Match(word(sentence, "nehme"), word(sentence, "auf."), "xaufnehmenx"), match)
        assertTrue(queries.contains("nehme auf"))
    }

    @Test
    fun `tapping the verb in the bracket gives the same match`() {
        val sentence = words("Ich nehme das Paket morgen auf.")
        val match = SplitVerbs.find(sentence, word(sentence, "nehme")) { query ->
            if (query == "nehme auf") aufnehmenLemmas else emptyList()
        }

        assertEquals(SplitVerbs.Match(word(sentence, "nehme"), word(sentence, "auf."), "xaufnehmenx"), match)
    }

    @Test
    fun `the split form query is lowercased`() {
        // "Nehme ich das auf?": the DB index is case-sensitive, so the query must lower-case
        // every candidate — nearest verb first: das, ich, then the matching nehme.
        val sentence = words("Nehme ich das auf?")
        val queries = mutableListOf<String>()
        val match = SplitVerbs.find(sentence, word(sentence, "auf?")) { query ->
            queries += query; if (query == "nehme auf") aufnehmenLemmas else emptyList()
        }

        assertEquals(listOf("das auf", "ich auf", "nehme auf"), queries)
        assertEquals(SplitVerbs.Match(word(sentence, "Nehme"), word(sentence, "auf?"), "xaufnehmenx"), match)
    }

    @Test
    fun `a preposition before the clause end yields no chip`() {
        // "Ich warte auf dich": the tapped auf is not clause-final, so it is treated as the
        // verb candidate and paired with the clause's last word — never looked up alone.
        val sentence = words("Ich warte auf dich.")
        val queries = mutableListOf<String>()
        val match = SplitVerbs.find(sentence, word(sentence, "auf")) { query ->
            queries += query; emptyList()
        }

        assertNull(match)
        assertEquals(listOf("auf dich"), queries)
        assertFalse(queries.contains("auf"))
    }

    @Test
    fun `a comma ends the clause so the join stays inside it`() {
        val sentence = words("Ich rufe dich an, wenn ich kann.")
        val queries = mutableListOf<String>()
        val match = SplitVerbs.find(sentence, word(sentence, "an,")) { query ->
            queries += query; if (query == "rufe an") anrufenLemmas else emptyList()
        }

        assertEquals(SplitVerbs.Match(word(sentence, "rufe"), word(sentence, "an,"), "xanrufenx"), match)
        assertEquals(listOf("dich an", "rufe an"), queries)
    }

    @Test
    fun `a join never crosses a clause`() {
        val sentence = words("Er nimmt es. Dann auf.")
        val queries = mutableListOf<String>()
        // Only a cross-clause query would match; the detector must never ask it.
        val match = SplitVerbs.find(sentence, word(sentence, "auf.")) { query ->
            queries += query
            if (query == "nimmt auf" || query == "es auf") anrufenLemmas else emptyList()
        }

        assertNull(match)
        assertEquals(listOf("dann auf"), queries)
    }

    @Test
    fun `the nearest verb wins when two could match`() {
        val sentence = words("Ich sah dich kommen an.")
        val queries = mutableListOf<String>()
        val match = SplitVerbs.find(sentence, word(sentence, "an.")) { query ->
            queries += query
            when (query) {
                "kommen an" -> listOf("xkommenx")
                "dich an" -> listOf("xdichx")
                else -> emptyList()
            }
        }

        assertEquals("kommen an", queries.first())
        assertEquals(SplitVerbs.Match(word(sentence, "kommen"), word(sentence, "an."), "xkommenx"), match)
    }

    @Test
    fun `the no-space lemma is preferred over the spaced spelling`() {
        val sentence = words("Er bleibt bestehen.")
        val lemma = SplitVerbs.find(sentence, word(sentence, "bestehen.")) { _ ->
            listOf("bestehen bleiben", "bestehenbleiben")
        }?.lemma

        assertEquals("bestehenbleiben", lemma)
    }

    @Test
    fun `two unrelated lemmas are never guessed`() {
        val sentence = words("Er bleibt bestehen.")
        val match = SplitVerbs.find(sentence, word(sentence, "bestehen.")) { _ ->
            listOf("alpha", "beta")
        }

        assertNull(match)
    }

    @Test
    fun `verb candidates are capped at twelve, nearest first`() {
        val sentence = words((1..14).joinToString(" ") { "w$it" } + " ziel.")
        val queries = mutableListOf<String>()
        SplitVerbs.find(sentence, word(sentence, "ziel.")) { query ->
            queries += query; emptyList()
        }

        assertEquals(12, queries.size)
        assertEquals("w14 ziel", queries.first())
        assertEquals("w3 ziel", queries.last())
        assertFalse(queries.contains("w1 ziel"))
        assertFalse(queries.contains("w2 ziel"))
    }
}
