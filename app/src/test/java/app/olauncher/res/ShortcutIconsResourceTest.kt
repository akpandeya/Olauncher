package app.olauncher.res

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
 * - fragment_settings.xml places shortcutIconsSection right after the Home screen section
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
    fun `settings layout contains shortcut icons section after home screen`() {
        val layoutFiles = listOf(
            resolveFile("src/main/res/layout/fragment_settings.xml"),
            resolveFile("src/main/res/layout-land/fragment_settings.xml"),
        )

        for (file in layoutFiles) {
            val content = file.readText()
            assertTrue(
                "shortcutIconsSection must be in ${file.name}",
                content.contains("android:id=\"@+id/shortcutIconsSection\"")
            )
            assertTrue(
                "shortcutIconsOnOffLayout must be in ${file.name}",
                content.contains("android:id=\"@+id/shortcutIconsOnOffLayout\"")
            )
            assertTrue(
                "shortcutCountLayout must be in ${file.name}",
                content.contains("android:id=\"@+id/shortcutCountLayout\"")
            )
            assertTrue(
                "shortcutSizeLayout must be in ${file.name}",
                content.contains("android:id=\"@+id/shortcutSizeLayout\"")
            )
            assertTrue(
                "shortcutSideLayout must be in ${file.name}",
                content.contains("android:id=\"@+id/shortcutSideLayout\"")
            )
            assertTrue(
                "shortcutHeightLayout must be in ${file.name}",
                content.contains("android:id=\"@+id/shortcutHeightLayout\"")
            )
            assertTrue(
                "shortcutIconStyleLayout must be in ${file.name}",
                content.contains("android:id=\"@+id/shortcutIconStyleLayout\"")
            )

            // Verify section ordering: home_screen -> shortcutIconsSection -> appearance
            val homeScreenIdx = content.indexOf("@string/home_screen")
            val shortcutSectionIdx = content.indexOf("@+id/shortcutIconsSection")
            val appearanceIdx = content.indexOf("@string/appearance")

            assertTrue("home_screen exists", homeScreenIdx != -1)
            assertTrue("shortcutIconsSection exists", shortcutSectionIdx != -1)
            assertTrue("appearance exists", appearanceIdx != -1)
            assertTrue(
                "shortcutIconsSection should sit after home_screen and before appearance in ${file.name}",
                homeScreenIdx < shortcutSectionIdx && shortcutSectionIdx < appearanceIdx
            )
        }
    }
}
