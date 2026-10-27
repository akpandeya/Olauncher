package app.olauncher.data.store

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.ceil

/**
 * The 9.7 model benchmark harness (#600), per docs/design/ai-practice.md "Benchmark".
 * Runs in the JVM test source set so it can reuse the real [PracticeContract] and
 * [OpenAiCompatibleProvider] classes; it is NOT part of the app. With no key in the
 * environment the test skips; with one it runs the live benchmark and writes
 * `app/build/ai-bench/<model>.csv` (one row per item) plus `summary.csv`.
 *
 * Setup: fixture of 20 saved words with their context sentences from one real German
 * news article (app/src/test/resources/ai_bench/words.json). Candidates and per-token
 * prices: [AiBenchLogic.PRICES]. Scoring: contract validity and grounding are checked
 * deterministically ([AiBenchLogic]); German correctness is a PROXY score from the
 * judge model [JUDGE_MODEL] via the same OpenAI-compatible endpoint, pending
 * native-speaker owner review.
 *
 * Key handling: read from the environment only (`AI_BENCH_KEY`, falling back to
 * `MELIOUS_API_KEY`) — never printed, logged, committed or written to a file. Budget
 * guard: estimated cost is logged per call before it is made; a model whose running
 * total would exceed the cap (default EUR 1) is aborted mid-run.
 */
class AiBenchmark {

    @Test
    fun `run the live benchmark over the fixture when a key is set`() {
        val key = keyFromEnv()
        Assume.assumeTrue(
            "No AI_BENCH_KEY or MELIOUS_API_KEY in the environment; live benchmark skipped",
            key != null,
        )
        runBenchmark(key!!)
    }

    /** The key comes from the environment at run time only; it is never logged. */
    private fun keyFromEnv(): String? =
        System.getenv("AI_BENCH_KEY") ?: System.getenv("MELIOUS_API_KEY")

    private fun runBenchmark(key: String) {
        val fixture = AiBenchLogic.loadFixture()
        val models = System.getenv("AI_BENCH_MODELS")
            ?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }
            ?: AiBenchLogic.CANDIDATE_MODELS
        val runs = System.getenv("AI_BENCH_RUNS")?.toIntOrNull() ?: 3
        val capEur = System.getenv("AI_BENCH_CAP_EUR")?.toDoubleOrNull() ?: 1.0
        val outDir = outDir().apply { mkdirs() }

        var spentEur = 0.0
        var outOfCredits = false
        val summaries = mutableListOf<String>()

