package app.olauncher.ui.settings

import app.olauncher.R
import app.olauncher.data.ProfileState
import app.olauncher.helper.createChoiceDialog
import app.olauncher.helper.profileChoices
import app.olauncher.helper.profileNameOf
import app.olauncher.helper.showToast
import app.olauncher.ui.SettingsFragment

internal fun SettingsFragment.currentProfileState(): ProfileState =
    prefs.profileState { id -> prefs.profileNameOf(requireContext(), id) ?: id }

internal fun SettingsFragment.populateProfileSwitch() {
    val ctx = context ?: return
    binding.profileSwitch.text = when (val state = currentProfileState()) {
        ProfileState.None -> ctx.getString(R.string.profile_name_day)
        is ProfileState.Active -> state.name
        is ProfileState.Custom -> ctx.getString(R.string.profile_custom_based_on, state.basedOn.name)
    }
}

/** Pick a profile. While the active one is Custom, it also offers Save to X and Reset to X on top. */
internal fun SettingsFragment.showProfileSwitchDialog() {
    val ctx = requireContext()
    val custom = currentProfileState() as? ProfileState.Custom
    val choices = prefs.profileChoices(ctx)
    val items = mutableListOf<CharSequence>()
    if (custom != null) {
        items.add(getString(R.string.profile_save_to, custom.basedOn.name))
        items.add(getString(R.string.profile_reset_to, custom.basedOn.name))
    }
    val offset = items.size
    items.addAll(choices.map { it.name })
    val activeId = prefs.appliedProfileId.ifEmpty { prefs.activeProfileId() }
    val checked = choices.indexOfFirst { it.id == activeId }
    dialog?.dismiss()
    val next = ctx.createChoiceDialog(
        title = R.string.profile_switch,
        items = items,
        checkedIndex = if (custom != null || checked < 0) -1 else checked + offset,
    ) { which ->
        when {
            custom != null && which == 0 -> {
                prefs.saveToProfile(custom.basedOn)
                ctx.showToast(getString(R.string.profile_saved, custom.basedOn.name))
            }
            custom != null && which == 1 -> {
                prefs.switchProfile(custom.basedOn.id)
                ctx.showToast(getString(R.string.profile_switched, custom.basedOn.name))
            }
            else -> choices[which - offset].let {
                prefs.switchProfile(it.id)
                ctx.showToast(getString(R.string.profile_switched, it.name))
            }
        }
        populateAllRows()
        viewModel.refreshHome(true)
    }
    dialog = next
    next.showRespectingStatusBar()
}
