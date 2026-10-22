package app.olauncher.ui.settings

import android.view.View
import android.widget.TextView
import app.olauncher.R
import app.olauncher.ui.SettingsFragment

internal data class RuntimeSettingsRow(
    val sectionKey: String,
    val rowView: View,
    val labelView: TextView,
    val viewId: Int,
)

internal fun SettingsFragment.initRuntimeRows() {
    runtimeRows =
        homeRuntimeRows() +
        shortcutsRuntimeRows() +
        drawerRuntimeRows() +
        wellbeingRuntimeRows() +
        todayRuntimeRows() +
        deutschRuntimeRows() +
        appearanceRuntimeRows() +
        gesturesRuntimeRows() +
        backupRuntimeRows()
    for (item in runtimeRows) {
        if (!item.rowView.hasOnClickListeners()) {
            item.rowView.setOnClickListener {
                binding.root.findViewById<View>(item.viewId)?.performClick()
            }
        }
        if (!item.labelView.hasOnClickListeners()) {
            item.labelView.setOnClickListener {
                binding.root.findViewById<View>(item.viewId)?.performClick()
            }
        }
    }
}

private fun SettingsFragment.homeRuntimeRows(): List<RuntimeSettingsRow> = listOf(
        RuntimeSettingsRow(SettingsSections.KEY_HOME, binding.homeAppsNum.parent as View, getFirstTextView(binding.homeAppsNum.parent as View), R.id.homeAppsNum),
        RuntimeSettingsRow(SettingsSections.KEY_HOME, binding.dateTime.parent as View, getFirstTextView(binding.dateTime.parent as View), R.id.dateTime),
        RuntimeSettingsRow(SettingsSections.KEY_HOME, binding.batteryModeLayout, binding.batteryModeTitle, R.id.batteryMode),
        RuntimeSettingsRow(SettingsSections.KEY_HOME, binding.infoLineLayout, binding.infoLineTitle, R.id.infoLine),
        RuntimeSettingsRow(SettingsSections.KEY_HOME, binding.homeSetsLayout, binding.homeSetsTitle, R.id.homeSets),
        RuntimeSettingsRow(SettingsSections.KEY_HOME, binding.editMorningAppsLayout, binding.editMorningAppsTitle, R.id.editMorningAppsLayout),
        RuntimeSettingsRow(SettingsSections.KEY_HOME, binding.editEveningAppsLayout, binding.editEveningAppsTitle, R.id.editEveningAppsLayout),
        RuntimeSettingsRow(SettingsSections.KEY_HOME, binding.morningStartLayout, binding.morningStartTitle, R.id.morningStart),
        RuntimeSettingsRow(SettingsSections.KEY_HOME, binding.dayStartLayout, binding.dayStartTitle, R.id.dayStart),
        RuntimeSettingsRow(SettingsSections.KEY_HOME, binding.eveningStartLayout, binding.eveningStartTitle, R.id.eveningStart),
        RuntimeSettingsRow(SettingsSections.KEY_HOME, binding.contextLineLayout, binding.contextLineTitle, R.id.contextLineValue),
        RuntimeSettingsRow(SettingsSections.KEY_HOME, binding.homeNextEventLayout, binding.homeNextEventTitle, R.id.homeNextEventValue),
        RuntimeSettingsRow(SettingsSections.KEY_HOME, binding.alignment.parent as View, getFirstTextView(binding.alignment.parent as View), R.id.alignment),
        RuntimeSettingsRow(SettingsSections.KEY_HOME, binding.swapSidesLayout, binding.swapSides, R.id.swapSides),
)

private fun SettingsFragment.shortcutsRuntimeRows(): List<RuntimeSettingsRow> = listOf(
        RuntimeSettingsRow(SettingsSections.KEY_SHORTCUTS, binding.shortcutIconsOnOffLayout, binding.shortcutIconsOnOffTitle, R.id.shortcutIconsOnOff),
        RuntimeSettingsRow(SettingsSections.KEY_SHORTCUTS, binding.shortcutCountLayout, binding.shortcutCountTitle, R.id.shortcutCount),
        RuntimeSettingsRow(SettingsSections.KEY_SHORTCUTS, binding.shortcutSizeLayout, binding.shortcutSizeTitle, R.id.shortcutSize),
        RuntimeSettingsRow(SettingsSections.KEY_SHORTCUTS, binding.shortcutSideLayout, binding.shortcutSideTitle, R.id.shortcutSide),
        RuntimeSettingsRow(SettingsSections.KEY_SHORTCUTS, binding.shortcutHeightLayout, binding.shortcutHeightTitle, R.id.shortcutHeight),
        RuntimeSettingsRow(SettingsSections.KEY_SHORTCUTS, binding.shortcutIconStyleLayout, binding.shortcutIconStyleTitle, R.id.shortcutIconStyle),
)

