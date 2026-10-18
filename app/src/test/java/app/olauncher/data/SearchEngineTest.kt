package app.olauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineTest {

    @Test
    fun `engine id constants match expected values`() {
        assertEquals("PHONE_DEFAULT", SearchEngine.PHONE_DEFAULT)
        assertEquals("GOOGLE", SearchEngine.GOOGLE)
        assertEquals("DUCKDUCKGO", SearchEngine.DUCKDUCKGO)
        assertEquals("BING", SearchEngine.BING)
        assertEquals("CUSTOM", SearchEngine.CUSTOM)
    }

    @Test
    fun `prefs keys match constants`() {
        assertEquals("SEARCH_ENGINE", Prefs.SEARCH_ENGINE)
        assertEquals("SEARCH_ENGINE_CUSTOM_URL", Prefs.SEARCH_ENGINE_CUSTOM_URL)
    }

    @Test
    fun `built-in url templates match expected values`() {
        assertEquals("https://www.google.com/search?q=%s", SearchEngine.TEMPLATE_GOOGLE)
        assertEquals("https://duckduckgo.com/?q=%s", SearchEngine.TEMPLATE_DUCKDUCKGO)
        assertEquals("https://www.bing.com/search?q=%s", SearchEngine.TEMPLATE_BING)
    }

    @Test
    fun `isValidTemplate accepts valid templates with http or https and percent s`() {
        assertTrue(SearchEngine.isValidTemplate("https://www.google.com/search?q=%s"))
        assertTrue(SearchEngine.isValidTemplate("http://duckduckgo.com/?q=%s"))
        assertTrue(SearchEngine.isValidTemplate("HTTPS://search.example.org/find?term=%s&lang=de"))
        assertTrue(SearchEngine.isValidTemplate("HTTP://search.example.org/?q=%s"))
        assertTrue(SearchEngine.isValidTemplate("  https://search.example.org/find?term=%s&lang=de  "))
    }

    @Test
    fun `isValidTemplate rejects templates without scheme, wrong scheme, or missing percent s`() {
        assertFalse(SearchEngine.isValidTemplate("https://search.example.org/find")) // missing %s
        assertFalse(SearchEngine.isValidTemplate("ftp://search.example.org/%s")) // wrong scheme
        assertFalse(SearchEngine.isValidTemplate("")) // blank
        assertFalse(SearchEngine.isValidTemplate("   ")) // whitespace
        assertFalse(SearchEngine.isValidTemplate("https://example.com/%S")) // case-sensitive %s
        assertFalse(SearchEngine.isValidTemplate("file:///local/%s"))
        assertFalse(SearchEngine.isValidTemplate("www.google.com/search?q=%s"))
    }

    @Test
    fun `buildUrl replaces percent s with url-encoded query and spaces as percent 20`() {
        val template = "https://duckduckgo.com/?q=%s"
        assertEquals("https://duckduckgo.com/?q=a%20b%26c", SearchEngine.buildUrl(template, "a b&c"))
        assertEquals(
            "https://duckduckgo.com/?q=kotlin%20sealed%26class",
            SearchEngine.buildUrl(template, "kotlin sealed&class")
        )

        val customTemplate = "https://search.example.org/find?term=%s&lang=de"
        assertEquals(
            "https://search.example.org/find?term=kotlin%20sealed%26class&lang=de",
            SearchEngine.buildUrl(customTemplate, "kotlin sealed&class")
        )
    }

    @Test
    fun `buildUrl replaces every occurrence of percent s`() {
        val template = "https://example.com/%s/item/%s"
        assertEquals(
            "https://example.com/foo%20bar/item/foo%20bar",
            SearchEngine.buildUrl(template, "foo bar")
        )
    }

    @Test
    fun `resolve non-bang query with phone default returns PhoneDefault target`() {
        val query = "kotlin sealed&class"
        val expected = SearchTarget.PhoneDefault("kotlin sealed&class")
        assertEquals(expected, SearchEngine.resolve(SearchEngine.PHONE_DEFAULT, "", query))
        assertEquals(expected, SearchEngine.resolve(SearchEngine.PHONE_DEFAULT, "", "  $query  "))
    }

    @Test
    fun `resolve non-bang query with built-in engines returns Url target`() {
        val query = "kotlin sealed&class"

        assertEquals(
            SearchTarget.Url("https://www.google.com/search?q=kotlin%20sealed%26class"),
            SearchEngine.resolve(SearchEngine.GOOGLE, "", query)
        )
        assertEquals(
            SearchTarget.Url("https://duckduckgo.com/?q=kotlin%20sealed%26class"),
            SearchEngine.resolve(SearchEngine.DUCKDUCKGO, "", query)
        )
        assertEquals(
            SearchTarget.Url("https://www.bing.com/search?q=kotlin%20sealed%26class"),
            SearchEngine.resolve(SearchEngine.BING, "", query)
        )
    }

    @Test
    fun `resolve non-bang query with custom valid template returns Url target`() {
        val customTemplate = "https://search.example.org/find?term=%s&lang=de"
        val query = "kotlin sealed&class"

        assertEquals(
            SearchTarget.Url("https://search.example.org/find?term=kotlin%20sealed%26class&lang=de"),
            SearchEngine.resolve(SearchEngine.CUSTOM, customTemplate, query)
        )
    }

    @Test
    fun `resolve non-bang query with custom invalid template falls back to PhoneDefault`() {
        val query = "kotlin sealed&class"
        val expected = SearchTarget.PhoneDefault("kotlin sealed&class")

        assertEquals(expected, SearchEngine.resolve(SearchEngine.CUSTOM, "https://search.example.org/find", query))
        assertEquals(expected, SearchEngine.resolve(SearchEngine.CUSTOM, "ftp://search.example.org/%s", query))
        assertEquals(expected, SearchEngine.resolve(SearchEngine.CUSTOM, "", query))
        assertEquals(expected, SearchEngine.resolve(SearchEngine.CUSTOM, "   ", query))
    }

    @Test
    fun `resolve non-bang query with unknown or blank id falls back to PhoneDefault`() {
        val query = "kotlin sealed&class"
        val expected = SearchTarget.PhoneDefault("kotlin sealed&class")

        assertEquals(expected, SearchEngine.resolve("UNKNOWN_ENGINE", "", query))
        assertEquals(expected, SearchEngine.resolve("", "", query))
        assertEquals(expected, SearchEngine.resolve("   ", "", query))
    }

    @Test
    fun `resolve bang query with duckduckgo or phone default or unknown id opens duckduckgo with full query`() {
        val bangQuery = "!w kotlin sealed&class"
        val expectedUrl = SearchTarget.Url("https://duckduckgo.com/?q=%21w%20kotlin%20sealed%26class")

        assertEquals(expectedUrl, SearchEngine.resolve(SearchEngine.PHONE_DEFAULT, "", bangQuery))
        assertEquals(expectedUrl, SearchEngine.resolve(SearchEngine.DUCKDUCKGO, "", bangQuery))
        assertEquals(expectedUrl, SearchEngine.resolve("UNKNOWN_ENGINE", "", bangQuery))
        assertEquals(expectedUrl, SearchEngine.resolve("", "", bangQuery))
        assertEquals(expectedUrl, SearchEngine.resolve(SearchEngine.PHONE_DEFAULT, "", "  $bangQuery  "))
    }

    @Test
    fun `resolve bang query with custom invalid template opens duckduckgo with full query`() {
        val bangQuery = "!w kotlin sealed&class"
        val expectedUrl = SearchTarget.Url("https://duckduckgo.com/?q=%21w%20kotlin%20sealed%26class")

        assertEquals(expectedUrl, SearchEngine.resolve(SearchEngine.CUSTOM, "https://search.example.org/find", bangQuery))
        assertEquals(expectedUrl, SearchEngine.resolve(SearchEngine.CUSTOM, "ftp://search.example.org/%s", bangQuery))
        assertEquals(expectedUrl, SearchEngine.resolve(SearchEngine.CUSTOM, "", bangQuery))
        assertEquals(expectedUrl, SearchEngine.resolve(SearchEngine.CUSTOM, "   ", bangQuery))
    }

    @Test
    fun `resolve bang query with other engines strips leading exclamation and trims`() {
        val bangQuery = "!w kotlin sealed&class"

        assertEquals(
            SearchTarget.Url("https://www.google.com/search?q=w%20kotlin%20sealed%26class"),
            SearchEngine.resolve(SearchEngine.GOOGLE, "", bangQuery)
        )
        assertEquals(
            SearchTarget.Url("https://www.bing.com/search?q=w%20kotlin%20sealed%26class"),
            SearchEngine.resolve(SearchEngine.BING, "", bangQuery)
        )

        val customTemplate = "https://search.example.org/find?term=%s&lang=de"
        assertEquals(
            SearchTarget.Url("https://search.example.org/find?term=w%20kotlin%20sealed%26class&lang=de"),
            SearchEngine.resolve(SearchEngine.CUSTOM, customTemplate, bangQuery)
        )

        // Bang with spaces after exclamation
        assertEquals(
            SearchTarget.Url("https://www.google.com/search?q=w%20kotlin%20sealed%26class"),
            SearchEngine.resolve(SearchEngine.GOOGLE, "", "!  w kotlin sealed&class  ")
        )
    }
}
