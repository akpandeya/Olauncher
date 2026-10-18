package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordCaptureTest {

    @Test
    fun `single word Fernweh classifies as Word`() {
        val result = WordCapture.classify("Fernweh")
        assertEquals(Capture.Word("Fernweh"), result)
    }

    @Test
    fun `word in German quotation marks Fernweh classifies as Word without quotes`() {
        val result = WordCapture.classify("„Fernweh“")
        assertEquals(Capture.Word("Fernweh"), result)
    }

    @Test
    fun `three word phrase sich freuen auf classifies as Word`() {
        val result = WordCapture.classify("sich freuen auf")
        assertEquals(Capture.Word("sich freuen auf"), result)
    }

    @Test
    fun `twelve word sentence classifies as Sentence with punctuation-free tokens`() {
        val sentence = "Dies ist ein ziemlich langer deutscher Beispielsatz mit genau zwölf verschiedenen Wörtern darin."
        val result = WordCapture.classify(sentence)

        assertTrue(result is Capture.Sentence)
        val captureSentence = result as Capture.Sentence
        assertEquals(sentence, captureSentence.text)
        val expectedTokens = listOf(
            "Dies", "ist", "ein", "ziemlich", "langer", "deutscher",
            "Beispielsatz", "mit", "genau", "zwölf", "verschiedenen", "Wörtern", "darin"
        )
        assertEquals(expectedTokens, captureSentence.tokens)
    }

    @Test
    fun `a URL classifies as Invalid`() {
        assertEquals(Capture.Invalid, WordCapture.classify("https://example.com"))
        assertEquals(Capture.Invalid, WordCapture.classify("https://de.wiktionary.org/wiki/Fernweh"))
        assertEquals(Capture.Invalid, WordCapture.classify("http://sub.example.org/path?q=test"))
        assertEquals(Capture.Invalid, WordCapture.classify("www.example.com"))
        assertEquals(Capture.Invalid, WordCapture.classify("Check out https://example.com for words"))
    }

    @Test
    fun `blank input classifies as Invalid`() {
        assertEquals(Capture.Invalid, WordCapture.classify(""))
        assertEquals(Capture.Invalid, WordCapture.classify("   "))
        assertEquals(Capture.Invalid, WordCapture.classify("\t\n  \r\n"))
    }

    @Test
    fun `input over 400 chars classifies as Invalid`() {
        val long500 = "Dies ist ein Satz ".repeat(28) // > 500 chars
        assertTrue(long500.length >= 500)
        assertEquals(Capture.Invalid, WordCapture.classify(long500))

        val exact401 = "a ".repeat(200) + "b" // 401 chars
        assertEquals(401, exact401.length)
        assertEquals(Capture.Invalid, WordCapture.classify(exact401))
    }

    @Test
    fun `various surrounding punctuation marks are trimmed for words`() {
        assertEquals(Capture.Word("Fernweh"), WordCapture.classify("\"Fernweh\""))
        assertEquals(Capture.Word("Fernweh"), WordCapture.classify("'Fernweh'"))
        assertEquals(Capture.Word("Fernweh"), WordCapture.classify("‚Fernweh‘"))
        assertEquals(Capture.Word("Fernweh"), WordCapture.classify("«Fernweh»"))
        assertEquals(Capture.Word("Fernweh"), WordCapture.classify("(Fernweh)"))
        assertEquals(Capture.Word("Fernweh"), WordCapture.classify("[Fernweh]"))
        assertEquals(Capture.Word("Fernweh"), WordCapture.classify("Fernweh."))
        assertEquals(Capture.Word("Fernweh"), WordCapture.classify("Fernweh!"))
        assertEquals(Capture.Word("Fernweh"), WordCapture.classify("Fernweh?"))
        assertEquals(Capture.Word("Fernweh"), WordCapture.classify("Fernweh;"))
        assertEquals(Capture.Word("Fernweh"), WordCapture.classify("Fernweh:"))
        assertEquals(Capture.Word("Fernweh"), WordCapture.classify("Fernweh,"))
        assertEquals(Capture.Word("Fernweh"), WordCapture.classify("  „Fernweh!“  "))
    }

    @Test
    fun `multi-word selections up to 4 words and 40 chars classify as Word`() {
        assertEquals(Capture.Word("ein Schritt nach vorne"), WordCapture.classify("ein Schritt nach vorne"))
        assertEquals(Capture.Word("sich freuen auf"), WordCapture.classify("  „sich freuen auf!“  "))
    }

    @Test
    fun `five word selection classifies as Sentence`() {
        val input = "Das ist ein schöner Tag"
        val result = WordCapture.classify(input)
        assertTrue(result is Capture.Sentence)
        val sentence = result as Capture.Sentence
        assertEquals(input, sentence.text)
        assertEquals(listOf("Das", "ist", "ein", "schöner", "Tag"), sentence.tokens)
    }

    @Test
    fun `sentence cleans internal punctuation from tokens`() {
        val input = "Er sagte: „Hallo!“, und ging weiter nach Hause."
        val result = WordCapture.classify(input)
        assertTrue(result is Capture.Sentence)
        val sentence = result as Capture.Sentence
        assertEquals(input, sentence.text)
        assertEquals(listOf("Er", "sagte", "Hallo", "und", "ging", "weiter", "nach", "Hause"), sentence.tokens)
    }

    @Test
    fun `word exceeding 40 chars classifies as Invalid`() {
        val longWord = "Rindfleischetikettierungsüberwachungsaufgabenübertragungsgesetz" // 63 chars
        assertEquals(Capture.Invalid, WordCapture.classify(longWord))
    }

    @Test
    fun `punctuation only classifies as Invalid`() {
        assertEquals(Capture.Invalid, WordCapture.classify(".,;:!?\"'„“”‚‘()[]«»"))
    }
}
