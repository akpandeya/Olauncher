package app.olauncher.helper

import android.content.Context
import org.json.JSONObject
import java.io.File

class RatesCache(private val filesDir: File) {

    constructor(context: Context) : this(context.filesDir)

    private val file = File(filesDir, "rates.json")

    /** Validates first; a bad response never replaces good rates. Returns false if rejected. */
    fun write(rawJson: String, fetchedAt: Long = System.currentTimeMillis()): Boolean = synchronized(LOCK) {
        if (RatesParser.parse(rawJson, fetchedAt) == null) return false
        return try {
            if (!filesDir.exists()) filesDir.mkdirs()
            val root = JSONObject()
            root.put("fetchedAt", fetchedAt)
            root.put("raw", JSONObject(rawJson))
            val temp = File(filesDir, "rates.json.tmp")
            temp.writeText(root.toString())
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun read(): Rates? = synchronized(LOCK) {
        if (!file.exists()) return null
        return try {
            val root = JSONObject(file.readText())
            RatesParser.parse(root.getJSONObject("raw").toString(), root.optLong("fetchedAt", 0L))
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
