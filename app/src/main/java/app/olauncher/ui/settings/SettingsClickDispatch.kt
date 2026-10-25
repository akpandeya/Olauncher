package app.olauncher.ui.settings

import android.os.Process
import android.view.View
import app.olauncher.BuildConfig
import app.olauncher.R
import app.olauncher.data.Constants
import app.olauncher.helper.GestureSlot
import app.olauncher.helper.Haptics
import app.olauncher.helper.HomeSet
import app.olauncher.helper.openAppInfo
import app.olauncher.helper.openUrl
import app.olauncher.ui.SettingsFragment
import app.olauncher.wellbeing.SystemGrey

/** Runs [action] and gives one tick if it changed any pref; none for dialogs or denied toggles. */
internal fun SettingsFragment.tickOnChange(view: View, action: () -> Unit) {
    var changed = false
    val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> changed = true }
    prefs.registerPreferenceChangeListener(listener)
    try {
        action()
    } finally {
        prefs.unregisterPreferenceChangeListener(listener)
    }
    if (changed) Haptics.perform(view, Haptics.Kind.CLOCK_TICK)
}

/** Runs the tap for [view]; false when no setting owns that view id. */
internal fun SettingsFragment.dispatchClick(view: View): Boolean =
    handleNavigationClick(view)
    || handleToggleRowClick(view)
    || handleWellbeingClick(view)
    || handleLauncherAndDrawerClick(view)
    || handleHomeAndShortcutsClick(view)
    || handleAppearanceClick(view)
    || handleTodayAndNudgesClick(view)
    || handleGermanBackupFooterClick(view)

/** Plain on/off rows carry their pref in the row model, so one handler serves them all. */
private fun SettingsFragment.handleToggleRowClick(view: View): Boolean {
    val spec = SettingsCatalog.rowByViewId(view.id)?.takeIf { it.pref != null } ?: return false
    tickOnChange(view) { toggleBooleanRow(spec) }
    return true
}

private fun SettingsFragment.handleNavigationClick(view: View): Boolean {
    when (view.id) {
        R.id.settingsBack, R.id.settingsBackLayout -> { Haptics.tick(view); showSectionList() }
        R.id.sectionRowHome -> { Haptics.tick(view); showSection(SettingsSections.HOME) }
        R.id.sectionRowShortcuts -> { Haptics.tick(view); showSection(SettingsSections.SHORTCUTS) }
        R.id.sectionRowDrawer -> { Haptics.tick(view); showSection(SettingsSections.DRAWER) }
        R.id.sectionRowWellbeing -> { Haptics.tick(view); showSection(SettingsSections.WELLBEING) }
        R.id.sectionRowToday -> { Haptics.tick(view); showSection(SettingsSections.TODAY) }
        R.id.sectionRowDeutsch -> { Haptics.tick(view); showSection(SettingsSections.DEUTSCH) }
        R.id.sectionRowAppearance -> { Haptics.tick(view); showSection(SettingsSections.APPEARANCE) }
        R.id.sectionRowGestures -> { Haptics.tick(view); showSection(SettingsSections.GESTURES) }
        R.id.sectionRowBackup -> { Haptics.tick(view); showSection(SettingsSections.BACKUP) }
        R.id.olauncherHiddenApps -> { if (prefs.hiddenApps.isNotEmpty()) Haptics.tick(view); showHiddenApps() }
        else -> return false
    }
    return true
}