        for (model in models) {
            val price = AiBenchLogic.PRICES[model]
            if (price == null) {
                println("bench: no recorded price for '$model' — skipped")
                continue
            }
            println("bench: model $model, $runs run(s) planned, price in ${price.inputPerMillion} out ${price.outputPerMillion} EUR/M, budget cap $capEur EUR")

            val rows = mutableListOf<BenchRow>()
            val latencies = mutableListOf<Long>()
            var failedCalls = 0
            var totalCalls = 0
            var modelCostEur = 0.0
            var budgetAborted = false
            val prompt = AiBenchLogic.buildGenerationPrompt(fixture.words)

            run@ for (run in 1..runs) {
                val estimate = AiBenchLogic.estimateCostEur(prompt, GEN_MAX_TOKENS, price)
                println("bench: model $model run $run estimated cost %.5f EUR (spent so far %.5f EUR)".format(estimate, spentEur))
                if (spentEur + estimate > capEur) {
                    println("bench: model $model aborted — running total would exceed the $capEur EUR cap")
                    budgetAborted = true
                    break@run
                }
                totalCalls++
                val start = System.nanoTime()
                val result = runBlocking {
                    benchProvider(model, key).complete(AiRequest(prompt = prompt, maxTokens = GEN_MAX_TOKENS))
                }
                val latencyMs = (System.nanoTime() - start) / 1_000_000
                when (result) {
                    is AiResult.Error -> {
                        failedCalls++
                        println("bench: model $model run $run failed (${result.kind.name}) — recorded, moving on")
                        rows += BenchRow(
                            run = run, judgeId = null, word = "RUN_FAILED", type = "", prompt = "", answer = "",
                            contractValid = false, grounded = false, groundedOverride = "", correctness = "",
                            tokensIn = 0, tokensOut = 0, costEur = 0.0, latencyMs = latencyMs,
                            errorKind = result.kind.name,
                        )
                        if (result.kind == AiResult.ErrorKind.BILLING) {
                            outOfCredits = true
                            break@run
                        }
                    }
                    is AiResult.Ok -> {
                        latencies += latencyMs
                        val cost = AiBenchLogic.costEur(price, result.usage.inputTokens, result.usage.outputTokens)
                        spentEur += cost
                        modelCostEur += cost
                        rows += scoredRows(fixture, model, run, result, cost)
                    }
                }
            }

            // Proxy judge: one stronger-model call over this model's whole output set.
            val judgeRows = rows.filter { it.judgeId != null }
            if (judgeRows.isNotEmpty()) {
                judge(fixture, key, judgeRows, capEur, spentEur, model)?.let { judgeCost ->
                    spentEur += judgeCost
                    modelCostEur += judgeCost
                }
            }

            val summary = AiBenchLogic.summarize(
                model = model, runsPlanned = runs, totalCalls = totalCalls, failedCalls = failedCalls,
                rows = rows, latenciesMs = latencies, costEur = modelCostEur, budgetAborted = budgetAborted,
            )
            summaries += summary.csvLine
            File(outDir, "$model.csv").writeText(rows.joinToString("\n", prefix = AiBenchLogic.CSV_HEADER + "\n") { it.csvLine() })
            println(summary.report)
            if (outOfCredits) {
                println("bench: out of credits (BILLING) — stopping the benchmark early")
                break
            }
        }

