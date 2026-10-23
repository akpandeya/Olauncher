package app.olauncher.helper

import app.olauncher.data.AppModel
import app.olauncher.data.store.WiktionaryApi

object WordLookupRows {
    fun found(text: String, meaning: String, render: (String, String) -> String) = AppModel.Result(
        text = render(text, meaning),
        action = AppModel.Result.Action.SaveWords(listOf(text), meaning),
        multiline = true
    )

    fun saved(text: String, meaning: String, render: (String, String) -> String) = AppModel.Result(
        text = render(text, meaning),
        action = AppModel.Result.Action.OpenDictionary(text, openOnTap = false),
        multiline = true
    )

    /** Meaning on a saved-word row: the stored one, else an offline hit, else the fallback template. */
    fun savedMeaning(
        stored: String,
        lookupResult: WiktionaryApi.LookupResult?,
        noMeaningYet: String,
    ): String = stored.ifBlank {
        offlineOnlyMeaning(lookupResult).orEmpty()
    }.ifBlank { noMeaningYet }

    /** Meaning shown while online lookup is off: only an offline hit counts. */
    fun offlineOnlyMeaning(lookupResult: WiktionaryApi.LookupResult?): String? =
        (lookupResult as? WiktionaryApi.LookupResult.Found)?.meaning
}
