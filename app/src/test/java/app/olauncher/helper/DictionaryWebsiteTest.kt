package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Test

class DictionaryWebsiteTest {
    @Test
    fun `German letters are UTF-8 encoded for every website`() {
        assertUrls("Straße", "Stra%C3%9Fe")
        assertUrls("Mädchen", "M%C3%A4dchen")
    }

    @Test
    fun `spaces are percent encoded and surrounding whitespace is trimmed`() {
        assertUrls("  guten Tag  ", "guten%20Tag")
    }

    @Test
    fun `reserved characters remain part of the word rather than URL structure`() {
        assertUrls("a/b?c&d=+e#f%", "a%2Fb%3Fc%26d%3D%2Be%23f%25")
    }

    private fun assertUrls(word: String, encoded: String) {
        assertEquals("https://www.verbformen.de/?w=$encoded", DictionaryWebsite.VERBFORMEN.buildUrl(word))
        assertEquals("https://www.dwds.de/wb/$encoded", DictionaryWebsite.DWDS.buildUrl(word))
        assertEquals("https://www.duden.de/suchen/dudenonline/$encoded", DictionaryWebsite.DUDEN.buildUrl(word))
        assertEquals("https://en.wiktionary.org/wiki/$encoded", DictionaryWebsite.WIKTIONARY.buildUrl(word))
    }
}