        File(outDir, "summary.csv").writeText(summaries.joinToString("\n", prefix = AiBenchLogic.SUMMARY_HEADER + "\n") { it + "\n" })
        val verdicts = summaries.map { AiBenchLogic.SummaryRow.parse(it) }.filter { !it.budgetAborted }
        println("bench: recommended default = " + (AiBenchLogic.cheapestPassing(verdicts) ?: "none passed"))
        println("bench: correctness is a glm-5.3 proxy score, pending native-speaker owner review")
    }

    /** Parses one successful generation response and turns it into per-item rows. */
    private fun scoredRows(
        fixture: AiBenchLogic.Fixture,
        model: String,
        run: Int,
        result: AiResult.Ok,
        callCostEur: Double,
    ): List<BenchRow> {
        val parsed = PracticeContract.parse(
            json = result.text,
            requestedWords = fixture.words,
            model = model,
            source = PRACTICE_SOURCE,
        )
        val rows = mutableListOf<BenchRow>()
        val perWord = parsed.items.groupBy { it.wordId }
        if (parsed.rejected.isNotEmpty()) {
            println("bench: model $model run $run rejected items: " + parsed.rejected.take(5).joinToString(" | "))
        }
        var nextJudgeId = (lastJudgeId + 1)
        for (word in fixture.words) {
            // tokens/cost are per call and shared by the 20 words of the run (documented).
            val share = callCostEur / fixture.words.size
            val pool = perWord[word.id].orEmpty()
            for (index in 0 until PracticeContract.ITEMS_PER_WORD) {
                val item = pool.getOrNull(index)
                rows += BenchRow(
                    run = run,
                    judgeId = nextJudgeId++,
                    word = word.text,
                    type = item?.type?.id ?: "",
                    prompt = item?.prompt ?: "",
                    answer = item?.answer ?: "",
                    contractValid = item != null,
                    grounded = item != null && AiBenchLogic.isGrounded(word, item.prompt, item.answer, item.choices),
                    groundedOverride = "",
                    correctness = "",
                    tokensIn = result.usage.inputTokens,
                    tokensOut = result.usage.outputTokens,
                    costEur = share,
                    latencyMs = result.latencyMs,
                    errorKind = if (item == null) "MISSING_OR_INVALID" else "",
                )
            }
        }
        lastJudgeId = nextJudgeId - 1
        return rows
    }

    /**
     * One judge call per candidate's output set, scoring 0-2 per item. Returns the
     * judge cost, or null when the judge failed (correctness stays blank). The judge
     * uses the same OpenAI-compatible endpoint with [JUDGE_MODEL]; its key handling
     * is identical. Rubric: 2 = natural, correct German; 1 = minor problems;
     * 0 = wrong or ungrammatical.
     */
    private fun judge(
        fixture: AiBenchLogic.Fixture,
        key: String,
        rows: List<BenchRow>,
        capEur: Double,
        spentEur: Double,
        model: String,
    ): Double? {
        val price = AiBenchLogic.PRICES.getValue(JUDGE_MODEL)
        val prompt = AiBenchLogic.buildJudgePrompt(fixture, rows)
        val estimate = AiBenchLogic.estimateCostEur(prompt.length, JUDGE_OUT_TOKENS, price)
        println("bench: judge for $model estimated cost %.5f EUR".format(estimate))
        if (spentEur + estimate > capEur) {
            println("bench: judge for $model aborted — would exceed the $capEur EUR cap")
            return null
        }
        val result = runBlocking {
            benchProvider(JUDGE_MODEL, key).complete(AiRequest(prompt = prompt, maxTokens = JUDGE_OUT_TOKENS))
        }
        if (result !is AiResult.Ok) {
            println("bench: judge for $model failed — correctness left blank")
            return null
        }
        val scores = AiBenchLogic.parseJudgeScores(result.text)
        val cost = AiBenchLogic.costEur(price, result.usage.inputTokens, result.usage.outputTokens)
        var matched = 0
        for (row in rows) {
            val id = row.judgeId ?: continue
            val score = scores[id]
            if (score != null) {
                row.correctness = score.toString()
                matched++
            }
        }
        println("bench: judge for $model scored $matched of ${rows.size} items")
        return cost
    }

    /** Output base per the acceptance: `app/build/ai-bench/` under the project root. */
    private fun outDir(): File {
        val userDir = File(System.getProperty("user.dir"))
        return if (userDir.name == "app") File(userDir, "build/ai-bench") else File(userDir, "app/build/ai-bench")
    }

    /**
     * The real adapter with a raised read timeout: one generation call covers 20
     * words x 3 items and runs past the adapter's 60s budget on the slower
     * candidate models (every timeout counted as a NETWORK failure in the first
     * live run). Harness-only — the app adapter and its timeouts stay untouched.
     */
    private fun benchProvider(model: String, key: String): AiProvider =
        object : OpenAiCompatibleProvider(
            AiProviderConfig(AiProviders.MELIOUS, BASE_URL, model),
            KeySource { key },
        ) {
            override fun openConnection(url: URL): HttpURLConnection =
                LongTimeoutConnection(url.openConnection() as HttpURLConnection)
        }

    private var lastJudgeId = 0

    companion object {
        const val BASE_URL = "https://api.melious.ai"
        const val JUDGE_MODEL = "glm-5.3"
        const val PRACTICE_SOURCE = "benchmark"
        /** 20 words x 3 items overflow the 4k default: the first live run truncated. */
        const val GEN_MAX_TOKENS = 16_384
        const val JUDGE_OUT_TOKENS = 8_192
    }
}

/** One CSV row of the benchmark output; mutable only for the judge's correctness fill. */
data class BenchRow(
    val run: Int,
    val judgeId: Int?,
    val word: String,
    val type: String,
    val prompt: String,
    val answer: String,
    val contractValid: Boolean,
    val grounded: Boolean,
    val groundedOverride: String,
    var correctness: String,
    val tokensIn: Int,
    val tokensOut: Int,
    val costEur: Double,
    val latencyMs: Long,
    val errorKind: String,
) {
    fun csvLine(): String = listOf(
        run.toString(),
        judgeId?.toString() ?: "",
        word, type, prompt, answer,
        contractValid.toString(),
        grounded.toString(),
        groundedOverride,
        correctness,
        tokensIn.toString(),
        tokensOut.toString(),
        "%.6f".format(costEur),
        latencyMs.toString(),
        errorKind,
    ).joinToString(",") { AiBenchLogic.csvEscape(it) }
}

