package app.olauncher.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.olauncher.R

/** Built-in actions a home shortcut slot can hold. [id] is stored in prefs; never change existing ids. */
enum class ShortcutAction(val id: Int, @DrawableRes val iconRes: Int, @StringRes val labelRes: Int) {
    SEARCH(1, R.drawable.ic_shortcut_search, R.string.shortcut_search),
    GALLERY(2, R.drawable.ic_shortcut_gallery, R.string.shortcut_gallery),
    CAMERA(3, R.drawable.ic_shortcut_camera, R.string.shortcut_camera),
    MESSAGES(4, R.drawable.ic_shortcut_messages, R.string.shortcut_messages),
    PHONE(5, R.drawable.ic_shortcut_phone, R.string.shortcut_phone),
    SETTINGS(6, R.drawable.ic_shortcut_more, R.string.shortcut_more);

    companion object {
        /** Stored value meaning "no built-in chosen; use the slot's default". */
        const val NONE_ID = 0

        fun fromId(id: Int): ShortcutAction? = values().firstOrNull { it.id == id }

        /** The original fixed action of a slot: slots 1–6 map to the six built-ins, others have none. */
        fun defaultFor(slot: Int): ShortcutAction? = when (slot) {
            1 -> SEARCH
            2 -> GALLERY
            3 -> CAMERA
            4 -> MESSAGES
            5 -> PHONE
            6 -> SETTINGS
            else -> null
        }

        /** The chosen built-in for [slot] when [storedId] is valid, else the slot's default. */
        fun resolve(slot: Int, storedId: Int): ShortcutAction? = fromId(storedId) ?: defaultFor(slot)
    }
}
