package app.olauncher.res

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class PhotoWallpaperMenuResourceTest {

    private fun resolveFile(relative: String): File {
        return listOf(
            File(relative),
            File("app/$relative"),
        ).firstOrNull { it.isFile } ?: error("$relative not found from ${File(".").absolutePath}")
    }

    private fun resolveDir(relative: String): File {
        return listOf(
            File(relative),
            File("app/$relative"),
        ).firstOrNull { it.isDirectory } ?: error("$relative not found from ${File(".").absolutePath}")
    }

    private fun loadStrings(file: File): Map<String, String> {
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
    fun `add_photos string is defined in main strings and not in other locales`() {
        val defaultStringsFile = resolveFile("src/main/res/values/strings.xml")
        val defaultStrings = loadStrings(defaultStringsFile)
        assertEquals("Add photos", defaultStrings["add_photos"])

        val resDir = resolveDir("src/main/res")
        val otherLocaleFiles = resDir.listFiles { dir ->
            dir.isDirectory && dir.name.startsWith("values-")
        }?.mapNotNull { dir ->
            val stringsFile = File(dir, "strings.xml")
            if (stringsFile.exists()) stringsFile else null
        }.orEmpty()

        assertTrue("Expected other locale directories", otherLocaleFiles.isNotEmpty())
        for (file in otherLocaleFiles) {
            val localizedStrings = loadStrings(file)
            assertNull("add_photos should not be defined in ${file.parentFile?.name}", localizedStrings["add_photos"])
        }
    }

    @Test
    fun `remove photos strings are defined in main strings and not in other locales`() {
        val defaultStringsFile = resolveFile("src/main/res/values/strings.xml")
        val defaultStrings = loadStrings(defaultStringsFile)
        assertEquals("Remove photos", defaultStrings["remove_photos"])
        assertEquals("Photo %d", defaultStrings["photo_label"])
        assertEquals("Photo %d (on screen)", defaultStrings["photo_label_current"])
        assertEquals("Remove", defaultStrings["remove"])
        assertEquals("Could not remove the photos", defaultStrings["wallpaper_photos_remove_failed"])

        val resDir = resolveDir("src/main/res")
        val otherLocaleFiles = resDir.listFiles { dir ->
            dir.isDirectory && dir.name.startsWith("values-")
        }?.mapNotNull { dir ->
            val stringsFile = File(dir, "strings.xml")
            if (stringsFile.exists()) stringsFile else null
        }.orEmpty()

        assertTrue("Expected other locale directories", otherLocaleFiles.isNotEmpty())
        val checkKeys = listOf(
            "remove_photos",
            "photo_label",
            "photo_label_current",
            "remove",
            "wallpaper_photos_remove_failed",
        )
        for (file in otherLocaleFiles) {
            val localizedStrings = loadStrings(file)
            for (key in checkKeys) {
                assertNull("$key should not be defined in ${file.parentFile?.name}", localizedStrings[key])
            }
        }
    }

    @Test
    fun `photo wallpaper menu has expected items and order`() {
        val menuFile = resolveFile("src/main/res/menu/photo_wallpaper.xml")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(menuFile)
        val items = doc.getElementsByTagName("item")
        assertEquals(8, items.length)

        val expected = listOf(
            Pair("@+id/photoWallpaperAdd", "@string/add_photos"),
            Pair("@+id/photoWallpaperRemove", "@string/remove_photos"),
            Pair("@+id/photoWallpaperChange", "@string/change_photos"),
            Pair("@+id/photoWallpaperTarget", "@string/set_on_home"),
            Pair("@+id/photoWallpaperNext", "@string/next_photo_now"),
            Pair("@+id/photoWallpaperPrevious", "@string/previous_photo"),
            Pair("@+id/photoWallpaperHomeMenu", "@string/wallpaper_home_menu"),
            Pair("@+id/photoWallpaperOff", "@string/off"),
        )

        for (i in 0 until items.length) {
            val element = items.item(i) as Element
            val (expectedId, expectedTitle) = expected[i]
            assertEquals("Item $i ID", expectedId, element.getAttribute("android:id"))
            assertEquals("Item $i Title", expectedTitle, element.getAttribute("android:title"))
        }

        val homeMenuItem = items.item(6) as Element
        assertEquals("true", homeMenuItem.getAttribute("android:checkable"))
    }

    @Test
    fun `manual rotation and home menu strings are defined in main strings and not in other locales`() {
        val defaultStringsFile = resolveFile("src/main/res/values/strings.xml")
        val defaultStrings = loadStrings(defaultStringsFile)
        assertEquals("Previous photo", defaultStrings["previous_photo"])
        assertEquals("Switch from home (long-press)", defaultStrings["wallpaper_home_menu"])

        val resDir = resolveDir("src/main/res")
        val otherLocaleFiles = resDir.listFiles { dir ->
            dir.isDirectory && dir.name.startsWith("values-")
        }?.mapNotNull { dir ->
            val stringsFile = File(dir, "strings.xml")
            if (stringsFile.exists()) stringsFile else null
        }.orEmpty()

        assertTrue("Expected other locale directories", otherLocaleFiles.isNotEmpty())
        val checkKeys = listOf(
            "previous_photo",
            "wallpaper_home_menu",
        )
        for (file in otherLocaleFiles) {
            val localizedStrings = loadStrings(file)
            for (key in checkKeys) {
                assertNull("$key should not be defined in ${file.parentFile?.name}", localizedStrings[key])
            }
        }
    }

    @Test
    fun `home screen wallpaper menu strings are defined in main strings and not in other locales`() {
        val defaultStringsFile = resolveFile("src/main/res/values/strings.xml")
        val defaultStrings = loadStrings(defaultStringsFile)
        assertEquals("Next wallpaper", defaultStrings["next_wallpaper"])
        assertEquals("Previous wallpaper", defaultStrings["previous_wallpaper"])
        assertEquals("Settings", defaultStrings["settings"])

        val resDir = resolveDir("src/main/res")
        val otherLocaleFiles = resDir.listFiles { dir ->
            dir.isDirectory && dir.name.startsWith("values-")
        }?.mapNotNull { dir ->
            val stringsFile = File(dir, "strings.xml")
            if (stringsFile.exists()) stringsFile else null
        }.orEmpty()

        assertTrue("Expected other locale directories", otherLocaleFiles.isNotEmpty())
        val checkKeys = listOf(
            "next_wallpaper",
            "previous_wallpaper",
            "settings",
        )
        for (file in otherLocaleFiles) {
            val localizedStrings = loadStrings(file)
            for (key in checkKeys) {
                assertNull("$key should not be defined in ${file.parentFile?.name}", localizedStrings[key])
            }
        }
    }
}
