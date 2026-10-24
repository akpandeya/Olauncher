package app.olauncher.ui.today

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import app.olauncher.ui.today.cards.HeldCard
import app.olauncher.ui.today.cards.WeatherCard
import app.olauncher.ui.today.cards.WordsCard

class TodayRaisedSurfacesTest {

    @Test
    fun `today_panel drawable contains shadow, highlight and readableCard surface`() {
        val file = File("src/main/res/drawable/today_panel.xml")
        assertTrue("today_panel.xml must exist", file.exists())
        val xml = file.readText()
        assertTrue("Must be a layer-list", xml.contains("<layer-list"))
        assertTrue("Must contain soft shadow", xml.contains("@color/todayPanelShadow"))
        assertTrue("Must contain top edge highlight", xml.contains("@color/todayPanelHighlight"))
        assertTrue("Must contain readableCard", xml.contains("@color/readableCard"))
        assertTrue("Must use radiusMedium", xml.contains("@dimen/radiusMedium"))
    }

    @Test
    fun `today_panel_held drawable maintains raised panel and adds held tint`() {
        val file = File("src/main/res/drawable/today_panel_held.xml")
        assertTrue("today_panel_held.xml must exist", file.exists())
        val xml = file.readText()
        assertTrue("Must be a layer-list", xml.contains("<layer-list"))
        assertTrue("Must contain soft shadow", xml.contains("@color/todayPanelShadow"))
        assertTrue("Must contain top edge highlight", xml.contains("@color/todayPanelHighlight"))
        assertTrue("Must contain readableCard", xml.contains("@color/readableCard"))
        assertTrue("Must contain heldTint", xml.contains("@color/heldTint"))
        assertTrue("Must use radiusMedium", xml.contains("@dimen/radiusMedium"))
    }

    @Test
    fun `today_tile drawable represents raised tile tier with ripple`() {
        val file = File("src/main/res/drawable/today_tile.xml")
        assertTrue("today_tile.xml must exist", file.exists())
        val xml = file.readText()
        assertTrue("Must be a ripple", xml.contains("<ripple"))
        assertTrue("Must contain tile surface", xml.contains("@color/todayTile"))
        assertTrue("Must contain tile top edge highlight", xml.contains("@color/todayTileHighlight"))
        assertTrue("Must contain tile soft shadow", xml.contains("@color/todayTileShadow"))
        assertTrue("Must use radiusSmall", xml.contains("@dimen/radiusSmall"))
    }

    @Test
    fun `colors xml defines all raised surface tokens in light and dark`() {
        val requiredTokens = listOf(
            "todayPanelHighlight",
            "todayPanelShadow",
            "todayPanelShadowSoft",
            "todayTile",
            "todayTileHighlight",
            "todayTileShadow",
            "todayTileShadowSoft",
            "todayTileStroke",
        )
        for (dir in listOf("values", "values-night")) {
            val xml = File("src/main/res/$dir/colors.xml").readText()
            for (token in requiredTokens) {
                assertTrue("Missing $token in $dir/colors.xml", xml.contains("name=\"$token\""))
            }
        }
    }

    @Test
    fun `every today card uses raised panel and only held is tinted`() {
        assertTrue("HeldCard must be tinted", HeldCard.tinted)
        assertFalse("WordsCard must not be tinted", WordsCard.tinted)
        assertFalse("WeatherCard must not be tinted", WeatherCard.tinted)
        val tintedCards = TodayCards.all.filter { it.tinted }
        assertTrue("Only one card should be tinted", tintedCards.size == 1)
        assertTrue("The only tinted card must be HeldCard", tintedCards.first() == HeldCard)
    }
}
