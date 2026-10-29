package app.olauncher.data.store

import app.olauncher.data.Prefs
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume
import org.junit.Test

/**
 * Chat transport benchmark (#699): is a native `messages[]` request worth the
 * production interface change, or is the flattened single `prompt` string good
 * enough? Per docs/design/ai-model-decision.md "Chat transport (#699)" the two
 * variants send the SAME instructions, groundings, summary and transcript:
 *
 * - **Variant A** — one flattened prompt (labelled lines) through the real
 *   [OpenAiCompatibleProvider] with `AiRequest(jsonMode = false)` (chat is prose).
 * - **Variant B** — one `system` message (instructions + groundings + summary)
 *   plus alternating `user`/`assistant` turns, sent by a harness-only client that
 *   builds its own JSON body (the adapter's `encodeRequest` is final) and decodes
 *   with the public `OpenAiCompatibleProvider.decodeResponse`, so error mapping
 *   is identical. Neither variant sends `response_format`.
 *
 * Fixtures are 5 synthetic German chat scenarios
 * (app/src/test/resources/ai_chat_bench/scenarios.json); the loader rejects
 * incomplete or unsupported scenarios BEFORE any request is built. Grading is
 * deterministic ([AiChatBenchLogic]): SUBJECT-line compliance, follow-up reuse
 * of an earlier turn, failed-turn canary leak, tokens/cost/latency; reply
 * quality is a glm-5.3 proxy judge score (0-2), same endpoint as #600.
 *
 * Key handling is identical to [AiBenchmark]: read from the environment only
 * (`AI_BENCH_KEY`, falling back to `MELIOUS_API_KEY`) — never printed, logged,
 * committed or written to a file. Budget guard: the estimated cost is logged
 * before every call and the run stops before a call that would exceed the cap
 * (`AI_BENCH_CAP_EUR`, default EUR 0.30). Output: per-run CSVs plus
 * `summary.csv` under `app/build/ai-chat-bench/`.
 */
class AiChatBenchmark {

    @Test
    fun `run the live chat transport benchmark when a key is set`() {
        val key = keyFromEnv()
        Assume.assumeTrue(
            "No AI_BENCH_KEY or MELIOUS_API_KEY in the environment; live chat transport benchmark skipped",
            key != null,
        )
        runBenchmark(key!!)
    }

    /** The key comes from the environment at run time only; it is never logged. */
    private fun keyFromEnv(): String? =
        System.getenv("AI_BENCH_KEY") ?: System.getenv("MELIOUS_API_KEY")

    private fun runBenchmark(key: String) {
        // The fixture loader rejects incomplete or unsupported scenarios before
        // any request is built (acceptance: independent completeness check).
        val scenarios = AiChatBenchLogic.loadScenarios()
        val models = System.getenv("AI_BENCH_MODELS")
            ?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }
            ?: listOf(Prefs.AI_MODEL_DEFAULT)
        val runs = System.getenv("AI_BENCH_RUNS")?.toIntOrNull() ?: 3
        val capEur = System.getenv("AI_BENCH_CAP_EUR")?.toDoubleOrNull() ?: DEFAULT_CAP_EUR
        val outDir = outDir().apply { mkdirs() }

        var spentEur = 0.0
        val summaries = mutableListOf<AiChatBenchLogic.SummaryRow>()

