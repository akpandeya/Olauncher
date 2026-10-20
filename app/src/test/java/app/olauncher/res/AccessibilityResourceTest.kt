package app.olauncher.res

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Every clickable view in the layouts must have text or a contentDescription.
 * A TextView filled in from code counts when the layout previews it with tools:text.
 * A clickable container counts when a descendant is labelled, because TalkBack reads its children.
 * Every ImageView or ImageButton needs a contentDescription or importantForAccessibility="no",
 * since images are often made clickable from code without android:clickable in the layout.
 */
class AccessibilityResourceTest {
    private val ns = "http://schemas.android.com/apk/res/android"

    private fun layoutDir(): File = listOf(File("src/main/res/layout"), File("app/src/main/res/layout"))
        .firstOrNull { it.isDirectory } ?: error("layout dir not found from ${File(".").absolutePath}")

    private val tools = "http://schemas.android.com/tools"

    private fun Element.hasAttr(name: String) = hasAttributeNS(ns, name) && getAttributeNS(ns, name).isNotBlank()

    private fun Element.isLabelled() =
        hasAttr("text") || hasAttr("contentDescription") || getAttributeNS(tools, "text").isNotBlank()

    private fun Element.hasLabelledDescendant(): Boolean {
        val children = childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (child.isLabelled() || child.hasLabelledDescendant()) return true
        }
        return false
    }

    private fun collect(el: Element, file: String, out: MutableList<String>) {
        val clickable = el.getAttributeNS(ns, "clickable") == "true"
        if (clickable && !el.isLabelled() && !el.hasLabelledDescendant()) {
            out.add("$file: <${el.tagName}> id=${el.getAttributeNS(ns, "id")}")
        }
        // Images have no text of their own, so they need a description or must be marked decorative.
        val isImage = el.localName in setOf("ImageView", "ImageButton") ||
            el.tagName.endsWith(".AppCompatImageView") || el.tagName.endsWith(".AppCompatImageButton")
        if (isImage && !el.hasAttr("contentDescription") && el.getAttributeNS(ns, "importantForAccessibility") != "no") {
            out.add("$file: <${el.tagName}> id=${el.getAttributeNS(ns, "id")} (image needs contentDescription or importantForAccessibility=\"no\")")
        }
        val children = el.childNodes
        for (i in 0 until children.length) (children.item(i) as? Element)?.let { collect(it, file, out) }
    }

    @Test
    fun clickableViewsHaveTextOrContentDescription() {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val files = layoutDir().listFiles { f -> f.extension == "xml" }!!.sortedBy { it.name }
        assertTrue("no layouts found", files.isNotEmpty())
        val offenders = mutableListOf<String>()
        for (f in files) collect(factory.newDocumentBuilder().parse(f).documentElement, f.name, offenders)
        assertTrue("Unlabelled clickable views:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
