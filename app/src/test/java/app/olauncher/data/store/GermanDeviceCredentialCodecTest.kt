package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class GermanDeviceCredentialCodecTest {

    @Test
    fun `encode and decode round-trip the credential`() {
        val credential = GermanDeviceCredential(token = TEST_TOKEN, deviceId = DEVICE_ID, username = USERNAME)
        val decoded = GermanDeviceCredentialCodec.decode(GermanDeviceCredentialCodec.encode(credential))
        assertEquals(credential, decoded)
    }

    @Test
    fun `decode rejects each partial payload`() {
        listOf(
            payload(token = null, deviceId = DEVICE_ID, username = USERNAME),
            payload(token = "", deviceId = DEVICE_ID, username = USERNAME),
            payload(token = TEST_TOKEN, deviceId = null, username = USERNAME),
            payload(token = TEST_TOKEN, deviceId = " ", username = USERNAME),
            payload(token = TEST_TOKEN, deviceId = DEVICE_ID, username = null),
            payload(token = TEST_TOKEN, deviceId = DEVICE_ID, username = "\t"),
        ).forEach { payload ->
            assertNull("payload '$payload' must not decode", GermanDeviceCredentialCodec.decode(payload))
        }
    }

    @Test
    fun `decode rejects a token without the gfd prefix`() {
        assertNull(GermanDeviceCredentialCodec.decode(payload("plain_token", DEVICE_ID, USERNAME)))
    }

    @Test
    fun `decode rejects non-object JSON`() {
        assertNull(GermanDeviceCredentialCodec.decode("[]"))
        assertNull(GermanDeviceCredentialCodec.decode("12"))
        assertNull(GermanDeviceCredentialCodec.decode("not json"))
        assertNull(GermanDeviceCredentialCodec.decode(""))
    }

    @Test
    fun `toString redacts the token but keeps device and user`() {
        val text = GermanDeviceCredential(TEST_TOKEN, DEVICE_ID, USERNAME).toString()
        assertFalse(text.contains(TEST_TOKEN))
        assertTrue(text.contains(DEVICE_ID))
        assertTrue(text.contains(USERNAME))
    }

    private fun payload(token: String?, deviceId: String?, username: String?): String {
        val json = JSONObject()
        token?.let { json.put("token", it) }
        deviceId?.let { json.put("deviceId", it) }
        username?.let { json.put("username", it) }
        return json.toString()
    }

    companion object {
        /** Obviously synthetic, with the required prefix; never a real device token. */
        private const val TEST_TOKEN = "gfd_test_token_0001"
        private const val DEVICE_ID = "dev-42"
        private const val USERNAME = "ola-test-user-7"
    }
}
