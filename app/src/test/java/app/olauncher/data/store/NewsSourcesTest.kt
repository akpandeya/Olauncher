package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsSourcesTest {

    @Test
    fun `six sources exist with unique ids`() {
        assertEquals(6, NewsSources.ALL.size)
        val ids = NewsSources.ALL.map { it.id }
        assertEquals(6, ids.distinct().size)
    }

    @Test
    fun `every feedUrl passes isAllowedUrl`() {
        for (source in NewsSources.ALL) {
            assertTrue("feedUrl '${source.feedUrl}' should be allowed", NewsSources.isAllowedUrl(source.feedUrl))
        }
    }

    @Test
    fun `isAllowedUrl validates allowed and blocked hosts properly`() {
        // Allowed subdomains and apex domain
        assertTrue(NewsSources.isAllowedUrl("https://www.dw.com/de/top-thema/s-123"))
        assertTrue(NewsSources.isAllowedUrl("https://rss.dw.com/xml/rss-de-all"))
        assertTrue(NewsSources.isAllowedUrl("https://dw.com/"))

        // Blocked cases: lookalikes, non-https schemes, off-list domains
        assertFalse(NewsSources.isAllowedUrl("https://evil-dw.com/news"))
        assertFalse(NewsSources.isAllowedUrl("https://dw.com.evil.net/x"))
        assertFalse(NewsSources.isAllowedUrl("http://www.dw.com/x"))
        assertFalse(NewsSources.isAllowedUrl("https://other-news.de/index.html"))
        assertFalse(NewsSources.isAllowedUrl("not-a-url"))
        assertFalse(NewsSources.isAllowedUrl(""))
    }

    @Test
    fun `canonicalArticleUrl removes query and fragment and preserves valid path`() {
        val canonical = NewsSources.canonicalArticleUrl("https://www.dw.com/de/a-123?maca=de-rss#top")
        assertEquals("https://www.dw.com/de/a-123", canonical)
    }

    @Test
    fun `canonicalArticleUrl ensures root slash for empty path`() {
        val canonical = NewsSources.canonicalArticleUrl("https://www.dw.com")
        assertEquals("https://www.dw.com/", canonical)
    }

    @Test
    fun `canonicalArticleUrl returns null for non-https and off-list hosts`() {
        assertNull(NewsSources.canonicalArticleUrl("http://www.dw.com/de/a"))
        assertNull(NewsSources.canonicalArticleUrl("https://evil-dw.com/article"))
        assertNull(NewsSources.canonicalArticleUrl("https://unknown-outlet.com/story"))
    }

    @Test
    fun `sourceForUrl maps url to correct source`() {
        val source = NewsSources.sourceForUrl("https://www.spiegel.de/x")
        assertNotNull(source)
        assertEquals("spiegel", source?.id)
    }
}
