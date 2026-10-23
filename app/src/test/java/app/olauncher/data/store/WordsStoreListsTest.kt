package app.olauncher.data.store

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class WordsStoreListsTest {

    private lateinit var tempDir: File
    private lateinit var store: WordsStore

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("words_lists_test").toFile()
        store = WordsStore(tempDir)
        store.epochDaySupplier = { 19500L }
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `addList trims names and rejects blank and duplicates`() {
        val list = store.addList("  Kitchen  ")
        assertNotNull(list)
        assertEquals("Kitchen", list!!.name)

        assertNull(store.addList(""))
        assertNull(store.addList("   "))
        assertNull(store.addList("kitchen"))
        assertNull(store.addList("KITCHEN"))
        assertEquals(1, store.lists().size)
    }

    @Test
    fun `lists come back sorted case-insensitively by name`() {
        store.addList("banana")
        store.addList("Apple")
        store.addList("cherry")
        assertEquals(listOf("Apple", "banana", "cherry"), store.lists().map { it.name })
    }

    @Test
    fun `addList generates unique ids on slug clash`() {
        val first = store.addList("Kitchen")
        val second = store.addList("kitchen!")
        assertNotNull(first)
        assertNotNull(second)
        assertEquals("kitchen", first!!.id)
        assertEquals("kitchen-2", second!!.id)
        assertEquals("kitchen!", second.name)
    }

    @Test
    fun `renameList keeps the id and rejects clashes`() {
        val kitchen = store.addList("Kitchen")!!
        val travel = store.addList("Travel")!!

        assertTrue(store.renameList(kitchen.id, "Cooking"))
        val renamed = store.lists().first { it.id == kitchen.id }
        assertEquals("kitchen", renamed.id)
        assertEquals("Cooking", renamed.name)

        // Clash with another list's name, ignoring case.
        assertFalse(store.renameList(travel.id, "cooking"))
        assertEquals("Travel", store.lists().first { it.id == travel.id }.name)

        // Renaming to the own name is fine (exceptId), unknown ids are rejected.
        assertTrue(store.renameList(travel.id, " travel "))
        assertEquals("travel", store.lists().first { it.id == travel.id }.name)
        assertFalse(store.renameList("no-such-list", "Whatever"))
    }

    @Test
    fun `deleteList scrubs members but leaves other word fields untouched`() {
        val kitchen = store.addList("Kitchen")!!
        val travel = store.addList("Travel")!!
        val word = store.add("Gabel", "Die Gabel liegt auf dem Tisch")
        store.update(word.copy(meaning = "fork", meaningSource = "wiktionary", box = 3))
        store.setLists(word.id, listOf(kitchen.id, travel.id))
        val before = store.get(word.id)!!

        store.deleteList(kitchen.id)

        assertEquals(listOf(travel.id), store.lists().map { it.id })
        val after = store.get(word.id)!!
        assertEquals(before.copy(listIds = listOf(travel.id)), after)
    }

    @Test
    fun `setLists replaces membership and drops unknown list ids`() {
        val kitchen = store.addList("Kitchen")!!
        val travel = store.addList("Travel")!!
        val word = store.add("Zug")

        store.setLists(word.id, listOf(kitchen.id))
        assertEquals(listOf(kitchen.id), store.get(word.id)!!.listIds)

        store.setLists(word.id, listOf(travel.id, "ghost-list"))
        assertEquals(listOf(travel.id), store.get(word.id)!!.listIds)

        // Unknown word: no crash, no change.
        store.setLists(999999L, listOf(kitchen.id))
        assertEquals(listOf(travel.id), store.get(word.id)!!.listIds)
    }

    @Test
    fun `assignAll and unassignAll are idempotent`() {
        val kitchen = store.addList("Kitchen")!!
        val w1 = store.add("Gabel")
        val w2 = store.add("Messer")
        val w3 = store.add("Zug")

        store.assignAll(listOf(w1.id, w2.id), kitchen.id)
        store.assignAll(listOf(w1.id, w2.id), kitchen.id)
        assertEquals(listOf(kitchen.id), store.get(w1.id)!!.listIds)
        assertEquals(listOf(kitchen.id), store.get(w2.id)!!.listIds)
        assertTrue(store.get(w3.id)!!.listIds.isEmpty())

        store.unassignAll(listOf(w1.id, w3.id), kitchen.id)
        store.unassignAll(listOf(w1.id, w3.id), kitchen.id)
        assertTrue(store.get(w1.id)!!.listIds.isEmpty())
        assertEquals(listOf(kitchen.id), store.get(w2.id)!!.listIds)
    }

    @Test
    fun `assignAll and unassignAll with unknown list id are no-ops`() {
        val kitchen = store.addList("Kitchen")!!
        val word = store.add("Gabel")
        store.assignAll(listOf(word.id), kitchen.id)

        store.assignAll(listOf(word.id), "ghost-list")
        store.unassignAll(listOf(word.id), "ghost-list")
        assertEquals(listOf(kitchen.id), store.get(word.id)!!.listIds)
    }

    @Test
    fun `wordsInList returns members even for a dangling list id`() {
        val kitchen = store.addList("Kitchen")!!
        val word = store.add("Gabel")
        store.assignAll(listOf(word.id), kitchen.id)
        assertEquals(listOf(word.id), store.wordsInList(kitchen.id).map { it.id })
        assertTrue(store.wordsInList("dangling-id").isEmpty())

        // A word carrying an id no list defines (e.g. from a backup merge) still counts.
        val stray = store.add("Löffel")
        store.update(stray.copy(listIds = listOf("dangling-id")))
        assertEquals(listOf(stray.id), store.wordsInList("dangling-id").map { it.id })
    }

    @Test
    fun `membership round-trips across store instances`() {
        val kitchen = store.addList("Kitchen")!!
        val word = store.add("Gabel")
        store.assignAll(listOf(word.id), kitchen.id)

        val newStoreInstance = WordsStore(tempDir)
        assertEquals(listOf(WordList("kitchen", "Kitchen")), newStoreInstance.lists())
        assertEquals(listOf(kitchen.id), newStoreInstance.get(word.id)!!.listIds)
        assertEquals(listOf(word.id), newStoreInstance.wordsInList(kitchen.id).map { it.id })
    }

    @Test
    fun `saving a legacy v1 file upgrades it to the envelope keeping every word`() {
        val v1 = """
            [
                {"id": 1, "text": "Haus", "dueDay": 19498, "addedAt": 1000},
                {"id": 2, "text": "Baum", "context": "Ein alter Baum", "dueDay": 19499, "addedAt": 2000}
            ]
        """.trimIndent()
        File(tempDir, "words.json").writeText(v1)

        store.add("Test")

        val raw = File(tempDir, "words.json").readText()
        assertEquals(WordsCodec.CURRENT_VERSION, JSONObject(raw).getInt("version"))
        val decoded = WordsCodec.decode(raw)
        assertEquals(3, decoded.words.size)
        assertEquals("Test", decoded.words[0].text)
        assertEquals("Haus", decoded.words[1].text)
        assertEquals("Baum", decoded.words[2].text)
        assertEquals("Ein alter Baum", decoded.words[2].context)
    }

    @Test
    fun `add de-dupe keeps the existing word and its listIds`() {
        val kitchen = store.addList("Kitchen")!!
        val word = store.add("Gabel", "Die Gabel liegt auf dem Tisch")
        store.assignAll(listOf(word.id), kitchen.id)

        // De-dupe hit with blank context: returns the stored word untouched.
        val same = store.add("gabel", "   ")
        assertEquals(word.id, same.id)
        assertEquals(listOf(kitchen.id), store.get(word.id)!!.listIds)

        // De-dupe hit with new context: context changes, membership stays.
        val updated = store.add("GABEL", "Eine saubere Gabel")
        assertEquals(word.id, updated.id)
        assertEquals("Eine saubere Gabel", updated.context)
        assertEquals(listOf(kitchen.id), store.get(word.id)!!.listIds)
        assertEquals(1, store.all().size)
    }
}
