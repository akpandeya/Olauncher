package app.olauncher.data.store

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Key-free tests for the deterministic parts of the chat transport benchmark
 * (AiChatBenchmark.kt, #699): fixture validation, request building for both
 * variants, SUBJECT/follow-up/canary grading, the judge parser and the summary.
 * No network here — the live run is a script-style execution of AiChatBenchmark
 * itself, gated on a key.
 */
class AiChatBenchmarkTest {

    // ---- Fixture ------------------------------------------------------------

    @Test
    fun `the fixture loads five scenarios of the five documented kinds`() {
        val scenarios = AiChatBenchLogic.loadScenarios()
        assertEquals(5, scenarios.size)
        assertEquals(
            listOf(
                AiChatBenchLogic.KIND_FIRST_TURN_MISS,
                AiChatBenchLogic.KIND_SUBJECT_PROPOSAL,
                AiChatBenchLogic.KIND_FOLLOW_UP,
                AiChatBenchLogic.KIND_RESUMED_SUMMARY,
                AiChatBenchLogic.KIND_FAILED_TURN,
            ),
            scenarios.map { it.kind },
        )
        assertEquals(scenarios.map { it.id }, scenarios.map { it.id }.distinct())
        val followUp = scenarios.first { it.kind == AiChatBenchLogic.KIND_FOLLOW_UP }
        assertTrue("the follow-up fixture lists the tokens the reply must reuse", followUp.expectedTokens.contains("arzt"))
        val failed = scenarios.first { it.kind == AiChatBenchLogic.KIND_FAILED_TURN }
        assertTrue("the failed-turn fixture carries its canary inside a failed turn", failed.messages.any { it.failed })
        val resumed = scenarios.first { it.kind == AiChatBenchLogic.KIND_RESUMED_SUMMARY }
        assertEquals(3, resumed.groundings.size)
        assertTrue(resumed.summary.isNotBlank())
    }

    private fun scenarioJson(json: String): JSONObject = JSONObject(json)

    @Test
    fun `the loader rejects an unknown scenario kind`() {
        val root = scenarioJson("""{"scenarios": [{"id": "x", "kind": "mystery", "groundings": [], "messages": []}]}""")
        val e = assertThrows(IllegalArgumentException::class.java) { loadSingle(root) }
        assertTrue("reason: ${e.message}", e.message!!.contains("unknown scenario kind 'mystery'"))
    }

    @Test
    fun `the loader rejects an unknown grounding kind`() {
        val root = scenarioJson(
            """{"scenarios": [{"id": "x", "kind": "first-turn-miss",
                "groundings": [{"kind": "recipe", "prompt": "p"}],
                "messages": [{"role": "user", "text": "q"}]}]}""",
        )
        val e = assertThrows(IllegalArgumentException::class.java) { loadSingle(root) }
        assertTrue("reason: ${e.message}", e.message!!.contains("unknown grounding kind 'recipe'"))
    }

    @Test
    fun `the loader rejects a message role other than user or assistant`() {
        val root = scenarioJson(
            """{"scenarios": [{"id": "x", "kind": "first-turn-miss",
                "groundings": [{"kind": "miss", "prompt": "p", "picked": "a", "answer": "b"}],
                "messages": [{"role": "system", "text": "q"}]}]}""",
        )
        val e = assertThrows(IllegalArgumentException::class.java) { loadSingle(root) }
        assertTrue("reason: ${e.message}", e.message!!.contains("message role must be 'user' or 'assistant', was 'system'"))
    }

    @Test
    fun `the loader rejects a follow-up without its expected tokens`() {
        val root = scenarioJson(
            """{"scenarios": [{"id": "x", "kind": "follow-up",
                "groundings": [{"kind": "miss", "prompt": "p", "picked": "a", "answer": "b"}],
                "messages": [
                    {"role": "user", "text": "first"},
                    {"role": "assistant", "text": "answer"},
                    {"role": "user", "text": "last"}]}]}""",
        )
        val e = assertThrows(IllegalArgumentException::class.java) { loadSingle(root) }
        assertTrue("reason: ${e.message}", e.message!!.contains("follow-up requires a non-empty expectedTokens list"))
    }

    @Test
    fun `the loader rejects a failed-turn scenario without its canary`() {
        val root = scenarioJson(
            """{"scenarios": [{"id": "x", "kind": "failed-turn",
                "groundings": [{"kind": "miss", "prompt": "p", "picked": "a", "answer": "b"}],
                "messages": [{"role": "user", "text": "q"}]}]}""",
        )
        val e = assertThrows(IllegalArgumentException::class.java) { loadSingle(root) }
        assertTrue("reason: ${e.message}", e.message!!.contains("failed-turn requires a non-blank canary"))
    }

    @Test
    fun `the loader rejects a resumed-summary without a summary`() {
        val root = scenarioJson(
            """{"scenarios": [{"id": "x", "kind": "resumed-summary",
                "groundings": [{"kind": "miss", "prompt": "p", "picked": "a", "answer": "b"}],
                "messages": [{"role": "user", "text": "q"}]}]}""",
        )
        val e = assertThrows(IllegalArgumentException::class.java) { loadSingle(root) }
        assertTrue("reason: ${e.message}", e.message!!.contains("resumed-summary requires a non-blank summary"))
    }

    @Test
    fun `the loader rejects a blank required grounding field`() {
        val root = scenarioJson(
            """{"scenarios": [{"id": "x", "kind": "first-turn-miss",
                "groundings": [{"kind": "miss", "prompt": "p", "picked": "a", "answer": " "}],
                "messages": [{"role": "user", "text": "q"}]}]}""",
        )
        val e = assertThrows(IllegalArgumentException::class.java) { loadSingle(root) }
        assertTrue("reason: ${e.message}", e.message!!.contains("missing required field 'answer'"))
    }

    @Test
    fun `the loader rejects a subject-proposal without a rule-tagged miss`() {
        val root = scenarioJson(
            """{"scenarios": [{"id": "x", "kind": "subject-proposal",
                "groundings": [
                    {"kind": "word", "text": "die Straße", "meaning": "the street", "context": "c"},
                    {"kind": "miss", "prompt": "p", "picked": "a", "answer": "b"}],
                "messages": [{"role": "user", "text": "q"}]}]}""",
        )
        val e = assertThrows(IllegalArgumentException::class.java) { loadSingle(root) }
        assertTrue("reason: ${e.message}", e.message!!.contains("requires a miss grounding with a non-blank ruleId"))
    }

    @Test
    fun `the loader rejects a canary that sits outside a failed turn`() {
        val root = scenarioJson(
            """{"scenarios": [{"id": "x", "kind": "failed-turn", "canary": "CANARY",
                "groundings": [{"kind": "miss", "prompt": "p", "picked": "a", "answer": "b"}],
                "messages": [{"role": "user", "text": "q without it"}]}]}""",
        )
        val e = assertThrows(IllegalArgumentException::class.java) { loadSingle(root) }
        assertTrue("reason: ${e.message}", e.message!!.contains("the canary must sit inside a failed turn"))
    }

    private fun loadSingle(root: JSONObject) {
        // Route through the same validation path by parsing the single scenario
        // the way loadScenarios does (the loader itself reads only from the
        // packaged fixture).
        val array = root.getJSONArray("scenarios")
        AiChatBenchLogic.parseScenario(array.getJSONObject(0), mutableSetOf())
    }

    // ---- Subject compliance ---------------------------------------------------

    @Test
    fun `an emphasized subject line on the first non-empty line passes`() {
        assertTrue(AiChatBenchLogic.subjectOk("**SUBJECT: Word**\n\nDie Straße ist feminin."))
    }

    @Test
    fun `a missing space after the colon fails per the documented rule`() {
        // Documented decision: the space in "SUBJECT: word" is REQUIRED.
        assertFalse(AiChatBenchLogic.subjectOk("subject:rule\nDer Artikel ist 'die'."))
    }

    @Test
    fun `an unknown subject value fails`() {
        assertFalse(AiChatBenchLogic.subjectOk("Subject: maybe\nHmm."))
    }

    @Test
    fun `the subject line on line 2 after a blank first line passes`() {
        assertTrue(AiChatBenchLogic.subjectOk("\nSUBJECT: rule\nDie Regel lautet:"))
    }

    @Test
    fun `a subject line that follows prose fails`() {
        assertFalse(AiChatBenchLogic.subjectOk("Gute Frage!\nSUBJECT: word"))
    }

    // ---- Follow-up reuse ------------------------------------------------------

    @Test
    fun `a reply that reuses an earlier turn's token passes`() {
        assertTrue(
            AiChatBenchLogic.usesEarlierTurn(
                "Zu Personen sagt man 'zum Arzt' oder 'zu Maria'.",
                listOf("arzt", "maria", "zum"),
            ),
        )
    }

    @Test
    fun `a reply that ignores the earlier turn fails`() {
        assertFalse(AiChatBenchLogic.usesEarlierTurn("Das ist eine andere Frage.", listOf("arzt", "maria")))
    }

    @Test
    fun `the token match is case- nfc- and umlaut-insensitive`() {
        assertTrue(AiChatBenchLogic.usesEarlierTurn("Man sagt: FRÜHER war alles besser.", listOf("früher")))
        assertTrue(AiChatBenchLogic.usesEarlierTurn("strasse", listOf("Straße")))
    }

    @Test
    fun `an empty token list never passes`() {
        assertFalse(AiChatBenchLogic.usesEarlierTurn("anything", emptyList()))
    }

    // ---- Request building -----------------------------------------------------

    private fun failedTurnScenario(): AiChatBenchLogic.Scenario {
        val scenarios = AiChatBenchLogic.loadScenarios()
        return scenarios.first { it.kind == AiChatBenchLogic.KIND_FAILED_TURN }
    }

    @Test
    fun `both request builders exclude the failed turn and its canary`() {
        val scenario = failedTurnScenario()
        val promptA = AiChatBenchLogic.buildFlattenedPrompt(scenario)
        val messagesB = AiChatBenchLogic.buildMessages(scenario)
        val failedText = scenario.messages.first { it.failed }.text

        assertFalse("variant A must not carry the failed turn", promptA.contains(failedText))
        assertFalse(promptA.contains(scenario.canary))
        val bodyB = AiChatBenchLogic.encodeMessagesBody("glm-5.3-flash", messagesB, 4_096)
        assertFalse("variant B must not carry the failed turn", bodyB.contains(failedText))
        assertFalse(bodyB.contains(scenario.canary))
        // And the delivered turns survive in both.
        assertTrue(promptA.contains("seit zwei Jahren"))
        assertTrue(bodyB.contains("seit zwei Jahren"))
    }

    @Test
    fun `variant b emits system first then strictly alternating roles ending on the new user turn`() {
        val scenario = failedTurnScenario()
        val messages = AiChatBenchLogic.buildMessages(scenario)
        assertEquals("system", messages.first().first)
        val turns = messages.drop(1)
        assertEquals("user", turns.last().first)
        for (i in 1 until turns.size) {
            assertTrue("roles must alternate", turns[i].first != turns[i - 1].first)
        }
        assertEquals(3, turns.size) // user, assistant, user (failed turn dropped)
    }

    @Test
    fun `variant b merges consecutive same-role turns so alternation survives`() {
        val scenario = AiChatBenchLogic.Scenario(
            id = "merge", kind = AiChatBenchLogic.KIND_FIRST_TURN_MISS, summary = "",
            groundings = listOf(AiChatBenchLogic.Grounding.Miss("p", "a", "b", "", emptyList())),
            messages = listOf(
                AiChatBenchLogic.ChatMessage("user", "one", false),
                AiChatBenchLogic.ChatMessage("user", "two", false),
                AiChatBenchLogic.ChatMessage("assistant", "three", false),
                AiChatBenchLogic.ChatMessage("user", "four", false),
            ),
            expectedTokens = emptyList(), canary = "",
        )
        val turns = AiChatBenchLogic.buildMessages(scenario).drop(1)
        assertEquals(listOf("user", "assistant", "user"), turns.map { it.first })
        assertTrue("the two user turns are merged, not dropped", turns[0].second.contains("one"))
        assertTrue(turns[0].second.contains("two"))
    }

    @Test
    fun `variant b's wire body has no response_format and carries model and max tokens`() {
        val messages = AiChatBenchLogic.buildMessages(failedTurnScenario())
        val body = JSONObject(AiChatBenchLogic.encodeMessagesBody("glm-5.3-flash", messages, 4_096))
        assertFalse(body.has("response_format"))
        assertEquals("glm-5.3-flash", body.getString("model"))
        assertEquals(4_096, body.getInt("max_tokens"))
        val wireMessages = body.getJSONArray("messages")
        assertEquals("system", wireMessages.getJSONObject(0).getString("role"))
        assertEquals(messages.size, wireMessages.length())
    }

    @Test
    fun `variant a through the real adapter is one prose message with no response format`() {
        // What the live bench sends for variant A: the flattened prompt through
        // the production encodeRequest with jsonMode = false (chat is prose).
        val request = AiRequest(
            prompt = AiChatBenchLogic.buildFlattenedPrompt(failedTurnScenario()),
            jsonMode = false,
            maxTokens = AiChatBenchmark.CHAT_MAX_TOKENS,
        )
        val provider = OpenAiCompatibleProvider(
            AiProviderConfig(AiProviders.MELIOUS, AiBenchmark.BASE_URL, "glm-5.3-flash"),
            KeySource { "unused-test-key" },
        )
        val body = JSONObject(provider.encodeRequest(request))
        assertFalse("chat is prose — no response_format", body.has("response_format"))
        val messages = body.getJSONArray("messages")
        assertEquals("the single prompt becomes exactly one message", 1, messages.length())
        assertEquals("user", messages.getJSONObject(0).getString("role"))
        assertEquals(request.prompt, messages.getJSONObject(0).getString("content"))
    }

    @Test
    fun `variant a carries the labelled grounding summary and transcript lines`() {
        val resumed = AiChatBenchLogic.loadScenarios().first { it.kind == AiChatBenchLogic.KIND_RESUMED_SUMMARY }
        val prompt = AiChatBenchLogic.buildFlattenedPrompt(resumed)
        assertTrue(prompt.contains("Facts you may use:"))
        assertTrue(prompt.contains("- Miss: prompt \"Ich warte auf ___ Bus.\""))
        assertTrue(prompt.contains("- Saved word: \"der Bus\""))
        assertTrue(prompt.contains("- Practice item:"))
        assertTrue(prompt.contains("Summary of earlier turns:"))
        assertTrue(prompt.contains("[New question | user] Welche Präposition gehörte nochmal zu 'sich freuen'?"))
    }

    @Test
    fun `the subject instruction is appended only for subject-proposal scenarios`() {
        val scenarios = AiChatBenchLogic.loadScenarios()
        val proposal = scenarios.first { it.kind == AiChatBenchLogic.KIND_SUBJECT_PROPOSAL }
        val firstMiss = scenarios.first { it.kind == AiChatBenchLogic.KIND_FIRST_TURN_MISS }
        assertTrue(AiChatBenchLogic.buildFlattenedPrompt(proposal).contains("SUBJECT: word"))
        assertTrue(AiChatBenchLogic.buildMessages(proposal).first().second.contains("SUBJECT: word"))
        assertFalse(AiChatBenchLogic.buildFlattenedPrompt(firstMiss).contains("SUBJECT:"))
        assertFalse(AiChatBenchLogic.buildMessages(firstMiss).first().second.contains("SUBJECT:"))
    }

    // ---- Judge ----------------------------------------------------------------

    @Test
    fun `a valid judge score parses`() {
        assertEquals(2, AiChatBenchLogic.parseJudgeScore("""{"score": 2}"""))
        assertEquals(0, AiChatBenchLogic.parseJudgeScore("prose around {\"score\": 0} the json"))
    }

    @Test
    fun `missing out-of-range and malformed judge scores are rejected`() {
        assertNull(AiChatBenchLogic.parseJudgeScore("""{"verdict": "fine"}"""))
        assertNull(AiChatBenchLogic.parseJudgeScore("""{"score": 5}"""))
        assertNull(AiChatBenchLogic.parseJudgeScore("""{"score": -1}"""))
        assertNull(AiChatBenchLogic.parseJudgeScore("""{"score": "x"}"""))
        assertNull(AiChatBenchLogic.parseJudgeScore("""{"score": 1.5}"""))
        assertNull(AiChatBenchLogic.parseJudgeScore("""{"score": """))
        assertNull(AiChatBenchLogic.parseJudgeScore("server error"))
    }

    @Test
    fun `the judge prompt carries the rubric facts transcript and reply`() {
        val scenario = failedTurnScenario()
        val prompt = AiChatBenchLogic.buildJudgePrompt(scenario, "flattened prompt", "Man sagt 'seit', weil...")
        assertTrue(prompt.contains("{\"score\": 2}"))
        assertTrue(prompt.contains("- Miss: prompt \"Ich wohne ___ zwei Jahren in Berlin.\""))
        assertTrue(prompt.contains("[New question | user] Nochmal langsam"))
        assertTrue(prompt.contains("Man sagt 'seit', weil..."))
    }

    // ---- Summary ---------------------------------------------------------------

    private fun row(
        variant: String = "A",
        errorKind: String = "",
        subjectOk: String = "",
        followupOk: String = "",
        canaryLeak: Boolean = false,
        judgeScore: String = "",
        tokensIn: Int = 100,
        tokensOut: Int = 50,
        latencyMs: Long = 1_000L,
        costEur: Double = 0.0001,
    ) = ChatBenchRow(
        run = 1, variant = variant, model = "glm-5.3-flash", scenario = "s",
        subjectOk = subjectOk, followupOk = followupOk, canaryLeak = canaryLeak, judgeScore = judgeScore,
        tokensIn = tokensIn, tokensOut = tokensOut, costEur = costEur, latencyMs = latencyMs, errorKind = errorKind,
    )

    @Test
    fun `summary computes percentages over the rows where each check applies`() {
        val rows = listOf(
            row(subjectOk = "true", judgeScore = "2"),
            row(subjectOk = "false", judgeScore = "1"),
            row(), // first-turn-miss: subject n/a, followup n/a
            row(errorKind = "RATE_LIMIT"), // failed call: no tokens, no cost
        )
        val summary = AiChatBenchLogic.summarize("glm-5.3-flash", "A", runsPlanned = 3, rows = rows, budgetAborted = false)
        assertEquals(50.0, summary.subjectOkPct, 0.1)
        assertEquals(2, summary.subjectN)
        assertEquals(0, summary.followupN)
        assertEquals(0.0, summary.followupOkPct, 0.1)
        assertEquals(1.5, summary.meanJudge!!, 1e-9)
        assertEquals(1, summary.failedCalls)
        assertEquals(4, summary.calls)
    }

    @Test
    fun `summary counts canary leaks over every row`() {
        val rows = listOf(row(canaryLeak = true), row(), row())
        val summary = AiChatBenchLogic.summarize("m", "B", runsPlanned = 3, rows = rows, budgetAborted = false)
        assertEquals(1, summary.canaryLeaks)
    }

    @Test
    fun `summary reports latency percentiles and token means per turn`() {
        val rows = listOf(
            row(latencyMs = 100L, tokensIn = 1_000, tokensOut = 500, costEur = 0.0003),
            row(latencyMs = 300L, tokensIn = 2_000, tokensOut = 1_000, costEur = 0.0006),
            row(latencyMs = 200L, tokensIn = 3_000, tokensOut = 1_500, costEur = 0.0009),
        )
        val summary = AiChatBenchLogic.summarize("m", "A", runsPlanned = 3, rows = rows, budgetAborted = false)
        assertEquals(200L, summary.p50LatencyMs)
        assertEquals(300L, summary.p95LatencyMs)
        assertEquals(2_000.0, summary.meanTokensIn, 1e-9)
        assertEquals(1_000.0, summary.meanTokensOut, 1e-9)
        assertEquals(0.0006, summary.costPerTurnEur, 1e-9)
        assertEquals(0.0018, summary.totalCostEur, 1e-9)
    }

    @Test
    fun `a budget-aborted variant is marked in its summary line`() {
        val summary = AiChatBenchLogic.summarize("m", "B", runsPlanned = 3, rows = listOf(row()), budgetAborted = true)
        assertTrue(summary.csvLine.endsWith("aborted_budget"))
        assertTrue(summary.report.contains("ABORTED"))
        assertTrue(summary.report.contains("B (native messages[])"))
    }

    @Test
    fun `summary rows round-trip through csv`() {
        val summary = AiChatBenchLogic.summarize(
            "glm-5.3-flash", "A", runsPlanned = 3,
            rows = listOf(row(subjectOk = "true", followupOk = "true", judgeScore = "2")), budgetAborted = false,
        )
        val fields = AiBenchLogic.splitCsv(summary.csvLine)
        assertEquals("A", fields[0])
        assertEquals("100.0", fields[5])
        assertEquals("1", fields[6])
        assertEquals("2.00", fields[10])
        assertEquals("100", fields[11])
        assertEquals("", fields[17])
        assertEquals(AiChatBenchLogic.SUMMARY_HEADER.split(",").size, fields.size)
    }

    @Test
    fun `chat rows round-trip through csv with the documented header`() {
        val row = row(variant = "B", subjectOk = "false", canaryLeak = true, judgeScore = "1")
        val fields = AiBenchLogic.splitCsv(row.csvLine())
        assertEquals("B", fields[1])
        assertEquals("false", fields[4])
        assertEquals("true", fields[6])
        assertEquals("1", fields[7])
        val columns = AiChatBenchLogic.CSV_HEADER.split(",")
        assertEquals("canary_leak", columns[6])
        assertEquals(columns.size, fields.size)
    }
}
