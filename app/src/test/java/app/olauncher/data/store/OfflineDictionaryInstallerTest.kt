package app.olauncher.data.store

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.GZIPOutputStream

class OfflineDictionaryInstallerTest {
    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = File("build/dictionary-tests/${UUID.randomUUID()}")
        check(directory.mkdirs())
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun hash(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun gzip(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(bytes) }
        return output.toByteArray()
    }

    @Test
    fun `installs and reuses verified database without reopening asset`() {
        val bytes = "test dictionary contents".toByteArray()
        val digest = hash(bytes)
        val file = OfflineDictionaryInstaller.install(directory, digest) { ByteArrayInputStream(gzip(bytes)) }
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(file, OfflineDictionaryInstaller.install(directory, digest) {
            error("Installed asset should not be unpacked a second time")
        })
    }

    @Test
    fun `checksum mismatch and truncated gzip never leave a partial database`() {
        val bytes = "complete dictionary".toByteArray()
        val digest = hash(bytes)
        for (corrupt in listOf(
            "not gzip".toByteArray(), gzip("wrong data".toByteArray()), gzip(bytes).dropLast(4).toByteArray()
        )) {
            assertThrows(IOException::class.java) {
                OfflineDictionaryInstaller.install(directory, digest) { ByteArrayInputStream(corrupt) }
            }
            assertEquals(emptyList<File>(), directory.listFiles()?.toList())
        }
    }

    @Test
    fun `new content replaces obsolete installed dictionary only after verification`() {
        val old = "old dictionary".toByteArray()
        val oldFile = OfflineDictionaryInstaller.install(directory, hash(old)) { ByteArrayInputStream(gzip(old)) }
        val updated = "new dictionary".toByteArray()
        assertThrows(IOException::class.java) {
            OfflineDictionaryInstaller.install(directory, hash(updated)) { ByteArrayInputStream(gzip(old)) }
        }
        assertArrayEquals(old, oldFile.readBytes())
        val newFile = OfflineDictionaryInstaller.install(directory, hash(updated)) {
            ByteArrayInputStream(gzip(updated))
        }
        assertArrayEquals(updated, newFile.readBytes())
        assertFalse(oldFile.exists())
    }

    @Test
    fun `shipped compressed asset installs with its published database hash`() {
        val asset = listOf(
            File("src/main/assets/german-v2.sqlite.gzip"),
            File("app/src/main/assets/german-v2.sqlite.gzip")
        ).first { it.isFile }
        val metadata = JSONObject(File(asset.parentFile, "german-v2.sqlite.json").readText())
        val installed = OfflineDictionaryInstaller.install(directory, metadata.getString("database_sha256")) {
            asset.inputStream()
        }
        assertEquals(metadata.getLong("database_bytes"), installed.length())
        assertEquals(metadata.getString("database_sha256"), hash(installed.readBytes()))
    }
}