        for (model in models) {
            val price = AiBenchLogic.PRICES[model]
            if (price == null) {
                println("chat-bench: no recorded price for '$model' — skipped")
                continue
            }
            println("chat-bench: model $model, $runs run(s) x ${scenarios.size} scenarios x 2 variants, budget cap $capEur EUR")
            val provider = benchProvider(model, key)

            for ((variant, label) in VARIANTS) {
                val variantRows = mutableListOf<ChatBenchRow>()
                var variantCostEur = 0.0
                var budgetAborted = false

                for (run in 1..runs) {
                    val runRows = mutableListOf<ChatBenchRow>()
                    scenario@ for (scenario in scenarios) {
                        // ---- Build the request; check the failed-turn canary first.
                        val built = when (variant) {
                            VARIANT_A -> {
                                val request = AiRequest(
                                    prompt = AiChatBenchLogic.buildFlattenedPrompt(scenario),
                                    jsonMode = false, // chat is prose (ask-ai-chat.md §Threads)
                                    maxTokens = CHAT_MAX_TOKENS,
                                )
                                BuiltRequest(
                                    body = provider.encodeRequest(request),
                                    prompt = request.prompt,
                                    inputChars = request.prompt.length,
                                )
                            }
                            else -> {
                                val messages = AiChatBenchLogic.buildMessages(scenario)
                                BuiltRequest(
                                    body = AiChatBenchLogic.encodeMessagesBody(model, messages, CHAT_MAX_TOKENS),
                                    prompt = null,
                                    inputChars = messages.sumOf { it.second.length },
                                )
                            }
                        }
                        val leakInBody = AiChatBenchLogic.leakedCanary(built.body, reply = null, scenario.canary)

                        // ---- Budget guard, before the call.
                        val estimate = AiBenchLogic.estimateCostEur(built.inputChars, ESTIMATE_OUT_TOKENS, price)
                        println(
                            "chat-bench: $model $variant run $run scenario ${scenario.id} " +
                                "estimated cost %.6f EUR (spent so far %.6f EUR)".format(estimate, spentEur),
                        )
                        if (spentEur + estimate > capEur) {
                            println("chat-bench: $model $variant aborted — the next call would exceed the $capEur EUR cap")
                            budgetAborted = true
                            break@scenario
                        }

                        // ---- The call.
                        val start = System.nanoTime()
                        val result = when (variant) {
                            VARIANT_A -> runBlocking {
                                provider.complete(
                                    AiRequest(
                                        prompt = built.prompt ?: error("variant A lost its prompt"),
                                        jsonMode = false,
                                        maxTokens = CHAT_MAX_TOKENS,
                                    ),
                                )
                            }
                            else -> postChat(model, key, built.body)
                        }
                        val latencyMs = (System.nanoTime() - start) / 1_000_000

                        val row = when (result) {
                            is AiResult.Error -> ChatBenchRow(
                                run = run, variant = variant, model = model, scenario = scenario.id,
                                subjectOk = "", followupOk = "", canaryLeak = leakInBody, judgeScore = "",
                                tokensIn = 0, tokensOut = 0, costEur = 0.0, latencyMs = latencyMs,
                                errorKind = result.kind.name + (result.errorCode?.let { ":$it" } ?: ""),
                            )
                            is AiResult.Ok -> {
                                val cost = AiBenchLogic.costEur(price, result.usage.inputTokens, result.usage.outputTokens)
                                spentEur += cost
                                variantCostEur += cost
                                val reply = result.text
                                val leak = AiChatBenchLogic.leakedCanary(built.body, reply, scenario.canary)
                                ChatBenchRow(
                                    run = run, variant = variant, model = model, scenario = scenario.id,
                                    subjectOk = if (scenario.kind == AiChatBenchLogic.KIND_SUBJECT_PROPOSAL) {
                                        AiChatBenchLogic.subjectOk(reply).toString()
                                    } else "",
                                    followupOk = if (scenario.expectedTokens.isNotEmpty()) {
                                        AiChatBenchLogic.usesEarlierTurn(reply, scenario.expectedTokens).toString()
                                    } else "",
                                    canaryLeak = leak,
                                    judgeScore = "", // filled by the judge below
                                    tokensIn = result.usage.inputTokens,
                                    tokensOut = result.usage.outputTokens,
                                    costEur = cost,
                                    latencyMs = latencyMs,
                                    errorKind = "",
                                ).also { row ->
                                    // Proxy judge: correctness + grounding of the reply, 0-2.
                                    val judge = judgeReply(key, scenario, label, reply, capEur, spentEur)
                                    spentEur += judge.costEur
                                    variantCostEur += judge.costEur
                                    row.judgeScore = judge.score?.toString() ?: ""
                                    if (judge.score == null && judge.costEur == 0.0 && judge.failed) {
                                        println("chat-bench: judge for ${scenario.id} ($variant run $run) failed — score left blank")
                                    }
                                }
                            }
                        }
                        runRows += row
                    }
                    File(outDir, "$model.$variant.run$run.csv").writeText(
                        runRows.joinToString("\n", prefix = AiChatBenchLogic.CSV_HEADER + "\n") { it.csvLine() },
                    )
                    variantRows += runRows
                    if (budgetAborted) break
                }

                val summary = AiChatBenchLogic.summarize(
                    model = model, variant = variant, runsPlanned = runs,
                    rows = variantRows, budgetAborted = budgetAborted,
                )
                summaries += summary
                println(summary.report)
            }
        }

