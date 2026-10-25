package app.olauncher.res

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class WellbeingWeekConsistencyTest {

    private fun resolveFile(relative: String): File {
        return listOf(
            File(relative),
            File("app/$relative"),
        ).firstOrNull { it.isFile } ?: error("File $relative not found from ${File(".").absolutePath}")
    }

    private fun parseXml(file: File): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        return factory.newDocumentBuilder().parse(file)
    }

    private fun forEachElement(node: Node, action: (Element) -> Unit) {
        if (node.nodeType == Node.ELEMENT_NODE) {
            action(node as Element)
        }
        val children = node.childNodes
        for (i in 0 until children.length) {
            forEachElement(children.item(i), action)
        }
    }

    private fun findElementById(doc: Document, id: String): Element? {
        val cleanId = id.removePrefix("@+id/").removePrefix("@id/")
        var found: Element? = null
        forEachElement(doc.documentElement) { el ->
            val elId = el.getAttribute("android:id").removePrefix("@+id/").removePrefix("@id/")
            if (elId == cleanId) {
                found = el
            }
        }
        return found
    }

    @Test
    fun `both range pills use TodayChip and today_chip_selectable and set no android textColor`() {
        val file = resolveFile("src/main/res/layout/card_wellbeing.xml")
        val doc = parseXml(file)

        for (pillId in listOf("tvRangeToday", "tvRangeWeek")) {
            val pill = findElementById(doc, pillId)
            assertNotNull("card_wellbeing.xml must contain $pillId", pill)
            assertEquals(
                "$pillId must use @style/TodayChip",
                "@style/TodayChip",
                pill!!.getAttribute("style"),
            )
            assertEquals(
                "$pillId must use @drawable/today_chip_selectable background",
                "@drawable/today_chip_selectable",
                pill.getAttribute("android:background"),
            )
            assertFalse(
                "$pillId must not specify android:textColor so it inherits @drawable/text_colors_chip",
                pill.hasAttribute("android:textColor"),
            )
        }
    }

    @Test
    fun `item_week_app xml uses only TodayCard styles and no raw sp`() {
        val file = resolveFile("src/main/res/layout/item_week_app.xml")
        val doc = parseXml(file)

        val rawSpRegex = Regex("^[0-9]+(\\.[0-9]+)?sp$")
        var elementCount = 0

        forEachElement(doc.documentElement) { element ->
            elementCount++
            val style = element.getAttribute("style")
            if (style.isNotEmpty()) {
                assertTrue(
                    "item_week_app.xml element style '$style' must start with @style/TodayCard",
                    style.startsWith("@style/TodayCard"),
                )
            }

            val textSize = element.getAttribute("android:textSize")
            if (textSize.isNotEmpty()) {
                assertFalse(
                    "item_week_app.xml must have no raw sp textSize, found: $textSize",
                    rawSpRegex.matches(textSize),
                )
                assertTrue(
                    "item_week_app.xml textSize '$textSize' must reference a dimen",
                    textSize.startsWith("@dimen/"),
                )
            }
        }
        assertTrue("item_week_app.xml must have elements", elementCount > 0)

        val appName = findElementById(doc, "tvAppName")
        assertNotNull("tvAppName must exist in item_week_app.xml", appName)
        assertEquals("@style/TodayCardBody", appName!!.getAttribute("style"))

        val appTime = findElementById(doc, "tvAppTime")
        assertNotNull("tvAppTime must exist in item_week_app.xml", appTime)
        assertEquals("@style/TodayCardBodyLight", appTime!!.getAttribute("style"))
        assertEquals("tnum", appTime.getAttribute("android:fontFeatureSettings"))
    }

    @Test
    fun `every tile in card_wellbeing xml and card_week xml uses today_tile drawable`() {
        val wellbeingTiles = listOf("tileWater", "tileWalk", "tileNotNow", "tileExercise", "tileRestingHr", "tileSleep")
        val wellbeingDoc = parseXml(resolveFile("src/main/res/layout/card_wellbeing.xml"))
        for (tileId in wellbeingTiles) {
            val tile = findElementById(wellbeingDoc, tileId)
            assertNotNull("card_wellbeing.xml must contain $tileId", tile)
            assertEquals(
                "$tileId in card_wellbeing.xml must use @drawable/today_tile",
                "@drawable/today_tile",
                tile!!.getAttribute("android:background"),
            )
        }

        val weekTiles = listOf("tileWeekNotNow", "tileWeekWaterWalk", "tileWeekExercise", "tileWeekRestingHr", "tileWeekSleep")
        val weekDoc = parseXml(resolveFile("src/main/res/layout/card_week.xml"))
        for (tileId in weekTiles) {
            val tile = findElementById(weekDoc, tileId)
            assertNotNull("card_week.xml must contain $tileId", tile)
            assertEquals(
                "$tileId in card_week.xml must use @drawable/today_tile",
                "@drawable/today_tile",
                tile!!.getAttribute("android:background"),
            )
        }
    }

    @Test
    fun `today_day_bars_height exists in dp`() {
        val dimensFile = resolveFile("src/main/res/values/dimens.xml")
        val doc = parseXml(dimensFile)
        val nodes = doc.getElementsByTagName("dimen")
        var value: String? = null
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            if (el.getAttribute("name") == "today_day_bars_height") {
                value = el.textContent.trim()
                break
            }
        }
        assertNotNull("dimens.xml must define today_day_bars_height", value)
        assertTrue("today_day_bars_height must end with dp, was $value", value!!.endsWith("dp"))
        val dpNumber = value.removeSuffix("dp").toFloat()
        assertEquals(112f, dpNumber, 0.001f)
    }

    @Test
    fun `DayBarsView kt source references today_label_size and today_day_bars_height and no longer contains LABEL_SP`() {
        val sourceFile = resolveFile("src/main/java/app/olauncher/ui/today/cards/DayBarsView.kt")
        val source = sourceFile.readText()

        assertTrue(
            "DayBarsView.kt must reference R.dimen.today_label_size",
            source.contains("R.dimen.today_label_size"),
        )
        assertTrue(
            "DayBarsView.kt must reference R.dimen.today_day_bars_height",
            source.contains("R.dimen.today_day_bars_height"),
        )
        assertFalse(
            "DayBarsView.kt must no longer contain LABEL_SP",
            source.contains("LABEL_SP"),
        )
        assertFalse(
            "DayBarsView.kt must no longer contain HEIGHT_DP",
            source.contains("HEIGHT_DP"),
        )
    }

    @Test
    fun `WellbeingCard kt attaches press scale to range switch pills`() {
        val sourceFile = resolveFile("src/main/java/app/olauncher/ui/today/cards/WellbeingCard.kt")
        val source = sourceFile.readText()

        assertTrue(
            "WellbeingCard.kt must attach press scale to tvRangeToday",
            source.contains("binding.tvRangeToday.attachPressScale()"),
        )
        assertTrue(
            "WellbeingCard.kt must attach press scale to tvRangeWeek",
            source.contains("binding.tvRangeWeek.attachPressScale()"),
        )
    }

    @Test
    fun `all five 7 days tiles share one structure label then one-line value then subline`() {
        val doc = parseXml(resolveFile("src/main/res/layout/card_week.xml"))
        val tiles = listOf("tileWeekNotNow", "tileWeekWaterWalk", "tileWeekExercise", "tileWeekRestingHr", "tileWeekSleep")
        val minHeights = mutableSetOf<String>()
        for (tileId in tiles) {
            val tile = findElementById(doc, tileId)
            assertNotNull("card_week.xml must contain $tileId", tile)
            minHeights += tile!!.getAttribute("android:minHeight")
            assertEquals("$tileId padding", "12dp", tile.getAttribute("android:padding"))
            val texts = mutableListOf<Element>()
            val children = tile.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child is Element && child.tagName == "TextView") texts += child
            }
            assertEquals("$tileId must hold label, value and subline", 3, texts.size)
            assertEquals("$tileId label style", "@style/TodayCardLabel", texts[0].getAttribute("style"))
            assertEquals("$tileId value style", "@style/TodayCardStat", texts[1].getAttribute("style"))
            assertEquals("$tileId value must stay on one line", "1", texts[1].getAttribute("android:maxLines"))
            assertEquals("$tileId value shrinks to fit", "uniform", texts[1].getAttribute("android:autoSizeTextType"))
            assertEquals("$tileId subline style", "@style/TodayCardBodyLight", texts[2].getAttribute("style"))
        }
        assertEquals("all 7 days tiles must share one minHeight", setOf("@dimen/today_tile_min_height"), minHeights)
    }

    @Test
    fun `every week tile id in card_week xml is referenced from Kotlin`() {
        val kotlin = listOf(
            "src/main/java/app/olauncher/ui/today/cards/WeekCard.kt",
            "src/main/java/app/olauncher/ui/today/cards/WellbeingCard.kt",
        ).joinToString("\n") { resolveFile(it).readText() }
        val ids = listOf(
            "tvWeekNotNow", "tvWeekNotNowSub", "tvWeekWaterWalk",
            "llWeekHealth", "tileWeekExercise", "tileWeekRestingHr", "tileWeekSleep",
            "tvWeekExercise", "tvWeekExerciseSub", "tvWeekRestingHr", "tvWeekSleep", "tvWeekSleepSub",
        )
        for (id in ids) {
            assertTrue("$id must be used in Kotlin", kotlin.contains(".$id"))
        }
    }
}
