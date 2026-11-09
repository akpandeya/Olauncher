package app.olauncher.data.store

import android.content.Context
import org.json.JSONException
import org.json.JSONObject

/**
 * The German web credential a pairing produced: the device token
 * (`gfd_…`, only ever used on `/api/german` routes), the device id and the
 * username it belongs to. The token is a secret: it lives only in the
 * encrypted payload behind [GermanDeviceCredentialStore], never in prefs,
 * never in a backup, never in code.
 */
data class GermanDeviceCredential(
    val token: String,
    val deviceId: String,
    val username: String,
)

/**
 * Packs one [GermanDeviceCredential] into the JSON payload of the encrypted
 * credential file (Context-free, unit-tested; encryption is [KeyVault]'s job).
 * [decode] rejects partial or unsupported input instead of producing a partial
 * credential: any missing or blank field, or a token without the `gfd_` prefix,
 * gives null.
 */
object GermanDeviceCredentialCodec {

    fun encode(credential: GermanDeviceCredential): String = JSONObject()
        .put("token", credential.token)
        .put("deviceId", credential.deviceId)
        .put("username", credential.username)
        .toString()

    fun decode(json: String): GermanDeviceCredential? {
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            return null
        }
        val token = root.optString("token")
        val deviceId = root.optString("deviceId")
        val username = root.optString("username")
        if (!token.startsWith(GermanWebApi.TOKEN_PREFIX)) return null
        if (deviceId.isBlank() || username.isBlank()) return null
        return GermanDeviceCredential(token = token, deviceId = deviceId, username = username)
    }
}

/**
 * The storage seam behind [GermanWebSession]: the Android-backed
 * [GermanDeviceCredentialStore] plus a fake in unit tests.
 */
interface GermanDeviceCredentialStorage {
    fun save(credential: GermanDeviceCredential)

    /** Null when nothing usable is stored; never throws. */
    fun read(): GermanDeviceCredential?

    /** File presence only — a cheap check for UI state, no decrypt. */
    fun isSet(): Boolean

    fun clear()
}

/**
 * Stores the German web device credential under `noBackupFilesDir`: the JSON
 * payload of [GermanDeviceCredentialCodec], encrypted by a [KeyVault] with its
 * own `AndroidKeyStore` alias and file (pattern of the AI key, issue #599).
 * [read] returns null and never throws when the file is missing, corrupt or
 * undecodable — a stale file can never yield a half-parsed credential.
 */
class GermanDeviceCredentialStore(context: Context) : GermanDeviceCredentialStorage {

    private val vault = KeyVault(context, KEY_ALIAS, FILE_NAME)

    override fun save(credential: GermanDeviceCredential) {
        vault.save(GermanDeviceCredentialCodec.encode(credential))
    }

    override fun read(): GermanDeviceCredential? = try {
        vault.read()?.let(GermanDeviceCredentialCodec::decode)
    } catch (e: Exception) {
        null
    }

    override fun isSet(): Boolean = vault.isSet()

    override fun clear() = vault.clear()

    companion object {
        const val KEY_ALIAS = "thumbline_german_device_token"
        const val FILE_NAME = "german_device_token.bin"
    }
}
