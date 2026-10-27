package app.olauncher.data.store

import app.olauncher.R
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The settings result line (#602, 9.9): every [AiResult.Error] kind has its own
 * user-facing string, and none of them can echo the key (the mapping only sees
 * the kind). The success line formats the latency as seconds.
 */
class AiPracticeMessagesTest {

    @Test
    fun `every error kind maps to its own string`() {
        assertEquals(R.string.ai_practice_key_rejected, AiPracticeMessages.labelRes(AiResult.ErrorKind.AUTH))
        assertEquals(R.string.ai_practice_billing_blocked, AiPracticeMessages.labelRes(AiResult.ErrorKind.BILLING))
        assertEquals(R.string.ai_error_rate_limit, AiPracticeMessages.labelRes(AiResult.ErrorKind.RATE_LIMIT))
        assertEquals(R.string.ai_error_network, AiPracticeMessages.labelRes(AiResult.ErrorKind.NETWORK))
        assertEquals(R.string.ai_error_bad_response, AiPracticeMessages.labelRes(AiResult.ErrorKind.BAD_RESPONSE))
        assertEquals(R.string.ai_error_server, AiPracticeMessages.labelRes(AiResult.ErrorKind.SERVER))
    }

    @Test
    fun `no two kinds share a string`() {
        val labels = AiResult.ErrorKind.values().map { AiPracticeMessages.labelRes(it) }
        assertEquals(labels.size, labels.toSet().size)
    }

    @Test
    fun `latency formats as seconds with one decimal`() {
        assertEquals("1.2", AiPracticeMessages.secondsText(1234))
        assertEquals("0.0", AiPracticeMessages.secondsText(0))
        assertEquals("12.0", AiPracticeMessages.secondsText(12_000L))
    }

    @Test
    fun `the provider label names Melious and falls back to the id`() {
        assertEquals("Melious", AiPracticeMessages.providerLabel("melious"))
        assertEquals("Anthropic", AiPracticeMessages.providerLabel("anthropic"))
    }
}
