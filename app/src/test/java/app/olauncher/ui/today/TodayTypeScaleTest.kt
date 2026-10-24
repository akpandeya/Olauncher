package app.olauncher.ui.today

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

class TodayTypeScaleTest {

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

    @Test
    fun `styles xml defines TodayCardLabel, TodayCardStat, and TodayCardBody`() {
        val stylesFile = resolveFile("src/main/res/values/styles.xml")
        val doc = parseXml(stylesFile)
        val styleElements = mutableMapOf<String, Element>()

        val nodes = doc.getElementsByTagName("style")
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            styleElements[el.getAttribute("name")] = el
        }

        assertTrue("styles.xml must contain TodayCardLabel", styleElements.containsKey("TodayCardLabel"))
        assertTrue("styles.xml must contain TodayCardStat", styleElements.containsKey("TodayCardStat"))
        assertTrue("styles.xml must contain TodayCardBody", styleElements.containsKey("TodayCardBody"))

        // TodayCardStat must configure tabular figures (tnum)
        val statStyle = styleElements["TodayCardStat"]!!
        var tnumFound = false
        var statSizeAttr: String? = null
        val statItems = statStyle.getElementsByTagName("item")
        for (i in 0 until statItems.length) {
            val item = statItems.item(i) as Element
            if (item.getAttribute("name") == "android:fontFeatureSettings" && item.textContent.trim() == "tnum") {
                tnumFound = true
            }
            if (item.getAttribute("name") == "android:textSize") {
                statSizeAttr = item.textContent.trim()
            }
        }
        assertTrue("TodayCardStat must have fontFeatureSettings set to tnum", tnumFound)
        assertNotNull("TodayCardStat must have textSize", statSizeAttr)
        assertTrue("TodayCardStat textSize must reference a dimen", statSizeAttr!!.startsWith("@dimen/"))

        // TodayCardLabel must configure uppercase and letterSpacing (tracking)
        val labelStyle = styleElements["TodayCardLabel"]!!
        var allCapsFound = false
        var trackingFound = false
        var labelSizeAttr: String? = null
        val labelItems = labelStyle.getElementsByTagName("item")
        for (i in 0 until labelItems.length) {
            val item = labelItems.item(i) as Element
            if (item.getAttribute("name") == "android:textAllCaps" && item.textContent.trim() == "true") {
                allCapsFound = true
            }
            if (item.getAttribute("name") == "android:letterSpacing") {
                trackingFound = true
            }
            if (item.getAttribute("name") == "android:textSize") {
                labelSizeAttr = item.textContent.trim()
            }
        }
        assertTrue("TodayCardLabel must be uppercase (textAllCaps=true)", allCapsFound)
        assertTrue("TodayCardLabel must be tracked (letterSpacing)", trackingFound)
        assertNotNull("TodayCardLabel must have textSize", labelSizeAttr)
        assertTrue("TodayCardLabel textSize must reference a dimen", labelSizeAttr!!.startsWith("@dimen/"))

        // TodayCardBody must specify textSize
        val bodyStyle = styleElements["TodayCardBody"]!!
        var bodySizeAttr: String? = null
        val bodyItems = bodyStyle.getElementsByTagName("item")
        for (i in 0 until bodyItems.length) {
            val item = bodyItems.item(i) as Element
            if (item.getAttribute("name") == "android:textSize") {
                bodySizeAttr = item.textContent.trim()
            }
        }
        assertNotNull("TodayCardBody must have textSize", bodySizeAttr)
        assertTrue("TodayCardBody textSize must reference a dimen", bodySizeAttr!!.startsWith("@dimen/"))
    }

    @Test
    fun `today card type scale dimens are in sp`() {
        val dimensFile = resolveFile("src/main/res/values/dimens.xml")
        val doc = parseXml(dimensFile)
        val nodes = doc.getElementsByTagName("dimen")
        val dimens = mutableMapOf<String, String>()
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            dimens[el.getAttribute("name")] = el.textContent.trim()
        }

        val requiredTokens = listOf("today_label_size", "today_stat_size", "today_body_size")
        for (token in requiredTokens) {
            val value = dimens[token]
            assertNotNull("dimens.xml must define $token", value)
            assertTrue("$token must end with sp, was $value", value!!.endsWith("sp"))
            val spNumber = value.removeSuffix("sp").toFloat()
            assertTrue("$token must be >= 14sp per design rules", spNumber >= 14f)
        }
    }

    @Test
    fun `all eight today card layouts use TodayCard text styles and have no raw sp sizes`() {
        val cardLayouts = listOf(
            "src/main/res/layout/card_wellbeing.xml",
            "src/main/res/layout/card_words.xml",
            "src/main/res/layout/card_weather.xml",
            "src/main/res/layout/card_notes.xml",
            "src/main/res/layout/card_week.xml",
            "src/main/res/layout/card_saved.xml",
            "src/main/res/layout/card_next_up.xml",
            "src/main/res/layout/card_held.xml",
        )

        for (relPath in cardLayouts) {
            val file = resolveFile(relPath)
            val doc = parseXml(file)
            var usesTodayCardStyle = false

            forEachElement(doc.documentElement) { element ->
                val styleAttr = element.getAttribute("style")
                if (styleAttr.contains("TodayCard") || styleAttr.contains("Today")) {
                    usesTodayCardStyle = true
                }

                val textSize = element.getAttribute("android:textSize")
                if (textSize.isNotEmpty()) {
                    assertTrue(
                        "${file.name} textSize '$textSize' must reference a dimen, not raw sp",
                        textSize.startsWith("@dimen/")
                    )
                }
            }

            assertTrue("${file.name} must use TodayCard typography styles", usesTodayCardStyle)
        }
    }

    @Test
    fun `stat number views use TodayCardStat across cards`() {
        val statChecks = listOf(
            Pair("src/main/res/layout/card_wellbeing.xml", "@+id/tvDistractingValue"),
            Pair("src/main/res/layout/card_wellbeing.xml", "@+id/tvWater"),
            Pair("src/main/res/layout/card_wellbeing.xml", "@+id/tvWalk"),
            Pair("src/main/res/layout/card_wellbeing.xml", "@+id/tvNotNow"),
            Pair("src/main/res/layout/card_words.xml", "@+id/tvDue"),
            Pair("src/main/res/layout/card_week.xml", "@+id/tvWeekTotal"),
            Pair("src/main/res/layout/card_week.xml", "@+id/tvWeekNotNow"),
            Pair("src/main/res/layout/card_week.xml", "@+id/tvWeekWaterWalk"),
            Pair("src/main/res/layout/card_weather.xml", "@+id/tvTemp"),
        )

        for ((relPath, viewId) in statChecks) {
            val file = resolveFile(relPath)
            val doc = parseXml(file)
            var matchedStyle: String? = null

            forEachElement(doc.documentElement) { element ->
                val id = element.getAttribute("android:id")
                if (id == viewId || id.endsWith(viewId.removePrefix("@+id/"))) {
                    matchedStyle = element.getAttribute("style")
                }
            }

            assertNotNull("View $viewId must exist in ${file.name}", matchedStyle)
            assertTrue(
                "View $viewId in ${file.name} must use TodayCardStat (was: $matchedStyle)",
                matchedStyle!!.contains("TodayCardStat") || matchedStyle!!.contains("TodayStat")
            )
        }
    }
}
