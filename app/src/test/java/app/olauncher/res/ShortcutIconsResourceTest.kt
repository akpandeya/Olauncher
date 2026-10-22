package app.olauncher.res

import app.olauncher.R
import app.olauncher.ui.settings.SettingsCatalog
import app.olauncher.ui.settings.SettingsSections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Verifies the resource definitions for the Shortcut icons section and alignment menu:
 * - alignment.xml keeps only Left, Center, Right, Bottom
 * - strings.xml defines the expected labels for the Shortcut icons section
 * - the Settings list places the Shortcut icons section right after the Home screen section
 */
class ShortcutIconsResourceTest {

    private fun resolveFile(relative: String): File {
        return listOf(
            File(relative),
            File("app/$relative"),
        ).firstOrNull { it.isFile } ?: error("$relative not found from ${File(".").absolutePath}")
    }

    private fun loadStrings(): Map<String, String> {
        val file = resolveFile("src/main/res/values/strings.xml")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        val result = mutableMapOf<String, String>()
        for (i in 0 until nodes.length) {
            val element = nodes.item(i) as Element
            var text = element.textContent
            if (text.length >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
                text = text.substring(1, text.length - 1)
            }
            result[element.getAttribute("name")] = text
        }
        return result
    }

    @Test
    fun `alignment menu contains only Left Center Right Bottom`() {
        val file = resolveFile("src/main/res/menu/alignment.xml")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val items = doc.getElementsByTagName("item")
        val ids = mutableListOf<String>()
        for (i in 0 until items.length) {
            val element = items.item(i) as Element
            ids.add(element.getAttribute("android:id").removePrefix("@+id/"))
        }

        val expected = listOf(
            "alignmentLeft",
            "alignmentCenter",
            "alignmentRight",
            "alignmentBottom",
        )
        assertEquals(expected, ids)

        // Specifically ensure shortcut options are no longer in the menu
        assertFalse("alignmentShortcuts must be removed", ids.contains("alignmentShortcuts"))
        assertFalse("alignmentShortcutCount must be removed", ids.contains("alignmentShortcutCount"))
        assertFalse("alignmentShortcutSide must be removed", ids.contains("alignmentShortcutSide"))
        assertFalse("alignmentShortcutHeight must be removed", ids.contains("alignmentShortcutHeight"))
        assertFalse("alignmentShortcutIconStyle must be removed", ids.contains("alignmentShortcutIconStyle"))
    }

    @Test
    fun `strings contains shortcut icons section row labels`() {
        val strings = loadStrings()
        assertEquals("Shortcut icons", strings["shortcut_icons"])
        assertEquals("Icons", strings["icons"])
        assertEquals("Size", strings["size"])
        assertEquals("Side", strings["side"])
        assertEquals("Height", strings["height"])
        assertEquals("Style", strings["style"])
        assertEquals("Icon size", strings["icon_size"])
        assertEquals("Small", strings["icon_size_small"])
        assertEquals("Medium", strings["icon_size_medium"])
        assertEquals("Large", strings["icon_size_large"])
        assertEquals("Original", strings["icon_style_original"])
        assertEquals("Monochrome", strings["icon_style_monochrome"])
        assertEquals("Text colour", strings["icon_style_text_colour"])
        assertEquals("Left", strings["left"])
        assertEquals("Right", strings["right"])
        assertEquals("On", strings["on"])
        assertEquals("Off", strings["off"])
    }

    @Test
    fun `settings list contains shortcut icons section after home screen`() {
        val order = SettingsSections.all.map { it.key }
        val home = order.indexOf(SettingsSections.KEY_HOME)
        val shortcuts = order.indexOf(SettingsSections.KEY_SHORTCUTS)
        val appearance = order.indexOf(SettingsSections.KEY_APPEARANCE)
        assertTrue("home screen exists", home != -1)
        assertTrue("shortcut icons section exists", shortcuts != -1)
        assertTrue("appearance exists", appearance != -1)
        assertTrue("shortcut icons should sit after home screen and before appearance", home < shortcuts && shortcuts < appearance)

        assertEquals(R.id.shortcutIconsSection, SettingsSections.SHORTCUTS.containerId)
        assertEquals(
            listOf(
                R.id.shortcutIconsOnOffLayout,
                R.id.shortcutCountLayout,
                R.id.shortcutSizeLayout,
                R.id.shortcutSideLayout,
                R.id.shortcutHeightLayout,
                R.id.shortcutIconStyleLayout,
            ),
            SettingsCatalog.rowsFor(SettingsSections.KEY_SHORTCUTS).map { it.layoutId },
        )
    }
}
