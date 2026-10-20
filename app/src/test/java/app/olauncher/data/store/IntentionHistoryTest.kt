package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class IntentionHistoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `put adds an entry`() {
        val result = IntentionHistory.put(emptyList(), 100L, "Write")
        assertEquals(listOf(IntentionEntry(100L, "Write")), result)
    }

    @Test
    fun `put on the same day replaces the entry`() {
        val first = IntentionHistory.put(emptyList(), 100L, "Write")
        val result = IntentionHistory.put(first, 100L, "Run")
        assertEquals(listOf(IntentionEntry(100L, "Run")), result)
    }

    @Test
    fun `blank text removes that day`() {
        val first = IntentionHistory.put(IntentionHistory.put(emptyList(), 100L, "A"), 101L, "B")
        val result = IntentionHistory.put(first, 100L, "   ")
        assertEquals(listOf(IntentionEntry(101L, "B")), result)
    }

    @Test
    fun `cap of 365 drops the oldest`() {
        var entries = emptyList<IntentionEntry>()
        for (day in 1L..370L) entries = IntentionHistory.put(entries, day, "d$day")
        assertEquals(365, entries.size)
        assertEquals(6L, entries.first().day)
        assertEquals(370L, entries.last().day)
    }

    @Test
    fun `decode skips corrupt entries`() {
        val raw = """[{"day":5,"text":"ok"},{"day":"x"},{"text":"no day"},7,{"day":6,"text":""},{"day":8,"text":"fine"}]"""
        assertEquals(
            listOf(IntentionEntry(5L, "ok"), IntentionEntry(8L, "fine")),
            IntentionHistory.decode(raw)
        )
    }

    @Test
    fun `codec round trip`() {
        val entries = listOf(IntentionEntry(1L, "one"), IntentionEntry(2L, "two \"q\""))
        assertEquals(entries, IntentionHistory.decode(IntentionHistory.encode(entries)))
    }

    @Test
    fun `decode of blank and invalid input is empty`() {
        assertTrue(IntentionHistory.decode(null).isEmpty())
        assertTrue(IntentionHistory.decode("  ").isEmpty())
        assertTrue(IntentionHistory.decode("not json").isEmpty())
    }

    @Test
    fun `store seeds from the existing intention on first load`() {
        val store = IntentionHistoryStore(tmp.root)
        assertEquals(listOf(IntentionEntry(50L, "Seed")), store.load("Seed", 50L))
        // File now exists; later seed values are ignored.
        assertEquals(listOf(IntentionEntry(50L, "Seed")), store.load("Other", 60L))
    }

    @Test
    fun `store does not seed from blank text`() {
        val store = IntentionHistoryStore(tmp.root)
        assertTrue(store.load("", 50L).isEmpty())
    }

    @Test
    fun `store persists puts across instances`() {
        IntentionHistoryStore(tmp.root).put(10L, "A")
        IntentionHistoryStore(tmp.root).put(10L, "B")
        assertEquals(listOf(IntentionEntry(10L, "B")), IntentionHistoryStore(tmp.root).load())
    }

    @Test
    fun `pastEntries excludes today and later days`() {
        val entries = listOf(IntentionEntry(9L, "A"), IntentionEntry(10L, "Today"), IntentionEntry(11L, "Future"))
        assertEquals(listOf(IntentionEntry(9L, "A")), IntentionHistory.pastEntries(entries, 10L))
    }

    @Test
    fun `pastEntries is newest first and skips days with no entry`() {
        val entries = listOf(IntentionEntry(2L, "A"), IntentionEntry(7L, "B"), IntentionEntry(5L, "C"))
        assertEquals(listOf(7L, 5L, 2L), IntentionHistory.pastEntries(entries, 10L).map { it.day })
    }

    @Test
    fun `pastEntries is limited to the 30 days before today`() {
        val entries = (1L..40L).map { IntentionEntry(it, "T$it") }
        val result = IntentionHistory.pastEntries(entries, 41L)
        assertEquals(30, result.size)
        assertEquals(40L, result.first().day)
        assertEquals(11L, result.last().day)
    }

    @Test
    fun `pastEntries is empty when there are no past entries`() {
        assertTrue(IntentionHistory.pastEntries(emptyList(), 10L).isEmpty())
        assertTrue(IntentionHistory.pastEntries(listOf(IntentionEntry(10L, "Today")), 10L).isEmpty())
    }
}
