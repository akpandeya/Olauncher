package app.olauncher.ui.today.cards

import app.olauncher.R
import app.olauncher.quiet.HeldItem
import app.olauncher.quiet.HeldStore
import app.olauncher.ui.today.TodayCards
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

class HeldCardTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storeDir: File
    private lateinit var store: HeldStore

    @Before
    fun setUp() {
        storeDir = tempFolder.newFolder("held_card_test")
        store = HeldStore(storeDir)
        HeldCard.storeProvider = { store }
    }

    @After
    fun tearDown() {
        HeldCard.storeProvider = null
        HeldCard.appOpener = null
    }

    @Test
    fun `held card is registered in TodayCards all`() {
        val card = TodayCards.all.firstOrNull { it.key == HeldCard.KEY }
        assertNotNull(card)
        assertEquals("held", card?.key)
        assertEquals(R.string.held, card?.titleRes)
        assertEquals(true, card?.defaultEnabled)
    }

    @Test
    fun `store clear removes only target package items`() {
        store.add(HeldItem("k1", "com.instagram.android", "Instagram", "Title 1", "Text 1", 1000L))
        store.add(HeldItem("k2", "com.instagram.android", "Instagram", "Title 2", "Text 2", 2000L))
        store.add(HeldItem("k3", "com.google.android.youtube", "YouTube", "Title 3", "Text 3", 3000L))

        assertEquals(3, store.all().size)

        store.clear("com.instagram.android")

        val remaining = store.all()
        assertEquals(1, remaining.size)
        assertEquals("com.google.android.youtube", remaining[0].pkg)
    }

    @Test
    fun `store clearAll clears all held items`() {
        store.add(HeldItem("k1", "com.instagram.android", "Instagram", "Title 1", "Text 1", 1000L))
        store.add(HeldItem("k2", "com.google.android.youtube", "YouTube", "Title 2", "Text 2", 2000L))

        assertEquals(2, store.all().size)

        store.clearAll()

        assertTrue(store.all().isEmpty())
    }

    @Test
    fun `store prunes items older than 48 hours`() {
        val now = Instant.parse("2026-10-19T12:00:00Z").toEpochMilli()
        val fortyEightHoursMs = 48L * 60 * 60 * 1000
        val cutoff = now - fortyEightHoursMs

        store.add(HeldItem("old", "com.old", "Old", "Old", "Old", cutoff - 1000L))
        store.add(HeldItem("fresh", "com.fresh", "Fresh", "Fresh", "Fresh", cutoff + 1000L))

        store.pruneOlderThan(cutoff)

        val items = store.all()
        assertEquals(1, items.size)
        assertEquals("com.fresh", items[0].pkg)
    }
}
