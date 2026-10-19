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
    ) : AppModel() {
        sealed interface Action {
            data class Copy(val value: String) : Action {
                val textToCopy: String get() = value
            }
            data class Dial(val number: String) : Action
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
