package app.olauncher.ui.settings

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import app.olauncher.databinding.FragmentSettingsBinding

/**
 * The views the Settings code reaches by name. The fixed page frame comes from
 * [FragmentSettingsBinding]; the rows come from the list, so a row exists only while the
 * adapter shows it. Asking for a row view that is not shown returns a detached stand-in:
 * writes to it do nothing, so a populate function can run for any row at any time.
 * The row is filled in again when the adapter binds it.
 */
internal class SettingsViews(private val frame: FragmentSettingsBinding) {

    val root: View get() = frame.root
    val scrollView get() = frame.scrollView
    val statusBarFade get() = frame.statusBarFade
    val settingsRows: RecyclerView get() = frame.settingsRows
    val settingsSearch get() = frame.settingsSearch
    val settingsSearchContainer get() = frame.settingsSearchContainer
    val settingsBack get() = frame.settingsBack
    val settingsBackLayout get() = frame.settingsBackLayout
    val thumblineHeaderCard get() = frame.thumblineHeaderCard
    val olauncherHiddenApps get() = frame.olauncherHiddenApps
    val appInfo get() = frame.appInfo
    val setLauncher get() = frame.setLauncher
    val sectionListLayout get() = frame.sectionListLayout
    val sectionRowHome get() = frame.sectionRowHome
    val sectionRowShortcuts get() = frame.sectionRowShortcuts
    val sectionRowDrawer get() = frame.sectionRowDrawer
    val sectionRowWellbeing get() = frame.sectionRowWellbeing
    val sectionRowToday get() = frame.sectionRowToday
    val sectionRowDeutsch get() = frame.sectionRowDeutsch
    val sectionRowAppearance get() = frame.sectionRowAppearance
    val sectionRowGestures get() = frame.sectionRowGestures
    val sectionRowBackup get() = frame.sectionRowBackup
    val footer get() = frame.footer
    val licenseNote get() = frame.licenseNote

    private val shown = HashMap<Int, View>()
    private val standIns = HashMap<Int, View>()

    /** Records the views of a row the adapter is showing. */
    fun register(spec: SettingsRowSpec, holder: SettingsRowHolder) {
        shown.putAll(holder.viewsById(spec))
    }

    /** Forgets the views of [holder]'s row, unless another holder has taken the ids since. */
    fun unregister(spec: SettingsRowSpec, holder: SettingsRowHolder) {
        for ((id, view) in holder.viewsById(spec)) {
            if (shown[id] === view) shown.remove(id)
        }
    }

    /** The view with [id] if the adapter shows it now, else null. */
    fun shownView(id: Int): View? = shown[id]

    fun textView(id: Int): TextView =
        shown[id] as? TextView ?: standIns.getOrPut(id) { TextView(root.context) } as TextView

    fun seekBar(id: Int): SeekBar =
        shown[id] as? SeekBar ?: standIns.getOrPut(id) { SeekBar(root.context) } as SeekBar

    fun viewGroup(id: Int): ViewGroup =
        shown[id] as? ViewGroup ?: standIns.getOrPut(id) { FrameLayout(root.context) } as ViewGroup