        File(outDir, "summary.csv").writeText(
            summaries.joinToString("\n", prefix = AiChatBenchLogic.SUMMARY_HEADER + "\n") { it.csvLine + "\n" },
        )
        println("chat-bench: correctness/grounding is a glm-5.3 proxy score, pending native-speaker owner review")
        println("chat-bench: the verdict rule in docs/design/ai-model-decision.md is PROPOSED — owner confirms before reading a verdict")
    }

    /** One judge call over one reply. Returns the score and the judge's cost. */
    private fun judgeReply(
        key: String,
        scenario: AiChatBenchLogic.Scenario,
        variantLabel: String,
        reply: String,
        capEur: Double,
        spentEur: Double,
    ): JudgeOutcome {
        val price = AiBenchLogic.PRICES.getValue(AiBenchmark.JUDGE_MODEL)
        val prompt = AiChatBenchLogic.buildJudgePrompt(scenario, variantLabel, reply)
        val estimate = AiBenchLogic.estimateCostEur(prompt, JUDGE_OUT_TOKENS, price)
        if (spentEur + estimate > capEur) {
            println("chat-bench: judge for ${scenario.id} aborted — would exceed the $capEur EUR cap")
            return JudgeOutcome(score = null, costEur = 0.0, failed = true)
        }
        val result = runBlocking {
            benchProvider(AiBenchmark.JUDGE_MODEL, key).complete(AiRequest(prompt = prompt, maxTokens = JUDGE_OUT_TOKENS))
        }
        if (result !is AiResult.Ok) {
            return JudgeOutcome(score = null, costEur = 0.0, failed = true)
        }
        val cost = AiBenchLogic.costEur(price, result.usage.inputTokens, result.usage.outputTokens)
        return JudgeOutcome(score = AiChatBenchLogic.parseJudgeScore(result.text), costEur = cost, failed = false)
    }

    /** Variant A path: the real adapter with the harness's raised read timeout. */
    private fun benchProvider(model: String, key: String): OpenAiCompatibleProvider =
        object : OpenAiCompatibleProvider(
            AiProviderConfig(AiProviders.MELIOUS, AiBenchmark.BASE_URL, model),
            KeySource { key },
        ) {
            override fun openConnection(url: URL): HttpURLConnection =
                BenchChatConnection(url.openConnection() as HttpURLConnection)
        }

    /**
     * Variant B path: a test-only client that builds its own JSON body (the
     * adapter's `encodeRequest` is final and single-prompt), POSTs it to
     * [OpenAiCompatibleProvider.CHAT_PATH], and decodes with the public
     * [OpenAiCompatibleProvider.decodeResponse] so error mapping is identical.
     */
    private fun postChat(model: String, key: String, bodyJson: String): AiResult {
        var connection: HttpURLConnection? = null
        val start = System.nanoTime()
        return try {
            val url = URL(AiBenchmark.BASE_URL.trimEnd('/') + OpenAiCompatibleProvider.CHAT_PATH)
            connection = BenchChatConnection(url.openConnection() as HttpURLConnection)
            connection.requestMethod = "POST"
            connection.connectTimeout = OpenAiCompatibleProvider.CONNECT_TIMEOUT_MS
            connection.readTimeout = BenchChatConnection.READ_TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $key")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.use { it.write(bodyJson.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val retryAfter = connection.getHeaderField(OpenAiCompatibleProvider.RETRY_AFTER_HEADER)?.trim()?.toIntOrNull()
            val body = readBody(connection)
            val latencyMs = (System.nanoTime() - start) / 1_000_000
            val decoded = OpenAiCompatibleProvider(
                AiProviderConfig(AiProviders.MELIOUS, AiBenchmark.BASE_URL, model),
                KeySource { key },
            ).decodeResponse(status, retryAfter, body, latencyMs)
            // Same last line of defense as the adapter: the key never surfaces.
            if (decoded is AiResult.Error && key.isNotBlank()) {
                decoded.copy(message = decoded.message.replace(key, "[redacted]"))
            } else {
                decoded
            }
        } catch (e: java.io.IOException) {
            AiResult.Error(AiResult.ErrorKind.NETWORK, "Could not reach the provider (${e.javaClass.simpleName})")
        } catch (e: Exception) {
            AiResult.Error(AiResult.ErrorKind.NETWORK, "Request failed (${e.javaClass.simpleName})")
        } finally {
            connection?.disconnect()
        }
    }

    private fun readBody(connection: HttpURLConnection): String? {
        val stream = try {
            connection.inputStream
        } catch (e: java.io.IOException) {
            connection.errorStream ?: return null
        }
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    /** Output base per the acceptance: `app/build/ai-chat-bench/` under the project root. */
    private fun outDir(): File {
        val userDir = File(System.getProperty("user.dir") ?: ".")
        return if (userDir.name == "app") File(userDir, "build/ai-chat-bench") else File(userDir, "app/build/ai-chat-bench")
    }

    /** A built request: the wire body, the raw prompt (variant A only) and the input size for the budget estimate. */
    private class BuiltRequest(val body: String, val prompt: String?, val inputChars: Int)

    /** Judge result with named parts (score may be null; cost is always counted). */
    private data class JudgeOutcome(val score: Int?, val costEur: Double, val failed: Boolean)

    companion object {
        /** Judge model: the #600 proxy judge, same endpoint ([AiBenchmark.BASE_URL]). */
        const val JUDGE_MODEL = AiBenchmark.JUDGE_MODEL

        /**
         * Chat replies are short prose, but the default model emits reasoning
         * tokens (#600 round 3), so the headroom matches the adapter's 4k
         * default. A cut-off reply (finish_reason "length") comes back as
         * BAD_RESPONSE through decodeResponse.
         */
        const val CHAT_MAX_TOKENS = 4_096

        /** Pre-call estimate only; the recorded cost always uses real usage. */
        const val ESTIMATE_OUT_TOKENS = 1_024

        /** Judge replies are one small JSON object; reasoning headroom included. */
        const val JUDGE_OUT_TOKENS = 1_024

        /**
         * Cap: 2 variants x 5 scenarios x 3 runs = 30 chat calls plus up to 30
         * judge calls; worst-case estimates at the recorded prices stay around
         * EUR 0.10, so EUR 0.30 leaves 3x headroom.
         */
        const val DEFAULT_CAP_EUR = 0.30

        const val VARIANT_A = "A"
        const val VARIANT_B = "B"

        val VARIANTS = listOf(VARIANT_A to "flattened prompt", VARIANT_B to "native messages[]")
    }
}

