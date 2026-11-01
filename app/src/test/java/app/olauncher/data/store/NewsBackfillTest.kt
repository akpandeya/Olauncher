package app.olauncher.data.store

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class NewsBackfillTest {

    private lateinit var tempDir: File
    private lateinit var sentencesStore: RecentSentencesStore
    private lateinit var wordsStore: WordsStore

    private val lemmaMap = mapOf(
        "ging" to "gehen",
        "gingen" to "gehen",
        "Häuser" to "Haus",
        "Katzen" to "Katze"
    )

    private val matcher = SentenceWordMatcher { form ->
        lemmaMap[form] ?: lemmaMap[form.lowercase()] ?: lemmaMap[form.replaceFirstChar { it.uppercase() }]
    }

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("news_backfill_test").toFile()
        sentencesStore = RecentSentencesStore(tempDir)
        wordsStore = WordsStore(tempDir)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `plan links exact word lists and selects context fills according to length criteria`() {
        val word1 = Word(id = 101L, text = "gehen", context = "Ich gehe oft spazieren.", dueDay = 1000L, addedAt = 1000L)
        val word2 = Word(id = 102L, text = "Haus", context = "", dueDay = 1000L, addedAt = 1000L)
        val word3 = Word(id = 103L, text = "Katze", context = "", dueDay = 1000L, addedAt = 1000L)

        val s1 = RecentSentence(
            id = "s1",
            text = "Kleine Katzen gingen heute schnell.", // 35 chars (< 40)
            source = "news",
            url = "https://news.example.com/1",
            publishedAt = 4000L,
            fetchedAt = 5000L
        )
        val s2 = RecentSentence(
            id = "s2",
            text = "Er ging in ein kleines Haus.", // 28 chars (< 40)
            source = "news",
            url = "https://news.example.com/2",
            publishedAt = 3000L,
            fetchedAt = 5000L
        )
        val s3 = RecentSentence(
            id = "s3",
            text = "In diesem großen Haus wohnen viele verschiedene Familien gemeinsam.", // 67 chars (40..200)
            source = "news",
            url = "https://news.example.com/3",
            publishedAt = 2000L,
            fetchedAt = 5000L
        )
        val s4 = RecentSentence(
            id = "s4",
            text = "Zwei Katzen gingen fort.", // 24 chars (< 40)
            source = "news",
            url = "https://news.example.com/4",
            publishedAt = 1000L,
            fetchedAt = 5000L
        )

        val plan = NewsBackfill.plan(
            words = listOf(word1, word2, word3),
            newSentences = listOf(s1, s2, s3, s4),
            matcher = matcher
        )

        // Exact sentence-id -> word-id lists for 3 words x 4 sentences
        val expectedLinks = mapOf(
            "s1" to listOf(101L, 103L),
            "s2" to listOf(101L, 102L),
            "s3" to listOf(102L),
            "s4" to listOf(101L, 103L)
        )
        assertEquals(expectedLinks, plan.links)

        // Word with non-blank context is linked but absent from contextFills
        assertFalse(plan.contextFills.containsKey(101L))

        // Blank-context word gets the newest in-range sentence (s3, length 67 in 40..200, newer than older matches)
        assertEquals(s3.text, plan.contextFills[102L])

        // When every candidate is shorter than 40 chars, the newest one is used (s1 at 4000L vs s4 at 1000L)
        assertEquals(s1.text, plan.contextFills[103L])
    }

    @Test
    fun `applyPlan links sentences and updates only blank contexts without touching other fields`() {
        val s1 = RecentSentence(
            id = "s1",
            text = "Kleine Katzen gingen heute schnell.",
            source = "news",
            url = "https://news.example.com/1",
            publishedAt = 4000L,
            fetchedAt = 5000L
        )
        val s2 = RecentSentence(
            id = "s2",
            text = "Er ging in ein kleines Haus.",
            source = "news",
            url = "https://news.example.com/2",
            publishedAt = 3000L,
            fetchedAt = 5000L
        )
        val s3 = RecentSentence(
            id = "s3",
            text = "In diesem großen Haus wohnen viele verschiedene Familien gemeinsam.",
            source = "news",
            url = "https://news.example.com/3",
            publishedAt = 2000L,
            fetchedAt = 5000L
        )
        val s4 = RecentSentence(
            id = "s4",
            text = "Zwei Katzen gingen fort.",
            source = "news",
            url = "https://news.example.com/4",
            publishedAt = 1000L,
            fetchedAt = 5000L
        )
        val storedSentences = sentencesStore.addAll(listOf(s1, s2, s3, s4))
        assertEquals(4, storedSentences.size)

        val w1 = wordsStore.add("gehen", context = "Ich gehe oft spazieren.")
        val w2Base = wordsStore.add("Haus", context = "")
        val w2 = w2Base.copy(
            meaning = "to go",
            box = 3,
            dueDay = 19600L,
            listIds = listOf("list-1")
        )
        wordsStore.update(w2)
        val w3 = wordsStore.add("Katze", context = "")

        val plan = NewsBackfill.plan(
            words = wordsStore.all(),
            newSentences = storedSentences,
            matcher = matcher
        )

        // User fills context for w3 between plan and applyPlan
        val userEditedContext = "Benutzerdefinierter Kontext für die Katze."
        wordsStore.update(wordsStore.get(w3.id)!!.copy(context = userEditedContext))

        val updatedCount = NewsBackfill.applyPlan(plan, sentencesStore, wordsStore)

        // Exact count of updated words: only w2 was updated
        assertEquals(1, updatedCount)

        // RecentSentencesStore.forWord(id) returns the linked sentences
        val linkedW1 = sentencesStore.forWord(w1.id)
        assertEquals(listOf(storedSentences[0].id, storedSentences[1].id, storedSentences[3].id), linkedW1.map { it.id })

        val linkedW2 = sentencesStore.forWord(w2.id)
        assertEquals(listOf(storedSentences[1].id, storedSentences[2].id), linkedW2.map { it.id })

        val linkedW3 = sentencesStore.forWord(w3.id)
        assertEquals(listOf(storedSentences[0].id, storedSentences[3].id), linkedW3.map { it.id })

        // Blank-context word's context equals the chosen text; meaning, box, dueDay, listIds stay unchanged
        val w2Current = wordsStore.get(w2.id)!!
        assertEquals(storedSentences[2].text, w2Current.context)
        assertEquals("to go", w2Current.meaning)
        assertEquals(3, w2Current.box)
        assertEquals(19600L, w2Current.dueDay)
        assertEquals(listOf("list-1"), w2Current.listIds)

        // Word whose context was filled by user is NOT overwritten
        val w3Current = wordsStore.get(w3.id)!!
        assertEquals(userEditedContext, w3Current.context)
    }

    @Test
    fun `empty inputs produce empty plan and applying empty plan returns zero`() {
        val plan = NewsBackfill.plan(emptyList(), emptyList(), matcher)
        assertTrue(plan.links.isEmpty())
        assertTrue(plan.contextFills.isEmpty())

        val count = NewsBackfill.applyPlan(plan, sentencesStore, wordsStore)
        assertEquals(0, count)
    }

    @Test
    fun `candidate longer than max context chars is skipped for in-range candidate`() {
        val longSentence = "A".repeat(201) + " Katze"
        val inRangeSentence = "Das ist ein schöner und passender Beispielsatz mit einer Katze." // 63 chars
        val sLong = RecentSentence(id = "sLong", text = longSentence, source = "news", url = "u1", publishedAt = 2000L, fetchedAt = 3000L)
        val sNormal = RecentSentence(id = "sNormal", text = inRangeSentence, source = "news", url = "u2", publishedAt = 1000L, fetchedAt = 3000L)

        val word = Word(id = 201L, text = "Katze", context = "", dueDay = 1000L, addedAt = 1000L)
        val plan = NewsBackfill.plan(listOf(word), listOf(sLong, sNormal), matcher)

        assertEquals(inRangeSentence, plan.contextFills[201L])
    }
}
