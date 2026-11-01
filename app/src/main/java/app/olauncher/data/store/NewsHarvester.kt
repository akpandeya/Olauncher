package app.olauncher.data.store

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

data class NewsHarvestLimits(
    val articlesLeftToday: Int,
    val maxArticlesPerRun: Int = 10,
    val minJitterMs: Long = 3_000,
    val maxJitterMs: Long = 10_000
)

data class NewsHarvestResult(
    val feedsFetched: Int,
    val feedsFailed: Int,
    val articleRequests: Int,
    val articlesFetched: Int,
    val articlesFailed: Int,
    val newSentences: List<RecentSentence>
)

/**
 * Harvests German news articles from allowlisted sources into [RecentSentencesStore].
 *
 * Algorithm:
 * 1. Prune expired entries from the store using the current clock.
 * 2. Calculate remaining budget; return an empty result if budget <= 0.
 * 3. Pace requests by sleeping with random jitter before each network call after the first.
 * 4. Shuffle sources once before any jitter draw, fetch feeds, parse recent items,
 *    canonicalize URLs, enforce source hosts, filter known articles, and build queues.
 * 5. Pick articles round-robin across source queues up to the run budget.
 * 6. Fetch picked articles with pacing, extract sentences, record articles, and update store.
 * 7. Return aggregate result counts and added sentences without throwing on network/parse errors.
 *
 * [onArticleRequest] runs once per article request, just before it is sent, so a caller can
 * count requests against its daily cap even when the run is cancelled part-way.
 */
class NewsHarvester(
    private val fetcher: NewsFetcher,
    private val store: RecentSentencesStore,
    private val sources: List<NewsSource> = NewsSources.ALL,
    private val random: kotlin.random.Random = kotlin.random.Random.Default,
    private val sleep: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    suspend fun harvest(
        limits: NewsHarvestLimits,
        onArticleRequest: () -> Unit = {},
    ): NewsHarvestResult {
        val now = clock()
        store.prune(now)

        val budget = min(limits.maxArticlesPerRun, limits.articlesLeftToday)
        if (budget <= 0) {
            return NewsHarvestResult(
                feedsFetched = 0,
                feedsFailed = 0,
                articleRequests = 0,
                articlesFetched = 0,
                articlesFailed = 0,
                newSentences = emptyList()
            )
        }

        var isFirstRequest = true
        suspend fun pace() {
            if (!isFirstRequest) {
                val minJitter = limits.minJitterMs
                val maxJitter = maxOf(minJitter, limits.maxJitterMs)
                val delayMs = random.nextLong(minJitter, maxJitter + 1)
                sleep(delayMs)
            } else {
                isFirstRequest = false
            }
        }

        val order = sources.shuffled(random)

        var feedsFetched = 0
        var feedsFailed = 0
        val sourceQueues = mutableMapOf<String, ArrayDeque<QueuedArticle>>()
        // One snapshot instead of a full store read per feed item.
        val knownArticles = store.articleUrls()

        for (source in order) {
            pace()
            val fetchResult = try {
                withContext(Dispatchers.IO) {
                    fetcher.fetch(source.feedUrl)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                NewsFetch.Failed(NewsFetch.Reason.NETWORK)
            }

            when (fetchResult) {
                is NewsFetch.Failed -> {
                    feedsFailed++
                }
                is NewsFetch.Ok -> {
                    feedsFetched++
                    val queue = ArrayDeque<QueuedArticle>()
                    val seenUrls = mutableSetOf<String>()
                    try {
                        val parsed = NewsFeedParser.parse(fetchResult.body)
                        val recent = NewsFeedParser.recent(parsed, now)
                        for (item in recent) {
                            val canonical = NewsSources.canonicalArticleUrl(item.url) ?: continue
                            if (NewsSources.sourceForUrl(canonical)?.id != source.id) continue
                            if (canonical in knownArticles) continue
                            if (seenUrls.add(canonical)) {
                                queue.add(
                                    QueuedArticle(
                                        source = source,
                                        url = canonical,
                                        publishedAt = item.publishedAt
                                    )
                                )
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // ignore parse errors
                    }
                    sourceQueues[source.id] = queue
                }
            }
        }

        val picks = mutableListOf<QueuedArticle>()
        while (picks.size < budget) {
            var anyAdded = false
            for (source in order) {
                if (picks.size >= budget) break
                val queue = sourceQueues[source.id] ?: continue
                if (queue.isNotEmpty()) {
                    picks.add(queue.removeFirst())
                    anyAdded = true
                }
            }
            if (!anyAdded) break
        }

        var articleRequests = 0
        var articlesFetched = 0
        var articlesFailed = 0
        val newSentences = mutableListOf<RecentSentence>()

        for (pick in picks) {
            pace()
            articleRequests++
            onArticleRequest()
            val articleFetch = try {
                withContext(Dispatchers.IO) {
                    fetcher.fetch(pick.url)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                NewsFetch.Failed(NewsFetch.Reason.NETWORK)
            }

            when (articleFetch) {
                is NewsFetch.Ok -> {
                    val texts = try {
                        NewsArticleText.sentences(articleFetch.body)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        emptyList()
                    }
                    val sentences = texts.map { t ->
                        RecentSentence(
                            id = RecentSentencesCodec.textId(t),
                            text = t,
                            source = pick.source.id,
                            url = pick.url,
                            publishedAt = pick.publishedAt,
                            fetchedAt = now
                        )
                    }
                    val added = store.addAll(sentences)
                    newSentences.addAll(added)
                    store.recordArticles(listOf(pick.url), now)
                    articlesFetched++
                }
                is NewsFetch.Failed -> {
                    articlesFailed++
                    when (articleFetch.reason) {
                        NewsFetch.Reason.NETWORK,
                        NewsFetch.Reason.HTTP_ERROR -> {
                            // do NOT record (a later run may retry)
                        }
                        NewsFetch.Reason.NOT_ALLOWED,
                        NewsFetch.Reason.REDIRECT_NOT_ALLOWED,
                        NewsFetch.Reason.TOO_LARGE -> {
                            store.recordArticles(listOf(pick.url), now)
                        }
                    }
                }
            }
        }

        return NewsHarvestResult(
            feedsFetched = feedsFetched,
            feedsFailed = feedsFailed,
            articleRequests = articleRequests,
            articlesFetched = articlesFetched,
            articlesFailed = articlesFailed,
            newSentences = newSentences
        )
    }

    private data class QueuedArticle(
        val source: NewsSource,
        val url: String,
        val publishedAt: Long
    )
}
