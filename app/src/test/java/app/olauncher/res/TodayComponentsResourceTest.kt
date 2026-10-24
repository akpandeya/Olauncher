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

class TodayComponentsResourceTest {

    private val rawDpOrSp = Regex("^[0-9]+(\\.[0-9]+)?(dp|sp)$")

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

    private fun parseStyles(file: File): Map<String, Element> {
        val doc = parseXml(file)
        val styles = mutableMapOf<String, Element>()
        val nodes = doc.getElementsByTagName("style")
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            styles[el.getAttribute("name")] = el
        }
        return styles
    }

    private fun styleItems(style: Element): Map<String, String> {
        val items = mutableMapOf<String, String>()
        val nodes = style.getElementsByTagName("item")
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            items[el.getAttribute("name")] = el.textContent.trim()
        }
        return items
    }

    private fun collectValues(node: Node, sink: MutableSet<String>) {
        if (node.nodeType == Node.ELEMENT_NODE) {
            val element = node as Element
            val attrs = element.attributes
            for (i in 0 until attrs.length) {
                sink.add(attrs.item(i).nodeValue)
            }
        }
        val text = node.textContent?.trim().orEmpty()
        if (text.isNotEmpty()) {
            sink.add(text)
        }
        val children = node.childNodes
        for (i in 0 until children.length) {
            collectValues(children.item(i), sink)
        }
    }

    @Test
    fun `four today styles exist with required parents, items, shadowRadius 0, and dimen-only sizes`() {
        val styles = parseStyles(resolveFile("src/main/res/values/styles.xml"))

        val required = listOf("TodayChip", "TodayChip.Small", "TodayButton", "TodayButton.Danger")
        for (name in required) {
            assertTrue("styles.xml must define $name", styles.containsKey(name))
        }

        // TodayChip: parent TodayCardBody, fontFamily ?attr/labelFontFamily, textColor @drawable/text_colors_chip, shadowRadius 0
        val chip = styles["TodayChip"]!!
        assertEquals("TodayCardBody", chip.getAttribute("parent"))
        val chipItems = styleItems(chip)
        assertEquals("?attr/labelFontFamily", chipItems["android:fontFamily"])
        assertEquals("@drawable/text_colors_chip", chipItems["android:textColor"])
        assertEquals("0", chipItems["android:shadowRadius"])

        // TodayChip.Small: parent TodayChip, textSize @dimen/today_label_size, shadowRadius 0
        val chipSmall = styles["TodayChip.Small"]!!
        assertEquals("TodayChip", chipSmall.getAttribute("parent"))
        val chipSmallItems = styleItems(chipSmall)
        assertEquals("@dimen/today_label_size", chipSmallItems["android:textSize"])
        assertEquals("0", chipSmallItems["android:shadowRadius"])

        // TodayButton: parent TodayCardBodyBold, background @drawable/today_tile, gravity center, minHeight @dimen/today_touch_min_height, textColor ?attr/accent, shadowRadius 0
        val button = styles["TodayButton"]!!
        assertEquals("TodayCardBodyBold", button.getAttribute("parent"))
        val buttonItems = styleItems(button)
        assertEquals("@drawable/today_tile", buttonItems["android:background"])
        assertEquals("center", buttonItems["android:gravity"])
        assertEquals("@dimen/today_touch_min_height", buttonItems["android:minHeight"])
        assertEquals("?attr/accent", buttonItems["android:textColor"])
        assertEquals("0", buttonItems["android:shadowRadius"])

        val buttonPadding = buttonItems["android:paddingHorizontal"]
            ?: buttonItems["android:paddingStart"]
        assertNotNull("TodayButton must define horizontal padding", buttonPadding)
        assertTrue("TodayButton horizontal padding must reference a dimen", buttonPadding!!.startsWith("@dimen/"))

        // TodayButton.Danger: parent TodayButton, textColor ?attr/dangerColor, shadowRadius 0
        val danger = styles["TodayButton.Danger"]!!
        assertEquals("TodayButton", danger.getAttribute("parent"))
        val dangerItems = styleItems(danger)
        assertEquals("?attr/dangerColor", dangerItems["android:textColor"])
        assertEquals("0", dangerItems["android:shadowRadius"])

        // All 4 styles must only use dimen references for sizes (no raw dp/sp)
        val sizeAttrNames = setOf(
            "android:textSize",
            "android:minHeight",
            "android:minWidth",
            "android:height",
            "android:width",
            "android:padding",
            "android:paddingHorizontal",
            "android:paddingVertical",
            "android:paddingStart",
            "android:paddingEnd",
            "android:paddingLeft",
            "android:paddingRight",
            "android:paddingTop",
            "android:paddingBottom",
        )
        for (name in required) {
            val items = styleItems(styles[name]!!)
            for ((attr, value) in items) {
                if (attr in sizeAttrNames) {
                    assertFalse("$name attribute $attr has raw sp/dp value: $value", rawDpOrSp.matches(value))
                    assertTrue("$name attribute $attr must reference a dimen: $value", value.startsWith("@dimen/"))
                }
            }
        }
    }

    @Test
    fun `text_colors_chip selector has accentColor for selected state`() {
        val file = resolveFile("src/main/res/drawable/text_colors_chip.xml")
        val doc = parseXml(file)
        val items = doc.getElementsByTagName("item")

        var selectedColor: String? = null
        var pressedColor: String? = null
        var defaultColor: String? = null

        for (i in 0 until items.length) {
            val el = items.item(i) as Element
            val color = el.getAttribute("android:color")
            val isSelected = el.getAttribute("android:state_selected") == "true"
            val isPressed = el.getAttribute("android:state_pressed") == "true"

            when {
                isSelected -> selectedColor = color
                isPressed -> pressedColor = color
                !el.hasAttribute("android:state_selected") && !el.hasAttribute("android:state_pressed") && !el.hasAttribute("android:state_focused") ->
                    defaultColor = color
            }
        }

        assertEquals("?attr/accentColor", selectedColor)
        assertEquals("?attr/primaryColorTrans80", pressedColor)
        assertEquals("?attr/primaryColor", defaultColor)
    }

    @Test
    fun `today_chip_selectable selected state contains todayTile and accentColor and no primaryColorTrans50`() {
        val file = resolveFile("src/main/res/drawable/today_chip_selectable.xml")
        val doc = parseXml(file)
        val items = doc.getElementsByTagName("item")

        var selectedItem: Element? = null
        for (i in 0 until items.length) {
            val el = items.item(i) as Element
            if (el.getAttribute("android:state_selected") == "true") {
                selectedItem = el
                break
            }
        }

        assertNotNull("today_chip_selectable.xml must have an item with android:state_selected=\"true\"", selectedItem)

        val values = mutableSetOf<String>()
        collectValues(selectedItem!!, values)

        assertTrue(
            "Selected state in today_chip_selectable.xml must contain @color/todayTile",
            values.contains("@color/todayTile")
        )
        assertTrue(
            "Selected state in today_chip_selectable.xml must contain ?attr/accentColor",
            values.contains("?attr/accentColor")
        )
        assertFalse(
            "Selected state in today_chip_selectable.xml must not contain ?attr/primaryColorTrans50",
            values.contains("?attr/primaryColorTrans50")
        )
    }

    @Test
    fun `light todayTile channels are at least 0xF8 and light todayTileShadow alpha is at least 0x24`() {
        val file = resolveFile("src/main/res/values/colors.xml")
        val doc = parseXml(file)
        val colorNodes = doc.getElementsByTagName("color")
        val colors = mutableMapOf<String, String>()

        for (i in 0 until colorNodes.length) {
            val el = colorNodes.item(i) as Element
            colors[el.getAttribute("name")] = el.textContent.trim()
        }

        val tileHex = colors["todayTile"]
        assertNotNull("colors.xml must define todayTile", tileHex)
        assertTrue("todayTile must be hex format", tileHex!!.startsWith("#"))

        val cleanTile = tileHex.removePrefix("#")
        val (r, g, b) = when (cleanTile.length) {
            6 -> Triple(
                cleanTile.substring(0, 2).toInt(16),
                cleanTile.substring(2, 4).toInt(16),
                cleanTile.substring(4, 6).toInt(16),
            )
            8 -> Triple(
                cleanTile.substring(2, 4).toInt(16),
                cleanTile.substring(4, 6).toInt(16),
                cleanTile.substring(6, 8).toInt(16),
            )
            else -> error("Unexpected hex length for todayTile: $tileHex")
        }

        assertTrue("todayTile red channel (0x${r.toString(16)}) must be >= 0xF8", r >= 0xF8)
        assertTrue("todayTile green channel (0x${g.toString(16)}) must be >= 0xF8", g >= 0xF8)
        assertTrue("todayTile blue channel (0x${b.toString(16)}) must be >= 0xF8", b >= 0xF8)

        val shadowHex = colors["todayTileShadow"]
        assertNotNull("colors.xml must define todayTileShadow", shadowHex)
        assertTrue("todayTileShadow must be 8-digit hex format (#AARRGGBB)", shadowHex!!.startsWith("#") && shadowHex.length == 9)

        val alpha = shadowHex.substring(1, 3).toInt(16)
        assertTrue("todayTileShadow alpha (0x${alpha.toString(16)}) must be >= 0x24", alpha >= 0x24)
    }

    @Test
    fun `dimens xml defines today_touch_min_height and today_chip_padding_horizontal in dp`() {
        val file = resolveFile("src/main/res/values/dimens.xml")
        val doc = parseXml(file)
        val dimenNodes = doc.getElementsByTagName("dimen")
        val dimens = mutableMapOf<String, String>()

        for (i in 0 until dimenNodes.length) {
            val el = dimenNodes.item(i) as Element
            dimens[el.getAttribute("name")] = el.textContent.trim()
        }

        val touchMinHeight = dimens["today_touch_min_height"]
        assertNotNull("dimens.xml must define today_touch_min_height", touchMinHeight)
        assertTrue("today_touch_min_height must be in dp", touchMinHeight!!.endsWith("dp"))
        assertEquals("48dp", touchMinHeight)

        val chipPaddingH = dimens["today_chip_padding_horizontal"]
        assertNotNull("dimens.xml must define today_chip_padding_horizontal", chipPaddingH)
        assertTrue("today_chip_padding_horizontal must be in dp", chipPaddingH!!.endsWith("dp"))
        assertEquals("12dp", chipPaddingH)

        val buttonPaddingH = dimens["today_button_padding_horizontal"]
        if (buttonPaddingH != null) {
            assertTrue("today_button_padding_horizontal must be in dp", buttonPaddingH.endsWith("dp"))
        }
    }
}
