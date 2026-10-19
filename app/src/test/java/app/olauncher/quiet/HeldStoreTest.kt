package app.olauncher.quiet

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class HeldStoreTest {

    private lateinit var tempDir: File
    private lateinit var store: HeldStore

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("held_store_test").toFile()
        store = HeldStore(tempDir)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `add and all returns stored items`() {
        val item1 = HeldItem(
            key = "key1",
            pkg = "com.instagram.android",
            label = "Instagram",
            title = "New follower",
            text = "user1 followed you",
            postTime = 1000L
        )
        val item2 = HeldItem(
            key = "key2",
            pkg = "com.google.android.youtube",
            label = "YouTube",
            title = "New video",
            text = "Channel posted a video",
            postTime = 2000L
        )

        store.add(item1)
        store.add(item2)

        val all = store.all()
        assertEquals(2, all.size)
        assertEquals(item1, all[0])
        assertEquals(item2, all[1])
    }

    @Test
    fun `add with same key replaces earlier item without duplicating`() {
        val item1 = HeldItem(
            key = "key1",
            pkg = "com.instagram.android",
            label = "Instagram",
            title = "1 like",
            text = "user1 liked your photo",
            postTime = 1000L
        )
        val item1Updated = HeldItem(
            key = "key1",
            pkg = "com.instagram.android",
            label = "Instagram",
            title = "2 likes",
            text = "user1 and user2 liked your photo",
            postTime = 1500L
        )

        store.add(item1)
        store.add(item1Updated)

        val all = store.all()
        assertEquals("Should contain only 1 item after replacement", 1, all.size)
        assertEquals(item1Updated, all[0])
    }

    @Test
    fun `clear pkg removes only items for that package`() {
        val itemIg = HeldItem(
            key = "ig1",
            pkg = "com.instagram.android",
            label = "Instagram",
            title = "Like",
            text = "Photo liked",
            postTime = 1000L
        )
        val itemYt = HeldItem(
            key = "yt1",
            pkg = "com.google.android.youtube",
            label = "YouTube",
            title = "Video",
            text = "New video uploaded",
            postTime = 2000L
        )
        val itemWa = HeldItem(
            key = "wa1",
            pkg = "com.whatsapp",
            label = "WhatsApp",
            title = "Chat",
            text = "New message",
            postTime = 3000L
        )

        store.add(itemIg)
        store.add(itemYt)
        store.add(itemWa)

        store.clear("com.instagram.android")

        val all = store.all()
        assertEquals(2, all.size)
        assertTrue(all.none { it.pkg == "com.instagram.android" })
        assertEquals("yt1", all[0].key)
        assertEquals("wa1", all[1].key)
    }

    @Test
    fun `clearAll removes all items`() {
        store.add(HeldItem("k1", "pkg1", "App1", "Title1", "Text1", 1000L))
        store.add(HeldItem("k2", "pkg2", "App2", "Title2", "Text2", 2000L))

        assertEquals(2, store.all().size)

        store.clearAll()

        assertTrue(store.all().isEmpty())
    }

    @Test
    fun `pruneOlderThan removes items older than cutoff`() {
        val oldItem = HeldItem("k1", "pkg1", "App1", "Old", "Old notification", 1000L)
        val cutoffItem = HeldItem("k2", "pkg2", "App2", "Cutoff", "Cutoff notification", 5000L)
        val newItem = HeldItem("k3", "pkg3", "App3", "New", "Recent notification", 9000L)

        store.add(oldItem)
        store.add(cutoffItem)
        store.add(newItem)

        store.pruneOlderThan(5000L)

        val all = store.all()
        assertEquals(2, all.size)
        assertEquals("k2", all[0].key)
        assertEquals("k3", all[1].key)
    }

    @Test
    fun `pruneOlderThan with 48h default removes older items`() {
        val now = System.currentTimeMillis()
        val olderThan48h = now - (49L * 60 * 60 * 1000)
        val recent = now - (10L * 60 * 1000)

        store.add(HeldItem("k1", "pkg1", "App1", "Stale", "Stale", olderThan48h))
        store.add(HeldItem("k2", "pkg2", "App2", "Fresh", "Fresh", recent))

        store.pruneOlderThan()

        val all = store.all()
        assertEquals(1, all.size)
        assertEquals("k2", all[0].key)
    }

    @Test
    fun `text longer than 120 chars is clamped to 120 chars`() {
        val longText = "a".repeat(200)
        val item = HeldItem(
            key = "k1",
            pkg = "pkg1",
            label = "Label",
            title = "Title",
            text = longText,
            postTime = 1000L
        )

        store.add(item)

        val retrieved = store.all().first()
        assertEquals(120, retrieved.text.length)
        assertEquals("a".repeat(120), retrieved.text)
    }

    @Test
    fun `atomic write persists across instances and writes valid json`() {
        val item = HeldItem("k1", "pkg1", "Label", "Title", "Text", 1234L)
        store.add(item)

        val file = File(tempDir, HeldStore.FILE_NAME)
        assertTrue("held.json should exist", file.exists())

        // Re-read with a second instance pointing to the same filesDir
        val store2 = HeldStore(tempDir)
        val all = store2.all()
        assertEquals(1, all.size)
        assertEquals(item, all[0])
    }

    @Test
    fun `corrupt json file returns empty list gracefully`() {
        val file = File(tempDir, HeldStore.FILE_NAME)
        file.writeText("corrupt json data here", Charsets.UTF_8)

        val all = store.all()
        assertTrue(all.isEmpty())
    }

    @Test
    fun `corrupt file is moved aside and store starts empty`() {
        val file = File(tempDir, HeldStore.FILE_NAME)
        file.writeText("{not json", Charsets.UTF_8)
        File(tempDir, "held.json.corrupt").writeText("older")

        assertTrue(store.all().isEmpty())

        val corrupt = File(tempDir, "held.json.corrupt")
        assertEquals("{not json", corrupt.readText(Charsets.UTF_8))
        assertTrue(!file.exists())
    }

    @Test
    fun `valid file is read and not quarantined`() {
        val item = HeldItem("k", "p", "L", "T", "X", 5L)
        store.add(item)
        assertEquals(listOf(item), store.all())
        assertTrue(!File(tempDir, "held.json.corrupt").exists())
    }

    @Test
    fun `missing file gives empty store without corrupt file`() {
        assertTrue(store.all().isEmpty())
        assertTrue(!File(tempDir, "held.json.corrupt").exists())
    }
}
