package app.olauncher.data.store

import java.net.URI

object UrlExtractor {

    private val URL_REGEX = Regex("""https?://[^\s]+""")

    fun extract(text: String?, subject: String?): Pair<String, String>? {
        val source = text?.takeIf { it.isNotBlank() }
            ?: subject?.takeIf { it.isNotBlank() }
            ?: return null

        val match = URL_REGEX.find(source) ?: return null
        val rawUrl = match.value
        val url = rawUrl.trimEnd('.', ',', ';', ':', '!', '?')

        val cleanSubject = subject?.trim()?.takeIf { it.isNotBlank() }
        val textMinusUrl = text?.replaceFirst(rawUrl, "")?.trim()?.takeIf { it.isNotBlank() }

        val title = cleanSubject
            ?: textMinusUrl
            ?: getHost(url)

        return Pair(url, title)
    }

    fun getHost(url: String): String {
        return runCatching {
            val uri = URI(url)
            uri.host?.takeIf { it.isNotBlank() }
        }.getOrNull()
            ?: Regex("""https?://([^/:\s?#]+)""").find(url)?.groupValues?.get(1)
            ?: url
    }
}
