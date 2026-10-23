package app.olauncher.ui.settings

import androidx.annotation.IdRes
import androidx.annotation.StringRes
import app.olauncher.R

data class SettingsSection(
    val key: String,
    @StringRes val titleRes: Int,
    @IdRes val containerId: Int,
) {
    val title: Int get() = titleRes
    val containerViewId: Int get() = containerId
    val titleStringRes: Int get() = titleRes
}

object SettingsSections : List<SettingsSection> {
    const val KEY_HOME = "home"
    const val KEY_SHORTCUTS = "shortcuts"
    const val KEY_DRAWER = "drawer"
    const val KEY_WELLBEING = "wellbeing"
    const val KEY_TODAY = "today"
    const val KEY_DEUTSCH = "deutsch"
    const val KEY_APPEARANCE = "appearance"
    const val KEY_GESTURES = "gestures"
    const val KEY_BACKUP = "backup"

    val HOME = SettingsSection(KEY_HOME, R.string.home_screen, R.id.homeSection)
    val SHORTCUTS = SettingsSection(KEY_SHORTCUTS, R.string.shortcut_icons, R.id.shortcutIconsSection)
    val DRAWER = SettingsSection(KEY_DRAWER, R.string.drawer, R.id.drawerSection)
    val WELLBEING = SettingsSection(KEY_WELLBEING, R.string.wellbeing, R.id.wellbeingSection)
    val TODAY = SettingsSection(KEY_TODAY, R.string.today_section, R.id.todaySection)
    val DEUTSCH = SettingsSection(KEY_DEUTSCH, R.string.german_section, R.id.deutschSection)
    val APPEARANCE = SettingsSection(KEY_APPEARANCE, R.string.appearance, R.id.appearanceSection)
    val GESTURES = SettingsSection(KEY_GESTURES, R.string.gestures, R.id.gesturesSection)
    val BACKUP = SettingsSection(KEY_BACKUP, R.string.backup, R.id.backupSection)

    val all: List<SettingsSection> = listOf(
        HOME,
        SHORTCUTS,
        DRAWER,
        WELLBEING,
        TODAY,
        DEUTSCH,
        APPEARANCE,
        GESTURES,
        BACKUP,
    )

    val sections: List<SettingsSection> get() = all

    override val size: Int get() = all.size
    override fun get(index: Int): SettingsSection = all[index]
    override fun isEmpty(): Boolean = all.isEmpty()
    override fun iterator(): Iterator<SettingsSection> = all.iterator()
    override fun listIterator(): ListIterator<SettingsSection> = all.listIterator()
    override fun listIterator(index: Int): ListIterator<SettingsSection> = all.listIterator(index)
    override fun subList(fromIndex: Int, toIndex: Int): List<SettingsSection> = all.subList(fromIndex, toIndex)
    override fun lastIndexOf(element: SettingsSection): Int = all.lastIndexOf(element)
    override fun indexOf(element: SettingsSection): Int = all.indexOf(element)
    override fun containsAll(elements: Collection<SettingsSection>): Boolean = all.containsAll(elements)
    override fun contains(element: SettingsSection): Boolean = all.contains(element)

    operator fun get(key: String): SettingsSection? = findByKey(key)
    fun byKey(key: String): SettingsSection? = findByKey(key)

    fun findByKey(key: String?): SettingsSection? {
        if (key.isNullOrBlank()) return null
        val trimmed = key.trim()
        return all.firstOrNull { section ->
            section.key.equals(trimmed, ignoreCase = true) ||
            when (section.key) {
                KEY_HOME -> trimmed.equals("home_screen", ignoreCase = true) || trimmed.equals("homeScreen", ignoreCase = true)
                KEY_SHORTCUTS -> trimmed.equals("shortcut_icons", ignoreCase = true) || trimmed.equals("shortcutIcons", ignoreCase = true)
                KEY_DEUTSCH -> trimmed.equals("german", ignoreCase = true) || trimmed.equals("deutsch_de", ignoreCase = true)
                KEY_TODAY -> trimmed.equals("today_section", ignoreCase = true) || trimmed.equals("glance", ignoreCase = true) || trimmed.equals("weather", ignoreCase = true)
                else -> false
            }
        }
    }

    /** The section that holds the row or section container with [viewId], from the row list. */
    fun findSectionByViewId(viewId: Int): SettingsSection? =
        SettingsCatalog.sectionKeyOfViewId(viewId)?.let { findByKey(it) }

    /**
     * Section to open for an explicit target: a section key wins, else the section that
     * holds the row view id. Returns null (open the list) when neither resolves.
     */
    fun resolveTarget(sectionKey: String?, @IdRes rowViewId: Int): SettingsSection? =
        findByKey(sectionKey) ?: findSectionByViewId(rowViewId)

    /**
     * Section to show when the Settings view is (re)created. The explicit deep-link target
     * applies only on first creation; after a rotation or a return from another screen the
     * section the user was on wins, and null there means the section list.
     */
    fun initialSection(
        restored: Boolean,
        restoredSectionKey: String?,
        explicitTarget: () -> SettingsSection?,
    ): SettingsSection? = if (restored) findByKey(restoredSectionKey) else explicitTarget()

    fun findSectionByNameOrKey(name: String?): SettingsSection? {
        if (name.isNullOrBlank()) return null
        val trimmed = name.trim()
        val direct = findByKey(trimmed)
        if (direct != null) return direct

        val lower = trimmed.lowercase()
        return when {
            lower.contains("home") || lower.contains("battery") || lower.contains("alignment") -> HOME
            lower.contains("shortcut") -> SHORTCUTS
            lower.contains("drawer") || lower.contains("keyboard") || lower.contains("search") ||
                lower.contains("letter") || lower.contains("strip") -> DRAWER
            lower.contains("pause") || lower.contains("wellbeing") || lower.contains("distracting") ||
                lower.contains("allowance") || lower.contains("escalate") || lower.contains("why") ||
                lower.contains("commit") || lower.contains("nudge") || lower.contains("screen") ||
                lower.contains("quiet") || lower.contains("hold") -> WELLBEING
            lower.contains("today") || lower.contains("weather") || lower.contains("glance") -> TODAY
            lower.contains("german") || lower.contains("deutsch") -> DEUTSCH
            lower.contains("wallpaper") || lower.contains("theme") || lower.contains("appearance") ||
                lower.contains("font") || lower.contains("text_size") || lower.contains("status_bar") ||
                lower.contains("haptic") -> APPEARANCE
            lower.contains("gesture") || lower.contains("swipe") || lower.contains("lock") -> GESTURES
            lower.contains("backup") || lower.contains("export") || lower.contains("import") -> BACKUP
            else -> null
        }
    }
}
