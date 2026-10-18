package app.olauncher.ui.today.cards

import android.view.View
import app.olauncher.R
import app.olauncher.databinding.CardNextUpBinding
import app.olauncher.glance.CalendarEvent
import app.olauncher.ui.today.TodayCards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class NextUpCardTest {

    @Test
    fun `next up card is registered in TodayCards all`() {
        val card = TodayCards.all.firstOrNull { it.key == NextUpCard.KEY }
        assertNotNull(card)
        assertEquals("next_up", card?.key)
        assertEquals(R.string.next_up, card?.titleRes)
        assertEquals(false, card?.defaultEnabled)
    }

    @Test
    fun `NextUpCard singleton holds no per-view state`() {
        val perViewTypes = setOf(
            CardNextUpBinding::class.java,
        )
        val leaked = NextUpCard::class.java.declaredFields.filter { field ->
            perViewTypes.any { it.isAssignableFrom(field.type) } || View::class.java.isAssignableFrom(field.type)
        }
        assertTrue("per-view fields on NextUpCard: ${leaked.map { it.name }}", leaked.isEmpty())
    }

    @Test
    fun `CalendarEvent data class and convenience properties`() {
        val startInstant = Instant.ofEpochMilli(1000000L)
        val endInstant = Instant.ofEpochMilli(2000000L)

        val event1 = CalendarEvent(
            id = 42L,
            title = "Design Sync",
            start = startInstant,
            end = endInstant,
            allDay = false,
        )

        assertEquals(42L, event1.id)
        assertEquals("Design Sync", event1.title)
        assertEquals(1000000L, event1.startMillis)
        assertEquals(2000000L, event1.endMillis)
        assertEquals(startInstant, event1.start)
        assertEquals(endInstant, event1.end)
        assertFalse(event1.allDay)

        val event2 = CalendarEvent(
            id = 43L,
            title = "All Day Workshop",
            startMillis = 3000000L,
            endMillis = 4000000L,
            allDay = true,
        )
        assertEquals(3000000L, event2.startMillis)
        assertEquals(4000000L, event2.endMillis)
        assertTrue(event2.allDay)
    }
}
