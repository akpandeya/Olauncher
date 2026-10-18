package app.olauncher.wellbeing

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class DaySnapshot(
    val epochDay: Long,
    val distractingMinutes: Int? = null,
    val distractingOpens: Int? = null,
    val topApps: List<Pair<String, Int>> = emptyList(), /* label, minutes, max 3 */
    val overAllowance: Boolean = false,
    val notNow: Int = 0,
    val opened: Int = 0,
    val why: Map<String, Int> = emptyMap(),
    val water: Int = 0,
    val walk: Int = 0,
) {
    init {
        require(topApps.size <= 3) { "topApps size must be at most 3, got ${topApps.size}" }
        distractingMinutes?.let { require(it >= 0) { "distractingMinutes must be non-negative" } }
        distractingOpens?.let { require(it >= 0) { "distractingOpens must be non-negative" } }
        require(notNow >= 0) { "notNow must be non-negative" }
        require(opened >= 0) { "opened must be non-negative" }
        require(water >= 0) { "water must be non-negative" }
        require(walk >= 0) { "walk must be non-negative" }
    }
}

object DaySnapshotCodec {

    fun encode(snapshots: List<DaySnapshot>): String {
        return encodeToJson(snapshots).toString(2)
    }

    fun encodeToJson(snapshots: List<DaySnapshot>): JSONArray {
        val array = JSONArray()
        for (snapshot in snapshots) {
            array.put(encodeSnapshot(snapshot))
        }
        return array
    }

    fun encodeSnapshot(snapshot: DaySnapshot): JSONObject {
        val obj = JSONObject()
        obj.put("epochDay", snapshot.epochDay)
        if (snapshot.distractingMinutes != null) {
            obj.put("distractingMinutes", snapshot.distractingMinutes)
        } else {
            obj.put("distractingMinutes", JSONObject.NULL)
        }
        if (snapshot.distractingOpens != null) {
            obj.put("distractingOpens", snapshot.distractingOpens)
        } else {
            obj.put("distractingOpens", JSONObject.NULL)
        }

        val topAppsArray = JSONArray()
        for ((label, minutes) in snapshot.topApps.take(3)) {
            val itemObj = JSONObject()
            itemObj.put("label", label)
            itemObj.put("minutes", minutes)
            topAppsArray.put(itemObj)
        }
        obj.put("topApps", topAppsArray)
        obj.put("overAllowance", snapshot.overAllowance)
        obj.put("notNow", snapshot.notNow)
        obj.put("opened", snapshot.opened)

        val whyObj = JSONObject()
        for ((k, v) in snapshot.why) {
            whyObj.put(k, v)
        }
        obj.put("why", whyObj)
        obj.put("water", snapshot.water)
        obj.put("walk", snapshot.walk)
        return obj
    }

    fun decode(raw: String): List<DaySnapshot> {
        if (raw.isBlank()) {
            throw IllegalArgumentException("Input snapshot JSON is blank")
        }
        val array = try {
            JSONArray(raw)
        } catch (e: Exception) {
            throw IllegalArgumentException("Malformed JSON: expected JSONArray", e)
        }
        return decode(array)
    }

    fun decode(array: JSONArray): List<DaySnapshot> {
        val list = ArrayList<DaySnapshot>(array.length())
        for (i in 0 until array.length()) {
            val obj = try {
                array.getJSONObject(i)
            } catch (e: Exception) {
                throw IllegalArgumentException("Expected JSONObject at index $i", e)
            }
            list.add(decodeSnapshot(obj, i))
        }
        return list
    }

