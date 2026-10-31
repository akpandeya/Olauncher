package app.olauncher.data.store

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        full: String? = null,
        extra: String = "",
    ): String {
        val hintJson = if (hint != null) ", \"hint\": \"$hint\"" else ""
        val fullJson = if (full != null) ", \"full\": \"$full\"" else ""
        return "{\"word_id\": $wordId, \"type\": \"$type\", \"prompt\": \"$prompt\", " +
            "\"answer\": \"$answer\", \"choices\": ${choices.joinToString(", ", "[", "]") { "\"$it\"" }}" +
            "$fullJson$hintJson$extra}"
    }

    // ------------------------------------------------------------------
    // buildPrompt
    // ------------------------------------------------------------------

    @Test
    fun `buildPrompt snapshot asks for three cards with the v2 rules and quoted context`() {
        val words = listOf(
            word(7, "Unterschrift", context = "Er setzte seine Unterschrift unter den Brief."),
            word(9, "vertrauen", context = ""),
        )
        val types = listOf(DrillType.CLOZE, DrillType.PLURALS, DrillType.PREPOSITIONS)
        val prompt = PracticeContract.buildPrompt(words, types)
        assertEquals(
            """
            You write German practice cards from words a learner saved while reading. The learner sees only "prompt" and "hint", says the answer aloud, then sees "answer" and grades themself. Write prompts and hints in German.

            For every word below, write exactly 3 cards:
            1. One "cloze": the word's context sentence with the word, in the form used there, replaced by ___.
            2. Two cards of other types from this list. Pick only types that fit this word: cloze, plurals, prepositions
               If only one other type fits, make the last card a second "cloze" that blanks a different form of the word.

            Rules for every card:
            - The gap tests the saved word itself: one of its forms, or the article, ending or preposition attached to it. Never test another word in the sentence.
            - Reuse the context sentence. You may drop whole clauses, but keep the words around the gap unchanged and invent no facts. Only if a word has no context, write one short, natural sentence.
            - Exactly one ___ per prompt. Putting the answer into ___ must give a correct German sentence with normal capitalisation ("Die …" at sentence start).
            - full: the prompt with its ___ replaced by the answer — exactly the sentence the learner reads when the gap is filled, not the original context sentence.
            - The 3 cards of one word have 3 different answers.
            - Neither prompt nor hint may contain the answer or spell it out (no "bei + dem" for "beim").
            - If the gap tests a form of a verb and the sentence does not name the verb, put its base form in brackets right after the gap: "___ (verzichten) auf unerlaubte Zölle!" → "Verzichte". Without it many verbs fit the gap and the card cannot be answered from what it shows. A card whose answer is the base form itself needs no bracket; a bracketed base always differs from the answer.
            - hint: a German synonym or short definition, or tense and person ("Präsens, 3. Person Singular"). At most 6 words, no rule explanations.
            - "type" names what the gap tests: articles → an article; plurals → a plural noun; conjugation, praeteritum, modal_verbs → a finite verb form; prepositions, verb_prepositions → a preposition; case_endings, adjective_endings, genitive, n_declension → a form whose ending is the point; any other type → its grammar point.

            Type rules:
            - Separable verb in a main clause: the gap takes only the finite verb; the prefix stays in the prompt at the clause end. "Das Personal ___ von 3.000 auf 6.000 an." → "wächst". After a modal or in a subordinate clause the verb is one word: "…, weil das Personal ___." → "anwächst".
            - articles: blank an article only if exactly one article fits ("___ eigenes Zentrum" → "ein"). If a definite and an indefinite article would both fit, write "bestimmter Artikel" or "unbestimmter Artikel" as the hint.
            - word_order: take one main clause of 4-8 words from the context. prompt = "Ordne: " + its words shuffled, one word per tile, separated by " / ". answer = exactly those tiles in the right order: same words, same spelling, nothing added, dropped, split or merged, no comma. choices = the answer plus 1-2 wrong orders of the same tiles.
            - All other types: choices = the answer plus 1-3 wrong forms of the same word (e.g. "weigert", "weigern", "weigerst").

            Words:
            - word_id 7: Unterschrift
              context: "Er setzte seine Unterschrift unter den Brief."
            - word_id 9: vertrauen

            Respond with one JSON object and nothing else:
            {"items": [{"word_id": 1, "type": "cloze", "prompt": "...", "answer": "...", "choices": ["...", "..."], "full": "...", "hint": "..."}]}
            """.trimIndent() + "\n",
            prompt,
        )
    }

    @Test
    fun `buildPrompt is deterministic for the same input`() {
        val words = listOf(word(1, "Haus", context = "Das Haus ist alt."))
        val types = listOf(DrillType.CLOZE, DrillType.ARTICLES)
        assertEquals(PracticeContract.buildPrompt(words, types), PracticeContract.buildPrompt(words, types))
        assertEquals(
            PracticeContract.buildPrompt(words, types, mapOf(1L to listOf(DrillType.ARTICLES))),
            PracticeContract.buildPrompt(words, types, mapOf(1L to listOf(DrillType.ARTICLES))),
        )
    }

    @Test
    fun `buildPrompt sends the meaning line only when non-blank and omits blank context`() {
        val prompt = PracticeContract.buildPrompt(
            listOf(word(1, "Haus", context = "  ").copy(meaning = "das Gebäude")),
            listOf(DrillType.CLOZE),
        )
        assertTrue(prompt.contains("  meaning: das Gebäude\n"))
        assertFalse(prompt.contains("context:"))
        val withoutMeaning = PracticeContract.buildPrompt(
            listOf(word(2, "Weg", context = "Der Weg ist kurz.")),
            listOf(DrillType.CLOZE),
        )
        assertFalse(withoutMeaning.contains("meaning:"))
    }

    @Test
    fun `buildPrompt quotes the context so pipes and newlines cannot break the line`() {
        val prompt = PracticeContract.buildPrompt(
            listOf(word(1, "Weg", context = "Der Weg | geht\nweiter")),
            listOf(DrillType.CLOZE),
        )
        // The whole context stays on one line, JSON-escaped.
        assertTrue(prompt.contains("  context: \"Der Weg | geht\\nweiter\"\n"))
    }

    @Test
    fun `buildPrompt carries the per-word narrowed types line`() {
        val prompt = PracticeContract.buildPrompt(
            listOf(word(1, "Unterschrift", context = "Kontext.")),
            DrillType.values().toList(),
            typesByWord = mapOf(1L to listOf(DrillType.ARTICLES, DrillType.PLURALS)),
        )
        assertTrue(prompt.contains("  types: articles, plurals\n"))
        // A word without an entry gets none.
        val without = PracticeContract.buildPrompt(
            listOf(word(1, "Unterschrift", context = "Kontext.")),
            DrillType.values().toList(),
        )
        assertFalse(without.contains("\n  types:"))
    }

    // ------------------------------------------------------------------
    // response schema + schema modes
    // ------------------------------------------------------------------

    @Test
    fun `the response schema matches the v2 contract shape with the full field`() {
        val schema = JSONObject(PracticeContract.responseSchemaJson())
        assertEquals("object", schema.getString("type"))
        val item = schema.getJSONObject("properties").getJSONObject("items").getJSONObject("items")
        val required = item.getJSONArray("required")
        assertEquals(
            listOf("word_id", "type", "prompt", "answer", "choices", "full"),
            (0 until required.length()).map { required.getString(it) },
        )
        val types = item.getJSONObject("properties").getJSONObject("type").getJSONArray("enum")
        assertEquals(
            DrillType.values().map { it.id },
            (0 until types.length()).map { types.getString(it) },
        )
        // Strict convention: nested objects also forbid extra keys.
        assertFalse(item.optBoolean("additionalProperties", true))
        assertFalse(schema.optBoolean("additionalProperties", true))
    }

    @Test
    fun `schema fallback fires on capability and context codes only`() {
        fun error(code: String?) = AiResult.Error(AiResult.ErrorKind.BAD_RESPONSE, "m", errorCode = code)
        assertEquals(
            PracticeSchemaModes.JSON_OBJECT,
            PracticeSchemaModes.fallbackMode(PracticeSchemaModes.JSON_SCHEMA, error("model_not_capable")),
        )
        assertEquals(
            PracticeSchemaModes.JSON_OBJECT,
            PracticeSchemaModes.fallbackMode(PracticeSchemaModes.JSON_SCHEMA, error("context_length_exceeded")),
        )
        assertNull("a truncation is a real failure, not a mode problem", PracticeSchemaModes.fallbackMode(PracticeSchemaModes.JSON_SCHEMA, error("truncated")))
        assertNull(PracticeSchemaModes.fallbackMode(PracticeSchemaModes.JSON_SCHEMA, error(null)))
        assertNull("json_object has nothing to fall back from", PracticeSchemaModes.fallbackMode(PracticeSchemaModes.JSON_OBJECT, error("model_not_capable")))
    }

    @Test
    fun `the response-format mode is a per-model preference`() {
        // Round-3 final review condition 1: glm-5.3-flash's passing benchmark
        // configuration is json_object (97.2 % validity; the schema runs lost
        // whole batches to runaway generations), so production starts there.
        assertEquals(PracticeSchemaModes.JSON_OBJECT, PracticeSchemaModes.initialMode("glm-5.3-flash"))
        // Every other model keeps the previous strict-schema behaviour.
        assertEquals(PracticeSchemaModes.JSON_SCHEMA, PracticeSchemaModes.initialMode("gemma-4-31b"))
        assertEquals(PracticeSchemaModes.JSON_SCHEMA, PracticeSchemaModes.initialMode("glm-5.3"))
        assertEquals(PracticeSchemaModes.JSON_SCHEMA, PracticeSchemaModes.initialMode(""))
        // From json_object the capability fallback has nothing to fall back to.
        assertNull(
            PracticeSchemaModes.fallbackMode(
                PracticeSchemaModes.initialMode("glm-5.3-flash"),
                AiResult.Error(AiResult.ErrorKind.BAD_RESPONSE, "m", errorCode = "model_not_capable"),
            ),
        )
    }

    // ------------------------------------------------------------------
    // parse
    // ------------------------------------------------------------------

    @Test
    fun `valid response parses to three items per word`() {
        val words = listOf(wordWithContext(1, "insistieren"), wordWithContext(2, "Vertrauen"))
        val json = response(
            item(
                1, "cloze", prompt = "Er ___ (insistieren) auf seiner Meinung.",
                answer = "insistiert", choices = listOf("insistiert", "insistieren", "insistiertest"),
            ),
            item(1, "prepositions", answer = "auf", choices = listOf("auf", "an", "in")),
            item(1, "plurals", answer = "Arbeiten", choices = listOf("Arbeiten", "Arbeit", "Arbeitet"), hint = "Mehrzahl"),
            item(2, "cloze", answer = "Vertrauen", choices = listOf("Vertrauen", "vertraut", "vertrauen")),
            item(2, "genitive", answer = "des", choices = listOf("des", "der", "dem")),
            item(2, "konjunktiv_ii", answer = "würde", choices = listOf("würde", "will", "soll")),
        )
        val result = PracticeContract.parse(json, words, model = "glm-5.3-flash")
        assertEquals(6, result.items.size)
        assertTrue(result.clozeFallbacks.isEmpty())
        assertTrue(result.rejected.isEmpty())
        assertTrue(result.rejections.isEmpty())
        assertFalse(result.malformed)
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
        assertEquals("unknown_type", result.rejections.single().filter)
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
        assertEquals("contract", result.rejections.single().filter)
    }

    @Test
    fun `missing word id gets three cloze fallbacks and is never dropped`() {
        val words = listOf(wordWithContext(1, "insistieren"), word(2, "Vertrauen", context = ""))
        val json = response(
            item(
                1, "cloze", prompt = "Er ___ (insistieren) auf seiner Meinung.",
                answer = "insistiert", choices = listOf("insistiert", "insistieren"),
            ),
            item(1, "plurals", answer = "Arbeiten", choices = listOf("Arbeiten", "Arbeit")),
        )
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
            item(
                1, "cloze", prompt = "Er ___ (insistieren) auf seiner Meinung.",
                answer = "insistiert", choices = listOf("insistiert", "insistieren"),
            ),
            item(
                1, "cloze", prompt = "Die ___ (insistieren) dauerte lange.",
                answer = "Arbeit", choices = listOf("Arbeit", "Arbeiten"),
            ),
            item(1, "plurals", answer = "Arbeiten", choices = listOf("Arbeiten", "Arbeit")),
            item(1, "genitive", answer = "der", choices = listOf("der", "des")),
        )
        val result = PracticeContract.parse(json, words)
        // Four valid items for one word id: all kept, at least 3 satisfies the pool.
        assertEquals(4, result.items.size)
        assertTrue(result.clozeFallbacks.isEmpty())
    }

    @Test
    fun `two valid items leave one slot for a fallback`() {
        val words = listOf(wordWithContext(1, "insistieren"))
        val json = response(
            item(
                1, "cloze", prompt = "Er ___ (insistieren) auf seiner Meinung.",
                answer = "insistiert", choices = listOf("insistiert", "insistieren"),
            ),
            item(1, "plurals", answer = "Arbeiten", choices = listOf("Arbeiten", "Arbeit")),
        )
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
        assertTrue(result.malformed)
        assertEquals(1, result.rejected.size)
        assertTrue(result.rejected[0].contains("Not valid JSON"))
    }

    @Test
    fun `padFallbacks false leaves the missing slots empty for the caller's retry pass`() {
        val words = listOf(wordWithContext(1, "insistieren"))
        val json = response(
            item(
                1, "cloze", prompt = "Er ___ (insistieren) auf seiner Meinung.",
                answer = "insistiert", choices = listOf("insistiert", "insistieren"),
            ),
        )
        val result = PracticeContract.parse(json, words, padFallbacks = false)
        assertEquals(1, result.items.size)
        assertTrue(result.clozeFallbacks.isEmpty())
        // The generator rebuilds the missing slots itself after the retry pass.
        val fallbacks = PracticeContract.fallbacksFor(words[0], 2, "word")
        assertEquals(2, fallbacks.size)
        assertEquals("Die Arbeit ___ auf seiner Unterschrift.", fallbacks[0].prompt)
    }

    @Test
    fun `extra json keys are ignored`() {
        val words = listOf(wordWithContext(1, "insistieren"))
        val json = "{\"model_used\": \"glm-5.3-flash\", \"billing_cost\": \"0.0002\", " +
            "\"items\": [${item(1, "cloze", prompt = "Er ___ (insistieren) das Wort heute.", extra = ", \"temperature\": 0.7")}, " +
            "${item(1, "plurals", answer = "Arbeiten", choices = listOf("Arbeiten", "Arbeit"), extra = ", \"usage\": {\"tokens\": 12}")}, " +
            "${item(1, "genitive", answer = "der", choices = listOf("der", "des"))}]}"
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
        assertEquals("word_id", result.rejections.single().filter)
    }

    @Test
    fun `a missing items array falls back to a cloze per word and flags the response malformed`() {
        val words = listOf(wordWithContext(1, "insistieren"), wordWithContext(2, "Vertrauen"))
        val json = """{"note": "no items here"}"""
        val result = PracticeContract.parse(json, words)
        assertTrue(result.items.isEmpty())
        assertEquals(6, result.clozeFallbacks.size)
        assertTrue(result.malformed)
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
        // answer first, then the word, its n-variant and a neutral mark — never
        // an uppercase flip.
        assertEquals(listOf("Haus", "Hausn", "Haus?"), choices)
        assertTrue(choices.none { it == "HAUS" })
    }

    @Test
    fun `fallback choices fill three distinct slots so the rotation never repeats a card`() {
        // answer == text: without a third distinct distractor the choices shrink
        // to two and the three-slot rotation makes slot 2 repeat slot 0 (R2 E).
        val result = PracticeContract.parse(response(), listOf(wordWithContext(1, "Haus")))
        val rotations = result.clozeFallbacks.map { it.choices }
        assertEquals(3, rotations.size)
        for (choices in rotations) {
            assertEquals(3, choices.size)
            assertEquals(choices.size, choices.toSet().size)
            assertTrue("Haus" in choices)
        }
        // The rotated slots differ — no card repeats the previous one.
        assertTrue(rotations[0] != rotations[1] && rotations[1] != rotations[2])

        // The inflected-form case keeps its three natural choices untouched.
        val inflected = PracticeContract.parse(
            response(), listOf(word(2, "insistieren", context = "Die Arbeit insistiert auf seiner Unterschrift.")),
        )
        assertEquals(listOf("insistiert", "insistieren", "insistierenn"), inflected.clozeFallbacks[0].choices)
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

    // ------------------------------------------------------------------
    // quality filters (epic 9 round 3)
    // ------------------------------------------------------------------

    @Test
    fun `filter word_order rejects tiles that do not spell the answer`() {
        val words = listOf(word(1, "insistieren", context = "Das Personal wächst heute an."))
        // Tile "auf 6.000" must be reversed — the tiles can't produce the answer.
        val unsolvable = response(
            item(
                1, "word_order",
                prompt = "Ordne: Personal / Das / 6.000 auf / wächst / an / .",
                answer = "Das Personal wächst auf 6.000 an.",
                choices = listOf("Das Personal wächst auf 6.000 an.", "Personal Das wächst 6.000 auf an."),
            ),
        )
        val rejected = PracticeContract.parse(unsolvable, words, padFallbacks = false)
        assertTrue(rejected.items.isEmpty())
        assertEquals(PracticeFilters.WORD_ORDER, rejected.rejections.single().filter)

        // The solvable variant with the same words passes.
        val solvable = response(
            item(
                1, "word_order",
                prompt = "Ordne: Das / Personal / wächst / heute / an / .",
                answer = "Das Personal wächst heute an.",
                choices = listOf("Das Personal wächst heute an.", "Personal Das wächst heute an."),
            ),
        )
        val accepted = PracticeContract.parse(solvable, words, padFallbacks = false)
        assertEquals(1, accepted.items.size)
        assertTrue(accepted.rejections.isEmpty())
    }

    @Test
    fun `filter word_order tolerates case punctuation and merged-tile mismatches`() {
        val words = listOf(word(1, "insistieren", context = "Kontext."))
        // Punctuation and case differences still match (no comma per the rules).
        val ok = response(
            item(
                1, "word_order",
                prompt = "Ordne: die / Arbeit / insistiert",
                answer = "Die Arbeit insistiert.",
                choices = listOf("Die Arbeit insistiert.", "Arbeit die insistiert"),
            ),
        )
        assertTrue(PracticeContract.parse(ok, words, padFallbacks = false).items.size == 1)

        // A tile that must be split ("EU-Kommission" vs "Kommission") is rejected.
        val split = response(
            item(
                1, "word_order",
                prompt = "Ordne: Kommission / die / mahnt",
                answer = "Die EU-Kommission mahnt",
                choices = listOf("Die EU-Kommission mahnt", "Kommission die mahnt"),
            ),
        )
        val rejected = PracticeContract.parse(split, words, padFallbacks = false)
        assertEquals(PracticeFilters.WORD_ORDER, rejected.rejections.single().filter)

        // No tile list at all: unverifiable, rejected.
        val noTiles = response(
            item(1, "word_order", prompt = "Ordne die Wörter.", answer = "Die Arbeit insistiert"),
        )
        assertEquals(PracticeFilters.WORD_ORDER, PracticeContract.parse(noTiles, words, padFallbacks = false).rejections.single().filter)
    }

    @Test
    fun `filter duplicate rejects a non-cloze answer that repeats the accepted cloze`() {
        val words = listOf(wordWithContext(1, "insistieren"))
        val json = response(
            item(
                1, "cloze", prompt = "Er ___ (insistieren) auf seiner Meinung.",
                answer = "insistiert", choices = listOf("insistiert", "insistieren"),
            ),
            item(1, "conjugation", answer = "Insistiert", choices = listOf("Insistiert", "insistieren")),
            item(1, "prepositions", answer = "auf", choices = listOf("auf", "an")),
        )
        val result = PracticeContract.parse(json, words, padFallbacks = false)
        // The cloze itself stays; the conjugation repeat is dropped even in a
        // different capitalization; the distinct preposition survives.
        assertEquals(2, result.items.size)
        assertEquals(
            listOf(DrillType.CLOZE, DrillType.PREPOSITIONS),
            result.items.map { it.type },
        )
        assertEquals(PracticeFilters.DUPLICATE, result.rejections.single().filter)
    }

    @Test
    fun `filter answer_fit checks the answer against the model's full sentence`() {
        val words = listOf(word(1, "weigern", context = "weil das Heimatland sich weigert."))
        // The infinitive does not fit a 3rd-person gap: prompt with the answer in
        // it is not the full sentence the model reported.
        val mismatch = response(
            item(
                1, "conjugation",
                prompt = "weil das Heimatland sich ___.",
                answer = "weigern",
                choices = listOf("weigern", "weigert"),
                full = "weil das Heimatland sich weigert.",
            ),
        )
        val rejected = PracticeContract.parse(mismatch, words, padFallbacks = false)
        assertTrue(rejected.items.isEmpty())
        assertEquals(PracticeFilters.ANSWER_FIT, rejected.rejections.single().filter)

        // The fitting answer passes the exact check (whitespace is normalized).
        val match = response(
            item(
                1, "conjugation",
                prompt = "weil das Heimatland sich ___ (weigern).",
                answer = "weigert",
                choices = listOf("weigert", "weigern"),
                full = "weil  das Heimatland sich weigert (weigern).",
            ),
        )
        val accepted = PracticeContract.parse(match, words, padFallbacks = false)
        assertEquals(1, accepted.items.size)
    }

    @Test
    fun `filter leak rejects answers and bracketed base forms in the hint`() {
        val words = listOf(word(1, "beim", context = "Er bleibt beim Thema."))
        // The classic hint leak: the answer itself verbatim in the hint.
        val hintLeak = response(
            item(
                1, "adjective_endings", prompt = "Das Ende kommt nur ___.",
                answer = "kurzfristig", choices = listOf("kurzfristig", "kurzfristige"), hint = "kurzfristig",
            ),
        )
        assertEquals(PracticeFilters.LEAK, PracticeContract.parse(hintLeak, words, padFallbacks = false).rejections.single().filter)

        // The prompt bracket IS the answer ("___ (kurzfristig)" → "kurzfristig").
        val promptLeak = response(
            item(
                1, "adjective_endings", prompt = "___ (kurzfristig)",
                answer = "kurzfristig", choices = listOf("kurzfristig", "kurzfristige"),
            ),
        )
        assertEquals(PracticeFilters.LEAK, PracticeContract.parse(promptLeak, words, padFallbacks = false).rejections.single().filter)

        // The hint reveals the prompt's base form for an inflected answer.
        val baseLeak = response(
            item(
                1, "separable_verbs", prompt = "Das Personal ___ (anwachsen) von 3.000 auf 6.000.",
                answer = "wächst", choices = listOf("wächst", "wuchsen"), hint = "anwachsen",
            ),
        )
        assertEquals(PracticeFilters.LEAK, PracticeContract.parse(baseLeak, words, padFallbacks = false).rejections.single().filter)

        // A differing base form in the prompt with a clean hint is allowed.
        val allowed = response(
            item(
                1, "separable_verbs", prompt = "Das Personal ___ (anwachsen) von 3.000 auf 6.000.",
                answer = "wächst", choices = listOf("wächst", "wuchsen"), hint = "Präsens, 3. Person Singular",
            ),
        )
        assertTrue(PracticeContract.parse(allowed, words, padFallbacks = false).items.size == 1)

        // Very short answers (articles) are exempt from the substring hint check.
        val shortAnswer = response(
            item(1, "prepositions", prompt = "Er bleibt ___ Thema.", answer = "beim", choices = listOf("beim", "am"), hint = "bei dem"),
        )
        // "beim" is 4 chars and appears in "bei dem"? No — "bei dem" has no
        // "beim" substring: the item passes.
        assertTrue(PracticeContract.parse(shortAnswer, words, padFallbacks = false).items.size == 1)
    }

    @Test
    fun `filter leak matches whole words only — mit no longer hits Mitgliedsstaat`() {
        val words = listOf(word(1, "mit", context = "Er kommt mit dem Bus."))
        // A compound containing the answer as a substring is NOT a leak.
        val compound = response(
            item(
                1, "prepositions", prompt = "___ die Mitgliedsstaaten!",
                answer = "Unterstützt", choices = listOf("Unterstützt", "Unterstütze"), hint = "unter die Mitgliedsstaaten",
            ),
        )
        val accepted = PracticeContract.parse(compound, words, padFallbacks = false)
        // "Unterstützt" (11 chars) is not in the hint; the compound doesn't leak.
        assertEquals(1, accepted.items.size)
        assertTrue(accepted.rejections.isEmpty())

        // The answer as its own word in the hint still leaks.
        val ownWord = response(
            item(
                1, "prepositions", prompt = "Er kommt ___ dem Bus.",
                answer = "mit", choices = listOf("mit", "ohne"), hint = "zusammen mit",
            ),
        )
        assertEquals(PracticeFilters.LEAK, PracticeContract.parse(ownWord, words, padFallbacks = false).rejections.single().filter)

        // A stem match across word boundary ("nach" inside "danach") is not a leak.
        val words2 = listOf(word(2, "nach", context = "Nach dem Essen."))
        val stemInside = response(
            item(
                2, "prepositions", prompt = "Er geht ___ Hause.",
                answer = "nach", choices = listOf("nach", "zu"), hint = "danach",
            ),
        )
        assertTrue(PracticeContract.parse(stemInside, words2, padFallbacks = false).rejections.isEmpty())
    }

    @Test
    fun `filter answer_fit normalizes punctuation between prompt and full`() {
        val words = listOf(word(1, "unterstützen", context = "Es gilt, die Mitgliedsstaaten zu unterstützen."))
        // The confirmed round-3 false positive: the prompt writes ", " where the
        // model's full sentence has " - " — the same sentence, so no rejection.
        val dashVsComma = response(
            item(
                1, "conjugation", prompt = "Es gilt, sie zu ___.",
                answer = "unterstützen", choices = listOf("unterstützen", "unterstützt"),
                full = "Es gilt - sie zu unterstützen.",
            ),
        )
        val accepted = PracticeContract.parse(dashVsComma, words, padFallbacks = false)
        assertEquals(1, accepted.items.size)
        assertTrue(accepted.rejections.isEmpty())

        // A real mismatch (different words) is still rejected.
        val mismatch = response(
            item(
                1, "conjugation", prompt = "Es gilt, sie zu ___.",
                answer = "unterstützen", choices = listOf("unterstützen", "unterstützt"),
                full = "Es gilt - sie zu vermeiden.",
            ),
        )
        assertEquals(PracticeFilters.ANSWER_FIT, PracticeContract.parse(mismatch, words, padFallbacks = false).rejections.single().filter)
    }

    @Test
    fun `filter separable rejects split answers with a space`() {
        val words = listOf(word(1, "anwachsen", context = "Das Personal wächst an."))
        val json = response(
            item(
                1, "separable_verbs", prompt = "Das Personal ___ (anwachsen) von 3.000 auf 6.000 an.",
                answer = "wächst an", choices = listOf("wächst an", "wächst"),
            ),
        )
        val result = PracticeContract.parse(json, words, padFallbacks = false)
        assertTrue(result.items.isEmpty())
        assertEquals(PracticeFilters.SEPARABLE_SPACE, result.rejections.single().filter)
    }

    @Test
    fun `filter missing_base rejects a verb-form card that never names the verb`() {
        // #724: "___ auf unerlaubte Zölle oder Zwang!" fits Achte, passe and
        // Verzichte alike — the card cannot be answered from its own material.
        val words = listOf(word(1, "verzichten", context = "Man könne darauf verzichten."))
        val bare = response(
            item(
                1, "conjugation",
                prompt = "___ auf unerlaubte Zölle oder Zwang!",
                answer = "Verzichte", choices = listOf("Verzichte", "Verzichtet", "Verzichtest"),
                hint = "Imperativ, du-Form",
                full = "Verzichte auf unerlaubte Zölle oder Zwang!",
            ),
        )
        assertEquals(
            PracticeFilters.MISSING_BASE,
            PracticeContract.parse(bare, words, padFallbacks = false).rejections.single().filter,
        )

        // The bracketed base names the verb: the same card is solvable and accepted.
        val bracketed = response(
            item(
                1, "conjugation",
                prompt = "___ (verzichten) auf unerlaubte Zölle oder Zwang!",
                answer = "Verzichte", choices = listOf("Verzichte", "Verzichtet", "Verzichtest"),
                hint = "Imperativ, du-Form",
                full = "Verzichte (verzichten) auf unerlaubte Zölle oder Zwang!",
            ),
        )
        val accepted = PracticeContract.parse(bracketed, words, padFallbacks = false)
        assertEquals(1, accepted.items.size)
        assertTrue(accepted.rejections.isEmpty())

        // A reflexive saved word is checked by its verb stem.
        val words3 = listOf(word(3, "sich weigern", context = "weil das Heimatland sich weigert."))
        val reflexive = response(
            item(
                3, "conjugation",
                prompt = "weil das Heimatland sich ___.",
                answer = "weigert", choices = listOf("weigert", "weigern"),
                full = "weil das Heimatland sich weigert.",
            ),
        )
        assertEquals(
            PracticeFilters.MISSING_BASE,
            PracticeContract.parse(reflexive, words3, padFallbacks = false).rejections.single().filter,
        )
    }

    @Test
    fun `filter missing_base accepts cards that already name the verb`() {
        val words = listOf(word(1, "verzichten", context = "Man könne darauf verzichten."))
        // The answer IS the base form: the card names the verb by itself.
        val baseAnswer = response(
            item(
                1, "cloze",
                prompt = "Man könne gemeinsam darauf dringen, dass andere Staaten auf Zwang ___.",
                answer = "verzichten", choices = listOf("verzichten", "verzichtet"),
                full = "Man könne gemeinsam darauf dringen, dass andere Staaten auf Zwang verzichten.",
            ),
        )
        assertTrue(PracticeContract.parse(baseAnswer, words, padFallbacks = false).rejections.isEmpty())

        // The base appears in the sentence itself: no bracket needed.
        val words2 = listOf(word(2, "anwachsen", context = "Das Personal wächst an."))
        val inSentence = response(
            item(
                2, "separable_verbs",
                prompt = "Während das Personal weiter anwachsen soll, ___ es heute nur langsam an.",
                answer = "wächst", choices = listOf("wächst", "wuchsen"),
                full = "Während das Personal weiter anwachsen soll, wächst es heute nur langsam an.",
            ),
        )
        assertTrue(PracticeContract.parse(inSentence, words2, padFallbacks = false).rejections.isEmpty())
    }

    @Test
    fun `filter missing_base leaves non-verb words and non-verb types alone`() {
        // A noun is never verb-shaped, even when its plural leaves the sentence.
        val words = listOf(word(1, "Kind", context = "Die Kinder spielen."))
        val plural = response(
            item(
                1, "plurals",
                prompt = "Die ___ spielen im Garten.",
                answer = "Kinder", choices = listOf("Kinder", "Kindern", "Kinds"),
                full = "Die Kinder spielen im Garten.",
            ),
        )
        assertTrue(PracticeContract.parse(plural, words, padFallbacks = false).rejections.isEmpty())

        // A verb word, but the item tests a preposition: out of scope.
        val words2 = listOf(word(2, "verzichten", context = "Man könne darauf verzichten."))
        val preposition = response(
            item(
                2, "prepositions",
                prompt = "Man dringt darauf, dass andere Staaten ___ Zölle verzichten.",
                answer = "auf", choices = listOf("auf", "an", "in"),
                full = "Man dringt darauf, dass andere Staaten auf Zölle verzichten.",
            ),
        )
        assertTrue(PracticeContract.parse(preposition, words2, padFallbacks = false).rejections.isEmpty())
    }

    @Test
    fun `capitalization fix uppercases a sentence-initial answer without rejecting it`() {
        val words = listOf(word(1, "insistieren", context = "Kontext."))
        val json = response(
            item(
                1, "articles", prompt = "___ eigenes Zentrum entsteht.",
                answer = "ein", choices = listOf("ein", "eine", "das"),
            ),
        )
        val result = PracticeContract.parse(json, words, padFallbacks = false)
        assertEquals(1, result.items.size)
        assertEquals("Ein", result.items[0].answer)
        // The matching choice was fixed with the answer, so validate still holds.
        assertTrue(result.items[0].answer in result.items[0].choices)
        // Not sentence-initial: untouched.
        val mid = PracticeContract.parse(
            response(item(1, "articles", prompt = "Das ist ___ eigenes Zentrum.", answer = "ein", choices = listOf("ein", "eine"))),
            words,
            padFallbacks = false,
        )
        assertEquals("ein", mid.items[0].answer)
    }

    @Test
    fun `the full field is used at parse time only and never stored`() {
        val words = listOf(word(1, "weigern", context = "weil das Heimatland sich weigert."))
        val json = response(
            item(
                1, "conjugation", prompt = "weil das Heimatland sich ___ (weigern).",
                answer = "weigert", choices = listOf("weigert", "weigern"),
                full = "weil das Heimatland sich weigert (weigern).",
            ),
        )
        val result = PracticeContract.parse(json, words)
        val stored = result.items.single()
        assertNull(PracticeItemsCodec.encode(listOf(stored)).let { encoded ->
            JSONArray(encoded).getJSONObject(0).optString("full", null)
        })
        // Old stored items without `full` decode untouched (backward compat).
        val oldJson = """[{"id": 1, "wordId": 1, "type": "cloze", "prompt": "p", "answer": "a", "choices": ["a", "b"], "createdAt": 9}]"""
        assertEquals(1, PracticeItemsCodec.decode(oldJson).size)
    }
}
