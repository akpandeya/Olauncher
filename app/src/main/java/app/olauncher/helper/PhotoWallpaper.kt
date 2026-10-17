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
import kotlin.math.roundToInt

object Rotation {
    fun next(index: Int, count: Int, today: Int, lastDay: Int): Int? {
        if (count <= 0) return null
        if (today == lastDay) return null
        return ((index + 1) % count + count) % count
    }
}

object PhotoWallpaper {
    const val TARGET_HOME = 0
    const val TARGET_LOCK = 1
    const val TARGET_BOTH = 2

    fun today(): Int = LocalDate.now().toEpochDay().toInt()

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

    suspend fun savePickedPhotos(
        context: Context,
        uris: List<Uri>,
    ): Int = withContext(Dispatchers.IO) {
        val dir = getWallpaperDir(context)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
        dir.mkdirs()

        val (screenWidth, screenHeight) = getScreenDimensions(context)
        val targetWidth = if (screenWidth > 0) screenWidth else 1080
        val targetHeight = if (screenHeight > 0) screenHeight else 1920

        var saved = 0
        for (uri in uris) {
            try {
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

                val outFile = File(dir, "$saved.jpg")
                FileOutputStream(outFile).use { fos ->
                    cropped.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                }
                cropped.recycle()
                saved++
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        saved
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
