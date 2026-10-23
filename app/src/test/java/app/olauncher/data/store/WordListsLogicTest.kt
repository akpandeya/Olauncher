package app.olauncher.data.store

import app.olauncher.data.store.WordListsLogic.NameError
import app.olauncher.data.store.WordListsLogic.SourceBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WordListsLogicTest {

    private fun word(id: Long, listIds: List<String> = emptyList(), meaningSource: String = "") =
        Word(id = id, text = "w$id", meaningSource = meaningSource, dueDay = 100L, addedAt = id, listIds = listIds)

    @Test
    fun `normalizeName trims and collapses whitespace`() {
        assertEquals("Kitchen", WordListsLogic.normalizeName("  Kitchen  "))
        assertEquals("my list", WordListsLogic.normalizeName("my \t\n  list"))
        assertEquals("", WordListsLogic.normalizeName("   "))
    }

    @Test
    fun `slugify keeps unicode letters and folds punctuation into dashes`() {
        assertEquals("küche", WordListsLogic.slugify("Küche"))
        assertEquals("bahnhofstraße-12", WordListsLogic.slugify("Bahnhofstraße 12"))
        assertEquals("hello-world", WordListsLogic.slugify("Hello World!"))
        assertEquals("weird-name", WordListsLogic.slugify("  --weird__  name-- "))
        assertEquals("list", WordListsLogic.slugify(""))
        assertEquals("list", WordListsLogic.slugify(" !!! "))
    }

    @Test
    fun `uniqueListId suffixes until free`() {
        assertEquals("kitchen", WordListsLogic.uniqueListId("Kitchen", emptySet()))
        assertEquals("kitchen-2", WordListsLogic.uniqueListId("Kitchen", setOf("kitchen")))
        assertEquals("kitchen-3", WordListsLogic.uniqueListId("Kitchen", setOf("kitchen", "kitchen-2")))
        // A taken base does not block a different slug.
        assertEquals("travel", WordListsLogic.uniqueListId("Travel", setOf("kitchen")))
    }

    @Test
    fun `uniqueListId caps long slugs and suffixes after truncation`() {
        val longName = "a".repeat(400)
        val id = WordListsLogic.uniqueListId(longName, emptySet())
        assertEquals(WordListsLogic.MAX_SLUG_LENGTH, id.length)

        // Truncated bases still suffix to unique, bounded ids.
        val second = WordListsLogic.uniqueListId(longName, setOf(id))
        val third = WordListsLogic.uniqueListId(longName, setOf(id, second))
        assertEquals("$id-2", second)
        assertEquals("$id-3", third)

        // Truncation never leaves a dangling dash.
        assertEquals("a".repeat(47), WordListsLogic.uniqueListId("${"a".repeat(47)} b", emptySet()))
    }

    @Test
    fun `validateName matches duplicates ignoring case and punctuation`() {
        val lists = listOf(WordList("kitchen", "Kitchen"), WordList("kütchen", "kütchen"))

        assertEquals(NameError.DUPLICATE, WordListsLogic.validateName("kitchen!", lists))
        assertEquals(NameError.DUPLICATE, WordListsLogic.validateName("Kü-tchen!", lists))
        assertNull(WordListsLogic.validateName("Küchen", lists)) // genuinely different
        // exceptId still excludes the list being renamed.
        assertNull(WordListsLogic.validateName("Kü-tchen!", lists, exceptId = "kütchen"))
    }

    @Test
    fun `validateName rejects blank and duplicate but not itself`() {
        val lists = listOf(WordList("kitchen", "Kitchen"), WordList("travel", "Travel"))

        assertEquals(NameError.BLANK, WordListsLogic.validateName("", lists))
        assertEquals(NameError.BLANK, WordListsLogic.validateName("   ", lists))
        assertEquals(NameError.DUPLICATE, WordListsLogic.validateName("kitchen", lists))
        assertEquals(NameError.DUPLICATE, WordListsLogic.validateName("KITCHEN", lists))
        assertNull(WordListsLogic.validateName("Cooking", lists))
        // Renaming "Kitchen": its own name no longer counts as a duplicate.
        assertNull(WordListsLogic.validateName("kitchen", lists, exceptId = "kitchen"))
        assertEquals(NameError.DUPLICATE, WordListsLogic.validateName("travel", lists, exceptId = "kitchen"))
    }

    @Test
    fun `withAssignment touches only the given ids and is idempotent`() {
        val words = listOf(word(1), word(2, listOf("kitchen")), word(3))

        val assigned = WordListsLogic.withAssignment(words, setOf(1L, 2L), "kitchen", assign = true)
        assertEquals(listOf("kitchen"), assigned[0].listIds)
        assertEquals(listOf("kitchen"), assigned[1].listIds) // not duplicated
        assertTrue(assigned[2].listIds.isEmpty()) // untouched
        assertSame(words[1], assigned[1]) // already in target state: same instance

        val unassigned = WordListsLogic.withAssignment(assigned, setOf(2L, 3L), "kitchen", assign = false)
        assertEquals(listOf("kitchen"), unassigned[0].listIds) // not in the id set: untouched
        assertTrue(unassigned[1].listIds.isEmpty())
        assertSame(assigned[2], unassigned[2]) // nothing to remove: same instance
    }

    @Test
    fun `pruneListId drops the id from every word`() {
        val words = listOf(
            word(1, listOf("kitchen", "travel")),
            word(2, listOf("travel")),
            word(3)
        )
        val pruned = WordListsLogic.pruneListId(words, "travel")
        assertEquals(listOf("kitchen"), pruned[0].listIds)
        assertTrue(pruned[1].listIds.isEmpty())
        assertTrue(pruned[2].listIds.isEmpty())
    }

    @Test
    fun `groupBySource buckets in fixed order including empty buckets`() {
        val words = listOf(
            word(1, meaningSource = "user"),
            word(2, meaningSource = "wiktionary"),
            word(3, meaningSource = ""),
            word(4, meaningSource = "something-future")
        )
        val grouped = WordListsLogic.groupBySource(words)
        assertEquals(
            listOf(SourceBucket.WIKTIONARY, SourceBucket.USER, SourceBucket.NONE),
            grouped.map { it.first }
        )
        assertEquals(listOf(2L), grouped[0].second.map { it.id })
        assertEquals(listOf(1L), grouped[1].second.map { it.id })
        assertEquals(listOf(3L, 4L), grouped[2].second.map { it.id })

        val allEmpty = WordListsLogic.groupBySource(emptyList())
        assertEquals(3, allEmpty.size)
        assertTrue(allEmpty.all { it.second.isEmpty() })
    }
}
