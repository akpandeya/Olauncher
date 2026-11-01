package app.olauncher.data.store

import app.olauncher.data.AiPracticePrefs
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Wiring for the context rewrite run (issue #693): real stores on a temp dir, a
 * scripted fake provider — no network in tests — and a literal [KeySource]. The
 * distinguishing values (model "wiring-model-7q", fixed clock, word id 4_242)
 * are asserted downstream, so swapped or missing wiring cannot pass.
 */
class ContextRewriteRunTest {

    companion object {
        /** Distinguishing model id: must reach the practice-sentence record. */
        private const val RUN_MODEL = "wiring-model-7q"

        /** A different non-blank model for the gate, so the two cannot be swapped. */
        private const val GATE_MODEL = "gate-model-2x"

        private const val FIXED_CLOCK = 1_712_345_678_901L

        private val TODAY: LocalDate = LocalDate.of(2026, 11, 1)

        /** Weak captured fragment (3 tokens, no punctuation) for the rewrite word. */
        private const val WEAK_CONTEXT = "Startseite Politik Wirtschaft"

        /** Strong harvested sentence inside the 40..200 window (97 chars). */
        private const val SOURCE_TEXT =
            "Die deutsche Wirtschaft wächst dieses Jahr langsamer als die Analysten in Berlin erwartet haben."

        /** Strong sentence for the as-is fill (78 chars). */
        private const val LINKED_TEXT =
            "Die junge Katze hat gestern Abend im Garten eine Maus gefangen und gefressen."
    }

    private lateinit var tempDir: File
    private lateinit var wordsStore: WordsStore
    private lateinit var sentences: RecentSentencesStore
    private lateinit var practiceItems: PracticeItemsStore
    private lateinit var practiceSentences: PracticeSentencesStore
    private lateinit var prefs: FakePrefs

    /** Scripted provider: records every request, pops one queued result per call. */
    private class ScriptedProvider(vararg results: AiResult) : AiProvider {
        private val queue = results.toMutableList()
        val requests = mutableListOf<AiRequest>()
        var calls = 0
            private set

        override suspend fun complete(request: AiRequest): AiResult {
            calls++
            requests += request
            check(queue.isNotEmpty()) { "provider script exhausted" }
            return queue.removeAt(0)
        }
    }

    private class FakePrefs : AiPracticePrefs {
        var sentCount = 0
        var sentDay = -1L
        override var aiProvider: String = "melious"
        override var aiBaseUrl: String = "https://api.melious.ai"
        override var aiModel: String = GATE_MODEL
        override var aiPracticeEnabled: Boolean = true

        /** Distinguishing cap: two billed words would still fit, three would not. */
        override var aiDailyWordCap: Int = 9
        override var aiKeyRejected: Boolean = false
        override var aiBillingBlocked: Boolean = false
        override var aiLastDigestAt: Long = 0
        override fun aiWordsSentToday(todayEpochDay: Long): Int =
            if (sentDay == todayEpochDay) sentCount else 0
        override fun recordAiWordsSent(count: Int, todayEpochDay: Long) {
            if (sentDay != todayEpochDay) {
                sentDay = todayEpochDay
                sentCount = 0
            }
            sentCount += count
        }
    }

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("context-rewrite-run").toFile()
        wordsStore = WordsStore(tempDir)
        sentences = RecentSentencesStore(tempDir)
        practiceItems = PracticeItemsStore(tempDir)
        practiceSentences = PracticeSentencesStore(tempDir)
        prefs = FakePrefs()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // --- fixtures ------------------------------------------------------------

    private fun writeWords(words: List<Word>) {
        File(tempDir, "words.json").writeText(WordsCodec.encode(words, emptyList()))
    }

    private fun word(
        id: Long,
        text: String,
        context: String,
        contextSource: String = ContextSource.CAPTURED,
        learnedAt: Long? = null,
    ) = Word(
        id = id,
        text = text,
        context = context,
        contextSource = contextSource,
        box = 1,
        dueDay = 20_261_101,
        addedAt = 1_000_000L + id,
        learnedAt = learnedAt,
    )

    /** Adds one harvested sentence and returns it with its real computed id. */
    private fun addSentence(text: String, publishedAt: Long): RecentSentence {
        val added = sentences.addAll(
            listOf(
                RecentSentence(
                    id = "fixture-id-overwritten",
                    text = text,
                    source = "news",
                    url = "https://news.example.invalid/article",
                    publishedAt = publishedAt,
                    fetchedAt = 1_000L,
                ),
            ),
        )
        assertEquals(1, added.size)
        return added.single()
    }

    private fun addPracticeCards(wordId: Long, count: Int) {
        practiceItems.append(
            (1..count).map { n ->
                PracticeItem(
                    wordId = wordId,
                    type = DrillType.CLOZE,
                    prompt = "Lücke $n für Wort $wordId",
                    answer = "Antwort $n",
                    choices = listOf("Antwort $n", "Andere $n"),
                )
            },
        )
        assertEquals(count, practiceItems.all().count { it.wordId == wordId })
    }

    private fun rewriter(provider: AiProvider) = SentenceRewriter(
        provider = provider,
        model = prefs.aiModel,
        keySource = KeySource { "fake-key" },
        matcher = SentenceWordMatcher { null },
        markConsumed = { id -> sentences.markConsumed(listOf(id)) },
    )

    private fun runStep(provider: AiProvider?): ContextRewriteSummary = runBlocking {
        ContextRewriteRun(
            words = wordsStore,
            sentences = sentences,
            practiceItems = practiceItems,
            practiceSentences = practiceSentences,
            rewriter = provider?.let { rewriter(it) },
            model = RUN_MODEL,
            clock = { FIXED_CLOCK },
        ).run(prefs, TODAY)
    }

    private fun storedWord(id: Long): Word =
        WordsCodec.decode(File(tempDir, "words.json").readText()).words.single { it.id == id }

    private fun storedSentences(): List<PracticeSentence> {
        val file = File(tempDir, "practice_sentences.json")
        return if (file.exists()) PracticeSentencesCodec.decode(file.readText()) else emptyList()
    }

    // --- rewrite path --------------------------------------------------------

    @Test
    fun `weak-context word is rewritten, recorded and its source consumed`() {
        val source = addSentence(SOURCE_TEXT, publishedAt = 5_000L)
        writeWords(listOf(word(id = 4_242L, text = "Wirtschaft", context = WEAK_CONTEXT)))
        val provider = ScriptedProvider(
            AiResult.Ok("""{"sentence": "$SOURCE_TEXT"}""", AiUsage(10, 20), 5L),
        )

        val summary = runStep(provider)

        assertEquals(1, summary.rewritten)
        val rewrittenWord = storedWord(4_242L)
        assertEquals(SOURCE_TEXT, rewrittenWord.context)
        assertEquals(ContextSource.AUTO, rewrittenWord.contextSource)

        val recorded = storedSentences()
        assertEquals(1, recorded.size)
        with(recorded.single()) {
            assertEquals(4_242L, wordId)
            assertEquals(RUN_MODEL, model)
            assertEquals(FIXED_CLOCK, createdAt)
            assertEquals(source.id, sourceSentenceId)
            assertEquals(WEAK_CONTEXT, originalContext)
            assertEquals(SOURCE_TEXT, text)
        }

        assertTrue(sentences.all().single { it.id == source.id }.consumed)
        assertEquals(1, prefs.aiWordsSentToday(TODAY.toEpochDay()))

        val prompt = provider.requests.single().prompt
        assertTrue(prompt.contains(SOURCE_TEXT))
        assertTrue(prompt.contains("Wirtschaft"))
    }

    // --- as-is path ----------------------------------------------------------

    @Test
    fun `linked sentence is kept as-is without a provider call and stays unconsumed`() {
        val linked = addSentence(LINKED_TEXT, publishedAt = 5_000L)
        sentences.linkWords(mapOf(linked.id to listOf(4_243L)))
        writeWords(listOf(word(id = 4_243L, text = "Katze", context = WEAK_CONTEXT)))
        val provider = ScriptedProvider()

        val summary = runStep(provider)

        assertEquals(1, summary.asIsFilled)
        val filled = storedWord(4_243L)
        assertEquals(LINKED_TEXT, filled.context)
        assertEquals(ContextSource.AUTO, filled.contextSource)

        val recorded = storedSentences()
        assertEquals(1, recorded.size)
        with(recorded.single()) {
            assertEquals(4_243L, wordId)
            assertEquals("", model)
            assertEquals(FIXED_CLOCK, createdAt)
            assertEquals(linked.id, sourceSentenceId)
            assertEquals(WEAK_CONTEXT, originalContext)
        }

        assertFalse(sentences.all().single { it.id == linked.id }.consumed)
        assertEquals(0, provider.calls)
        assertEquals(0, prefs.aiWordsSentToday(TODAY.toEpochDay()))
    }

    // --- eligibility ---------------------------------------------------------

    @Test
    fun `a capped word is untouched while a zero-card word in the same run is rewritten`() {
        // The source contains the zero-card word "Katze", so its rewrite passes the
        // containment guard; the capped word "Regierung" is excluded before any
        // sentence lookup, so it gets no job and no call.
        val source = addSentence(LINKED_TEXT, publishedAt = 5_000L)
        writeWords(
            listOf(
                word(id = 5_151L, text = "Regierung", context = WEAK_CONTEXT),
                word(id = 6_161L, text = "Katze", context = WEAK_CONTEXT),
            ),
        )
        addPracticeCards(5_151L, WordNeedRanker.PRACTICE_CAP)
        val provider = ScriptedProvider(
            AiResult.Ok("""{"sentence": "$LINKED_TEXT"}""", AiUsage(10, 20), 5L),
        )

        runStep(provider)

        assertEquals(1, provider.calls)
        val capped = storedWord(5_151L)
        assertEquals(WEAK_CONTEXT, capped.context)
        assertEquals(ContextSource.CAPTURED, capped.contextSource)
        assertTrue(storedSentences().none { it.wordId == 5_151L })

        val filled = storedWord(6_161L)
        assertEquals(LINKED_TEXT, filled.context)
        assertEquals(ContextSource.AUTO, filled.contextSource)
        assertTrue(storedSentences().any { it.wordId == 6_161L && it.model == RUN_MODEL })
        assertTrue(sentences.all().single { it.id == source.id }.consumed)
    }

    @Test
    fun `a user-edited word is untouched and gets no provider call`() {
        addSentence(SOURCE_TEXT, publishedAt = 5_000L)
        writeWords(
            listOf(
                word(
                    id = 7_171L,
                    text = "Wirtschaft",
                    context = WEAK_CONTEXT,
                    contextSource = ContextSource.USER,
                ),
            ),
        )
        val provider = ScriptedProvider()

        runStep(provider)

        assertEquals(0, provider.calls)
        val userWord = storedWord(7_171L)
        assertEquals(WEAK_CONTEXT, userWord.context)
        assertEquals(ContextSource.USER, userWord.contextSource)
        assertTrue(storedSentences().isEmpty())
        assertFalse(sentences.all().single().consumed)
        assertEquals(0, prefs.aiWordsSentToday(TODAY.toEpochDay()))
    }

    @Test
    fun `null rewriter still fills as-is but records no rewrite`() {
        val linked = addSentence(LINKED_TEXT, publishedAt = 5_000L)
        sentences.linkWords(mapOf(linked.id to listOf(7_181L)))
        writeWords(
            listOf(
                word(id = 7_181L, text = "Katze", context = WEAK_CONTEXT),
                word(id = 7_182L, text = "Wirtschaft", context = WEAK_CONTEXT),
            ),
        )

        val summary = runStep(provider = null)

        assertEquals(1, summary.asIsFilled)
        val filled = storedWord(7_181L)
        assertEquals(LINKED_TEXT, filled.context)
        assertEquals(ContextSource.AUTO, filled.contextSource)

        // No rewriter: the weak-context word without a link waits, no records.
        val waiting = storedWord(7_182L)
        assertEquals(WEAK_CONTEXT, waiting.context)
        assertEquals(ContextSource.CAPTURED, waiting.contextSource)

        val recorded = storedSentences()
        assertEquals(1, recorded.size)
        assertEquals("", recorded.single().model)
        assertFalse(sentences.all().single { it.id == linked.id }.consumed)
    }

    // --- backfill eligibility (worker's filter, pure helper) ------------------

    @Test
    fun `backfill skips a capped blank word for links and fills while a zero-card word gets both`() {
        writeWords(
            listOf(
                word(id = 5_151L, text = "Regierung", context = ""),
                word(id = 6_161L, text = "Katze", context = ""),
            ),
        )
        addPracticeCards(5_151L, WordNeedRanker.PRACTICE_CAP)
        val counts = practiceItems.all().groupingBy { it.wordId }.eachCount()
        val fresh = addSentence(
            "Die Regierung und die Katze teilen sich den Garten in der Stadt.",
            publishedAt = 6_000L,
        )
        val matcher = SentenceWordMatcher { null }

        val plan = NewsBackfill.plan(NewsBackfill.eligibleWords(wordsStore.all(), counts), listOf(fresh), matcher)
        NewsBackfill.applyPlan(plan, sentences, wordsStore)

        // The capped word: still blank, and not linked to the fresh sentence.
        val capped = storedWord(5_151L)
        assertEquals("", capped.context)
        assertTrue(sentences.all().single { it.id == fresh.id }.wordIds.none { it == 5_151L })

        // The zero-card word: linked and filled.
        val filled = storedWord(6_161L)
        assertTrue(sentences.all().single { it.id == fresh.id }.wordIds.contains(6_161L))
        assertTrue(filled.context.isNotBlank())
    }
}
