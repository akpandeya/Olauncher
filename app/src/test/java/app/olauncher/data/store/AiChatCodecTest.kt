package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatCodecTest {

    // --- fixtures with distinct, non-default values so a swapped field fails ---

    private fun wordGrounding() = AiChatGrounding(
        id = "g-word",
        payload = AiChatGroundingPayload.Word(
            text = "die Straße",
            meaning = "the street",
            context = "Die Straße ist breit."
        )
    )

    private fun missGroundingWithRule() = AiChatGrounding(
        id = "g-miss-rule",
        payload = AiChatGroundingPayload.Miss(
            prompt = "Ich gehe ___ Schule.",
            picked = "der",
            answer = "zur",
            ruleId = "prep-two-way",
            chips = listOf("movement", "wohin?")
        )
    )

    private fun missGroundingNullRule() = AiChatGrounding(
        id = "g-miss-null",
        payload = AiChatGroundingPayload.Miss(
            prompt = "Das Buch liegt ___ Tisch.",
            picked = "den",
            answer = "dem",
            ruleId = null,
            chips = emptyList()
        )
    )

    private fun practiceGrounding() = AiChatGrounding(
        id = "g-practice",
        payload = AiChatGroundingPayload.Practice(
            prompt = "Ich ___ nach Berlin.",
            answer = "fahre",
            wordId = 4242L
        )
    )

    private fun fullThread() = AiChatThread(
        id = "thread-7f3a",
        subject = AiChatSubject(AiChatSubject.Kind.WORD, "die straße"),
        createdAt = 1_700_000_000_000,
        updatedAt = 1_700_000_500_000,
        groundings = listOf(
            wordGrounding(),
            missGroundingWithRule(),
            missGroundingNullRule(),
            practiceGrounding()
        ),
        summaries = listOf(
            AiChatSummary(text = "Earlier turns: the wohin/wo distinction.", coversUpTo = 1, createdAt = 1_700_000_300_000)
        ),
        messages = listOf(
            AiChatMessage(
                role = AiChatMessage.Role.USER,
                text = "Wann sagt man wohin?",
                ts = 1_700_000_100_000,
                groundingId = "g-miss-rule"
            ),
            AiChatMessage(
                role = AiChatMessage.Role.ASSISTANT,
                text = "Wohin takes the accusative.",
                ts = 1_700_000_110_000,
                usage = AiUsage(inputTokens = 1234, outputTokens = 567)
            ),
            AiChatMessage(
                role = AiChatMessage.Role.USER,
                text = "Und in welcher Richtung?",
                ts = 1_700_000_200_000,
                failed = true
            )
        )
    )

    private fun roundTrip(raw: String): AiChatCodec.AiChatDecoded {
        val decoded = AiChatCodec.decode(raw)
        val reEncoded = when (decoded) {
            is AiChatCodec.AiChatDecoded.Decoded -> AiChatCodec.encode(decoded.threads)
            is AiChatCodec.AiChatDecoded.Corrupt -> throw AssertionError("unexpected corrupt: ${decoded.reason}")
        }
        assertEquals(decoded, AiChatCodec.decode(reEncoded))
        return decoded
    }

    // --- round trips, one per payload kind ---

    @Test
    fun `word grounding round trip keeps text meaning and context`() {
        val decoded = roundTrip(AiChatCodec.encode(listOf(threadWith(wordGrounding())))) as AiChatCodec.AiChatDecoded.Decoded
        val payload = decoded.threads.single().groundings.single().payload as AiChatGroundingPayload.Word
        assertEquals("die Straße", payload.text)
        assertEquals("the street", payload.meaning)
        assertEquals("Die Straße ist breit.", payload.context)
    }

    @Test
    fun `miss grounding with rule id and chips round trips`() {
        val decoded = roundTrip(AiChatCodec.encode(listOf(threadWith(missGroundingWithRule())))) as AiChatCodec.AiChatDecoded.Decoded
        val grounding = decoded.threads.single().groundings.single()
        assertEquals("g-miss-rule", grounding.id)
        val payload = grounding.payload as AiChatGroundingPayload.Miss
        assertEquals("Ich gehe ___ Schule.", payload.prompt)
        assertEquals("der", payload.picked)
        assertEquals("zur", payload.answer)
        assertEquals("prep-two-way", payload.ruleId)
        assertEquals(listOf("movement", "wohin?"), payload.chips)
    }

    @Test
    fun `miss grounding with null rule id round trips as null`() {
        val decoded = roundTrip(AiChatCodec.encode(listOf(threadWith(missGroundingNullRule())))) as AiChatCodec.AiChatDecoded.Decoded
        val payload = decoded.threads.single().groundings.single().payload as AiChatGroundingPayload.Miss
        assertNull(payload.ruleId)
        assertEquals(emptyList<String>(), payload.chips)
    }

    @Test
    fun `practice grounding with non-default wordId round trips`() {
        val decoded = roundTrip(AiChatCodec.encode(listOf(threadWith(practiceGrounding())))) as AiChatCodec.AiChatDecoded.Decoded
        val payload = decoded.threads.single().groundings.single().payload as AiChatGroundingPayload.Practice
        assertEquals("Ich ___ nach Berlin.", payload.prompt)
        assertEquals("fahre", payload.answer)
        assertEquals(4242L, payload.wordId)
    }

    @Test
    fun `full thread round trip keeps every field`() {
        val decoded = roundTrip(AiChatCodec.encode(listOf(fullThread())))

        val envelope = org.json.JSONObject(AiChatCodec.encode(listOf(fullThread())))
        assertEquals(1, envelope.getInt("version"))

        val decodedThreads = (decoded as AiChatCodec.AiChatDecoded.Decoded).threads
        assertEquals(1, decodedThreads.size)
        val thread = decodedThreads.single()
        assertEquals("thread-7f3a", thread.id)
        assertEquals(AiChatSubject.Kind.WORD, thread.subject.kind)
        assertEquals("die straße", thread.subject.ref)
        assertEquals(1_700_000_000_000, thread.createdAt)
        assertEquals(1_700_000_500_000, thread.updatedAt)
        assertEquals(fullThread(), thread)

        // Summary survived with its coverage index.
        assertEquals(1, thread.summaries.size)
        assertEquals("Earlier turns: the wohin/wo distinction.", thread.summaries[0].text)
        assertEquals(1, thread.summaries[0].coversUpTo)
        assertEquals(1_700_000_300_000, thread.summaries[0].createdAt)

        // User turn with groundingId, assistant reply with usage, failed user turn.
        assertEquals(3, thread.messages.size)
        assertEquals(AiChatMessage.Role.USER, thread.messages[0].role)
        assertEquals("g-miss-rule", thread.messages[0].groundingId)
        assertNull(thread.messages[0].usage)
        assertEquals(false, thread.messages[0].failed)
        assertEquals(AiUsage(1234, 567), thread.messages[1].usage)
        assertNull(thread.messages[1].groundingId)
        assertEquals(1_700_000_200_000, thread.messages[2].ts)
        assertEquals(true, thread.messages[2].failed)
    }

    @Test
    fun `thread without optional fields decodes with defaults`() {
        val raw = """{"threads":[{"id":"t1","subject":{"kind":"rule","ref":"prep-two-way"},"createdAt":11}]}"""
        val decoded = AiChatCodec.decode(raw) as AiChatCodec.AiChatDecoded.Decoded
        val thread = decoded.threads.single()
        assertEquals(11, thread.updatedAt) // defaults to createdAt
        assertEquals(emptyList<AiChatGrounding>(), thread.groundings)
        assertEquals(emptyList<AiChatSummary>(), thread.summaries)
        assertEquals(emptyList<AiChatMessage>(), thread.messages)
    }

    @Test
    fun `all subject kinds round trip`() {
        val threads = listOf(
            AiChatThread("t-word", AiChatSubject(AiChatSubject.Kind.WORD, "haus"), 1, 2),
            AiChatThread("t-rule", AiChatSubject(AiChatSubject.Kind.RULE, "prep-two-way"), 3, 4),
            AiChatThread("t-topic", AiChatSubject(AiChatSubject.Kind.TOPIC, DrillType.CLOZE.id), 5, 6)
        )
        val decoded = AiChatCodec.decode(AiChatCodec.encode(threads)) as AiChatCodec.AiChatDecoded.Decoded
        assertEquals(threads, decoded.threads)
    }

    // --- strict decode: corruption is reported, never swallowed ---

    private fun assertCorrupt(raw: String, expectedFragment: String? = null) {
        val decoded = AiChatCodec.decode(raw)
        assertTrue("expected Corrupt for: $raw", decoded is AiChatCodec.AiChatDecoded.Corrupt)
        val reason = (decoded as AiChatCodec.AiChatDecoded.Corrupt).reason
        assertTrue("reason should name the problem, was: $reason", reason.isNotBlank())
        if (expectedFragment != null) {
            assertTrue("reason '$reason' should mention '$expectedFragment'", reason.contains(expectedFragment))
        }
    }

    @Test
    fun `not json is corrupt`() {
        assertCorrupt("this is not json")
        assertCorrupt("[1, 2, 3]") // a JSON array is not the envelope object
    }

    @Test
    fun `missing threads array is corrupt`() {
        assertCorrupt("""{"version": 1}""")
        assertCorrupt("""{"version": 1, "threads": "nope"}""")
    }

    @Test
    fun `thread missing id is corrupt`() {
        assertCorrupt(
            """{"threads":[{"subject":{"kind":"word","ref":"haus"},"createdAt":1}]}""",
            "id"
        )
    }

    @Test
    fun `thread missing subject is corrupt`() {
        assertCorrupt("""{"threads":[{"id":"t1","createdAt":1}]}""", "subject")
    }

    @Test
    fun `thread missing createdAt is corrupt`() {
        assertCorrupt("""{"threads":[{"id":"t1","subject":{"kind":"word","ref":"haus"}}]}""", "createdAt")
    }

    @Test
    fun `unknown subject kind is corrupt`() {
        assertCorrupt(
            """{"threads":[{"id":"t1","subject":{"kind":"fruit","ref":"apfel"},"createdAt":1}]}""",
            "kind"
        )
    }

    @Test
    fun `unknown grounding kind is corrupt`() {
        assertCorrupt(
            """{"threads":[{"id":"t1","subject":{"kind":"word","ref":"haus"},"createdAt":1,
                "groundings":[{"id":"g1","kind":"recipe","payload":{"text":"x"}}]}]}""",
            "grounding"
        )
    }

    @Test
    fun `payload missing a required field is corrupt`() {
        // miss without answer
        assertCorrupt(
            """{"threads":[{"id":"t1","subject":{"kind":"word","ref":"haus"},"createdAt":1,
                "groundings":[{"id":"g1","kind":"miss","payload":{"prompt":"p","picked":"a","chips":[]}}]}]}""",
            "answer"
        )
        // practice without wordId
        assertCorrupt(
            """{"threads":[{"id":"t1","subject":{"kind":"word","ref":"haus"},"createdAt":1,
                "groundings":[{"id":"g2","kind":"practice","payload":{"prompt":"p","answer":"a"}}]}]}"""
        )
        // word without context
        assertCorrupt(
            """{"threads":[{"id":"t1","subject":{"kind":"word","ref":"haus"},"createdAt":1,
                "groundings":[{"id":"g3","kind":"word","payload":{"text":"t","meaning":"m"}}]}]}"""
        )
    }

    @Test
    fun `unknown role is corrupt`() {
        assertCorrupt(
            """{"threads":[{"id":"t1","subject":{"kind":"word","ref":"haus"},"createdAt":1,
                "messages":[{"role":"system","text":"hi","ts":1}]}]}""",
            "role"
        )
    }

    @Test
    fun `coversUpTo outside message bounds is corrupt`() {
        // beyond the last message
        assertCorrupt(
            """{"threads":[{"id":"t1","subject":{"kind":"word","ref":"haus"},"createdAt":1,
                "summaries":[{"text":"s","coversUpTo":5,"createdAt":2}],
                "messages":[{"role":"user","text":"hi","ts":1}]}]}"""
        )
        // empty messages cannot cover anything
        assertCorrupt(
            """{"threads":[{"id":"t1","subject":{"kind":"word","ref":"haus"},"createdAt":1,
                "summaries":[{"text":"s","coversUpTo":0,"createdAt":2}],"messages":[]}]}"""
        )
    }

    @Test
    fun `empty absent or blank input decodes to zero threads`() {
        assertEquals(0, (AiChatCodec.decode(null) as AiChatCodec.AiChatDecoded.Decoded).threads.size)
        assertEquals(0, (AiChatCodec.decode("") as AiChatCodec.AiChatDecoded.Decoded).threads.size)
        assertEquals(0, (AiChatCodec.decode("   \n ") as AiChatCodec.AiChatDecoded.Decoded).threads.size)
        assertEquals(0, (AiChatCodec.decode("""{"threads":[]}""") as AiChatCodec.AiChatDecoded.Decoded).threads.size)
    }

    @Test
    fun `optional arrays must really be arrays`() {
        val base = """{"id":"t1","subject":{"kind":"word","ref":"haus"},"createdAt":1"""
        assertCorrupt("""{"threads":[$base,"messages":"chat"}]}""", "messages")
        assertCorrupt("""{"threads":[$base,"groundings":2}]}""", "groundings")
        assertCorrupt("""{"threads":[$base,"summaries":{"text":"s"}}]}""", "summaries")

        // Absent or JSON null still decodes to empty lists.
        val nulled = AiChatCodec.decode(
            """{"threads":[$base,"messages":null,"groundings":null,"summaries":null}]}"""
        ) as AiChatCodec.AiChatDecoded.Decoded
        val thread = nulled.threads.single()
        assertEquals(emptyList<AiChatMessage>(), thread.messages)
        assertEquals(emptyList<AiChatGrounding>(), thread.groundings)
        assertEquals(emptyList<AiChatSummary>(), thread.summaries)
    }

    @Test
    fun `wrongly typed optional scalars are corrupt`() {
        val base = """{"id":"t1","subject":{"kind":"word","ref":"haus"},"createdAt":1"""
        // failed must be a boolean, not a coercible string.
        assertCorrupt(
            """{"threads":[$base,"messages":[{"role":"user","text":"hi","ts":1,"failed":"yes"}]}]}""",
            "failed"
        )
        // groundingId must be a string, not a number silently read as null.
        assertCorrupt(
            """{"threads":[$base,"messages":[{"role":"user","text":"hi","ts":1,"groundingId":5}]}]}""",
            "groundingId"
        )
        // ruleId must be a string, not a number silently read as null.
        assertCorrupt(
            """{"threads":[$base,"groundings":[{"id":"g1","kind":"miss","payload":
                {"prompt":"p","picked":"a","answer":"b","ruleId":3,"chips":[]}}]}]}""",
            "ruleId"
        )
    }

    @Test
    fun `null optional scalars decode to their defaults`() {
        val raw = """{"threads":[{"id":"t1","subject":{"kind":"word","ref":"haus"},"createdAt":1,
            "messages":[{"role":"user","text":"hi","ts":1,"failed":null,"groundingId":null}],
            "groundings":[{"id":"g1","kind":"miss","payload":
                {"prompt":"p","picked":"a","answer":"b","ruleId":null,"chips":[]}}]}]}"""
        val decoded = AiChatCodec.decode(raw) as AiChatCodec.AiChatDecoded.Decoded
        val thread = decoded.threads.single()
        assertEquals(false, thread.messages.single().failed)
        assertNull(thread.messages.single().groundingId)
        assertNull((thread.groundings.single().payload as AiChatGroundingPayload.Miss).ruleId)
    }

    // --- derived helpers ---

    @Test
    fun `deliveredTurns counts answered user messages only`() {
        val messages = listOf(
            AiChatMessage(AiChatMessage.Role.USER, "q1", 1),
            AiChatMessage(AiChatMessage.Role.ASSISTANT, "a1", 2),
            AiChatMessage(AiChatMessage.Role.USER, "q2 failed", 3, failed = true),
            AiChatMessage(AiChatMessage.Role.ASSISTANT, "never", 4), // failed turn above is not a turn
            AiChatMessage(AiChatMessage.Role.USER, "q3 unanswered", 5)
        )
        val thread = AiChatThread("t", AiChatSubject(AiChatSubject.Kind.WORD, "haus"), 0, 1, messages = messages)
        assertEquals(1, AiChatThreads.deliveredTurns(thread))
    }

    @Test
    fun `deliveredTurns counts summarised turns`() {
        // Summary replaces turns in the request only; the transcript keeps them.
        val messages = listOf(
            AiChatMessage(AiChatMessage.Role.USER, "q1", 1),
            AiChatMessage(AiChatMessage.Role.ASSISTANT, "a1", 2)
        )
        val thread = AiChatThread(
            "t", AiChatSubject(AiChatSubject.Kind.WORD, "haus"), 0, 1,
            summaries = listOf(AiChatSummary("sum", 1, 3)),
            messages = messages
        )
        assertEquals(1, AiChatThreads.deliveredTurns(thread))
    }

    @Test
    fun `deliveredTurns is zero for empty and failed-only threads`() {
        val empty = AiChatThread("t", AiChatSubject(AiChatSubject.Kind.WORD, "haus"), 0, 1)
        assertEquals(0, AiChatThreads.deliveredTurns(empty))
        val failedOnly = AiChatThread(
            "t", AiChatSubject(AiChatSubject.Kind.WORD, "haus"), 0, 1,
            messages = listOf(AiChatMessage(AiChatMessage.Role.USER, "q", 1, failed = true))
        )
        assertEquals(0, AiChatThreads.deliveredTurns(failedOnly))
    }

    @Test
    fun `latestThread returns newest by updatedAt for the subject`() {
        val subject = AiChatSubject(AiChatSubject.Kind.RULE, "prep-two-way")
        val older = AiChatThread("old", subject, 1, 100)
        val newer = AiChatThread("new", subject, 2, 300)
        val other = AiChatThread("other", AiChatSubject(AiChatSubject.Kind.RULE, "articles"), 3, 400)
        assertEquals("new", AiChatThreads.latestThread(listOf(older, other, newer), subject)?.id)
    }

    @Test
    fun `latestThread includes a thread whose only turn failed`() {
        val subject = AiChatSubject(AiChatSubject.Kind.WORD, "haus")
        val failedOnly = AiChatThread(
            "failed", subject, 1, 10,
            messages = listOf(AiChatMessage(AiChatMessage.Role.USER, "q", 2, failed = true))
        )
        assertEquals(failedOnly, AiChatThreads.latestThread(listOf(failedOnly), subject))
    }

    @Test
    fun `latestThread is null when no thread matches the subject`() {
        val subject = AiChatSubject(AiChatSubject.Kind.TOPIC, DrillType.CLOZE.id)
        val word = AiChatThread("w", AiChatSubject(AiChatSubject.Kind.WORD, "haus"), 1, 2)
        assertNull(AiChatThreads.latestThread(listOf(word), subject))
        assertNull(AiChatThreads.latestThread(emptyList(), subject))
    }

    private fun threadWith(grounding: AiChatGrounding): AiChatThread {
        return AiChatThread(
            id = "t-g",
            subject = AiChatSubject(AiChatSubject.Kind.WORD, "haus"),
            createdAt = 1,
            updatedAt = 2,
            groundings = listOf(grounding)
        )
    }
}
