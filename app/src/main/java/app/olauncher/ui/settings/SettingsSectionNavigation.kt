package app.olauncher.ui.settings

import android.view.WindowInsets
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
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

    showItems(SettingsCatalog.itemsForSearch(matchingIds))
}

internal fun SettingsFragment.renderSection(section: SettingsSection) {
    currentSectionKey = section.key
    backCallback.isEnabled = true

    binding.settingsSearchContainer.isVisible = false

    binding.settingsBackLayout.isVisible = true
    binding.thumblineHeaderCard.isVisible = false
    binding.sectionListLayout.isVisible = false

    showItems(
        listOf(SettingsItem.Heading(section.titleRes, SettingsHeadingStyle.CARD)) +
            SettingsCatalog.itemsFor(section.key)
    )
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

    showItems(emptyList())
}

/** Draws [items] in the one Settings list and scrolls back to the top. The list hides when empty. */
private fun SettingsFragment.showItems(items: List<SettingsItem>) {
    binding.settingsRows.isVisible = items.isNotEmpty()
    rowsAdapter?.submitList(items) {
        if (_binding != null) binding.scrollView.scrollTo(0, 0)
    }
    binding.scrollView.scrollTo(0, 0)
}
