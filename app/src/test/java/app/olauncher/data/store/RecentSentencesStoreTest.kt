package app.olauncher.data.store

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class RecentSentencesStoreTest {

    private lateinit var tempDir: File
    private lateinit var store: RecentSentencesStore

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("recent_sentences_test").toFile()
        store = RecentSentencesStore(tempDir)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `encode and decode round-trip keeps every field with non-default values`() {
        val originalSentence = RecentSentence(
            id = "test-id-12345678",
            text = "Dies ist ein wichtiger Beispielsatz.",
            source = "tagesschau",
            url = "https://tagesschau.de/meldung-1",
            publishedAt = 1700000000123L,
            fetchedAt = 1700000005432L,
            consumed = true,
            wordIds = listOf(7L, 42L)
        )
        val originalArticle = FetchedArticle(
            url = "https://tagesschau.de/artikel-1",
            fetchedAt = 1700000010000L
        )
        val originalData = RecentSentencesData(
            sentences = listOf(originalSentence),
            articles = listOf(originalArticle)
        )

        val encoded = RecentSentencesCodec.encode(originalData)
        val decoded = RecentSentencesCodec.decode(encoded)

        assertEquals(1, decoded.sentences.size)
        val decodedSentence = decoded.sentences[0]
        assertEquals(originalSentence.id, decodedSentence.id)
        assertEquals(originalSentence.text, decodedSentence.text)
        assertEquals(originalSentence.source, decodedSentence.source)
        assertEquals(originalSentence.url, decodedSentence.url)
        assertEquals(originalSentence.publishedAt, decodedSentence.publishedAt)
        assertEquals(originalSentence.fetchedAt, decodedSentence.fetchedAt)
        assertEquals(true, decodedSentence.consumed)
        assertEquals(listOf(7L, 42L), decodedSentence.wordIds)

        assertEquals(1, decoded.articles.size)
        val decodedArticle = decoded.articles[0]
        assertEquals(originalArticle.url, decodedArticle.url)
        assertEquals(originalArticle.fetchedAt, decodedArticle.fetchedAt)

        assertEquals(originalData, decoded)
    }

    @Test
    fun `decode of null, empty, brackets, and garbage gives empty data`() {
        assertEquals(RecentSentencesData(emptyList(), emptyList()), RecentSentencesCodec.decode(null))
        assertEquals(RecentSentencesData(emptyList(), emptyList()), RecentSentencesCodec.decode(""))
        assertEquals(RecentSentencesData(emptyList(), emptyList()), RecentSentencesCodec.decode("   "))
        assertEquals(RecentSentencesData(emptyList(), emptyList()), RecentSentencesCodec.decode("[]"))
        assertEquals(RecentSentencesData(emptyList(), emptyList()), RecentSentencesCodec.decode("garbage"))
        assertEquals(RecentSentencesData(emptyList(), emptyList()), RecentSentencesCodec.decode("{not-valid-json"))
    }

    @Test
    fun `corrupt entry is skipped while neighbour decodes`() {
        val raw = """
            {
              "version": 1,
              "sentences": [
                {
                  "id": "corrupt-missing-text",
                  "source": "tagesschau",
                  "url": "https://tagesschau.de/1",
                  "publishedAt": 100,
                  "fetchedAt": 100
                },
                {
                  "id": "valid-sentence",
                  "text": "Gültiger Satz.",
                  "source": "tagesschau",
                  "url": "https://tagesschau.de/2",
                  "publishedAt": 200,
                  "fetchedAt": 200,
                  "consumed": false,
                  "wordIds": [1, 2]
                },
                {
                  "id": "corrupt-non-string-text",
                  "text": 12345,
                  "source": "tagesschau",
                  "url": "https://tagesschau.de/3",
                  "publishedAt": 300,
                  "fetchedAt": 300
                },
                {
                  "id": "corrupt-missing-pub",
                  "text": "Kein Datum.",
                  "source": "tagesschau",
                  "url": "https://tagesschau.de/4",
                  "fetchedAt": 400
                }
              ],
              "articles": [
                {
                  "url": "",
                  "fetchedAt": 500
                },
                {
                  "url": "https://tagesschau.de/art-ok",
                  "fetchedAt": 600
                },
                {
                  "url": "https://tagesschau.de/art-no-date"
                }
              ]
            }
        """.trimIndent()

        val decoded = RecentSentencesCodec.decode(raw)
        assertEquals(1, decoded.sentences.size)
        assertEquals("valid-sentence", decoded.sentences[0].id)
        assertEquals("Gültiger Satz.", decoded.sentences[0].text)

        assertEquals(1, decoded.articles.size)
        assertEquals("https://tagesschau.de/art-ok", decoded.articles[0].url)
        assertEquals(600L, decoded.articles[0].fetchedAt)
    }

    @Test
    fun `decode defaults missing consumed to false and drops malformed wordIds and dedups duplicate ids`() {
        val raw = """
            {
              "version": 1,
              "sentences": [
                {
                  "id": "s1",
                  "text": "Erster Satz.",
                  "source": "tagesschau",
                  "url": "https://tagesschau.de/1",
                  "publishedAt": 100,
                  "fetchedAt": 100,
                  "wordIds": [10, "bad", 20, 30.0]
                },
                {
                  "id": "s1",
                  "text": "Duplikat erster Satz (sollte ignoriert werden).",
                  "source": "tagesschau",
                  "url": "https://tagesschau.de/dup",
                  "publishedAt": 999,
                  "fetchedAt": 999
                },
                {
                  "id": "s2",
                  "text": "Zweiter Satz.",
                  "source": "tagesschau",
                  "url": "https://tagesschau.de/2",
                  "publishedAt": 200,
                  "fetchedAt": 200,
                  "wordIds": "not-an-array"
                }
              ]
            }
        """.trimIndent()

        val decoded = RecentSentencesCodec.decode(raw)
        assertEquals(2, decoded.sentences.size)

        val s1 = decoded.sentences[0]
        assertEquals("s1", s1.id)
        assertEquals("Erster Satz.", s1.text)
        assertFalse(s1.consumed)
        assertEquals(listOf(10L, 20L, 30L), s1.wordIds)

        val s2 = decoded.sentences[1]
        assertEquals("s2", s2.id)
        assertEquals("Zweiter Satz.", s2.text)
        assertFalse(s2.consumed)
        assertTrue(s2.wordIds.isEmpty())
    }

    @Test
    fun `textId is equal for different spacing and case, distinct for other sentences, length 16 hex`() {
        val id1 = RecentSentencesCodec.textId("Das  ist ein Test.")
        val id2 = RecentSentencesCodec.textId("das ist ein test.")
        val id3 = RecentSentencesCodec.textId("Ein ganz anderer Satz.")

        assertEquals(id1, id2)
        assertEquals(16, id1.length)
        assertTrue(id1.all { it in "0123456789abcdef" })
        assertNotEquals(id1, id3)
    }

    @Test
    fun `normalizeText collapses whitespace, replaces non-breaking space, trims, and normalizes NFC`() {
        val input = "  \u00A0Das \t\n  ist   ein\u00A0Test.  \u00A0"
        val normalized = RecentSentencesCodec.normalizeText(input)
        assertEquals("Das ist ein Test.", normalized)
    }

    @Test
    fun `addAll dedups against store and within batch and returns only new ones`() {
        val s1 = RecentSentence(
            id = "will-be-recomputed",
            text = "Erste Meldung aus Berlin.",
            source = "tagesschau",
            url = "https://tagesschau.de/1",
            publishedAt = 1000L,
            fetchedAt = 1000L
        )

        val firstAdded = store.addAll(listOf(s1))
        assertEquals(1, firstAdded.size)
        val expectedS1Id = RecentSentencesCodec.textId("Erste Meldung aus Berlin.")
        assertEquals(expectedS1Id, firstAdded[0].id)
        assertEquals("Erste Meldung aus Berlin.", firstAdded[0].text)

        val s2 = RecentSentence(
            id = "",
            text = "Zweite Meldung aus Hamburg.",
            source = "tagesschau",
            url = "https://tagesschau.de/2",
            publishedAt = 2000L,
            fetchedAt = 2000L
        )
        val s2Dup = RecentSentence(
            id = "",
            text = "  zweite   meldung aus   hamburg. ",
            source = "tagesschau",
            url = "https://tagesschau.de/2-dup",
            publishedAt = 2100L,
            fetchedAt = 2100L
        )
        val s3 = RecentSentence(
            id = "",
            text = "Dritte Meldung aus München.",
            source = "tagesschau",
            url = "https://tagesschau.de/3",
            publishedAt = 3000L,
            fetchedAt = 3000L
        )
        val blank = RecentSentence(
            id = "",
            text = "   \u00A0  ",
            source = "tagesschau",
            url = "https://tagesschau.de/blank",
            publishedAt = 4000L,
            fetchedAt = 4000L
        )

        val secondAdded = store.addAll(listOf(s1, s2, s2Dup, s3, blank))
        assertEquals(2, secondAdded.size)
        assertEquals("Zweite Meldung aus Hamburg.", secondAdded[0].text)
        assertEquals("Dritte Meldung aus München.", secondAdded[1].text)

        val all = store.all()
        assertEquals(3, all.size)
        assertEquals(firstAdded[0].id, all[0].id)
        assertEquals(secondAdded[0].id, all[1].id)
        assertEquals(secondAdded[1].id, all[2].id)

        // Calling addAll with only duplicates returns empty list and does not re-write
        val emptyAdded = store.addAll(listOf(s2, s3))
        assertTrue(emptyAdded.isEmpty())
        assertEquals(3, store.all().size)
    }

    @Test
    fun `markConsumed updates consumed status and unconsumed filters`() {
        val s1 = RecentSentence(
            id = "",
            text = "Satz Nummer 1",
            source = "src",
            url = "u1",
            publishedAt = 100L,
            fetchedAt = 100L
        )
        val s2 = RecentSentence(
            id = "",
            text = "Satz Nummer 2",
            source = "src",
            url = "u2",
            publishedAt = 200L,
            fetchedAt = 200L
        )

        val added = store.addAll(listOf(s1, s2))
        assertEquals(2, store.unconsumed().size)

        store.markConsumed(listOf(added[0].id, "unknown-id-999"))

        val unconsumed = store.unconsumed()
        assertEquals(1, unconsumed.size)
        assertEquals(added[1].id, unconsumed[0].id)

        val all = store.all()
        assertTrue(all.first { it.id == added[0].id }.consumed)
        assertFalse(all.first { it.id == added[1].id }.consumed)
    }

    @Test
    fun `linkWords merges without duplicates and forWord orders newest first`() {
        val s1 = RecentSentence(
            id = "",
            text = "Älterer Satz",
            source = "src",
            url = "u1",
            publishedAt = 1000L,
            fetchedAt = 1000L
        )
        val s2 = RecentSentence(
            id = "",
            text = "Neuerer Satz",
            source = "src",
            url = "u2",
            publishedAt = 2000L,
            fetchedAt = 2000L
        )
        val added = store.addAll(listOf(s1, s2))
        val s1Id = added[0].id
        val s2Id = added[1].id

        store.linkWords(
            mapOf(
                s1Id to listOf(10L, 20L),
                s2Id to listOf(10L),
                "unknown-id" to listOf(10L, 99L)
            )
        )
        // Merge with existing
        store.linkWords(mapOf(s1Id to listOf(20L, 30L)))

        val forWord10 = store.forWord(10L)
        assertEquals(2, forWord10.size)
        assertEquals(s2Id, forWord10[0].id) // newest publishedAt (2000L) first
        assertEquals(s1Id, forWord10[1].id) // 1000L second

        val forWord30 = store.forWord(30L)
        assertEquals(1, forWord30.size)
        assertEquals(s1Id, forWord30[0].id)

        val updatedS1 = store.all().first { it.id == s1Id }
        assertEquals(listOf(10L, 20L, 30L), updatedS1.wordIds)
    }

    @Test
    fun `recordArticles and hasArticle track fetched articles and avoid duplicates`() {
        assertFalse(store.hasArticle("https://tagesschau.de/artikel-1"))
        store.recordArticles(listOf("https://tagesschau.de/artikel-1", "https://tagesschau.de/artikel-2"), 5000L)

        assertTrue(store.hasArticle("https://tagesschau.de/artikel-1"))
        assertTrue(store.hasArticle("https://tagesschau.de/artikel-2"))
        assertFalse(store.hasArticle("https://tagesschau.de/artikel-3"))

        // Re-recording should not duplicate
        store.recordArticles(listOf("https://tagesschau.de/artikel-1"), 6000L)
        val loaded = store.hasArticle("https://tagesschau.de/artikel-1")
        assertTrue(loaded)
    }

    @Test
    fun `prune keeps sentence 39 days old and drops 41 days old, same for articles`() {
        val nowMs = 100_000_000_000L
        val dayMs = TimeUnit.DAYS.toMillis(1)

        val s39 = RecentSentence(
            id = "",
            text = "Satz vor 39 Tagen",
            source = "src",
            url = "u1",
            publishedAt = nowMs - 39 * dayMs,
            fetchedAt = nowMs
        )
        val s41 = RecentSentence(
            id = "",
            text = "Satz vor 41 Tagen",
            source = "src",
            url = "u2",
            publishedAt = nowMs - 41 * dayMs,
            fetchedAt = nowMs
        )
        store.addAll(listOf(s39, s41))
        store.recordArticles(listOf("https://news.de/art39"), fetchedAt = nowMs - 39 * dayMs)
        store.recordArticles(listOf("https://news.de/art41"), fetchedAt = nowMs - 41 * dayMs)

        store.prune(nowMs)

        val sentences = store.all()
        assertEquals(1, sentences.size)
        assertEquals("Satz vor 39 Tagen", sentences[0].text)

        assertTrue(store.hasArticle("https://news.de/art39"))
        assertFalse(store.hasArticle("https://news.de/art41"))
    }

    @Test
    fun `cap drops consumed oldest sentences first when exceeding 5000 sentences`() {
        val count = 5003
        val candidates = ArrayList<RecentSentence>(count)

        // 10 consumed sentences: publishedAt 1000..1009
        for (i in 0 until 10) {
            candidates.add(
                RecentSentence(
                    id = "",
                    text = "Konsumierter Satz $i",
                    source = "src",
                    url = "https://news.de/c/$i",
                    publishedAt = 1000L + i,
                    fetchedAt = 1000L + i,
                    consumed = true
                )
            )
        }
        // 4993 unconsumed sentences: publishedAt 2000..6992
        for (i in 10 until count) {
            candidates.add(
                RecentSentence(
                    id = "",
                    text = "Unkonsumierter Satz $i",
                    source = "src",
                    url = "https://news.de/u/$i",
                    publishedAt = 2000L + i,
                    fetchedAt = 2000L + i,
                    consumed = false
                )
            )
        }

        val added = store.addAll(candidates)
        val all = store.all()

        assertEquals(5000, all.size)
        assertEquals(5000, added.size)

        // The 3 oldest consumed sentences (i = 0, 1, 2) should have been dropped
        val dropped0 = RecentSentencesCodec.textId("Konsumierter Satz 0")
        val dropped1 = RecentSentencesCodec.textId("Konsumierter Satz 1")
        val dropped2 = RecentSentencesCodec.textId("Konsumierter Satz 2")
        val kept3 = RecentSentencesCodec.textId("Konsumierter Satz 3")

        val allIds = all.map { it.id }.toSet()
        assertFalse(allIds.contains(dropped0))
        assertFalse(allIds.contains(dropped1))
        assertFalse(allIds.contains(dropped2))
        assertTrue(allIds.contains(kept3))
    }

    @Test
    fun `cap drops unconsumed oldest sentences if consumed sentences are exhausted`() {
        val count = 5002
        val candidates = ArrayList<RecentSentence>(count)

        // 1 consumed sentence: publishedAt 1000L
        candidates.add(
            RecentSentence(
                id = "",
                text = "Einziger konsumierter Satz",
                source = "src",
                url = "https://news.de/c/0",
                publishedAt = 1000L,
                fetchedAt = 1000L,
                consumed = true
            )
        )
        // 5001 unconsumed sentences: publishedAt 2000..7000
        for (i in 1 until count) {
            candidates.add(
                RecentSentence(
                    id = "",
                    text = "Unkonsumierter Satz $i",
                    source = "src",
                    url = "https://news.de/u/$i",
                    publishedAt = 2000L + i,
                    fetchedAt = 2000L + i,
                    consumed = false
                )
            )
        }

        store.addAll(candidates)
        val all = store.all()
        assertEquals(5000, all.size)

        val droppedConsumed = RecentSentencesCodec.textId("Einziger konsumierter Satz")
        val droppedOldestUnconsumed = RecentSentencesCodec.textId("Unkonsumierter Satz 1")
        val keptNextUnconsumed = RecentSentencesCodec.textId("Unkonsumierter Satz 2")

        val allIds = all.map { it.id }.toSet()
        assertFalse(allIds.contains(droppedConsumed))
        assertFalse(allIds.contains(droppedOldestUnconsumed))
        assertTrue(allIds.contains(keptNextUnconsumed))
    }

    @Test
    fun `article cap drops oldest fetchedAt when exceeding MAX_ARTICLES`() {
        val count = RecentSentencesStore.MAX_ARTICLES + 2
        val urls = (0 until count).map { "https://news.de/article/$it" }
        // Record them with distinct fetchedAt times
        for (i in 0 until count) {
            store.recordArticles(listOf(urls[i]), fetchedAt = 1000L + i)
        }

        // Oldest two (0 and 1) should be dropped
        assertFalse(store.hasArticle(urls[0]))
        assertFalse(store.hasArticle(urls[1]))
        assertTrue(store.hasArticle(urls[2]))
        assertTrue(store.hasArticle(urls[count - 1]))
    }
}
