package app.olauncher.glance

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class WeatherCache(private val filesDir: File) {

    constructor(context: Context) : this(context.filesDir)

    private val file = File(filesDir, "weather.json")

    // The periodic and the one-time worker each build their own WeatherCache, so the lock and
    // the temp file must be shared across instances, not per instance.
    fun write(rawForecastJson: String, fetchedAt: Long = System.currentTimeMillis()) = synchronized(LOCK) {
        if (!filesDir.exists()) {
            filesDir.mkdirs()
        }
        val tempFile = File(filesDir, "weather.json.tmp")
        val root = JSONObject()
        root.put("fetchedAt", fetchedAt)
        try {
            root.put("raw", JSONObject(rawForecastJson))
        } catch (e: Exception) {
            root.put("raw", rawForecastJson)
        }
        tempFile.writeText(root.toString())

        if (!tempFile.renameTo(file)) {
            try {
                Files.move(
                    tempFile.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (e: Exception) {
                if (file.exists()) file.delete()
                if (!tempFile.renameTo(file)) {
                    tempFile.copyTo(file, overwrite = true)
                    tempFile.delete()
                }
            }
        }
    }

    fun read(): Pair<Forecast, Long>? = synchronized(LOCK) {
        if (!file.exists()) return null
        return try {
            val text = file.readText()
            val root = JSONObject(text)
            val fetchedAt = root.optLong("fetchedAt", 0L)
            val raw = if (root.has("raw")) root.get("raw").toString() else text
            val forecast = WeatherParser.parseForecast(raw)
            if (forecast != null) Pair(forecast, fetchedAt) else null
        } catch (e: Exception) {
            null
        }
    }

    fun clear() = synchronized(LOCK) {
        if (file.exists()) file.delete()
    }

    private companion object {
        val LOCK = Any()
    }
}
