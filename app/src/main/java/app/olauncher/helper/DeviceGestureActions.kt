package app.olauncher.helper

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent

private const val TAG = "DeviceGestureActions"

/** Torch on and off. Needs no permission. */
object Torch {
    @Volatile private var on = false
    private var watching = false
    private var cameraId: String? = null

    /** Starts following the torch state, so the first tap flips the real state. */
    fun watch(context: Context) {
        if (watching) return
        try {
            val manager = context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
            cameraId = flashCameraId(manager)
            manager.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(id: String, enabled: Boolean) {
                    if (id == cameraId) on = enabled
                }
            }, Handler(Looper.getMainLooper()))
            watching = true
        } catch (e: Exception) {
            Log.w(TAG, "Torch watch failed", e)
        }
    }

    /** Flips the torch. Returns false when the phone has no torch or it cannot be switched. */
    fun toggle(context: Context): Boolean {
        watch(context)
        return try {
            val manager = context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false
            val id = cameraId ?: flashCameraId(manager)?.also { cameraId = it } ?: return false
            manager.setTorchMode(id, !on)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Torch toggle failed", e)
            false
        }
    }

    private fun flashCameraId(manager: CameraManager): String? = manager.cameraIdList.firstOrNull {
        manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
    }
}

/** Sends a media key to the active player. Needs no permission. */
object MediaKeys {
    fun send(context: Context, keyCode: Int) {
        try {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        } catch (e: Exception) {
            Log.w(TAG, "Media key failed", e)
        }
    }

    fun next(context: Context) = send(context, KeyEvent.KEYCODE_MEDIA_NEXT)

    fun previous(context: Context) = send(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
}
