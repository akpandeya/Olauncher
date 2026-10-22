package app.olauncher.ui.settings

import app.olauncher.R
import app.olauncher.data.HomeSetProfiles
import app.olauncher.data.Profile
import app.olauncher.data.ProfileFormatException
import app.olauncher.data.ProfileRule
import app.olauncher.data.ProfileSchedule
import app.olauncher.helper.createChoiceDialog
import app.olauncher.ui.SettingsFragment
import app.olauncher.helper.createMultiChoiceDialog
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

internal fun SettingsFragment.populateProfileSchedule() {
    binding.profileSchedule.text = if (prefs.hasSavedProfileSchedule) {
        getString(R.string.profile_schedule_rule_count, prefs.profileSchedule().rules.size)
    } else {
        getString(R.string.profile_schedule_from_sets)
    }
}

private fun SettingsFragment.savedProfiles(): List<Profile> = try {
    Profile.listFromJson(prefs.profilesJson)
} catch (e: ProfileFormatException) {
    emptyList()
}

private fun SettingsFragment.profileChoices(): List<Pair<String, String>> =
    listOf(
        HomeSetProfiles.MORNING_ID to getString(R.string.profile_name_morning),
        HomeSetProfiles.DAY_ID to getString(R.string.profile_name_day),
        HomeSetProfiles.EVENING_ID to getString(R.string.profile_name_evening),
    ) + savedProfiles().map { it.id to it.name }

private fun SettingsFragment.profileName(id: String): String =
    profileChoices().firstOrNull { it.first == id }?.second ?: id

private fun SettingsFragment.daysLabel(days: Set<Int>): String = when {
    days.size == 7 -> getString(R.string.profile_rule_every_day)
    days.isEmpty() -> getString(R.string.profile_rule_no_days)
    else -> days.sorted().joinToString(" ") { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
}

private fun SettingsFragment.ruleLabel(rule: ProfileRule): String =
    "${profileName(rule.profileId)}  ${daysLabel(rule.weekdays)}  " +
        "${formatMinutes(rule.startMinutes)}–${formatMinutes(rule.endMinutes)}"

private fun SettingsFragment.saveProfileSchedule(schedule: ProfileSchedule) {
    prefs.profileScheduleJson = schedule.toJson()
    populateProfileSchedule()
    viewModel.refreshHome(false)
}

private fun SettingsFragment.openDialog(next: app.olauncher.helper.OlDialog) {
    dialog?.dismiss()
    dialog = next
    next.showRespectingStatusBar()
}

/** The rule list. The first edit saves the schedule built from the old Home-set times. */
internal fun SettingsFragment.showProfileScheduleDialog() {
    val schedule = prefs.profileSchedule()
    val items = schedule.rules.map { ruleLabel(it) }.toMutableList()
    items.add(getString(R.string.profile_schedule_add))
    val resetIndex = if (prefs.hasSavedProfileSchedule) items.size else -1
    if (resetIndex >= 0) items.add(getString(R.string.profile_schedule_reset))
    openDialog(
        requireContext().createChoiceDialog(title = R.string.profile_schedule, items = items) { which ->
            when {
                which < schedule.rules.size -> showProfileRuleDialog(schedule, which)
                which == schedule.rules.size -> {
                    val added = ProfileRule(HomeSetProfiles.DAY_ID, 9 * 60, 17 * 60, (1..5).toSet())
                    val next = schedule.copy(rules = schedule.rules + added)
                    saveProfileSchedule(next)
                    showProfileRuleDialog(next, next.rules.lastIndex)
                }
                which == resetIndex -> {
                    prefs.profileScheduleJson = ""
                    populateProfileSchedule()
                    viewModel.refreshHome(false)
                    showProfileScheduleDialog()
                }
            }
        }
    )
}

private fun SettingsFragment.showProfileRuleDialog(schedule: ProfileSchedule, index: Int) {
    val rule = schedule.rules[index]
    fun update(changed: ProfileRule) {
        val next = schedule.copy(rules = schedule.rules.toMutableList().also { it[index] = changed })
        saveProfileSchedule(next)
        showProfileRuleDialog(next, index)
    }
    val items = listOf(
        "${getString(R.string.profile_rule_profile)}: ${profileName(rule.profileId)}",
        "${getString(R.string.profile_rule_starts)}: ${formatMinutes(rule.startMinutes)}",
        "${getString(R.string.profile_rule_ends)}: ${formatMinutes(rule.endMinutes)}",
        "${getString(R.string.profile_rule_days)}: ${daysLabel(rule.weekdays)}",
        getString(R.string.profile_rule_delete),
    )
    openDialog(
        requireContext().createChoiceDialog(titleText = ruleLabel(rule), items = items) { which ->
            when (which) {
                0 -> {
                    val choices = profileChoices()
                    openDialog(
                        requireContext().createChoiceDialog(
                            title = R.string.profile_rule_profile,
                            items = choices.map { it.second },
                            checkedIndex = choices.indexOfFirst { it.first == rule.profileId },
                        ) { pick -> update(rule.copy(profileId = choices[pick].first)) }
                    )
                }
                1 -> showTimePicker(R.string.profile_rule_starts, rule.startMinutes / 60, rule.startMinutes % 60) { h, m ->
                    update(rule.copy(startMinutes = h * 60 + m))
                }
                2 -> showTimePicker(R.string.profile_rule_ends, rule.endMinutes / 60, rule.endMinutes % 60) { h, m ->
                    update(rule.copy(endMinutes = h * 60 + m))
                }
                3 -> {
                    val checked = BooleanArray(7) { (it + 1) in rule.weekdays }
                    val names = (1..7).map { DayOfWeek.of(it).getDisplayName(TextStyle.FULL, Locale.getDefault()) }
                    openDialog(
                        requireContext().createMultiChoiceDialog(
                            title = R.string.profile_rule_days,
                            items = names,
                            checked = checked,
                            action = R.string.save,
                        ) {
                            update(rule.copy(weekdays = (1..7).filter { checked[it - 1] }.toSet()))
                        }
                    )
                }
                4 -> {
                    val next = schedule.copy(rules = schedule.rules.filterIndexed { i, _ -> i != index })
                    saveProfileSchedule(next)
                    showProfileScheduleDialog()
                }
            }
        }
    )
}
