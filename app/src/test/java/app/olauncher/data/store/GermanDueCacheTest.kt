package app.olauncher.data.store

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The locally cached server due set (epic #835, task 04): day filtering,
 * locally rated exclusions, the per-user read rule and the clear — all on a
 * fresh temporary folder per test, no network, no Context.
 */
class GermanDueCacheTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dir: File
    private lateinit var cache: GermanDueCache

    @Before
    fun setUp() {
        dir = tempFolder.newFolder()
        cache = GermanDueCache(dir)
    }

    private fun dueWord(
        id: String = "w-9f3",
        text: String = "Fernweh",
        dueDay: Long = 20401L,
        box: Int = 3,
    ): GermanDueWord = GermanDueWord(
        id = id,
        text = text,
        context = "Ich habe großes Fernweh nach dem Meer.",
        meaning = "wanderlust",
        box = box,
        dueDay = dueDay,
    )

    @Test
    fun `dueWords filters by today and excludes rated ids`() {
        cache.save(
            username = USERNAME,
            fetchedAt = 1_000L,
            fetchedDay = 20401L,
            dueCount = 3,
            words = listOf(
                dueWord("w-1", dueDay = 20399L),
                dueWord("w-2", dueDay = 20400L),
                dueWord("w-3", dueDay = 20402L),
            ),
        )

        assertEquals(listOf("w-1", "w-2"), cache.dueWords(USERNAME, today = 20400L).map { it.id })

        assertTrue(cache.markRated(USERNAME, "w-1"))
        assertEquals(listOf("w-2"), cache.dueWords(USERNAME, 20400L).map { it.id })

        // The rating persists on disk, also for a freshly built cache instance.
        assertEquals(listOf("w-2"), GermanDueCache(dir).dueWords(USERNAME, 20400L).map { it.id })

        // Marking again stays idempotent and does not duplicate the id.
        assertTrue(cache.markRated(USERNAME, "w-1"))
        assertEquals(listOf("w-2"), cache.dueWords(USERNAME, 20400L).map { it.id })
    }

    @Test
    fun `a cache for another username reads as empty`() {
        cache.save(
            username = "user-a",
            fetchedAt = 1_000L,
            fetchedDay = 20401L,
            dueCount = 2,
            words = listOf(dueWord("w-1"), dueWord("w-2")),
        )

        assertTrue(cache.dueWords("user-b", today = 20500L).isEmpty())
        assertEquals(0, cache.dueCount("user-b", 20500L))
        assertNull(cache.fetchedAt("user-b"))
        assertFalse(cache.markRated("user-b", "w-1"))

        // The other user's snapshot is untouched by all of the above.
        assertEquals(2, cache.dueWords("user-a", 20500L).size)
    }

    @Test
    fun `dueCount keeps the summary count for the fetch day and falls back to the cached words later`() {
        cache.save(
            username = USERNAME,
            fetchedAt = 1_000L,
            fetchedDay = 20401L,
            dueCount = 9,
            words = listOf(dueWord("w-1", dueDay = 20400L), dueWord("w-2", dueDay = 20400L)),
        )

        // Fresh for the fetch day: the summary's own count, minus locally rated words.
        assertEquals(9, cache.dueCount(USERNAME, 20401L))
        cache.markRated(USERNAME, "w-1")
        assertEquals(8, cache.dueCount(USERNAME, 20401L))

        // A later day: only the cached words that are due by then are known due.
        assertEquals(1, cache.dueCount(USERNAME, 20405L))
    }

    @Test
    fun `a fresh fetch resets the locally rated ids`() {
        cache.save(USERNAME, 1_000L, 20401L, 1, listOf(dueWord("w-1")))
        cache.markRated(USERNAME, "w-1")
        assertTrue(cache.dueWords(USERNAME, 20401L).isEmpty())

        // The next refresh replaces the snapshot: w-1 is due on the server again.
        cache.save(USERNAME, 2_000L, 20401L, 1, listOf(dueWord("w-1")))
        assertEquals(listOf("w-1"), cache.dueWords(USERNAME, 20401L).map { it.id })
    }

    @Test
    fun `clear drops the cache and a corrupt file reads as empty`() {
        cache.save(USERNAME, 1_000L, 20401L, 1, listOf(dueWord("w-1")))
        cache.clear()
        assertTrue(cache.dueWords(USERNAME, 20500L).isEmpty())
        assertNull(cache.read(USERNAME))

        // A corrupt file never yields a half-parsed cache either.
        File(dir, GermanDueCache.FILE_NAME).writeText("""{"version":1,"username":"$USERNAME","words":["corrupt"]}""")
        assertTrue(cache.dueWords(USERNAME, 20500L).isEmpty())
    }

    @Test
    fun `the codec round-trips a snapshot and rejects partial snapshots`() {
        val state = GermanDueCacheState(
            username = USERNAME,
            fetchedAt = 1_234L,
            fetchedDay = 20401L,
            dueCount = 5,
            words = listOf(dueWord("w-1"), dueWord("w-2", dueDay = 20402L, box = 7)),
            ratedIds = listOf("w-2"),
        )
        assertEquals(state, GermanDueCacheCodec.decode(GermanDueCacheCodec.encode(state)))

        // Every field must be present with the right type; anything less is null.
        listOf(
            "not json at all",
            """{"version":1}""",
            """{"version":2,"username":"$USERNAME"}""",
            """{"version":1,"username":" "}""",
            """{"version":1,"username":"$USERNAME","fetchedAt":1234,"fetchedDay":20401,"dueCount":5}""",
            // words must be an array, not a number.
            """{"version":1,"username":"$USERNAME","fetchedAt":1234,"fetchedDay":20401,"dueCount":5,"words":5}""",
        ).forEach { raw ->
            assertNull("raw '$raw'", GermanDueCacheCodec.decode(raw))
        }

        // A word missing any required field rejects the whole snapshot.
        val wordless = listOf(
            """{"text":"Fernweh","box":3,"dueDay":20401}""",
            """{"id":"","text":"Fernweh","box":3,"dueDay":20401}""",
            """{"id":"w-1","text":" ","box":3,"dueDay":20401}""",
            """{"id":"w-1","text":"Fernweh","box":0,"dueDay":20401}""",
            """{"id":"w-1","text":"Fernweh","box":8,"dueDay":20401}""",
            """{"id":"w-1","text":"Fernweh","box":3,"dueDay":"20401"}""",
            """{"id":"w-1","text":"Fernweh","box":3,"dueDay":20401.5}""",
        )
        for (word in wordless) {
            val raw = """{"version":1,"username":"$USERNAME","fetchedAt":1234,"fetchedDay":20401,"dueCount":5,"words":[$word]}"""
            assertNull("word '$word'", GermanDueCacheCodec.decode(raw))
        }
    }

    companion object {
        private const val USERNAME = "ola-test-user-7"
    }
}
