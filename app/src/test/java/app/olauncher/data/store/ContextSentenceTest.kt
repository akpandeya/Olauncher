package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextSentenceTest {

    private fun aroundWord(paragraph: String, word: String): String {
        val start = paragraph.indexOf(word)
        assertTrue("word '$word' not in paragraph", start >= 0)
        return ContextSentence.around(paragraph, start, start + word.length)
    }

    @Test
    fun `returns the sentence that contains the word span`() {
        val paragraph = "Ich liebe Fernweh. Guten Morgen!"
        assertEquals("Ich liebe Fernweh.", aroundWord(paragraph, "Fernweh"))
        assertEquals("Guten Morgen!", aroundWord(paragraph, "Morgen"))
    }

    @Test
    fun `splits after punctuation followed by a space and a capital`() {
        val paragraph = "Er kam früh? Wir warteten. Sie ging … Dann kam sie."
        assertEquals("Er kam früh?", aroundWord(paragraph, "früh"))
        assertEquals("Wir warteten.", aroundWord(paragraph, "warteten"))
        assertEquals("Dann kam sie.", aroundWord(paragraph, "Dann"))
    }

    @Test
    fun `splits before a sentence that opens with a German quote`() {
        val paragraph = "Das war gestern. „Heute ist alles anders“, schrieb sie."
        assertEquals("„Heute ist alles anders“, schrieb sie.", aroundWord(paragraph, "anders"))
        assertEquals("Das war gestern.", aroundWord(paragraph, "gestern"))
    }

    @Test
    fun `keeps abbreviations intact`() {
        assertEquals(
            "Ich mag Obst, z. B. Äpfel und Birnen.",
            aroundWord("Ich mag Obst, z. B. Äpfel und Birnen. Sie sind gesund.", "Äpfel")
        )
        assertEquals(
            "Er kommt, d. h. wir warten.",
            aroundWord("Er kommt, d. h. wir warten. Dann gehen wir.", "warten")
        )
        assertEquals(
            "Siehe Dr. Müller.",
            aroundWord("Siehe Dr. Müller. Er hilft.", "Müller")
        )
        assertEquals(
            "Lies Seite Nr. 12 laut.",
            aroundWord("Lies Seite Nr. 12 laut. Danach ruhen wir.", "laut")
        )
        // Per the epic decision, "usw." never ends a sentence here.
        assertEquals(
            "Äpfel, Birnen usw. Und so weiter.",
            aroundWord("Äpfel, Birnen usw. Und so weiter.", "weiter")
        )
    }

    @Test
    fun `keeps decimal numbers like 3dot5 intact`() {
        assertEquals(
            "Version 3.5 ist neu.",
            aroundWord("Version 3.5 ist neu. Sie läuft stabil.", "Version")
        )
    }

    @Test
    fun `does not split when followed by a digit`() {
        assertEquals(
            "Am 3. 10. kam er.",
            aroundWord("Am 3. 10. kam er. Wir trafen uns.", "kam")
        )
    }

    @Test
    fun `heading without a full stop stays whole`() {
        assertEquals("Impressum und Kontakte", aroundWord("Impressum und Kontakte", "Kontakte"))
    }

    @Test
    fun `punctuation and stopwords are kept`() {
        assertEquals(
            "Morgen, liebe Freunde!",
            aroundWord("Morgen, liebe Freunde! Wir sehen uns.", "liebe")
        )
    }

    @Test
    fun `caps long sentences by trimming around the word, never through it`() {
        val paragraph = "a".repeat(300) + " ZIELWORT " + "b".repeat(300) + "."
        val result = aroundWord(paragraph, "ZIELWORT")
        assertEquals(ScreenText.MAX_CONTEXT_CHARS, result.length)
        assertTrue(result.contains("ZIELWORT"))
    }

    @Test
    fun `caps by trimming only the long side when the word sits at an edge`() {
        val paragraph = "Wort " + "x".repeat(500)
        val result = aroundWord(paragraph, "Wort")
        assertEquals(ScreenText.MAX_CONTEXT_CHARS, result.length)
        assertTrue(result.startsWith("Wort "))
    }

    @Test
    fun `word at the very end keeps its sentence with punctuation`() {
        assertEquals("Guten Morgen!", aroundWord("Guten Morgen!", "Morgen"))
        assertEquals("Ende.", aroundWord("Ende.", "Ende"))
    }

    @Test
    fun `blank paragraph and broken spans give no crash`() {
        assertEquals("", ContextSentence.around("", 0, 2))
        assertEquals("", ContextSentence.around("   ", 0, 2))
        assertEquals("Ende.", ContextSentence.around("Ende.", -5, -1))
    }

    @Test
    fun `split separates sentences on punctuation and capital letter`() {
        val paragraph = "Er kam früh? Wir warteten. Sie ging … Dann kam sie."
        val expected = listOf(
            "Er kam früh?",
            "Wir warteten.",
            "Sie ging …",
            "Dann kam sie."
        )
        assertEquals(expected, ContextSentence.split(paragraph))
    }

    @Test
    fun `split keeps abbreviations like z B and Dr and decimals intact`() {
        val paragraph = "Ich mag Obst, z. B. Äpfel. Siehe Dr. Müller. Version 3.5 ist neu."
        val expected = listOf(
            "Ich mag Obst, z. B. Äpfel.",
            "Siehe Dr. Müller.",
            "Version 3.5 ist neu."
        )
        assertEquals(expected, ContextSentence.split(paragraph))
    }

    @Test
    fun `split separates before a sentence opening with a German quote`() {
        val paragraph = "Das war gestern. „Heute ist alles anders“, schrieb sie."
        val expected = listOf(
            "Das war gestern.",
            "„Heute ist alles anders“, schrieb sie."
        )
        assertEquals(expected, ContextSentence.split(paragraph))
    }

    @Test
    fun `split on blank or empty input returns empty list`() {
        assertEquals(emptyList<String>(), ContextSentence.split(""))
        assertEquals(emptyList<String>(), ContextSentence.split("   \n\t  "))
    }
}
