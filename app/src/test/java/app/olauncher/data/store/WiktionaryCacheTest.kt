package app.olauncher.data.store

import app.olauncher.data.store.WiktionaryApi.LookupResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class WiktionaryCacheTest {

    private lateinit var dir: File
    private lateinit var file: File
    private var now = 1_000_000L

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("wikt-cache").toFile()
        file = File(dir, "wiktionary_cache.json")
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun cache() = WiktionaryCache(file).apply { clockSupplier = { now } }

    @Test
    fun `found round-trips across instances and key is lower-cased`() {
        cache().put("Fernweh", LookupResult.Found("wanderlust", "Fernweh"))
        assertEquals(LookupResult.Found("wanderlust", "Fernweh"), cache().get("fernWEH"))
    }

    @Test
    fun `not found is cached`() {
        cache().put("xyzzy", LookupResult.NotFound)
        assertEquals(LookupResult.NotFound, cache().get("xyzzy"))
    }

    @Test
    fun `failed is never cached`() {
        val c = cache()
        c.put("ging", LookupResult.Failed)
        assertNull(c.get("ging"))
        assertFalse(file.exists())
    }

    @Test
    fun `unknown text returns null`() {
        assertNull(cache().get("nope"))
    }

    @Test
    fun `entries older than 30 days are ignored`() {
        cache().put("alt", LookupResult.NotFound)
        now += WiktionaryCache.MAX_AGE_MS + 1
        assertNull(cache().get("alt"))
    }

    @Test
    fun `entries at 30 days are still valid`() {
        cache().put("alt", LookupResult.NotFound)
        now += WiktionaryCache.MAX_AGE_MS
        assertEquals(LookupResult.NotFound, cache().get("alt"))
    }

    @Test
    fun `keeps at most 200 entries and drops oldest first`() {
        val c = cache()
        for (i in 0 until 205) {
            now += 1
            c.put("word$i", LookupResult.Found("m$i", "word$i"))
        }
        assertNull(c.get("word0"))
        assertNull(c.get("word4"))
        assertEquals(LookupResult.Found("m5", "word5"), c.get("word5"))
        assertEquals(LookupResult.Found("m204", "word204"), c.get("word204"))
        assertEquals(200, WiktionaryCacheCodec.decode(file.readText()).size)
    }

    @Test
    fun `codec skips corrupt entries`() {
        val raw = """[
            {"key":"a","storedAt":1,"found":true,"meaning":"m","candidate":"A"},
            {"key":"b"},
            "junk",
            {"key":"c","storedAt":2,"found":false}
        ]"""
        val entries = WiktionaryCacheCodec.decode(raw)
        assertEquals(listOf("a", "c"), entries.map { it.key })
        assertTrue(entries[1].result == LookupResult.NotFound)
    }

    @Test
    fun `codec returns empty for blank or invalid input`() {
        assertTrue(WiktionaryCacheCodec.decode("").isEmpty())
        assertTrue(WiktionaryCacheCodec.decode("   ").isEmpty())
        assertTrue(WiktionaryCacheCodec.decode("{not json").isEmpty())
    }

    @Test
    fun `corrupt file reads as empty and is overwritten on put`() {
        file.writeText("garbage")
        val c = cache()
        assertNull(c.get("x"))
        c.put("x", LookupResult.NotFound)
        assertEquals(LookupResult.NotFound, c.get("x"))
    }
}
