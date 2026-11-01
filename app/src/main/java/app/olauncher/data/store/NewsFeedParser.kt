package app.olauncher.data.store

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

data class FeedItem(
    val title: String,
    val url: String,
    val publishedAt: Long
)

object NewsFeedParser {

    private data class RootPair(val opener: Regex, val closer: Regex)

    private val ROOT_PAIRS = listOf(
        RootPair(Regex("""<rss\b""", RegexOption.IGNORE_CASE), Regex("""</rss\s*>""", RegexOption.IGNORE_CASE)),
        RootPair(Regex("""<feed\b""", RegexOption.IGNORE_CASE), Regex("""</feed\s*>""", RegexOption.IGNORE_CASE)),
        RootPair(Regex("""<rdf:RDF\b""", RegexOption.IGNORE_CASE), Regex("""</rdf:RDF\s*>""", RegexOption.IGNORE_CASE))
    )

    private val ITEM_REGEX = Regex(
        """<(item|entry)\b[^>]*>(.*?)</\1>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val CDATA_REGEX = Regex("""<!\[CDATA\[(.*?)\]\]>""", RegexOption.DOT_MATCHES_ALL)
    private val LINK_ATTR_TAG_REGEX = Regex("""<link\b([^>]*)/?>""", RegexOption.IGNORE_CASE)
    private val HREF_ATTR_REGEX = Regex("""\bhref\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val REL_ATTR_REGEX = Regex("""\brel\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    fun parse(xml: String): List<FeedItem> {
        // 1. Completeness check first: root opener AND matching closer after it.
        val firstOpener = ROOT_PAIRS
            .mapNotNull { pair ->
                pair.opener.find(xml)?.let { match ->
                    Triple(match.range.first, match.range.last, pair.closer)
                }
            }
            .minByOrNull { it.first } ?: return emptyList()

        firstOpener.third.find(xml, firstOpener.second) ?: return emptyList()

        val items = mutableListOf<FeedItem>()
        val seenUrls = mutableSetOf<String>()

        // 2. Item blocks: every <item>...</item> and <entry>...</entry>
        for (itemMatch in ITEM_REGEX.findAll(xml)) {
            val block = itemMatch.groupValues[2]

            // 3. Child element extraction
            val title = extractTagText(block, "title") ?: ""

            var link = extractTagText(block, "link")?.trim() ?: ""
            if (link.isEmpty()) {
                val atomCandidates = LINK_ATTR_TAG_REGEX.findAll(block).mapNotNull { match ->
                    val attrs = match.groupValues[1]
                    val rawHref = HREF_ATTR_REGEX.find(attrs)?.groupValues?.get(1)?.trim()
                    if (rawHref.isNullOrBlank()) null
                    else {
                        val rel = REL_ATTR_REGEX.find(attrs)?.groupValues?.get(1)?.trim()?.lowercase(Locale.ROOT)
                        Pair(rel, cleanElementText(rawHref))
                    }
                }.toList()
                val preferred = atomCandidates.firstOrNull { it.first == null || it.first == "alternate" }
                    ?: atomCandidates.firstOrNull()
                if (preferred != null) {
                    link = preferred.second
                }
            }
            if (link.isEmpty()) {
                val guid = extractTagText(block, "guid")?.trim() ?: ""
                if (guid.startsWith("https://", ignoreCase = true)) {
                    link = guid
                }
            }

            // 4. Date = first non-blank of <pubDate>, <dc:date>, <published>, <updated>
            var publishedAt: Long? = null
            for (tagName in listOf("pubDate", "dc:date", "published", "updated")) {
                val dateText = extractTagText(block, tagName)
                if (!dateText.isNullOrBlank()) {
                    publishedAt = parseDate(dateText)
                    break
                }
            }

            // 5. Skip an item whose link is blank or whose date is null. Skip duplicates by url.
            if (link.isBlank() || publishedAt == null) {
                continue
            }
            if (!seenUrls.add(link)) {
                continue
            }

            items.add(FeedItem(title = title, url = link, publishedAt = publishedAt))
        }

        return items
    }

    private fun cleanElementText(raw: String): String {
        val text = if (raw.contains("<![CDATA[", ignoreCase = true)) {
            CDATA_REGEX.replace(raw) { it.groupValues[1] }
        } else {
            WiktionaryParser.decodeHtmlEntities(raw)
        }
        return text.trim()
    }

    private fun extractTagText(block: String, name: String): String? {
        val regex = Regex(
            "<" + Regex.escape(name) + "\\b[^>]*>(.*?)</" + Regex.escape(name) + ">",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val match = regex.find(block) ?: return null
        return cleanElementText(match.groupValues[1])
    }

    fun parseDate(raw: String): Long? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        try {
            return ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        } catch (_: Exception) {}

        try {
            return OffsetDateTime.parse(trimmed).toInstant().toEpochMilli()
        } catch (_: Exception) {}

        try {
            return Instant.parse(trimmed).toEpochMilli()
        } catch (_: Exception) {}

        return null
    }

    fun recent(
        items: List<FeedItem>,
        nowMs: Long,
        windowDays: Int = RecentSentencesStore.RETENTION_DAYS
    ): List<FeedItem> {
        val minPublishedAt = nowMs - TimeUnit.DAYS.toMillis(windowDays.toLong())
        val maxPublishedAt = nowMs + TimeUnit.DAYS.toMillis(1)
        return items
            .filter { it.publishedAt in minPublishedAt..maxPublishedAt }
            .sortedByDescending { it.publishedAt }
    }
}