private fun SettingsFragment.handleWellbeingClick(view: View): Boolean {
    when (view.id) {
        R.id.distractingApps, R.id.distractingAppsTitle, R.id.distractingAppsLayout -> showDistractingAppsDialog()
        R.id.pauseBeforeAppsOnOff, R.id.pauseBeforeAppsTitle, R.id.pauseBeforeAppsLayout -> tickOnChange(view) { togglePauseBeforeApps() }
        R.id.pauseAllowance, R.id.pauseAllowanceTitle, R.id.pauseAllowanceLayout -> showPauseAllowanceMenu(binding.pauseAllowance)
        R.id.pauseEscalateOnOff, R.id.pauseEscalateTitle, R.id.pauseEscalateLayout -> tickOnChange(view) { togglePauseEscalate() }
        R.id.pauseAskWhyOnOff, R.id.pauseAskWhyTitle, R.id.pauseAskWhyLayout -> tickOnChange(view) { togglePauseAskWhy() }
        R.id.windDown, R.id.windDownTitle, R.id.windDownLayout -> showWindDownMenu()
        R.id.windDownGreyOnOff, R.id.windDownGreyTitle, R.id.windDownGreyLayout -> tickOnChange(view) { toggleWindDownGrey() }
        R.id.systemGreyOnOff, R.id.systemGreyTitle, R.id.systemGreyLayout -> tickOnChange(view) { toggleSystemGrey() }
        R.id.systemGreyCopy -> copySystemGreyCommand()
        R.id.systemGreyBedtime -> SystemGrey.openBedtimeMode(requireContext())
        R.id.commitLockOnOff, R.id.commitLockTitle, R.id.commitLockSubtitle, R.id.commitLockLayout -> tickOnChange(view) { toggleCommitLock() }
        R.id.holdNotificationsOnOff, R.id.holdNotificationsTitle, R.id.holdNotificationsLayout -> tickOnChange(view) { toggleHoldNotifications() }
        R.id.healthConnectOnOff, R.id.healthConnectTitle, R.id.healthConnectLayout -> tickOnChange(view) { toggleHealthConnect() }
        R.id.quietReleaseTimes, R.id.quietReleaseTimesTitle, R.id.quietReleaseTimesLayout -> showReleaseTimesEditor()
        R.id.screenTimeOnOff -> viewModel.showDialog.postValue(Constants.Dialog.DIGITAL_WELLBEING)
        R.id.screenTimeStyle, R.id.screenTimeStyleTitle, R.id.screenTimeStyleLayout -> showScreenTimeStyleMenu(binding.screenTimeStyle)
        R.id.screenTimeTarget, R.id.screenTimeTargetTitle, R.id.screenTimeTargetLayout -> showScreenTimeTargetMenu(binding.screenTimeTarget)
        else -> return false
    }
    return true
}

private fun SettingsFragment.handleLauncherAndDrawerClick(view: View): Boolean {
    when (view.id) {
        R.id.appInfo -> openAppInfo(requireContext(), Process.myUserHandle(), BuildConfig.APPLICATION_ID)
        R.id.setLauncher -> viewModel.resetLauncherLiveData.call()
        R.id.toggleLock -> tickOnChange(view) { toggleLockMode() }
        // Home button for recents feature disabled
        // R.id.homeButtonRecents -> toggleHomeButtonRecents()
        R.id.autoShowKeyboard -> tickOnChange(view) { toggleKeyboardText() }
        R.id.searchContacts, R.id.searchContactsTitle, R.id.searchContactsLayout -> tickOnChange(view) { toggleSearchContacts() }
        R.id.currencyRates, R.id.currencyRatesTitle, R.id.currencyRatesLayout -> tickOnChange(view) { toggleCurrencyRates() }
        R.id.searchEngine, R.id.searchEngineTitle, R.id.searchEngineLayout -> showSearchEngineMenu(binding.searchEngine)
        else -> return false
    }
    return true
}

