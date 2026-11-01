package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RewriteContractTest {

    private val source = "Die Stadtwerke erhöhen im Frühjahr die Preise."
    private val word = "insistieren"

    // --- buildPrompt ---

    @Test
    fun promptQuotesSourceAndWordVerbatim() {
        val prompt = RewriteContract.buildPrompt(source, word)
        assertTrue(prompt.contains(source))
        assertTrue(prompt.contains(word))
        assertTrue(prompt.contains("\"sentence\""))
    }

    @Test
    fun promptAsksForMinimalEditAndOneSentence() {
        val prompt = RewriteContract.buildPrompt(source, word)
        assertTrue(prompt.contains("minimal edit"))
        assertTrue(prompt.contains("one German sentence"))
    }

    @Test
    fun promptWithoutRetryReasonHasNoRejectionLine() {
        val prompt = RewriteContract.buildPrompt(source, word)
        assertFalse(prompt.contains("rejected"))
    }

    @Test
    fun promptWithBlankRetryReasonHasNoRejectionLine() {
        val prompt = RewriteContract.buildPrompt(source, word, retryReason = "   ")
        assertFalse(prompt.contains("rejected"))
    }

    @Test
    fun promptWithRetryReasonIncludesItVerbatim() {
        val reason = "The sentence does not contain the saved word"
        val prompt = RewriteContract.buildPrompt(source, word, retryReason = reason)
        assertTrue(prompt.contains("rejected"))
        assertTrue(prompt.contains(reason))
    }

    @Test
    fun promptIsDeterministic() {
        assertEquals(
            RewriteContract.buildPrompt(source, word, retryReason = "x"),
            RewriteContract.buildPrompt(source, word, retryReason = "x"),
        )
    }

    @Test
    fun constantSizesMatchAiRequestExpectations() {
        assertEquals(256, RewriteContract.MAX_TOKENS)
        assertEquals(60_000, RewriteContract.READ_TIMEOUT_MS)
    }

    // --- parse: Ok ---

    @Test
    fun parseOkForValidObjectWithExtraKey() {
        val raw = """{"note": "ignored", "sentence": "Die Stadtwerke insistieren auf den Preisen."}"""
        val result = RewriteContract.parse(raw)
        assertTrue(result is RewriteParse.Ok)
        assertEquals("Die Stadtwerke insistieren auf den Preisen.", (result as RewriteParse.Ok).text)
    }

    @Test
    fun parseOkNormalizesNbspAndDoubleSpaces() {
        val raw = "{\"sentence\": \"Die  Stadtwerke\u00A0erhöhen die Preise.\"}"
        val result = RewriteContract.parse(raw)
        assertTrue(result is RewriteParse.Ok)
        assertEquals("Die Stadtwerke erhöhen die Preise.", (result as RewriteParse.Ok).text)
    }

    // --- parse: Rejected(json) ---

    @Test
    fun parseRejectsEmptyStringAsJson() {
        val result = RewriteContract.parse("")
        assertEquals(RewriteContract.FILTER_JSON, result.filterOnly())
    }

    @Test
    fun parseRejectsNotJson() {
        val result = RewriteContract.parse("not json")
        assertEquals(RewriteContract.FILTER_JSON, result.filterOnly())
    }

    @Test
    fun parseRejectsTruncatedObject() {
        val result = RewriteContract.parse("""{"sentence": "Abc""")
        assertEquals(RewriteContract.FILTER_JSON, result.filterOnly())
    }

    @Test
    fun parseRejectsTopLevelArray() {
        val result = RewriteContract.parse("""["x"]""")
        assertEquals(RewriteContract.FILTER_JSON, result.filterOnly())
    }

    // --- parse: Rejected(missing_sentence) ---

    @Test
    fun parseRejectsEmptyObject() {
        val result = RewriteContract.parse("{}")
        assertEquals(RewriteContract.FILTER_MISSING, result.filterOnly())
    }

    @Test
    fun parseRejectsNumberSentence() {
        val result = RewriteContract.parse("""{"sentence": 42}""")
        assertEquals(RewriteContract.FILTER_MISSING, result.filterOnly())
    }

    @Test
    fun parseRejectsBlankSentence() {
        val result = RewriteContract.parse("""{"sentence": "   "}""")
        assertEquals(RewriteContract.FILTER_MISSING, result.filterOnly())
    }

    /** The filter id of a parse result, for assertion brevity. */
    private fun RewriteParse.filterOnly(): String = when (this) {
        is RewriteParse.Ok -> error("expected rejection, got Ok($text)")
        is RewriteParse.Rejected -> filter
    }
}
