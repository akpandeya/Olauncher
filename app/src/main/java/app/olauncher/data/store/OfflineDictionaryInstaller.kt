package app.olauncher.data.store

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

object OfflineDictionaryInstaller {
    @Synchronized
    fun install(directory: File, sha256: String, openAsset: () -> InputStream): File {
        require(sha256.matches(Regex("[0-9a-f]{64}")))
        val destination = File(directory, "german-$sha256.sqlite")
        if (destination.isFile) return destination
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create dictionary directory")
        val staging = File(directory, "${destination.name}.partial")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            openAsset().use { asset ->
                GZIPInputStream(asset).use { input ->
                    staging.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                        }
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (actual != sha256) throw IOException("Dictionary checksum mismatch")
            if (!staging.renameTo(destination)) throw IOException("Cannot install dictionary")
            directory.listFiles()?.filter {
                it.name.startsWith("german-") && it.extension == "sqlite" && it != destination
            }?.forEach { it.delete() }
            return destination
        } finally {
            staging.delete()
        }
    }
}
