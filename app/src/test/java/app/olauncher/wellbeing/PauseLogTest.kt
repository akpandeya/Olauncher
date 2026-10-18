package app.olauncher.wellbeing

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PauseLogTest {

    private class InMemoryStorage : PauseLogStorage {
        val longs = mutableMapOf<String, Long>()
        val ints = mutableMapOf<String, Int>()

        override fun getLong(key: String, defValue: Long): Long = longs[key] ?: defValue
        override fun getInt(key: String, defValue: Int): Int = ints[key] ?: defValue
        override fun putLong(key: String, value: Long) { longs[key] = value }
        override fun putInt(key: String, value: Int) { ints[key] = value }
        override fun contains(key: String): Boolean = longs.containsKey(key) || ints.containsKey(key)
    }

    private lateinit var storage: InMemoryStorage
    private var currentDay: Long = 1000L

    @Before
    fun setUp() {
        storage = InMemoryStorage()
        currentDay = 1000L
        PauseLog.onDayRollover = null
    }

    @After
    fun tearDown() {
        PauseLog.onDayRollover = null
    }

    @Test
    fun `initial run sets today day without triggering rollover`() {
        var rolloverReceived: PauseDay? = null
        val log = PauseLog(
            storage = storage,
            daySupplier = { currentDay },
            onDayRollover = { rolloverReceived = it },
        )

        assertFalse(storage.contains(PauseLog.PAUSE_LOG_DAY))
        val rolled = log.checkRollover()
        assertFalse(rolled)
        assertNull(rolloverReceived)
        assertEquals(1000L, storage.getLong(PauseLog.PAUSE_LOG_DAY, 0L))
        assertEquals(0, log.notNowToday)
        assertEquals(0, log.openedToday)
    }

    @Test
    fun `recordNotNow increments count and stores today`() {
        val log = PauseLog(storage, daySupplier = { currentDay })

        assertEquals(1, log.recordNotNow())
        assertEquals(1, log.notNowToday)
        assertEquals(1, storage.getInt(PauseLog.PAUSE_NOT_NOW_TODAY, 0))

        assertEquals(2, log.recordNotNow())
        assertEquals(2, log.notNowToday)
        assertEquals(2, storage.getInt(PauseLog.PAUSE_NOT_NOW_TODAY, 0))
    }

    @Test
    fun `recordOpen without why increments opened count only`() {
        val log = PauseLog(storage, daySupplier = { currentDay })

        assertEquals(1, log.recordOpen())
        assertEquals(1, log.openedToday)
        assertEquals(0, log.whyReplyToday)
        assertEquals(0, log.whyLookupToday)
        assertEquals(0, log.whyBoredToday)

        assertEquals(2, log.recordOpen(null))
        assertEquals(2, log.openedToday)
        assertEquals(0, log.whyReplyToday)
    }

    @Test
    fun `recordOpen with why choices increments both opened and why counter`() {
        val log = PauseLog(storage, daySupplier = { currentDay })

        assertEquals(1, log.recordOpen(PauseWhy.REPLY))
        assertEquals(1, log.openedToday)
        assertEquals(1, log.whyReplyToday)
        assertEquals(0, log.whyLookupToday)
        assertEquals(0, log.whyBoredToday)

        assertEquals(2, log.recordOpen(PauseWhy.LOOKUP))
        assertEquals(2, log.openedToday)
        assertEquals(1, log.whyReplyToday)
        assertEquals(1, log.whyLookupToday)
        assertEquals(0, log.whyBoredToday)

        assertEquals(3, log.recordOpen(PauseWhy.BORED))
        assertEquals(3, log.openedToday)
        assertEquals(1, log.whyReplyToday)
        assertEquals(1, log.whyLookupToday)
        assertEquals(1, log.whyBoredToday)
    }

    @Test
    fun `recordWhy increments specific why counter directly`() {
        val log = PauseLog(storage, daySupplier = { currentDay })

        assertEquals(1, log.recordWhy(PauseWhy.BORED))
        assertEquals(2, log.recordWhy(PauseWhy.BORED))
        assertEquals(2, log.whyBoredToday)
        assertEquals(0, log.openedToday)
    }

    @Test
    fun `today returns snapshot of all counters for the active day`() {
        val log = PauseLog(storage, daySupplier = { currentDay })

        log.recordNotNow()
        log.recordNotNow()
        log.recordOpen(PauseWhy.REPLY)
        log.recordOpen(PauseWhy.BORED)
        log.recordOpen(null)

        val today = log.today()
        assertEquals(1000L, today.epochDay)
        assertEquals(1000L, today.day)
        assertEquals(2, today.notNow)
        assertEquals(3, today.opened)
        assertEquals(1, today.whyReply)
        assertEquals(0, today.whyLookup)
        assertEquals(1, today.whyBored)
        assertEquals(2, today.totalWhy)
    }

    @Test
    fun `rollover on new day resets counters and passes previous day values to callback`() {
        var rolloverReceived: PauseDay? = null
        val log = PauseLog(
            storage = storage,
            daySupplier = { currentDay },
            onDayRollover = { rolloverReceived = it },
        )

        // Day 1000 activity
        log.recordNotNow()
        log.recordOpen(PauseWhy.REPLY)
        log.recordOpen(PauseWhy.LOOKUP)
        log.recordOpen(PauseWhy.BORED)
        log.recordOpen(PauseWhy.BORED)

        assertEquals(1, log.notNowToday)
        assertEquals(4, log.openedToday)
        assertEquals(1, log.whyReplyToday)
        assertEquals(1, log.whyLookupToday)
        assertEquals(2, log.whyBoredToday)

        // Advance to next day
        currentDay = 1001L

        // Trigger rollover via recordNotNow on new day
        log.recordNotNow()

        assertNotNull(rolloverReceived)
        val prev = rolloverReceived!!
        assertEquals(1000L, prev.epochDay)
        assertEquals(1000L, prev.day)
        assertEquals(1, prev.notNow)
        assertEquals(4, prev.opened)
        assertEquals(1, prev.whyReply)
        assertEquals(1, prev.whyLookup)
        assertEquals(2, prev.whyBored)

        // On day 1001: notNow has 1 (from the recordNotNow call), others reset to 0
        assertEquals(1001L, storage.getLong(PauseLog.PAUSE_LOG_DAY, 0L))
        assertEquals(1, log.notNowToday)
        assertEquals(0, log.openedToday)
        assertEquals(0, log.whyReplyToday)
        assertEquals(0, log.whyLookupToday)
        assertEquals(0, log.whyBoredToday)
    }

    @Test
    fun `rollover triggers via checkRollover directly`() {
        var previousDay: PauseDay? = null
        val log = PauseLog(
            storage = storage,
            daySupplier = { currentDay },
            onDayRollover = { previousDay = it },
        )

        log.recordOpen(PauseWhy.LOOKUP)
        assertEquals(1, log.openedToday)

        currentDay = 1005L
        val rolled = log.checkRollover()
        assertTrue(rolled)

        assertNotNull(previousDay)
        assertEquals(1000L, previousDay?.epochDay)
        assertEquals(1, previousDay?.opened)
        assertEquals(1, previousDay?.whyLookup)
        assertEquals(0, previousDay?.notNow)

        assertEquals(0, log.openedToday)
        assertEquals(0, log.whyLookupToday)
        assertEquals(1005L, log.today().epochDay)

        // Calling checkRollover again on same day should not trigger
        var secondRoll: PauseDay? = null
        log.onDayRollover = { secondRoll = it }
        val secondRolled = log.checkRollover()
        assertFalse(secondRolled)
        assertNull(secondRoll)
    }

    @Test
    fun `global onDayRollover companion callback is invoked during rollover`() {
        var globalCallbackReceived: PauseDay? = null
        PauseLog.onDayRollover = { globalCallbackReceived = it }

        val log = PauseLog(storage, daySupplier = { currentDay })
        log.recordOpen(PauseWhy.BORED)

        currentDay = 1001L
        log.recordNotNow()

        assertNotNull(globalCallbackReceived)
        assertEquals(1000L, globalCallbackReceived?.epochDay)
        assertEquals(1, globalCallbackReceived?.opened)
        assertEquals(1, globalCallbackReceived?.whyBored)
    }

    @Test
    fun `both instance and global callbacks are notified once`() {
        var globalCalls = 0
        var instanceCalls = 0
        PauseLog.onDayRollover = { globalCalls++ }

        val log = PauseLog(
            storage = storage,
            daySupplier = { currentDay },
            onDayRollover = { instanceCalls++ },
        )
        log.recordOpen()

        currentDay = 1001L
        log.checkRollover()

        assertEquals(1, globalCalls)
        assertEquals(1, instanceCalls)
    }
}
