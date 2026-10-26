package app.olauncher.ui.words

import app.olauncher.R
import app.olauncher.helper.ReadableScrim
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NounArticleTest {

    private fun resolveFile(relative: String): File {
        return listOf(
            File(relative),
            File("app/$relative"),
        ).firstOrNull { it.isFile } ?: error("File $relative not found from ${File(".").absolutePath}")
    }

    @Test
    fun `NounArticle maps genders to accent colors`() {
        assertEquals(R.color.accentSky, NounArticle.colorResForGender("der"))
        assertEquals(R.color.accentRose, NounArticle.colorResForGender("die"))
        assertEquals(R.color.accentSage, NounArticle.colorResForGender("das"))

        assertEquals(R.color.accentSky, NounArticle.colorResForGender("masculine"))
        assertEquals(R.color.accentRose, NounArticle.colorResForGender("feminine"))
        assertEquals(R.color.accentSage, NounArticle.colorResForGender("neuter"))

        assertEquals(R.color.accentSky, NounArticle.colorResForGender("m"))
        assertEquals(R.color.accentRose, NounArticle.colorResForGender("f"))
        assertEquals(R.color.accentSage, NounArticle.colorResForGender("n"))

        assertNull(NounArticle.colorResForGender(null))
        assertNull(NounArticle.colorResForGender(""))
        assertNull(NounArticle.colorResForGender("verb"))
        assertNull(NounArticle.colorResForGender("der/das"))
    }

    @Test
    fun `extractLeadingArticle identifies leading der, die, das`() {
        assertEquals("der", NounArticle.extractLeadingArticle("der Hund"))
        assertEquals("Die", NounArticle.extractLeadingArticle("Die Katze"))
        assertEquals("DAS", NounArticle.extractLeadingArticle("DAS Haus"))
        assertEquals("die", NounArticle.extractLeadingArticle("  die   Frau  "))

        assertNull(NounArticle.extractLeadingArticle("Hund"))
        assertNull(NounArticle.extractLeadingArticle("Dermatologe"))
        assertNull(NounArticle.extractLeadingArticle("diese Katze"))
        assertNull(NounArticle.extractLeadingArticle("der"))
        assertNull(NounArticle.extractLeadingArticle(""))
    }

    @Test
    fun `stripLeadingArticle removes leading der die das`() {
        assertEquals("Hund", NounArticle.stripLeadingArticle("der Hund"))
        assertEquals("Katze", NounArticle.stripLeadingArticle("Die Katze"))
        assertEquals("Haus", NounArticle.stripLeadingArticle("DAS Haus"))
        assertEquals("Frau", NounArticle.stripLeadingArticle("  die   Frau  "))
        assertEquals("Rückkehr", NounArticle.stripLeadingArticle("Rückkehr"))
        assertEquals("Dermatologe", NounArticle.stripLeadingArticle("Dermatologe"))
        assertEquals("der", NounArticle.stripLeadingArticle("der"))
        assertEquals("", NounArticle.stripLeadingArticle(""))
    }

    @Test
    fun `resolveGender resolves from leading article or dictionary`() {
        assertEquals("der", NounArticle.resolveGender("der Hund"))
        assertEquals("die", NounArticle.resolveGender("Die Katze"))
        assertEquals("das", NounArticle.resolveGender("das Haus"))

        assertEquals("der", NounArticle.resolveGender("Hund", "der"))
        assertEquals("die", NounArticle.resolveGender("Katze", "die"))
        assertEquals("das", NounArticle.resolveGender("Haus", "das"))
        assertEquals("der", NounArticle.resolveGender("Hund", "masculine"))
        assertEquals("die", NounArticle.resolveGender("Katze", "feminine"))
        assertEquals("das", NounArticle.resolveGender("Haus", "neuter"))

        assertNull(NounArticle.resolveGender("laufen", ""))
        assertNull(NounArticle.resolveGender("unbekannt", null))
        assertNull(NounArticle.resolveGender("Joghurt", "der/das"))
    }

    @Test
    fun `format prepends article and sets color when dictionary gender known`() {
        val hund = NounArticle.format("Hund", "der")
        assertEquals("der Hund", hund.displayText)
        assertEquals("der", hund.article)
        assertEquals(0..2, hund.articleRange)
        assertEquals("der", hund.gender)
        assertEquals(R.color.accentSky, hund.colorRes)

        val katze = NounArticle.format("Katze", "die")
        assertEquals("die Katze", katze.displayText)
        assertEquals("die", katze.article)
        assertEquals(0..2, katze.articleRange)
        assertEquals("die", katze.gender)
        assertEquals(R.color.accentRose, katze.colorRes)

        val haus = NounArticle.format("Haus", "das")
        assertEquals("das Haus", haus.displayText)
        assertEquals("das", haus.article)
        assertEquals(0..2, haus.articleRange)
        assertEquals("das", haus.gender)
        assertEquals(R.color.accentSage, haus.colorRes)
    }

    @Test
    fun `format preserves existing article and sets color when leading article present`() {
        val hund = NounArticle.format("der Hund")
        assertEquals("der Hund", hund.displayText)
        assertEquals("der", hund.article)
        assertEquals(0..2, hund.articleRange)
        assertEquals(R.color.accentSky, hund.colorRes)

        val katze = NounArticle.format("Die Katze")
        assertEquals("Die Katze", katze.displayText)
        assertEquals("Die", katze.article)
        assertEquals(0..2, katze.articleRange)
        assertEquals(R.color.accentRose, katze.colorRes)

        // Does not duplicate when both leading article and dictionary gender are present
        val haus = NounArticle.format("das Haus", "das")
        assertEquals("das Haus", haus.displayText)
        assertEquals("das", haus.article)
        assertEquals(0..2, haus.articleRange)
        assertEquals(R.color.accentSage, haus.colorRes)
    }

    @Test
    fun `format leaves unknown gender uncoloured without adding article`() {
        val verb = NounArticle.format("laufen")
        assertEquals("laufen", verb.displayText)
        assertNull(verb.article)
        assertNull(verb.articleRange)
        assertNull(verb.gender)
        assertNull(verb.colorRes)

        val dual = NounArticle.format("Joghurt", "der/das")
        assertEquals("Joghurt", dual.displayText)
        assertNull(dual.article)
        assertNull(dual.colorRes)

        val empty = NounArticle.format("")
        assertEquals("", empty.displayText)
        assertNull(empty.article)
        assertNull(empty.colorRes)
    }

    @Test
    fun `colours meet 4_5 to 1 contrast in light and dark mode`() {
        // Light theme accent colors on white card/panel background
        val lightColors = mapOf(
            "accentSkyLight (der/blue)" to 0xFF2E6585.toInt(),
            "accentRoseLight (die/rose)" to 0xFF82444E.toInt(),
            "accentSageLight (das/sage)" to 0xFF4D6653.toInt(),
        )
        val white = 0xFFFFFFFF.toInt()
        for ((name, color) in lightColors) {
            val c = ReadableScrim.contrast(color, white)
            assertTrue("$name on light background contrast $c must be >= 4.5", c >= 4.5)
        }

        // Dark theme accent colors on black card/panel background
        val darkColors = mapOf(
            "accentSkyDark (der/blue)" to 0xFF96B9D0.toInt(),
            "accentRoseDark (die/rose)" to 0xFFDE9FA7.toInt(),
            "accentSageDark (das/sage)" to 0xFF9CAF88.toInt(),
        )
        val black = 0xFF000000.toInt()
        for ((name, color) in darkColors) {
            val c = ReadableScrim.contrast(color, black)
            assertTrue("$name on dark background contrast $c must be >= 4.5", c >= 4.5)
        }
    }

    @Test
    fun `colors xml defines accent and article colors in light and dark`() {
        val lightXml = resolveFile("src/main/res/values/colors.xml").readText()
        val darkXml = resolveFile("src/main/res/values-night/colors.xml").readText()

        assertTrue(lightXml.contains("""<color name="accentSky">@color/accentSkyLight</color>"""))
        assertTrue(lightXml.contains("""<color name="accentRose">@color/accentRoseLight</color>"""))
        assertTrue(lightXml.contains("""<color name="accentSage">@color/accentSageLight</color>"""))
        assertTrue(lightXml.contains("""<color name="articleDer">@color/accentSky</color>"""))
        assertTrue(lightXml.contains("""<color name="articleDie">@color/accentRose</color>"""))
        assertTrue(lightXml.contains("""<color name="articleDas">@color/accentSage</color>"""))

        assertTrue(darkXml.contains("""<color name="accentSky">@color/accentSkyDark</color>"""))
        assertTrue(darkXml.contains("""<color name="accentRose">@color/accentRoseDark</color>"""))
        assertTrue(darkXml.contains("""<color name="accentSage">@color/accentSageDark</color>"""))
        assertTrue(darkXml.contains("""<color name="articleDer">@color/accentSky</color>"""))
        assertTrue(darkXml.contains("""<color name="articleDie">@color/accentRose</color>"""))
        assertTrue(darkXml.contains("""<color name="articleDas">@color/accentSage</color>"""))
    }
}
