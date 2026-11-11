package app.olauncher.data.store

import android.content.SharedPreferences
import app.olauncher.data.GermanPrefsStore
import app.olauncher.data.Prefs
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "sign in on this device" pairing flow end to end at the Context-free
 * seams (epic #835 lineage, password pairing): login → register with the
 * in-memory session token → [GermanWebSession.onPaired]. The regression at
 * its centre: the password and the session token must never reach any
 * persistence seam — the credential store saves only the device token with
 * its deviceId and username, and prefs only the two pairing keys (lapsed
 * flag, server address).
 */
class GermanWebSignInFlowTest {

    @Test
    fun `the device label comes from one source and keeps the server's 60-char limit`() {
        assertEquals("Thumbline (Pixel 9a)", GermanWebDeviceLabel.forModel("Pixel 9a"))
        assertEquals(60, GermanWebDeviceLabel.forModel("M".repeat(80)).length)
        assertTrue(GermanWebDeviceLabel.forModel("M".repeat(80)).startsWith("Thumbline"))
    }

    @Test
    fun `sign in then register produces the device credential and stores nothing else`() = runBlocking {
        val responses = ArrayDeque(
            listOf(
                // POST /api/auth/login
                SignInResponse(200, """{"token":"$SESSION_TOKEN"}"""),
                // POST /api/auth/devices/register
                SignInResponse(200, SignInResponse.json(DEVICE_TOKEN, DEVICE_ID, USERNAME)),
            ),
        )
        val connections = mutableListOf<FakeSignInConnection>()
        val api = object : GermanWebApi(BASE) {
            override fun openConnection(url: URL): HttpURLConnection =
                FakeSignInConnection(url, responses.removeFirst()).also(connections::add)
        }
        val storage = FakeCredentialStorage()
        val map = mutableMapOf<String, Any?>()
        val session = GermanWebSession(storage, GermanPrefsStore(FakeSharedPreferences(map)))

        // The two network hops, exactly as the dialog drives them.
        val signedIn = api.signIn(USERNAME, PASSWORD) as SignInResult.SignedIn
        val registered = api.registerDevice(
            signedIn.sessionToken,
            GermanWebDeviceLabel.forModel(DEVICE_MODEL),
        ) as PairingResult.Paired
        session.onPaired(
            GermanDeviceCredential(registered.token, registered.deviceId, registered.username),
            BASE,
        )

        // Both hops happened, in order, against the right paths.
        assertEquals(2, connections.size)
        assertEquals("https://pair.example.com/api/auth/login", connections[0].url.toString())
        assertEquals("https://pair.example.com/api/auth/devices/register", connections[1].url.toString())
        assertEquals("Bearer $SESSION_TOKEN", connections[1].getRequestProperty("Authorization"))

        // The device credential is complete and correct.
        assertEquals(
            GermanDeviceCredential(DEVICE_TOKEN, DEVICE_ID, USERNAME),
            storage.stored,
        )
        assertEquals(GermanWebSessionState.SignedIn(USERNAME, DEVICE_ID), session.state())

        // The persisted payload carries the device token — and only that secret.
        val encoded = GermanDeviceCredentialCodec.encode(storage.stored!!)
        assertTrue(encoded, encoded.contains(DEVICE_TOKEN))
        assertEquals(USERNAME, JSONObject(encoded).getString("username"))

        // Prefs hold only the two pairing keys: the lapsed flag and the host.
        assertEquals(setOf(Prefs.GERMAN_WEB_LAPSED, Prefs.GERMAN_WEB_BASE_URL), map.keys)
        assertEquals("https://pair.example.com", map[Prefs.GERMAN_WEB_BASE_URL])

        // The password and the session token reached no persistence seam at all.
        val persisted = encoded + map.values.joinToString("|") + storage.stored.toString()
        assertTrue(persisted, !persisted.contains(PASSWORD))
        assertTrue(persisted, !persisted.contains(SESSION_TOKEN))
    }

    @Test
    fun `a failed register after a successful login stores nothing`() = runBlocking {
        val responses = ArrayDeque(
            listOf(
                SignInResponse(200, """{"token":"$SESSION_TOKEN"}"""),
                SignInResponse(401, """{"error":"session rejected"}"""),
            ),
        )
        val connections = mutableListOf<FakeSignInConnection>()
        val api = object : GermanWebApi(BASE) {
            override fun openConnection(url: URL): HttpURLConnection =
                FakeSignInConnection(url, responses.removeFirst()).also(connections::add)
        }
        val storage = FakeCredentialStorage()
        val map = mutableMapOf<String, Any?>()
        val session = GermanWebSession(storage, GermanPrefsStore(FakeSharedPreferences(map)))

        val signedIn = api.signIn(USERNAME, PASSWORD) as SignInResult.SignedIn
        val registered = api.registerDevice(signedIn.sessionToken, "Thumbline phone")
        // One attempt, no retry: a 401 from register is a ServerError to surface.
        assertEquals(PairingResult.ServerError(401), registered)
        // Nothing was paired, so nothing may be persisted.
        assertEquals(null, storage.stored)
        assertEquals(0, storage.clearCalls)
        assertTrue(map.isEmpty())
    }

/** One canned response per connection; every request opens its own, in order. */
private class FakeSignInConnection(url: URL, private val response: SignInResponse) : HttpURLConnection(url) {

    private val out = ByteArrayOutputStream()
    private var fakeMethod: String = "GET"

    override fun connect() {}

    override fun setRequestMethod(method: String?) {
        fakeMethod = method ?: "GET"
    }

    override fun getRequestMethod(): String = fakeMethod

    override fun usingProxy(): Boolean = false

    override fun disconnect() {}

    override fun getOutputStream(): OutputStream = out

    override fun getResponseCode(): Int = response.status

    override fun getHeaderField(name: String?): String? = null

    override fun getInputStream(): InputStream {
        if (response.status !in 200..399) throw IOException("HTTP ${response.status}")
        return ByteArrayInputStream(response.body.toByteArray(Charsets.UTF_8))
    }

    override fun getErrorStream(): InputStream? =
        if (response.status in 200..399) null
        else ByteArrayInputStream(response.body.toByteArray(Charsets.UTF_8))
}

private data class SignInResponse(val status: Int, val body: String) {
    companion object {
        fun json(token: String, deviceId: String, username: String): String = JSONObject()
            .put("token", token)
            .put("deviceId", deviceId)
            .put("username", username)
            .toString()
    }
}

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

    private class FakeEditor(private val map: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values?.toSet() }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { pending[key] = null }
        override fun clear() = apply { map.clear() }
        override fun commit(): Boolean {
            apply()
            return true
        }
        override fun apply() {
            map.putAll(pending)
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
        private const val BASE = "https://pair.example.com"
        private const val USERNAME = "ola-test-user-7"
        private const val PASSWORD = "s3cret-pass-0001"
        private const val SESSION_TOKEN = "gst_test_session_token_0001"
        private const val DEVICE_TOKEN = "gfd_test_token_0001"
        private const val DEVICE_ID = "dev-42"
        private const val DEVICE_MODEL = "Pixel 9a"
    }
}