private fun SettingsFragment.drawerRuntimeRows(): List<RuntimeSettingsRow> = listOf(
        RuntimeSettingsRow(SettingsSections.KEY_DRAWER, binding.autoShowKeyboard.parent as View, getFirstTextView(binding.autoShowKeyboard.parent as View), R.id.autoShowKeyboard),
        RuntimeSettingsRow(SettingsSections.KEY_DRAWER, binding.drawerBottomSearchLayout, binding.drawerBottomSearchTitle, R.id.drawerBottomSearch),
        RuntimeSettingsRow(SettingsSections.KEY_DRAWER, binding.searchEngineLayout, binding.searchEngineTitle, R.id.searchEngine),
        RuntimeSettingsRow(SettingsSections.KEY_DRAWER, binding.drawerNewFirstLayout, binding.drawerNewFirstTitle, R.id.drawerNewFirst),
        RuntimeSettingsRow(SettingsSections.KEY_DRAWER, binding.drawerLetterStripLayout, binding.drawerLetterStripTitle, R.id.drawerLetterStrip),
        RuntimeSettingsRow(SettingsSections.KEY_DRAWER, binding.drawerRecentsLayout, binding.drawerRecentsTitle, R.id.drawerRecents),
        RuntimeSettingsRow(SettingsSections.KEY_DRAWER, binding.searchContactsLayout, binding.searchContactsTitle, R.id.searchContacts),
        RuntimeSettingsRow(SettingsSections.KEY_DRAWER, binding.currencyRatesLayout, binding.currencyRatesTitle, R.id.currencyRates),
)

private fun SettingsFragment.wellbeingRuntimeRows(): List<RuntimeSettingsRow> = listOf(
        RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.distractingAppsLayout, binding.distractingAppsTitle, R.id.distractingApps),
        RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.pauseBeforeAppsLayout, binding.pauseBeforeAppsTitle, R.id.pauseBeforeAppsOnOff),
        RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.pauseAllowanceLayout, binding.pauseAllowanceTitle, R.id.pauseAllowance),
        RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.pauseEscalateLayout, binding.pauseEscalateTitle, R.id.pauseEscalateOnOff),
        RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.pauseAskWhyLayout, binding.pauseAskWhyTitle, R.id.pauseAskWhyOnOff),
        RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.windDownLayout, binding.windDownTitle, R.id.windDown),
        RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.windDownGreyLayout, binding.windDownGreyTitle, R.id.windDownGreyOnOff),
        RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.systemGreyLayout, binding.systemGreyTitle, R.id.systemGreyOnOff),
        RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.commitLockLayout, binding.commitLockTitle, R.id.commitLockOnOff),
        RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.holdNotificationsLayout, binding.holdNotificationsTitle, R.id.holdNotificationsOnOff),
        RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.healthConnectLayout, binding.healthConnectTitle, R.id.healthConnectOnOff),
        RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.quietReleaseTimesLayout, binding.quietReleaseTimesTitle, R.id.quietReleaseTimes),
        RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.screenTimeLayout, getFirstTextView(binding.screenTimeLayout), R.id.screenTimeOnOff),
        RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.screenTimeStyleLayout, binding.screenTimeStyleTitle, R.id.screenTimeStyle),
        RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.screenTimeTargetLayout, binding.screenTimeTargetTitle, R.id.screenTimeTarget),
        RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.nudgeWaterLayout, binding.nudgeWaterTitle, R.id.nudgeWater),
        RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.nudgeWalkLayout, binding.nudgeWalkTitle, R.id.nudgeWalk),
        RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.nudgeSocialLayout, binding.nudgeSocialTitle, R.id.nudgeSocial),
        RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.nudgeActiveHoursLayout, binding.nudgeActiveHoursTitle, R.id.nudgeActiveHours),
)

private fun SettingsFragment.todayRuntimeRows(): List<RuntimeSettingsRow> = listOf(
        RuntimeSettingsRow(SettingsSections.KEY_TODAY, binding.todayCardsLayout, binding.todayCardsTitle, R.id.todayCards),
        RuntimeSettingsRow(SettingsSections.KEY_TODAY, binding.todayOrderLayout, binding.todayOrderTitle, R.id.todayOrder),
        RuntimeSettingsRow(SettingsSections.KEY_TODAY, binding.intentionOnHomeLayout, binding.intentionOnHomeTitle, R.id.intentionOnHome),
        RuntimeSettingsRow(SettingsSections.KEY_TODAY, binding.weatherLayout, binding.weatherTitle, R.id.weather),
)

