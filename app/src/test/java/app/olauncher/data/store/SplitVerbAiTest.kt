package app.olauncher.data.store

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitVerbAiTest {

    /** Distinctive, non-default inputs so a hard-coded prompt or a missing field cannot pass. */
    private val sentence = "Gestern wollte sie das Paket unbedingt abholen."
    private val tapped = "abholen"

    @Test
    fun `prompt carries the sentence and the tapped token verbatim`() {
        val prompt = SplitVerbAi.buildPrompt(sentence, tapped)

        assertTrue(prompt.contains(sentence))
        assertTrue(prompt.contains(tapped))
    }

    @Test
    fun `a swapped sentence or token changes the prompt`() {
        val base = SplitVerbAi.buildPrompt(sentence, tapped)

        assertNotEquals(base, SplitVerbAi.buildPrompt("Ein voellig anderer Satz.", tapped))
        assertNotEquals(base, SplitVerbAi.buildPrompt(sentence, "wegbringen"))
    }

    @Test
    fun `schema is valid JSON that mentions both keys`() {
        val schema = JSONObject(SplitVerbAi.schema())

        assertTrue(schema.getJSONObject("properties").has("isSplitVerb"))
        assertTrue(schema.getJSONObject("properties").has("lemma"))
        assertEquals(false, schema.getBoolean("additionalProperties"))
    }

    @Test
    fun `true with a lemma is accepted`() {
        assertEquals(
            SplitVerbAi.Answer(true, "aufnehmen"),
            SplitVerbAi.parseResponse("{\"isSplitVerb\":true,\"lemma\":\"aufnehmen\"}"),
        )
    }

    @Test
    fun `false without a lemma is accepted`() {
        assertEquals(SplitVerbAi.Answer(false, null), SplitVerbAi.parseResponse("{\"isSplitVerb\":false}"))
        assertEquals(SplitVerbAi.Answer(false, null), SplitVerbAi.parseResponse("{\"isSplitVerb\":false,\"lemma\":null}"))
    }

    @Test
    fun `partial or out-of-contract input is rejected`() {
        val rejected = listOf(
            "not json",
            "",
            "{\"isSplitVerb\":true,\"lemma\":\"aufnehmen\"",           // truncated
            "{\"isSplitVerb\":true,\"lemma\":\"aufnehmen\",\"x\":1}",  // extra key
            "{\"lemma\":\"aufnehmen\"}",                                // missing isSplitVerb
            "{\"isSplitVerb\":\"true\",\"lemma\":\"aufnehmen\"}",       // string, not boolean
            "{\"isSplitVerb\":true,\"lemma\":null}",                    // true without a lemma
            "{\"isSplitVerb\":true}",                                   // true without a lemma key
            "{\"isSplitVerb\":true,\"lemma\":\"  \"}",                  // blank lemma
            "{\"isSplitVerb\":false,\"lemma\":\"aufnehmen\"}",          // false with a lemma
            "{\"isSplitVerb\":false}\n```",                             // valid object plus trailing junk
        )

        for (text in rejected) {
            assertNull("should reject: $text", SplitVerbAi.parseResponse(text))
        }
    }
}
