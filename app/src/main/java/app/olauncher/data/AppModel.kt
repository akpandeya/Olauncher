package app.olauncher.data

import android.os.UserHandle
import java.text.CollationKey

sealed class AppModel : Comparable<AppModel> {
    abstract val appLabel: String
    abstract val key: CollationKey?
    abstract val appPackage: String
    abstract val user: UserHandle
    abstract val isNew: Boolean
    open val isArchived: Boolean = false
    open val firstInstallTime: Long = 0L

    data class App(
        override val appLabel: String,
        override val key: CollationKey?,
        override val appPackage: String,
        val activityClassName: String?,
        override val isNew: Boolean = false,
        override val user: UserHandle,
        override val firstInstallTime: Long = 0L,
        override val isArchived: Boolean = false,
        val isRecent: Boolean = false,
    ) : AppModel()

    data class PinnedShortcut(
        override val appLabel: String,
        override val key: CollationKey?,
        override val appPackage: String,
        val shortcutId: String,
        override val isNew: Boolean = false,
        override val user: UserHandle,
        override val firstInstallTime: Long = 0L,
        override val isArchived: Boolean = false,
    ) : AppModel() {
        val identity: String
            get() = shortcutIdentity(appPackage, shortcutId, user.toString())
    }

    data class Result(
        val text: String,
        val action: Action,
        val multiline: Boolean = false,
    ) : AppModel() {
        sealed interface Action {
            data class Copy(val value: String) : Action {
                val textToCopy: String get() = value
            }
            data class Dial(val number: String) : Action
            data class Contact(val lookupUri: String) : Action
            data class Event(val eventId: Long) : Action
            data class Web(val query: String) : Action
            /** [meaning] is a looked-up Wiktionary meaning; only used when [items] has one item. */
            data class SaveWords(val items: List<String>, val meaning: String = "") : Action
            /** Tap re-runs the `:w` lookup for [text]. */
            data class LookUpWord(val text: String) : Action
            /** Tap puts [query] into the search box (command help rows). */
            data class FillQuery(val query: String) : Action
            /** Tap starts a countdown timer in the clock app (`:t`). */
            data class StartTimer(val seconds: Int, val label: String) : Action
            /** Tap sets an alarm in the clock app (`:a`). [days] are Calendar constants. */
            data class SetAlarm(val hour: Int, val minute: Int, val days: List<Int>, val label: String) : Action
            /** Tap opens the calendar's new-event screen prefilled (`:e`). Millis follow CalendarContract. */
            data class AddEvent(val title: String, val place: String, val beginMillis: Long, val endMillis: Long, val allDay: Boolean) : Action
            /** Unreadable command input (`Can't read '…'`). Tap and Enter do nothing. */
            data object Error : Action
            /** Tap switches to the profile [profileId] (`:p`). */
            data class SwitchProfile(val profileId: String, val name: String) : Action
            /** Tap saves an open note (`:n`). */
            data class SaveNote(val text: String, val showFrom: java.time.LocalDate? = null) : Action
            /** [openOnTap] false: tap does nothing, long-press still opens the page. */
            data class OpenWiktionary(val text: String, val openOnTap: Boolean = true) : Action
        }

        override val appLabel: String get() = text
        override val key: CollationKey? get() = null
        override val appPackage: String get() = ""
        override val isNew: Boolean get() = false
        override val user: UserHandle get() = defaultUser
        override val firstInstallTime: Long get() = 0L
    }

    data class PrivateSpaceHeader(
        val isLocked: Boolean = true,
        override val user: UserHandle = android.os.Process.myUserHandle(),
    ) : AppModel() {
        override val appLabel: String = ""
        override val key: CollationKey? = null
        override val appPackage: String = ""
        override val isNew: Boolean get() = false
        override val firstInstallTime: Long get() = 0L
    }

    data class RecentHeader(
        val title: String = "",
    ) : AppModel() {
        override val appLabel: String get() = title
        override val key: CollationKey? get() = null
        override val appPackage: String get() = ""
        override val isNew: Boolean get() = false
        override val user: UserHandle get() = defaultUser
        override val firstInstallTime: Long get() = 0L
    }

    companion object {
        internal val defaultUser: UserHandle by lazy {
            try {
                android.os.Process.myUserHandle()
            } catch (_: Throwable) {
                val unsafeClass = Class.forName("sun.misc.Unsafe")
                val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe")
                theUnsafeField.isAccessible = true
                val unsafe = theUnsafeField.get(null)
                val allocateInstance = unsafeClass.getMethod("allocateInstance", Class::class.java)
                allocateInstance.invoke(unsafe, UserHandle::class.java) as UserHandle
            }
        }
    }

    override fun compareTo(other: AppModel): Int = when {
        key != null && other.key != null -> key!!.compareTo(other.key)
        else -> appLabel.compareTo(other.appLabel, true)
    }
}
