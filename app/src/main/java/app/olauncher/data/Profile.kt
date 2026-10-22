package app.olauncher.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** One home app slot of a profile. */
data class HomeApp(
    val name: String,
    val packageName: String,
    val activityClassName: String?,
    val user: String,
    val isShortcut: Boolean = false,
    val shortcutId: String = "",
)

data class Profile(
    val id: String,
    val name: String,
    val homeApps: List<HomeApp>,
    val settings: ProfileSettings,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("homeApps", JSONArray().also { arr ->
            homeApps.forEach { app ->
                arr.put(JSONObject().apply {
                    put("name", app.name)
                    put("package", app.packageName)
                    app.activityClassName?.let { put("activity", it) }
                    put("user", app.user)
                    put("isShortcut", app.isShortcut)
                    put("shortcutId", app.shortcutId)
                })
            }
        })
        put("settings", settings.toJson())
    }

    companion object {
        private val FIELDS = setOf("id", "name", "homeApps", "settings")

        /** Strict: unknown or missing fields throw, and nothing is returned. */
        fun fromJson(json: JSONObject): Profile {
            val unknown = json.keys().asSequence().toSet() - FIELDS
            if (unknown.isNotEmpty()) throw ProfileFormatException("Unknown profile field: ${unknown.sorted().joinToString()}")
            try {
                val arr = json.getJSONArray("homeApps")
                val apps = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    HomeApp(
                        name = o.getString("name"),
                        packageName = o.getString("package"),
                        activityClassName = if (o.has("activity")) o.getString("activity") else null,
                        user = o.getString("user"),
                        isShortcut = o.getBoolean("isShortcut"),
                        shortcutId = o.getString("shortcutId"),
                    )
                }
                return Profile(
                    id = json.getString("id"),
                    name = json.getString("name"),
                    homeApps = apps,
                    settings = ProfileSettings.fromJson(json.getJSONObject("settings")),
                )
            } catch (e: JSONException) {
                throw ProfileFormatException("Bad profile", e)
            }
        }

        fun listToJson(profiles: List<Profile>): String =
            JSONArray().also { arr -> profiles.forEach { arr.put(it.toJson()) } }.toString()

        /** All or nothing: one bad profile rejects the whole list. */
        fun listFromJson(text: String): List<Profile> {
            if (text.isBlank()) return emptyList()
            try {
                val arr = JSONArray(text)
                return (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            } catch (e: JSONException) {
                throw ProfileFormatException("Bad profile list", e)
            }
        }
    }
}
