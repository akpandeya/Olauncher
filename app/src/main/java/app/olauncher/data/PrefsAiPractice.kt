package app.olauncher.data

import android.content.SharedPreferences
import androidx.core.content.edit
import app.olauncher.data.Prefs.Companion.AI_BASE_URL
import app.olauncher.data.Prefs.Companion.AI_BASE_URL_DEFAULT
import app.olauncher.data.Prefs.Companion.AI_BILLING_BLOCKED
import app.olauncher.data.Prefs.Companion.AI_DAILY_WORD_CAP
import app.olauncher.data.Prefs.Companion.AI_DAILY_WORD_CAP_DEFAULT
import app.olauncher.data.Prefs.Companion.AI_KEY_REJECTED
import app.olauncher.data.Prefs.Companion.AI_MODEL
import app.olauncher.data.Prefs.Companion.AI_PRACTICE_ENABLED
import app.olauncher.data.Prefs.Companion.AI_PRACTICE_ENABLED_DEFAULT
import app.olauncher.data.Prefs.Companion.AI_PROVIDER
import app.olauncher.data.Prefs.Companion.AI_PROVIDER_MELIOUS
import app.olauncher.data.Prefs.Companion.AI_SENT_COUNT
import app.olauncher.data.Prefs.Companion.AI_SENT_DAY

/**
 * Settings group: AI practice (see [Prefs]). Also the budget bookkeeping the
 * generation queue (#601, 9.8) reads and writes: the daily word cap is counted
 * from a persisted "words sent today" counter, reset by calendar day, so it
 * holds across both the background top-up and the on-demand path.
 */
interface AiPracticePrefs {

    /** Which provider adapter to use, e.g. [Prefs.AI_PROVIDER_MELIOUS]. */
    var aiProvider: String

    /** OpenAI-compatible base URL; must start with https://. */
    var aiBaseUrl: String

    /** Model id; empty until the benchmark (#600, 9.7) picks the default. */
    var aiModel: String

    /** Master switch: off means no network call, ever. */
    var aiPracticeEnabled: Boolean

    /** The daily word cap: at most this many words are sent for generation per day. */
    var aiDailyWordCap: Int

    /** Set when the provider rejected the key (AUTH); cleared on a successful call. */
    var aiKeyRejected: Boolean

    /** Set when the provider is out of credits (BILLING); cleared on a successful call. */
    var aiBillingBlocked: Boolean

    /** Words sent for generation so far today (epoch day [todayEpochDay]); 0 on a new day. */
    fun aiWordsSentToday(todayEpochDay: Long): Int

    /** Adds [count] to today's sent-words counter, starting fresh when the day changed. */
    fun recordAiWordsSent(count: Int, todayEpochDay: Long)
}

internal class AiPracticePrefsStore(private val prefs: SharedPreferences) : AiPracticePrefs {

    override var aiProvider: String
        get() = prefs.getString(AI_PROVIDER, AI_PROVIDER_MELIOUS) ?: AI_PROVIDER_MELIOUS
        set(value) = prefs.edit { putString(AI_PROVIDER, value).apply() }

    override var aiBaseUrl: String
        get() = prefs.getString(AI_BASE_URL, AI_BASE_URL_DEFAULT) ?: AI_BASE_URL_DEFAULT
        set(value) = prefs.edit { putString(AI_BASE_URL, value).apply() }

    override var aiModel: String
        get() = prefs.getString(AI_MODEL, "") ?: ""
        set(value) = prefs.edit { putString(AI_MODEL, value).apply() }

    override var aiPracticeEnabled: Boolean
        get() = prefs.getBoolean(AI_PRACTICE_ENABLED, AI_PRACTICE_ENABLED_DEFAULT)
        set(value) = prefs.edit { putBoolean(AI_PRACTICE_ENABLED, value).apply() }

    override var aiDailyWordCap: Int
        get() = prefs.getInt(AI_DAILY_WORD_CAP, AI_DAILY_WORD_CAP_DEFAULT)
        set(value) = prefs.edit { putInt(AI_DAILY_WORD_CAP, value).apply() }

    override var aiKeyRejected: Boolean
        get() = prefs.getBoolean(AI_KEY_REJECTED, false)
        set(value) = prefs.edit { putBoolean(AI_KEY_REJECTED, value).apply() }

    override var aiBillingBlocked: Boolean
        get() = prefs.getBoolean(AI_BILLING_BLOCKED, false)
        set(value) = prefs.edit { putBoolean(AI_BILLING_BLOCKED, value).apply() }

    override fun aiWordsSentToday(todayEpochDay: Long): Int {
        if (prefs.getLong(AI_SENT_DAY, -1L) != todayEpochDay) return 0
        return prefs.getInt(AI_SENT_COUNT, 0)
    }

    override fun recordAiWordsSent(count: Int, todayEpochDay: Long) {
        val day = prefs.getLong(AI_SENT_DAY, -1L)
        val current = if (day == todayEpochDay) prefs.getInt(AI_SENT_COUNT, 0) else 0
        prefs.edit {
            putLong(AI_SENT_DAY, todayEpochDay)
            putInt(AI_SENT_COUNT, current + count).apply()
        }
    }
}
