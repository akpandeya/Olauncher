package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Collator
import java.util.Locale

class LetterIndexTest {

    @Test
    fun `build returns empty list for empty input`() {
        assertTrue(LetterIndex.build(emptyList()).isEmpty())
    }

    @Test
    fun `build extracts distinct letters in collator order`() {
        val labels = listOf("Spotify", "Signal", "Amazon", "Browser", "Calculator")
        val result = LetterIndex.build(labels)

        assertEquals(
            listOf('A', 'B', 'C', 'S'),
            result.map { it.letter }
        )
        assertEquals(2, result.first { it.letter == 'A' }.index)
        assertEquals(3, result.first { it.letter == 'B' }.index)
        assertEquals(4, result.first { it.letter == 'C' }.index)
        assertEquals(0, result.first { it.letter == 'S' }.index)
    }

    @Test
    fun `build records first index for multiple apps with same letter`() {
        val labels = listOf("Slack", "Signal", "Spotify")
        val result = LetterIndex.build(labels)

        assertEquals(1, result.size)
        assertEquals('S', result[0].letter)
        assertEquals(0, result[0].index)
        assertEquals(0, result[0].pos)
        assertEquals(0, result[0].position)
    }

    @Test
    fun `build folds umlauts to base Latin letters`() {
        val labels = listOf("Über", "Äpfel", "Öl", "amazon")
        val result = LetterIndex.build(labels)

        // 'Ä' and 'a' fold to 'A'
        // 'Ö' folds to 'O'
        // 'Ü' folds to 'U'
        assertEquals(listOf('A', 'O', 'U'), result.map { it.letter })
        assertEquals(1, result.first { it.letter == 'A' }.index) // "Äpfel" was seen at index 1
        assertEquals(2, result.first { it.letter == 'O' }.index)
        assertEquals(0, result.first { it.letter == 'U' }.index)
    }

    @Test
    fun `build folds accents and special European letters`() {
        val labels = listOf("Éclair", "Øre", "Æon", "ßeta")
        val result = LetterIndex.build(labels)

        // Æon -> A, Éclair -> E, Øre -> O, ßeta -> S
        assertEquals(listOf('A', 'E', 'O', 'S'), result.map { it.letter })
    }

    @Test
    fun `build groups digits and symbols under hash`() {
        val labels = listOf("1Password", "9gag", "#hashtag", "!Bang", "+Add", "\$Dollar")
        val result = LetterIndex.build(labels)

        assertEquals(1, result.size)
        assertEquals('#', result[0].letter)
        assertEquals(0, result[0].index)
    }

    @Test
    fun `build sorts hash before A in collator order`() {
        val labels = listOf("Zebra", "1Password", "Amazon")
        val result = LetterIndex.build(labels)

        assertEquals(listOf('#', 'A', 'Z'), result.map { it.letter })
        assertEquals(1, result.first { it.letter == '#' }.index)
        assertEquals(2, result.first { it.letter == 'A' }.index)
        assertEquals(0, result.first { it.letter == 'Z' }.index)
    }

    @Test
    fun `build ignores leading whitespace`() {
        val labels = listOf("   Spotify", "\t\tCalculator", "  #tag")
        val result = LetterIndex.build(labels)

        assertEquals(listOf('#', 'C', 'S'), result.map { it.letter })
    }

    @Test
    fun `build classifies blank strings under hash`() {
        val labels = listOf("", "   ", "Notes")
        val result = LetterIndex.build(labels)

        assertEquals(listOf('#', 'N'), result.map { it.letter })
        assertEquals(0, result.first { it.letter == '#' }.index)
        assertEquals(2, result.first { it.letter == 'N' }.index)
    }

    @Test
    fun `build respects custom Collator`() {
        val collator = Collator.getInstance(Locale.ROOT)
        val result = LetterIndex.build(listOf("B", "A"), collator)
        assertEquals(listOf('A', 'B'), result.map { it.letter })
    }

    @Test
    fun `letterAt returns minus 1 when count is 0 or negative`() {
        assertEquals(-1, LetterIndex.letterAt(10f, 100f, 0))
        assertEquals(-1, LetterIndex.letterAt(10f, 100f, -5))
    }

    @Test
    fun `letterAt returns 0 when height is 0 or negative`() {
        assertEquals(0, LetterIndex.letterAt(10f, 0f, 5))
        assertEquals(0, LetterIndex.letterAt(10f, -100f, 5))
    }

    @Test
    fun `letterAt clamps negative y to 0`() {
        assertEquals(0, LetterIndex.letterAt(-10f, 100f, 5))
        assertEquals(0, LetterIndex.letterAt(-0.01f, 100f, 5))
    }

    @Test
    fun `letterAt clamps y at or beyond height to count minus 1`() {
        assertEquals(4, LetterIndex.letterAt(100f, 100f, 5))
        assertEquals(4, LetterIndex.letterAt(150f, 100f, 5))
    }

    @Test
    fun `letterAt calculates correct letter index for slices`() {
        // height = 100, count = 4 -> each slice is 25 units
        assertEquals(0, LetterIndex.letterAt(0f, 100f, 4))
        assertEquals(0, LetterIndex.letterAt(24.9f, 100f, 4))
        assertEquals(1, LetterIndex.letterAt(25f, 100f, 4))
        assertEquals(1, LetterIndex.letterAt(49.9f, 100f, 4))
        assertEquals(2, LetterIndex.letterAt(50f, 100f, 4))
        assertEquals(3, LetterIndex.letterAt(75f, 100f, 4))
        assertEquals(3, LetterIndex.letterAt(99.9f, 100f, 4))
    }

    @Test
    fun `letterAt handles int and float overloads consistently`() {
        assertEquals(2, LetterIndex.letterAt(50, 100, 4))
        assertEquals(2, LetterIndex.letterAt(50f, 100, 4))
        assertEquals(2, LetterIndex.letterAt(50f, 100f, 4))
    }

    @Test
    fun `letterAt handles single count`() {
        assertEquals(0, LetterIndex.letterAt(0f, 100f, 1))
        assertEquals(0, LetterIndex.letterAt(50f, 100f, 1))
        assertEquals(0, LetterIndex.letterAt(150f, 100f, 1))
    }

    @Test
    fun `LetterPos data class properties and constructor`() {
        val pos = LetterPos('A', 3)
        assertEquals('A', pos.letter)
        assertEquals(3, pos.index)
        assertEquals(3, pos.pos)
        assertEquals(3, pos.position)
        assertEquals("A", pos.text)

        val posFromStr = LetterPos("B", 5)
        assertEquals('B', posFromStr.letter)
        assertEquals(5, posFromStr.index)

        val emptyStrPos = LetterPos("", 1)
        assertEquals('#', emptyStrPos.letter)
    }
}
