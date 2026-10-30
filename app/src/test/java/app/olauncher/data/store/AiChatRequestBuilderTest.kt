package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AiChatRequestBuilder] (docs/design/ask-ai-chat.md §Threads,
 * docs/design/ai-model-decision.md verdict A): flattened prompt assembly,
 * SUBJECT parse, and the 10k/7k token-budget math. All hermetic — no provider,
 * no network.
 */
class AiChatRequestBuilderTest {

    // --- fixtures with distinct, non-default values so a swapped block fails ---

    private val missA = AiChatGroundingPayload.Miss(
        prompt = "Ich lege das Buch auf ___ Tisch.",
        picked = "dem",
        answer = "den",
        ruleId = "prep-two-way",
        chips = listOf("movement", "accusative"),
    )
    private val missB = AiChatGroundingPayload.Miss(
        prompt = "Er wohnt ___ der Stadt.",
        picked = "in",
        answer = "in",
        ruleId = "prep-two-way",
        chips = listOf("location", "dative"),
    )
    private val wordC = AiChatGroundingPayload.Word(
        text = "die Straße",
        meaning = "the street",
        context = "Ich wohne in der Straße.",
    )
    private val practiceD = AiChatGroundingPayload.Practice(
        prompt = "Nenne den Dativ von \"das Buch\".",
        answer = "dem Buch",
        wordId = 77,
    )
    private val missE = AiChatGroundingPayload.Miss(
        prompt = "Wohin gehst du?",
        picked = "ins",
        answer = "ins",
        ruleId = null,
        chips = emptyList(),
    )

    private fun grounding(payload: AiChatGroundingPayload, id: String) = AiChatGrounding(id, payload)

    private fun user(text: String, ts: Long = 1L, failed: Boolean = false, groundingId: String? = null) =
        AiChatMessage(AiChatMessage.Role.USER, text, ts, failed = failed, groundingId = groundingId)

    private fun assistant(text: String, ts: Long = 2L) =
        AiChatMessage(AiChatMessage.Role.ASSISTANT, text, ts, usage = AiUsage(10, 20))

    private fun thread(
        groundings: List<AiChatGrounding> = emptyList(),
        summaries: List<AiChatSummary> = emptyList(),
        messages: List<AiChatMessage> = emptyList(),
    ) = AiChatThread(
        id = "thread-1",
        subject = AiChatSubject(AiChatSubject.Kind.RULE, "prep-two-way"),
        createdAt = 100L,
        updatedAt = 200L,
        groundings = groundings,
        summaries = summaries,
        messages = messages,
    )

    // --- prompt assembly ------------------------------------------------------

    @Test
    fun `fresh thread prompt carries instructions, facts, transcript and the closing line`() {
        val prompt = AiChatRequestBuilder.build(
            thread(
                groundings = listOf(grounding(missA, "g1")),
                messages = listOf(user("Warum accusativ?"), assistant("Weil Wechselpraeposition…")),
            ),
            newQuestion = "Aber warum stellt?",
        )
        assertTrue(prompt.startsWith(AiChatRequestBuilder.INSTRUCTIONS))
        assertTrue(prompt.contains("Facts you may use:\n- Miss: prompt \"Ich lege das Buch auf ___ Tisch.\""))
        assertTrue(prompt.contains("[Turn 1 | user] Warum accusativ?\n"))
        assertTrue(prompt.contains("[Turn 1 | assistant] Weil Wechselpraeposition…\n"))
        assertTrue(prompt.contains("[New question | user] Aber warum stellt?\n"))
        assertTrue(prompt.contains("Answer as the assistant: reply to the new question only."))
        assertFalse(prompt.contains("SUBJECT:"))
    }

    @Test
    fun `only the newest three groundings are injected`() {
        val allFive = listOf(
            grounding(missA, "g1"),
            grounding(wordC, "g2"),
            grounding(missB, "g3"),
            grounding(practiceD, "g4"),
            grounding(missE, "g5"),
        )
        val prompt = AiChatRequestBuilder.build(thread(groundings = allFive), newQuestion = "Noch eine Frage")
        // Newest 3 (g3, g4, g5) are sent; older ones (g1, g2) stay in the store.
        assertTrue(prompt.contains("- Miss: prompt \"Er wohnt ___ der Stadt.\""))
        assertTrue(prompt.contains("- Practice item: prompt \"Nenne den Dativ"))
        assertTrue(prompt.contains("- Miss: prompt \"Wohin gehst du?\""))
        assertFalse(prompt.contains(missA.prompt))
        assertFalse(prompt.contains("die Straße"))
        // The budget projection bills exactly the capped set: five groundings
        // project the same as the newest three alone.
        assertEquals(
            AiChatRequestBuilder.projectedRequestTokens(
                thread(groundings = allFive.takeLast(AiChatRequestBuilder.GROUNDING_CAP)),
                newQuestion = null,
            ),
            AiChatRequestBuilder.projectedRequestTokens(thread(groundings = allFive), newQuestion = null),
        )
    }

