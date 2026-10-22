package app.olauncher.data

import android.content.SharedPreferences
import androidx.core.content.edit
import app.olauncher.helper.GestureAction
import app.olauncher.helper.GestureActions
import app.olauncher.helper.GestureApp
import app.olauncher.helper.GestureSlot

/** Settings group: custom gestures (see [Prefs]). New keys only. */
interface GesturePrefs {
    fun gestureAction(slot: GestureSlot): GestureAction

    fun setGestureAction(slot: GestureSlot, action: GestureAction)

    fun gestureApp(slot: GestureSlot): GestureApp?

    fun setGestureApp(slot: GestureSlot, app: GestureApp)
}

internal class GesturePrefsStore(private val prefs: SharedPreferences) : GesturePrefs {
    private fun actionKey(slot: GestureSlot) = "GESTURE_ACTION_${slot.key}"
    private fun appKey(slot: GestureSlot, field: String) = "GESTURE_APP_${slot.key}_$field"

    override fun gestureAction(slot: GestureSlot): GestureAction =
        GestureActions.fromKey(prefs.getString(actionKey(slot), null))

    override fun setGestureAction(slot: GestureSlot, action: GestureAction) =
        prefs.edit { putString(actionKey(slot), action.key) }

    override fun gestureApp(slot: GestureSlot): GestureApp? {
        val pkg = prefs.getString(appKey(slot, "PACKAGE"), "").orEmpty()
        if (pkg.isEmpty()) return null
        return GestureApp(
            label = prefs.getString(appKey(slot, "LABEL"), "").orEmpty(),
            packageName = pkg,
            className = prefs.getString(appKey(slot, "CLASS_NAME"), null),
            user = prefs.getString(appKey(slot, "USER"), "").orEmpty(),
        )
    }

    override fun setGestureApp(slot: GestureSlot, app: GestureApp) = prefs.edit {
        putString(appKey(slot, "LABEL"), app.label)
        putString(appKey(slot, "PACKAGE"), app.packageName)
        putString(appKey(slot, "CLASS_NAME"), app.className)
        putString(appKey(slot, "USER"), app.user)
    }
}
