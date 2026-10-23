package app.olauncher.helper

import app.olauncher.data.AppModel

object WordLookupRows {
    fun found(text: String, meaning: String, render: (String, String) -> String) = AppModel.Result(
        text = render(text, meaning),
        action = AppModel.Result.Action.SaveWords(listOf(text), meaning),
        multiline = true
    )

    fun saved(text: String, meaning: String, render: (String, String) -> String) = AppModel.Result(
        text = render(text, meaning),
        action = AppModel.Result.Action.OpenWiktionary(text, openOnTap = false),
        multiline = true
    )
}