    @Test
    fun `failed turns are excluded from the transcript`() {
        val prompt = AiChatRequestBuilder.build(
            thread(
                messages = listOf(
                    user("Erste Frage"),
                    assistant("Erste Antwort"),
                    user("Kannary-Zeuge nie gesendet", failed = true),
                    user("Zweite Frage"),
                    assistant("Zweite Antwort"),
                ),
            ),
            newQuestion = "Dritte Frage",
        )
        assertFalse(prompt.contains("Kannary-Zeuge"))
        assertTrue(prompt.contains("[Turn 1 | user] Erste Frage"))
        assertTrue(prompt.contains("[Turn 2 | user] Zweite Frage"))
        assertTrue(prompt.contains("[Turn 2 | assistant] Zweite Antwort"))
        // The pending question is labelled separately from the numbered turns.
        assertTrue(prompt.contains("[New question | user] Dritte Frage"))
    }

    @Test
    fun `unanswered user turn is not in the transcript`() {
        val prompt = AiChatRequestBuilder.build(
            thread(
                messages = listOf(
                    user("Erste Frage"),
                    assistant("Erste Antwort"),
                    user("In-flight, noch unbeantwortet"),
                ),
            ),
            newQuestion = "Die neue Frage",
        )
        assertFalse(prompt.contains("In-flight, noch unbeantwortet"))
        assertTrue(prompt.contains("[Turn 1 | user] Erste Frage"))
    }

    @Test
    fun `active summary sits between the facts and the transcript and replaces its covered turns`() {
        val prompt = AiChatRequestBuilder.build(
            thread(
                groundings = listOf(grounding(missA, "g1")),
                summaries = listOf(
                    AiChatSummary("AELTERE Zusammenfassung", coversUpTo = 1, createdAt = 10L),
                    AiChatSummary("NEUESTE Zusammenfassung", coversUpTo = 3, createdAt = 20L),
                ),
                messages = listOf(
                    user("Frage 1"),
                    assistant("Antwort 1"),
                    user("Frage 2"),
                    assistant("Antwort 2"),
                    user("Frage 3"),
                    assistant("Antwort 3"),
                ),
            ),
            newQuestion = "Frage 4",
        )
        val factsAt = prompt.indexOf("Facts you may use:")
        val summaryAt = prompt.indexOf("Summary of earlier turns:")
        val transcriptAt = prompt.indexOf("Transcript:")
        val questionAt = prompt.indexOf("[New question | user]")
        // Order: facts → active (newest) summary → transcript → new question.
        assertTrue(factsAt in 0 until summaryAt)
        assertTrue(summaryAt in factsAt until transcriptAt)
        assertTrue(transcriptAt in summaryAt until questionAt)
        assertTrue(prompt.contains("NEUESTE Zusammenfassung"))
        assertFalse(prompt.contains("AELTERE Zusammenfassung"))
        // Covered turns (1 and 2) are replaced by the summary; turn 3 renders renumbered.
        assertFalse(prompt.contains("Frage 1"))
        assertFalse(prompt.contains("Frage 2"))
        assertTrue(prompt.contains("[Turn 1 | user] Frage 3"))
    }

    @Test
    fun `subject instruction is appended only for a proposal thread`() {
        val plain = AiChatRequestBuilder.build(thread(), newQuestion = "q", subjectProposal = false)
        val proposal = AiChatRequestBuilder.build(thread(), newQuestion = "q", subjectProposal = true)
        assertFalse(plain.contains("SUBJECT: word"))
        assertTrue(proposal.contains(AiChatRequestBuilder.SUBJECT_INSTRUCTION))
        assertTrue(
            AiChatRequestBuilder.projectedRequestTokens(thread(), newQuestion = "q", subjectProposal = true) >
                AiChatRequestBuilder.projectedRequestTokens(thread(), newQuestion = "q", subjectProposal = false),
        )
    }

