package app.olauncher.ui.today

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import app.olauncher.ui.today.cards.HeldCard
import app.olauncher.ui.today.cards.WeatherCard
import app.olauncher.ui.today.cards.WordsCard

class TodayRaisedSurfacesTest {

    /** Root tag and every attribute value in the XML, read from the parsed tree so comments never count. */
    private class ParsedXml(val rootTag: String, val values: Set<String>)

    private fun parse(file: File): ParsedXml {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val values = mutableSetOf<String>()
        fun walk(node: Node) {
            if (node.nodeType == Node.ELEMENT_NODE) {
                val attrs = (node as Element).attributes
                for (i in 0 until attrs.length) values.add(attrs.item(i).nodeValue)
            }
            val kids = node.childNodes
            for (i in 0 until kids.length) walk(kids.item(i))
        }
        walk(doc.documentElement)
        return ParsedXml(doc.documentElement.tagName, values)
    }

    @Test
    fun `today_panel drawable contains shadow, highlight and readableCard surface`() {
        val file = File("src/main/res/drawable/today_panel.xml")
        assertTrue("today_panel.xml must exist", file.exists())
        val xml = parse(file)
        assertTrue("Must be a layer-list", xml.rootTag == "layer-list")
        assertTrue("Must contain soft shadow", xml.values.contains("@color/todayPanelShadow"))
        assertTrue("Must contain top edge highlight", xml.values.contains("@color/todayPanelHighlight"))
        assertTrue("Must contain readableCard", xml.values.contains("@color/readableCard"))
        assertTrue("Must use radiusMedium", xml.values.contains("@dimen/radiusMedium"))
    }

    @Test
    fun `today_panel_held drawable maintains raised panel and adds held tint`() {
        val file = File("src/main/res/drawable/today_panel_held.xml")
        assertTrue("today_panel_held.xml must exist", file.exists())
        val xml = parse(file)
        assertTrue("Must be a layer-list", xml.rootTag == "layer-list")
        assertTrue("Must contain soft shadow", xml.values.contains("@color/todayPanelShadow"))
        assertTrue("Must contain top edge highlight", xml.values.contains("@color/todayPanelHighlight"))
        assertTrue("Must contain readableCard", xml.values.contains("@color/readableCard"))
        assertTrue("Must contain heldTint", xml.values.contains("@color/heldTint"))
        assertTrue("Must use radiusMedium", xml.values.contains("@dimen/radiusMedium"))
    }

    @Test
    fun `today_tile drawable represents raised tile tier with ripple`() {
        val file = File("src/main/res/drawable/today_tile.xml")
        assertTrue("today_tile.xml must exist", file.exists())
        val xml = parse(file)
        assertTrue("Must be a ripple", xml.rootTag == "ripple")
        assertTrue("Must contain tile surface", xml.values.contains("@color/todayTile"))
        assertTrue("Must contain tile top edge highlight", xml.values.contains("@color/todayTileHighlight"))
        assertTrue("Must contain tile soft shadow", xml.values.contains("@color/todayTileShadow"))
        assertTrue("Must use radiusSmall", xml.values.contains("@dimen/radiusSmall"))
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
            val xml = parse(File("src/main/res/$dir/colors.xml"))
            for (token in requiredTokens) {
                assertTrue("Missing $token in $dir/colors.xml", xml.values.contains(token))
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