private fun SettingsFragment.handleHomeAndShortcutsClick(view: View): Boolean {
    when (view.id) {
        R.id.homeAppsNum -> showHomeAppsNumMenu(view)
        R.id.alignment -> showAlignmentMenu(view)
        R.id.swapSides, R.id.swapSidesLayout -> swapSides()
        R.id.shortcutIconsOnOff, R.id.shortcutIconsOnOffTitle, R.id.shortcutIconsOnOffLayout -> tickOnChange(view) { toggleShortcutIcons() }
        R.id.shortcutCount, R.id.shortcutCountTitle, R.id.shortcutCountLayout -> showShortcutCountDialog()
        R.id.shortcutSize, R.id.shortcutSizeTitle, R.id.shortcutSizeLayout -> showShortcutSizeDialog()
        R.id.shortcutSide, R.id.shortcutSideTitle, R.id.shortcutSideLayout -> tickOnChange(view) { toggleShortcutSide() }
        R.id.shortcutHeight, R.id.shortcutHeightTitle, R.id.shortcutHeightLayout -> showShortcutHeightDialog()
        R.id.shortcutIconStyle, R.id.shortcutIconStyleTitle, R.id.shortcutIconStyleLayout -> showShortcutIconStyleDialog()
        R.id.statusBar -> tickOnChange(view) { toggleStatusBar() }
        R.id.dateTime -> showDateTimeMenu(view)
        R.id.batteryMode, R.id.batteryModeTitle, R.id.batteryModeLayout -> showBatteryModeMenu(binding.batteryMode)
        R.id.infoLine, R.id.infoLineTitle, R.id.infoLineLayout -> tickOnChange(view) { toggleInfoLine() }
        R.id.homeSets, R.id.homeSetsTitle, R.id.homeSetsLayout -> tickOnChange(view) { toggleHomeSets() }
        R.id.editMorningAppsTitle, R.id.editMorningAppsLayout -> showHomeSetSlotsDialog(HomeSet.MORNING)
        R.id.editEveningAppsTitle, R.id.editEveningAppsLayout -> showHomeSetSlotsDialog(HomeSet.EVENING)
        R.id.morningStart, R.id.morningStartTitle, R.id.morningStartLayout -> showHomeSetStartPicker(HomeSet.MORNING, R.string.morning_starts)
        R.id.dayStart, R.id.dayStartTitle, R.id.dayStartLayout -> showHomeSetStartPicker(HomeSet.DAY, R.string.day_starts)
        R.id.eveningStart, R.id.eveningStartTitle, R.id.eveningStartLayout -> showHomeSetStartPicker(HomeSet.EVENING, R.string.evening_starts)
        R.id.profileSwitch, R.id.profileSwitchTitle, R.id.profileSwitchLayout -> showProfileSwitchDialog()
        R.id.profileSchedule, R.id.profileScheduleTitle, R.id.profileScheduleLayout -> showProfileScheduleDialog()
        R.id.contextLineValue, R.id.contextLineTitle, R.id.contextLineLayout -> tickOnChange(view) { toggleContextLine() }
        R.id.homeNextEventValue, R.id.homeNextEventTitle, R.id.homeNextEventLayout -> tickOnChange(view) { toggleHomeNextEvent() }
        R.id.homeNotesValue, R.id.homeNotesTitle, R.id.homeNotesLayout -> tickOnChange(view) { toggleHomeNotes() }
        else -> return false
    }
    return true
}

private fun SettingsFragment.handleAppearanceClick(view: View): Boolean {
    when (view.id) {
        R.id.appThemeText -> showAppThemeMenu(view, showSystem = false)
        R.id.accent -> showAccentMenu(view)
        R.id.fontFamily -> showFontDialog()
        R.id.textSizeValue -> showTextSizeDialog()
        R.id.boldFont -> tickOnChange(view) { toggleBoldFont() }
        R.id.textContrast, R.id.textContrastTitle, R.id.textContrastLayout -> tickOnChange(view) { toggleTextContrast() }
        R.id.homeDarkness, R.id.homeWashTitle, R.id.homeWashLayout -> cycleHomeDarkness()
        R.id.textBrightness, R.id.textBrightnessTitle, R.id.textBrightnessLayout -> cycleTextBrightness()
        R.id.musicPulse, R.id.musicPulseTitle, R.id.musicPulseLayout -> tickOnChange(view) { toggleMusicPulse() }
        R.id.weatherEffects, R.id.weatherEffectsTitle, R.id.weatherEffectsLayout -> tickOnChange(view) { toggleWeatherEffects() }
        R.id.weatherIcons, R.id.weatherIconsTitle, R.id.weatherIconsLayout -> tickOnChange(view) { toggleWeatherIcons() }

        else -> return false
    }
    return true
}

