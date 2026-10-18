package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlExtractorTest {

    @Test
    fun `plain url extracts url and host as title`() {
        val result = UrlExtractor.extract("https://example.com/some/path", null)
        assertEquals("https://example.com/some/path", result?.first)
        assertEquals("example.com", result?.second)

        val httpResult = UrlExtractor.extract("http://sub.test.org", null)
        assertEquals("http://sub.test.org", httpResult?.first)
        assertEquals("sub.test.org", httpResult?.second)
    }

    @Test
    fun `title and url extracts url and title from text`() {
        val result = UrlExtractor.extract("Title https://x.y/z", null)
        assertEquals("https://x.y/z", result?.first)
        assertEquals("Title", result?.second)

        val suffixResult = UrlExtractor.extract("https://x.y/z Suffix Title", null)
        assertEquals("https://x.y/z", suffixResult?.first)
        assertEquals("Suffix Title", suffixResult?.second)
    }

    @Test
    fun `no url returns null`() {
        assertNull(UrlExtractor.extract("Just some random text without any links", null))
        assertNull(UrlExtractor.extract("", null))
        assertNull(UrlExtractor.extract(null, null))
        assertNull(UrlExtractor.extract("   ", "   "))
    }

    @Test
    fun `multiple urls returns the first url`() {
        val result = UrlExtractor.extract("https://first.com/1 https://second.com/2", null)
        assertEquals("https://first.com/1", result?.first)

        val textWithMultiple = UrlExtractor.extract("Read https://first.com and also https://second.com", null)
        assertEquals("https://first.com", textWithMultiple?.first)
    }

    @Test
    fun `subject takes precedence over text title`() {
        val result = UrlExtractor.extract("Text Title https://x.y/z", "Subject Title")
        assertEquals("https://x.y/z", result?.first)
        assertEquals("Subject Title", result?.second)

        val plainUrlWithSubject = UrlExtractor.extract("https://x.y/z", "Subject Title")
        assertEquals("https://x.y/z", plainUrlWithSubject?.first)
        assertEquals("Subject Title", plainUrlWithSubject?.second)
    }

    @Test
    fun `trailing punctuation in url is stripped from url`() {
        val result = UrlExtractor.extract("Check this out: https://example.com/post.", null)
        assertEquals("https://example.com/post", result?.first)
    }
}
