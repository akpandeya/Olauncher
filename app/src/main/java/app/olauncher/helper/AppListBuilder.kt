package app.olauncher.helper

import java.text.Collator

/**
 * Context-free sort and filter rules for the app list.
 * Takes plain data only, so it can be covered by JVM unit tests.
 */
object AppListBuilder {

    /** The label the user sees: the rename label if set, otherwise the system label. */
    fun displayLabel(renameLabel: String, systemLabel: String): String =
        renameLabel.ifBlank { systemLabel }

    /** Key used to look up an app in the hidden apps set: `package|user`. */
    fun hiddenKey(packageName: String, userString: String): String =
        "$packageName|$userString"

    /**
     * Whether an app of a normal profile belongs in the list.
     * The launcher itself is never listed. Hidden apps need [includeHiddenApps],
     * all other apps need [includeRegularApps].
     */
    fun shouldInclude(
        packageName: String,
        userString: String,
        ownPackage: String,
        hiddenApps: Set<String>,
        includeRegularApps: Boolean,
        includeHiddenApps: Boolean,
    ): Boolean {
        if (packageName == ownPackage) return false
        return if (hiddenApps.contains(hiddenKey(packageName, userString))) includeHiddenApps
        else includeRegularApps
    }

    /** Private space lists everything except the launcher itself. */
    fun shouldIncludePrivate(packageName: String, ownPackage: String): Boolean =
        packageName != ownPackage

    /** Sorts in place by the shown label, using [collator]. */
    fun <T> sortByLabel(list: MutableList<T>, collator: Collator, label: (T) -> String) {
        list.sortWith(compareBy(collator, label))
    }
}