private fun SettingsFragment.handleTodayAndNudgesClick(view: View): Boolean {
    when (view.id) {
        R.id.photoWallpaper, R.id.photoWallpaperTitle, R.id.photoWallpaperLayout -> showPhotoWallpaperMenu(binding.photoWallpaper)
        R.id.doubleTapAction -> showGestureActionMenu(GestureSlot.DOUBLE_TAP)
        R.id.clockTapAction -> showGestureActionMenu(GestureSlot.CLOCK_TAP)
        R.id.dateTapAction -> showGestureActionMenu(GestureSlot.DATE_TAP)
        R.id.swipeLeftApp -> showSwipeLeftMenu(binding.swipeLeftApp)
        R.id.swipeRightApp, R.id.swipeRightLayout -> showSwipeRightMenu(binding.swipeRightApp)
        R.id.todayCards, R.id.todayCardsTitle, R.id.todayCardsLayout -> showTodayCardsDialog()
        R.id.todayOrder, R.id.todayOrderTitle, R.id.todayOrderLayout -> tickOnChange(view) { toggleTodayOrder() }
        R.id.weatherTitle, R.id.weatherLayout -> showCitySearchDialog()
        R.id.weather -> {
            if (prefs.weatherEnabled) {
                showWeatherMenu(binding.weather)
            } else {
                showCitySearchDialog()
            }
        }
        R.id.nudgeWater, R.id.nudgeWaterTitle, R.id.nudgeWaterLayout -> showNudgeWaterMenu(binding.nudgeWater)
        R.id.nudgeWalk, R.id.nudgeWalkTitle, R.id.nudgeWalkLayout -> showNudgeWalkMenu(binding.nudgeWalk)
        R.id.nudgeSocial, R.id.nudgeSocialTitle, R.id.nudgeSocialLayout -> showNudgeSocialMenu(binding.nudgeSocial)
        R.id.nudgeActiveHours, R.id.nudgeActiveHoursTitle, R.id.nudgeActiveHoursLayout -> showActiveHoursDialog()
        else -> return false
    }
    return true
}

private fun SettingsFragment.handleGermanBackupFooterClick(view: View): Boolean {
    when (view.id) {
        R.id.germanOnOff, R.id.germanTitle, R.id.germanLayout -> tickOnChange(view) { toggleGerman() }
        R.id.germanClock, R.id.germanClockTitle, R.id.germanClockLayout -> showGermanClockMenu(binding.germanClock)
        R.id.dictionaryApp, R.id.dictionaryAppTitle, R.id.dictionaryAppLayout -> showDictionaryAppMenu(binding.dictionaryApp)
        R.id.germanOfflineDictionaryOnOff, R.id.germanOfflineDictionaryTitle, R.id.germanOfflineDictionarySubtitle, R.id.germanOfflineDictionaryLayout -> tickOnChange(view) { toggleGermanOfflineDictionary() }
        R.id.germanLookupOnOff, R.id.germanLookupTitle, R.id.germanLookupSubtitle, R.id.germanLookupLayout -> tickOnChange(view) { toggleGermanLookup() }
        R.id.germanSpellSuggestOnOff, R.id.germanSpellSuggestTitle, R.id.germanSpellSuggestSubtitle, R.id.germanSpellSuggestLayout -> tickOnChange(view) { toggleGermanSpellSuggest() }
        R.id.germanScreenPickOnOff, R.id.germanScreenPickTitle, R.id.germanScreenPickSubtitle, R.id.germanScreenPickLayout -> tickOnChange(view) { toggleGermanScreenPick() }
        R.id.germanPauseWordOnOff, R.id.germanPauseWordTitle, R.id.germanPauseWordLayout -> tickOnChange(view) { toggleGermanPauseWord() }
        R.id.germanUiOnOff, R.id.germanUiTitle, R.id.germanUiLayout -> tickOnChange(view) { toggleGermanUi() }
        R.id.exportBackup, R.id.exportBackupLayout -> launchExportBackup()
        R.id.importBackup, R.id.importBackupLayout -> launchImportBackup()
        R.id.autoBackup, R.id.autoBackupTitle, R.id.autoBackupLayout -> tickOnChange(view) { toggleAutoBackup() }
        R.id.autoBackupStatus -> if (prefs.autoBackupLastFailed) launchAutoBackupFolderPicker()
        R.id.footer -> requireContext().openUrl(Constants.URL_SOURCE)
        else -> return false
    }
    return true
}
