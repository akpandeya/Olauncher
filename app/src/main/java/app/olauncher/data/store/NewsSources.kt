package app.olauncher.data.store

import java.net.URI
import java.util.Locale

data class NewsSource(
    val id: String,
    val name: String,
    val feedUrl: String,
    val hosts: List<String>
)

/**
 * News outlets named in #694.
 *
 * The feed URLs are verified on a device by the maintainer, not by tests.
 */
object NewsSources {

    val ALL: List<NewsSource> = listOf(
        NewsSource("tagesschau", "tagesschau", "https://www.tagesschau.de/infoservices/alle-meldungen-100~rss2.xml", listOf("tagesschau.de")),
        NewsSource("dw", "DW", "https://rss.dw.com/xml/rss-de-all", listOf("dw.com")),
        NewsSource("ntv", "n-tv", "https://www.n-tv.de/rss", listOf("n-tv.de")),
        NewsSource("zeit", "ZEIT", "https://newsfeed.zeit.de/index", listOf("zeit.de")),
        NewsSource("spiegel", "SPIEGEL", "https://www.spiegel.de/schlagzeilen/index.rss", listOf("spiegel.de")),
        NewsSource("faz", "FAZ", "https://www.faz.net/rss/aktuell/", listOf("faz.net"))
    )

    fun isAllowedUrl(url: String): Boolean = sourceForUrl(url) != null

    fun sourceForUrl(url: String): NewsSource? {
        val uri = try {
            URI(url)
        } catch (_: Exception) {
            return null
        }
        val scheme = uri.scheme ?: return null
        if (!scheme.equals("https", ignoreCase = true)) return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        return ALL.firstOrNull { source ->
            source.hosts.any { allowedHost ->
                val lowerAllowed = allowedHost.lowercase(Locale.ROOT)
                host == lowerAllowed || host.endsWith(".$lowerAllowed")
            }
        }
    }

    fun canonicalArticleUrl(url: String): String? {
        val trimmed = url.trim()
        if (!isAllowedUrl(trimmed)) return null
        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            return null
        }
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        var path = uri.rawPath
        if (path.isNullOrEmpty()) {
            path = "/"
        } else if (!path.startsWith("/")) {
            path = "/$path"
        }
        return "https://$host$path"
    }
}
