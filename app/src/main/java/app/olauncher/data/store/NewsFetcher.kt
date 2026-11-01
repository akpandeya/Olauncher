package app.olauncher.data.store

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

sealed class NewsFetch {
    data class Ok(val body: String) : NewsFetch()
    data class Failed(val reason: Reason) : NewsFetch()

    enum class Reason {
        NOT_ALLOWED,
        REDIRECT_NOT_ALLOWED,
        HTTP_ERROR,
        TOO_LARGE,
        NETWORK,
    }
}

fun interface NewsFetcher {
    fun fetch(url: String): NewsFetch
}

open class HttpNewsFetcher : NewsFetcher {

    override fun fetch(url: String): NewsFetch {
        if (!NewsSources.isAllowedUrl(url)) {
            return NewsFetch.Failed(NewsFetch.Reason.NOT_ALLOWED)
        }

        var currentUrl = url
        var redirects = 0

        while (true) {
            var connection: HttpURLConnection? = null
            val nextUrl = try {
                val conn = openConnection(URL(currentUrl))
                connection = conn
                conn.requestMethod = "GET"
                conn.connectTimeout = CONNECT_TIMEOUT_MS
                conn.readTimeout = READ_TIMEOUT_MS
                conn.instanceFollowRedirects = false
                conn.useCaches = false
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.setRequestProperty("Accept", ACCEPT_HEADER)

                val status = conn.responseCode
                when (status) {
                    200 -> return readResponseBody(conn)
                    301, 302, 303, 307, 308 -> {
                        val location = conn.getHeaderField("Location")
                        if (location.isNullOrBlank()) {
                            return NewsFetch.Failed(NewsFetch.Reason.HTTP_ERROR)
                        }
                        val resolved = try {
                            URL(URL(currentUrl), location).toString()
                        } catch (_: Exception) {
                            return NewsFetch.Failed(NewsFetch.Reason.HTTP_ERROR)
                        }
                        if (!NewsSources.isAllowedUrl(resolved)) {
                            return NewsFetch.Failed(NewsFetch.Reason.REDIRECT_NOT_ALLOWED)
                        }
                        if (redirects >= MAX_REDIRECTS) {
                            return NewsFetch.Failed(NewsFetch.Reason.HTTP_ERROR)
                        }
                        redirects++
                        resolved
                    }
                    else -> return NewsFetch.Failed(NewsFetch.Reason.HTTP_ERROR)
                }
            } catch (e: IOException) {
                return NewsFetch.Failed(NewsFetch.Reason.NETWORK)
            } catch (e: Exception) {
                return NewsFetch.Failed(NewsFetch.Reason.NETWORK)
            } finally {
                connection?.disconnect()
            }

            currentUrl = nextUrl
        }
    }

    protected open fun openConnection(url: URL): HttpURLConnection =
        url.openConnection() as HttpURLConnection

    private fun readResponseBody(connection: HttpURLConnection): NewsFetch {
        val inputStream = connection.inputStream ?: return NewsFetch.Failed(NewsFetch.Reason.HTTP_ERROR)
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var totalBytes = 0
        inputStream.use { stream ->
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                totalBytes += read
                if (totalBytes > MAX_BODY_BYTES) {
                    return NewsFetch.Failed(NewsFetch.Reason.TOO_LARGE)
                }
                out.write(buffer, 0, read)
            }
        }
        val bytes = out.toByteArray()
        val rawContentType = connection.contentType ?: connection.getHeaderField("Content-Type")
        val charset = determineCharset(rawContentType, bytes)
        return NewsFetch.Ok(String(bytes, charset))
    }

    private fun determineCharset(contentType: String?, bytes: ByteArray): Charset {
        // A valid header charset wins; an unknown or malformed one is treated as absent.
        return parseHeaderCharset(contentType)
            ?: sniffBodyCharset(bytes)
            ?: Charsets.UTF_8
    }

    private fun parseHeaderCharset(contentType: String?): Charset? {
        if (contentType.isNullOrBlank()) return null
        val parts = contentType.split(';')
        for (part in parts) {
            val trimmed = part.trim()
            val eqIndex = trimmed.indexOf('=')
            if (eqIndex != -1) {
                val paramName = trimmed.substring(0, eqIndex).trim()
                if (paramName.equals("charset", ignoreCase = true)) {
                    val name = trimmed.substring(eqIndex + 1).trim().trim('"', '\'')
                    return safeCharset(name)
                }
            }
        }
        val direct = contentType.trim().trim('"', '\'')
        return safeCharset(direct)
    }

    private fun sniffBodyCharset(bytes: ByteArray): Charset? {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return Charsets.UTF_8
        }
        // The declaration must open the document, so only its first bytes are read.
        val prefixLen = minOf(bytes.size, XML_DECL_MAX_BYTES)
        val prefix = String(bytes, 0, prefixLen, Charsets.ISO_8859_1)
        val match = XML_DECL_REGEX.find(prefix) ?: return null
        val encodingName = match.groupValues[1].trim()
        return safeCharset(encodingName)
    }

    private fun safeCharset(name: String): Charset? {
        if (name.isEmpty()) return null
        return try {
            if (Charset.isSupported(name)) Charset.forName(name) else null
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 20_000
        const val MAX_BODY_BYTES = 2_000_000
        const val MAX_REDIRECTS = 2
        const val USER_AGENT = "Thumbline (+https://github.com/akpandeya/Olauncher)"
        const val ACCEPT_HEADER =
            "application/rss+xml, application/atom+xml, application/xml, text/xml, text/html;q=0.9"

        private const val XML_DECL_MAX_BYTES = 256
        private val XML_DECL_REGEX = Regex(
            """^\s*<\?xml\s+[^>]*?\bencoding\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
    }
}