    fun decodeSnapshot(obj: JSONObject, index: Int = -1): DaySnapshot {
        if (!obj.has("epochDay") || obj.isNull("epochDay")) {
            throw IllegalArgumentException("Missing epochDay${if (index >= 0) " at index $index" else ""}")
        }
        val epochDay = try {
            obj.getLong("epochDay")
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid epochDay${if (index >= 0) " at index $index" else ""}", e)
        }

        val distractingMinutes = if (obj.has("distractingMinutes") && !obj.isNull("distractingMinutes")) {
            try {
                obj.getInt("distractingMinutes")
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid distractingMinutes${if (index >= 0) " at index $index" else ""}", e)
            }
        } else {
            null
        }

        val distractingOpens = if (obj.has("distractingOpens") && !obj.isNull("distractingOpens")) {
            try {
                obj.getInt("distractingOpens")
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid distractingOpens${if (index >= 0) " at index $index" else ""}", e)
            }
        } else {
            null
        }

        val topApps = if (obj.has("topApps") && !obj.isNull("topApps")) {
            val arr = try {
                obj.getJSONArray("topApps")
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid topApps array${if (index >= 0) " at index $index" else ""}", e)
            }
            if (arr.length() > 3) {
                throw IllegalArgumentException("topApps cannot have more than 3 items${if (index >= 0) " at index $index" else ""}")
            }
            val items = ArrayList<Pair<String, Int>>(arr.length())
            for (j in 0 until arr.length()) {
                val itemObj = try {
                    arr.getJSONObject(j)
                } catch (e: Exception) {
                    throw IllegalArgumentException("topApps item at index $j is not a JSONObject", e)
                }
                val label = itemObj.getString("label")
                val minutes = itemObj.getInt("minutes")
                items.add(label to minutes)
            }
            items
        } else {
            emptyList()
        }

        val overAllowance = if (obj.has("overAllowance") && !obj.isNull("overAllowance")) {
            try {
                obj.getBoolean("overAllowance")
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid overAllowance", e)
            }
        } else {
            false
        }

        val notNow = if (obj.has("notNow") && !obj.isNull("notNow")) {
            obj.getInt("notNow")
        } else {
            0
        }

        val opened = if (obj.has("opened") && !obj.isNull("opened")) {
            obj.getInt("opened")
        } else {
            0
        }

        val why = if (obj.has("why") && !obj.isNull("why")) {
            val whyObj = try {
                obj.getJSONObject("why")
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid why object", e)
            }
            val map = mutableMapOf<String, Int>()
            for (key in whyObj.keys()) {
                map[key] = whyObj.getInt(key)
            }
            map
        } else {
            emptyMap()
        }

        val water = if (obj.has("water") && !obj.isNull("water")) {
            obj.getInt("water")
        } else {
            0
        }

        val walk = if (obj.has("walk") && !obj.isNull("walk")) {
            obj.getInt("walk")
        } else {
            0
        }

        return DaySnapshot(
            epochDay = epochDay,
            distractingMinutes = distractingMinutes,
            distractingOpens = distractingOpens,
            topApps = topApps,
            overAllowance = overAllowance,
            notNow = notNow,
            opened = opened,
            why = why,
            water = water,
            walk = walk,
        )
    }
}

typealias UsageSnapshotCodec = DaySnapshotCodec

fun mergeSnapshots(existing: DaySnapshot, incoming: DaySnapshot): DaySnapshot {
    require(existing.epochDay == incoming.epochDay) {
        "Cannot merge snapshots with different epochDays: ${existing.epochDay} vs ${incoming.epochDay}"
    }

    val distractingMinutes = incoming.distractingMinutes ?: existing.distractingMinutes
    val distractingOpens = incoming.distractingOpens ?: existing.distractingOpens
    val topApps = if (incoming.topApps.isNotEmpty()) {
        incoming.topApps.take(3)
    } else {
        existing.topApps.take(3)
    }

    val overAllowance = when {
        incoming.distractingMinutes != null -> incoming.overAllowance
        existing.distractingMinutes != null -> existing.overAllowance
        else -> incoming.overAllowance || existing.overAllowance
    }

    val notNow = maxOf(existing.notNow, incoming.notNow)
    val opened = maxOf(existing.opened, incoming.opened)

    val why = if (incoming.why.isEmpty()) {
        existing.why
    } else if (existing.why.isEmpty()) {
        incoming.why
    } else {
        val allKeys = existing.why.keys + incoming.why.keys
        allKeys.associateWith { k ->
            maxOf(existing.why[k] ?: 0, incoming.why[k] ?: 0)
        }
    }

    val water = maxOf(existing.water, incoming.water)
    val walk = maxOf(existing.walk, incoming.walk)

    return DaySnapshot(
        epochDay = existing.epochDay,
        distractingMinutes = distractingMinutes,
        distractingOpens = distractingOpens,
        topApps = topApps,
        overAllowance = overAllowance,
        notNow = notNow,
        opened = opened,
        why = why,
        water = water,
        walk = walk,
    )
}

