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

    private fun animatorFor(doc: Document, property: String): Element {
        val nodes = doc.getElementsByTagName("objectAnimator")
        val matches = (0 until nodes.length)
            .map { nodes.item(it) as Element }
            .filter { it.getAttribute("android:propertyName") == property }
        assertEquals(1, matches.size)
        return matches.single().also {
            assertEquals("120", it.getAttribute("android:duration"))
            assertTrue(it.getAttribute("android:duration").toInt() <= 150)
        }
    }

    private fun assertSlideAndFade(
        name: String,
        slideProperty: String,
        slideFrom: String,
        slideTo: String,
        alphaFrom: String,
        alphaTo: String,
    ) {
        val doc = parseXml(resolveFile("src/main/res/animator/$name.xml"))
        val slide = animatorFor(doc, slideProperty)
        assertEquals(slideFrom, slide.getAttribute("android:valueFrom"))
        assertEquals(slideTo, slide.getAttribute("android:valueTo"))
        val alpha = animatorFor(doc, "alpha")
        assertEquals(alphaFrom, alpha.getAttribute("android:valueFrom"))
        assertEquals(alphaTo, alpha.getAttribute("android:valueTo"))
    }

    @Test
    fun `drawer enter animator has 120 ms fade and 24dp slide up`() {
        assertSlideAndFade("drawer_enter", "translationY", "24dp", "0", "0.0", "1.0")
    }

    @Test
    fun `drawer exit animator reverses drawer enter with 120 ms duration`() {
        assertSlideAndFade("drawer_exit", "translationY", "0", "24dp", "1.0", "0.0")
    }

    @Test
    fun `today enter animator has 120 ms fade and slide from left`() {
        assertSlideAndFade("today_enter", "translationX", "-360dp", "0", "0.0", "1.0")
    }

    @Test
    fun `today exit animator reverses today enter with 120 ms duration`() {
        assertSlideAndFade("today_exit", "translationX", "0", "-360dp", "1.0", "0.0")
    }

    @Test
    fun `fade animators are plain alpha changes`() {
        val enter = parseXml(resolveFile("src/main/res/animator/fade_enter.xml")).documentElement
        assertEquals("alpha", enter.getAttribute("android:propertyName"))
        assertEquals("0.0", enter.getAttribute("android:valueFrom"))
        assertEquals("1.0", enter.getAttribute("android:valueTo"))

        val exit = parseXml(resolveFile("src/main/res/animator/fade_exit.xml")).documentElement
        assertEquals("alpha", exit.getAttribute("android:propertyName"))
        assertEquals("1.0", exit.getAttribute("android:valueFrom"))
        assertEquals("0.0", exit.getAttribute("android:valueTo"))
    }

    @Test
    fun `nav graph connects drawer and today actions to quiet motion animators`() {
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
        assertEquals("@animator/drawer_enter", drawerAction!!.getAttribute("app:enterAnim"))
        assertEquals("@animator/drawer_exit", drawerAction.getAttribute("app:popExitAnim"))

        val todayAction = actionsById["action_mainFragment_to_todayFragment"]
        assertNotNull(todayAction)
        assertEquals("@animator/today_enter", todayAction!!.getAttribute("app:enterAnim"))
        assertEquals("@animator/today_exit", todayAction.getAttribute("app:popExitAnim"))

        val settingsAction = actionsById["action_mainFragment_to_settingsFragment"]
        assertNotNull(settingsAction)
        assertEquals("@animator/fade_enter", settingsAction!!.getAttribute("app:enterAnim"))
        assertEquals("@animator/fade_exit", settingsAction.getAttribute("app:exitAnim"))

        val pauseAction = actionsById["action_mainFragment_to_pauseFragment"]
        assertNotNull(pauseAction)
        assertEquals("@animator/fade_enter", pauseAction!!.getAttribute("app:enterAnim"))
        assertEquals("@animator/fade_exit", pauseAction.getAttribute("app:exitAnim"))
    }
}