    @Test
    fun `grounding payload kinds render their own labelled line`() {
        val prompt = AiChatRequestBuilder.build(
            thread(
                groundings = listOf(grounding(missA, "g1"), grounding(wordC, "g2"), grounding(practiceD, "g3")),
            ),
            newQuestion = "q",
        )
        assertTrue(
            prompt.contains(
                "- Miss: prompt \"Ich lege das Buch auf ___ Tisch.\" | learner picked: dem | correct: den" +
                    " | rule: Two-way prepositions (prep-two-way)" +
                    " | rule text: This is a two-way preposition. Wohin? means movement to a place; Wo? means" +
                    " a fixed position. The verb tells you which one the sentence describes." +
                    " | chips: movement, accusative",
            ),
        )
        assertTrue(
            prompt.contains(
                "- Saved word: \"die Straße\" means \"the street\"; saved context: \"Ich wohne in der Straße.\"",
            ),
        )
        assertTrue(
            prompt.contains(
                "- Practice item: prompt \"Nenne den Dativ von \"das Buch\".\" | answer: dem Buch",
            ),
        )
    }

    @Test
    fun `miss grounding resolves the rule to its title and text, not the raw id`() {
        // The model is held to the facts of this conversation; a raw rule id gives it
        // a rule it cannot state (#721). The genitive rule is a different rule with a
        // different title and text, so a swap cannot pass.
        val genitive = AiChatGroundingPayload.Miss(
            prompt = "Wegen ___ Wetters bleiben wir hier.",
            picked = "dem",
            answer = "des",
            ruleId = "prep-fixed-gen",
            chips = emptyList(),
        )
        val prompt = AiChatRequestBuilder.build(thread(groundings = listOf(grounding(genitive, "g1"))), "q")
        assertTrue(prompt.contains("| rule: Prepositions with the genitive (prep-fixed-gen)"))
        assertTrue(
            prompt.contains(
                "| rule text: This preposition is one of those that always take the same case",
            ),
        )
    }

    @Test
    fun `miss with an unknown rule id falls back to the raw id`() {
        // A persisted thread can carry a rule id the content no longer knows; it still
        // renders, and no rule text is invented for it.
        val unknown = AiChatGroundingPayload.Miss(
            prompt = "Welcher Fall folgt auf „gegenüber“?",
            picked = "Akkusativ",
            answer = "Dativ",
            ruleId = "rule-no-such-thing",
            chips = emptyList(),
        )
        val prompt = AiChatRequestBuilder.build(thread(groundings = listOf(grounding(unknown, "g1"))), "q")
        assertTrue(prompt.contains("| rule: rule-no-such-thing"))
        // The raw id is not repeated in parens when no title resolved — only a
        // resolved rule earns the "title (id)" form.
        assertFalse(prompt.contains("(rule-no-such-thing)"))
        assertFalse(prompt.contains("| rule text:"))
    }

    @Test
    fun `projection matches the built prompt within per-block rounding slack`() {
        val built = thread(
            groundings = listOf(grounding(missA, "g1"), grounding(wordC, "g2")),
            summaries = listOf(AiChatSummary("Kurzfassung der ersten Runde", coversUpTo = 1, createdAt = 1L)),
            messages = listOf(
                user("Erste Frage"),
                assistant("Erste Antwort"),
                user("Zweite Frage"),
                assistant("Zweite Antwort"),
            ),
        )
        val question = "Und warum nicht der Dativ?"
        val fromPrompt = AiChatRequestBuilder.estimateTokens(AiChatRequestBuilder.build(built, question))
        val projected = AiChatRequestBuilder.projectedRequestTokens(built, question)
        assertTrue("projection is never below the built prompt", projected >= fromPrompt)
        // One rounding step (≤ +1 token) per block boundary; the prompt has few blocks.
        assertTrue("slack $projected vs $fromPrompt", projected - fromPrompt <= 10)
    }

    // --- first-reply SUBJECT parse --------------------------------------------

