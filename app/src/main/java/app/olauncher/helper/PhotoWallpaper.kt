package app.olauncher.helper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import app.olauncher.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

enum class RotationInterval(
    val id: Int,
    val intervalMillis: Long?,
    val periodicRepeatMinutes: Long?,
) {
    ON_OPEN(1, 0L, null),
    MINUTES_15(2, TimeUnit.MINUTES.toMillis(15), 15L),
    MINUTES_30(3, TimeUnit.MINUTES.toMillis(30), 30L),
    HOURS_1(4, TimeUnit.HOURS.toMillis(1), 60L),
    HOURS_3(5, TimeUnit.HOURS.toMillis(3), 180L),
    HOURS_6(6, TimeUnit.HOURS.toMillis(6), 360L),
    HOURS_12(7, TimeUnit.HOURS.toMillis(12), 720L),
    DAILY(8, TimeUnit.DAYS.toMillis(1), 1440L),
    WEEKLY(9, TimeUnit.DAYS.toMillis(7), 10080L),
    NEVER(10, null, null);

    companion object {
        val DEFAULT = DAILY

        fun fromId(id: Int): RotationInterval =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

data class RotationStep(
    val nextIndex: Int?,
    val lastChangedMillis: Long,
)

object Rotation {
    fun step(
        index: Int,
        count: Int,
        nowMillis: Long,
        lastChangedMillis: Long,
        interval: RotationInterval,
    ): RotationStep {
        if (count <= 0) return RotationStep(null, lastChangedMillis)
        if (interval == RotationInterval.NEVER) return RotationStep(null, lastChangedMillis)
        if (nowMillis < lastChangedMillis) return RotationStep(null, nowMillis)

        val intervalMillis = interval.intervalMillis ?: return RotationStep(null, lastChangedMillis)
        val slack = intervalMillis / 10
        val threshold = intervalMillis - slack
        val elapsed = nowMillis - lastChangedMillis
        return if (elapsed >= threshold) {
            RotationStep(((index + 1) % count + count) % count, nowMillis)
        } else {
            RotationStep(null, lastChangedMillis)
        }
    }

    fun resolveLastChanged(
        storedMillis: Long,
        lastDay: Int,
        today: Int,
        nowMillis: Long,
    ): Long {
        return when {
            storedMillis > 0L -> storedMillis
            lastDay == today -> nowMillis
            else -> 0L
        }
    }

    fun previous(index: Int, count: Int): Int? {
        if (count <= 0) return null
        return ((index - 1) % count + count) % count
    }

    fun manualNext(index: Int, count: Int): Int? {
        if (count <= 0) return null
        return ((index + 1) % count + count) % count
    }
}

object WallpaperHomeMenu {
    fun isShown(enabled: Boolean, count: Int): Boolean = enabled && count > 0
}

object PhotoWallpaper {
    const val TARGET_HOME = 0
    const val TARGET_LOCK = 1
    const val TARGET_BOTH = 2

    fun today(): Int = LocalDate.now().toEpochDay().toInt()

    fun markChanged(prefs: Prefs, nowMillis: Long = System.currentTimeMillis()) {
        prefs.photoWallpaperLastChanged = nowMillis
        prefs.photoWallpaperLastDay = today()
    }

    fun rotateIfDue(prefs: Prefs, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val count = prefs.photoWallpaperCount
        val currentIndex = prefs.photoWallpaperIndex
        val today = today()
        val lastChanged = Rotation.resolveLastChanged(
            prefs.photoWallpaperLastChanged,
            prefs.photoWallpaperLastDay,
            today,
            nowMillis,
        )
        val step = Rotation.step(
            currentIndex,
            count,
            nowMillis,
            lastChanged,
            RotationInterval.fromId(prefs.photoWallpaperInterval),
        )
        val rotated = step.nextIndex != null
        if (rotated) {
            prefs.photoWallpaperIndex = step.nextIndex!!
            prefs.photoWallpaperLastDay = today
        }
        prefs.photoWallpaperLastChanged = step.lastChangedMillis
        return rotated && step.nextIndex != currentIndex
    }

    fun getWallpaperDir(context: Context): File {
        return File(context.filesDir, "wallpapers")
    }

    fun getWallpaperFile(context: Context, index: Int): File {
        return File(getWallpaperDir(context), "$index.jpg")
    }

    fun deleteAll(context: Context) {
        try {
            val dir = getWallpaperDir(context)
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun targetFlags(target: Int): Int {
        return when (target) {
            TARGET_HOME -> WallpaperManager.FLAG_SYSTEM
            TARGET_LOCK -> WallpaperManager.FLAG_LOCK
            TARGET_BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            else -> WallpaperManager.FLAG_SYSTEM
        }
    }

    fun applyCurrent(context: Context, prefs: Prefs): Boolean {
        val count = prefs.photoWallpaperCount
        if (count <= 0) return false
        val index = prefs.photoWallpaperIndex.coerceIn(0, count - 1)
        val file = getWallpaperFile(context, index)
        if (!file.exists()) return false

        return try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return false
            val flags = targetFlags(prefs.photoWallpaperTarget)
            val manager = WallpaperManager.getInstance(context)
            manager.setBitmap(bitmap, null, true, flags)
            bitmap.recycle()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun stepManually(
        context: Context,
        prefs: Prefs,
        forward: Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        val count = prefs.photoWallpaperCount
        if (count <= 0) return@withContext false
        val newIndex = if (forward) {
            Rotation.manualNext(prefs.photoWallpaperIndex, count)
        } else {
            Rotation.previous(prefs.photoWallpaperIndex, count)
        } ?: return@withContext false
        prefs.photoWallpaperIndex = newIndex
        markChanged(prefs)
        applyCurrent(context, prefs)
    }

    suspend fun savePickedPhotos(
        context: Context,
        uris: List<Uri>,
    ): Int = withContext(Dispatchers.IO) {
        val dir = getWallpaperDir(context)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
        appendPickedPhotos(context, uris, startIndex = 0)
    }

    suspend fun appendPickedPhotos(
        context: Context,
        uris: List<Uri>,
        startIndex: Int,
    ): Int = withContext(Dispatchers.IO) {
        val dir = getWallpaperDir(context)
        dir.mkdirs()

        val (screenWidth, screenHeight) = getScreenDimensions(context)
        val targetWidth = if (screenWidth > 0) screenWidth else 1080
        val targetHeight = if (screenHeight > 0) screenHeight else 1920

        var saved = 0
        for (uri in uris) {
            try {
                val cropped = decodeAndCrop(context, uri, targetWidth, targetHeight)
                try {
                    val outFile = File(dir, "${startIndex + saved}.jpg")
                    FileOutputStream(outFile).use { fos ->
                        cropped.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                    }
                    saved++
                } finally {
                    cropped.recycle()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        saved
    }

    fun removePhotos(context: Context, plan: PhotoSet.RemovalPlan): Boolean {
        return try {
            PhotoSet.applyRemoval(getWallpaperDir(context), plan)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun decodeAndCrop(
        context: Context,
        uri: Uri,
        targetWidth: Int,
        targetHeight: Int,
    ): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val origW = info.size.width
            val origH = info.size.height
            if (origW > 0 && origH > 0) {
                val scale = maxOf(
                    targetWidth.toFloat() / origW,
                    targetHeight.toFloat() / origH
                )
                val w = maxOf(1, (origW * scale).roundToInt())
                val h = maxOf(1, (origH * scale).roundToInt())
                decoder.setTargetSize(w, h)
            }
        }

        val cropped = centreCrop(decoded, targetWidth, targetHeight)
        if (cropped != decoded) {
            decoded.recycle()
        }
        return cropped
    }

    fun centreCrop(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        if (bitmap.width == targetWidth && bitmap.height == targetHeight) {
            return bitmap
        }

        val baseBitmap: Bitmap
        val shouldRecycleBase: Boolean
        if (bitmap.width < targetWidth || bitmap.height < targetHeight) {
            val scale = maxOf(
                targetWidth.toFloat() / bitmap.width,
                targetHeight.toFloat() / bitmap.height
            )
            val scaledW = (bitmap.width * scale).roundToInt().coerceAtLeast(targetWidth)
            val scaledH = (bitmap.height * scale).roundToInt().coerceAtLeast(targetHeight)
            baseBitmap = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)
            shouldRecycleBase = baseBitmap != bitmap
        } else {
            baseBitmap = bitmap
            shouldRecycleBase = false
        }

        val x = ((baseBitmap.width - targetWidth) / 2).coerceIn(0, maxOf(0, baseBitmap.width - targetWidth))
        val y = ((baseBitmap.height - targetHeight) / 2).coerceIn(0, maxOf(0, baseBitmap.height - targetHeight))

        val cropped = Bitmap.createBitmap(baseBitmap, x, y, targetWidth, targetHeight)
        if (shouldRecycleBase && cropped != baseBitmap) {
            baseBitmap.recycle()
        }
        return cropped
    }
}
