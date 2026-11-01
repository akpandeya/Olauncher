package app.olauncher.data.store

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.util.ArrayDeque
import java.util.TreeMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpNewsFetcherTest {

    private class FakeNewsConnection(url: URL) : HttpURLConnection(url) {
        var status: Int = 200
        var bodyBytes: ByteArray = ByteArray(0)
        var responseCodeException: IOException? = null
        var disconnects: Int = 0
            private set

        val headers = TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER)
        private val requestProperties = TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER)

        var body: String
            get() = String(bodyBytes, Charsets.UTF_8)
            set(value) {
                bodyBytes = value.toByteArray(Charsets.UTF_8)
            }

        override fun connect() {}

        override fun disconnect() {
            disconnects++
        }

        override fun usingProxy(): Boolean = false

        override fun getResponseCode(): Int {
            responseCodeException?.let { throw it }
            return status
        }

        override fun getInputStream(): InputStream {
            if (status in 200..299) return ByteArrayInputStream(bodyBytes)
            throw IOException("HTTP $status")
        }

        override fun getHeaderField(name: String): String? = headers[name]

        override fun getContentType(): String? = headers["Content-Type"]

        override fun setRequestProperty(key: String, value: String) {
            super.setRequestProperty(key, value)
            requestProperties[key] = value
        }

        override fun getRequestProperty(key: String): String? {
            return requestProperties[key] ?: super.getRequestProperty(key)
        }
    }

    private val openedUrls = mutableListOf<String>()
    private val queuedConnections = ArrayDeque<FakeNewsConnection>()

    private val fetcher = object : HttpNewsFetcher() {
        override fun openConnection(url: URL): HttpURLConnection {
            openedUrls.add(url.toString())
            return queuedConnections.pollFirst() ?: FakeNewsConnection(url)
        }
    }

    @Test
    fun `200 with text xml charset UTF-8 returns Ok with expected config and one disconnect`() {
        val fake = FakeNewsConnection(URL("https://www.tagesschau.de/feed"))
        fake.status = 200
        fake.headers["Content-Type"] = "text/xml; charset=UTF-8"
        fake.body = "<rss>ä</rss>"
        queuedConnections.add(fake)

        val result = fetcher.fetch("https://www.tagesschau.de/feed")

        assertEquals(NewsFetch.Ok("<rss>ä</rss>"), result)
        assertEquals("GET", fake.requestMethod)
        assertEquals(HttpNewsFetcher.USER_AGENT, fake.getRequestProperty("User-Agent"))
        assertEquals(
            "application/rss+xml, application/atom+xml, application/xml, text/xml, text/html;q=0.9",
            fake.getRequestProperty("Accept")
        )
        assertFalse(fake.instanceFollowRedirects)
        assertFalse(fake.useCaches)
        assertEquals(15_000, fake.connectTimeout)
        assertEquals(20_000, fake.readTimeout)
        assertEquals(1, fake.disconnects)
        assertEquals(listOf("https://www.tagesschau.de/feed"), openedUrls)
    }

    @Test
    fun `ISO-8859-1 content type with bytes for Groesse is decoded correctly`() {
        val fake = FakeNewsConnection(URL("https://www.tagesschau.de/feed"))
        fake.status = 200
        fake.headers["Content-Type"] = "text/xml; charset=ISO-8859-1"
        fake.bodyBytes = "Größe".toByteArray(Charset.forName("ISO-8859-1"))
        queuedConnections.add(fake)

        val result = fetcher.fetch("https://www.tagesschau.de/feed")

        assertEquals(NewsFetch.Ok("Größe"), result)
        assertEquals(1, fake.disconnects)
    }

    @Test
    fun `off-list host and http url return NOT_ALLOWED with zero connections opened`() {
        val resultOffList = fetcher.fetch("https://example.com/feed")
        assertEquals(NewsFetch.Failed(NewsFetch.Reason.NOT_ALLOWED), resultOffList)
        assertEquals(0, openedUrls.size)

        val resultHttp = fetcher.fetch("http://www.tagesschau.de/x")
        assertEquals(NewsFetch.Failed(NewsFetch.Reason.NOT_ALLOWED), resultHttp)
        assertEquals(0, openedUrls.size)
    }

    @Test
    fun `404 maps to HTTP_ERROR and disconnects once`() {
        val fake = FakeNewsConnection(URL("https://www.tagesschau.de/notfound"))
        fake.status = 404
        queuedConnections.add(fake)

        val result = fetcher.fetch("https://www.tagesschau.de/notfound")

        assertEquals(NewsFetch.Failed(NewsFetch.Reason.HTTP_ERROR), result)
        assertEquals(1, fake.disconnects)
        assertEquals(1, openedUrls.size)
    }

    @Test
    fun `getResponseCode throwing IOException maps to NETWORK and disconnects once`() {
        val fake = FakeNewsConnection(URL("https://www.tagesschau.de/error"))
        fake.responseCodeException = IOException("Connection reset by peer")
        queuedConnections.add(fake)

        val result = fetcher.fetch("https://www.tagesschau.de/error")

        assertEquals(NewsFetch.Failed(NewsFetch.Reason.NETWORK), result)
        assertEquals(1, fake.disconnects)
        assertEquals(1, openedUrls.size)
    }

    @Test
    fun `body of MAX_BODY_BYTES plus one bytes returns TOO_LARGE`() {
        val fake = FakeNewsConnection(URL("https://www.tagesschau.de/large"))
        fake.status = 200
        fake.bodyBytes = ByteArray(HttpNewsFetcher.MAX_BODY_BYTES + 1)
        queuedConnections.add(fake)

        val result = fetcher.fetch("https://www.tagesschau.de/large")

        assertEquals(NewsFetch.Failed(NewsFetch.Reason.TOO_LARGE), result)
        assertEquals(1, fake.disconnects)
    }

    @Test
    fun `301 with relative Location resolves and follows redirect with two disconnects`() {
        val fake1 = FakeNewsConnection(URL("https://www.tagesschau.de/alt.html"))
        fake1.status = 301
        fake1.headers["Location"] = "/neu.html"

        val fake2 = FakeNewsConnection(URL("https://www.tagesschau.de/neu.html"))
        fake2.status = 200
        fake2.body = "<rss>neu</rss>"

        queuedConnections.addAll(listOf(fake1, fake2))

        val result = fetcher.fetch("https://www.tagesschau.de/alt.html")

        assertEquals(NewsFetch.Ok("<rss>neu</rss>"), result)
        assertEquals(
            listOf("https://www.tagesschau.de/alt.html", "https://www.tagesschau.de/neu.html"),
            openedUrls
        )
        assertEquals(1, fake1.disconnects)
        assertEquals(1, fake2.disconnects)
    }

    @Test
    fun `302 to unallowed host returns REDIRECT_NOT_ALLOWED and opens only one connection`() {
        val fake = FakeNewsConnection(URL("https://www.tagesschau.de/tracker"))
        fake.status = 302
        fake.headers["Location"] = "https://tracker.example.net/x"
        queuedConnections.add(fake)

        val result = fetcher.fetch("https://www.tagesschau.de/tracker")

        assertEquals(NewsFetch.Failed(NewsFetch.Reason.REDIRECT_NOT_ALLOWED), result)
        assertEquals(listOf("https://www.tagesschau.de/tracker"), openedUrls)
        assertEquals(1, fake.disconnects)
        assertTrue(queuedConnections.isEmpty())
    }

    @Test
    fun `three consecutive allowed redirects return HTTP_ERROR`() {
        val fake1 = FakeNewsConnection(URL("https://www.tagesschau.de/start"))
        fake1.status = 301
        fake1.headers["Location"] = "https://www.tagesschau.de/hop1"

        val fake2 = FakeNewsConnection(URL("https://www.tagesschau.de/hop1"))
        fake2.status = 302
        fake2.headers["Location"] = "https://www.tagesschau.de/hop2"

        val fake3 = FakeNewsConnection(URL("https://www.tagesschau.de/hop2"))
        fake3.status = 307
        fake3.headers["Location"] = "https://www.tagesschau.de/hop3"

        queuedConnections.addAll(listOf(fake1, fake2, fake3))

        val result = fetcher.fetch("https://www.tagesschau.de/start")

        assertEquals(NewsFetch.Failed(NewsFetch.Reason.HTTP_ERROR), result)
        assertEquals(
            listOf(
                "https://www.tagesschau.de/start",
                "https://www.tagesschau.de/hop1",
                "https://www.tagesschau.de/hop2"
            ),
            openedUrls
        )
        assertEquals(1, fake1.disconnects)
        assertEquals(1, fake2.disconnects)
        assertEquals(1, fake3.disconnects)
    }

    @Test
    fun `redirect missing Location header returns HTTP_ERROR`() {
        val fake = FakeNewsConnection(URL("https://www.tagesschau.de/missing-loc"))
        fake.status = 301
        queuedConnections.add(fake)

        val result = fetcher.fetch("https://www.tagesschau.de/missing-loc")

        assertEquals(NewsFetch.Failed(NewsFetch.Reason.HTTP_ERROR), result)
        assertEquals(1, fake.disconnects)
    }

    @Test
    fun `body of exact MAX_BODY_BYTES returns Ok`() {
        val fake = FakeNewsConnection(URL("https://www.tagesschau.de/exact"))
        fake.status = 200
        val bytes = ByteArray(HttpNewsFetcher.MAX_BODY_BYTES) { 'x'.code.toByte() }
        fake.bodyBytes = bytes
        queuedConnections.add(fake)

        val result = fetcher.fetch("https://www.tagesschau.de/exact")

        assertTrue(result is NewsFetch.Ok)
        assertEquals(HttpNewsFetcher.MAX_BODY_BYTES, (result as NewsFetch.Ok).body.length)
        assertEquals(1, fake.disconnects)
    }

    @Test
    fun `unsupported charset falls back to UTF-8`() {
        val fake = FakeNewsConnection(URL("https://www.tagesschau.de/unknown-charset"))
        fake.status = 200
        fake.headers["Content-Type"] = "text/xml; charset=unsupported-xyz-999"
        fake.body = "<rss>fallback</rss>"
        queuedConnections.add(fake)

        val result = fetcher.fetch("https://www.tagesschau.de/unknown-charset")

        assertEquals(NewsFetch.Ok("<rss>fallback</rss>"), result)
    }
}
