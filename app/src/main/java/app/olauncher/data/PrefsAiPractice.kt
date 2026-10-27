package app.olauncher.data

import android.content.SharedPreferences
import androidx.core.content.edit
import app.olauncher.data.Prefs.Companion.AI_BASE_URL
import app.olauncher.data.Prefs.Companion.AI_BASE_URL_DEFAULT
import app.olauncher.data.Prefs.Companion.AI_MODEL
import app.olauncher.data.Prefs.Companion.AI_PROVIDER
import app.olauncher.data.Prefs.Companion.AI_PROVIDER_MELIOUS

/** Settings group: AI practice (see [Prefs]). */
interface AiPracticePrefs {

    /** Which provider adapter to use, e.g. [Prefs.AI_PROVIDER_MELIOUS]. */
    var aiProvider: String

    /** OpenAI-compatible base URL; must start with https://. */
    var aiBaseUrl: String

    /** Model id; empty until the benchmark (#600, 9.7) picks the default. */
    var aiModel: String
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
}
