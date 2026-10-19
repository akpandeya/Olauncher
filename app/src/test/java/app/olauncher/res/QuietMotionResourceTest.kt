package app.olauncher.res

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class QuietMotionResourceTest {

    private fun resolveFile(relative: String): File {
        return listOf(
            File(relative),
            File("app/$relative"),
        ).firstOrNull { it.isFile } ?: error("$relative not found from ${File(".").absolutePath}")
    }

    private fun parseXml(file: File): Document {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
    }

    @Test
    fun `drawer enter animation has 120 ms fade and 24dp slide up`() {
        val file = resolveFile("src/main/res/anim/drawer_enter.xml")
        val doc = parseXml(file)

        val translates = doc.getElementsByTagName("translate")
        assertEquals(1, translates.length)
        val translate = translates.item(0) as Element
        assertEquals("120", translate.getAttribute("android:duration"))
        assertEquals("24dp", translate.getAttribute("android:fromYDelta"))
        assertEquals("0", translate.getAttribute("android:toYDelta"))
        assertTrue(translate.getAttribute("android:duration").toInt() <= 150)

        val alphas = doc.getElementsByTagName("alpha")
        assertEquals(1, alphas.length)
        val alpha = alphas.item(0) as Element
        assertEquals("120", alpha.getAttribute("android:duration"))
        assertEquals("0.0", alpha.getAttribute("android:fromAlpha"))
        assertEquals("1.0", alpha.getAttribute("android:toAlpha"))
        assertTrue(alpha.getAttribute("android:duration").toInt() <= 150)
    }

    @Test
    fun `drawer exit animation reverses drawer enter with 120 ms duration`() {
        val file = resolveFile("src/main/res/anim/drawer_exit.xml")
        val doc = parseXml(file)

        val translates = doc.getElementsByTagName("translate")
        assertEquals(1, translates.length)
        val translate = translates.item(0) as Element
        assertEquals("120", translate.getAttribute("android:duration"))
        assertEquals("0", translate.getAttribute("android:fromYDelta"))
        assertEquals("24dp", translate.getAttribute("android:toYDelta"))
        assertTrue(translate.getAttribute("android:duration").toInt() <= 150)

        val alphas = doc.getElementsByTagName("alpha")
        assertEquals(1, alphas.length)
        val alpha = alphas.item(0) as Element
        assertEquals("120", alpha.getAttribute("android:duration"))
        assertEquals("1.0", alpha.getAttribute("android:fromAlpha"))
        assertEquals("0.0", alpha.getAttribute("android:toAlpha"))
        assertTrue(alpha.getAttribute("android:duration").toInt() <= 150)
    }

    @Test
    fun `today enter animation has 120 ms fade and slide from left`() {
        val file = resolveFile("src/main/res/anim/today_enter.xml")
        val doc = parseXml(file)

        val translates = doc.getElementsByTagName("translate")
        assertEquals(1, translates.length)
        val translate = translates.item(0) as Element
        assertEquals("120", translate.getAttribute("android:duration"))
        assertEquals("-100%", translate.getAttribute("android:fromXDelta"))
        assertEquals("0%", translate.getAttribute("android:toXDelta"))
        assertTrue(translate.getAttribute("android:duration").toInt() <= 150)

        val alphas = doc.getElementsByTagName("alpha")
        assertEquals(1, alphas.length)
        val alpha = alphas.item(0) as Element
        assertEquals("120", alpha.getAttribute("android:duration"))
        assertEquals("0.0", alpha.getAttribute("android:fromAlpha"))
        assertEquals("1.0", alpha.getAttribute("android:toAlpha"))
        assertTrue(alpha.getAttribute("android:duration").toInt() <= 150)
    }

    @Test
    fun `today exit animation reverses today enter with 120 ms duration`() {
        val file = resolveFile("src/main/res/anim/today_exit.xml")
        val doc = parseXml(file)

        val translates = doc.getElementsByTagName("translate")
        assertEquals(1, translates.length)
        val translate = translates.item(0) as Element
        assertEquals("120", translate.getAttribute("android:duration"))
        assertEquals("0%", translate.getAttribute("android:fromXDelta"))
        assertEquals("-100%", translate.getAttribute("android:toXDelta"))
        assertTrue(translate.getAttribute("android:duration").toInt() <= 150)

        val alphas = doc.getElementsByTagName("alpha")
        assertEquals(1, alphas.length)
        val alpha = alphas.item(0) as Element
        assertEquals("120", alpha.getAttribute("android:duration"))
        assertEquals("1.0", alpha.getAttribute("android:fromAlpha"))
        assertEquals("0.0", alpha.getAttribute("android:toAlpha"))
        assertTrue(alpha.getAttribute("android:duration").toInt() <= 150)
    }

    @Test
    fun `nav graph connects drawer and today actions to quiet motion animations`() {
        val file = resolveFile("src/main/res/navigation/nav_graph.xml")
        val doc = parseXml(file)

        val actions = doc.getElementsByTagName("action")
        val actionsById = mutableMapOf<String, Element>()
        for (i in 0 until actions.length) {
            val element = actions.item(i) as Element
            val id = element.getAttribute("android:id").substringAfterLast("/")
            actionsById[id] = element
        }

        val drawerAction = actionsById["action_mainFragment_to_appListFragment"]
        assertNotNull(drawerAction)
        assertEquals("@anim/drawer_enter", drawerAction!!.getAttribute("app:enterAnim"))
        assertEquals("@anim/drawer_exit", drawerAction.getAttribute("app:popExitAnim"))

        val todayAction = actionsById["action_mainFragment_to_todayFragment"]
        assertNotNull(todayAction)
        assertEquals("@anim/today_enter", todayAction!!.getAttribute("app:enterAnim"))
        assertEquals("@anim/today_exit", todayAction.getAttribute("app:popExitAnim"))

        // Settings and Pause keep their current (no motion) behaviour
        val settingsAction = actionsById["action_mainFragment_to_settingsFragment"]
        assertNotNull(settingsAction)
        assertEquals("@anim/fade_enter", settingsAction!!.getAttribute("app:enterAnim"))
        assertEquals("@anim/fade_exit", settingsAction.getAttribute("app:exitAnim"))

        val pauseAction = actionsById["action_mainFragment_to_pauseFragment"]
        assertNotNull(pauseAction)
        assertEquals("@anim/fade_enter", pauseAction!!.getAttribute("app:enterAnim"))
        assertEquals("@anim/fade_exit", pauseAction.getAttribute("app:exitAnim"))
    }
}