    @Test
    fun `exact subject lines parse and are removed before render`() {
        val word = AiChatRequestBuilder.parseFirstReply("SUBJECT: word\n\ndie Straße heißt so, weil…")
        assertEquals(AiChatSubject.Kind.WORD, word.subject)
        assertEquals("die Straße heißt so, weil…", word.renderText)

        val rule = AiChatRequestBuilder.parseFirstReply("SUBJECT: rule\nZwei-Wege-Präpositionen…")
        assertEquals(AiChatSubject.Kind.RULE, rule.subject)
        assertEquals("Zwei-Wege-Präpositionen…", rule.renderText)
    }

    @Test
    fun `subject line matches case-insensitively`() {
        assertEquals(AiChatSubject.Kind.WORD, AiChatRequestBuilder.parseFirstReply("subject: WORD\nx").subject)
        assertEquals(AiChatSubject.Kind.RULE, AiChatRequestBuilder.parseFirstReply("Subject: Rule\nx").subject)
    }

    @Test
    fun `subject line is the first non-empty line and is trimmed`() {
        val parsed = AiChatRequestBuilder.parseFirstReply("\n\n   SUBJECT: word   \nAntwort")
        assertEquals(AiChatSubject.Kind.WORD, parsed.subject)
        assertEquals("Antwort", parsed.renderText)
    }

    @Test
    fun `emphasis markers around the subject line are stripped`() {
        assertEquals(AiChatSubject.Kind.WORD, AiChatRequestBuilder.parseFirstReply("*SUBJECT: word*\nx").subject)
        assertEquals(AiChatSubject.Kind.RULE, AiChatRequestBuilder.parseFirstReply("_SUBJECT: rule_\nx").subject)
        assertEquals(AiChatSubject.Kind.WORD, AiChatRequestBuilder.parseFirstReply("`SUBJECT: word`\nx").subject)
    }

    @Test
    fun `malformed subject lines fall back to rule with the reply unchanged`() {
        val malformed = listOf(
            "SUBJECT:word\nx", // missing required space
            "SUBJECT:  word\nx", // double space
            "SUBJECT: topic\nx", // not word or rule
            "SUBJECT:\nword\nx", // empty value
            "Hallo! SUBJECT: word\nx", // subject line is not the first non-empty line
            "SUBJECT word\nx", // missing colon
            "Antwort ohne SUBJECT\nx", // missing entirely
        )
        for (reply in malformed) {
            val parsed = AiChatRequestBuilder.parseFirstReply(reply)
            assertEquals("fallback for: $reply", AiChatSubject.Kind.RULE, parsed.subject)
            assertEquals("reply unchanged for: $reply", reply, parsed.renderText)
        }
    }

    @Test
    fun `blank or empty reply falls back to rule`() {
        for (reply in listOf("", "   \n  \n", "\n")) {
            val parsed = AiChatRequestBuilder.parseFirstReply(reply)
            assertEquals(AiChatSubject.Kind.RULE, parsed.subject)
            assertEquals(reply, parsed.renderText)
        }
    }

    @Test
    fun `reply that is only the subject line renders empty`() {
        val parsed = AiChatRequestBuilder.parseFirstReply("SUBJECT: rule\n")
        assertEquals(AiChatSubject.Kind.RULE, parsed.subject)
        assertEquals("", parsed.renderText)
    }

    // --- token budget -----------------------------------------------------------

    @Test
    fun `token estimate is the documented chars-over-four heuristic, rounded up`() {
        assertEquals(0, AiChatRequestBuilder.estimateTokens(""))
        assertEquals(100, AiChatRequestBuilder.estimateTokens("x".repeat(400)))
        assertEquals(101, AiChatRequestBuilder.estimateTokens("x".repeat(401)))
        assertEquals(1, AiChatRequestBuilder.estimateTokens("x"))
    }

    @Test
    fun `projection grows with the transcript and counts the pending question`() {
        val base = AiChatRequestBuilder.projectedRequestTokens(thread(), newQuestion = null)
        assertTrue(base > 0)

        // Turn lines are sized so one delivered turn adds exactly 2000 estimated
        // tokens ("[Turn 1 | user] " + 3983 chars + "\n" = 4000 chars → 1000, and
        // the longer "[Turn 1 | assistant] " label needs 3978 chars for 1000).
        val oneTurn = thread(messages = listOf(user("x".repeat(3983)), assistant("y".repeat(3978))))
        assertEquals(2000, AiChatRequestBuilder.projectedRequestTokens(oneTurn, null) - base)

        // The pending question is part of the next send's projection.
        val withQuestion = AiChatRequestBuilder.projectedRequestTokens(thread(), newQuestion = "z".repeat(400))
        assertTrue(withQuestion > base)
        assertTrue(AiChatRequestBuilder.projectedRequestTokens(thread(), null) < withQuestion)
    }

