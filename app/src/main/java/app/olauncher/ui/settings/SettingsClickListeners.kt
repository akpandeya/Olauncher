package app.olauncher.ui.settings

import android.view.View
import androidx.navigation.fragment.findNavController
import app.olauncher.R
import app.olauncher.helper.Haptics
import app.olauncher.ui.SettingsFragment

/**
 * Taps on the fixed page frame: the header, the section list, the back row and the footer.
 * Row taps are wired when the adapter binds a row (see [bindRow]).
 */
internal fun SettingsFragment.initClickListeners() {
    binding.olauncherHiddenApps.setOnClickListener(this)
    binding.appInfo.setOnClickListener(this)
    binding.setLauncher.setOnClickListener(this)
    binding.footer.setOnClickListener(this)
    binding.settingsBack.setOnClickListener(this)
    binding.settingsBackLayout.setOnClickListener(this)
    binding.sectionRowHome.setOnClickListener(this)
    binding.sectionRowShortcuts.setOnClickListener(this)
    binding.sectionRowDrawer.setOnClickListener(this)
    binding.sectionRowWellbeing.setOnClickListener(this)
    binding.sectionRowToday.setOnClickListener(this)
    binding.sectionRowDeutsch.setOnClickListener(this)
    binding.sectionRowAppearance.setOnClickListener(this)
    binding.sectionRowGestures.setOnClickListener(this)
    binding.sectionRowBackup.setOnClickListener(this)
    binding.sectionListLayout.findViewWithTag<View>("sectionRowPrivacy")?.setOnClickListener {
        Haptics.tick(it)
        findNavController().navigate(R.id.action_settingsFragment_to_privacyFragment)
    }
}
