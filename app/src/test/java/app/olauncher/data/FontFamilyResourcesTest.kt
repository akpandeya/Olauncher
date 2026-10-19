package app.olauncher.data

import app.olauncher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FontFamilyResourcesTest {

    @Test
    fun `forId returns SYSTEM resource for SYSTEM id`() {
        val result = FontFamilyResources.forId(Constants.FontFamily.SYSTEM)
        assertEquals(Constants.FontFamily.SYSTEM, result.id)
        assertNull(result.fontRes)
        assertNull(result.fontResourceId)
        assertEquals(R.string.system_default, result.labelRes)
        assertEquals(R.string.system_default, result.stringRes)
    }

    @Test
    fun `forId returns INTER resource for INTER id`() {
        val result = FontFamilyResources.forId(Constants.FontFamily.INTER)
        assertEquals(Constants.FontFamily.INTER, result.id)
        assertEquals(R.font.inter, result.fontRes)
        assertEquals(R.font.inter, result.fontResourceId)
        assertEquals(R.string.font_inter, result.labelRes)
        assertEquals(R.string.font_inter, result.stringRes)
    }

    @Test
    fun `forId returns PLEX resource for PLEX id`() {
        val result = FontFamilyResources.forId(Constants.FontFamily.PLEX)
        assertEquals(Constants.FontFamily.PLEX, result.id)
        assertEquals(R.font.ibm_plex_sans, result.fontRes)
        assertEquals(R.font.ibm_plex_sans, result.fontResourceId)
        assertEquals(R.string.font_plex, result.labelRes)
        assertEquals(R.string.font_plex, result.stringRes)
    }

    @Test
    fun `unknown ids map to System`() {
        val unknownIds = listOf(-100, -1, 3, 4, 10, 42, 99, 999, Int.MIN_VALUE, Int.MAX_VALUE)
        for (id in unknownIds) {
            val result = FontFamilyResources.forId(id)
            assertEquals("Id $id must map to SYSTEM", FontFamilyResources.SYSTEM, result)
            assertNull("Id $id fontRes must be null", result.fontRes)
            assertEquals(Constants.FontFamily.SYSTEM, result.id)
            assertEquals(R.string.system_default, result.labelRes)
        }
    }

    @Test
    fun `result is a named FontFamilyResource instance and not a Pair or Triple`() {
        val result: Any = FontFamilyResources.forId(Constants.FontFamily.INTER)
        assertTrue(result is FontFamilyResource)
        assertTrue(result !is Pair<*, *>)
        assertTrue(result !is Triple<*, *, *>)
    }

    @Test
    fun `all font family options are registered in expected order`() {
        val all = FontFamilyResources.all()
        assertEquals(3, all.size)
        assertEquals(FontFamilyResources.SYSTEM, all[0])
        assertEquals(FontFamilyResources.INTER, all[1])
        assertEquals(FontFamilyResources.PLEX, all[2])
    }
}