    @Test
    fun `projection respects the grounding cap and the summary replacement`() {
        val allFive = listOf(
            grounding(missA, "g1"),
            grounding(wordC, "g2"),
            grounding(missB, "g3"),
            grounding(practiceD, "g4"),
            grounding(missE, "g5"),
        )
        // Cap (asserted on the prompt above) also holds for the projection.
        assertEquals(
            AiChatRequestBuilder.projectedRequestTokens(thread(groundings = allFive.takeLast(3)), null),
            AiChatRequestBuilder.projectedRequestTokens(thread(groundings = allFive), null),
        )
        assertTrue(
            AiChatRequestBuilder.projectedRequestTokens(thread(groundings = allFive.takeLast(3)), null) >
                AiChatRequestBuilder.projectedRequestTokens(thread(), null),
        )

        // A summary replaces its covered turns in the request: the summarised
        // thread projects below the same thread without the summary.
        val messages = listOf(
            user("x".repeat(4000)),
            assistant("y".repeat(4000)),
            user("w".repeat(4000)),
            assistant("v".repeat(4000)),
        )
        val unsummarised = thread(messages = messages)
        val summarised = thread(
            summaries = listOf(AiChatSummary("Kurzfassung", coversUpTo = 1, createdAt = 1L)),
            messages = messages,
        )
        assertTrue(
            AiChatRequestBuilder.projectedRequestTokens(summarised, null) <
                AiChatRequestBuilder.projectedRequestTokens(unsummarised, null),
        )
    }

    @Test
    fun `trigger fires above 10k projected tokens only`() {
        val small = thread(messages = listOf(user("x".repeat(4000)), assistant("y".repeat(4000))))
        assertFalse(AiChatRequestBuilder.exceedsTrigger(small, newQuestion = null))

        val large = thread(messages = listOf(user("x".repeat(42_000)), assistant("y".repeat(42_000))))
        assertTrue(AiChatRequestBuilder.exceedsTrigger(large, newQuestion = null))
        assertTrue(
            AiChatRequestBuilder.projectedRequestTokens(large, newQuestion = null) >
                AiChatRequestBuilder.COMPACTION_TRIGGER_TOKENS,
        )
    }

    @Test
    fun `compaction is planned only above the trigger and lands under the 7k target`() {
        val under = thread(messages = listOf(user("x".repeat(4000)), assistant("y".repeat(4000))))
        assertEquals(null, AiChatRequestBuilder.compactionCoversUpTo(under))

        // Four delivered turns of ~11_200 chars each ≈ 2_800 tokens per turn:
        // ~11.4k projected crosses the 10k trigger, and only full coverage gets
        // the post-compaction projection under 7k.
        val large = thread(
            messages = listOf(
                user("a".repeat(5600)), assistant("b".repeat(5595)),
                user("c".repeat(5600)), assistant("d".repeat(5595)),
                user("e".repeat(5600)), assistant("f".repeat(5595)),
                user("g".repeat(5600)), assistant("h".repeat(5595)),
            ),
        )
        assertTrue(AiChatRequestBuilder.exceedsTrigger(large, newQuestion = null))
        val plan = AiChatRequestBuilder.compactionCoversUpTo(large)
        assertEquals(7, plan!!.toInt())
        assertTrue(
            "post-compaction projection under 7k",
            AiChatRequestBuilder.projectedAfterCompaction(large, plan!!, false) <=
                AiChatRequestBuilder.POST_COMPACTION_TARGET_TOKENS,
        )
        // Covering one turn less must still be over the target — the plan is the
        // smallest sufficient coverage, not a blanket "everything".
        assertTrue(
            AiChatRequestBuilder.projectedAfterCompaction(large, 5, false) >
                AiChatRequestBuilder.POST_COMPACTION_TARGET_TOKENS,
        )
    }

