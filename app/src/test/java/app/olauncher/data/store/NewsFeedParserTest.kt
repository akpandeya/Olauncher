package app.olauncher.data.store

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsFeedParserTest {

    private val now = Instant.parse("2026-11-01T12:00:00Z").toEpochMilli()

    private fun resource(name: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream(name)
            ?: javaClass.getResourceAsStream("/$name")
        return stream?.bufferedReader()?.readText() ?: error("Test resource '$name' not found")
    }

    @Test
    fun `parse rss2 skips bad date and missing link and reads items in order`() {
        val xml = resource("news/feed_rss2.xml")
        val items = NewsFeedParser.parse(xml)

        assertEquals(3, items.size)

        assertEquals("Bundestag beschließt neues Klimagesetz", items[0].title)
        assertEquals("https://www.tagesschau.de/inland/meldung-1?utm_source=rss", items[0].url)
        assertEquals(Instant.parse("2026-10-28T13:05:00Z").toEpochMilli(), items[0].publishedAt)

        assertEquals("Wirtschaftswachstum in Deutschland stabilisiert sich", items[1].title)
        assertEquals("https://www.tagesschau.de/wirtschaft/meldung-2", items[1].url)
        assertEquals(Instant.parse("2026-10-01T08:00:00Z").toEpochMilli(), items[1].publishedAt)

        assertEquals("Schulstart in mehreren Bundesländern", items[2].title)
        assertEquals("https://www.tagesschau.de/inland/meldung-3", items[2].url)
        assertEquals(Instant.parse("2026-09-01T06:30:00Z").toEpochMilli(), items[2].publishedAt)
    }

    @Test
    fun `recent on rss2 drops item older than 40 days and sorts newest first`() {
        val items = NewsFeedParser.parse(resource("news/feed_rss2.xml"))
        val recentItems = NewsFeedParser.recent(items, now)

        assertEquals(2, recentItems.size)
        assertEquals("Bundestag beschließt neues Klimagesetz", recentItems[0].title)
        assertEquals(Instant.parse("2026-10-28T13:05:00Z").toEpochMilli(), recentItems[0].publishedAt)

        assertEquals("Wirtschaftswachstum in Deutschland stabilisiert sich", recentItems[1].title)
        assertEquals(Instant.parse("2026-10-01T08:00:00Z").toEpochMilli(), recentItems[1].publishedAt)
    }

    @Test
    fun `parse rdf parses 2 items with correct urls and dc dates`() {
        val items = NewsFeedParser.parse(resource("news/feed_rdf.xml"))

        assertEquals(2, items.size)
        assertEquals("DW Wirtschaftsbericht aus Bonn", items[0].title)
        assertEquals("https://www.dw.com/de/wirtschaft-nachrichten/a-101", items[0].url)
        assertEquals(Instant.parse("2026-10-28T13:05:00Z").toEpochMilli(), items[0].publishedAt)

        assertEquals("DW Kultur und Gesellschaft", items[1].title)
        assertEquals("https://www.dw.com/de/kultur-berichte/a-102", items[1].url)
        assertEquals(Instant.parse("2026-10-27T09:15:00Z").toEpochMilli(), items[1].publishedAt)
    }

    @Test
    fun `parse atom parses 2 items with alternate link href and published or updated dates`() {
        val items = NewsFeedParser.parse(resource("news/feed_atom.xml"))

        assertEquals(2, items.size)
        assertEquals("ZEIT Debatte über Bildung", items[0].title)
        assertEquals("https://www.zeit.de/politik/artikel-1", items[0].url)
        assertEquals(Instant.parse("2026-10-28T13:00:00Z").toEpochMilli(), items[0].publishedAt)

        assertEquals("ZEIT Wissenschaft und Forschung", items[1].title)
        assertEquals("https://www.zeit.de/wissen/artikel-2", items[1].url)
        assertEquals(Instant.parse("2026-10-27T12:00:00Z").toEpochMilli(), items[1].publishedAt)
    }

    @Test
    fun `parse truncated or incomplete feed returns empty list`() {
        val items = NewsFeedParser.parse(resource("news/feed_truncated.xml"))
        assertTrue(items.isEmpty())
    }

    @Test
    fun `parse empty or non-feed markup returns empty list`() {
        assertTrue(NewsFeedParser.parse("").isEmpty())
        assertTrue(NewsFeedParser.parse("<html></html>").isEmpty())
    }

    @Test
    fun `parseDate handles RFC-1123, ISO formats and rejects garbage`() {
        val expected = Instant.parse("2026-10-28T13:05:00Z").toEpochMilli()

        // RFC-1123 with +0100
        assertEquals(expected, NewsFeedParser.parseDate("Wed, 28 Oct 2026 14:05:00 +0100"))

        // RFC-1123 with GMT
        assertEquals(expected, NewsFeedParser.parseDate("Wed, 28 Oct 2026 13:05:00 GMT"))

        // ISO with offset
        assertEquals(expected, NewsFeedParser.parseDate("2026-10-28T14:05:00+01:00"))

        // ISO with Z
        assertEquals(expected, NewsFeedParser.parseDate("2026-10-28T13:05:00Z"))

        // Garbage inputs
        assertNull(NewsFeedParser.parseDate("gestern"))
        assertNull(NewsFeedParser.parseDate("invalid-date-string"))
        assertNull(NewsFeedParser.parseDate(""))
        assertNull(NewsFeedParser.parseDate("   "))
    }
}