/**
 * Delegates to a real connection but pins the read timeout at [READ_TIMEOUT_MS]:
 * chat replies are prose, not 60-item generations, but the flash model's
 * reasoning tokens can still push past the adapter's 60 s default. Harness-only;
 * replicated from AiBenchmark (its copy is private).
 */
private class BenchChatConnection(private val real: HttpURLConnection) : HttpURLConnection(real.url) {

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
        const val READ_TIMEOUT_MS = 600_000
    }
}

/** One CSV row of the chat transport benchmark; mutable only for the judge score. */
data class ChatBenchRow(
    val run: Int,
    val variant: String,
    val model: String,
    val scenario: String,
    /** "true"/"false" for subject-proposal scenarios; blank when not applicable. */
    val subjectOk: String,
    /** "true"/"false" when the fixture lists expected tokens; blank otherwise. */
    val followupOk: String,
    val canaryLeak: Boolean,
    var judgeScore: String,
    val tokensIn: Int,
    val tokensOut: Int,
    val costEur: Double,
    val latencyMs: Long,
    val errorKind: String,
) {
    fun csvLine(): String = listOf(
        run.toString(),
        variant,
        model,
        scenario,
        subjectOk,
        followupOk,
        canaryLeak.toString(),
        judgeScore,
        tokensIn.toString(),
        tokensOut.toString(),
        "%.6f".format(costEur),
        latencyMs.toString(),
        errorKind,
    ).joinToString(",") { AiBenchLogic.csvEscape(it) }
}

/**
 * The deterministic part of the chat transport harness: fixture loading and
 * validation, request building for both variants, SUBJECT / follow-up / canary
 * grading, the judge prompt and score parser, and the per-variant summary.
 * Context-free and key-free; unit-tested with canned fixtures
 * (AiChatBenchmarkTest) — no network here. Prices and cost math come from
 * [AiBenchLogic] (reuse, never a copied price table).
 */
object AiChatBenchLogic {

    const val FIXTURE_PATH = "ai_chat_bench/scenarios.json"

    // Scenario kinds; unknown kinds are rejected by the loader.
    const val KIND_FIRST_TURN_MISS = "first-turn-miss"
    const val KIND_SUBJECT_PROPOSAL = "subject-proposal"
    const val KIND_FOLLOW_UP = "follow-up"
    const val KIND_RESUMED_SUMMARY = "resumed-summary"
    const val KIND_FAILED_TURN = "failed-turn"

    val SCENARIO_KINDS = setOf(
        KIND_FIRST_TURN_MISS, KIND_SUBJECT_PROPOSAL, KIND_FOLLOW_UP, KIND_RESUMED_SUMMARY, KIND_FAILED_TURN,
    )

    // Grounding kinds, mirroring docs/design/ask-ai-chat.md "Data" payloads.
    const val GROUNDING_MISS = "miss"
    const val GROUNDING_WORD = "word"
    const val GROUNDING_PRACTICE = "practice"

    val GROUNDING_KINDS = setOf(GROUNDING_MISS, GROUNDING_WORD, GROUNDING_PRACTICE)

    const val ROLE_USER = "user"
    const val ROLE_ASSISTANT = "assistant"

    // ---- Fixture ------------------------------------------------------------

    sealed class Grounding {
        data class Miss(
            val prompt: String,
            val picked: String,
            val answer: String,
            /** Blank for a topic grounding (no rule). */
            val ruleId: String,
            val chips: List<String>,
        ) : Grounding()

        data class Word(val text: String, val meaning: String, val context: String) : Grounding()
        data class Practice(val prompt: String, val answer: String) : Grounding()
    }

    data class ChatMessage(val role: String, val text: String, val failed: Boolean)

    data class Scenario(
        val id: String,
        val kind: String,
        val summary: String,
        val groundings: List<Grounding>,
        /** In stored order, INCLUDING failed turns (they carry the canary). */
        val messages: List<ChatMessage>,
        val expectedTokens: List<String>,
        val canary: String,
    ) {
        /** The turns that may reach a request: failed turns never do. */
        val deliveredMessages: List<ChatMessage> get() = messages.filter { !it.failed }
    }

    /**
     * Loads and validates the fixture. Every check here runs BEFORE any request
     * is built: unknown scenario kind, unknown grounding kind, a message role
     * other than user/assistant, and per-kind required fields — each checked
     * present and non-blank, independent of counts.
     */
    fun loadScenarios(): List<Scenario> {
        val text = AiChatBenchLogic::class.java.classLoader?.getResourceAsStream(FIXTURE_PATH)?.bufferedReader()?.readText()
            ?: AiChatBenchLogic::class.java.getResourceAsStream("/$FIXTURE_PATH")?.bufferedReader()?.readText()
            ?: error("Fixture '$FIXTURE_PATH' not found")
        val root = JSONObject(text)
        val array = root.optJSONArray("scenarios")
            ?: throw IllegalArgumentException("Fixture '$FIXTURE_PATH' has no scenarios array")
        val ids = mutableSetOf<String>()
        return (0 until array.length()).map { i -> parseScenario(array.getJSONObject(i), ids) }
    }

