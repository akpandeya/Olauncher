package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class KeyVaultCodecTest {

    private val iv = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
    private val ciphertext = "encrypted-marker-0123456789abcdef".toByteArray(Charsets.UTF_8)

    @Test
    fun `pack and unpack round-trip the iv and ciphertext`() {
        val packed = KeyVaultCodec.pack(iv, ciphertext)
        assertEquals(1 + iv.size + ciphertext.size, packed.size)

        val payload = KeyVaultCodec.unpack(packed)
        assertEquals(iv.toList(), payload?.iv?.toList())
        assertEquals(ciphertext.toList(), payload?.ciphertext?.toList())
    }

    @Test
    fun `pack stamps the current layout version`() {
        val packed = KeyVaultCodec.pack(iv, ciphertext)
        assertEquals(KeyVaultCodec.VERSION, packed[0].toInt())
    }

    @Test
    fun `pack rejects an iv that is not 12 bytes`() {
        assertThrows(IllegalArgumentException::class.java) {
            KeyVaultCodec.pack(byteArrayOf(1, 2, 3), ciphertext)
        }
    }

    @Test
    fun `unpack rejects an empty file`() {
        assertNull(KeyVaultCodec.unpack(ByteArray(0)))
    }

    @Test
    fun `unpack rejects a file cut off inside the header`() {
        val packed = KeyVaultCodec.pack(iv, ciphertext)
        assertNull(KeyVaultCodec.unpack(packed.copyOfRange(0, 8)))
    }

    @Test
    fun `unpack rejects a file cut off inside the ciphertext`() {
        val packed = KeyVaultCodec.pack(iv, ciphertext)
        // Keep the header intact but leave fewer bytes than the 16-byte GCM tag.
        assertNull(KeyVaultCodec.unpack(packed.copyOfRange(0, 1 + iv.size + 8)))
    }

    @Test
    fun `unpack rejects an unknown version`() {
        val packed = KeyVaultCodec.pack(iv, ciphertext)
        packed[0] = 99
        assertNull(KeyVaultCodec.unpack(packed))
    }
}
