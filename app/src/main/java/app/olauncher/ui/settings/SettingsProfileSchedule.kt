package app.olauncher.ui.settings

import app.olauncher.R
import app.olauncher.data.HomeSetProfiles
import app.olauncher.data.ProfileEventRule
import app.olauncher.data.ProfileRule
import app.olauncher.data.ProfileSchedule
import app.olauncher.glance.CalendarRepository
import app.olauncher.helper.createChoiceDialog
import app.olauncher.helper.profileChoices
import app.olauncher.helper.createDialog
import app.olauncher.helper.showMessageDialog
import app.olauncher.ui.SettingsFragment
import app.olauncher.helper.createMultiChoiceDialog
import android.Manifest
import android.content.pm.PackageManager
import android.text.InputType
import android.widget.EditText
import androidx.core.content.ContextCompat
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun SettingsFragment.populateProfileSchedule() {
    binding.profileSchedule.text = if (prefs.hasSavedProfileSchedule) {
        getString(R.string.profile_schedule_rule_count, prefs.profileSchedule().rules.size + prefs.profileSchedule().eventRules.size)
    } else {
        getString(R.string.profile_schedule_from_sets)
    }
}

/** Every profile the `:p` list offers: the built-in ones, the Morning/Day/Evening ones and saved ones. */
private fun SettingsFragment.profileChoices(): List<Pair<String, String>> =
    prefs.profileChoices(requireContext(), includeSets = true).distinctBy { it.id }.map { it.id to it.name }

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
internal fun SettingsFragment.showProfileScheduleDialog() = withCalendars { showProfileScheduleList() }

private fun SettingsFragment.showProfileScheduleList() {
    val schedule = prefs.profileSchedule()
    val items = schedule.rules.map { ruleLabel(it) }.toMutableList()
    items.add(getString(R.string.profile_schedule_add))
    val eventStart = items.size
    items.addAll(schedule.eventRules.map { eventRuleLabel(it) })
    val addEventIndex = items.size
    items.add(getString(R.string.profile_event_add))
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
                which in eventStart until addEventIndex -> showEventRuleDialog(schedule, which - eventStart)
                which == addEventIndex -> {
                    val added = ProfileEventRule(HomeSetProfiles.DAY_ID, keyword = "work")
                    val next = schedule.copy(eventRules = schedule.eventRules + added)
                    saveProfileSchedule(next)
                    showEventRuleDialog(next, next.eventRules.lastIndex)
                    askCalendarAccessIfMissing()
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

private fun SettingsFragment.calendarGranted(): Boolean =
    ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

/** Calendar access is asked only when the first event rule is added. A denial leaves the rule saved but skipped. */
private fun SettingsFragment.askCalendarAccessIfMissing() {
    if (calendarGranted()) return
    dialog?.dismiss()
    dialog = requireContext().showMessageDialog(
        title = R.string.profile_event_title,
        message = R.string.profile_event_access_message,
        action = R.string.allow,
    ) {
        homeNextEventCalendarLauncher.launch(Manifest.permission.READ_CALENDAR)
    }
}

/** Runs [then] once the calendar names are cached; reads them off the main thread the first time. */
private fun SettingsFragment.withCalendars(then: () -> Unit) {
    if (profileCalendars != null || !calendarGranted()) {
        then()
        return
    }
    val repository = CalendarRepository(requireContext().applicationContext)
    viewLifecycleOwner.lifecycleScope.launch {
        val loaded = try {
            withContext(Dispatchers.IO) { repository.calendars() }
        } catch (e: RuntimeException) {
            emptyList()
        }
        profileCalendars = loaded
        if (isAdded) then()
    }
}

private fun SettingsFragment.calendarName(id: Long?): String =
    if (id == null) getString(R.string.profile_event_any_calendar)
    else profileCalendars.orEmpty().firstOrNull { it.first == id }?.second ?: id.toString()

private fun SettingsFragment.eventRuleLabel(rule: ProfileEventRule): String {
    val what = listOfNotNull(
        rule.calendarId?.let { calendarName(it) },
        rule.keyword.takeIf { it.isNotBlank() }?.let { "\"$it\"" },
    ).joinToString(" ")
    val label = getString(R.string.profile_event_label, profileName(rule.profileId), what)
    return if (calendarGranted()) label else "$label (${getString(R.string.profile_event_needs_access)})"
}

private fun SettingsFragment.showEventRuleDialog(schedule: ProfileSchedule, index: Int) =
    withCalendars { showEventRule(schedule, index) }

private fun SettingsFragment.showEventRule(schedule: ProfileSchedule, index: Int) {
    val rule = schedule.eventRules[index]
    fun update(changed: ProfileEventRule) {
        val next = schedule.copy(eventRules = schedule.eventRules.toMutableList().also { it[index] = changed })
        saveProfileSchedule(next)
        showEventRuleDialog(next, index)
    }
    val onOff = { on: Boolean -> getString(if (on) R.string.on else R.string.off) }
    val items = listOf(
        "${getString(R.string.profile_rule_profile)}: ${profileName(rule.profileId)}",
        "${getString(R.string.profile_event_calendar)}: ${calendarName(rule.calendarId)}",
        "${getString(R.string.profile_event_keyword)}: ${rule.keyword.ifBlank { getString(R.string.profile_event_keyword_none) }}",
        "${getString(R.string.profile_event_all_day)}: ${onOff(rule.includeAllDay)}",
        getString(R.string.profile_rule_delete),
    )
    openDialog(
        requireContext().createChoiceDialog(titleText = eventRuleLabel(rule), items = items) { which ->
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
                1 -> {
                    if (!calendarGranted()) {
                        askCalendarAccessIfMissing()
                        return@createChoiceDialog
                    }
                    val calendars = profileCalendars.orEmpty()
                    val names = listOf(getString(R.string.profile_event_any_calendar)) + calendars.map { it.second }
                    openDialog(
                        requireContext().createChoiceDialog(
                            title = R.string.profile_event_calendar,
                            items = names,
                            checkedIndex = calendars.indexOfFirst { it.first == rule.calendarId } + 1,
                        ) { pick ->
                            val id = if (pick == 0) null else calendars[pick - 1].first
                            // A rule must keep a calendar or a keyword.
                            if (id == null && rule.keyword.isBlank()) showEventRuleDialog(schedule, index) else update(rule.copy(calendarId = id))
                        }
                    )
                }
                2 -> {
                    val input = EditText(requireContext()).apply {
                        inputType = InputType.TYPE_CLASS_TEXT
                        setSingleLine()
                        setText(rule.keyword)
                    }
                    openDialog(
                        requireContext().createDialog(
                            title = R.string.profile_event_keyword,
                            action = R.string.save,
                            content = { input },
                            onAction = {
                                val word = input.text.toString().trim()
                                if (word.isBlank() && rule.calendarId == null) showEventRuleDialog(schedule, index)
                                else update(rule.copy(keyword = word))
                            },
                        )
                    )
                }
                3 -> update(rule.copy(includeAllDay = !rule.includeAllDay))
                4 -> {
                    val next = schedule.copy(eventRules = schedule.eventRules.filterIndexed { i, _ -> i != index })
                    saveProfileSchedule(next)
                    showProfileScheduleDialog()
                }
            }
        }
    )
}
