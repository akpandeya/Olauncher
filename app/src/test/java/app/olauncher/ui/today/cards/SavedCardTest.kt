package app.olauncher.ui.today.cards

import app.olauncher.R
import app.olauncher.data.store.SavedItem
import app.olauncher.data.store.SavedItemsStore
import app.olauncher.ui.today.TodayCards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class SavedCardTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val zoneUtc: ZoneId = ZoneId.of("UTC")
    private val fixedNow: Long = Instant.parse("2026-10-18T12:00:00Z").toEpochMilli()

    @Test
    fun `saved card is registered in TodayCards all`() {
        val card = TodayCards.all.firstOrNull { it.key == SavedCard.KEY }
        assertNotNull(card)
        assertEquals("saved", card?.key)
        assertEquals(R.string.saved_for_later, card?.titleRes)
    }

    @Test
    fun `formatHost strips www prefix and extracts host correctly`() {
        assertEquals("news.ycombinator.com", SavedCardLogic.formatHost("https://news.ycombinator.com/item?id=123"))
        assertEquals("theverge.com", SavedCardLogic.formatHost("https://www.theverge.com/tech/2026/foo"))
        assertEquals("sub.domain.org", SavedCardLogic.formatHost("http://sub.domain.org/path"))
        assertEquals("example.co.uk", SavedCardLogic.formatHost("https://www.example.co.uk/test?q=1"))
        assertEquals("localhost", SavedCardLogic.formatHost("http://localhost:8080/page"))
        assertEquals("raw-text", SavedCardLogic.formatHost("raw-text"))
    }

    @Test
    fun `formatRelativeDay formats same day as today and past days with suffix d`() {
        val sameDay = Instant.parse("2026-10-18T08:00:00Z").toEpochMilli()
        assertEquals("today", SavedCardLogic.formatRelativeDay(sameDay, fixedNow, zoneUtc))

        val oneDayAgo = Instant.parse("2026-10-17T12:00:00Z").toEpochMilli()
        assertEquals("1 d", SavedCardLogic.formatRelativeDay(oneDayAgo, fixedNow, zoneUtc))

        val threeDaysAgo = Instant.parse("2026-10-15T12:00:00Z").toEpochMilli()
        assertEquals("3 d", SavedCardLogic.formatRelativeDay(threeDaysAgo, fixedNow, zoneUtc))

        val tenDaysAgo = Instant.parse("2026-10-08T12:00:00Z").toEpochMilli()
        assertEquals("10 d", SavedCardLogic.formatRelativeDay(tenDaysAgo, fixedNow, zoneUtc))

        val future = Instant.parse("2026-10-19T12:00:00Z").toEpochMilli()
        assertEquals("today", SavedCardLogic.formatRelativeDay(future, fixedNow, zoneUtc))
    }

    @Test
    fun `formatSubtitle formats host and relative day`() {
        val sameDay = fixedNow
        assertEquals(
            "news.ycombinator.com · today",
            SavedCardLogic.formatSubtitle("https://news.ycombinator.com/item?id=123", sameDay, fixedNow, zoneUtc)
        )

        val threeDaysAgo = Instant.parse("2026-10-15T12:00:00Z").toEpochMilli()
        assertEquals(
            "theverge.com · 3 d",
            SavedCardLogic.formatSubtitle("https://www.theverge.com/tech", threeDaysAgo, fixedNow, zoneUtc)
        )
    }

    @Test
    fun `sortActiveItems puts newest nearest the bottom`() {
        val item1 = SavedItem(id = 1, url = "https://a.com", title = "A", savedAt = 1000L)
        val item2 = SavedItem(id = 2, url = "https://b.com", title = "B", savedAt = 2000L)
        val item3 = SavedItem(id = 3, url = "https://c.com", title = "C", savedAt = 3000L)

        // Pass in unsorted
        val sorted = SavedCardLogic.sortActiveItems(listOf(item2, item3, item1))

        assertEquals(listOf(item1, item2, item3), sorted)
        // Last element is item3 (newest, nearest the bottom)
        assertEquals(item3, sorted.last())
    }

    @Test
    fun `sortArchivedItems orders newest archived item first`() {
        val item1 = SavedItem(id = 1, url = "https://a.com", title = "A", savedAt = 1000L, archivedAt = 5000L)
        val item2 = SavedItem(id = 2, url = "https://b.com", title = "B", savedAt = 2000L, archivedAt = 6000L)

        val sorted = SavedCardLogic.sortArchivedItems(listOf(item1, item2))
        assertEquals(listOf(item2, item1), sorted)
    }

    @Test
    fun `visible active items shows at most 20 when not expanded, with newest at bottom`() {
        val items = (1..25).map { i ->
            SavedItem(
                id = i.toLong(),
                url = "https://example.com/$i",
                title = "Item $i",
                savedAt = 1000L * i
            )
        }

        // Not expanded: should show the 20 newest items (items 6 through 25)
        val collapsed = SavedCardLogic.getVisibleActiveItems(items, isExpanded = false, maxCount = 20)
        assertEquals(20, collapsed.size)
        assertEquals(6L, collapsed.first().id)
        assertEquals(25L, collapsed.last().id)

        // Expanded: should show all 25 items (items 1 through 25)
        val expanded = SavedCardLogic.getVisibleActiveItems(items, isExpanded = true, maxCount = 20)
        assertEquals(25, expanded.size)
        assertEquals(1L, expanded.first().id)
        assertEquals(25L, expanded.last().id)
    }

    @Test
    fun `visible active items returns all items when total is 20 or less`() {
        val items = (1..15).map { i ->
            SavedItem(
                id = i.toLong(),
                url = "https://example.com/$i",
                title = "Item $i",
                savedAt = 1000L * i
            )
        }

        val visible = SavedCardLogic.getVisibleActiveItems(items, isExpanded = false, maxCount = 20)
        assertEquals(15, visible.size)
        assertEquals(1L, visible.first().id)
        assertEquals(15L, visible.last().id)
    }

    @Test
    fun `getMoreCount calculates the hidden older items count`() {
        assertEquals(0, SavedCardLogic.getMoreCount(10, 20))
        assertEquals(0, SavedCardLogic.getMoreCount(20, 20))
        assertEquals(5, SavedCardLogic.getMoreCount(25, 20))
        assertEquals(80, SavedCardLogic.getMoreCount(100, 20))
    }

    @Test
    fun `store integration for active, archive, unarchive and delete`() {
        val store = SavedItemsStore(tempFolder.root)
        assertTrue(store.active().isEmpty())
        assertTrue(store.archived().isEmpty())

        val item1 = store.add("https://kotlinlang.org", "Kotlin")
        val item2 = store.add("https://android.com", "Android")

        assertEquals(2, store.active().size)
        val sortedActive = SavedCardLogic.sortActiveItems(store.active())
        assertEquals(item1.id, sortedActive.first().id)
        assertEquals(item2.id, sortedActive.last().id) // newest nearest bottom

        // Archive item1
        store.archive(item1.id)
        assertEquals(1, store.active().size)
        assertEquals(1, store.archived().size)
        assertEquals(item1.id, store.archived().first().id)

        // Unarchive item1
        store.unarchive(item1.id)
        assertEquals(2, store.active().size)
        assertEquals(0, store.archived().size)

        // Delete item2
        store.delete(item2.id)
        assertEquals(1, store.active().size)
        assertEquals(item1.id, store.active().first().id)
    }
}
