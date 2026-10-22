package app.olauncher.res

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the design tokens (epic #247): layouts and drawables must use colour, type and radius
 * tokens instead of raw hex colours, font families or dp radii.
 */
class DesignTokenResourceTest {

    private val hexColour = Regex("#([0-9a-fA-F]{8}|[0-9a-fA-F]{6})\\b")
    private val fontFamilyAttr = Regex("\\b(android|app):fontFamily\\s*=")
    private val rawRadius = Regex("\\b(android:)?(\\w*[rR]adius)\\s*=\\s*\"(-?[0-9.]+)dp\"")

    // Known exceptions, to be cleaned up in a follow-up. Keep this short.
    private val hexAllowList = emptySet<String>()

    // Hard-coded font families; should move to the text styles.
    private val fontFamilyAllowList = setOf(
        "layout-land/fragment_home.xml",
    )

    private fun resDir(): File {
        return listOf(File("src/main/res"), File("app/src/main/res"))
            .firstOrNull { it.isDirectory } ?: error("src/main/res not found from ${File(".").absolutePath}")
    }

    private fun xmlFiles(prefixes: List<String>): List<File> {
        return resDir().listFiles { f -> f.isDirectory && prefixes.any { f.name == it || f.name.startsWith("$it-") } }
            .orEmpty()
            .flatMap { dir -> dir.listFiles { f -> f.isFile && f.extension == "xml" }.orEmpty().toList() }
            .sortedBy { it.path }
    }

    private fun relative(file: File) = "${file.parentFile.name}/${file.name}"

    private fun violations(files: List<File>, allowed: Set<String>, describe: (String) -> String?): List<String> {
        return files.filter { relative(it) !in allowed }.flatMap { file ->
            file.readLines().mapIndexedNotNull { i, line ->
                describe(line)?.let { "${relative(file)}:${i + 1}: $it" }
            }
        }
    }

    @Test
    fun layoutsAndDrawablesHaveNoRawHexColours() {
        val files = xmlFiles(listOf("layout", "drawable")).filterNot { it.name.startsWith("ic_launcher") }
        val found = violations(files, hexAllowList) { line ->
            hexColour.find(line)?.let { "raw hex colour ${it.value}, use a colour token" }
        }
        assertTrue("Raw hex colours found:\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test
    fun layoutsDoNotSetFontFamily() {
        val found = violations(xmlFiles(listOf("layout")), fontFamilyAllowList) { line ->
            fontFamilyAttr.find(line)?.let { "fontFamily attribute, fonts come from text styles" }
        }
        assertTrue("fontFamily found in layouts:\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test
    fun drawablesUseDimenRadii() {
        val files = xmlFiles(listOf("drawable")).filterNot { it.name.startsWith("ic_launcher") }
        val found = violations(files, emptySet()) { line ->
            rawRadius.find(line)?.takeIf { it.groupValues[3].toDouble() != 0.0 }
                ?.let { "raw radius ${it.value}, use a @dimen/radius* token" }
        }
        assertTrue("Raw radii found in drawables:\n" + found.joinToString("\n"), found.isEmpty())
    }
}
