package app.olauncher.data.store

import java.io.IOException
import javax.crypto.AEADBadTagException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When [KeyVault.read] may delete the stored key file (review R1 #8): only proof
 * that the file can never decrypt again — a missing Keystore alias, or an
 * authenticated decryption failure — makes it stale. Any temporary Keystore
 * error must keep the file, or a valid key would be destroyed on the first
 * not-ready-after-boot read.
 */
class KeyVaultReadPolicyTest {

    @Test
    fun `a missing keystore alias makes the file stale`() {
        assertTrue(KeyVaultReadPolicy.isStale(aliasPresent = false, decryptError = null))
    }

    @Test
    fun `an authenticated decryption failure makes the file stale`() {
        // Wrong key: GCM tag mismatch — the file can never decrypt again.
        assertTrue(KeyVaultReadPolicy.isStale(aliasPresent = true, decryptError = AEADBadTagException()))
    }

    @Test
    fun `a temporary keystore error keeps the file`() {
        // e.g. the Keystore is not ready early after boot: not proof of staleness.
        assertFalse(KeyVaultReadPolicy.isStale(aliasPresent = true, decryptError = IOException("keystore unavailable")))
        assertFalse(KeyVaultReadPolicy.isStale(aliasPresent = true, decryptError = null))
    }
}
