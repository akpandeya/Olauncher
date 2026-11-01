package app.olauncher.data.store

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import app.olauncher.BuildConfig
import app.olauncher.data.Prefs
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate

object Backup {
    const val PREFS_NAME = "app.olauncher"

    val VOLATILE_KEYS = setOf(
        "SCREEN_TIME_LAST_UPDATED",
        "LAUNCHER_RECREATE_TIMESTAMP",
        "AUTO_BACKUP_LAST_OK",
        "AUTO_BACKUP_LAST_FAILED",
        "AUTO_BACKUP_ENABLED",
        "AUTO_BACKUP_TREE_URI",
        // Widget instance IDs and their sizes only exist on the backing home screen; restoring
        // them elsewhere binds dead IDs ("This widget is unavailable").
        "TODAY_WIDGET_IDS",
        "TODAY_PENDING_WIDGET_ID",
        "TODAY_WIDGET_HEIGHTS",
        // News daily fetch counter: ephemeral runtime state, never reset or lowered on restore.
        Prefs.NEWS_FETCH_DAY,
        Prefs.NEWS_FETCH_COUNT
    )
    const val OWN_OPENS_PREFIX = "OWN_OPENS_"

    const val FILE_SAVED_ITEMS = "saved_items.json"
    const val FILE_WORD_PROGRESS = "word_progress.json"
    const val FILE_WORDS = "words.json"
    const val FILE_USAGE_DAYS = "usage_days.json"
    const val FILE_INTENTION_HISTORY = "intention_history.json"
    const val FILE_REVIEW_EVENTS = "review_events.json"
    const val FILE_PRACTICE_ITEMS = "practice_items.json"

    // Files in filesDir that are deliberately NOT exported:
    // - weather.json (WeatherCache) and rates.json (RatesCache): refetched from the network.
    // - held.json (HeldStore): short-lived queue of held notifications; stale after a restore.
    // - wallpapers/ (PhotoWallpaper): large binary images, not JSON data.
    // - wiktionary_cache.json / wiktionary suggest cache live in cacheDir and are pure caches.
    // Health Connect data is read live and never stored.
    val EXPORTED_FILES = listOf(
        FILE_SAVED_ITEMS,
        FILE_WORD_PROGRESS,
        FILE_WORDS,
        FILE_USAGE_DAYS,
        FILE_INTENTION_HISTORY,
        FILE_REVIEW_EVENTS,
        // AI-generated practice items (#596): user data that costs money to regenerate.
        FILE_PRACTICE_ITEMS
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

    /** Imported values without volatile keys, plus the device's current volatile values. */
    fun mergeKeepingVolatile(current: Map<String, *>, imported: Map<String, Any?>): Map<String, Any?> {
        val merged = imported.filterKeys { it !in VOLATILE_KEYS }.toMutableMap()
        for ((key, value) in current) {
            if (key in VOLATILE_KEYS) merged[key] = value
        }
        mergeNewsFetchCounter(current, imported, merged)
        return merged
    }

    internal fun mergeNewsFetchCounter(
        current: Map<String, *>,
        imported: Map<String, Any?>,
        merged: MutableMap<String, Any?>
    ) {
        val currentDay = (current[Prefs.NEWS_FETCH_DAY] as? Number)?.toLong()
        val currentCount = (current[Prefs.NEWS_FETCH_COUNT] as? Number)?.toInt() ?: 0

        val importedDay = (imported[Prefs.NEWS_FETCH_DAY] as? Number)?.toLong()
        val importedCount = (imported[Prefs.NEWS_FETCH_COUNT] as? Number)?.toInt() ?: 0

        // The device's own day wins: adopting an imported day that differs from it (even a
        // later or future one) would make today's count read as 0 and bypass the daily cap.
        val (bestDay, bestCount) = when {
            currentDay != null && importedDay == currentDay -> currentDay to maxOf(currentCount, importedCount)
            currentDay != null -> currentDay to currentCount
            importedDay != null -> importedDay to importedCount
            else -> {
                merged.remove(Prefs.NEWS_FETCH_DAY)
                merged.remove(Prefs.NEWS_FETCH_COUNT)
                return
            }
        }

        merged[Prefs.NEWS_FETCH_DAY] = bestDay
        merged[Prefs.NEWS_FETCH_COUNT] = bestCount
    }

    fun restorePrefs(prefs: SharedPreferences, importedMap: Map<String, Any?>) {
        val prefsMap = mergeKeepingVolatile(prefs.all, importedMap)
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
