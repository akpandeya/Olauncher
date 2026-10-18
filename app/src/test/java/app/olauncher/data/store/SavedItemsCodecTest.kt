package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedItemsCodecTest {

    @Test
    fun `round trip single active item`() {
        val items = listOf(
            SavedItem(
                id = 1L,
                url = "https://example.com/article",
                title = "Example Article",
                savedAt = 1700000000000L,
                archivedAt = null
            )
        )
        val encoded = SavedItemsCodec.encode(items)
        val decoded = SavedItemsCodec.decode(encoded)
        assertEquals(items, decoded)
    }

    @Test
    fun `round trip single archived item`() {
        val items = listOf(
            SavedItem(
                id = 42L,
                url = "https://example.org/docs",
                title = "Documentation",
                savedAt = 1700000000000L,
                archivedAt = 1700001000000L
            )
        )
        val encoded = SavedItemsCodec.encode(items)
        val decoded = SavedItemsCodec.decode(encoded)
        assertEquals(items, decoded)
    }

    @Test
    fun `round trip multiple items preserves order and fields`() {
        val items = listOf(
            SavedItem(1L, "https://a.com", "A", 1000L, null),
            SavedItem(2L, "https://b.com", "B", 2000L, 3000L),
            SavedItem(3L, "https://c.com", "C", 4000L, null)
        )
        val encoded = SavedItemsCodec.encode(items)
        val decoded = SavedItemsCodec.decode(encoded)
        assertEquals(items, decoded)
    }

    @Test
    fun `round trip empty list`() {
        val items = emptyList<SavedItem>()
        val encoded = SavedItemsCodec.encode(items)
        val decoded = SavedItemsCodec.decode(encoded)
        assertTrue(decoded.isEmpty())
    }

    @Test
    fun `decoding empty string returns empty list`() {
        val decoded = SavedItemsCodec.decode("")
        assertTrue(decoded.isEmpty())
    }

    @Test
    fun `decoding blank string returns empty list`() {
        val decoded = SavedItemsCodec.decode("   \n\t  ")
        assertTrue(decoded.isEmpty())
    }

    @Test
    fun `decoding null string returns empty list`() {
        val decoded = SavedItemsCodec.decode(null)
        assertTrue(decoded.isEmpty())
    }

    @Test
    fun `decoding corrupt string returns empty list without crashing`() {
        val corruptSamples = listOf(
            "not json at all",
            "{ not valid json",
            "[ unclosed array",
            "12345",
            "true",
            "null"
        )
        for (sample in corruptSamples) {
            val decoded = SavedItemsCodec.decode(sample)
            assertTrue("Expected empty list for '$sample'", decoded.isEmpty())
        }
    }

    @Test
    fun `decoding json object instead of array returns empty list without crashing`() {
        val decoded = SavedItemsCodec.decode("""{"id": 1, "url": "https://example.com"}""")
        assertTrue(decoded.isEmpty())
    }

    @Test
    fun `decoding array with malformed object skips corrupt item without crashing`() {
        val json = """
            [
                {"id": 1, "url": "https://valid.com", "title": "Valid", "savedAt": 100},
                {"missing_required_keys": true},
                {"id": 2, "url": "https://valid2.com", "title": "Valid 2", "savedAt": 200}
            ]
        """.trimIndent()
        val decoded = SavedItemsCodec.decode(json)
        assertEquals(2, decoded.size)
        assertEquals("https://valid.com", decoded[0].url)
        assertEquals("https://valid2.com", decoded[1].url)
    }

    @Test
    fun `encodeToJson and decode with JSONArray directly`() {
        val items = listOf(
            SavedItem(10L, "https://test.com", "Test", 5000L, null)
        )
        val jsonArray = SavedItemsCodec.encodeToJson(items)
        val decoded = SavedItemsCodec.decode(jsonArray)
        assertEquals(items, decoded)
    }
}
