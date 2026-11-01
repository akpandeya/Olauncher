package app.olauncher.data.store

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextQualityTest {

    private fun word(context: String, contextSource: String) = Word(
        id = 1L,
        text = "Regierung",
        context = context,
        contextSource = contextSource,
        dueDay = 100L,
        addedAt = 10L
    )

    @Test
    fun `blank and short contexts are weak`() {
        assertTrue(ContextQuality.isWeak("", "Regierung"))
        assertTrue(ContextQuality.isWeak("   ", "Regierung"))
        assertTrue(ContextQuality.isWeak("Regierung", "Regierung"))
        assertTrue(ContextQuality.isWeak("die neue Regierung", "Regierung"))
    }

    @Test
    fun `a screen navigation fragment from circle-to-search is weak`() {
        // Capitalized nouns, no punctuation, no verb-like token.
        assertTrue(
            ContextQuality.isWeak(
                "Startseite Politik Wirtschaft Sport Kultur",
                "Wirtschaft"
            )
        )
    }

    @Test
    fun `an english sentence is weak because too few chunks look german`() {
        assertTrue(
            ContextQuality.isWeak(
                "This is the best of the year, and we love it.",
                "lieben"
            )
        )
    }

    @Test
    fun `a ticker line of times and symbols is weak`() {
        assertTrue(ContextQuality.isWeak("12:30 Uhr – 3 € – #news", "fahren"))
    }

    @Test
    fun `a real german sentence with punctuation is not weak`() {
        assertFalse(
            ContextQuality.isWeak(
                "Die Regierung hat am Montag neue Regeln beschlossen.",
                "Regierung"
            )
        )
    }

    @Test
    fun `a german sentence with a verb-like token but no punctuation is not weak`() {
        // "plant" is not the first token, lowercase, 4+ letters, ends in "t" and is not the word.
        assertFalse(
            ContextQuality.isWeak(
                "Bundesregierung plant neue Steuer ab Januar",
                "Steuer"
            )
        )
    }

    @Test
    fun `a token of the word itself is never mistaken for the sentence's verb`() {
        // "sitzen" is exactly the word being learned, so it must not count as the
        // context's verb ("am" is too short); without a verb-like token the fragment is weak.
        assertTrue(ContextQuality.isWeak("Am sitzen am sitzen", "sitzen"))
        // Same shape but an unrelated word: "sitzen" now is the verb-like token, and
        // all chunks look German, so the four-token context is kept.
        assertFalse(ContextQuality.isWeak("Am sitzen am sitzen", "laufen"))
    }

    @Test
    fun `replaceable follows the one eligibility rule`() {
        val weak = "die neue Regierung"
        // A user-typed context is never replaceable, even when weak.
        assertFalse(ContextQuality.isReplaceable(word(weak, ContextSource.USER)))
        // Captured contexts (the default, including legacy words) and rewrites are.
        assertTrue(ContextQuality.isReplaceable(word(weak, ContextSource.CAPTURED)))
        assertTrue(ContextQuality.isReplaceable(word(weak, ContextSource.AUTO)))
        // A blank context is always fillable, whatever its source.
        assertTrue(ContextQuality.isReplaceable(word("", ContextSource.USER)))
        // A non-weak captured context is kept.
        assertFalse(
            ContextQuality.isReplaceable(
                word("Die Regierung hat am Montag neue Regeln beschlossen.", ContextSource.CAPTURED)
            )
        )
    }
}
