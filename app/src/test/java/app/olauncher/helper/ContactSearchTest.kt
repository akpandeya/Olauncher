package app.olauncher.helper

import app.olauncher.data.AppModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactSearchTest {

    private class FakeSource(private val list: List<ContactEntry>) : ContactSource {
        override fun contacts() = list
        override fun primaryNumber(lookupUri: String): String? = "555-$lookupUri"
    }

    @Test
    fun shortQueryGivesNothing() {
        assertTrue(ContactSearch.rank(listOf("Anna"), "a").isEmpty())
        assertTrue(ContactSearch.rank(listOf("Anna"), " a ").isEmpty())
    }

    @Test
    fun prefixMatchesRankedAndCapped() {
        val names = listOf("Hannah", "Anna", "Anders", "Andy", "Anton", "Zed")
        val ranked = ContactSearch.rank(names, "an", 3)
        assertEquals(3, ranked.size)
        assertTrue("Zed" !in ranked)
        assertTrue("Hannah" !in ranked.take(2))
    }

    @Test
    fun lowScoresAreDropped() {
        assertTrue(ContactSearch.rank(listOf("Zed"), "an").isEmpty())
    }

    @Test
    fun blankNamesIgnored() {
        assertTrue(ContactSearch.rank(listOf("", "  "), "an").isEmpty())
    }

    @Test
    fun searchUsesSourceAndBuildsResult() {
        val source = FakeSource(
            listOf(ContactEntry("Anna", "u1"), ContactEntry("Bob", "u2"))
        )
        val found = ContactSearch.search(source, "an")
        assertEquals(listOf(ContactEntry("Anna", "u1")), found)
        val row = ContactSearch.toResult(found[0])
        assertEquals("👤 Anna", row.text)
        assertEquals(AppModel.Result.Action.Contact("u1"), row.action)
        assertEquals("555-u1", source.primaryNumber("u1"))
    }
}
