package app.olauncher.data

import android.content.SharedPreferences
import androidx.core.content.edit
import app.olauncher.data.Prefs.Companion.NEWS_FETCH_COUNT
import app.olauncher.data.Prefs.Companion.NEWS_FETCH_DAY
import app.olauncher.data.Prefs.Companion.NEWS_SENTENCES_ENABLED
import app.olauncher.data.Prefs.Companion.NEWS_SENTENCES_ENABLED_DEFAULT

interface NewsSentencesPrefs {
    var newsSentencesEnabled: Boolean
    fun newsArticlesFetchedToday(todayEpochDay: Long): Int
    fun recordNewsArticlesFetched(count: Int, todayEpochDay: Long)
}

internal class NewsSentencesPrefsStore(private val prefs: SharedPreferences) : NewsSentencesPrefs {

    override var newsSentencesEnabled: Boolean
        get() = prefs.getBoolean(NEWS_SENTENCES_ENABLED, NEWS_SENTENCES_ENABLED_DEFAULT)
        set(value) = prefs.edit { putBoolean(NEWS_SENTENCES_ENABLED, value).apply() }

    override fun newsArticlesFetchedToday(todayEpochDay: Long): Int {
        if (prefs.getLong(NEWS_FETCH_DAY, -1L) != todayEpochDay) return 0
        return prefs.getInt(NEWS_FETCH_COUNT, 0)
    }

    override fun recordNewsArticlesFetched(count: Int, todayEpochDay: Long) {
        val day = prefs.getLong(NEWS_FETCH_DAY, -1L)
        val current = if (day == todayEpochDay) prefs.getInt(NEWS_FETCH_COUNT, 0) else 0
        prefs.edit {
            putLong(NEWS_FETCH_DAY, todayEpochDay)
            putInt(NEWS_FETCH_COUNT, current + count).apply()
        }
    }
}
