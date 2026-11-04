package app.olauncher.helper

import app.olauncher.data.ThemePackCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The bundled packs under `app/src/main/assets/packs/` must parse with the strict codec,
 * resolve to a generated overlay style, and stay byte-identical to their source of truth
 * in `docs/design/themes/packs/`. Spot-checks the generated overlay colours against the
 * JSON for one light and one dark pack.
 */
class ThemePackAssetsTest {

    /** The test working directory is the app module dir; fall back to the repo root. */
    private fun locate(modulePath: String, repoPath: String): File =
        listOf(File(modulePath), File(repoPath)).firstOrNull { it.exists() }
            ?: error("Could not find $modulePath or $repoPath from ${File("").absolutePath}")

    private val packDir: File get() = locate("src/main/assets/packs", "app/src/main/assets/packs")
    private val docsPackDir: File get() = locate("../docs/design/themes/packs", "docs/design/themes/packs")
    private val overlaysFile: File
        get() = locate("src/main/res/values/theme_pack_overlays.xml", "app/src/main/res/values/theme_pack_overlays.xml")

    private fun assetFiles(): List<File> =
        packDir.listFiles { file -> file.isFile && file.name.endsWith(".json") }!!
            .sortedBy { it.name }

    /** #RRGGBB -> the uppercase #AARRGGBB literal the generator writes into the overlays. */
    private fun opaque(hex: String): String = "#FF" + hex.removePrefix("#").uppercase()

    @Test
    fun `all 28 bundled packs parse with the strict codec`() {
        val files = assetFiles()
        assertEquals(28, files.size)
        val packs = files.map { ThemePackCodec.decode(it.readText(), it.name) }
        assertEquals(files.size, packs.size)
        // Ids are unique and match the file names.
        assertEquals(files.size, packs.map { it.id }.toSet().size)
    }

    @Test
    fun `every pack id resolves to a generated overlay style`() {
        val packs = assetFiles().map { ThemePackCodec.decode(it.readText(), it.name) }
        for (pack in packs) {
            assertNotEquals(
                "Pack ${pack.id} has no overlay style",
                0,
                ThemePacks.styleResForId(pack.id),
            )
        }
        assertEquals(0, ThemePacks.styleResForId(""))
        assertEquals(0, ThemePacks.styleResForId("no-such-pack"))
    }

    @Test
    fun `the thumbline light overlay matches the pack colours`() {
        assertOverlayMatches("thumbline-light", "Pack.ThumblineLight")
    }

    @Test
    fun `the christmas dark overlay matches the pack colours`() {
        assertOverlayMatches("christmas-dark", "Pack.ChristmasDark")
    }

    private fun assertOverlayMatches(id: String, styleName: String) {
        val pack = ThemePackCodec.decode(File(packDir, "$id.json").readText(), "$id.json")
        val style = parseOverlays().firstOrNull { it.getAttribute("name") == styleName }
            ?: error("Style $styleName missing from theme_pack_overlays.xml")

        // The colours are <item> child elements of the style, keyed by item name.
        val items = mutableMapOf<String, String>()
        for (i in 0 until style.childNodes.length) {
            val node = style.childNodes.item(i) as? org.w3c.dom.Element ?: continue
            items[node.getAttribute("name")] = node.textContent.trim()
        }
        assertEquals(opaque(pack.background), items["android:windowBackground"])
        assertEquals(opaque(pack.ink), items["ink"])
        assertEquals(opaque(pack.accent), items["accentColor"])
        assertEquals(opaque(pack.surface), items["surface"])
        // Today panels/chips are ink-tinted in BOTH modes (mirrors values-night/colors.xml);
        // pins the review fix where dark packs previously derived them from the background.
        val panelAlpha = if (pack.mode == "light") "0F" else "12"
        val chipAlpha = if (pack.mode == "light") "1F" else "24"
        assertEquals("#$panelAlpha${pack.ink.removePrefix("#").uppercase()}", items["todayPanelColor"])
        assertEquals("#$chipAlpha${pack.ink.removePrefix("#").uppercase()}", items["todayChipColor"])
    }

    @Test
    fun `bundled packs are byte-identical to the design docs source`() {
        val files = assetFiles()
        assertTrue("No bundled packs found under $packDir", files.isNotEmpty())
        for (file in files) {
            val source = File(docsPackDir, file.name)
            assertTrue("Missing docs source for ${file.name}", source.isFile)
            assertArrayEquals(
                "${file.name} drifted from docs/design/themes/packs — copy the file over",
                source.readBytes(),
                file.readBytes(),
            )
        }
    }

    private fun parseOverlays(): List<org.w3c.dom.Element> {
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val document = builder.parse(overlaysFile)
        val styles = document.getElementsByTagName("style")
        return (0 until styles.length).map { styles.item(it) as org.w3c.dom.Element }
    }
}
