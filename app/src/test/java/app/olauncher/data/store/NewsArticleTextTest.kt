package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsArticleTextTest {

    private fun resource(name: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream(name)
            ?: javaClass.getResourceAsStream("/$name")
        return stream?.bufferedReader()?.readText() ?: error("Test resource '$name' not found")
    }

    @Test
    fun `extracts exact usable sentences from article_basic html`() {
        val html = resource("news/article_basic.html")
        val sentences = NewsArticleText.sentences(html)

        val expected = listOf(
            "In den Bergen gibt es heute wieder große Kälte für alle Besucher.",
            "Die Sonne scheint aber trotzdem den ganzen Nachmittag am Himmel.",
            "Einige Tiere suchen schon jetzt nach Nahrung, wie die scheuen Rehe im dichten Wald.",
            "Sie finden dort im Moos viele schmackhafte Kräuter für den Winter.",
            "Manche Vögel, z. B. Meisen und Finken, bleiben auch bei Frost in der Region.",
            "Andere Vögel fliegen dagegen schon früh in den warmen Süden."
        )

        assertEquals(expected, sentences)
        // Entities decoded to ä and ü
        assertTrue(sentences[0].contains("ä") && sentences[0].contains("ü"))
        // Link text kept
        assertTrue(sentences[2].contains("die scheuen Rehe"))
        // Nav text absent
        assertFalse(sentences.any { it.contains("Startseite") })
        // Caption text absent
        assertFalse(sentences.any { it.contains("Foto:") || it.contains("dpa") })
        // Footer text absent
        assertFalse(sentences.any { it.contains("Beispielverlag") || it.contains("©") })
        // Script text absent
        assertFalse(sentences.any { it.contains("analytics") })
    }

    @Test
    fun `extracts exact usable sentences from article_no_article_tag html`() {
        val html = resource("news/article_no_article_tag.html")
        val sentences = NewsArticleText.sentences(html)

        val expected = listOf(
            "Die alte Bibliothek im Stadtzentrum wird nun nach vielen Jahren endlich renoviert.",
            "Die Bauarbeiten sollen bereits im nächsten Frühling beginnen.",
            "Viele Bürger haben lange auf diese wichtige Maßnahme gewartet."
        )

        assertEquals(expected, sentences)
        assertFalse(sentences.any { it.contains("Mehr zum Thema") })
    }

    @Test
    fun `returns empty list for article_junk html`() {
        val html = resource("news/article_junk.html")
        assertEquals(emptyList<String>(), NewsArticleText.sentences(html))
    }

    @Test
    fun `returns empty list for empty html`() {
        assertEquals(emptyList<String>(), NewsArticleText.sentences(""))
    }

    @Test
    fun `returns empty list when html exceeds MAX_HTML_CHARS`() {
        val longHtml = "<p>" + "a".repeat(NewsArticleText.MAX_HTML_CHARS + 1) + "</p>"
        assertEquals(emptyList<String>(), NewsArticleText.sentences(longHtml))
        assertEquals(emptyList<String>(), NewsArticleText.paragraphs(longHtml))
    }

    @Test
    fun `isUsableSentence checks rule 1 length between 30 and 300`() {
        // Passing: 66 chars, 9 words
        assertTrue(NewsArticleText.isUsableSentence("Dies ist ein ganz normaler deutscher Satz mit ausreichender Länge."))
        // Failing: 29 chars, 6 words
        val short = "Ein zu kurzer Satz für Tests."
        assertEquals(29, short.length)
        assertFalse(NewsArticleText.isUsableSentence(short))
        // Failing: > 300 chars with word count in 5..40
        val long = "Donaudampfschifffahrtsgesellschaftskajüte ".repeat(8) + "Ende."
        assertTrue(long.length > 300)
        assertFalse(NewsArticleText.isUsableSentence(long))
    }

    @Test
    fun `isUsableSentence checks rule 2 word count between 5 and 40`() {
        // Passing: 7 words
        assertTrue(NewsArticleText.isUsableSentence("Dies ist ein ganz normaler deutscher Satz."))
        // Failing: 3 words with length >= 30
        val fewWords = "Donaudampfschifffahrtsgesellschaftskapitänskajütentür ist wunderschön gestaltet."
        assertTrue(fewWords.length >= 30)
        assertFalse(NewsArticleText.isUsableSentence(fewWords))
        // Failing: 41 words
        val manyWords = (1..41).joinToString(" ") { "Wort$it" } + "."
        assertFalse(NewsArticleText.isUsableSentence(manyWords))
    }

    @Test
    fun `isUsableSentence checks rule 3 first char uppercase or opening quote`() {
        // Passing uppercase letter
        assertTrue(NewsArticleText.isUsableSentence("Das Wetter heute in Berlin ist sonnig und klar."))
        // Passing opening quotes: „ " » «
        assertTrue(NewsArticleText.isUsableSentence("„Das Wetter heute in Berlin ist sonnig und klar.“"))
        assertTrue(NewsArticleText.isUsableSentence("\"Das Wetter heute in Berlin ist sonnig und klar.\""))
        assertTrue(NewsArticleText.isUsableSentence("»Das Wetter heute in Berlin ist sonnig und klar.«"))
        assertTrue(NewsArticleText.isUsableSentence("«Das Wetter heute in Berlin ist sonnig und klar.»"))
        // Failing: lowercase letter
        assertFalse(NewsArticleText.isUsableSentence("das Wetter heute in Berlin ist sonnig und klar."))
        // Failing: digit
        assertFalse(NewsArticleText.isUsableSentence("100 Menschen versammelten sich heute friedlich auf dem Marktplatz."))
    }

    @Test
    fun `isUsableSentence checks rule 4 last char is terminal punct after stripping trailing quotes`() {
        // Passing: dot, exclamation mark, question mark
        assertTrue(NewsArticleText.isUsableSentence("Das Wetter heute in Berlin ist sonnig und klar."))
        assertTrue(NewsArticleText.isUsableSentence("Das Wetter heute in Berlin ist sonnig und klar!"))
        assertTrue(NewsArticleText.isUsableSentence("Ist das Wetter heute in Berlin sonnig und klar?"))
        // Passing with trailing quotes: “ ” " « » '
        assertTrue(NewsArticleText.isUsableSentence("„Das Wetter heute in Berlin ist sonnig und klar!“"))
        assertTrue(NewsArticleText.isUsableSentence("„Das Wetter heute in Berlin ist sonnig und klar!”"))
        assertTrue(NewsArticleText.isUsableSentence("\"Das Wetter heute in Berlin ist sonnig und klar.\""))
        assertTrue(NewsArticleText.isUsableSentence("»Das Wetter heute in Berlin ist sonnig und klar.«"))
        assertTrue(NewsArticleText.isUsableSentence("«Das Wetter heute in Berlin ist sonnig und klar.»"))
        assertTrue(NewsArticleText.isUsableSentence("Das Wetter heute in Berlin ist sonnig und klar.'"))
        // Failing: missing terminal punctuation
        assertFalse(NewsArticleText.isUsableSentence("Das Wetter heute in Berlin ist sonnig und klar"))
        // Failing: semicolon or comma
        assertFalse(NewsArticleText.isUsableSentence("Das Wetter heute in Berlin ist sonnig und klar;"))
        assertFalse(NewsArticleText.isUsableSentence("Das Wetter heute in Berlin ist sonnig und klar,"))
    }

    @Test
    fun `isUsableSentence checks rule 5 forbidden substrings`() {
        // Passing
        assertTrue(NewsArticleText.isUsableSentence("Das Wetter heute in Berlin ist sonnig und klar."))
        // Failing forbidden substrings
        assertFalse(NewsArticleText.isUsableSentence("Besuchen Sie http://example.com für weitere Informationen."))
        assertFalse(NewsArticleText.isUsableSentence("Besuchen Sie www.example.com für weitere Informationen."))
        assertFalse(NewsArticleText.isUsableSentence("Schreiben Sie an info@example.com für weitere Hilfe."))
        assertFalse(NewsArticleText.isUsableSentence("Alle Rechte liegen beim Verlag © zweitausendsechsundzwanzig."))
        assertFalse(NewsArticleText.isUsableSentence("Nachrichten | Sport | Kultur | Politik aus aller Welt heute."))
        assertFalse(NewsArticleText.isUsableSentence("Folgen Sie dem Thema unter #nachrichten im sozialen Netzwerk."))
    }

    @Test
    fun `isUsableSentence checks rule 6 forbidden prefixes`() {
        // Passing
        assertTrue(NewsArticleText.isUsableSentence("Das Wetter heute in Berlin ist sonnig und klar."))
        // Failing forbidden prefixes
        assertFalse(NewsArticleText.isUsableSentence("Foto: Peter Müller zeigt den neuen Park in der Stadt."))
        assertFalse(NewsArticleText.isUsableSentence("Bild: Archivaufnahme des großen Rathauses am Marktplatz."))
        assertFalse(NewsArticleText.isUsableSentence("Quelle: Statistisches Bundesamt berichtet über die Daten."))
        assertFalse(NewsArticleText.isUsableSentence("Video: Sehen Sie hier den gesamten Beitrag aus der Sendung."))
        assertFalse(NewsArticleText.isUsableSentence("Audio: Hören Sie den vollständigen Podcast in der Mediathek."))
        assertFalse(NewsArticleText.isUsableSentence("Mehr zum Thema finden Sie in unserem ausführlichen Bericht."))
        assertFalse(NewsArticleText.isUsableSentence("Lesen Sie den gesamten Artikel in der morgigen Ausgabe."))
        assertFalse(NewsArticleText.isUsableSentence("Hören Sie die neueste Episode direkt auf unserer Webseite."))
        assertFalse(NewsArticleText.isUsableSentence("Anzeige: Das neue Elektroauto überzeugt mit großer Reichweite."))
    }

    @Test
    fun `isUsableSentence checks rule 7 letters are at least 60 percent of non-whitespace chars`() {
        // Passing: letters ratio ~97%
        assertTrue(NewsArticleText.isUsableSentence("Das Wetter heute in Berlin ist sonnig und klar."))
        // Failing: letters ratio < 60%
        val digitHeavy = "Ergebnis: 10 : 20 - 30 : 40 - 50 : 60 - 70 : 80 im Spiel."
        assertFalse(NewsArticleText.isUsableSentence(digitHeavy))
    }

    @Test
    fun `sentences caps at 40 for generated HTML with 50 distinct usable sentences`() {
        val paragraphs = (1..50).joinToString("\n") { i ->
            "<p>Dies ist der eindeutige deutsche Satz Nummer $i fuer den Testlauf.</p>"
        }
        val html = "<html><body>$paragraphs</body></html>"
        val result = NewsArticleText.sentences(html)

        assertEquals(40, result.size)
        assertEquals(40, result.distinct().size)
        assertEquals("Dies ist der eindeutige deutsche Satz Nummer 1 fuer den Testlauf.", result.first())
        assertEquals("Dies ist der eindeutige deutsche Satz Nummer 40 fuer den Testlauf.", result.last())
    }
}
