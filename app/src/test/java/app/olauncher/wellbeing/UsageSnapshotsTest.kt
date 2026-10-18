package app.olauncher.wellbeing

import app.olauncher.data.Prefs
import app.olauncher.data.store.Backup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

class UsageSnapshotsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `DaySnapshot codec round trip with all fields populated`() {
        val original = listOf(
            DaySnapshot(
                epochDay = 19500L,
                distractingMinutes = 45,
                distractingOpens = 12,
                topApps = listOf(
                    "YouTube" to 25,
                    "Instagram" to 15,
                    "Reddit" to 5,
                ),
                overAllowance = true,
                notNow = 4,
                opened = 7,
                why = mapOf("reply" to 2, "lookup" to 1, "bored" to 4),
                water = 3,
                walk = 2,
            ),
            DaySnapshot(
                epochDay = 19501L,
                distractingMinutes = 10,
                distractingOpens = 3,
                topApps = listOf("Twitter" to 10),
                overAllowance = false,
                notNow = 1,
                opened = 2,
                why = mapOf("reply" to 2),
                water = 5,
                walk = 0,
            ),
        )

        val encoded = DaySnapshotCodec.encode(original)
        val decoded = DaySnapshotCodec.decode(encoded)

        assertEquals(original.size, decoded.size)
        assertEquals(original, decoded)
    }

    @Test
    fun `DaySnapshot codec round trip with null usage and empty fields`() {
        val original = listOf(
            DaySnapshot(
                epochDay = 19500L,
                distractingMinutes = null,
                distractingOpens = null,
                topApps = emptyList(),
                overAllowance = false,
                notNow = 0,
                opened = 0,
                why = emptyMap(),
                water = 0,
                walk = 0,
            )
        )

        val encoded = DaySnapshotCodec.encode(original)
        val decoded = DaySnapshotCodec.decode(encoded)

        assertEquals(original, decoded)
        assertNull(decoded[0].distractingMinutes)
        assertNull(decoded[0].distractingOpens)
        assertTrue(decoded[0].topApps.isEmpty())
        assertFalse(decoded[0].overAllowance)
    }

    @Test
    fun `DaySnapshot codec round trip with empty list`() {
        val encoded = DaySnapshotCodec.encode(emptyList())
        val decoded = DaySnapshotCodec.decode(encoded)
        assertTrue(decoded.isEmpty())
    }

    @Test
    fun `UsageSnapshotCodec alias works identically to DaySnapshotCodec`() {
        val original = listOf(DaySnapshot(epochDay = 100L, distractingMinutes = 10))
        val encoded = UsageSnapshotCodec.encode(original)
        val decoded = UsageSnapshotCodec.decode(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `codec rejects blank input`() {
        try {
            DaySnapshotCodec.decode("   ")
            fail("Expected IllegalArgumentException for blank input")
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun `codec rejects malformed JSON`() {
        val invalidInputs = listOf(
            "not a json",
            "{ \"epochDay\": 100 }", // Object instead of Array
            "[ { \"epochDay\": \"not-a-number\" } ]",
            "[ { } ]", // Missing epochDay
            "[ { \"epochDay\": 100, \"distractingMinutes\": \"invalid\" } ]",
            "[ { \"epochDay\": 100, \"topApps\": \"not-an-array\" } ]",
            "[ { \"epochDay\": 100, \"topApps\": [ { \"label\": \"A\", \"minutes\": 1 }, { \"label\": \"B\", \"minutes\": 2 }, { \"label\": \"C\", \"minutes\": 3 }, { \"label\": \"D\", \"minutes\": 4 } ] } ]", // > 3 topApps
        )

        for (input in invalidInputs) {
            try {
                DaySnapshotCodec.decode(input)
                fail("Expected IllegalArgumentException for invalid input: $input")
            } catch (_: IllegalArgumentException) {}
        }
    }

    @Test
    fun `DaySnapshot requires topApps size at most 3`() {
        try {
            DaySnapshot(
                epochDay = 100L,
                topApps = listOf(
                    "App1" to 10,
                    "App2" to 20,
                    "App3" to 30,
                    "App4" to 40,
                )
            )
            fail("Expected IllegalArgumentException for >3 topApps")
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun `DaySnapshot rejects negative values`() {
        try {
            DaySnapshot(epochDay = 100L, distractingMinutes = -5)
            fail("Expected IllegalArgumentException for negative distractingMinutes")
        } catch (_: IllegalArgumentException) {}

        try {
            DaySnapshot(epochDay = 100L, notNow = -1)
            fail("Expected IllegalArgumentException for negative notNow")
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun `56-day trimming in UsageSnapshotStore keeps last 56 days`() {
        val storeDir = tempFolder.newFolder("trim_test")
        val store = UsageSnapshotStore(storeDir)

        // Insert 65 consecutive days: 1L..65L
        for (day in 1L..65L) {
            store.upsert(DaySnapshot(epochDay = day, notNow = day.toInt()))
        }

        val all = store.getAll()
        assertEquals(56, all.size)
        // Kept days must be 10L..65L (oldest 1..9 trimmed)
        assertEquals(10L, all.first().epochDay)
        assertEquals(65L, all.last().epochDay)
        assertEquals((10L..65L).toList(), all.map { it.epochDay })
    }

    @Test
    fun `56-day trimming trims days older than 56 days before maxDay even with gaps`() {
        val storeDir = tempFolder.newFolder("trim_gap_test")
        val store = UsageSnapshotStore(storeDir)

        // Day 1 and Day 100 (gap > 56 days)
        store.upsert(DaySnapshot(epochDay = 1L))
        store.upsert(DaySnapshot(epochDay = 100L))

        val all = store.getAll()
        assertEquals(1, all.size)
        assertEquals(100L, all.first().epochDay)
    }

    @Test
    fun `range queries return inclusive subset in chronological order`() {
        val storeDir = tempFolder.newFolder("range_test")
        val store = UsageSnapshotStore(storeDir)

        for (day in 10L..20L) {
            store.upsert(DaySnapshot(epochDay = day, water = day.toInt()))
        }

        val subset = store.range(12L, 15L)
        assertEquals(listOf(12L, 13L, 14L, 15L), subset.map { it.epochDay })
        assertEquals(listOf(12, 13, 14, 15), subset.map { it.water })

        // Empty when fromDay > toDay
        assertTrue(store.range(15L, 12L).isEmpty())

        // Non-overlapping range
        assertTrue(store.range(100L, 200L).isEmpty())
    }

    @Test
    fun `merging a counter rollover with a later usage fill for the same day`() {
        val storeDir = tempFolder.newFolder("merge_test")
        val store = UsageSnapshotStore(storeDir)
        val epochDay = 19800L

        // Step 1: Counter rollover writes previous day counters (usage fields are null/default)
        val rolloverSnapshot = DaySnapshot(
            epochDay = epochDay,
            notNow = 3,
            opened = 5,
            why = mapOf("reply" to 2, "bored" to 3),
            water = 4,
            walk = 2,
        )
        store.upsert(rolloverSnapshot)

        val afterRollover = store.get(epochDay)
        assertNotNull(afterRollover)
        assertEquals(3, afterRollover!!.notNow)
        assertEquals(5, afterRollover.opened)
        assertEquals(mapOf("reply" to 2, "bored" to 3), afterRollover.why)
        assertEquals(4, afterRollover.water)
        assertEquals(2, afterRollover.walk)
        assertNull(afterRollover.distractingMinutes)
        assertNull(afterRollover.distractingOpens)
        assertTrue(afterRollover.topApps.isEmpty())
        assertFalse(afterRollover.overAllowance)

        // Step 2: Later, usage fill writes usage stats (counter fields are default 0/empty)
        val usageSnapshot = DaySnapshot(
            epochDay = epochDay,
            distractingMinutes = 45,
            distractingOpens = 12,
            topApps = listOf(
                "YouTube" to 30,
                "Reddit" to 15,
            ),
            overAllowance = true,
        )
        store.upsert(usageSnapshot)

        // Step 3: Verify the merged snapshot preserves both counters and usage
        val merged = store.get(epochDay)
        assertNotNull(merged)
        assertEquals(epochDay, merged!!.epochDay)
        assertEquals(45, merged.distractingMinutes)
        assertEquals(12, merged.distractingOpens)
        assertEquals(listOf("YouTube" to 30, "Reddit" to 15), merged.topApps)
        assertTrue(merged.overAllowance)
        assertEquals(3, merged.notNow)
        assertEquals(5, merged.opened)
        assertEquals(mapOf("reply" to 2, "bored" to 3), merged.why)
        assertEquals(4, merged.water)
        assertEquals(2, merged.walk)
    }

    @Test
    fun `merging works when usage fill comes before counter rollover`() {
        val storeDir = tempFolder.newFolder("merge_reverse_test")
        val store = UsageSnapshotStore(storeDir)
        val epochDay = 19800L

        // Usage fill first
        val usageSnapshot = DaySnapshot(
            epochDay = epochDay,
            distractingMinutes = 60,
            distractingOpens = 20,
            topApps = listOf("Instagram" to 60),
            overAllowance = true,
        )
        store.upsert(usageSnapshot)

        // Counter rollover second
        val rolloverSnapshot = DaySnapshot(
            epochDay = epochDay,
            notNow = 2,
            opened = 8,
            why = mapOf("lookup" to 3),
            water = 6,
            walk = 3,
        )
        store.upsert(rolloverSnapshot)

        val merged = store.get(epochDay)
        assertNotNull(merged)
        assertEquals(epochDay, merged!!.epochDay)
        assertEquals(60, merged.distractingMinutes)
        assertEquals(20, merged.distractingOpens)
        assertEquals(listOf("Instagram" to 60), merged.topApps)
        assertTrue(merged.overAllowance)
        assertEquals(2, merged.notNow)
        assertEquals(8, merged.opened)
        assertEquals(mapOf("lookup" to 3), merged.why)
        assertEquals(6, merged.water)
        assertEquals(3, merged.walk)
    }

    @Test
    fun `mergeSnapshots directly combines counters and usage`() {
        val snap1 = DaySnapshot(
            epochDay = 50L,
            distractingMinutes = 30,
            distractingOpens = 10,
            topApps = listOf("A" to 30),
            overAllowance = false,
            notNow = 1,
            opened = 2,
            why = mapOf("reply" to 2),
            water = 3,
            walk = 1,
        )
        val snap2 = DaySnapshot(
            epochDay = 50L,
            distractingMinutes = 40,
            distractingOpens = 15,
            topApps = listOf("B" to 40),
            overAllowance = true,
            notNow = 4,
            opened = 5,
            why = mapOf("bored" to 3),
            water = 6,
            walk = 2,
        )

        val merged = mergeSnapshots(snap1, snap2)
        assertEquals(50L, merged.epochDay)
        assertEquals(40, merged.distractingMinutes)
        assertEquals(15, merged.distractingOpens)
        assertEquals(listOf("B" to 40), merged.topApps)
        assertTrue(merged.overAllowance)
        assertEquals(4, merged.notNow)
        assertEquals(5, merged.opened)
        assertEquals(mapOf("reply" to 2, "bored" to 3), merged.why)
        assertEquals(6, merged.water)
        assertEquals(2, merged.walk)
    }

    @Test
    fun `Backup EXPORTED_FILES includes usage_days json`() {
        assertTrue(Backup.EXPORTED_FILES.contains("usage_days.json"))
    }

    @Test
    fun `UsageSnapshotWorker constant matches specification`() {
        assertEquals("usage_snapshot", UsageSnapshotWorker.WORK_NAME)
    }

    @Test
    fun `dayStartAndEndMillis spans exact 24 hours`() {
        val epochDay = 20000L
        val zone = ZoneId.of("UTC")
        val (start, end) = dayStartAndEndMillis(epochDay, zone)
        assertEquals(24 * 60 * 60 * 1000L, end - start)
        assertEquals(LocalDate.ofEpochDay(epochDay).atStartOfDay(zone).toInstant().toEpochMilli(), start)
    }

    private class FakeUsageRepository(
        private val statsPerDay: Map<Long, Map<String, Long>> = emptyMap(),
        private val opensPerDay: Map<Long, Int> = emptyMap(),
    ) : UsageRepository() {
        override suspend fun foregroundMillisByPackageForDay(
            pkgs: Set<String>,
            epochDay: Long,
        ): Map<String, Long>? {
            return statsPerDay[epochDay]?.filterKeys { it in pkgs }
        }

        override suspend fun openCountForDay(
            pkgs: Set<String>,
            epochDay: Long,
        ): Int? {
            return opensPerDay[epochDay]
        }
    }

    @Test
    fun `fillUsageSnapshots writes null usage when usage access is off`() = kotlinx.coroutines.runBlocking {
        val storeDir = tempFolder.newFolder("worker_access_off")
        val store = UsageSnapshotStore(storeDir)
        val today = 100L
        val yesterday = 99L

        UsageSnapshotWorker.fillUsageSnapshots(
            today = today,
            store = store,
            usageRepo = FakeUsageRepository(),
            hasUsageAccess = false,
            distractingPkgs = setOf("com.example.app"),
            allowanceMin = 30,
            resolveAppLabel = { it }
        )

        val snapshot = store.get(yesterday)
        assertNotNull(snapshot)
        assertNull(snapshot!!.distractingMinutes)
        assertNull(snapshot.distractingOpens)
        assertTrue(snapshot.topApps.isEmpty())
        assertFalse(snapshot.overAllowance)
    }

    @Test
    fun `fillUsageSnapshots fills usage fields and respects allowance when usage access is on`() = kotlinx.coroutines.runBlocking {
        val storeDir = tempFolder.newFolder("worker_access_on")
        val store = UsageSnapshotStore(storeDir)
        val today = 100L
        val yesterday = 99L

        val fakeRepo = FakeUsageRepository(
            statsPerDay = mapOf(
                yesterday to mapOf(
                    "com.youtube" to 40 * 60_000L,
                    "com.reddit" to 20 * 60_000L,
                    "com.unused" to 0L,
                )
            ),
            opensPerDay = mapOf(yesterday to 14)
        )

        UsageSnapshotWorker.fillUsageSnapshots(
            today = today,
            store = store,
            usageRepo = fakeRepo,
            hasUsageAccess = true,
            distractingPkgs = setOf("com.youtube", "com.reddit", "com.unused"),
            allowanceMin = 45,
            resolveAppLabel = { pkg ->
                when (pkg) {
                    "com.youtube" -> "YouTube"
                    "com.reddit" -> "Reddit"
                    else -> pkg
                }
            }
        )

        val snapshot = store.get(yesterday)
        assertNotNull(snapshot)
        assertEquals(60, snapshot!!.distractingMinutes)
        assertEquals(14, snapshot.distractingOpens)
        assertTrue(snapshot.overAllowance)
        assertEquals(listOf("YouTube" to 40, "Reddit" to 20), snapshot.topApps)
    }

    @Test
    fun `fillUsageSnapshots preserves existing counters for yesterday`() = kotlinx.coroutines.runBlocking {
        val storeDir = tempFolder.newFolder("worker_preserve_counters")
        val store = UsageSnapshotStore(storeDir)
        val today = 100L
        val yesterday = 99L

        // Prior counter rollover for yesterday
        store.upsert(
            DaySnapshot(
                epochDay = yesterday,
                notNow = 5,
                opened = 3,
                why = mapOf("reply" to 2),
                water = 4,
                walk = 2,
            )
        )

        val fakeRepo = FakeUsageRepository(
            statsPerDay = mapOf(yesterday to mapOf("pkg.a" to 30 * 60_000L)),
            opensPerDay = mapOf(yesterday to 8)
        )

        UsageSnapshotWorker.fillUsageSnapshots(
            today = today,
            store = store,
            usageRepo = fakeRepo,
            hasUsageAccess = true,
            distractingPkgs = setOf("pkg.a"),
            allowanceMin = 60,
            resolveAppLabel = { "App A" }
        )

        val snapshot = store.get(yesterday)
        assertNotNull(snapshot)
        // Counters preserved
        assertEquals(5, snapshot!!.notNow)
        assertEquals(3, snapshot.opened)
        assertEquals(mapOf("reply" to 2), snapshot.why)
        assertEquals(4, snapshot.water)
        assertEquals(2, snapshot.walk)
        // Usage filled
        assertEquals(30, snapshot.distractingMinutes)
        assertEquals(8, snapshot.distractingOpens)
        assertFalse(snapshot.overAllowance)
        assertEquals(listOf("App A" to 30), snapshot.topApps)
    }

    @Test
    fun `fillUsageSnapshots backfills missing days without overwriting existing usage`() = kotlinx.coroutines.runBlocking {
        val storeDir = tempFolder.newFolder("worker_backfill")
        val store = UsageSnapshotStore(storeDir)
        val today = 100L
        val yesterday = 99L
        val day96 = 96L
        val day95 = 95L

        // Day 96 was already filled with 10 mins
        store.upsert(
            DaySnapshot(
                epochDay = day96,
                distractingMinutes = 10,
                distractingOpens = 2,
                topApps = listOf("OldApp" to 10),
            )
        )

        val fakeRepo = FakeUsageRepository(
            statsPerDay = mapOf(
                yesterday to mapOf("pkg.a" to 25 * 60_000L),
                day95 to mapOf("pkg.a" to 15 * 60_000L),
                day96 to mapOf("pkg.a" to 999 * 60_000L), // Should NOT overwrite day 96
            ),
            opensPerDay = mapOf(yesterday to 5, day95 to 3, day96 to 999)
        )

        UsageSnapshotWorker.fillUsageSnapshots(
            today = today,
            store = store,
            usageRepo = fakeRepo,
            hasUsageAccess = true,
            distractingPkgs = setOf("pkg.a"),
            allowanceMin = 0,
            resolveAppLabel = { "App A" }
        )

        // Yesterday filled
        val snapYesterday = store.get(yesterday)
        assertNotNull(snapYesterday)
        assertEquals(25, snapYesterday!!.distractingMinutes)

        // Missing day 95 backfilled
        val snap95 = store.get(day95)
        assertNotNull(snap95)
        assertEquals(15, snap95!!.distractingMinutes)

        // Day 96 preserved
        val snap96 = store.get(day96)
        assertNotNull(snap96)
        assertEquals(10, snap96!!.distractingMinutes)
        assertEquals(listOf("OldApp" to 10), snap96.topApps)
    }

    @Test
    fun `PauseLog rollover writes previous day into UsageSnapshotStore`() {
        val storeDir = tempFolder.newFolder("pause_rollover")
        val store = UsageSnapshotStore(storeDir)

        val storage = object : PauseLogStorage {
            val ints = mutableMapOf<String, Int>()
            val longs = mutableMapOf<String, Long>()
            override fun getLong(key: String, defValue: Long): Long = longs[key] ?: defValue
            override fun getInt(key: String, defValue: Int): Int = ints[key] ?: defValue
            override fun putLong(key: String, value: Long) { longs[key] = value }
            override fun putInt(key: String, value: Int) { ints[key] = value }
            override fun contains(key: String): Boolean = longs.containsKey(key) || ints.containsKey(key)
        }

        var currentDay = 100L
        val log = PauseLog(
            storage = storage,
            daySupplier = { currentDay },
            onDayRollover = { prev ->
                store.upsert(
                    DaySnapshot(
                        epochDay = prev.epochDay,
                        notNow = prev.notNow,
                        opened = prev.opened,
                        why = buildMap {
                            if (prev.whyReply > 0) put("reply", prev.whyReply)
                            if (prev.whyLookup > 0) put("lookup", prev.whyLookup)
                            if (prev.whyBored > 0) put("bored", prev.whyBored)
                        }
                    )
                )
            }
        )

        log.recordNotNow()
        log.recordOpen(PauseWhy.REPLY)
        log.recordOpen(PauseWhy.BORED)

        currentDay = 101L
        log.checkRollover()

        val saved = store.get(100L)
        assertNotNull(saved)
        assertEquals(1, saved!!.notNow)
        assertEquals(2, saved.opened)
        assertEquals(mapOf("reply" to 1, "bored" to 1), saved.why)
    }

    @Test
    fun `store skips a corrupted entry and keeps the good days`() {
        val dir = tempFolder.newFolder("partial")
        val store = UsageSnapshotStore(dir)
        store.upsert(DaySnapshot(epochDay = 100L, notNow = 1))
        store.upsert(DaySnapshot(epochDay = 102L, notNow = 3))
        val file = File(dir, UsageSnapshotStore.FILE_NAME)
        val array = org.json.JSONArray(file.readText())
        val bad = org.json.JSONObject().put("epochDay", "not-a-number")
        val withBad = org.json.JSONArray()
        withBad.put(array.get(0)); withBad.put(bad); withBad.put(array.get(1))
        file.writeText(withBad.toString())

        val loaded = store.load()
        assertEquals(listOf(100L, 102L), loaded.snapshots.map { it.epochDay })
        assertEquals(1, loaded.skippedEntries)
        assertFalse(loaded.unparseable)

        assertTrue(store.upsert(DaySnapshot(epochDay = 103L, notNow = 4)))
        assertEquals(listOf(100L, 102L, 103L), store.getAll().map { it.epochDay })
    }

    @Test
    fun `upsert leaves an unparseable file untouched and reports failure`() {
        val dir = tempFolder.newFolder("broken")
        val file = File(dir, UsageSnapshotStore.FILE_NAME)
        val garbage = "[{\"epochDay\": 100, "
        file.writeText(garbage)
        val store = UsageSnapshotStore(dir)

        assertTrue(store.load().unparseable)
        assertTrue(store.getAll().isEmpty())
        assertFalse(store.upsert(DaySnapshot(epochDay = 200L, notNow = 9)))
        assertEquals(garbage, file.readText())
    }
}
