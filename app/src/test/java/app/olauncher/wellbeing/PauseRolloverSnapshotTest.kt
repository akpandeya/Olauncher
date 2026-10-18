package app.olauncher.wellbeing

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PauseRolloverSnapshotTest {

    private class MapStorage : PauseLogStorage {
        val longs = mutableMapOf<String, Long>()
        val ints = mutableMapOf<String, Int>()
        override fun getLong(key: String, defValue: Long): Long = longs[key] ?: defValue
        override fun getInt(key: String, defValue: Int): Int = ints[key] ?: defValue
        override fun putLong(key: String, value: Long) { longs[key] = value }
        override fun putInt(key: String, value: Int) { ints[key] = value }
        override fun contains(key: String): Boolean = longs.containsKey(key) || ints.containsKey(key)
    }

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("rollover-snapshot").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `rollover through the shared log writes the previous day to the store`() {
        val store = UsageSnapshotStore(dir)
        var day = 20_100L
        // Same wiring as Prefs.pauseLog: rollover -> previous.toDaySnapshot() -> store.upsert.
        val log = PauseLog(
            storage = MapStorage(),
            daySupplier = { day },
            onDayRollover = { store.upsert(it.toDaySnapshot()) },
        )
        log.recordNotNow()
        log.recordNotNow()
        log.recordOpen(PauseWhy.BORED)
        assertNull(store.get(20_100L))

        day = 20_101L
        log.recordNotNow()

        val written = store.get(20_100L)!!
        assertEquals(2, written.notNow)
        assertEquals(1, written.opened)
        assertEquals(mapOf("bored" to 1), written.why)
        assertEquals(1, log.notNowToday)
        assertEquals(0, log.openedToday)
    }

    @Test
    fun `toDaySnapshot omits zero why counters`() {
        val snapshot = PauseDay(
            epochDay = 7L, notNow = 3, opened = 4, whyReply = 2, whyLookup = 0, whyBored = 5,
        ).toDaySnapshot()
        assertEquals(mapOf("reply" to 2, "bored" to 5), snapshot.why)
        assertEquals(7L, snapshot.epochDay)
    }
}
