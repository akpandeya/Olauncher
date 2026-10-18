package app.olauncher.ui.today

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import app.olauncher.data.Prefs
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

import app.olauncher.R
import app.olauncher.ui.today.cards.SavedCard

class TodayCardTest {

    @Test
    fun `today cards list contains saved card`() {
        val saved = TodayCards.all.firstOrNull { it.key == SavedCard.KEY }
        assertNotNull(saved)
        assertEquals("saved", saved?.key)
        assertEquals(R.string.saved_for_later, saved?.titleRes)
    }

    @Test
    fun `today cards list contains wellbeing card`() {
        val wellbeing = TodayCards.all.firstOrNull { it.key == app.olauncher.ui.today.cards.WellbeingCard.KEY }
        assertNotNull(wellbeing)
        assertEquals("wellbeing", wellbeing?.key)
        assertEquals(R.string.wellbeing, wellbeing?.titleRes)
    }

    @Test
    fun `today cards list contains words card`() {
        val words = TodayCards.all.firstOrNull { it.key == app.olauncher.ui.today.cards.WordsCard.KEY }
        assertNotNull(words)
        assertEquals("words", words?.key)
        assertEquals(R.string.words, words?.titleRes)
    }


    @Test
    fun `swipe right action constants match spec`() {
        assertEquals("SWIPE_RIGHT_ACTION", Prefs.SWIPE_RIGHT_ACTION)
        assertEquals(0, Prefs.SWIPE_RIGHT_ACTION_TODAY)
        assertEquals(1, Prefs.SWIPE_RIGHT_ACTION_APP)
    }

    @Test
    fun `today card pref prefix matches spec`() {
        assertEquals("TODAY_CARD_", Prefs.TODAY_CARD_PREFIX)
    }

    @Test
    fun `today card contract defaults`() {
        var resumed = false
        val card = object : TodayCard {
            override val key: String = "test_card"
            override val titleRes: Int = 0
            override fun createView(inflater: LayoutInflater, parent: ViewGroup, fragment: Fragment): View {
                throw UnsupportedOperationException()
            }
            override fun onResume() {
                resumed = true
            }
        }

        assertEquals("test_card", card.key)
        assertEquals(0, card.titleRes)
        card.onResume()
        assertTrue(resumed)
    }

    @Test
    fun `org json dependency is available on classpath`() {
        val json = JSONObject().apply {
            put("key", "test")
            put("list", JSONArray().put("item1"))
        }
        assertEquals("test", json.getString("key"))
        assertEquals("item1", json.getJSONArray("list").getString(0))
        assertNotNull(json.toString())
    }
}
