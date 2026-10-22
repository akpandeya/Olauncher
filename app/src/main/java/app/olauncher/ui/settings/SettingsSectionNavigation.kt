package app.olauncher.ui.settings

import android.view.WindowInsets
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import app.olauncher.R
import app.olauncher.helper.DrawerScrollHelper
import app.olauncher.ui.SettingsFragment

internal fun SettingsFragment.setupSearchInsets() {
    binding.root.setOnApplyWindowInsetsListener { root, insets ->
        val imeBottom = insets.getInsets(WindowInsets.Type.ime()).bottom
        val navBarBottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
        val searchMargin = (12 * resources.displayMetrics.density).toInt()
        val bottomPadding = if (imeBottom > 0) {
            DrawerScrollHelper.imeBottomPadding(
                drawerBottomSearch = true,
                imeBottom = imeBottom,
                searchBottomMargin = searchMargin,
            )
        } else {
            navBarBottom
        }
        if (root.paddingBottom != bottomPadding) {
            root.setPadding(root.paddingLeft, root.paddingTop, root.paddingRight, bottomPadding)
        }
        insets
    }
    binding.root.requestApplyInsets()
}

internal fun SettingsFragment.initSearch() {
    binding.settingsSearch.doAfterTextChanged { text ->
        onSearchQueryChanged(text?.toString().orEmpty())
    }
}

internal fun SettingsFragment.onSearchQueryChanged(query: String) {
    if (query.isBlank()) {
        if (currentSectionKey == null) {
            showSectionList()
        }
        return
    }

    val matchingRows = SettingsSearch.match(query, collectRows())
    val matchingIds = matchingRows.map { it.viewId }.toSet()

    backCallback.isEnabled = true
    binding.settingsBackLayout.isVisible = false
    binding.thumblineHeaderCard.isVisible = false
    binding.sectionListLayout.isVisible = false
    binding.footer.isVisible = false
    binding.licenseNote.isVisible = false

    for (item in runtimeRows) {
        item.rowView.isVisible = item.viewId in matchingIds
    }

    binding.homeSection.isVisible = runtimeRows.any { it.sectionKey == SettingsSections.KEY_HOME && it.viewId in matchingIds }
    binding.shortcutIconsSection.isVisible = runtimeRows.any { it.sectionKey == SettingsSections.KEY_SHORTCUTS && it.viewId in matchingIds }
    binding.drawerSection.isVisible = runtimeRows.any { it.sectionKey == SettingsSections.KEY_DRAWER && it.viewId in matchingIds }

    val wellbeingMatch = runtimeRows.any { it.sectionKey == SettingsSections.KEY_WELLBEING && it.viewId in matchingIds }
    binding.wellbeingSection.isVisible = wellbeingMatch
    if (wellbeingMatch) {
        val nudgeIds = setOf(R.id.nudgeWater, R.id.nudgeWalk, R.id.nudgeSocial, R.id.nudgeActiveHours)
        binding.nudgesSection.isVisible = nudgeIds.any { it in matchingIds }
    }

    binding.todaySection.isVisible = (R.id.todayCards in matchingIds || R.id.todayOrder in matchingIds || R.id.intentionOnHome in matchingIds)
    binding.glanceSection.isVisible = (R.id.weather in matchingIds)
    binding.deutschSection.isVisible = runtimeRows.any { it.sectionKey == SettingsSections.KEY_DEUTSCH && it.viewId in matchingIds }
    binding.appearanceSection.isVisible = runtimeRows.any { it.sectionKey == SettingsSections.KEY_APPEARANCE && it.viewId in matchingIds }
    binding.gesturesSection.isVisible = runtimeRows.any { it.sectionKey == SettingsSections.KEY_GESTURES && it.viewId in matchingIds }
    binding.backupSection.isVisible = runtimeRows.any { it.sectionKey == SettingsSections.KEY_BACKUP && it.viewId in matchingIds }

    binding.scrollView.scrollTo(0, 0)
}

internal fun SettingsFragment.renderSection(section: SettingsSection) {
    currentSectionKey = section.key
    backCallback.isEnabled = true

    binding.settingsSearchContainer.isVisible = false

    binding.settingsBackLayout.isVisible = true
    binding.thumblineHeaderCard.isVisible = false
    binding.sectionListLayout.isVisible = false

    for (item in runtimeRows) {
        if (item.sectionKey == section.key) {
            item.rowView.isVisible = isRowAvailable(item.viewId)
        }
    }
    binding.nudgesSection.isVisible = true

    binding.homeSection.isVisible = (section == SettingsSections.HOME)
    binding.shortcutIconsSection.isVisible = (section == SettingsSections.SHORTCUTS)
    binding.drawerSection.isVisible = (section == SettingsSections.DRAWER)
    binding.wellbeingSection.isVisible = (section == SettingsSections.WELLBEING)
    binding.todaySection.isVisible = (section == SettingsSections.TODAY)
    binding.glanceSection.isVisible = (section == SettingsSections.TODAY)
    binding.deutschSection.isVisible = (section == SettingsSections.DEUTSCH)
    binding.appearanceSection.isVisible = (section == SettingsSections.APPEARANCE)
    binding.gesturesSection.isVisible = (section == SettingsSections.GESTURES)
    binding.backupSection.isVisible = (section == SettingsSections.BACKUP)

    binding.scrollView.scrollTo(0, 0)
}

internal fun SettingsFragment.renderSectionList() {
    currentSectionKey = null
    val hasSearch = _binding != null && !binding.settingsSearch.text.isNullOrEmpty()
    backCallback.isEnabled = false

    binding.settingsSearchContainer.isVisible = true
    if (hasSearch) {
        binding.settingsSearch.setText("")
    }

    binding.settingsBackLayout.isVisible = false
    binding.thumblineHeaderCard.isVisible = true
    binding.sectionListLayout.isVisible = true
    binding.footer.isVisible = true
    binding.licenseNote.isVisible = true

    for (item in runtimeRows) {
        item.rowView.isVisible = isRowAvailable(item.viewId)
    }
    binding.nudgesSection.isVisible = true

    binding.homeSection.isVisible = false
    binding.shortcutIconsSection.isVisible = false
    binding.drawerSection.isVisible = false
    binding.wellbeingSection.isVisible = false
    binding.todaySection.isVisible = false
    binding.glanceSection.isVisible = false
    binding.deutschSection.isVisible = false
    binding.appearanceSection.isVisible = false
    binding.gesturesSection.isVisible = false
    binding.backupSection.isVisible = false

    binding.scrollView.scrollTo(0, 0)
}