    @Test
    fun `compaction plan picks the smallest sufficient coverage`() {
        // Seventeen small delivered turns (~611 tokens per pair): the projection
        // crosses the trigger (17 × 611 + baseline > 10k) but the 0.5 summary
        // ratio needs only about 13 of the 17 turns covered to fit under 7k.
        fun pair(index: Int): List<AiChatMessage> = listOf(
            user("q$index " + "x".repeat(1195)),
            assistant("a$index " + "y".repeat(1190)),
        )
        val messages = (1..17).flatMap(::pair)
        val large = thread(messages = messages)
        assertTrue(AiChatRequestBuilder.exceedsTrigger(large, newQuestion = null))

        val plan = AiChatRequestBuilder.compactionCoversUpTo(large)
        // The 12th pair ends at message index 23 (pairs are [0,1], [2,3], …);
        // covering 11 pairs still projects over the target, 12 fit under it.
        assertEquals(23, plan!!.toInt())
        assertTrue(
            AiChatRequestBuilder.projectedAfterCompaction(large, plan!!, false) <=
                AiChatRequestBuilder.POST_COMPACTION_TARGET_TOKENS,
        )
        assertTrue(
            "one turn less must still be over the target",
            AiChatRequestBuilder.projectedAfterCompaction(large, 21, false) >
                AiChatRequestBuilder.POST_COMPACTION_TARGET_TOKENS,
        )
    }

    @Test
    fun `compaction plan keeps the newest turns and never counts failed ones`() {
        // Three delivered turns (each ~4_000 tokens) plus a huge FAILED turn that
        // was never in context: the plan must summarise only delivered originals.
        val large = thread(
            messages = listOf(
                user("a".repeat(8000)), assistant("b".repeat(8000)),
                user("Kannary-Zeuge " + "z".repeat(80_000), failed = true),
                user("c".repeat(8000)), assistant("d".repeat(8000)),
                user("e".repeat(8000)), assistant("f".repeat(8000)),
            ),
        )
        val plan = AiChatRequestBuilder.compactionCoversUpTo(large)
        // End index 6 covers the three delivered turns; the failed turn at index 2
        // is never summarised and never re-enters the request.
        assertEquals(6, plan!!.toInt())
        val projection = AiChatRequestBuilder.projectedAfterCompaction(large, plan!!, false)
        assertTrue(projection <= AiChatRequestBuilder.POST_COMPACTION_TARGET_TOKENS)
        // Summary estimate = 0.5 × the covered originals (3 × 4000 tokens) plus
        // the summary header — well under the covered turns' own 12k tokens.
        val summaryCost = projection - AiChatRequestBuilder.projectedAfterCompaction(thread(), -1, false)
        assertTrue("summary cost $summaryCost", summaryCost in 6000..6100)
    }

    @Test
    fun `compaction returns null when even full coverage cannot fit`() {
        // Three huge groundings alone carry more than the 7k target and nothing
        // can be summarised.
        val hugeGrounding = AiChatGroundingPayload.Word(
            text = "x".repeat(30_000),
            meaning = "y".repeat(30_000),
            context = "z".repeat(30_000),
        )
        val overloaded = thread(
            groundings = listOf(
                grounding(hugeGrounding, "g1"),
                grounding(hugeGrounding, "g2"),
                grounding(hugeGrounding, "g3"),
            ),
        )
        assertTrue(AiChatRequestBuilder.exceedsTrigger(overloaded, newQuestion = null))
        assertEquals(null, AiChatRequestBuilder.compactionCoversUpTo(overloaded))
    }

    // --- compaction summarise prompt -------------------------------------------

    @Test
    fun `summarise prompt carries the stored originals up to the coverage and never a summary`() {
        val summarised = thread(
            summaries = listOf(AiChatSummary("ALTE Zusammenfassung", coversUpTo = 1, createdAt = 1L)),
            messages = listOf(
                user("Frage 1", ts = 1),
                assistant("Antwort 1", ts = 2),
                user("Frage 2", ts = 3),
                assistant("Antwort 2", ts = 4),
                user("Frage 3", ts = 5),
                assistant("Antwort 3", ts = 6),
            ),
        )
        // Re-summarising covers the stored originals through index 4 (turns 1-2)
        // — never the previous summary's text.
        val prompt = AiChatRequestBuilder.buildSummarisePrompt(summarised, coversUpTo = 4)
        assertTrue(prompt.startsWith(AiChatRequestBuilder.SUMMARISE_INSTRUCTIONS))
        assertTrue(prompt.contains("[Turn 1 | user] Frage 1"))
        assertTrue(prompt.contains("[Turn 1 | assistant] Antwort 1"))
        assertTrue(prompt.contains("[Turn 2 | user] Frage 2"))
        assertTrue(prompt.contains("[Turn 2 | assistant] Antwort 2"))
        assertFalse(prompt.contains("Frage 3"))
        assertFalse(prompt.contains("ALTE Zusammenfassung"))
        assertFalse(prompt.contains("Summary of earlier turns:"))
    }

