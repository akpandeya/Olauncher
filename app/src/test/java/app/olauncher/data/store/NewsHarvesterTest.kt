package app.olauncher.data.store

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class NewsHarvesterTest {

    private val fixedClock = Instant.parse("2026-11-01T12:00:00Z").toEpochMilli()
    private lateinit var tempDir: File
    private lateinit var store: RecentSentencesStore
    private val recordedSleeps = mutableListOf<Long>()
    private val recordingSleep: suspend (Long) -> Unit = { recordedSleeps.add(it) }

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("news_harvester_test").toFile()
        store = RecentSentencesStore(tempDir)
        recordedSleeps.clear()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private class FakeFetcher(
        var responses: Map<String, NewsFetch> = emptyMap()
    ) : NewsFetcher {
        val requestedUrls = mutableListOf<String>()

        override fun fetch(url: String): NewsFetch {
            requestedUrls.add(url)
            return responses[url] ?: NewsFetch.Failed(NewsFetch.Reason.NETWORK)
        }
    }

    private fun rssFeed(vararg items: Pair<String, Long>): String {
        val itemsXml = items.joinToString("\n") { (url, publishedAt) ->
            val isoDate = Instant.ofEpochMilli(publishedAt).toString()
            """
            <item>
                <title>Test Nachricht</title>
                <link>$url</link>
                <pubDate>$isoDate</pubDate>
            </item>
            """.trimIndent()
        }
        return """
        <rss version="2.0">
            <channel>
                <title>Test Kanal</title>
                $itemsXml
            </channel>
        </rss>
        """.trimIndent()
    }

    private fun articleHtml(vararg sentences: String): String {
        val body = sentences.joinToString("\n") { s -> "<p>$s</p>" }
        return """
        <!DOCTYPE html>
        <html>
            <body>
                <article>
                    $body
                </article>
            </body>
        </html>
        """.trimIndent()
    }

    @Test
    fun `happy path with two sources stores sentences with right metadata and exact counts`() = runBlocking {
        val source1 = NewsSources.ALL[0] // tagesschau
        val source2 = NewsSources.ALL[1] // dw
        val sources = listOf(source1, source2)

        val pubDate1 = fixedClock - TimeUnit.HOURS.toMillis(1)
        val pubDate2 = fixedClock - TimeUnit.HOURS.toMillis(2)

        val feed1 = rssFeed("https://www.tagesschau.de/inland/meldung-1?utm_source=rss" to pubDate1)
        val feed2 = rssFeed("https://www.dw.com/de/meldung-2?source=app" to pubDate2)

        val sent1 = "Dies ist der erste vollständige Beispielsatz von Tagesschau."
        val sent2 = "Hier ist der zweite vollständige Beispielsatz von der Deutschen Welle."

        val responses = mapOf(
            source1.feedUrl to NewsFetch.Ok(feed1),
            source2.feedUrl to NewsFetch.Ok(feed2),
            "https://www.tagesschau.de/inland/meldung-1" to NewsFetch.Ok(articleHtml(sent1)),
            "https://www.dw.com/de/meldung-2" to NewsFetch.Ok(articleHtml(sent2))
        )
        val fetcher = FakeFetcher(responses)

        val harvester = NewsHarvester(
            fetcher = fetcher,
            store = store,
            sources = sources,
            random = Random(42),
            sleep = recordingSleep,
            clock = { fixedClock }
        )

        val result = harvester.harvest(NewsHarvestLimits(articlesLeftToday = 10))

        assertEquals(2, result.feedsFetched)
        assertEquals(0, result.feedsFailed)
        assertEquals(2, result.articleRequests)
        assertEquals(2, result.articlesFetched)
        assertEquals(0, result.articlesFailed)
        assertEquals(2, result.newSentences.size)

        val stored = store.all()
        assertEquals(2, stored.size)

        val s1 = stored.first { it.source == source1.id }
        assertEquals(sent1, s1.text)
        assertEquals("https://www.tagesschau.de/inland/meldung-1", s1.url)
        assertEquals(pubDate1, s1.publishedAt)
        assertEquals(fixedClock, s1.fetchedAt)

        val s2 = stored.first { it.source == source2.id }
        assertEquals(sent2, s2.text)
        assertEquals("https://www.dw.com/de/meldung-2", s2.url)
        assertEquals(pubDate2, s2.publishedAt)
        assertEquals(fixedClock, s2.fetchedAt)

        assertTrue(store.hasArticle("https://www.tagesschau.de/inland/meldung-1"))
        assertTrue(store.hasArticle("https://www.dw.com/de/meldung-2"))
    }

    @Test
    fun `articlesLeftToday zero makes zero requests and leaves store untouched`() = runBlocking {
        val fetcher = FakeFetcher()
        val harvester = NewsHarvester(
            fetcher = fetcher,
            store = store,
            random = Random(42),
            sleep = recordingSleep,
            clock = { fixedClock }
        )

        val result = harvester.harvest(NewsHarvestLimits(articlesLeftToday = 0))

        assertEquals(0, result.feedsFetched)
        assertEquals(0, result.feedsFailed)
        assertEquals(0, result.articleRequests)
        assertEquals(0, result.articlesFetched)
        assertEquals(0, result.articlesFailed)
        assertTrue(result.newSentences.isEmpty())
        assertTrue(fetcher.requestedUrls.isEmpty())
        assertTrue(store.all().isEmpty())
        assertTrue(recordedSleeps.isEmpty())
    }

    @Test
    fun `budget round-robin across sources with maxArticlesPerRun`() = runBlocking {
        val sources = NewsSources.ALL.take(3)
        val shuffledSources = sources.shuffled(Random(42))

        val responses = mutableMapOf<String, NewsFetch>()
        for (source in sources) {
            val host = source.hosts.first()
            val items = (1..4).map { i ->
                val articleUrl = "https://$host/art-$i"
                val pubDate = fixedClock - (i * 1000L)
                val html = articleHtml("Dies ist Satz $i für die Quelle ${source.id} im Testlauf.")
                responses[articleUrl] = NewsFetch.Ok(html)
                articleUrl to pubDate
            }
            responses[source.feedUrl] = NewsFetch.Ok(rssFeed(*items.toTypedArray()))
        }

        val fetcher = FakeFetcher(responses)
        val harvester = NewsHarvester(
            fetcher = fetcher,
            store = store,
            sources = sources,
            random = Random(42),
            sleep = recordingSleep,
            clock = { fixedClock }
        )

        val limits = NewsHarvestLimits(articlesLeftToday = 10, maxArticlesPerRun = 5)
        val result = harvester.harvest(limits)

        assertEquals(3, result.feedsFetched)
        assertEquals(5, result.articleRequests)
        assertEquals(5, result.articlesFetched)

        // Expected round robin order:
        // First item of each source in shuffled order, then second item of each
        val expectedArticles = listOf(
            "https://${shuffledSources[0].hosts.first()}/art-1",
            "https://${shuffledSources[1].hosts.first()}/art-1",
            "https://${shuffledSources[2].hosts.first()}/art-1",
            "https://${shuffledSources[0].hosts.first()}/art-2",
            "https://${shuffledSources[1].hosts.first()}/art-2"
        )

        val feedUrls = shuffledSources.map { it.feedUrl }
        assertEquals(feedUrls, fetcher.requestedUrls.take(3))
        assertEquals(expectedArticles, fetcher.requestedUrls.drop(3))
    }

    @Test
    fun `jitter records sleep before every request after the first within bounds`() = runBlocking {
        val source1 = NewsSources.ALL[0]
        val source2 = NewsSources.ALL[1]
        val sources = listOf(source1, source2)

        val responses = mapOf(
            source1.feedUrl to NewsFetch.Ok(rssFeed("https://www.tagesschau.de/art-1" to fixedClock - 1000L)),
            source2.feedUrl to NewsFetch.Ok(rssFeed("https://www.dw.com/art-2" to fixedClock - 1000L)),
            "https://www.tagesschau.de/art-1" to NewsFetch.Ok(articleHtml("Dies ist ein wichtiger Beispielsatz aus der Tagesschau.")),
            "https://www.dw.com/art-2" to NewsFetch.Ok(articleHtml("Dies ist ein weiterer wichtiger Beispielsatz von DW."))
        )
        val fetcher = FakeFetcher(responses)
        val harvester = NewsHarvester(
            fetcher = fetcher,
            store = store,
            sources = sources,
            random = Random(42),
            sleep = recordingSleep,
            clock = { fixedClock }
        )

        val minJitter = 4_000L
        val maxJitter = 8_000L
        harvester.harvest(NewsHarvestLimits(articlesLeftToday = 5, minJitterMs = minJitter, maxJitterMs = maxJitter))

        val totalRequests = fetcher.requestedUrls.size
        assertEquals(4, totalRequests) // 2 feeds + 2 articles
        assertEquals(totalRequests - 1, recordedSleeps.size)

        for (sleepMs in recordedSleeps) {
            assertTrue("Sleep $sleepMs should be in [$minJitter, $maxJitter]", sleepMs in minJitter..maxJitter)
        }
    }

    @Test
    fun `item older than 40 days and item linking to another outlet are never requested`() = runBlocking {
        val source = NewsSources.ALL[0] // tagesschau
        val freshValidUrl = "https://www.tagesschau.de/inland/gueltig"
        val oldUrl = "https://www.tagesschau.de/inland/zu-alt"
        val foreignUrl = "https://www.spiegel.de/spiegel-artikel"

        val feedXml = rssFeed(
            freshValidUrl to (fixedClock - TimeUnit.HOURS.toMillis(2)),
            oldUrl to (fixedClock - TimeUnit.DAYS.toMillis(45)),
            foreignUrl to (fixedClock - TimeUnit.HOURS.toMillis(1))
        )

        val responses = mapOf(
            source.feedUrl to NewsFetch.Ok(feedXml),
            freshValidUrl to NewsFetch.Ok(articleHtml("Hier ist ein gültiger Nachrichtensatz für die Leser."))
        )
        val fetcher = FakeFetcher(responses)
        val harvester = NewsHarvester(
            fetcher = fetcher,
            store = store,
            sources = listOf(source),
            random = Random(42),
            sleep = recordingSleep,
            clock = { fixedClock }
        )

        val result = harvester.harvest(NewsHarvestLimits(articlesLeftToday = 10))

        assertEquals(1, result.articleRequests)
        assertEquals(1, result.articlesFetched)
        assertEquals(listOf(source.feedUrl, freshValidUrl), fetcher.requestedUrls)
        assertFalse(fetcher.requestedUrls.contains(oldUrl))
        assertFalse(fetcher.requestedUrls.contains(foreignUrl))
    }

    @Test
    fun `url already recorded in store is not requested again on second run`() = runBlocking {
        val source = NewsSources.ALL[0]
        val artUrl = "https://www.tagesschau.de/inland/meldung-einmal"

        val feedXml = rssFeed(artUrl to (fixedClock - TimeUnit.HOURS.toMillis(1)))
        val responses = mapOf(
            source.feedUrl to NewsFetch.Ok(feedXml),
            artUrl to NewsFetch.Ok(articleHtml("Hier ist ein Satz, der nur einmal heruntergeladen werden soll."))
        )
        val fetcher = FakeFetcher(responses)
        val harvester = NewsHarvester(
            fetcher = fetcher,
            store = store,
            sources = listOf(source),
            random = Random(42),
            sleep = recordingSleep,
            clock = { fixedClock }
        )

        val result1 = harvester.harvest(NewsHarvestLimits(articlesLeftToday = 10))
        assertEquals(1, result1.articlesFetched)
        assertTrue(store.hasArticle(artUrl))

        fetcher.requestedUrls.clear()

        val result2 = harvester.harvest(NewsHarvestLimits(articlesLeftToday = 10))
        assertEquals(1, result2.feedsFetched)
        assertEquals(0, result2.articleRequests)
        assertEquals(0, result2.articlesFetched)
        assertEquals(listOf(source.feedUrl), fetcher.requestedUrls)
    }

    @Test
    fun `article failed network is retried next run while failed too large is recorded`() = runBlocking {
        val source = NewsSources.ALL[0]
        val netFailUrl = "https://www.tagesschau.de/inland/netzwerk-fehler"
        val tooLargeUrl = "https://www.tagesschau.de/inland/zu-gross"

        val feedXml = rssFeed(
            netFailUrl to (fixedClock - TimeUnit.HOURS.toMillis(1)),
            tooLargeUrl to (fixedClock - TimeUnit.HOURS.toMillis(2))
        )

        val responses = mapOf(
            source.feedUrl to NewsFetch.Ok(feedXml),
            netFailUrl to NewsFetch.Failed(NewsFetch.Reason.NETWORK),
            tooLargeUrl to NewsFetch.Failed(NewsFetch.Reason.TOO_LARGE)
        )
        val fetcher = FakeFetcher(responses)
        val harvester = NewsHarvester(
            fetcher = fetcher,
            store = store,
            sources = listOf(source),
            random = Random(42),
            sleep = recordingSleep,
            clock = { fixedClock }
        )

        val result1 = harvester.harvest(NewsHarvestLimits(articlesLeftToday = 10))
        assertEquals(2, result1.articleRequests)
        assertEquals(2, result1.articlesFailed)
        assertFalse(store.hasArticle(netFailUrl))
        assertTrue(store.hasArticle(tooLargeUrl))

        fetcher.requestedUrls.clear()

        // Second run: netFailUrl is in the queue again, but tooLargeUrl is skipped because it was recorded
        val result2 = harvester.harvest(NewsHarvestLimits(articlesLeftToday = 10))
        assertEquals(1, result2.articleRequests)
        assertEquals(listOf(source.feedUrl, netFailUrl), fetcher.requestedUrls)
    }

    @Test
    fun `feed failed network increments feedsFailed and harvests other source`() = runBlocking {
        val source1 = NewsSources.ALL[0]
        val source2 = NewsSources.ALL[1]
        val validUrl = "https://www.dw.com/de/meldung-ok"

        val feed2Xml = rssFeed(validUrl to (fixedClock - TimeUnit.HOURS.toMillis(1)))
        val responses = mapOf(
            source1.feedUrl to NewsFetch.Failed(NewsFetch.Reason.NETWORK),
            source2.feedUrl to NewsFetch.Ok(feed2Xml),
            validUrl to NewsFetch.Ok(articleHtml("Ein Satz aus der zweiten Quelle nach dem Fehlschlag."))
        )
        val fetcher = FakeFetcher(responses)
        val harvester = NewsHarvester(
            fetcher = fetcher,
            store = store,
            sources = listOf(source1, source2),
            random = Random(42),
            sleep = recordingSleep,
            clock = { fixedClock }
        )

        val result = harvester.harvest(NewsHarvestLimits(articlesLeftToday = 10))

        assertEquals(1, result.feedsFailed)
        assertEquals(1, result.feedsFetched)
        assertEquals(1, result.articleRequests)
        assertEquals(1, result.articlesFetched)
        assertEquals(1, result.newSentences.size)
    }

    @Test
    fun `sentence already in store is not in newSentences and not duplicated`() = runBlocking {
        val source = NewsSources.ALL[0]
        val existingSentenceText = "Dies ist ein bereits vorhandener Beispielsatz im Speicher."
        val newSentenceText = "Hier ist ein völlig neuer Beispielsatz aus der aktuellen Meldung."

        val existingSentence = RecentSentence(
            id = RecentSentencesCodec.textId(existingSentenceText),
            text = existingSentenceText,
            source = source.id,
            url = "https://www.tagesschau.de/alt",
            publishedAt = fixedClock - TimeUnit.DAYS.toMillis(5),
            fetchedAt = fixedClock - TimeUnit.DAYS.toMillis(5)
        )
        store.addAll(listOf(existingSentence))
        assertEquals(1, store.all().size)

        val articleUrl = "https://www.tagesschau.de/inland/meldung-neu"
        val feedXml = rssFeed(articleUrl to (fixedClock - TimeUnit.HOURS.toMillis(1)))
        val responses = mapOf(
            source.feedUrl to NewsFetch.Ok(feedXml),
            articleUrl to NewsFetch.Ok(articleHtml(existingSentenceText, newSentenceText))
        )
        val fetcher = FakeFetcher(responses)
        val harvester = NewsHarvester(
            fetcher = fetcher,
            store = store,
            sources = listOf(source),
            random = Random(42),
            sleep = recordingSleep,
            clock = { fixedClock }
        )

        val result = harvester.harvest(NewsHarvestLimits(articlesLeftToday = 10))

        assertEquals(1, result.newSentences.size)
        assertEquals(newSentenceText, result.newSentences.first().text)

        val stored = store.all()
        assertEquals(2, stored.size)
        assertEquals(1, stored.count { it.text == existingSentenceText })
        assertEquals(1, stored.count { it.text == newSentenceText })
    }

    @Test(expected = CancellationException::class)
    fun `cancellation exception propagates out of harvest`() {
        runBlocking {
            val source = NewsSources.ALL[0]
            val fetcher = object : NewsFetcher {
                override fun fetch(url: String): NewsFetch {
                    throw CancellationException("Cancelled")
                }
            }
            val harvester = NewsHarvester(
                fetcher = fetcher,
                store = store,
                sources = listOf(source),
                clock = { fixedClock }
            )
            harvester.harvest(NewsHarvestLimits(articlesLeftToday = 10))
        }
    }
}
