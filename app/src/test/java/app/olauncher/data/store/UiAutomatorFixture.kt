package app.olauncher.data.store

import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Test-only loader for uiautomator dump fixtures (`app/src/test/resources/capture/`).
 *
 * tagesschau_article.xml is a real dump; two_column.xml is synthetic (the real dump has no
 * clean two-column body region). uiautomator has no per-character boxes, so the loader
 * splits each text node's bounds evenly per character (integer division, no stretching).
 * That is synthetic geometry — the production path gets real boxes from
 * EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY.
 */
object UiAutomatorFixture {

    fun loadTagesschau(): List<ScreenNode> = load("/capture/tagesschau_article.xml")

    fun loadTwoColumn(): List<ScreenNode> = load("/capture/two_column.xml")

    fun load(path: String): List<ScreenNode> {
        val stream = UiAutomatorFixture::class.java.classLoader?.getResourceAsStream(path.trimStart('/'))
            ?: UiAutomatorFixture::class.java.getResourceAsStream(path)
            ?: error("fixture not found: $path")
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(stream)
        val nodes = ArrayList<ScreenNode>()
        visit(document.documentElement, parentId = -1, nodes = nodes)
        return nodes
    }

    private fun visit(element: Element, parentId: Int, nodes: MutableList<ScreenNode>) {
        if (element.tagName != "node") {
            elementChildElements(element).forEach { visit(it, parentId, nodes) }
            return
        }
        val id = nodes.size
        val text = element.getAttribute("text").takeIf { it.isNotEmpty() }
        val bounds = parseBounds(element.getAttribute("bounds"))
        nodes += ScreenNode(
            id = id,
            parentId = parentId,
            text = text,
            bounds = bounds,
            charBoxes = evenCharBoxes(text, bounds),
            className = element.getAttribute("class").takeIf { it.isNotEmpty() },
            contentDescription = element.getAttribute("content-desc").takeIf { it.isNotEmpty() },
        )
        elementChildElements(element).forEach { visit(it, id, nodes) }
    }

    /** One box per character of [text], spaced evenly across [bounds]' width. */
    private fun evenCharBoxes(text: String?, bounds: ScreenBox?): List<ScreenBox>? {
        if (text.isNullOrEmpty() || bounds == null) return null
        val charWidth = bounds.width / text.length
        return text.indices.map { i ->
            ScreenBox(bounds.left + i * charWidth, bounds.top, bounds.left + (i + 1) * charWidth, bounds.bottom)
        }
    }

    /** uiautomator bounds format: "[left,top][right,bottom]". */
    private fun parseBounds(raw: String): ScreenBox? {
        val match = BOUNDS_REGEX.matchEntire(raw) ?: return null
        val (left, top, right, bottom) = match.destructured
        return ScreenBox(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
    }

    private val BOUNDS_REGEX = Regex("""\[(-?\d+),(-?\d+)]\[(-?\d+),(-?\d+)]""")

    private fun elementChildElements(element: Element): List<Element> =
        (0 until element.childNodes.length)
            .map { element.childNodes.item(it) }
            .filterIsInstance<Element>()
}
