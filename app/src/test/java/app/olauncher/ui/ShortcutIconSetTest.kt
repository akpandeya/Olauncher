package app.olauncher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutIconSetTest {

    private val names = listOf("search", "gallery", "camera", "messages", "phone", "more")

    private fun read(name: String): String {
        val rel = "src/main/res/drawable/ic_shortcut_$name.xml"
        val file = listOf(File(rel), File("app/$rel")).first { it.exists() }
        return file.readText()
    }

    @Test
    fun `every built-in shortcut icon is an outline with the same stroke and viewport`() {
        for (name in names) {
            val xml = read(name)
            assertTrue("$name must have a no fill", !xml.contains("fillColor"))
            assertTrue("$name must be stroked with primaryColor", xml.contains("android:strokeColor=\"?attr/primaryColor\""))
            assertTrue("$name stroke width", xml.contains("android:strokeWidth=\"1.75\""))
            assertTrue("$name viewport", xml.contains("android:viewportWidth=\"24\"") && xml.contains("android:viewportHeight=\"24\""))
        }
        assertEquals(6, names.size)
    }
}
