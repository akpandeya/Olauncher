package app.olauncher.data.store

import android.content.Context
import android.net.Uri
import app.olauncher.BuildConfig
import java.io.File
import java.io.IOException
import java.time.LocalDate

object Backup {
    const val PREFS_NAME = "app.olauncher"

    val VOLATILE_KEYS = setOf(
        "SCREEN_TIME_LAST_UPDATED",
        "LAUNCHER_RECREATE_TIMESTAMP"
    )
    const val OWN_OPENS_PREFIX = "OWN_OPENS_"

    val EXPORTED_FILES = listOf(
        "saved_items.json",
        "word_progress.json"
    )

    fun isVolatileKey(key: String): Boolean {
        return key in VOLATILE_KEYS || key.startsWith(OWN_OPENS_PREFIX)
    }

    fun filterPrefs(allPrefs: Map<String, *>): Map<String, Any?> {
        return allPrefs.filterKeys { !isVolatileKey(it) }
    }

    fun readFiles(filesDir: File): Map<String, String> {
        val filesMap = mutableMapOf<String, String>()
        for (fileName in EXPORTED_FILES) {
            val file = File(filesDir, fileName)
            if (file.exists() && file.isFile) {
                filesMap[fileName] = file.readText(Charsets.UTF_8)
            }
        }
        return filesMap
    }

    fun defaultBackupFileName(date: LocalDate = LocalDate.now()): String {
        return "thumbline-backup-$date.json"
    }

    fun export(context: Context, uri: Uri, filesDir: File = context.filesDir) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val filteredPrefs = filterPrefs(prefs.all)
        val files = readFiles(filesDir)

        val jsonString = BackupCodec.encode(
            prefs = filteredPrefs,
            files = files,
            appVersion = BuildConfig.VERSION_NAME
        )

        val outputStream = context.contentResolver.openOutputStream(uri)
            ?: throw IOException("Cannot open output stream for URI: $uri")

        outputStream.use { stream ->
            stream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(jsonString)
                writer.flush()
            }
        }
    }
}
