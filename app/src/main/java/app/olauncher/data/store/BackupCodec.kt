package app.olauncher.data.store

import org.json.JSONArray
import org.json.JSONObject

class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class BackupData(
    val prefs: Map<String, Any?>,
    val files: Map<String, String>,
    val appVersion: String = ""
)

object BackupCodec {
    const val FORMAT = "thumbline-backup"
    const val VERSION = 1

    const val TYPE_BOOLEAN = "b"
    const val TYPE_INT = "i"
    const val TYPE_LONG = "l"
    const val TYPE_FLOAT = "f"
    const val TYPE_STRING = "s"
    const val TYPE_STRING_SET = "ss"

    fun encode(prefs: Map<String, Any?>, files: Map<String, String>, appVersion: String): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)
        root.put("appVersion", appVersion)

        val prefsObj = JSONObject()
        for (key in prefs.keys.sorted()) {
            val value = prefs[key] ?: continue
            val entry = JSONObject()
            when (value) {
                is Boolean -> {
                    entry.put("t", TYPE_BOOLEAN)
                    entry.put("v", value)
                }
                is Int -> {
                    entry.put("t", TYPE_INT)
                    entry.put("v", value)
                }
                is Long -> {
                    entry.put("t", TYPE_LONG)
                    entry.put("v", value)
                }
                is Float -> {
                    entry.put("t", TYPE_FLOAT)
                    entry.put("v", value.toDouble())
                }
                is Double -> {
                    entry.put("t", TYPE_FLOAT)
                    entry.put("v", value)
                }
                is String -> {
                    entry.put("t", TYPE_STRING)
                    entry.put("v", value)
                }
                is Set<*> -> {
                    entry.put("t", TYPE_STRING_SET)
                    val arr = JSONArray()
                    val sortedItems = value.mapNotNull { it?.toString() }.sorted()
                    for (item in sortedItems) {
                        arr.put(item)
                    }
                    entry.put("v", arr)
                }
                else -> {
                    throw IllegalArgumentException("Unsupported pref value type for key '$key': ${value::class.java}")
                }
            }
            prefsObj.put(key, entry)
        }
        root.put("prefs", prefsObj)

        val filesObj = JSONObject()
        for (fileName in files.keys.sorted()) {
            val content = files[fileName] ?: continue
            filesObj.put(fileName, content)
        }
        root.put("files", filesObj)

        return root.toString(2)
    }

    fun decode(raw: String): BackupData {
        if (raw.isBlank()) {
            throw BackupFormatException("Input backup JSON is blank")
        }
        val root = try {
            JSONObject(raw)
        } catch (e: Exception) {
            throw BackupFormatException("Malformed JSON: ${e.message}", e)
        }

        val format = try {
            root.getString("format")
        } catch (e: Exception) {
            throw BackupFormatException("Missing 'format' field", e)
        }
        if (format != FORMAT) {
            throw BackupFormatException("Unsupported backup format: $format")
        }

        if (!root.has("version") || root.isNull("version")) {
            throw BackupFormatException("Missing 'version' field")
        }
        val version = try {
            root.getInt("version")
        } catch (e: Exception) {
            throw BackupFormatException("Invalid 'version' field", e)
        }
        if (version != VERSION) {
            throw BackupFormatException("Unsupported backup version: $version")
        }

        val appVersion = root.optString("appVersion", "")

        val prefsObj = root.optJSONObject("prefs")
            ?: throw BackupFormatException("Missing or invalid 'prefs' object")

        val decodedPrefs = mutableMapOf<String, Any?>()
        val prefsKeys = prefsObj.keys()
        while (prefsKeys.hasNext()) {
            val key = prefsKeys.next()
            val entry = prefsObj.optJSONObject(key)
                ?: throw BackupFormatException("Pref entry '$key' is not an object")
            if (!entry.has("t") || !entry.has("v") || entry.isNull("t") || entry.isNull("v")) {
                throw BackupFormatException("Pref entry '$key' missing valid 't' or 'v'")
            }
            val type = entry.optString("t", "")
            val value: Any = try {
                when (type) {
                    TYPE_BOOLEAN -> entry.getBoolean("v")
                    TYPE_INT -> entry.getInt("v")
                    TYPE_LONG -> entry.getLong("v")
                    TYPE_FLOAT -> entry.getDouble("v").toFloat()
                    TYPE_STRING -> entry.getString("v")
                    TYPE_STRING_SET -> {
                        val arr = entry.getJSONArray("v")
                        val set = LinkedHashSet<String>()
                        for (i in 0 until arr.length()) {
                            set.add(arr.getString(i))
                        }
                        set
                    }
                    else -> throw BackupFormatException("Unknown pref type '$type' for key '$key'")
                }
            } catch (e: BackupFormatException) {
                throw e
            } catch (e: Exception) {
                throw BackupFormatException("Failed to parse value for key '$key' with type '$type'", e)
            }
            decodedPrefs[key] = value
        }

        val filesObj = root.optJSONObject("files")
            ?: throw BackupFormatException("Missing or invalid 'files' object")

        val decodedFiles = mutableMapOf<String, String>()
        val filesKeys = filesObj.keys()
        while (filesKeys.hasNext()) {
            val fileName = filesKeys.next()
            val content = try {
                filesObj.getString(fileName)
            } catch (e: Exception) {
                throw BackupFormatException("Invalid content for file '$fileName'", e)
            }
            decodedFiles[fileName] = content
        }

        return BackupData(
            prefs = decodedPrefs,
            files = decodedFiles,
            appVersion = appVersion
        )
    }
}
