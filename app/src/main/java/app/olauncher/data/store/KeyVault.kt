package app.olauncher.data.store

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.AEADBadTagException
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
 * Keystore key is gone (for example on a new device). The stored file is deleted
 * only when it is provably stale — [KeyVaultReadPolicy]: a missing Keystore alias
 * or an authenticated-decryption failure — never on a temporary Keystore error
 * such as the store not being ready early after boot (review R1 #8).
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
     * A temporary Keystore failure keeps the file: the key may still be valid and
     * the next read can succeed (review R1 #8).
     */
    fun read(): String? {
        val file = file()
        if (!file.exists()) return null
        val payload = try {
            KeyVaultCodec.unpack(file.readBytes())
        } catch (e: Exception) {
            null
        } ?: return deleteStale(file)
        val keyStore = try {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        } catch (e: Exception) {
            // The Keystore is unavailable (for example not ready early after boot):
            // not proof of staleness — keep the file, a later read can succeed.
            return null
        }
        val key = try {
            (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        } catch (e: Exception) {
            null
        }
        if (key == null) {
            // No usable key: only a missing alias proves the file stale. When
            // the alias lookup itself throws the store is still unreachable —
            // unknown, not absent — so the file is kept (review R2 A: the
            // not-ready-after-boot case makes both calls throw).
            val aliasPresent = try {
                keyStore.containsAlias(KEY_ALIAS)
            } catch (e: Exception) {
                null
            }
            return if (KeyVaultReadPolicy.isStale(aliasPresent, decryptError = null)) deleteStale(file) else null
        }
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, payload.iv))
            String(cipher.doFinal(payload.ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            if (KeyVaultReadPolicy.isStale(aliasPresent = true, decryptError = e)) deleteStale(file) else null
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

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
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

/**
 * When [KeyVault.read] may delete the stored file after a failure: only proof
 * that the file can never decrypt again — a missing Keystore alias, or an
 * authenticated decryption failure (wrong key: AEADBadTagException; invalidated
 * key) — makes it stale. Any other error is temporary (a Keystore not ready
 * after boot, an IO failure) and must keep the file (review R1 #8). An alias
 * lookup that itself throws is unknown, never proof of absence (review R2 A):
 * a valid key file must survive a store that is unreachable twice in a row.
 * Kept apart from the Android-dependent vault so the decision is unit-testable.
 */
internal object KeyVaultReadPolicy {
    fun isStale(aliasPresent: Boolean?, decryptError: Exception?): Boolean = when {
        aliasPresent == false -> true
        decryptError is AEADBadTagException || decryptError is KeyPermanentlyInvalidatedException -> true
        else -> false
    }
}
