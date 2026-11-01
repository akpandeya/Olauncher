package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RewriteGuardsTest {

    private val lemmaMap = mapOf(
        "insistiert" to "insistieren",
        "Preise" to "Preis",
        "besteht" to "bestehen"
    )

    private val defaultLemmaOf: (String) -> String? = { form ->
        lemmaMap[form] ?: lemmaMap[form.lowercase()] ?: lemmaMap[form.replaceFirstChar { it.uppercase() }]
    }

    private val matcher = SentenceWordMatcher(defaultLemmaOf)

    private val offMatcher = SentenceWordMatcher { null }

    @Test
    fun `filter ids are distinct`() {
        val ids = listOf(
            RewriteGuards.SANITY_LENGTH,
            RewriteGuards.SANITY_ARTIFACT,
            RewriteGuards.SANITY_SCRIPT,
            RewriteGuards.SANITY_PUNCTUATION,
            RewriteGuards.CONTAINMENT,
            RewriteGuards.GROUNDING
        )
        assertEquals(6, ids.toSet().size)
    }

    @Test
    fun `accept when every non-target token is grounded`() {
        val verdict = RewriteGuards.check(
            source = "Die Stadtwerke bestehen im Frühjahr auf höheren Preisen.",
            rewrite = "Die Stadtwerke insistieren im Frühjahr auf höheren Preisen.",
            wordText = "insistieren",
            matcher = matcher
        )
        assertTrue(verdict.reason, verdict.accepted)
    }

    @Test
    fun `rejects with grounding when only 5 of 8 remaining tokens are grounded`() {
        val verdict = RewriteGuards.check(
            source = "Die Stadtwerke erhöhen im Frühjahr die Preise deutlich.",
            rewrite = "Die Stadtwerke insistieren im Frühjahr auf deutlich höheren Preisen.",
            wordText = "insistieren",
            matcher = matcher
        )
        assertEquals(RewriteGuards.GROUNDING, verdict.filter)
        assertFalse(verdict.accepted)
    }

    @Test
    fun `inflection accepted via lemma map`() {
        val verdict = RewriteGuards.check(
            source = "Die Firma besteht seit vielen Jahren.",
            rewrite = "Die Firma besteht seit vielen Jahren hier.",
            wordText = "bestehen",
            matcher = matcher
        )
        assertTrue(verdict.reason, verdict.accepted)
    }

    @Test
    fun `inflection accepted via stem fallback when the dictionary is off`() {
        val verdict = RewriteGuards.check(
            source = "Der Redner sagte nichts und schaute auf seine Antwort.",
            rewrite = "Der Redner insistiert auf seiner Antwort.",
            wordText = "insistieren",
            matcher = offMatcher
        )
        assertTrue(verdict.reason, verdict.accepted)
    }

    @Test
    fun `containment rejects when the word is absent`() {
        val verdict = RewriteGuards.containment(
            wordText = "schwimmen",
            rewrite = "Der Redner insistiert auf seiner Antwort.",
            matcher = matcher
        )
        assertEquals(RewriteGuards.CONTAINMENT, verdict.filter)
        assertTrue(verdict.reason!!.contains("schwimmen"))
    }

    @Test
    fun `grounding accepts a share of exactly 70 percent with at most 3 new tokens`() {
        val verdict = RewriteGuards.check(
            source = "Die Menschen lobten den neuen Plan und die alten Regeln.",
            rewrite = "Die Menschen bestehen auf dem neuen Plan und drei alten Regeln.",
            wordText = "bestehen",
            matcher = matcher
        )
        // 10 remaining tokens, 7 grounded (70 %), 3 distinct new tokens.
        assertTrue(verdict.reason, verdict.accepted)
    }

    @Test
    fun `grounding rejects on the max new tokens rule even with a share above 70 percent`() {
        val source = "Die Menschen lobten den neuen Plan und die alten Regeln in der Stadt."
        // 14 remaining tokens, 10 grounded (71 %), but 4 distinct new tokens.
        val verdict = RewriteGuards.check(
            source = source,
            rewrite = "Die Menschen insistiert auf dem neuen Plan und drei alten Regeln lobten den Stadt Pferde.",
            wordText = "insistieren",
            matcher = matcher
        )
        assertEquals(RewriteGuards.GROUNDING, verdict.filter)
    }

    @Test
    fun `grounding accepts the same shape with exactly 3 new tokens`() {
        val source = "Die Menschen lobten den neuen Plan und die alten Regeln in der Stadt."
        // 14 remaining tokens, 11 grounded (78 %), exactly 3 distinct new tokens.
        val verdict = RewriteGuards.check(
            source = source,
            rewrite = "Die Menschen insistiert auf dem neuen Plan und drei alten Regeln lobten den Stadt in.",
            wordText = "insistieren",
            matcher = matcher
        )
        assertTrue(verdict.reason, verdict.accepted)
    }

    @Test
    fun `grounding rejects a rewrite that shares no words with the source`() {
        val verdict = RewriteGuards.grounding(
            source = "Die Kinder spielen im Garten.",
            rewrite = "insistieren auf völlig fremden Dingen überall dort heute.",
            wordText = "insistieren",
            matcher = matcher
        )
        assertEquals(RewriteGuards.GROUNDING, verdict.filter)
    }

    @Test
    fun `sanity rejects a 19 character rewrite`() {
        assertEquals(RewriteGuards.SANITY_LENGTH, RewriteGuards.sanity("Das ist ein kurzer.").filter)
    }

    @Test
    fun `sanity rejects a 251 character rewrite`() {
        assertEquals(RewriteGuards.SANITY_LENGTH, RewriteGuards.sanity("Wort ".repeat(50) + "!").filter)
    }

    @Test
    fun `sanity rejects a rewrite with a placeholder`() {
        assertEquals(
            RewriteGuards.SANITY_ARTIFACT,
            RewriteGuards.sanity("Die Stadtwerke bestehen auf ___ hier.").filter
        )
    }

    @Test
    fun `sanity rejects a rewrite with a brace`() {
        assertEquals(
            RewriteGuards.SANITY_ARTIFACT,
            RewriteGuards.sanity("Die Stadtwerke bestehen auf {Regeln} hier.").filter
        )
    }

    @Test
    fun `sanity rejects a rewrite with a Satz label`() {
        assertEquals(
            RewriteGuards.SANITY_ARTIFACT,
            RewriteGuards.sanity("Satz: Die Stadtwerke bestehen hier.").filter
        )
    }

    @Test
    fun `sanity rejects a rewrite with a line break`() {
        assertEquals(
            RewriteGuards.SANITY_ARTIFACT,
            RewriteGuards.sanity("Die Stadtwerke bestehen\nim Frühjahr hier.").filter
        )
    }

    @Test
    fun `sanity rejects a rewrite with Cyrillic letters`() {
        assertEquals(
            RewriteGuards.SANITY_SCRIPT,
            RewriteGuards.sanity("Die Stadtwerke bestehen хорошо hier.").filter
        )
    }

    @Test
    fun `sanity rejects a rewrite without final punctuation`() {
        assertEquals(
            RewriteGuards.SANITY_PUNCTUATION,
            RewriteGuards.sanity("Die Stadtwerke bestehen im Frühjahr").filter
        )
    }

    @Test
    fun `sanity accepts umlauts eszett and closing quotes`() {
        assertEquals(null, RewriteGuards.sanity("Die Mädchen genießen große Größe.").filter)
        assertEquals(null, RewriteGuards.sanity("Er sagte: „Wir beginnen jetzt.“").filter)
        assertEquals(null, RewriteGuards.sanity("Er fragte: „Beginnen wir jetzt?“").filter)
    }

    @Test
    fun `check returns the sanity rejection before grounding`() {
        // Fails sanity (line break) and grounding (no shared tokens); sanity wins.
        val verdict = RewriteGuards.check(
            source = "Die Kinder spielen im Garten.",
            rewrite = "Völlig\nfremde Worte tauchen überall auf und bleiben.",
            wordText = "spielen",
            matcher = matcher
        )
        assertEquals(RewriteGuards.SANITY_ARTIFACT, verdict.filter)
    }
}
