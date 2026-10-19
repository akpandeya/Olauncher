package app.olauncher.helper

import app.olauncher.data.AppModel
import org.json.JSONArray
import org.json.JSONObject

data class RecentApp(
    val packageName: String,
    val activityClassName: String? = null,
    val user: String,
) {
    val appPackage: String get() = packageName
    val activity: String? get() = activityClassName

    constructor(packageName: String, user: String) : this(packageName, null, user)
}

object RecentApps {

    fun push(
        list: List<RecentApp>,
        app: RecentApp,
        max: Int = 3,
    ): List<RecentApp> {
        if (max <= 0) return emptyList()
        if (app.packageName.isBlank() || app.user.isBlank()) {
            return list.take(max)
        }
        val filtered = list.filterNot {
            it.packageName == app.packageName && it.user == app.user
        }
        return (listOf(app) + filtered).take(max)
    }

    fun encode(list: List<RecentApp>): String {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject()
            obj.put("package", item.packageName)
            if (!item.activityClassName.isNullOrBlank()) {
                obj.put("activity", item.activityClassName)
            }
            obj.put("user", item.user)
            array.put(obj)
        }
        return array.toString()
    }

    fun decode(raw: String?): List<RecentApp> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            val result = mutableListOf<RecentApp>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val pkg = when {
                    obj.has("package") -> obj.optString("package")
                    obj.has("packageName") -> obj.optString("packageName")
                    else -> null
                }?.trim()
                if (pkg.isNullOrEmpty()) continue

                val user = when {
                    obj.has("user") -> obj.optString("user")
                    else -> null
                }?.trim()
                if (user.isNullOrEmpty()) continue

                val activity = when {
                    obj.has("activity") && !obj.isNull("activity") -> obj.optString("activity")
                    obj.has("activityClassName") && !obj.isNull("activityClassName") -> obj.optString("activityClassName")
                    else -> null
                }?.trim()?.takeIf { it.isNotEmpty() }

                result.add(RecentApp(packageName = pkg, activityClassName = activity, user = user))
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun forEmptySearch(
        appsList: List<AppModel>,
        recentApps: List<AppModel>,
        recentCaption: String,
        bottomSearch: Boolean,
    ): List<AppModel> {
        if (recentApps.isEmpty()) {
            return DrawerOrdering.forSearchPosition(appsList, bottomSearch)
        }

        val header = AppModel.RecentHeader(recentCaption)
        return if (bottomSearch) {
            val reversedApps = appsList.reversed()
            val recents = listOf(header) + recentApps.reversed()
            reversedApps + recents
        } else {
            val recents = listOf(header) + recentApps
            recents + appsList
        }
    }
}
