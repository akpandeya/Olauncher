package app.olauncher.data.store

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PracticeItemsStoreTest {

    private lateinit var tempDir: File
    private lateinit var store: PracticeItemsStore

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("practice_items_test").toFile()
        store = PracticeItemsStore(tempDir)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun item(wordId: Long = 1, type: DrillType = DrillType.CLOZE) = PracticeItem(
        wordId = wordId,
        type = type,
        prompt = "Die Arbeit ___ auf seiner Unterschrift.",
        answer = "insistiert",
        choices = listOf("insistiert", "insistieren", "insistiertest"),
        hint = "Präsens, 3. Person Singular",
        source = PracticeItem.Source.WORD,
        model = "glm-5.3-flash",
    )

    @Test
    fun `empty store returns empty list`() {
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun `append assigns ids and createdAt and persists across instances`() {
        val stored = store.append(listOf(item(), item(wordId = 2, type = DrillType.PLURALS)))

        assertEquals(1L, stored[0].id)
        assertEquals(2L, stored[1].id)
        assertEquals(stored[0].createdAt, stored[1].createdAt)
        assertTrue(stored[0].createdAt > 0)

        val reloaded = PracticeItemsStore(tempDir).all()
        assertEquals(stored, reloaded)
        // Content is frozen at creation: the stored copy matches what was handed in.
        assertEquals("insistiert", reloaded[0].answer)
        assertEquals(PracticeItem.Source.WORD, reloaded[0].source)
        assertEquals("glm-5.3-flash", reloaded[0].model)
    }

    @Test
    fun `append keeps existing items and never rewrites their content`() {
        val first = store.append(listOf(item()))[0]
        val second = store.append(listOf(item(wordId = 5, type = DrillType.GENITIVE)))[0]

        val all = store.all()
        assertEquals(listOf(first, second), all)
        assertEquals(first, all[0])
    }

    @Test
    fun `decode drops corrupt and unknown-type entries`() {
        val raw = """
            [
                {"id": 1, "wordId": 5, "type": "cloze", "prompt": "Er ___ das Wort.", "answer": "benutzt",
                 "choices": ["benutzt", "benutzen", "benutzte"], "hint": "Präsens", "source": "word",
                 "model": "glm-5.3-flash", "createdAt": 100},
                {"id": 2, "wordId": 6, "type": "bogus", "prompt": "x", "answer": "y", "choices": ["y", "z"], "createdAt": 100},
                {"nonsense": true},
                {"id": 3, "wordId": 7, "type": "plurals", "prompt": "Zwei ___", "answer": "Wörter",
                 "choices": ["Wörter", "Wörte", "Worts"], "createdAt": 200}
            ]
        """.trimIndent()
        val items = PracticeItemsCodec.decode(raw)
        assertEquals(2, items.size)
        assertEquals("benutzt", items[0].answer)
        assertEquals("Wörter", items[1].answer)
    }

    @Test
    fun `old json without hint source or model still decodes`() {
        val oldJson = """
            [
                {"id": 1, "wordId": 10, "type": "cloze", "prompt": "Er ___ das Wort.",
                 "answer": "benutzt", "choices": ["benutzt", "benutzen"], "createdAt": 100}
            ]
        """.trimIndent()
        val decoded = PracticeItemsCodec.decode(oldJson)
        assertEquals(
            listOf(
                PracticeItem(
                    id = 1, wordId = 10, type = DrillType.CLOZE,
                    prompt = "Er ___ das Wort.", answer = "benutzt",
                    choices = listOf("benutzt", "benutzen"),
                    hint = null, source = null, model = null, createdAt = 100,
                ),
            ),
            decoded,
        )
    }

    @Test
    fun `decode of garbage returns empty and the store keeps appending`() {
        tempDir.resolve("practice_items.json").writeText("not json at all")
        assertTrue(store.all().isEmpty())
        val stored = store.append(listOf(item()))
        assertEquals(listOf(stored[0]), store.all())
    }

    @Test
    fun `concurrent appends from separate instances all survive`() {
        // Each worker builds its own store; the shared lock keeps the file's
        // read-modify-write from losing a run's paid items (review R1 #3).
        val instances = (1..4).map { PracticeItemsStore(tempDir) }
        val threads = instances.mapIndexed { index, instance ->
            Thread {
                instance.append(listOf(item(wordId = index + 1L)))
            }.also { it.start() }
        }
        threads.forEach { it.join() }

        val all = store.all()
        assertEquals(4, all.size)
        assertEquals((1L..4L).toSet(), all.map { it.wordId }.toSet())
        assertEquals((1L..4L).toSet(), all.map { it.id }.toSet())
    }

    @Test
    fun `append skips a card that is already stored and keeps existing ids`() {
        val first = store.append(listOf(item()))[0]
        // Same word, type, prompt and answer: a fallback copy with other
        // choices, no hint and no model is still the same card.
        val copy = item().copy(choices = listOf("insistieren", "insistiert"), hint = null, model = null)

        val stored = store.append(listOf(copy, item(wordId = 2)))

        assertEquals(listOf(2L), stored.map { it.wordId })
        assertEquals(2L, stored[0].id)
        assertEquals(listOf(first, stored[0]), store.all())
    }

    @Test
    fun `append stores one copy of a card repeated within the batch`() {
        val slot0 = item()
        val slot1 = item().copy(choices = slot0.choices.drop(1) + slot0.choices.take(1))

        val stored = store.append(listOf(slot0, slot1, slot0))

        assertEquals(1, stored.size)
        assertEquals(slot0.choices, stored[0].choices)
        assertEquals(1, store.all().size)
    }

    @Test
    fun `append of only known cards writes nothing`() {
        val first = store.append(listOf(item()))
        val before = File(tempDir, "practice_items.json").readText()

        assertTrue(store.append(listOf(item())).isEmpty())
        assertEquals(before, File(tempDir, "practice_items.json").readText())
        assertEquals(first, store.all())
    }
}
