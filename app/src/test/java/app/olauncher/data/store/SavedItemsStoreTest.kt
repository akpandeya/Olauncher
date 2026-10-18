package app.olauncher.data.store

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SavedItemsStoreTest {

    private lateinit var tempDir: File
    private lateinit var store: SavedItemsStore

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("saved_items_test").toFile()
        store = SavedItemsStore(tempDir)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `empty store returns empty lists`() {
        assertTrue(store.active().isEmpty())
        assertTrue(store.archived().isEmpty())
    }

    @Test
    fun `add creates new active item at the top`() {
        val item1 = store.add("https://first.com", "First")
        assertEquals("https://first.com", item1.url)
        assertEquals("First", item1.title)
        assertNull(item1.archivedAt)

        val item2 = store.add("https://second.com", "Second")
        val active = store.active()

        assertEquals(2, active.size)
        assertEquals(item2.id, active[0].id)
        assertEquals(item1.id, active[1].id)
    }

    @Test
    fun `adding a URL that is already active does not duplicate it and moves to top`() {
        val itemA = store.add("https://a.com", "A")
        val itemB = store.add("https://b.com", "B")
        val itemC = store.add("https://c.com", "C")

        // Initial order: C, B, A
        val initialActive = store.active()
        assertEquals(3, initialActive.size)
        assertEquals(itemC.id, initialActive[0].id)
        assertEquals(itemB.id, initialActive[1].id)
        assertEquals(itemA.id, initialActive[2].id)

        // Re-add item A with updated title
        val reAddedA = store.add("https://a.com", "A Updated")
        val updatedActive = store.active()

        // Size must still be 3 (no duplication)
        assertEquals(3, updatedActive.size)
        // Item A must now be at index 0 (top)
        assertEquals(itemA.id, updatedActive[0].id)
        assertEquals("A Updated", updatedActive[0].title)
        assertEquals(itemC.id, updatedActive[1].id)
        assertEquals(itemB.id, updatedActive[2].id)
    }

    @Test
    fun `archive moves item to archived list`() {
        val item1 = store.add("https://test.com", "Test")
        store.archive(item1.id)

        val active = store.active()
        val archived = store.archived()

        assertTrue(active.isEmpty())
        assertEquals(1, archived.size)
        assertEquals(item1.id, archived[0].id)
        assertNotNull(archived[0].archivedAt)
    }

    @Test
    fun `unarchive restores item to active list at top`() {
        val item1 = store.add("https://first.com", "First")
        val item2 = store.add("https://second.com", "Second")

        store.archive(item1.id)
        assertEquals(1, store.active().size)
        assertEquals(1, store.archived().size)

        store.unarchive(item1.id)
        val active = store.active()
        val archived = store.archived()

        assertEquals(2, active.size)
        assertTrue(archived.isEmpty())
        assertEquals(item1.id, active[0].id)
        assertNull(active[0].archivedAt)
    }

    @Test
    fun `unarchive does not duplicate a url that was re-added while archived`() {
        val original = store.add("https://dup.com", "Old")
        store.archive(original.id)
        store.add("https://dup.com", "New")

        store.unarchive(original.id)

        val active = store.active()
        assertEquals(1, active.size)
        assertEquals(original.id, active[0].id)
        assertTrue(store.archived().isEmpty())
    }

    @Test
    fun `delete removes item permanently`() {
        val item = store.add("https://delete.me", "Delete Me")
        store.delete(item.id)

        assertTrue(store.active().isEmpty())
        assertTrue(store.archived().isEmpty())
    }

    @Test
    fun `persists across store instances atomically`() {
        store.add("https://persistent.com", "Persistent")

        val newStoreInstance = SavedItemsStore(tempDir)
        val items = newStoreInstance.active()

        assertEquals(1, items.size)
        assertEquals("https://persistent.com", items[0].url)
        assertEquals("Persistent", items[0].title)
    }
}
