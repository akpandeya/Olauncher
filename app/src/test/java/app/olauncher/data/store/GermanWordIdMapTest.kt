package app.olauncher.data.store

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The local-id → server-id bridge for word captures: put/read/overwrite,
 * UUID validation on both the codec and the store, reload from disk and
 * corrupt-entry tolerance. Everything in a fresh temporary folder per test.
 */
class GermanWordIdMapTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dir: File
    private lateinit var map: GermanWordIdMap

    private val uuidA = "9f1c2b3a-1111-4222-8333-444455556666"
    private val uuidB = "a1b2c3d4-5555-4666-8777-888899990000"

    @Before
    fun setUp() {
        dir = tempFolder.newFolder()
        map = GermanWordIdMap(dir)
    }

    @Test
    fun `put then serverIdFor returns the recorded server id`() {
        map.put(42L, uuidA)

        assertEquals(uuidA, map.serverIdFor(42L))
    }

    @Test
    fun `an unknown local id has no server id`() {
        assertNull(map.serverIdFor(999L))
    }

    @Test
    fun `the newest put for a local id wins`() {
        map.put(42L, uuidA)
        map.put(42L, uuidB)

        assertEquals(uuidB, map.serverIdFor(42L))
        assertEquals(1, GermanWordIdMap(dir).serverIdFor(42L)?.let { 1 } ?: 0)
        assertEquals(uuidB, GermanWordIdMap(dir).serverIdFor(42L))
    }

    @Test
    fun `distinct local ids keep distinct server ids`() {
        map.put(42L, uuidA)
        map.put(43L, uuidB)

        assertEquals(uuidA, map.serverIdFor(42L))
        assertEquals(uuidB, map.serverIdFor(43L))
    }

    @Test
    fun `a non-UUID server id is never stored`() {
        map.put(42L, "not-a-uuid")
        map.put(43L, "")

        assertNull(map.serverIdFor(42L))
        assertNull(map.serverIdFor(43L))
        assertFalse(File(dir, GermanWordIdMap.FILE_NAME).exists())
    }

    @Test
    fun `the map survives a reload from disk`() {
        map.put(42L, uuidA)
        map.put(43L, uuidB)

        val reloaded = GermanWordIdMap(dir)

        assertEquals(uuidA, reloaded.serverIdFor(42L))
        assertEquals(uuidB, reloaded.serverIdFor(43L))
    }

    @Test
    fun `a missing file is an empty mapping, not an error`() {
        assertNull(map.serverIdFor(42L))
        assertTrue(GermanWordIdCodec.decode(null).isEmpty())
        assertTrue(GermanWordIdCodec.decode("  ").isEmpty())
    }

    @Test
    fun `decode skips corrupt rows and non-UUID server ids but keeps the rest`() {
        val raw = """
            {"version":1,"entries":[
              {"localId":1,"serverId":"$uuidA"},
              {"localId":"one","serverId":"$uuidB"},
              {"localId":2,"serverId":"junk"},
              {"localId":3},
              {"junk":true}
            ]}
        """.trimIndent()

        val decoded = GermanWordIdCodec.decode(raw)

        assertEquals(listOf(GermanWordIdEntry(1L, uuidA)), decoded)
    }

    @Test
    fun `decode accepts integer and long local ids the way org json parses them`() {
        val raw = """{"version":1,"entries":[{"localId":1,"serverId":"$uuidA"},{"localId":9007199254740992,"serverId":"$uuidB"}]}"""

        val decoded = GermanWordIdCodec.decode(raw)

        assertEquals(listOf(1L, 9007199254740992L), decoded.map { it.localId })
    }

    @Test
    fun `a file that is not a JSON object decodes as empty`() {
        File(dir, GermanWordIdMap.FILE_NAME).writeText("[1,2,3]")

        assertNull(map.serverIdFor(42L))
    }
}