class UsageSnapshotStore(
    private val filesDir: File,
) {
    constructor(context: Context) : this(context.filesDir)

    companion object {
        const val FILE_NAME = "usage_days.json"
        const val MAX_DAYS = 56
    }

    private val file = File(filesDir, FILE_NAME)

    /** Result of reading the file: good entries, how many were skipped, and whether the file was unreadable. */
    data class LoadResult(
        val snapshots: List<DaySnapshot>,
        val skippedEntries: Int,
        val unparseable: Boolean,
    )

    @Synchronized
    fun load(): LoadResult {
        if (!file.exists()) return LoadResult(emptyList(), 0, false)
        val raw = try {
            file.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            return LoadResult(emptyList(), 0, true)
        }
        if (raw.isBlank()) return LoadResult(emptyList(), 0, false)
        val array = try {
            JSONArray(raw)
        } catch (_: Exception) {
            return LoadResult(emptyList(), 0, true)
        }
        val good = ArrayList<DaySnapshot>(array.length())
        var skipped = 0
        for (i in 0 until array.length()) {
            try {
                good.add(DaySnapshotCodec.decodeSnapshot(array.getJSONObject(i), i))
            } catch (_: Exception) {
                skipped++
            }
        }
        return LoadResult(good, skipped, false)
    }

    @Synchronized
    fun getAll(): List<DaySnapshot> = load().snapshots

    @Synchronized
    fun get(epochDay: Long): DaySnapshot? {
        return getAll().firstOrNull { it.epochDay == epochDay }
    }

    @Synchronized
    fun range(fromDay: Long, toDay: Long): List<DaySnapshot> {
        if (fromDay > toDay) return emptyList()
        return getAll().filter { it.epochDay in fromDay..toDay }.sortedBy { it.epochDay }
    }

    /**
     * Returns false (and leaves the file untouched) when the existing file cannot be parsed,
     * so a corrupt file is never replaced by a file holding only the new day.
     */
    @Synchronized
    fun upsert(snapshot: DaySnapshot): Boolean {
        val loaded = load()
        if (loaded.unparseable) return false
        val all = loaded.snapshots.toMutableList()
        val index = all.indexOfFirst { it.epochDay == snapshot.epochDay }
        val merged = if (index != -1) {
            val existing = all.removeAt(index)
            mergeSnapshots(existing, snapshot)
        } else {
            snapshot
        }
        all.add(merged)
        val trimmed = trim(all)
        saveAll(trimmed)
        return true
    }

    private fun trim(snapshots: List<DaySnapshot>): List<DaySnapshot> {
        if (snapshots.isEmpty()) return emptyList()
        val sorted = snapshots.sortedBy { it.epochDay }
        val maxDay = sorted.last().epochDay
        val minDay = maxDay - MAX_DAYS + 1
        return sorted.filter { it.epochDay >= minDay }.takeLast(MAX_DAYS)
    }

    private fun saveAll(items: List<DaySnapshot>) {
        if (!filesDir.exists()) {
            filesDir.mkdirs()
        }
        val tempFile = File(filesDir, "$FILE_NAME.tmp")
        val json = DaySnapshotCodec.encode(items)
        tempFile.writeText(json, Charsets.UTF_8)
        try {
            Files.move(
                tempFile.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: Exception) {
            if (file.exists()) file.delete()
            if (!tempFile.renameTo(file)) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }
        }
    }
}
