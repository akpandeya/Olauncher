package app.olauncher.data.store

import app.olauncher.data.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiProviderConfigTest {

    private val keySource = { "sentinel-key" }

    @Test
    fun `unknown provider id has no adapter`() {
        for (providerId in listOf("openai", "anthropic", "bogus", "")) {
            val config = AiProviderConfig(providerId, "https://api.melious.ai", "glm-5.3-flash")
            assertNull("providerId '$providerId'", AiProviders.create(config, keySource))
        }
    }

    @Test
    fun `the default provider id is melious`() {
        assertEquals("melious", AiProviders.MELIOUS)
    }

    @Test
    fun `base url must start with https`() {
        assertNull(
            AiProviderConfig("melious", "https://api.melious.ai", "glm-5.3-flash").validate(),
        )
        assertEquals(
            "Base URL must start with https://",
            AiProviderConfig("melious", "http://api.melious.ai", "glm-5.3-flash").validate(),
        )
        assertEquals(
            "Base URL must start with https://",
            AiProviderConfig("melious", "", "glm-5.3-flash").validate(),
        )
        assertEquals(
            "Base URL must start with https://",
            AiProviderConfig("melious", "api.melious.ai", "glm-5.3-flash").validate(),
        )
    }

    @Test
    fun `prefs keys and defaults are pinned`() {
        assertEquals("AI_PROVIDER", Prefs.AI_PROVIDER)
        assertEquals("melious", Prefs.AI_PROVIDER_MELIOUS)
        assertEquals("AI_BASE_URL", Prefs.AI_BASE_URL)
        assertEquals("https://api.melious.ai", Prefs.AI_BASE_URL_DEFAULT)
        assertEquals("AI_MODEL", Prefs.AI_MODEL)
    }
}