private fun SettingsFragment.deutschRuntimeRows(): List<RuntimeSettingsRow> = listOf(
        RuntimeSettingsRow(SettingsSections.KEY_DEUTSCH, binding.germanLayout, binding.germanTitle, R.id.germanOnOff),
        RuntimeSettingsRow(SettingsSections.KEY_DEUTSCH, binding.germanClockLayout, binding.germanClockTitle, R.id.germanClock),
        RuntimeSettingsRow(SettingsSections.KEY_DEUTSCH, binding.germanLookupLayout, binding.germanLookupTitle, R.id.germanLookupOnOff),
        RuntimeSettingsRow(SettingsSections.KEY_DEUTSCH, binding.germanSpellSuggestLayout, binding.germanSpellSuggestTitle, R.id.germanSpellSuggestOnOff),
        RuntimeSettingsRow(SettingsSections.KEY_DEUTSCH, binding.germanScreenPickLayout, binding.germanScreenPickTitle, R.id.germanScreenPickOnOff),
        RuntimeSettingsRow(SettingsSections.KEY_DEUTSCH, binding.germanPauseWordLayout, binding.germanPauseWordTitle, R.id.germanPauseWordOnOff),
        RuntimeSettingsRow(SettingsSections.KEY_DEUTSCH, binding.germanUiLayout, binding.germanUiTitle, R.id.germanUiOnOff),
)

private fun SettingsFragment.appearanceRuntimeRows(): List<RuntimeSettingsRow> = listOf(
        RuntimeSettingsRow(SettingsSections.KEY_APPEARANCE, binding.photoWallpaperLayout, binding.photoWallpaperTitle, R.id.photoWallpaper),
        RuntimeSettingsRow(SettingsSections.KEY_APPEARANCE, binding.statusBar.parent as View, getFirstTextView(binding.statusBar.parent as View), R.id.statusBar),
        RuntimeSettingsRow(SettingsSections.KEY_APPEARANCE, binding.appThemeText.parent as View, getFirstTextView(binding.appThemeText.parent as View), R.id.appThemeText),
        RuntimeSettingsRow(SettingsSections.KEY_APPEARANCE, binding.accent.parent as View, getFirstTextView(binding.accent.parent as View), R.id.accent),
        RuntimeSettingsRow(SettingsSections.KEY_APPEARANCE, binding.fontFamily.parent as View, getFirstTextView(binding.fontFamily.parent as View), R.id.fontFamily),
        RuntimeSettingsRow(SettingsSections.KEY_APPEARANCE, binding.boldFont.parent as View, getFirstTextView(binding.boldFont.parent as View), R.id.boldFont),
        RuntimeSettingsRow(SettingsSections.KEY_APPEARANCE, binding.textSizeValue.parent as View, getFirstTextView(binding.textSizeValue.parent as View), R.id.textSizeValue),
        RuntimeSettingsRow(SettingsSections.KEY_APPEARANCE, binding.haptics.parent as View, getFirstTextView(binding.haptics.parent as View), R.id.haptics),
        RuntimeSettingsRow(SettingsSections.KEY_APPEARANCE, binding.hapticStrengthLayout, binding.hapticStrengthTitle, R.id.hapticStrength),
        RuntimeSettingsRow(SettingsSections.KEY_APPEARANCE, binding.hapticDurationLayout, binding.hapticDurationTitle, R.id.hapticDuration),
        RuntimeSettingsRow(SettingsSections.KEY_APPEARANCE, binding.textContrastLayout, binding.textContrastTitle, R.id.textContrast),
        RuntimeSettingsRow(SettingsSections.KEY_APPEARANCE, binding.homeWashLayout, binding.homeWashTitle, R.id.homeDarkness),
        RuntimeSettingsRow(SettingsSections.KEY_APPEARANCE, binding.textBrightnessLayout, binding.textBrightnessTitle, R.id.textBrightness),
        RuntimeSettingsRow(SettingsSections.KEY_APPEARANCE, binding.musicPulseLayout, binding.musicPulseTitle, R.id.musicPulse),
        RuntimeSettingsRow(SettingsSections.KEY_APPEARANCE, binding.glowStrengthLayout, binding.glowStrengthTitle, R.id.glowStrength),
        RuntimeSettingsRow(SettingsSections.KEY_APPEARANCE, binding.weatherEffectsLayout, binding.weatherEffectsTitle, R.id.weatherEffects),
)

private fun SettingsFragment.gesturesRuntimeRows(): List<RuntimeSettingsRow> = listOf(
        RuntimeSettingsRow(SettingsSections.KEY_GESTURES, binding.swipeLeftApp.parent as View, getFirstTextView(binding.swipeLeftApp.parent as View), R.id.swipeLeftApp),
        RuntimeSettingsRow(SettingsSections.KEY_GESTURES, binding.swipeRightLayout, getFirstTextView(binding.swipeRightLayout), R.id.swipeRightApp),
        RuntimeSettingsRow(SettingsSections.KEY_GESTURES, binding.toggleLock.parent as View, getFirstTextView(binding.toggleLock.parent as View), R.id.toggleLock),
)

private fun SettingsFragment.backupRuntimeRows(): List<RuntimeSettingsRow> = listOf(
        RuntimeSettingsRow(SettingsSections.KEY_BACKUP, binding.exportBackupLayout, binding.exportBackup, R.id.exportBackup),
        RuntimeSettingsRow(SettingsSections.KEY_BACKUP, binding.autoBackupLayout, binding.autoBackupTitle, R.id.autoBackup),
        RuntimeSettingsRow(SettingsSections.KEY_BACKUP, binding.importBackupLayout, binding.importBackup, R.id.importBackup),
)
