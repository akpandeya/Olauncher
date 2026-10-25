package app.olauncher.res

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class AppDrawerResourceTest {

    private fun resolveFile(relative: String): File {
        return listOf(
            File(relative),
            File("app/$relative"),
        ).firstOrNull { it.isFile } ?: error("$relative not found from ${File(".").absolutePath}")
    }

    @Test
    fun `adapter_app_drawer row and appTitle have minHeight 48dp`() {
        val file = resolveFile("src/main/res/layout/adapter_app_drawer.xml")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val root = doc.documentElement
        assertEquals("FrameLayout", root.tagName)
        assertEquals("48dp", root.getAttribute("android:minHeight"))

        val textViews = doc.getElementsByTagName("TextView")
        var appTitle: Element? = null
        for (i in 0 until textViews.length) {
            val element = textViews.item(i) as Element
            if (element.getAttribute("android:id").substringAfterLast("/") == "appTitle") {
                appTitle = element
                break
            }
        }
        assertNotNull(appTitle)
        assertEquals("48dp", appTitle!!.getAttribute("android:minHeight"))
    }

    @Test
    fun `adapter_app_drawer contains highlightPill with bg_pill background`() {
        val file = resolveFile("src/main/res/layout/adapter_app_drawer.xml")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val views = doc.getElementsByTagName("View")
        var pill: Element? = null
        for (i in 0 until views.length) {
            val element = views.item(i) as Element
            if (element.getAttribute("android:id").substringAfterLast("/") == "highlightPill") {
                pill = element
                break
            }
        }
        assertNotNull("highlightPill View must exist in adapter_app_drawer.xml", pill)
        assertEquals("@drawable/bg_pill", pill!!.getAttribute("android:background"))
    }

    @Test
    fun `letterStripContainer and letterStrip do not clip children`() {
        for (layout in listOf("src/main/res/layout/fragment_app_drawer.xml", "src/main/res/layout-land/fragment_app_drawer.xml")) {
            val file = resolveFile(layout)
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val containers = doc.getElementsByTagName("LinearLayout")
            var strip: Element? = null
            var zone: Element? = null
            var container: Element? = null
            for (i in 0 until containers.length) {
                val element = containers.item(i) as Element
                when (element.getAttribute("android:id").substringAfterLast("/")) {
                    "letterStrip" -> strip = element
                    "letterStripZone" -> zone = element
                    "letterStripContainer" -> container = element
                }
            }
            assertNotNull("$layout must contain letterStrip", strip)
            assertNotNull("$layout must contain letterStripZone", zone)
            assertNotNull("$layout must contain letterStripContainer", container)
            assertEquals("$layout letterStrip must set clipChildren false", "false", strip!!.getAttribute("android:clipChildren"))
            assertEquals("$layout letterStripZone must set clipChildren false", "false", zone!!.getAttribute("android:clipChildren"))
            assertEquals("$layout letterStripContainer must set clipChildren false", "false", container!!.getAttribute("android:clipChildren"))
        }
    }
}