/**
 * The deterministic part of the harness: fixture loading, prices, cost math, the
 * grounding rule, judge-response parsing and summarizing. Context-free and
 * unit-tested with canned fixtures (AiBenchmarkTest) — no network here.
 */
object AiBenchLogic {

    /** The three 9.7 candidates (owner decision, 2026-10-27), in benchmark order. */
    val CANDIDATE_MODELS = listOf("glm-5.3-flash", "deepseek-v4-flash-0731", "gemma-4-31b")

    /** Per-million-euro prices from https://melious.ai/hub/models, read 2026-10-27. */
    val PRICES = mapOf(
        "glm-5.3-flash" to ModelPrice(0.10, 0.40),
        "deepseek-v4-flash-0731" to ModelPrice(0.10, 0.25),
        "gemma-4-31b" to ModelPrice(0.10, 0.30),
        // The proxy judge is not a candidate; its cost is still part of the budget.
        "glm-5.3" to ModelPrice(1.00, 3.00),
    )

    /** Rubric thresholds (owner, 2026-10-27): cheapest model clearing all three wins. */
    const val THRESHOLD_VALIDITY_PCT = 95.0
    const val THRESHOLD_CORRECTNESS = 1.8
    const val THRESHOLD_GROUNDING_PCT = 90.0

    const val CSV_HEADER = "run,judge_id,word,type,prompt,answer,contract_valid,grounded,grounded_override,correctness,tokens_in,tokens_out,cost,latency_ms,error_kind"
    const val SUMMARY_HEADER = "model,runs,calls,failed_calls,failure_rate,validity_pct,grounding_pct,mean_correctness_proxy,cost_per_item_eur,total_cost_eur,p50_latency_ms,p95_latency_ms,verdict"

    /** Allowed drill types for generated practice (one CLOZE + two others per word). */
    val ALLOWED_TYPES = listOf(
        DrillType.CLOZE, DrillType.ARTICLES, DrillType.PREPOSITIONS, DrillType.PLURALS,
        DrillType.CASE_ENDINGS, DrillType.CONJUGATION, DrillType.WORD_ORDER,
    )

    /** ~4 chars per token; used only for the pre-run budget estimate, never for cost. */
    const val CHARS_PER_TOKEN = 4.0

    data class ModelPrice(val inputPerMillion: Double, val outputPerMillion: Double)

    data class Fixture(
        val url: String,
        val date: String,
        val words: List<Word>,
    )

