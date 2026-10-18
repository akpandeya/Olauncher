package app.olauncher.wellbeing

import android.content.SharedPreferences
import java.time.LocalDate

data class PauseDay(
    val epochDay: Long,
    val notNow: Int,
    val opened: Int,
    val whyReply: Int,
    val whyLookup: Int,
    val whyBored: Int,
) {
    val day: Long get() = epochDay
    val totalWhy: Int get() = whyReply + whyLookup + whyBored
}

enum class PauseWhy(val prefKey: String) {
    REPLY(PauseLog.PAUSE_WHY_REPLY_TODAY),
    LOOKUP(PauseLog.PAUSE_WHY_LOOKUP_TODAY),
    BORED(PauseLog.PAUSE_WHY_BORED_TODAY),
}

interface PauseLogStorage {
    fun getLong(key: String, defValue: Long): Long
    fun getInt(key: String, defValue: Int): Int
    fun putLong(key: String, value: Long)
    fun putInt(key: String, value: Int)
    fun contains(key: String): Boolean = false
}

class SharedPreferencesPauseLogStorage(
    private val prefs: SharedPreferences,
) : PauseLogStorage {
    override fun getLong(key: String, defValue: Long): Long = prefs.getLong(key, defValue)
    override fun getInt(key: String, defValue: Int): Int = prefs.getInt(key, defValue)
    override fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }
    override fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }
    override fun contains(key: String): Boolean = prefs.contains(key)
}

class PauseLog(
    private val storage: PauseLogStorage,
    private val daySupplier: () -> Long = { LocalDate.now().toEpochDay() },
    var onDayRollover: ((previous: PauseDay) -> Unit)? = null,
) {
    constructor(
        sharedPreferences: SharedPreferences,
        daySupplier: () -> Long = { LocalDate.now().toEpochDay() },
        onDayRollover: ((previous: PauseDay) -> Unit)? = null,
    ) : this(SharedPreferencesPauseLogStorage(sharedPreferences), daySupplier, onDayRollover)

    companion object {
        const val PAUSE_LOG_DAY = "PAUSE_LOG_DAY"
        const val PAUSE_NOT_NOW_TODAY = "PAUSE_NOT_NOW_TODAY"
        const val PAUSE_OPENED_TODAY = "PAUSE_OPENED_TODAY"
        const val PAUSE_WHY_REPLY_TODAY = "PAUSE_WHY_REPLY_TODAY"
        const val PAUSE_WHY_LOOKUP_TODAY = "PAUSE_WHY_LOOKUP_TODAY"
        const val PAUSE_WHY_BORED_TODAY = "PAUSE_WHY_BORED_TODAY"

        var onDayRollover: ((previous: PauseDay) -> Unit)? = null
    }

    val notNowToday: Int
        get() {
            checkRollover()
            return storage.getInt(PAUSE_NOT_NOW_TODAY, 0)
        }

    val openedToday: Int
        get() {
            checkRollover()
            return storage.getInt(PAUSE_OPENED_TODAY, 0)
        }

    val whyReplyToday: Int
        get() {
            checkRollover()
            return storage.getInt(PAUSE_WHY_REPLY_TODAY, 0)
        }

    val whyLookupToday: Int
        get() {
            checkRollover()
            return storage.getInt(PAUSE_WHY_LOOKUP_TODAY, 0)
        }

    val whyBoredToday: Int
        get() {
            checkRollover()
            return storage.getInt(PAUSE_WHY_BORED_TODAY, 0)
        }

    fun checkRollover(): Boolean {
        val today = daySupplier()
        val hasSavedDay = storage.contains(PAUSE_LOG_DAY)
        val savedDay = storage.getLong(PAUSE_LOG_DAY, 0L)
        if (!hasSavedDay && savedDay == 0L) {
            storage.putLong(PAUSE_LOG_DAY, today)
            return false
        }
        if (savedDay != today) {
            val previous = PauseDay(
                epochDay = savedDay,
                notNow = storage.getInt(PAUSE_NOT_NOW_TODAY, 0),
                opened = storage.getInt(PAUSE_OPENED_TODAY, 0),
                whyReply = storage.getInt(PAUSE_WHY_REPLY_TODAY, 0),
                whyLookup = storage.getInt(PAUSE_WHY_LOOKUP_TODAY, 0),
                whyBored = storage.getInt(PAUSE_WHY_BORED_TODAY, 0),
            )
            storage.putInt(PAUSE_NOT_NOW_TODAY, 0)
            storage.putInt(PAUSE_OPENED_TODAY, 0)
            storage.putInt(PAUSE_WHY_REPLY_TODAY, 0)
            storage.putInt(PAUSE_WHY_LOOKUP_TODAY, 0)
            storage.putInt(PAUSE_WHY_BORED_TODAY, 0)
            storage.putLong(PAUSE_LOG_DAY, today)

            onDayRollover?.invoke(previous)
            if (onDayRollover !== Companion.onDayRollover) {
                Companion.onDayRollover?.invoke(previous)
            }
            return true
        }
        return false
    }

    fun recordNotNow(): Int {
        checkRollover()
        val count = storage.getInt(PAUSE_NOT_NOW_TODAY, 0) + 1
        storage.putInt(PAUSE_NOT_NOW_TODAY, count)
        return count
    }

    fun recordOpen(why: PauseWhy? = null): Int {
        checkRollover()
        val count = storage.getInt(PAUSE_OPENED_TODAY, 0) + 1
        storage.putInt(PAUSE_OPENED_TODAY, count)
        if (why != null) {
            val whyKey = why.prefKey
            val whyCount = storage.getInt(whyKey, 0) + 1
            storage.putInt(whyKey, whyCount)
        }
        return count
    }

    fun recordWhy(why: PauseWhy): Int {
        checkRollover()
        val whyKey = why.prefKey
        val whyCount = storage.getInt(whyKey, 0) + 1
        storage.putInt(whyKey, whyCount)
        return whyCount
    }

    fun today(): PauseDay {
        checkRollover()
        return PauseDay(
            epochDay = storage.getLong(PAUSE_LOG_DAY, daySupplier()),
            notNow = storage.getInt(PAUSE_NOT_NOW_TODAY, 0),
            opened = storage.getInt(PAUSE_OPENED_TODAY, 0),
            whyReply = storage.getInt(PAUSE_WHY_REPLY_TODAY, 0),
            whyLookup = storage.getInt(PAUSE_WHY_LOOKUP_TODAY, 0),
            whyBored = storage.getInt(PAUSE_WHY_BORED_TODAY, 0),
        )
    }
}
