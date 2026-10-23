package app.olauncher.helper

import java.net.URLEncoder

enum class DictionaryWebsite(private val baseUrl: String) {
    VERBFORMEN("https://www.verbformen.de/?w="),
    DWDS("https://www.dwds.de/wb/"),
    DUDEN("https://www.duden.de/suchen/dudenonline/"),
    WIKTIONARY("https://en.wiktionary.org/wiki/");

    fun buildUrl(word: String): String {
        val encoded = URLEncoder.encode(word.trim(), "UTF-8").replace("+", "%20")
        return baseUrl + encoded
    }
}
