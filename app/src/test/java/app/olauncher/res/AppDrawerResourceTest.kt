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
}
