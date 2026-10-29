package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AiChatStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val tempDir: File
        get() = tempFolder.root

    private lateinit var store: AiChatStore

    private val wordSubject = AiChatSubject(AiChatSubject.Kind.WORD, "die straße")
    private val ruleSubject = AiChatSubject(AiChatSubject.Kind.RULE, "prep-two-way")

    private fun thread(
        id: String,
        subject: AiChatSubject,
        updatedAt: Long,
        question: String = "Warum so?"
    ): AiChatThread {
        return AiChatThread(
            id = id,
            subject = subject,
            createdAt = updatedAt - 10,
            updatedAt = updatedAt,
            messages = listOf(
                AiChatMessage(AiChatMessage.Role.USER, question, updatedAt - 5),
                AiChatMessage(AiChatMessage.Role.ASSISTANT, "Weil $question", updatedAt - 4, usage = AiUsage(11, 22))
            )
        )
    }

    @Before
    fun setUp() {
        store = AiChatStore(tempDir)
    }

    @Test
    fun `absent file loads to zero threads`() {
        assertEquals(0, store.threads().size)
        assertNull(store.thread("nope"))
        assertTrue(store.load() is AiChatLoad.Ok)
    }

    @Test
    fun `upsert stores a thread and reads it back with every field`() {
        val chat = thread("t1", wordSubject, 1_700_000_000_000)
        assertEquals(AiChatWrite.Ok, store.upsert(chat))

        val loaded = store.thread("t1")
        assertNotNull(loaded)
        assertEquals(chat, loaded)

        val file = File(tempDir, "ai_chats.json")
        assertTrue(file.exists())
        val envelope = org.json.JSONObject(file.readText())
        assertEquals(1, envelope.getInt("version"))
    }

    @Test
    fun `upsert replaces by id instead of duplicating`() {
        store.upsert(thread("t1", wordSubject, 100))
        val updated = thread("t1", wordSubject, 200, question = "Anders?")
        assertEquals(AiChatWrite.Ok, store.upsert(updated))

        val all = store.threads()
        assertEquals(1, all.size)
        assertEquals(200, all.single().updatedAt)
    }

    @Test
    fun `threadsFor returns only the threads of that subject`() {
        val a1 = thread("word-1", wordSubject, 100)
        val a2 = thread("word-2", wordSubject, 200)
        val r1 = thread("rule-1", ruleSubject, 300)
        store.upsert(a1)
        store.upsert(a2)
        store.upsert(r1)

        assertEquals(listOf(a1, a2), store.threadsFor(wordSubject))
        assertEquals(listOf(r1), store.threadsFor(ruleSubject))
    }

    @Test
    fun `delete removes only the named thread and unknown ids are a no-op`() {
        val keep = thread("keep", wordSubject, 100)
        val gone = thread("gone", wordSubject, 200)
        store.upsert(keep)
        store.upsert(gone)

        assertEquals(AiChatWrite.Ok, store.delete("gone"))
        assertEquals(listOf(keep), store.threads())
        assertNull(store.thread("gone"))

        val fileBefore = File(tempDir, "ai_chats.json").readText()
        assertEquals(AiChatWrite.Ok, store.delete("never-existed"))
        assertEquals(fileBefore, File(tempDir, "ai_chats.json").readText())
    }

    @Test
    fun `threads survive a new store instance over the same directory`() {
        val chat = thread("t1", ruleSubject, 500)
        store.upsert(chat)

        val reopened = AiChatStore(tempDir)
        assertEquals(listOf(chat), reopened.threadsFor(ruleSubject))
    }

    @Test
    fun `corrupt store is backed up and the store starts empty`() {
        val garbage = "::{ this is not valid json ]"
        File(tempDir, "ai_chats.json").writeText(garbage)

        val load = store.load()
        assertTrue(load is AiChatLoad.Recovered)
        assertTrue((load as AiChatLoad.Recovered).reason.isNotBlank())

        assertEquals(0, store.threads().size)

        val backup = File(tempDir, "ai_chats.json.bak")
        assertTrue(backup.exists())
        assertEquals(garbage, backup.readText())
        assertFalse(File(tempDir, "ai_chats.json").exists())
    }

    @Test
    fun `a second corrupt store replaces the older backup`() {
        File(tempDir, "ai_chats.json").writeText("first garbage")
        assertTrue(store.load() is AiChatLoad.Recovered)

        File(tempDir, "ai_chats.json").writeText("second garbage")
        assertTrue(store.load() is AiChatLoad.Recovered)

        assertEquals("second garbage", File(tempDir, "ai_chats.json.bak").readText())
    }

    @Test
    fun `empty file is an empty store, not corruption`() {
        File(tempDir, "ai_chats.json").writeText("")
        val load = store.load()
        assertTrue(load is AiChatLoad.Ok)
        assertEquals(0, (load as AiChatLoad.Ok).threads.size)
        assertFalse(File(tempDir, "ai_chats.json.bak").exists())
    }

    @Test
    fun `after recovery the store writes fresh threads over the empty state`() {
        File(tempDir, "ai_chats.json").writeText("garbage")
        assertTrue(store.load() is AiChatLoad.Recovered)

        val chat = thread("fresh", wordSubject, 42)
        assertEquals(AiChatWrite.Ok, store.upsert(chat))
        assertEquals(listOf(chat), store.threads())
    }

    @Test
    fun `recovery triggered by upsert is still reported by the next load`() {
        File(tempDir, "ai_chats.json").writeText("garbage")

        // The first call after the bad file is a mutation, not load(): it moves
        // the corrupt file aside and writes a fresh one; the recovery must not
        // be lost.
        assertEquals(AiChatWrite.Ok, store.upsert(thread("fresh", wordSubject, 42)))
        assertEquals("garbage", File(tempDir, "ai_chats.json.bak").readText())

        val load = store.load()
        assertTrue("expected Recovered, was $load", load is AiChatLoad.Recovered)

        // Reported once: the next load sees the freshly written store.
        assertTrue(store.load() is AiChatLoad.Ok)
    }

    @Test
    fun `unreadable store refuses writes and keeps the file untouched`() {
        // ai_chats.json exists as a non-empty directory: it cannot be read as
        // text, and no rename or copy can replace it.
        val blocker = File(tempDir, "ai_chats.json")
        assertTrue(blocker.mkdirs())
        File(blocker, "occupied.txt").writeText("in the way")

        val result = store.upsert(thread("t1", wordSubject, 1))
        assertTrue("expected AiChatWrite.Failed, was $result", result is AiChatWrite.Failed)
        assertTrue((result as AiChatWrite.Failed).cause.message?.contains("ai_chats.json") == true)

        // The file (directory) is untouched, and no .tmp or .bak was left behind.
        assertTrue(blocker.isDirectory)
        assertEquals("in the way", File(blocker, "occupied.txt").readText())
        assertFalse(File(tempDir, "ai_chats.json.tmp").exists())
        assertFalse(File(tempDir, "ai_chats.json.bak").exists())

        // Reads report the unreadable store instead of throwing, and return
        // an empty list where a list is required.
        assertTrue(store.load() is AiChatLoad.Unreadable)
        assertEquals(0, store.threads().size)
    }

    @Test
    fun `corrupt store that cannot be moved to bak refuses writes`() {
        File(tempDir, "ai_chats.json").writeText("garbage")
        // A non-empty directory blocks the .bak rename, so the corrupt file
        // would have to be overwritten in place.
        val backupBlocker = File(tempDir, "ai_chats.json.bak")
        assertTrue(backupBlocker.mkdirs())
        File(backupBlocker, "keep.txt").writeText("blocker")

        val result = store.upsert(thread("t1", wordSubject, 1))
        assertTrue("expected AiChatWrite.Failed, was $result", result is AiChatWrite.Failed)

        // The corrupt file is still there, not silently overwritten, and the
        // failed write left no temp file behind.
        assertEquals("garbage", File(tempDir, "ai_chats.json").readText())
        assertEquals("blocker", File(backupBlocker, "keep.txt").readText())
        assertFalse(File(tempDir, "ai_chats.json.tmp").exists())

        val load = store.load()
        assertTrue(load is AiChatLoad.Recovered)
        assertTrue((load as AiChatLoad.Recovered).reason.contains("rename failed"))
    }

    @Test
    fun `store only touches its own file`() {
        store.upsert(thread("t1", wordSubject, 1))
        val names = tempDir.list().orEmpty().toList()
        assertEquals(setOf("ai_chats.json"), names.toSet())
    }
}
