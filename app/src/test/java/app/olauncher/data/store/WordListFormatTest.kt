package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class WordListFormatTest {

    private lateinit var lines: List<String>
    private lateinit var header: List<String>
    private lateinit var rows: List<List<String>>

    @Before
    fun setUp() {
        val file = listOf(
            File("src/main/assets/words_de.tsv"),
            File("app/src/main/assets/words_de.tsv")
        ).firstOrNull { it.exists() }
            ?: throw AssertionError("words_de.tsv not found in src/main/assets or app/src/main/assets")

        lines = file.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
        assertTrue("File must not be empty", lines.isNotEmpty())
        header = lines.first().split("\t")
        rows = lines.drop(1).map { it.split("\t") }
    }

    @Test
    fun `header matches expected format`() {
        assertEquals(listOf("id", "de", "plural", "en", "example"), header)
    }

    @Test
    fun `every row has exactly 5 columns`() {
        rows.forEachIndexed { index, row ->
            assertEquals("Row ${index + 2} must have exactly 5 columns", 5, row.size)
        }
    }

    @Test
    fun `all ids are unique positive integers`() {
        val ids = rows.map { row ->
            val id = row[0].toIntOrNull()
            assertTrue("ID '${row[0]}' must be a valid positive integer", id != null && id > 0)
            id!!
        }
        assertEquals("IDs must be unique", ids.size, ids.toSet().size)
    }

    @Test
    fun `row count is within expected range`() {
        assertTrue("Row count (${rows.size}) must be >= 1400", rows.size >= 1400)
        assertTrue("Row count (${rows.size}) must be <= 1600", rows.size <= 1600)
    }

    @Test
    fun `no empty de or en fields`() {
        rows.forEachIndexed { index, row ->
            val de = row[1]
            val en = row[3]
            assertFalse("Row ${index + 2} has empty 'de'", de.isBlank())
            assertFalse("Row ${index + 2} has empty 'en'", en.isBlank())
        }
    }

    @Test
    fun `every noun row starts with der, die, or das`() {
        var nounCount = 0
        rows.forEachIndexed { index, row ->
            val de = row[1]
            val plural = row[2]

            // A row is a noun if plural is present, or the German lemma is capitalized
            val isNoun = plural.isNotEmpty() ||
                de.startsWith("der ") ||
                de.startsWith("die ") ||
                de.startsWith("das ") ||
                de.split(" ").last().firstOrNull()?.isUpperCase() == true

            if (isNoun) {
                nounCount++
                assertTrue(
                    "Noun row ${index + 2} ('$de') must start with der, die, or das",
                    de.startsWith("der ") || de.startsWith("die ") || de.startsWith("das ")
                )
            } else {
                assertTrue("Non-noun row ${index + 2} ('$de') must not have a plural", plural.isEmpty())
                assertFalse(
                    "Non-noun row ${index + 2} ('$de') must not start with an article",
                    de.startsWith("der ") || de.startsWith("die ") || de.startsWith("das ")
                )
            }
        }
        assertTrue("There must be noun rows in the dataset", nounCount > 0)
    }

    @Test
    fun `example field is under 90 characters and contains no newlines`() {
        rows.forEachIndexed { index, row ->
            val example = row[4]
            assertTrue("Row ${index + 2} example length (${example.length}) must be < 90", example.length < 90)
            assertFalse("Row ${index + 2} example must not contain newlines", example.contains("\n") || example.contains("\r"))
        }
    }

    @Test
    fun `attribution file exists and credits all required sources under CC BY-SA 4_0`() {
        val file = listOf(
            File("src/main/assets/WORDS_ATTRIBUTION.txt"),
            File("app/src/main/assets/WORDS_ATTRIBUTION.txt")
        ).firstOrNull { it.exists() }
        assertTrue("WORDS_ATTRIBUTION.txt must exist", file != null && file.exists())
        val content = file!!.readText(Charsets.UTF_8)
        assertTrue("Must credit Wiktionary contributors", content.contains("Wiktionary"))
        assertTrue("Must credit kaikki.org", content.contains("kaikki.org"))
        assertTrue("Must credit Tatu Ylonen", content.contains("Tatu Ylonen"))
        assertTrue("Must credit frequency list", content.contains("FrequencyWords") || content.contains("OpenSubtitles"))
        assertTrue("Must reference CC BY-SA 4.0", content.contains("CC BY-SA 4.0"))
    }
}
