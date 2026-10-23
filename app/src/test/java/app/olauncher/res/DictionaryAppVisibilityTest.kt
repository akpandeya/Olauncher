package app.olauncher.res

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class DictionaryAppVisibilityTest {
    @Test fun `package visibility includes both text handoffs and launcher fallback`() {
        val file = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml"))
            .first { it.isFile }
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val queries = document.getElementsByTagName("queries").item(0) as Element
        val intents = queries.getElementsByTagName("intent")
        val actions = mutableMapOf<String, Element>()
        for (index in 0 until intents.length) {
            val intent = intents.item(index) as Element
            val action = intent.getElementsByTagName("action").item(0) as Element
            actions[action.getAttribute("android:name")] = intent
        }
        for (action in listOf("android.intent.action.PROCESS_TEXT", "android.intent.action.SEND")) {
            val intent = actions[action] ?: error("Missing query for $action")
            val data = intent.getElementsByTagName("data").item(0) as Element
            assertEquals("text/plain", data.getAttribute("android:mimeType"))
        }
        val launcher = actions["android.intent.action.MAIN"] ?: error("Missing launcher query")
        val category = launcher.getElementsByTagName("category").item(0) as Element
        assertEquals("android.intent.category.LAUNCHER", category.getAttribute("android:name"))
    }
}