    /** Public for the key-free unit tests; rejects incomplete scenarios outright. */
    fun parseScenario(obj: JSONObject, ids: MutableSet<String>): Scenario {
        val index = ids.size + 1
        val id = obj.optString("id").trim()
        require(id.isNotBlank()) { "scenario #$index: missing id" }
        require(ids.add(id)) { "duplicate scenario id '$id'" }
        val kind = obj.optString("kind").trim()
        require(kind in SCENARIO_KINDS) { "scenario '$id': unknown scenario kind '$kind'" }

        val groundingsJson = obj.optJSONArray("groundings")
        require(groundingsJson != null && groundingsJson.length() > 0) { "scenario '$id': no groundings" }
        val groundings = (0 until groundingsJson.length()).map { i -> parseGrounding(groundingsJson.getJSONObject(i), id) }

        val messagesJson = obj.optJSONArray("messages")
        require(messagesJson != null && messagesJson.length() > 0) { "scenario '$id': no messages" }
        val messages = (0 until messagesJson.length()).map { i ->
            val message = messagesJson.getJSONObject(i)
            val role = message.optString("role").trim()
            require(role == ROLE_USER || role == ROLE_ASSISTANT) {
                "scenario '$id': message role must be '$ROLE_USER' or '$ROLE_ASSISTANT', was '$role'"
            }
            val text = message.optString("text").trim()
            require(text.isNotBlank()) { "scenario '$id': a $role message has blank text" }
            ChatMessage(role, text, failed = message.optBoolean("failed", false))
        }
        val lastDelivered = messages.lastOrNull { !it.failed }
        require(lastDelivered != null && lastDelivered.role == ROLE_USER) {
            "scenario '$id': the thread must end on a delivered user turn (the new question)"
        }

        val summary = obj.optString("summary", "").trim()
        if (kind == KIND_RESUMED_SUMMARY) {
            require(summary.isNotBlank()) { "scenario '$id': resumed-summary requires a non-blank summary" }
        }

        val expectedTokens = obj.optJSONArray("expectedTokens")
            ?.let { array -> (0 until array.length()).map { array.optString(it).trim() } }
            ?: emptyList()
        if (kind == KIND_FOLLOW_UP) {
            require(expectedTokens.isNotEmpty() && expectedTokens.all { it.isNotBlank() }) {
                "scenario '$id': follow-up requires a non-empty expectedTokens list of non-blank tokens"
            }
        }

        val canary = obj.optString("canary", "").trim()
        if (kind == KIND_FAILED_TURN) {
            require(canary.isNotBlank()) { "scenario '$id': failed-turn requires a non-blank canary" }
            require(messages.any { it.failed && it.text.contains(canary) }) {
                "scenario '$id': the canary must sit inside a failed turn, otherwise the leak check is vacuous"
            }
        }

        if (kind == KIND_SUBJECT_PROPOSAL) {
            require(groundings.any { it is Grounding.Word }) {
                "scenario '$id': subject-proposal requires a word grounding"
            }
            require(groundings.any { it is Grounding.Miss && it.ruleId.isNotBlank() }) {
                "scenario '$id': subject-proposal requires a miss grounding with a non-blank ruleId"
            }
        }

        return Scenario(id, kind, summary, groundings, messages, expectedTokens, canary)
    }

    private fun parseGrounding(obj: JSONObject, scenarioId: String): Grounding {
        val kind = obj.optString("kind").trim()
        return when (kind) {
            GROUNDING_MISS -> Grounding.Miss(
                prompt = requiredText(obj, "prompt", scenarioId, kind),
                picked = requiredText(obj, "picked", scenarioId, kind),
                answer = requiredText(obj, "answer", scenarioId, kind),
                ruleId = obj.optString("ruleId", "").trim(),
                chips = obj.optJSONArray("chips")?.let { array ->
                    (0 until array.length()).map { array.optString(it).trim() }.filter { it.isNotBlank() }
                } ?: emptyList(),
            )
            GROUNDING_WORD -> Grounding.Word(
                text = requiredText(obj, "text", scenarioId, kind),
                meaning = requiredText(obj, "meaning", scenarioId, kind),
                context = requiredText(obj, "context", scenarioId, kind),
            )
            GROUNDING_PRACTICE -> Grounding.Practice(
                prompt = requiredText(obj, "prompt", scenarioId, kind),
                answer = requiredText(obj, "answer", scenarioId, kind),
            )
            else -> throw IllegalArgumentException("scenario '$scenarioId': unknown grounding kind '$kind'")
        }
    }

    /** Each required field is checked present and non-blank, independent of counts. */
    private fun requiredText(obj: JSONObject, field: String, scenarioId: String, what: String): String {
        val value = obj.optString(field, "").trim()
        require(value.isNotBlank()) { "scenario '$scenarioId': $what grounding is missing required field '$field'" }
        return value
    }

