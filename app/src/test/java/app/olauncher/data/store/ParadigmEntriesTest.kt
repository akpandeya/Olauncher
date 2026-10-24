package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParadigmEntriesTest {

    @Test
    fun `different lemmas sharing a form are not mixed`() {
        // "fand" style collision: two lemmas, one shared spelling.
        val entries = listOf(
            VerbEntryRef(10, "finden"),
            VerbEntryRef(11, "fänden"),
            VerbEntryRef(12, "finden"),
        )
        assertEquals(
            listOf(VerbEntryRef(10, "finden"), VerbEntryRef(12, "finden")),
            ParadigmEntries.sameLemmaAsFirst(entries),
        )
    }

    @Test
    fun `same-lemma homographs all stay`() {
        val entries = listOf(VerbEntryRef(1, "übersetzen"), VerbEntryRef(2, "übersetzen"))
        assertEquals(entries, ParadigmEntries.sameLemmaAsFirst(entries))
    }

    @Test
    fun `empty input stays empty`() {
        assertTrue(ParadigmEntries.sameLemmaAsFirst(emptyList()).isEmpty())
    }
}
