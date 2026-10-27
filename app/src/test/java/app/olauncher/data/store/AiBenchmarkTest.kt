package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Canned-fixture tests for the deterministic parts of the benchmark harness
 * (AiBenchmark.kt, #600): fixture shape, cost math, the grounding rule, judge
 * score parsing and the summary/threshold logic. No network here — the live run
 * is a script-style execution of AiBenchmark itself, gated on a key.
 */
class AiBenchmarkTest {

    private fun word(text: String, context: String) = Word(
        id = 1, text = text, context = context, dueDay = 0, addedAt = 0,
    )

    // ---- Fixture ------------------------------------------------------------

    @Test
    fun `fixture has 20 words with short contexts from the recorded article`() {
        val fixture = AiBenchLogic.loadFixture()
        assertEquals(20, fixture.words.size)
        assertEquals((1L..20L).toList(), fixture.words.map { it.id })
        assertTrue("article URL is recorded", fixture.url.startsWith("https://www.tagesschau.de/"))
        assertTrue("article date is recorded", fixture.date.isNotBlank())
        for (word in fixture.words) {
            assertTrue("context of '${word.text}' is saved", word.context.isNotBlank())
            assertTrue("context of '${word.text}' stays under the app limit", word.context.length <= 400)
        }
    }

    @Test
    fun `generation prompt carries the fixture words and the contract schema`() {
        val fixture = AiBenchLogic.loadFixture()
        val prompt = AiBenchLogic.buildGenerationPrompt(fixture.words)
        assertTrue(prompt.contains("word_id 1: ${fixture.words[0].text}"))
        assertTrue(prompt.contains("word_id 20: ${fixture.words[19].text}"))
        assertTrue(prompt.contains("Respond with one JSON object"))
        assertTrue(prompt.contains(DrillItem.GAP_MARKER))
    }

    // ---- Cost math ----------------------------------------------------------

    @Test
    fun `cost is usage tokens times the model price`() {
        val price = AiBenchLogic.PRICES.getValue("deepseek-v4-flash-0731")
        assertEquals(0.0000225, AiBenchLogic.costEur(price, 100, 50), 1e-9)
    }

    @Test
    fun `prices are recorded for every candidate and the judge`() {
        for (model in AiBenchLogic.CANDIDATE_MODELS) {
            assertTrue("price for $model", AiBenchLogic.PRICES.containsKey(model))
        }
        assertTrue(AiBenchLogic.PRICES.containsKey("glm-5.3"))
    }

    @Test
    fun `the budget estimate scales with prompt size and output tokens`() {
        val price = AiBenchLogic.PRICES.getValue("glm-5.3-flash")
        val small = AiBenchLogic.estimateCostEur(4_000, 0, price)
        val big = AiBenchLogic.estimateCostEur(8_000, 0, price)
        assertEquals(2.0, big / small, 1e-9)
        assertTrue(AiBenchLogic.estimateCostEur(0, 1_000_000, price) == price.outputPerMillion)
    }

    // ---- Grounding ----------------------------------------------------------

    @Test
    fun `an item reusing context content is grounded`() {
        val word = word("ausbauen", "Die EU-Kommission will Frontex massiv ausbauen.")
        assertTrue(
            AiBenchLogic.isGrounded(
                word,
                prompt = "Die EU-Kommission will Frontex massiv ___.",
                answer = "ausbauen",
                choices = listOf("ausbauen", "abbauen", "anbauen"),
            ),
        )
    }

    @Test
    fun `an inflected answer of the saved word counts as grounded`() {
        val word = word("anwachsen", "Das Personal soll auf 6.000 anwachsen.")
        assertTrue(
            AiBenchLogic.isGrounded(
                word,
                prompt = "Das Personal soll auf 6.000 ___.",
                answer = "anwächst",
                choices = listOf("anwächst", "anwuchsen"),
            ),
        )
    }

    @Test
    fun `an item with no context overlap and an unrelated answer is not grounded`() {
        val word = word("ausbauen", "Die EU-Kommission will Frontex massiv ausbauen.")
        assertFalse(
            AiBenchLogic.isGrounded(
                word,
                prompt = "Der Koch muss die Suppe gut ___.",
                answer = "umrühren",
                choices = listOf("umrühren", "abschmecken", "servieren"),
            ),
        )
    }

    @Test
    fun `short function words alone do not make an item grounded`() {
        val word = word("eigenmächtig", "Frontex darf dabei nicht eigenmächtig handeln.")
        assertFalse(
            AiBenchLogic.isGrounded(
                word,
                prompt = "Das ist ___ nicht erlaubt.",
                answer = "auch",
                choices = listOf("auch", "nur"),
            ),
        )
    }

    // ---- Judge scores -------------------------------------------------------

    @Test
    fun `judge scores are parsed and clamped to 0-2`() {
        val scores = AiBenchLogic.parseJudgeScores("""{"scores": [{"id": 1, "score": 2}, {"id": 2, "score": 0}]}""")
        assertEquals(2, scores[1])
        assertEquals(0, scores[2])
    }

    @Test
    fun `out-of-range and malformed judge entries are dropped`() {
        val scores = AiBenchLogic.parseJudgeScores(
            """{"scores": [{"id": 1, "score": 5}, {"id": 2, "score": "x"}, {"id": 3, "score": 1}]}""",
        )
        assertFalse(scores.containsKey(1))
        assertFalse(scores.containsKey(2))
        assertEquals(1, scores[3])
    }

    @Test
    fun `a truncated judge response yields no scores instead of crashing`() {
        assertEquals(emptyMap<Int, Int>(), AiBenchLogic.parseJudgeScores("""{"scores": [{"id": 1"""))
        assertEquals(emptyMap<Int, Int>(), AiBenchLogic.parseJudgeScores("server error"))
    }

    @Test
    fun `the judge prompt asks for the compact score shape and lists the items`() {
        val fixture = AiBenchLogic.loadFixture()
        val row = BenchRow(
            run = 1, judgeId = 7, word = fixture.words[0].text, type = "cloze",
            prompt = "Die EU-Kommission will Frontex massiv ___.", answer = "ausbauen",
            contractValid = true, grounded = true, groundedOverride = "", correctness = "",
            tokensIn = 0, tokensOut = 0, costEur = 0.0, latencyMs = 0, errorKind = "",
        )
        val prompt = AiBenchLogic.buildJudgePrompt(fixture, listOf(row))
        assertTrue(prompt.contains("\"scores\": [{\"id\": 1, \"score\": 2}]"))
        assertTrue(prompt.contains("id 7 | word: ausbauen"))
        assertTrue(prompt.contains(fixture.words[0].context))
    }

    // ---- Summary ------------------------------------------------------------

    private fun row(valid: Boolean, grounded: Boolean = valid, correctness: String = "") = BenchRow(
        run = 1, judgeId = 1, word = "ausbauen", type = "cloze", prompt = "p", answer = "a",
        contractValid = valid, grounded = grounded, groundedOverride = "", correctness = correctness,
        tokensIn = 0, tokensOut = 0, costEur = 0.0, latencyMs = 0, errorKind = "",
    )

    @Test
    fun `summary computes validity grounding and the verdict against the thresholds`() {
        // 2 calls x 20 words x 3 items = 120 expected; 118 valid (98.3%), 110 grounded (93.2%).
        val rows = List(110) { row(valid = true, grounded = true, correctness = "2") } +
            List(8) { row(valid = true, grounded = false, correctness = "2") } +
            List(2) { row(valid = false, grounded = false, correctness = "") }
        val summary = AiBenchLogic.summarize(
            model = "glm-5.3-flash", runsPlanned = 2, totalCalls = 2, failedCalls = 0,
            rows = rows, latenciesMs = listOf(900L, 1_200L), costEur = 0.00012, budgetAborted = false,
        )
        assertEquals(98.3, summary.validityPct, 0.1)
        assertEquals(93.2, summary.groundingPct, 0.1)
        assertEquals(2.0, summary.meanCorrectness!!, 1e-9)
        assertEquals("PASS", summary.verdict)
        assertEquals(900L, summary.p50LatencyMs)
    }

    @Test
    fun `a model below any threshold fails`() {
        val base = List(60) { row(valid = true, grounded = true, correctness = "2") }
        val weakCorrectness = AiBenchLogic.summarize(
            model = "m", runsPlanned = 1, totalCalls = 1, failedCalls = 0,
            rows = base.map { it.copy(correctness = "1") }, latenciesMs = listOf(1L),
            costEur = 0.0, budgetAborted = false,
        )
        assertEquals("FAIL", weakCorrectness.verdict)
        val weakValidity = AiBenchLogic.summarize(
            model = "m", runsPlanned = 1, totalCalls = 1, failedCalls = 0,
            rows = base.dropLast(5), latenciesMs = listOf(1L), // 55 of 60 valid = 91.7%
            costEur = 0.0, budgetAborted = false,
        )
        assertEquals("FAIL", weakValidity.verdict)
        val weakGrounding = AiBenchLogic.summarize(
            model = "m", runsPlanned = 1, totalCalls = 1, failedCalls = 0,
            rows = base.mapIndexed { i, r -> if (i < 8) r.copy(grounded = false) else r }, // 86.7%
            latenciesMs = listOf(1L), costEur = 0.0, budgetAborted = false,
        )
        assertEquals("FAIL", weakGrounding.verdict)
    }

    @Test
    fun `an unscored model fails the correctness threshold rather than passing silently`() {
        val rows = List(60) { row(valid = true, grounded = true, correctness = "") }
        val summary = AiBenchLogic.summarize(
            model = "m", runsPlanned = 1, totalCalls = 1, failedCalls = 0,
            rows = rows, latenciesMs = listOf(1L), costEur = 0.0, budgetAborted = false,
        )
        assertNull(summary.meanCorrectness)
        assertEquals("FAIL", summary.verdict)
    }

    @Test
    fun `the cheapest passing model wins regardless of order`() {
        val pass = listOf("a", "b", "c").mapIndexed { i, m ->
            AiBenchLogic.summarize(
                model = m, runsPlanned = 1, totalCalls = 1, failedCalls = 0,
                rows = List(60) { row(valid = true, correctness = "2") }, latenciesMs = listOf(1L),
                costEur = 0.0001 * (i + 1), budgetAborted = false,
            )
        } + AiBenchLogic.summarize(
            model = "cheap_but_failing", runsPlanned = 1, totalCalls = 1, failedCalls = 0,
            rows = List(60) { row(valid = true, correctness = "0") }, latenciesMs = listOf(1L),
            costEur = 0.0, budgetAborted = false,
        )
        assertEquals("a", AiBenchLogic.cheapestPassing(pass))
    }

    // ---- CSV ----------------------------------------------------------------

    @Test
    fun `csv fields with commas quotes and newlines are escaped and round-trip`() {
        val escaped = AiBenchLogic.csvEscape("Er sagte: \"hallo, welt\"\nZeile 2")
        assertTrue(escaped.startsWith("\""))
        val fields = AiBenchLogic.splitCsv("1,2,$escaped,4")
        assertEquals(listOf("1", "2", "Er sagte: \"hallo, welt\"\nZeile 2", "4"), fields)
    }

    @Test
    fun `a bench row round-trips through its csv line`() {
        val row = BenchRow(
            run = 2, judgeId = 9, word = "Reserve", type = "cloze",
            prompt = "Die ständige ___, die sich zusammensetzt, soll anwachsen.", answer = "Reserve",
            contractValid = true, grounded = true, groundedOverride = "", correctness = "2",
            tokensIn = 512, tokensOut = 128, costEur = 0.000123, latencyMs = 1_234, errorKind = "",
        )
        val fields = AiBenchLogic.splitCsv(row.csvLine())
        assertEquals("Reserve", fields[2])
        assertEquals("true", fields[6])
        assertEquals("2", fields[9])
        assertEquals("0.000123", fields[12])
        assertEquals("1234", fields[13])
    }

    @Test
    fun `summary rows round-trip through csv`() {
        val summary = AiBenchLogic.summarize(
            model = "gemma-4-31b", runsPlanned = 3, totalCalls = 3, failedCalls = 1,
            rows = List(60) { row(valid = true, correctness = "2") }, latenciesMs = listOf(100L, 200L, 300L),
            costEur = 0.00045, budgetAborted = false,
        )
        val parsed = AiBenchLogic.SummaryRow.parse(summary.csvLine)
        assertEquals(summary.model, parsed.model)
        assertEquals(summary.calls, parsed.calls)
        assertEquals(summary.failedCalls, parsed.failedCalls)
        assertEquals(summary.verdict, parsed.verdict)
    }
}