    // ---- Request building ---------------------------------------------------

    /** Labelled grounding lines, identical for both variants. */
    fun groundingLines(scenario: Scenario): List<String> = scenario.groundings.map { grounding ->
        when (grounding) {
            is Grounding.Miss -> buildString {
                append("- Miss: prompt \"${grounding.prompt}\" | learner picked: ${grounding.picked} | correct: ${grounding.answer}")
                if (grounding.ruleId.isNotBlank()) append(" | rule: ${grounding.ruleId}")
                if (grounding.chips.isNotEmpty()) append(" | chips: ${grounding.chips.joinToString(", ")}")
            }
            is Grounding.Word ->
                "- Saved word: \"${grounding.text}\" means \"${grounding.meaning}\"; saved context: \"${grounding.context}\""
            is Grounding.Practice ->
                "- Practice item: prompt \"${grounding.prompt}\" | answer: ${grounding.answer}"
        }
    }

    private fun factsBlock(scenario: Scenario): String =
        groundingLines(scenario).joinToString("\n", postfix = "\n")

    /**
     * Variant A: one flattened prompt — instructions, grounding block, summary
     * and transcript as labelled lines, ending on the new question. Failed
     * turns are excluded. No SUBJECT instruction unless the thread has both a
     * word and a rule candidate.
     */
    fun buildFlattenedPrompt(scenario: Scenario): String {
        val builder = StringBuilder()
        builder.append(AiChatBenchPrompt.INSTRUCTIONS).append('\n')
        if (scenario.kind == KIND_SUBJECT_PROPOSAL) {
            builder.append('\n').append(AiChatBenchPrompt.SUBJECT_INSTRUCTION).append('\n')
        }
        builder.append("\nFacts you may use:\n").append(factsBlock(scenario))
        if (scenario.summary.isNotBlank()) {
            builder.append("\nSummary of earlier turns:\n").append(scenario.summary).append('\n')
        }
        builder.append("\nTranscript:\n")
        val history = scenario.deliveredMessages
        for ((index, message) in history.dropLast(1).withIndex()) {
            builder.append("[Turn ${index + 1} | ${message.role}] ${message.text}\n")
        }
        builder.append("[New question | ${ROLE_USER}] ${history.last().text}\n")
        builder.append("\nAnswer as the assistant: reply to the new question only.\n")
        return builder.toString()
    }

    /**
     * Variant B: native `messages[]` — one `system` message carrying the
     * instructions, groundings and summary, then the delivered history as
     * alternating `user`/`assistant` turns, ending on the new `user` turn.
     * Failed turns are excluded. Consecutive same-role turns (possible after a
     * failed turn is dropped) are merged so the roles stay strictly alternating.
     */
    fun buildMessages(scenario: Scenario): List<Pair<String, String>> {
        val system = StringBuilder(AiChatBenchPrompt.INSTRUCTIONS)
        if (scenario.kind == KIND_SUBJECT_PROPOSAL) {
            system.append("\n\n").append(AiChatBenchPrompt.SUBJECT_INSTRUCTION)
        }
        system.append("\n\nFacts you may use:\n").append(factsBlock(scenario))
        if (scenario.summary.isNotBlank()) {
            system.append("\nSummary of earlier turns:\n").append(scenario.summary).append('\n')
        }
        val messages = mutableListOf<Pair<String, String>>()
        fun addTurn(role: String, text: String) {
            val last = messages.lastOrNull()
            messages += if (last != null && last.first == role) {
                messages.removeAt(messages.size - 1)
                last.copy(second = last.second + "\n\n" + text)
            } else {
                Pair(role, text)
            }
        }
        val history = scenario.deliveredMessages
        for (message in history.dropLast(1)) addTurn(message.role, message.text)
        addTurn(ROLE_USER, history.last().text)
        return listOf(Pair("system", system.toString())) + messages
    }

    /**
     * Variant B's wire body, OpenAI chat shape. No `response_format` — chat is
     * prose and the adapter's `encodeRequest` (final, single-prompt) cannot be
     * reused for a message list.
     */
    fun encodeMessagesBody(model: String, messages: List<Pair<String, String>>, maxTokens: Int): String {
        val array = JSONArray()
        for ((role, content) in messages) {
            array.put(JSONObject().put("role", role).put("content", content))
        }
        return JSONObject()
            .put("model", model)
            .put("messages", array)
            .put("max_tokens", maxTokens)
            .toString()
    }

    // ---- Deterministic grading ---------------------------------------------

    private val emphasisMarkers = Regex("[*_`]")

    /**
     * The SUBJECT rule of docs/design/ask-ai-chat.md §Threads: the FIRST
     * non-empty line of the reply, trimmed, emphasis markers (`*`, `_`,
     * backticks) stripped, compared case-insensitively against exactly
     * `SUBJECT: word` or `SUBJECT: rule`.
     *
     * Documented decision: the space after the colon is REQUIRED —
     * `subject:rule` FAILS. The model is instructed with the exact literal
     * `SUBJECT: word`/`SUBJECT: rule`, and production (task-03) falls back to
     * the deterministic router on any malformed line, so a strict parser is the
     * safer default to measure.
     */
    fun subjectOk(reply: String): Boolean {
        val firstNonEmptyLine = reply.lineSequence().firstOrNull { it.isNotBlank() } ?: return false
        val cleaned = firstNonEmptyLine.replace(emphasisMarkers, "").trim()
        return cleaned.equals("SUBJECT: word", ignoreCase = true) ||
            cleaned.equals("SUBJECT: rule", ignoreCase = true)
    }

