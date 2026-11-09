package app.olauncher.data.store

import android.content.SharedPreferences
import app.olauncher.data.GermanPrefsStore
import app.olauncher.data.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GermanWebSessionTest {

    @Test
    fun `state is signed out with no base url until paired`() {
        val (session, _) = session(FakeCredentialStorage())
        assertEquals(GermanWebSessionState.SignedOut, session.state())
        assertNull(session.baseUrl())
    }

    @Test
    fun `onPaired stores the credential, the normalized base url and clears lapsed`() {
        val storage = FakeCredentialStorage()
        val (session, map) = session(storage)
        map[Prefs.GERMAN_WEB_LAPSED] = true

        session.onPaired(credential(), "Pair.Example.COM/de?x=1")

        assertEquals(GermanWebSessionState.SignedIn(USERNAME, DEVICE_ID), session.state())
        assertEquals(credential(), storage.stored)
        assertEquals("https://pair.example.com", session.baseUrl())
        assertEquals("https://pair.example.com", map[Prefs.GERMAN_WEB_BASE_URL])
        assertEquals(false, map[Prefs.GERMAN_WEB_LAPSED])
    }

    @Test
    fun `onUnauthorized clears the credential and lapses, keeping the base url`() {
        val storage = FakeCredentialStorage()
        val (session, map) = session(storage)
        session.onPaired(credential(), "pair.example.com")

        session.onUnauthorized()

        assertEquals(GermanWebSessionState.Lapsed, session.state())
        assertNull(storage.stored)
        assertEquals(1, storage.clearCalls)
        assertEquals(true, map[Prefs.GERMAN_WEB_LAPSED])
        // The server address stays: pairing again must not need it retyped.
        assertEquals("https://pair.example.com", session.baseUrl())
    }

    @Test
    fun `pairing again after a lapse clears lapsed`() {
        val storage = FakeCredentialStorage()
        val (session, map) = session(storage)
        session.onPaired(credential(), "pair.example.com")
        session.onUnauthorized()

        session.onPaired(renewedCredential(), "pair.example.com")

        assertEquals(GermanWebSessionState.SignedIn(USERNAME_2, DEVICE_ID_2), session.state())
        assertEquals(false, map[Prefs.GERMAN_WEB_LAPSED])
        assertEquals(renewedCredential(), storage.stored)
    }

    @Test
    fun `signOut clears the credential and base url and is not lapsed`() {
        val storage = FakeCredentialStorage()
        val (session, map) = session(storage)
        session.onPaired(credential(), "pair.example.com")

        session.signOut()

        assertEquals(GermanWebSessionState.SignedOut, session.state())
        assertNull(storage.stored)
        assertEquals(1, storage.clearCalls)
        assertNull(session.baseUrl())
        assertEquals("", map[Prefs.GERMAN_WEB_BASE_URL])
        assertEquals(false, map[Prefs.GERMAN_WEB_LAPSED])
    }

    @Test
    fun `stateOf derives each state from the credential and the lapsed flag`() {
        // A valid credential wins over a stale lapsed flag from an older token.
        assertEquals(
            GermanWebSessionState.SignedIn(USERNAME, DEVICE_ID),
            GermanWebSession.stateOf(credential(), lapsed = false),
        )
        assertEquals(
            GermanWebSessionState.SignedIn(USERNAME, DEVICE_ID),
            GermanWebSession.stateOf(credential(), lapsed = true),
        )
        assertEquals(GermanWebSessionState.Lapsed, GermanWebSession.stateOf(null, lapsed = true))
        assertEquals(GermanWebSessionState.SignedOut, GermanWebSession.stateOf(null, lapsed = false))
    }

    /** In-memory stand-in for the Keystore-backed [GermanDeviceCredentialStore]. */
    private class FakeCredentialStorage : GermanDeviceCredentialStorage {
        var stored: GermanDeviceCredential? = null
        var clearCalls: Int = 0
            private set

        override fun save(credential: GermanDeviceCredential) {
            stored = credential
        }

        override fun read(): GermanDeviceCredential? = stored

        override fun isSet(): Boolean = stored != null

        override fun clear() {
            clearCalls++
            stored = null
        }
    }

    private fun session(storage: FakeCredentialStorage): Pair<GermanWebSession, MutableMap<String, Any?>> {
        val map = mutableMapOf<String, Any?>()
        return GermanWebSession(storage, GermanPrefsStore(FakeSharedPreferences(map))) to map
    }

    /** Distinguishing values, so a swapped field cannot pass (synthetic token). */
    private fun credential() = GermanDeviceCredential(TEST_TOKEN, DEVICE_ID, USERNAME)

    private fun renewedCredential() = GermanDeviceCredential(TEST_TOKEN, DEVICE_ID_2, USERNAME_2)

    private class FakeEditor(private val map: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removed = mutableSetOf<String>()
        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values?.toSet() }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { removed += key }
        override fun clear() = apply { map.clear() }
        override fun commit(): Boolean {
            apply()
            return true
        }
        override fun apply() {
            removed.forEach { map.remove(it) }
            map.putAll(pending)
            removed.clear()
            pending.clear()
        }
    }

    private class FakeSharedPreferences(private val map: MutableMap<String, Any?>) : SharedPreferences {
        override fun getAll(): MutableMap<String, *> = HashMap(map)
        override fun getString(key: String, defValue: String?): String? = (map[key] as? String) ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
            (map[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String, defValue: Int): Int = (map[key] as? Int) ?: defValue
        override fun getLong(key: String, defValue: Long): Long = (map[key] as? Long) ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
        override fun contains(key: String): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(map)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    companion object {
        /** Obviously synthetic, with the required prefix; never a real device token. */
        private const val TEST_TOKEN = "gfd_test_token_0001"
        private const val DEVICE_ID = "dev-42"
        private const val USERNAME = "ola-test-user-7"
        private const val DEVICE_ID_2 = "dev-43"
        private const val USERNAME_2 = "ola-test-user-9"
    }
}
