package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeContractTest {

    private fun word(id: Long, text: String, context: String = "") = Word(
        id = id,
        text = text,
        context = context,
        dueDay = 100,
        addedAt = 200,
    )

    /** A word whose saved context contains the exact word. */
    private fun wordWithContext(id: Long, text: String) =
        word(id, text, context = "Die Arbeit $text auf seiner Unterschrift.")

    private fun response(vararg items: String) = """{"items": [${items.joinToString(",")}]}"""

    private fun item(
        wordId: Long,
        type: String,
        prompt: String = "Er ___ das Wort heute.",
        answer: String = "benutzt",
        choices: List<String> = listOf(answer, "benutzen", "benutzte"),
        hint: String? = null,
        extra: String = "",
    ): String {
        val hintJson = if (hint != null) ", \"hint\": \"$hint\"" else ""
        return "{\"word_id\": $wordId, \"type\": \"$type\", \"prompt\": \"$prompt\", " +
            "\"answer\": \"$answer\", \"choices\": ${choices.joinToString(", ", "[", "]") { "\"$it\"" }}" +
            "$hintJson$extra}"
    }

    // ------------------------------------------------------------------
    // buildPrompt
    // ------------------------------------------------------------------

    @Test
    fun `buildPrompt snapshot asks for three items per word with context and types`() {
        val words = listOf(
            word(7, "Unterschrift", context = "Er setzte seine Unterschrift unter den Brief."),
            word(9, "vertrauen", context = ""),
        )
        val types = listOf(DrillType.CLOZE, DrillType.PLURALS, DrillType.PREPOSITIONS)
        val prompt = PracticeContract.buildPrompt(words, types)
        assertEquals(
            """
            You create German practice drills from saved words.

            For every word below, produce exactly 3 items: one of type "cloze" and two of the other allowed types. Ground every item in the word's saved context.

            Allowed types: cloze, plurals, prepositions

            Words:
            - word_id 7: Unterschrift | context: Er setzte seine Unterschrift unter den Brief.
            - word_id 9: vertrauen

            Respond with one JSON object and nothing else:
            {"items": [{"word_id": 1, "type": "cloze", "prompt": "...", "answer": "...", "choices": ["...", "...", "..."], "hint": "optional"}]}

            Rules:
            - cloze prompts use ___ for the gap
            - the answer must be one of the choices; use 2-4 distinct choices
            - the hint is optional and must never reveal the answer
            - every word_id from the list above gets its 3 items
            """.trimIndent() + "\n",
            prompt,
        )
    }

    @Test
    fun `buildPrompt is deterministic for the same input`() {
        val words = listOf(word(1, "Haus", context = "Das Haus ist alt."))
        val types = listOf(DrillType.CLOZE, DrillType.ARTICLES)
        assertEquals(PracticeContract.buildPrompt(words, types), PracticeContract.buildPrompt(words, types))
    }

    // ------------------------------------------------------------------
    // parse
    // ------------------------------------------------------------------

    @Test
    fun `valid response parses to three items per word`() {
        val words = listOf(wordWithContext(1, "insistieren"), wordWithContext(2, "Vertrauen"))
        val json = response(
            item(1, "cloze"), item(1, "prepositions"), item(1, "plurals", hint = "Mehrzahl"),
            item(2, "cloze"), item(2, "genitive"), item(2, "konjunktiv_ii"),
        )
        val result = PracticeContract.parse(json, words, model = "glm-5.3-flash")
        assertEquals(6, result.items.size)
        assertTrue(result.clozeFallbacks.isEmpty())
        assertTrue(result.rejected.isEmpty())
        assertEquals(1L, result.items[0].wordId)
        assertEquals(DrillType.CLOZE, result.items[0].type)
        assertEquals("word", result.items[0].source)
        assertEquals("glm-5.3-flash", result.items[0].model)
        assertEquals("Mehrzahl", result.items[2].hint)
    }

    @Test
    fun `unknown type becomes a cloze fallback from the saved context`() {
        val words = listOf(word(1, "Unterschrift", context = "Er setzte seine Unterschrift unter den Brief."))
        val json = response(item(1, "bogus_type"))
        val result = PracticeContract.parse(json, words)
        assertEquals(0, result.items.size)
        assertEquals(3, result.clozeFallbacks.size)
        val fallback = result.clozeFallbacks[0]
        assertEquals(DrillType.CLOZE, fallback.type)
        assertEquals(1L, fallback.wordId)
        assertEquals("Er setzte seine ___ unter den Brief.", fallback.prompt)
        assertEquals("Unterschrift", fallback.answer)
        assertTrue(fallback.answer in fallback.choices)
        assertEquals(null, fallback.hint)
    }

    @Test
    fun `item failing validate becomes a cloze fallback from the saved context`() {
        val words = listOf(word(1, "Unterschrift", context = "Er setzte seine Unterschrift unter den Brief."))
        // The answer is not among the choices, so DrillItem.validate() fails.
        val json = response(item(1, "cloze", answer = "fehlt", choices = listOf("anders", "andere")))
        val result = PracticeContract.parse(json, words)
        assertEquals(0, result.items.size)
        assertEquals(3, result.clozeFallbacks.size)
        assertTrue(result.clozeFallbacks.all { it.wordId == 1L && it.type == DrillType.CLOZE })
        assertEquals("Unterschrift", result.clozeFallbacks[0].answer)
    }

    @Test
    fun `missing word id gets three cloze fallbacks and is never dropped`() {
        val words = listOf(wordWithContext(1, "insistieren"), word(2, "Vertrauen", context = ""))
        val json = response(item(1, "cloze"), item(1, "plurals"))
        val result = PracticeContract.parse(json, words)
        assertEquals(2, result.items.size)
        assertEquals(3, result.clozeFallbacks.count { it.wordId == 2L })
        // No usable context: a bare gap with the word as the answer.
        val bare = result.clozeFallbacks.first { it.wordId == 2L }
        assertEquals("___", bare.prompt)
        assertEquals("Vertrauen", bare.answer)
    }

    @Test
    fun `duplicate word id items land in one pool and fill the slots`() {
        val words = listOf(wordWithContext(1, "insistieren"))
        val json = response(
            item(1, "cloze"), item(1, "cloze"), item(1, "plurals"), item(1, "genitive"),
        )
        val result = PracticeContract.parse(json, words)
        // Four valid items for one word id: all kept, at least 3 satisfies the pool.
        assertEquals(4, result.items.size)
        assertTrue(result.clozeFallbacks.isEmpty())
    }

    @Test
    fun `two valid items leave one slot for a fallback`() {
        val words = listOf(wordWithContext(1, "insistieren"))
        val json = response(item(1, "cloze"), item(1, "plurals"))
        val result = PracticeContract.parse(json, words)
        assertEquals(2, result.items.size)
        assertEquals(1, result.clozeFallbacks.size)
        assertEquals(1L, result.clozeFallbacks[0].wordId)
    }

    @Test
    fun `truncated json is rejected before any item is produced`() {
        val truncated = """{"items": [{"word_id": 1, "type": "cloze", "prompt": "Er ___"""
        val result = PracticeContract.parse(truncated, listOf(wordWithContext(1, "insistieren")))
        assertTrue(result.items.isEmpty())
        assertTrue(result.clozeFallbacks.isEmpty())
        assertEquals(1, result.rejected.size)
        assertTrue(result.rejected[0].contains("Not valid JSON"))
    }

    @Test
    fun `extra json keys are ignored`() {
        val words = listOf(wordWithContext(1, "insistieren"))
        val json = "{\"model_used\": \"glm-5.3-flash\", \"billing_cost\": \"0.0002\", " +
            "\"items\": [${item(1, "cloze", extra = ", \"temperature\": 0.7")}, " +
            "${item(1, "plurals", extra = ", \"usage\": {\"tokens\": 12}")}, ${item(1, "genitive")}]}"
        val result = PracticeContract.parse(json, words)
        assertEquals(3, result.items.size)
        assertTrue(result.clozeFallbacks.isEmpty())
        assertTrue(result.rejected.isEmpty())
    }

    @Test
    fun `items for unknown word ids are rejected and the word still gets its pool`() {
        val words = listOf(wordWithContext(1, "insistieren"))
        val json = response(item(99, "cloze"))
        val result = PracticeContract.parse(json, words)
        assertTrue(result.items.isEmpty())
        assertEquals(3, result.clozeFallbacks.size)
        assertTrue(result.clozeFallbacks.all { it.wordId == 1L })
        assertEquals(1, result.rejected.size)
        assertTrue(result.rejected[0].contains("unknown or missing word_id"))
    }

    @Test
    fun `a missing items array falls back to a cloze per word`() {
        val words = listOf(wordWithContext(1, "insistieren"), wordWithContext(2, "Vertrauen"))
        val json = """{"note": "no items here"}"""
        val result = PracticeContract.parse(json, words)
        assertTrue(result.items.isEmpty())
        assertEquals(6, result.clozeFallbacks.size)
    }

    @Test
    fun `fallback blanks an inflected context form via the longest shared stem`() {
        // The saved word is the infinitive, the context holds the conjugated form.
        val words = listOf(word(1, "insistieren", context = "Die Arbeit insistiert auf seiner Unterschrift."))
        val result = PracticeContract.parse(response(), words)
        assertEquals(3, result.clozeFallbacks.size)
        assertEquals("Die Arbeit ___ auf seiner Unterschrift.", result.clozeFallbacks[0].prompt)
        assertEquals("insistiert", result.clozeFallbacks[0].answer)
        assertTrue(result.clozeFallbacks[0].answer in result.clozeFallbacks[0].choices)
    }

    @Test
    fun `fallback choices carry no case-flip distractor`() {
        val words = listOf(wordWithContext(1, "Haus"))
        val result = PracticeContract.parse(response(), words)
        val choices = result.clozeFallbacks[0].choices
        // answer first, then the word and its n-variant — never an uppercase flip.
        assertEquals(listOf("Haus", "Hausn"), choices)
        assertTrue(choices.none { it == "HAUS" })
    }

    @Test
    fun `a blank-text word gets a deterministic minimal fallback instead of nothing`() {
        val withMeaning = word(4, text = "  ", context = "Etwas Kontext.").copy(meaning = "die Wiederholung")
        val result = PracticeContract.parse(response(), listOf(withMeaning))
        // "Never dropped" holds even without usable word text (review R1 #9).
        assertEquals(3, result.clozeFallbacks.size)
        val first = result.clozeFallbacks[0]
        assertEquals("___", first.prompt)
        assertEquals("die Wiederholung", first.answer)
        assertTrue(first.answer in first.choices)
        assertTrue(first.choices.toSet().size == first.choices.size)
        // Deterministic: the same word always builds the same card.
        val again = PracticeContract.parse(response(), listOf(withMeaning))
        assertEquals(result.clozeFallbacks, again.clozeFallbacks)

        // Without a meaning either, the word id stands in as the answer.
        val bare = PracticeContract.parse(response(), listOf(word(5, text = "", context = "")))
        assertEquals(3, bare.clozeFallbacks.size)
        assertEquals("5", bare.clozeFallbacks[0].answer)
    }
}