    fun loadFixture(): Fixture {
        val text = AiBenchLogic::class.java.classLoader?.getResourceAsStream(FIXTURE_PATH)?.bufferedReader()?.readText()
            ?: AiBenchLogic::class.java.getResourceAsStream("/$FIXTURE_PATH")?.bufferedReader()?.readText()
            ?: error("Fixture '$FIXTURE_PATH' not found")
        val root = JSONObject(text)
        val words = root.getJSONArray("words").let { array ->
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                Word(
                    id = obj.getLong("id"),
                    text = obj.getString("text"),
                    context = obj.optString("context"),
                    dueDay = 0L,
                    addedAt = 0L,
                )
            }
        }
        return Fixture(url = root.getString("url"), date = root.getString("date"), words = words)
    }

    fun buildGenerationPrompt(words: List<Word>): String =
        PracticeContract.buildPrompt(words, ALLOWED_TYPES)

    fun costEur(price: ModelPrice, tokensIn: Int, tokensOut: Int): Double =
        tokensIn / 1_000_000.0 * price.inputPerMillion + tokensOut / 1_000_000.0 * price.outputPerMillion

    /** Pre-run estimate: prompt chars at ~4 chars/token, expected output tokens. */
    fun estimateCostEur(promptChars: Int, outTokens: Int, price: ModelPrice): Double =
        promptChars / CHARS_PER_TOKEN / 1_000_000.0 * price.inputPerMillion +
            outTokens / 1_000_000.0 * price.outputPerMillion

    fun estimateCostEur(prompt: String, outTokens: Int, price: ModelPrice): Double =
        estimateCostEur(prompt.length, outTokens, price)

    // ---- Grounding ----------------------------------------------------------

    /**
     * The deterministic grounding rule (documented in docs/design/ai-model-decision.md):
     * an item is grounded when
     * (a) the answer is the saved word itself or an inflected form of it (shared stem
     * of >= 4 chars covering at least half of the word, German umlauts folded:
     * ä→a, ö→o, ü→u, ß→ss), or
     * (b) the item's own text — the prompt with the gap filled by the answer, plus
     * the answer and the choices — shares with the word's saved context sentence at
     * least one content token (lowercased, punctuation stripped, length >= 4) that is
     * itself at least 6 characters long. Short shared tokens ("nicht", "dabei", …)
     * do not count on their own: almost any German sentence contains them.
     */
    fun isGrounded(word: Word, prompt: String, answer: String, choices: List<String>): Boolean {
        val stem = normalizeGerman(answer.trim()).commonPrefixWith(normalizeGerman(word.text.trim())).length
        if (stem >= 4 && stem * 2 >= word.text.trim().length) return true
        val itemTokens = contentTokens(prompt.replace(DrillItem.GAP_MARKER, answer) + " " + answer + " " + choices.joinToString(" "))
        val contextTokens = contentTokens(word.context)
        return itemTokens.intersect(contextTokens).any { it.length >= 6 }
    }

    /** Lowercased, punctuation-stripped tokens of length >= 4, umlauts folded. */
    fun contentTokens(text: String): Set<String> {
        return normalizeGerman(text)
            .split(whitespaceAndPunctuation)
            .filter { it.length >= MIN_CONTENT_TOKEN }
            .toSet()
    }

    /** Folds German umlauts so inflected forms share a stem across ä/ae, ß/ss. */
    fun normalizeGerman(text: String): String = text.lowercase()
        .replace("ä", "a").replace("ö", "o").replace("ü", "u").replace("ß", "ss")

    private val whitespaceAndPunctuation = Regex("[\\s.,;:!?\"'»«()\\[\\]—–\\-]+")
    private const val MIN_CONTENT_TOKEN = 4

    // ---- Judge response -----------------------------------------------------

    /** Parses `{"scores":[{"id":1,"score":2},…]}`; unknown ids, bad scores and
     *  truncated JSON are ignored — missing items simply stay unscored. Markdown
     *  fences and surrounding prose are stripped before parsing (some judge models
     *  wrap JSON in ``` fences even in JSON mode). */
    fun parseJudgeScores(text: String): Map<Int, Int> {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return emptyMap()
        val root = try {
            JSONObject(text.substring(start, end + 1))
        } catch (e: Exception) {
            return emptyMap()
        }
        val array = root.optJSONArray("scores") ?: return emptyMap()
        val scores = mutableMapOf<Int, Int>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.opt("id") as? Number ?: continue
            val score = obj.opt("score") as? Number ?: continue
            if (score.toInt() in 0..2) scores[id.toInt()] = score.toInt()
        }
        return scores
    }

    fun buildJudgePrompt(fixture: Fixture, rows: List<BenchRow>): String {
        val builder = StringBuilder()
        builder.append("You review AI-generated German practice items for correctness.\n")
        builder.append("Score every item from 0 to 2:\n")
        builder.append("2 = natural, grammatically correct German that fits the word and its context\n")
        builder.append("1 = understandable German with grammar, spelling or register problems\n")
        builder.append("0 = wrong, ungrammatical or nonsensical\n")
        builder.append("\n")
        builder.append("Answer with one JSON object and nothing else:\n")
        builder.append("{\"scores\": [{\"id\": 1, \"score\": 2}]}\n")
        builder.append("\n")
        builder.append("Words and saved context sentences:\n")
        for (word in fixture.words) {
            builder.append("- ${word.text}: ${word.context}\n")
        }
        builder.append("\nItems:\n")
        for (row in rows) {
            val id = row.judgeId
            if (id == null || !row.contractValid) continue
            builder.append("[id $id | word: ${row.word} | type: ${row.type}] prompt: ${row.prompt} | answer: ${row.answer}\n")
        }
        return builder.toString()
    }

    // ---- Summary ------------------------------------------------------------

    data class SummaryRow(
        val model: String,
        val runs: Int,
        val calls: Int,
        val failedCalls: Int,
        val failureRate: Double,
        val validityPct: Double,
        val groundingPct: Double,
        val meanCorrectness: Double?,
        val costPerItemEur: Double,
        val totalCostEur: Double,
        val p50LatencyMs: Long,
        val p95LatencyMs: Long,
        val verdict: String,
        val budgetAborted: Boolean,
    ) {
        val csvLine: String
            get() = listOf(
                model, runs.toString(), calls.toString(), failedCalls.toString(),
                "%.3f".format(failureRate),
                "%.1f".format(validityPct),
                "%.1f".format(groundingPct),
                meanCorrectness?.let { "%.2f".format(it) } ?: "",
                "%.6f".format(costPerItemEur),
                "%.6f".format(totalCostEur),
                p50LatencyMs.toString(), p95LatencyMs.toString(),
                verdict,
            ).joinToString(",") { csvEscape(it) }

        val report: String
            get() = "bench: $model — validity ${"%.1f".format(validityPct)}% (>= $THRESHOLD_VALIDITY_PCT), " +
                "grounding ${"%.1f".format(groundingPct)}% (>= $THRESHOLD_GROUNDING_PCT), " +
                "correctness proxy ${meanCorrectness?.let { "%.2f".format(it) } ?: "n/a"} of 2 (>= $THRESHOLD_CORRECTNESS), " +
                "EUR/item ${"%.6f".format(costPerItemEur)}, p50 ${p50LatencyMs}ms, p95 ${p95LatencyMs}ms, " +
                "failures $failedCalls/$calls, verdict $verdict"

        companion object {
            /** Inverse of [csvLine], tolerant of the optional correctness column. */
            fun parse(line: String): SummaryRow {
                val cols = splitCsv(line)
                fun col(i: Int) = cols.getOrElse(i) { "" }
                return SummaryRow(
                    model = col(0),
                    runs = col(1).toIntOrNull() ?: 0,
                    calls = col(2).toIntOrNull() ?: 0,
                    failedCalls = col(3).toIntOrNull() ?: 0,
                    failureRate = col(4).toDoubleOrNull() ?: 0.0,
                    validityPct = col(5).toDoubleOrNull() ?: 0.0,
                    groundingPct = col(6).toDoubleOrNull() ?: 0.0,
                    meanCorrectness = col(7).toDoubleOrNull(),
                    costPerItemEur = col(8).toDoubleOrNull() ?: 0.0,
                    totalCostEur = col(9).toDoubleOrNull() ?: 0.0,
                    p50LatencyMs = col(10).toLongOrNull() ?: 0L,
                    p95LatencyMs = col(11).toLongOrNull() ?: 0L,
                    verdict = col(12),
                    budgetAborted = false,
                )
            }
        }
    }

    fun summarize(
        model: String,
        runsPlanned: Int,
        totalCalls: Int,
        failedCalls: Int,
        rows: List<BenchRow>,
        latenciesMs: List<Long>,
        costEur: Double,
        budgetAborted: Boolean,
    ): SummaryRow {
        val expected = totalCalls * WORDS_PER_RUN * PracticeContract.ITEMS_PER_WORD
        val validItems = rows.count { it.contractValid }
        val validityPct = if (expected == 0) 0.0 else validItems * 100.0 / expected
        val grounded = rows.count { it.grounded || it.groundedOverride.equals("true", ignoreCase = true) }
        val groundingPct = if (validItems == 0) 0.0 else grounded * 100.0 / validItems
        val scored = rows.mapNotNull { it.correctness.toDoubleOrNull() }
        val meanCorrectness = if (scored.isEmpty()) null else scored.average()
        val verdict = when {
            budgetAborted -> "ABORTED_BUDGET"
            validityPct >= THRESHOLD_VALIDITY_PCT &&
                groundingPct >= THRESHOLD_GROUNDING_PCT &&
                (meanCorrectness ?: 0.0) >= THRESHOLD_CORRECTNESS -> "PASS"
            else -> "FAIL"
        }
        val sorted = latenciesMs.sorted()
        return SummaryRow(
            model = model,
            runs = runsPlanned,
            calls = totalCalls,
            failedCalls = failedCalls,
            failureRate = if (totalCalls == 0) 0.0 else failedCalls * 1.0 / totalCalls,
            validityPct = validityPct,
            groundingPct = groundingPct,
            meanCorrectness = meanCorrectness,
            costPerItemEur = if (validItems == 0) 0.0 else costEur / validItems,
            totalCostEur = costEur,
            p50LatencyMs = percentile(sorted, 0.50),
            p95LatencyMs = percentile(sorted, 0.95),
            verdict = verdict,
            budgetAborted = budgetAborted,
        )
    }

    /** Cheapest passing model by cost per item; ties broken by name for determinism. */
    fun cheapestPassing(rows: List<SummaryRow>): String? =
        rows.filter { it.verdict == "PASS" }
            .minWithOrNull(compareBy({ it.costPerItemEur }, { it.model }))
            ?.model

    /** Nearest-rank percentile of a sorted list; empty lists give 0. */
    fun percentile(sorted: List<Long>, p: Double): Long {
        if (sorted.isEmpty()) return 0
        val rank = ceil(p * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    // ---- CSV ----------------------------------------------------------------

    fun csvEscape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    /** Minimal CSV field split honouring double quotes; used by the summary parser. */
    fun splitCsv(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    fields += current.toString(); current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        fields += current.toString()
        return fields
    }

    const val WORDS_PER_RUN = 20
    const val FIXTURE_PATH = "ai_bench/words.json"
}

/**
 * Delegates to a real connection but pins the read timeout at [READ_TIMEOUT_MS]:
 * the adapter sets its 60s default through [setReadTimeout], which is ignored
 * here. Harness-only; the adapter code path is otherwise identical.
 */
private class LongTimeoutConnection(private val real: HttpURLConnection) : HttpURLConnection(real.url) {

    override fun setReadTimeout(timeoutMs: Int) {
        real.readTimeout = READ_TIMEOUT_MS
    }

    override fun getReadTimeout(): Int = real.readTimeout

    override fun setConnectTimeout(timeoutMs: Int) {
        real.connectTimeout = timeoutMs
    }

    override fun getConnectTimeout(): Int = real.connectTimeout

    override fun setRequestMethod(method: String) {
        real.requestMethod = method
    }

    override fun getRequestMethod(): String = real.requestMethod

    override fun setDoOutput(dooutput: Boolean) {
        real.doOutput = dooutput
    }

    override fun getDoOutput(): Boolean = real.doOutput

    override fun setRequestProperty(key: String, value: String) {
        real.setRequestProperty(key, value)
    }

    override fun getRequestProperty(key: String): String? = real.getRequestProperty(key)

    override fun getOutputStream(): OutputStream = real.outputStream

    override fun getInputStream(): InputStream = real.inputStream

    override fun getErrorStream(): InputStream? = real.errorStream

    override fun getResponseCode(): Int = real.responseCode

    override fun getHeaderField(name: String): String? = real.getHeaderField(name)

    override fun connect() = real.connect()

    override fun disconnect() = real.disconnect()

    override fun usingProxy(): Boolean = real.usingProxy()

    companion object {
        /** 60 items of German practice text can take minutes on slower models. */
        const val READ_TIMEOUT_MS = 300_000
    }
}
