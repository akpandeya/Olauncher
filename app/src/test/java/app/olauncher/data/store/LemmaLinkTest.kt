package app.olauncher.data.store

import app.olauncher.data.store.WiktionaryApi.FetchOutcome
import app.olauncher.data.store.WiktionaryApi.LookupResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LemmaLinkTest {
    private val inflection = "vernagelt — inflection of vernageln: third-person singular present …"

    @Test
    fun `inflection gloss returns the base word`() {
        assertEquals("vernageln", LemmaLink.baseWord(inflection))
        assertEquals("vernageln", LemmaLink.baseWord("inflection of vernageln: third-person singular present"))
    }

    @Test
    fun `other form-of glosses return the base word`() {
        assertEquals("Haus", LemmaLink.baseWord("plural of Haus"))
        assertEquals("gehen", LemmaLink.baseWord("past participle of gehen"))
        assertEquals("gehen", LemmaLink.baseWord("present participle of gehen"))
        assertEquals("schön", LemmaLink.baseWord("strong nominative masculine singular of schön"))
    }

    @Test
    fun `normal gloss returns null`() {
        assertNull(LemmaLink.baseWord("house, building; home"))
        assertNull(LemmaLink.baseWord("capital of Germany"))
    }

    @Test
    fun `gloss without of returns null`() {
        assertNull(LemmaLink.baseWord("inflection"))
        assertNull(LemmaLink.baseWord("to nail shut"))
    }

    @Test
    fun `blank gloss returns null`() {
        assertNull(LemmaLink.baseWord(""))
        assertNull(LemmaLink.baseWord("   "))
    }

    @Test
    fun `resolve uses the base meaning`() {
        val resolved = LemmaLink.resolve(inflection) { if (it == "vernageln") "to nail shut" else null }
        assertEquals(LemmaLink.Resolution("vernageln", "to nail shut"), resolved)
    }

    @Test
    fun `resolve stops after one link`() {
        assertNull(LemmaLink.resolve(inflection) { "inflection of nageln: first-person singular" })
    }

    @Test
    fun `resolve returns null when base lookup fails`() {
        assertNull(LemmaLink.resolve(inflection) { null })
        assertNull(LemmaLink.resolve(inflection) { " " })
    }

    @Test
    fun `online lookup saves the base form with its meaning`() {
        val fetcher = { c: String ->
            when (c) {
                "vernagelt" -> FetchOutcome.Success("inflection of vernageln: third-person singular present")
                "vernageln" -> FetchOutcome.Success("to nail shut")
                else -> FetchOutcome.NotFound
            }
        }
        assertEquals(LookupResult.Found("to nail shut", "vernageln", "vernageln"), WiktionaryApi.lookup("vernagelt", fetcher))
    }

    @Test
    fun `online lookup falls back to the typed form when base is missing or a loop`() {
        val inflectionGloss = "inflection of vernageln: third-person singular present"
        val missing = { c: String ->
            if (c == "vernagelt") FetchOutcome.Success(inflectionGloss) else FetchOutcome.NotFound
        }
        assertEquals(LookupResult.Found(inflectionGloss, "vernagelt"), WiktionaryApi.lookup("vernagelt", missing))
        val loop = { c: String -> FetchOutcome.Success(inflectionGloss) }
        assertEquals(LookupResult.Found(inflectionGloss, "vernagelt"), WiktionaryApi.lookup("vernagelt", loop))
    }

    @Test
    fun `two base forms in one gloss are not followed`() {
        val gloss = "inflection of gehen: first-person singular present; inflection of Gehe: plural"
        assertEquals(listOf("gehen", "Gehe"), LemmaLink.baseWords(gloss))
        assertNull(LemmaLink.baseWord(gloss))
        assertNull(LemmaLink.resolve(gloss) { "to go" })
    }

    @Test
    fun `same base form repeated counts once`() {
        assertEquals("gehen", LemmaLink.baseWord("inflection of gehen: first-person; inflection of gehen: third-person"))
    }

    @Test
    fun `participle that is also an adjective resolves to the adjective entry`() {
        val resolved = LemmaLink.resolve("inflection of gelungen: strong nominative feminine singular") { "successful" }
        assertEquals(LemmaLink.Resolution("gelungen", "successful"), resolved)
        // A base that is only the participle of another verb is a second link: stop.
        assertNull(LemmaLink.resolve("inflection of gelungen: strong nominative") { "past participle of gelingen" })
    }

    @Test
    fun `separable prefix base form is kept whole`() {
        assertEquals("aufgeben", LemmaLink.baseWord("aufgegeben — past participle of aufgeben"))
        assertEquals(
            LemmaLink.Resolution("aufgeben", "to give up"),
            LemmaLink.resolve("aufgegeben — past participle of aufgeben") { if (it == "aufgeben") "to give up" else null }
        )
    }

    @Test
    fun `unparseable gloss returns null`() {
        assertNull(LemmaLink.baseWord("inflection of"))
        assertNull(LemmaLink.baseWord("of of of"))
        assertNull(LemmaLink.baseWord("form of :"))
        assertNull(LemmaLink.resolve("") { "x" })
    }

    @Test
    fun `base form the lookup cannot find keeps the typed form`() {
        assertNull(LemmaLink.resolve("past participle of aufgeben") { null })
    }
}