    // Row views, by the id the row model gives them.
    val accent: TextView get() = textView(R.id.accent)
    val alignment: TextView get() = textView(R.id.alignment)
    val appThemeText: TextView get() = textView(R.id.appThemeText)
    val autoBackup: TextView get() = textView(R.id.autoBackup)
    val autoBackupStatus: TextView get() = textView(R.id.autoBackupStatus)
    val autoShowKeyboard: TextView get() = textView(R.id.autoShowKeyboard)
    val batteryMode: TextView get() = textView(R.id.batteryMode)
    val boldFont: TextView get() = textView(R.id.boldFont)
    val clockWallpaper: TextView get() = textView(R.id.clockWallpaper)
    val commitLockOnOff: TextView get() = textView(R.id.commitLockOnOff)
    val contextLineValue: TextView get() = textView(R.id.contextLineValue)
    val currencyRates: TextView get() = textView(R.id.currencyRates)
    val dateTime: TextView get() = textView(R.id.dateTime)
    val dayStart: TextView get() = textView(R.id.dayStart)
    val distractingApps: TextView get() = textView(R.id.distractingApps)
    val eveningStart: TextView get() = textView(R.id.eveningStart)
    val fontFamily: TextView get() = textView(R.id.fontFamily)
    val germanClock: TextView get() = textView(R.id.germanClock)
    val germanClockLayout: ViewGroup get() = viewGroup(R.id.germanClockLayout)
    val germanLookupLayout: ViewGroup get() = viewGroup(R.id.germanLookupLayout)
    val germanLookupOnOff: TextView get() = textView(R.id.germanLookupOnOff)
    val germanOnOff: TextView get() = textView(R.id.germanOnOff)
    val germanPauseWordLayout: ViewGroup get() = viewGroup(R.id.germanPauseWordLayout)
    val germanPauseWordOnOff: TextView get() = textView(R.id.germanPauseWordOnOff)
    val germanScreenPickLayout: ViewGroup get() = viewGroup(R.id.germanScreenPickLayout)
    val germanScreenPickOnOff: TextView get() = textView(R.id.germanScreenPickOnOff)
    val germanSpellSuggestLayout: ViewGroup get() = viewGroup(R.id.germanSpellSuggestLayout)
    val germanSpellSuggestOnOff: TextView get() = textView(R.id.germanSpellSuggestOnOff)
    val germanUiLayout: ViewGroup get() = viewGroup(R.id.germanUiLayout)
    val germanUiOnOff: TextView get() = textView(R.id.germanUiOnOff)
    val glowStrength: TextView get() = textView(R.id.glowStrength)
    val glowStrengthSeekBar: SeekBar get() = seekBar(R.id.glowStrengthSeekBar)
    val hapticDuration: TextView get() = textView(R.id.hapticDuration)
    val hapticDurationSeekBar: SeekBar get() = seekBar(R.id.hapticDurationSeekBar)
    val hapticStrength: TextView get() = textView(R.id.hapticStrength)
    val hapticStrengthSeekBar: SeekBar get() = seekBar(R.id.hapticStrengthSeekBar)
    val healthConnectOnOff: TextView get() = textView(R.id.healthConnectOnOff)
    val holdNotificationsOnOff: TextView get() = textView(R.id.holdNotificationsOnOff)
    val homeAppsNum: TextView get() = textView(R.id.homeAppsNum)
    val homeDarkness: TextView get() = textView(R.id.homeDarkness)
    val homeNextEventValue: TextView get() = textView(R.id.homeNextEventValue)
    val homeNotesValue: TextView get() = textView(R.id.homeNotesValue)
    val homeSets: TextView get() = textView(R.id.homeSets)
    val profileSchedule: TextView get() = textView(R.id.profileSchedule)
    val profileSwitch: TextView get() = textView(R.id.profileSwitch)
    val infoLine: TextView get() = textView(R.id.infoLine)
    val morningStart: TextView get() = textView(R.id.morningStart)
    val musicPulse: TextView get() = textView(R.id.musicPulse)
    val nudgeActiveHours: TextView get() = textView(R.id.nudgeActiveHours)
    val nudgeSocial: TextView get() = textView(R.id.nudgeSocial)
    val nudgeWalk: TextView get() = textView(R.id.nudgeWalk)
    val nudgeWater: TextView get() = textView(R.id.nudgeWater)
    val pauseAllowance: TextView get() = textView(R.id.pauseAllowance)
    val pauseAskWhyOnOff: TextView get() = textView(R.id.pauseAskWhyOnOff)
    val pauseBeforeAppsOnOff: TextView get() = textView(R.id.pauseBeforeAppsOnOff)
    val pauseEscalateOnOff: TextView get() = textView(R.id.pauseEscalateOnOff)
    val photoWallpaper: TextView get() = textView(R.id.photoWallpaper)
    val quietReleaseTimes: TextView get() = textView(R.id.quietReleaseTimes)
    val screenTimeOnOff: TextView get() = textView(R.id.screenTimeOnOff)
    val screenTimeStyle: TextView get() = textView(R.id.screenTimeStyle)
    val screenTimeTarget: TextView get() = textView(R.id.screenTimeTarget)
    val searchContacts: TextView get() = textView(R.id.searchContacts)
    val searchEngine: TextView get() = textView(R.id.searchEngine)
    val shortcutCount: TextView get() = textView(R.id.shortcutCount)
    val shortcutCountLayout: ViewGroup get() = viewGroup(R.id.shortcutCountLayout)
    val windDownGreyLayout: ViewGroup get() = viewGroup(R.id.windDownGreyLayout)
    val systemGreyLayout: ViewGroup get() = viewGroup(R.id.systemGreyLayout)
    val quietReleaseTimesLayout: ViewGroup get() = viewGroup(R.id.quietReleaseTimesLayout)
    val shortcutHeight: TextView get() = textView(R.id.shortcutHeight)
    val shortcutHeightLayout: ViewGroup get() = viewGroup(R.id.shortcutHeightLayout)
    val shortcutIconStyle: TextView get() = textView(R.id.shortcutIconStyle)
    val shortcutIconStyleLayout: ViewGroup get() = viewGroup(R.id.shortcutIconStyleLayout)
    val shortcutIconsOnOff: TextView get() = textView(R.id.shortcutIconsOnOff)
    val shortcutSide: TextView get() = textView(R.id.shortcutSide)
    val shortcutSideLayout: ViewGroup get() = viewGroup(R.id.shortcutSideLayout)
    val shortcutSize: TextView get() = textView(R.id.shortcutSize)
    val shortcutSizeLayout: ViewGroup get() = viewGroup(R.id.shortcutSizeLayout)
    val statusBar: TextView get() = textView(R.id.statusBar)
    val doubleTapAction: TextView get() = textView(R.id.doubleTapAction)
    val clockTapAction: TextView get() = textView(R.id.clockTapAction)
    val dateTapAction: TextView get() = textView(R.id.dateTapAction)
    val swipeLeftApp: TextView get() = textView(R.id.swipeLeftApp)
    val swipeRightApp: TextView get() = textView(R.id.swipeRightApp)
    val systemGreyActions: ViewGroup get() = viewGroup(R.id.systemGreyActions)
    val systemGreyOnOff: TextView get() = textView(R.id.systemGreyOnOff)
    val systemGreyStatus: TextView get() = textView(R.id.systemGreyStatus)
    val textBrightness: TextView get() = textView(R.id.textBrightness)
    val textContrast: TextView get() = textView(R.id.textContrast)
    val textSizeValue: TextView get() = textView(R.id.textSizeValue)
    val todayCards: TextView get() = textView(R.id.todayCards)
    val todayOrder: TextView get() = textView(R.id.todayOrder)
    val toggleLock: TextView get() = textView(R.id.toggleLock)
    val weather: TextView get() = textView(R.id.weather)
    val weatherEffects: TextView get() = textView(R.id.weatherEffects)
    val windDown: TextView get() = textView(R.id.windDown)
    val windDownGreyOnOff: TextView get() = textView(R.id.windDownGreyOnOff)
}