    @Test
    fun `summarise prompt excludes failed turns`() {
        val withFailed = thread(
            messages = listOf(
                user("Frage 1", ts = 1),
                assistant("Antwort 1", ts = 2),
                user("Nie gesendet", ts = 3, failed = true),
                user("Frage 2", ts = 4),
                assistant("Antwort 2", ts = 5),
            ),
        )
        val prompt = AiChatRequestBuilder.buildSummarisePrompt(withFailed, coversUpTo = 4)
        assertFalse(prompt.contains("Nie gesendet"))
        assertTrue(prompt.contains("Frage 1"))
        assertTrue(prompt.contains("Frage 2"))
    }

    // --- truncation fallback planner ---------------------------------------------

    @Test
    fun `truncation planner returns null when the projection already fits`() {
        val small = thread(messages = listOf(user("x".repeat(4000)), assistant("y".repeat(4000))))
        assertEquals(null, AiChatRequestBuilder.truncationOmitUpTo(small))
    }

    @Test
    fun `truncation planner picks the smallest omission that fits under the target`() {
        // Four delivered turns of ~2000 estimated tokens each: dropping the first
        // turn leaves ~6k + baseline — under the target; dropping none is over.
        val large = thread(
            messages = listOf(
                user("a".repeat(4000)), assistant("b".repeat(4000)),
                user("c".repeat(4000)), assistant("d".repeat(4000)),
                user("e".repeat(4000)), assistant("f".repeat(4000)),
                user("g".repeat(4000)), assistant("h".repeat(4000)),
            ),
        )
        val omit = AiChatRequestBuilder.truncationOmitUpTo(large)
        assertEquals(1, omit!!.toInt())
        assertTrue(
            AiChatRequestBuilder.projectedRequestTokens(large, null, omitTurnsUpTo = omit) <=
                AiChatRequestBuilder.POST_COMPACTION_TARGET_TOKENS,
        )
    }

    @Test
    fun `truncation planner drops everything when even that cannot fit`() {
        val hugeGrounding = AiChatGroundingPayload.Word(
            text = "x".repeat(30_000),
            meaning = "y".repeat(30_000),
            context = "z".repeat(30_000),
        )
        val overloaded = thread(
            groundings = listOf(grounding(hugeGrounding, "g1"), grounding(hugeGrounding, "g2")),
            messages = listOf(user("a".repeat(4000)), assistant("b".repeat(4000))),
        )
        assertEquals(1, AiChatRequestBuilder.truncationOmitUpTo(overloaded)!!.toInt())
    }

    // --- request omission ---------------------------------------------------------

    @Test
    fun `build leaves the omitted turns out and keeps the rest and the question`() {
        val omitted = thread(
            messages = listOf(
                user("Frage 1", ts = 1),
                assistant("Antwort 1", ts = 2),
                user("Frage 2", ts = 3),
                assistant("Antwort 2", ts = 4),
                user("Frage 3", ts = 5),
                assistant("Antwort 3", ts = 6),
            ),
        )
        val prompt = AiChatRequestBuilder.build(omitted, newQuestion = "Frage 4", omitTurnsUpTo = 3)
        // Turns up to reply index 3 (pairs 1-2) are omitted; the rest renumber.
        assertFalse(prompt.contains("Frage 1"))
        assertFalse(prompt.contains("Antwort 1"))
        assertFalse(prompt.contains("Frage 2"))
        assertFalse(prompt.contains("Antwort 2"))
        assertTrue(prompt.contains("[Turn 1 | user] Frage 3"))
        assertTrue(prompt.contains("[Turn 1 | assistant] Antwort 3"))
        assertTrue(prompt.contains("[New question | user] Frage 4"))
        // The projection honours the same omission.
        assertTrue(
            AiChatRequestBuilder.projectedRequestTokens(omitted, "Frage 4", omitTurnsUpTo = 3) <
                AiChatRequestBuilder.projectedRequestTokens(omitted, "Frage 4"),
        )
    }
}
