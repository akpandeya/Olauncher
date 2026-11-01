package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextRewritePlannerTest {

    // --- helpers -------------------------------------------------------------

    /** A weak non-blank captured context: four capitalized nouns, no punctuation, no verb. */
    private val weakContext = "Startseite Politik Wirtschaft Sport"

    /** A strong German sentence a rewrite could keep. */
    private val strongContext = "Die Regierung hat gestern ein neues Gesetz in Berlin beschlossen."

    /** A strong harvested sentence inside the 40..200 window (78 chars). */
    private val strongSentence =
        "Die junge Katze hat gestern Abend im Garten eine Maus gefangen und gefressen."

    private fun word(
        id: Long,
        text: String = "Katze",
        context: String = "",
        contextSource: String = ContextSource.CAPTURED,
        learnedAt: Long? = null,
        addedAt: Long = 1000L,
    ) = Word(
        id = id,
        text = text,
        context = context,
        contextSource = contextSource,
        dueDay = 20000L,
        addedAt = addedAt,
        learnedAt = learnedAt,
    )

    private fun sentence(
        id: String,
        text: String,
        publishedAt: Long,
        consumed: Boolean = false,
        wordIds: List<Long> = emptyList(),
    ) = RecentSentence(
        id = id,
        text = text,
        source = "news",
        url = "https://news.example.com/$id",
        publishedAt = publishedAt,
        fetchedAt = 9000L,
        consumed = consumed,
        wordIds = wordIds,
    )

    // --- WordNeedRanker ------------------------------------------------------

    @Test
    fun `a word with PRACTICE_CAP minus one cards is kept and one at the cap is dropped`() {
        val kept = word(id = 1L, text = "Haus")
        val capped = word(id = 2L, text = "Katze")
        val counts = mapOf(1L to WordNeedRanker.PRACTICE_CAP - 1, 2L to WordNeedRanker.PRACTICE_CAP)

        assertEquals(listOf(kept), WordNeedRanker.rank(listOf(kept, capped), counts))
    }

    @Test
    fun `fewest cards first and equal counts order by newest addedAt with missing entry as zero`() {
        val olderZero = word(id = 1L, text = "Haus", addedAt = 1000L) // no entry -> 0 cards
        val newerZero = word(id = 2L, text = "Katze", addedAt = 3000L)
        val fourCards = word(id = 3L, text = "Regierung", addedAt = 2000L)
        val counts = mapOf(2L to 0, 3L to 4)

        val ranked = WordNeedRanker.rank(listOf(fourCards, olderZero, newerZero), counts)

        assertEquals(listOf(2L, 1L, 3L), ranked.map { it.id })
    }

    @Test
    fun `ineligible words are dropped by the ranker`() {
        val userWord = word(id = 1L, text = "Haus", context = weakContext, contextSource = ContextSource.USER)
        val learned = word(id = 2L, text = "Katze", context = weakContext, learnedAt = 5000L)
        val strong = word(id = 3L, text = "Regierung", context = strongContext)
        val twoLetters = word(id = 4L, text = "Ei", context = "")

        assertTrue(WordNeedRanker.rank(listOf(userWord, learned, strong, twoLetters), emptyMap()).isEmpty())
    }

    // --- plan ----------------------------------------------------------------

    @Test
    fun `blank-context word with no linked sentence gets neither fill nor job`() {
        val w = word(id = 1L, text = "Katze", context = "")
        val pool = sentence(id = "sGood", text = strongSentence, publishedAt = 5000L)

        val plan = ContextRewritePlanner.plan(listOf(w), emptyMap(), listOf(pool))

        assertTrue(plan.asIs.isEmpty())
        assertTrue(plan.rewrites.isEmpty())
    }

    @Test
    fun `blank-context word with a linked in-range sentence gets an as-is fill`() {
        val w = word(id = 1L, text = "Katze", context = "")
        val linked = sentence(id = "sLink", text = strongSentence, publishedAt = 5000L, wordIds = listOf(1L))

        val plan = ContextRewritePlanner.plan(listOf(w), emptyMap(), listOf(linked))

        assertEquals(listOf(AsIsFill(w, linked)), plan.asIs)
        assertTrue(plan.rewrites.isEmpty())
    }

    @Test
    fun `capped word with a linked sentence gets neither fill nor job`() {
        val w = word(id = 1L, text = "Katze", context = weakContext)
        val linked = sentence(id = "sLink", text = strongSentence, publishedAt = 5000L, wordIds = listOf(1L))

        val plan = ContextRewritePlanner.plan(listOf(w), mapOf(1L to WordNeedRanker.PRACTICE_CAP), listOf(linked))

        assertTrue(plan.asIs.isEmpty())
        assertTrue(plan.rewrites.isEmpty())
    }

    @Test
    fun `weak word with a linked sentence fills as-is with the in-range one over a newer short one`() {
        val w = word(id = 1L, text = "Katze", context = weakContext)
        // Newest link first, but it is a short (29 chars) weak fragment.
        val newerShort = sentence(id = "sShort", text = "Startseite Politik Wirtschaft", publishedAt = 5000L, wordIds = listOf(1L))
        val olderInRange = sentence(id = "sRange", text = strongSentence, publishedAt = 4000L, wordIds = listOf(1L))

        val plan = ContextRewritePlanner.plan(listOf(w), emptyMap(), listOf(newerShort, olderInRange))

        assertEquals(listOf(AsIsFill(w, olderInRange)), plan.asIs)
        assertTrue(plan.rewrites.isEmpty())
    }

    @Test
    fun `weak word with no link gets a job with the newest unconsumed in-range source and ties pick the shorter text`() {
        val w = word(id = 1L, text = "Haus", context = weakContext)
        val older = sentence(id = "sOld", text = strongSentence, publishedAt = 3000L)
        val tieLong = sentence(id = "sTieLong", text = strongSentence + " Wirklich.", publishedAt = 4000L)
        val tieShort = sentence(id = "sTieShort", text = strongSentence, publishedAt = 4000L)
        val consumed = sentence(id = "sConsumed", text = "Neu und unbrauchbar hier.", publishedAt = 5000L, consumed = true)

        val plan = ContextRewritePlanner.plan(listOf(w), emptyMap(), listOf(older, tieLong, tieShort, consumed))

        assertTrue(plan.asIs.isEmpty())
        assertEquals(1, plan.rewrites.size)
        assertEquals(w, plan.rewrites[0].word)
        assertEquals("sTieShort", plan.rewrites[0].source.id)
    }

    @Test
    fun `consumed too-short and too-long sentences never become rewrite sources`() {
        val w = word(id = 1L, text = "Haus", context = weakContext)
        val tooShort = sentence(id = "s39", text = "s".repeat(39), publishedAt = 5000L)
        val tooLong = sentence(id = "s201", text = "l".repeat(201), publishedAt = 4000L)
        val consumed = sentence(id = "sConsumed", text = strongSentence, publishedAt = 3000L, consumed = true)
        val good = sentence(id = "sGood", text = strongSentence, publishedAt = 2000L)

        val plan = ContextRewritePlanner.plan(listOf(w), emptyMap(), listOf(tooShort, tooLong, consumed, good))

        assertEquals(listOf("sGood"), plan.rewrites.map { it.source.id })
    }

    @Test
    fun `two jobs never share a source and an exhausted pool stops job creation`() {
        val first = word(id = 1L, text = "Haus", context = weakContext, addedAt = 1000L) // 0 cards
        val second = word(id = 2L, text = "Katze", context = weakContext, addedAt = 2000L)
        val counts = mapOf(2L to 4) // second has more cards, so it is ranked after first
        val onlySource = sentence(id = "sOnly", text = strongSentence, publishedAt = 5000L)

        val plan = ContextRewritePlanner.plan(listOf(first, second), counts, listOf(onlySource))

        assertEquals(listOf(RewriteJob(first, onlySource)), plan.rewrites)
    }

    @Test
    fun `maxRewrites caps the jobs and keeps the two newest addedAt words`() {
        val oldest = word(id = 11L, text = "Haus", context = weakContext, addedAt = 1000L)
        val newest = word(id = 12L, text = "Katze", context = weakContext, addedAt = 3000L)
        val middle = word(id = 13L, text = "Regierung", context = weakContext, addedAt = 2000L)
        val sourceA = sentence(id = "sA", text = strongSentence, publishedAt = 5000L)
        val sourceB = sentence(id = "sB", text = strongSentence + " Sehr gut.", publishedAt = 4000L)
        val sourceC = sentence(id = "sC", text = strongSentence + " Auch gut.", publishedAt = 3000L)

        val plan = ContextRewritePlanner.plan(
            listOf(oldest, newest, middle),
            emptyMap(),
            listOf(sourceA, sourceB, sourceC),
            maxRewrites = 2,
        )

        assertEquals(listOf(12L, 13L), plan.rewrites.map { it.word.id })
        assertEquals(listOf("sA", "sB"), plan.rewrites.map { it.source.id })
    }

    @Test
    fun `user-typed learned strong and two-letter words are skipped by the plan`() {
        val userWord = word(id = 1L, text = "Haus", context = weakContext, contextSource = ContextSource.USER)
        val learned = word(id = 2L, text = "Katze", context = weakContext, learnedAt = 5000L)
        val strong = word(id = 3L, text = "Regierung", context = strongContext)
        val twoLetters = word(id = 4L, text = "Ei", context = "")
        val pool = sentence(id = "sGood", text = strongSentence, publishedAt = 5000L)

        val plan = ContextRewritePlanner.plan(listOf(userWord, learned, strong, twoLetters), emptyMap(), listOf(pool))

        assertTrue(plan.asIs.isEmpty())
        assertTrue(plan.rewrites.isEmpty())
    }

    @Test
    fun `blank-context word with source USER is still eligible for an as-is fill`() {
        val w = word(id = 1L, text = "Katze", context = "", contextSource = ContextSource.USER)
        val linked = sentence(id = "sLink", text = strongSentence, publishedAt = 5000L, wordIds = listOf(1L))

        val plan = ContextRewritePlanner.plan(listOf(w), emptyMap(), listOf(linked))

        assertEquals(listOf(AsIsFill(w, linked)), plan.asIs)
    }

    @Test
    fun `a linked sentence identical to the current context is not chosen`() {
        val w = word(id = 1L, text = "Haus", context = weakContext)
        // Same text after normalization (extra whitespace), so not a candidate.
        val identical = sentence(id = "sSame", text = "Startseite  Politik   Wirtschaft Sport", publishedAt = 5000L, wordIds = listOf(1L))
        val pool = sentence(id = "sPool", text = strongSentence, publishedAt = 4000L)

        val plan = ContextRewritePlanner.plan(listOf(w), emptyMap(), listOf(identical, pool))

        assertTrue(plan.asIs.isEmpty())
        assertEquals(listOf(RewriteJob(w, pool)), plan.rewrites)
    }
}