    /** Lowercased, NFC-normalized, umlaut-folded text (umlaut rule as [AiBenchLogic.isGrounded]). */
    fun normalizeComparable(text: String): String =
        AiBenchLogic.normalizeGerman(Normalizer.normalize(text, Normalizer.Form.NFC))

    /**
     * Follow-up check: the reply uses at least one of the fixture's expected
     * tokens (the words that only an earlier turn supplies), compared
     * lowercased, NFC-normalized and umlaut-insensitive.
     */
    fun usesEarlierTurn(reply: String, expectedTokens: List<String>): Boolean {
        if (expectedTokens.isEmpty()) return false
        val replyTokens = normalizeComparable(reply)
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 2 }
            .toSet()
        return expectedTokens.any { token ->
            val normalized = normalizeComparable(token)
            normalized.length >= 2 && normalized in replyTokens
        }
    }

    /**
     * Failed-turn leak: the canary (which lives only in a failed turn) must not
     * appear in the built request body nor in the reply.
     */
    fun leakedCanary(requestBody: String, reply: String?, canary: String): Boolean {
        if (canary.isBlank()) return false
        return requestBody.contains(canary, ignoreCase = true) ||
            (reply != null && reply.contains(canary, ignoreCase = true))
    }

    // ---- Judge --------------------------------------------------------------

    /** The proxy judge's prompt: correctness + grounding of one reply, 0-2. */
    fun buildJudgePrompt(scenario: Scenario, variantLabel: String, reply: String): String {
        val builder = StringBuilder()
        builder.append("You review one reply of a German-tutor chat (variant: $variantLabel) for correctness and grounding.\n")
        builder.append("Score the reply from 0 to 2:\n")
        builder.append("2 = correct German, answers the learner's question, uses only the shown facts and earlier turns\n")
        builder.append("1 = mostly right, with small problems of correctness or grounding\n")
        builder.append("0 = wrong German, does not answer the question, or contradicts the shown facts\n")
        builder.append("\n")
        builder.append("Answer with one JSON object and nothing else:\n")
        builder.append("{\"score\": 2}\n")
        builder.append("\n")
        builder.append("Facts the tutor was shown:\n")
        groundingLines(scenario).forEach { builder.append(it).append('\n') }
        if (scenario.summary.isNotBlank()) {
            builder.append("\nSummary of earlier turns the tutor was shown:\n").append(scenario.summary).append('\n')
        }
        builder.append("\nTranscript:\n")
        val history = scenario.deliveredMessages
        for ((index, message) in history.dropLast(1).withIndex()) {
            builder.append("[Turn ${index + 1} | ${message.role}] ${message.text}\n")
        }
        builder.append("[New question | ${ROLE_USER}] ${history.last().text}\n")
        builder.append("\nReply to score:\n\"\"\"\n").append(reply).append("\n\"\"\"\n")
        return builder.toString()
    }

    /**
     * Parses `{"score": n}` from the judge reply. Returns null — never a guess
     * — when the score is missing, non-numeric, non-integer or out of 0..2.
     */
    fun parseJudgeScore(text: String): Int? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val root = try {
            JSONObject(text.substring(start, end + 1))
        } catch (e: Exception) {
            return null
        }
        val score = root.opt("score") as? Number ?: return null
        val asInt = score.toInt()
        return if (asInt.toDouble() == score.toDouble() && asInt in 0..2) asInt else null
    }

    // ---- Summary ------------------------------------------------------------

    const val CSV_HEADER =
        "run,variant,model,scenario,subject_ok,followup_ok,canary_leak,judge_score,tokens_in,tokens_out,cost_eur,latency_ms,error_kind"
    const val SUMMARY_HEADER =
        "variant,model,runs,calls,failed_calls,subject_ok_pct,subject_n,followup_ok_pct,followup_n,canary_leaks,mean_judge,mean_tokens_in,mean_tokens_out,cost_per_turn_eur,total_cost_eur,p50_latency_ms,p95_latency_ms,notes"

    data class SummaryRow(
        val variant: String,
        val model: String,
        val runs: Int,
        val calls: Int,
        val failedCalls: Int,
        val subjectOkPct: Double,
        val subjectN: Int,
        val followupOkPct: Double,
        val followupN: Int,
        val canaryLeaks: Int,
        val meanJudge: Double?,
        val meanTokensIn: Double,
        val meanTokensOut: Double,
        val costPerTurnEur: Double,
        val totalCostEur: Double,
        val p50LatencyMs: Long,
        val p95LatencyMs: Long,
        val budgetAborted: Boolean,
    ) {
        val csvLine: String
            get() = listOf(
                variant, model, runs.toString(), calls.toString(), failedCalls.toString(),
                "%.1f".format(subjectOkPct), subjectN.toString(),
                "%.1f".format(followupOkPct), followupN.toString(),
                canaryLeaks.toString(),
                meanJudge?.let { "%.2f".format(it) } ?: "",
                "%.0f".format(meanTokensIn),
                "%.0f".format(meanTokensOut),
                "%.6f".format(costPerTurnEur),
                "%.6f".format(totalCostEur),
                p50LatencyMs.toString(), p95LatencyMs.toString(),
                if (budgetAborted) "aborted_budget" else "",
            ).joinToString(",") { AiBenchLogic.csvEscape(it) }

        val report: String
            get() {
                val label = if (variant == AiChatBenchmark.VARIANT_A) "A (flattened prompt)" else "B (native messages[])"
                return "chat-bench: variant $label — subject ${"%.1f".format(subjectOkPct)}% (n=$subjectN), " +
                    "followup ${"%.1f".format(followupOkPct)}% (n=$followupN), canary leaks $canaryLeaks/$calls, " +
                    "judge ${meanJudge?.let { "%.2f".format(it) } ?: "n/a"} of 2, " +
                    "tokens in ${"%.0f".format(meanTokensIn)} / out ${"%.0f".format(meanTokensOut)} per turn, " +
                    "EUR/turn ${"%.6f".format(costPerTurnEur)}, p50 ${p50LatencyMs}ms, p95 ${p95LatencyMs}ms, " +
                    "failures $failedCalls/$calls" +
                    (if (budgetAborted) " — ABORTED at the budget cap" else "")
            }
    }

    /** Percentages are computed over the rows where the check applies (n > 0). */
    fun summarize(
        model: String,
        variant: String,
        runsPlanned: Int,
        rows: List<ChatBenchRow>,
        budgetAborted: Boolean,
    ): SummaryRow {
        val delivered = rows.filter { it.errorKind.isBlank() }
        val subjectRows = rows.filter { it.subjectOk.isNotBlank() }
        val followupRows = rows.filter { it.followupOk.isNotBlank() }
        val judged = delivered.mapNotNull { it.judgeScore.toDoubleOrNull() }
        val latencies = delivered.map { it.latencyMs }.filter { it > 0 }.sorted()
        val totalCostEur = rows.sumOf { it.costEur }
        return SummaryRow(
            variant = variant,
            model = model,
            runs = runsPlanned,
            calls = rows.size,
            failedCalls = rows.size - delivered.size,
            subjectOkPct = if (subjectRows.isEmpty()) 0.0 else subjectRows.count { it.subjectOk == "true" } * 100.0 / subjectRows.size,
            subjectN = subjectRows.size,
            followupOkPct = if (followupRows.isEmpty()) 0.0 else followupRows.count { it.followupOk == "true" } * 100.0 / followupRows.size,
            followupN = followupRows.size,
            canaryLeaks = rows.count { it.canaryLeak },
            meanJudge = if (judged.isEmpty()) null else judged.average(),
            meanTokensIn = if (delivered.isEmpty()) 0.0 else delivered.averageOf { it.tokensIn },
            meanTokensOut = if (delivered.isEmpty()) 0.0 else delivered.averageOf { it.tokensOut },
            costPerTurnEur = if (delivered.isEmpty()) 0.0 else totalCostEur / delivered.size,
            totalCostEur = totalCostEur,
            p50LatencyMs = AiBenchLogic.percentile(latencies, 0.50),
            p95LatencyMs = AiBenchLogic.percentile(latencies, 0.95),
            budgetAborted = budgetAborted,
        )
    }

    private inline fun List<ChatBenchRow>.averageOf(selector: (ChatBenchRow) -> Int): Double =
        sumOf(selector) * 1.0 / size
}

/**
 * The provisional chat instructions for the #699 chat transport benchmark.
 * task-03 ports THIS TEXT into production (single source for the two variants,
 * so the benchmark cannot drift from what production will send). Both variants
 * embed [INSTRUCTIONS]; [SUBJECT_INSTRUCTION] is appended ONLY when a thread
 * has both a word and a rule candidate (docs/design/ask-ai-chat.md §Threads).
 */
object AiChatBenchPrompt {

    val INSTRUCTIONS = """
        You are a patient German tutor inside a language-learning app. The learner asks about German grammar or vocabulary, often about a specific mistake they made or a saved word. Answer in English about German: quote German words and sentences as needed, but explain in English. Keep the answer short (2-5 sentences), concrete and directly about the question. Use earlier turns when the question builds on them. Work only from what this conversation shows you; never invent saved words, rules or earlier answers that are not in it.
    """.trimIndent()

    val SUBJECT_INSTRUCTION = """
        This thread could be about either the saved word or the grammar rule shown in the facts. Begin your reply with exactly one first line — "SUBJECT: word" if the question is mainly about the saved word, or "SUBJECT: rule" if it is mainly about the grammar rule — then answer normally after it.
    """.trimIndent()
}
