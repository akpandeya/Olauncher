package app.olauncher.data.store

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the user's API key (issues #599, 9.6; owner decisions in #561): encrypted
 * with an AES-256-GCM key that lives in the `AndroidKeyStore` (encrypt + decrypt,
 * no user authentication, platform APIs only — no security-crypto dependency) and
 * written to `noBackupFilesDir/ai_key.bin`, packed by [KeyVaultCodec]. The key is
 * never in prefs, never in any backup and never in code.
 *
 * [read] returns null and never throws when the file is missing or corrupt or the
 * Keystore key is gone (for example on a new device), and deletes the stale file.
 */
class KeyVault(private val context: Context) {

    /** Encrypts [key] with a fresh IV and stores it at `noBackupFilesDir/ai_key.bin`. */
    fun save(key: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val packed = KeyVaultCodec.pack(cipher.iv, cipher.doFinal(key.toByteArray(Charsets.UTF_8)))
        val dir = context.noBackupFilesDir
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val tempFile = File(dir, "$FILE_NAME.tmp")
        tempFile.writeBytes(packed)
        val file = file()
        if (!tempFile.renameTo(file)) {
            tempFile.copyTo(file, overwrite = true)
            tempFile.delete()
        }
    }

    /**
     * Decrypts the stored key, or null when nothing usable is stored. A file that
     * cannot be unpacked or decrypted — corrupt, truncated, or encrypted with a
     * Keystore key that no longer exists — is stale: it is deleted, never surfaced.
     */
    fun read(): String? {
        val file = file()
        if (!file.exists()) return null
        val payload = try {
            KeyVaultCodec.unpack(file.readBytes())
        } catch (e: Exception) {
            null
        } ?: return deleteStale(file)
        val key = existingKey() ?: return deleteStale(file)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, payload.iv))
            String(cipher.doFinal(payload.ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            deleteStale(file)
        }
    }

    /** Deletes the stored key and the Keystore key behind it. Best effort both ways. */
    fun clear() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
        } catch (e: Exception) {
            // The Keystore key is gone or unreachable; the file still gets deleted.
        }
        file().delete()
    }

    private fun file(): File = File(context.noBackupFilesDir, FILE_NAME)

    /** The stored Keystore key, or null — [read] must never create one. */
    private fun existingKey(): SecretKey? = try {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    } catch (e: Exception) {
        null
    }

    private fun getOrCreateKey(): SecretKey {
        existingKey()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun deleteStale(file: File): String? {
        file.delete()
        return null
    }

    companion object {
        const val KEY_ALIAS = "thumbline_ai_key"
        const val FILE_NAME = "ai_key.bin"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val GCM_TAG_BITS = 128
    }
}
