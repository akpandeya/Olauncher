package app.olauncher.helper

import java.io.File

object PhotoSet {

    data class Rename(val from: Int, val to: Int)

    data class RemovalPlan(
        val renames: List<Rename>,
        val removed: List<Int>,
        val newCount: Int,
        val newIndex: Int,
        val currentRemoved: Boolean
    )

    fun planRemoval(count: Int, index: Int, removed: Set<Int>): RemovalPlan {
        if (count <= 0) {
            throw IllegalArgumentException("count must be positive: $count")
        }
        if (removed.isEmpty()) {
            throw IllegalArgumentException("removed must not be empty")
        }
        for (r in removed) {
            if (r !in 0 until count) {
                throw IllegalArgumentException("removed index $r is outside 0 until $count")
            }
        }

        val safeIndex = index.coerceIn(0, count - 1)
        val sortedRemoved = removed.sorted()
        val kept = (0 until count).filter { it !in removed }
        val newCount = kept.size

        if (kept.isEmpty()) {
            return RemovalPlan(
                renames = emptyList(),
                removed = sortedRemoved,
                newCount = 0,
                newIndex = 0,
                currentRemoved = true
            )
        }

        val currentRemoved = safeIndex in removed
        val newIndex = if (!currentRemoved) {
            kept.indexOf(safeIndex)
        } else {
            val nextIdx = kept.indexOfFirst { it > safeIndex }
            if (nextIdx != -1) nextIdx else 0
        }

        val renames = kept.mapIndexedNotNull { newIdx, oldIdx ->
            if (oldIdx != newIdx) Rename(from = oldIdx, to = newIdx) else null
        }

        return RemovalPlan(
            renames = renames,
            removed = sortedRemoved,
            newCount = newCount,
            newIndex = newIndex,
            currentRemoved = currentRemoved
        )
    }

    fun applyRemoval(dir: File, plan: RemovalPlan): Boolean {
        if (!dir.exists() || !dir.isDirectory) {
            return false
        }
        for (index in plan.removed) {
            val file = File(dir, "$index.jpg")
            if (!file.delete()) {
                return false
            }
        }
        // Keep going after a failed rename so later photos are still moved into place.
        // Never overwrite: a target that still exists holds a photo whose own rename failed.
        var allOk = true
        for (rename in plan.renames.sortedBy { it.from }) {
            val fromFile = File(dir, "${rename.from}.jpg")
            val toFile = File(dir, "${rename.to}.jpg")
            if (toFile.exists() || !fromFile.renameTo(toFile)) {
                allOk = false
            }
        }
        return allOk
    }

    fun countContiguous(dir: File): Int {
        if (!dir.exists() || !dir.isDirectory) {
            return 0
        }
        var count = 0
        while (File(dir, "$count.jpg").isFile) {
            count++
        }
        return count
    }
}
