package app.olauncher.data.store

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import app.olauncher.BuildConfig
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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
        "word_progress.json",
        "words.json",
        "usage_days.json"
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

    fun readBackup(context: Context, uri: Uri): BackupData {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open input stream for URI: $uri")
        val rawJson = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return BackupCodec.decode(rawJson)
    }

    fun restore(
        context: Context,
        backupData: BackupData,
        filesDir: File = context.filesDir,
        prefsName: String = PREFS_NAME,
    ) {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        restorePrefs(prefs, backupData.prefs)
        writeFiles(filesDir, backupData.files)
    }

    fun restorePrefs(prefs: SharedPreferences, prefsMap: Map<String, Any?>) {
        val editor = prefs.edit().clear()
        for ((key, value) in prefsMap) {
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Double -> editor.putFloat(key, value.toFloat())
                is String -> editor.putString(key, value)
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    val stringSet = (value as? Set<String>) ?: value.mapNotNull { it?.toString() }.toSet()
                    editor.putStringSet(key, stringSet)
                }
            }
        }
        val committed = editor.commit()
        if (!committed) {
            throw IOException("Failed to commit SharedPreferences")
        }
    }

    fun writeFiles(filesDir: File, files: Map<String, String>) {
        if (!filesDir.exists()) {
            filesDir.mkdirs()
        }
        for (fileName in EXPORTED_FILES) {
            if (!files.containsKey(fileName)) {
                val file = File(filesDir, fileName)
                if (file.exists()) {
                    file.delete()
                }
            }
        }
        for ((fileName, content) in files) {
            val file = File(filesDir, fileName)
            if (file.canonicalFile.parentFile?.canonicalPath != filesDir.canonicalPath) {
                continue
            }
            writeFileAtomically(file, content)
        }
    }

    fun writeFileAtomically(file: File, content: String) {
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        val tempFile = File(file.parentFile, "${file.name}.tmp")
        tempFile.writeText(content, Charsets.UTF_8)
        try {
            Files.move(
                tempFile.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Exception) {
            if (!tempFile.renameTo(file)) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }
        }
    }
}
