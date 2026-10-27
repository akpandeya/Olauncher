package app.olauncher.ui.settings

import androidx.annotation.IdRes
import androidx.annotation.StringRes
import app.olauncher.R
import app.olauncher.data.Prefs

/** What tapping a row does. Only [TOGGLE] rows flip a switch; [CHOICE] rows open a menu or dialog. */
enum class SettingsRowType { TOGGLE, CHOICE, ACTION, SLIDER, STATUS }

/** Which row layout draws the row. Several rows can share one shape. */
enum class SettingsRowShape { VALUE, SUBTITLE, SLIDER, STATUS }

/** [CARD] is the big heading that opens a block; [SUB] is a small heading inside a section. */
enum class SettingsHeadingStyle { CARD, SUB }

/**
 * Reads and writes one on/off preference. Rows that carry one are switched by the shared
 * toggle code instead of a hand written toggle function.
 */
class BooleanSettingPref(
    val get: (Prefs) -> Boolean,
    val set: (Prefs, Boolean) -> Unit,
)

/**
 * One row of the Settings list. The view ids are the ids the click and populate code has
 * always used, so a row can be found by any of them (deep links, search, availability).
 */
class SettingsRowSpec(
    /** Stable name; the resource name of [primaryId]. */
    val key: String,
    val sectionKey: String,
    @StringRes val titleRes: Int,
    val type: SettingsRowType,
    val shape: SettingsRowShape = SettingsRowShape.VALUE,
    /** The id search results, deep links and [SettingsRowAvailability] refer to. */
    @IdRes val primaryId: Int,
    @IdRes val layoutId: Int = 0,
    @IdRes val titleId: Int = 0,
    @IdRes val valueId: Int = 0,
    @StringRes val subtitleRes: Int = 0,
    @IdRes val subtitleId: Int = 0,
    @IdRes val seekBarId: Int = 0,
    val actionIds: IntArray = IntArray(0),
    @StringRes val actionLabelRes: IntArray = IntArray(0),
    @IdRes val actionsContainerId: Int = 0,
    val searchable: Boolean = true,
    val pref: BooleanSettingPref? = null,
) {
    /** Every view id this row owns. */
    val viewIds: List<Int> by lazy {
        (listOf(primaryId, layoutId, titleId, valueId, subtitleId, seekBarId, actionsContainerId).filter { it != 0 } + actionIds.toList()).distinct()
    }
}

sealed interface SettingsItem {
    data class Heading(
        @StringRes val titleRes: Int,
        val style: SettingsHeadingStyle,
        @IdRes val id: Int = 0,
    ) : SettingsItem

    data class Row(val spec: SettingsRowSpec) : SettingsItem
}

/** The preferences the shared toggle code can flip. */
object SettingsBooleanPrefs {
    val drawerBottomSearch = BooleanSettingPref({ it.drawerBottomSearch }, { p, v -> p.drawerBottomSearch = v })
    val drawerNewFirst = BooleanSettingPref({ it.drawerNewFirst }, { p, v -> p.drawerNewFirst = v })
    val drawerLetterStrip = BooleanSettingPref({ it.drawerLetterStrip }, { p, v -> p.drawerLetterStrip = v })
    val drawerRecents = BooleanSettingPref({ it.drawerRecents }, { p, v -> p.drawerRecents = v })
    val haptics = BooleanSettingPref({ it.haptics }, { p, v -> p.haptics = v })
    val intentionOnHome = BooleanSettingPref({ it.intentionOnHome }, { p, v -> p.intentionOnHome = v })
}

private fun row(
    key: String,
    sectionKey: String,
    @StringRes titleRes: Int,
    type: SettingsRowType,
    shape: SettingsRowShape = SettingsRowShape.VALUE,
    @IdRes primaryId: Int,
    @IdRes layoutId: Int = 0,
    @IdRes titleId: Int = 0,
    @IdRes valueId: Int = 0,
    @StringRes subtitleRes: Int = 0,
    @IdRes subtitleId: Int = 0,
    @IdRes seekBarId: Int = 0,
    actionIds: IntArray = IntArray(0),
    actionLabelRes: IntArray = IntArray(0),
    @IdRes actionsContainerId: Int = 0,
    searchable: Boolean = true,
    pref: BooleanSettingPref? = null,
): SettingsItem.Row = SettingsItem.Row(
    SettingsRowSpec(
        key = key, sectionKey = sectionKey, titleRes = titleRes, type = type, shape = shape,
        primaryId = primaryId, layoutId = layoutId, titleId = titleId, valueId = valueId,
        subtitleRes = subtitleRes, subtitleId = subtitleId, seekBarId = seekBarId,
        actionIds = actionIds, actionLabelRes = actionLabelRes, actionsContainerId = actionsContainerId, searchable = searchable, pref = pref,
    )
)

/**
 * Every Settings row, in display order, grouped by section. This list is the single source
 * for what Settings shows: the adapter renders it, search reads the titles from it, and
 * deep links find a row's section in it. Add a row here and nowhere else.
 */
object SettingsCatalog {

    private val home: List<SettingsItem> = listOf(
    row(key = "profileSwitch", sectionKey = SettingsSections.KEY_HOME, titleRes = R.string.profile_switch, type = SettingsRowType.CHOICE,
        primaryId = R.id.profileSwitch, layoutId = R.id.profileSwitchLayout, titleId = R.id.profileSwitchTitle, valueId = R.id.profileSwitch),
    row(key = "homeAppsNum", sectionKey = SettingsSections.KEY_HOME, titleRes = R.string.apps_on_home_screen, type = SettingsRowType.CHOICE,
        primaryId = R.id.homeAppsNum, valueId = R.id.homeAppsNum),
    row(key = "dateTime", sectionKey = SettingsSections.KEY_HOME, titleRes = R.string.show_date_time, type = SettingsRowType.CHOICE,
        primaryId = R.id.dateTime, valueId = R.id.dateTime),
    row(key = "batteryMode", sectionKey = SettingsSections.KEY_HOME, titleRes = R.string.battery, type = SettingsRowType.CHOICE,
        primaryId = R.id.batteryMode, layoutId = R.id.batteryModeLayout, titleId = R.id.batteryModeTitle, valueId = R.id.batteryMode),
    row(key = "infoLine", sectionKey = SettingsSections.KEY_HOME, titleRes = R.string.info_line, type = SettingsRowType.TOGGLE,
        primaryId = R.id.infoLine, layoutId = R.id.infoLineLayout, titleId = R.id.infoLineTitle, valueId = R.id.infoLine),
    row(key = "homeSets", sectionKey = SettingsSections.KEY_HOME, titleRes = R.string.app_sets, type = SettingsRowType.TOGGLE,
        primaryId = R.id.homeSets, layoutId = R.id.homeSetsLayout, titleId = R.id.homeSetsTitle, valueId = R.id.homeSets),
    row(key = "editMorningAppsLayout", sectionKey = SettingsSections.KEY_HOME, titleRes = R.string.edit_morning_apps, type = SettingsRowType.ACTION,
        primaryId = R.id.editMorningAppsLayout, layoutId = R.id.editMorningAppsLayout, titleId = R.id.editMorningAppsTitle),
    row(key = "editEveningAppsLayout", sectionKey = SettingsSections.KEY_HOME, titleRes = R.string.edit_evening_apps, type = SettingsRowType.ACTION,
        primaryId = R.id.editEveningAppsLayout, layoutId = R.id.editEveningAppsLayout, titleId = R.id.editEveningAppsTitle),
    row(key = "morningStart", sectionKey = SettingsSections.KEY_HOME, titleRes = R.string.morning_starts, type = SettingsRowType.CHOICE,
        primaryId = R.id.morningStart, layoutId = R.id.morningStartLayout, titleId = R.id.morningStartTitle, valueId = R.id.morningStart),
    row(key = "dayStart", sectionKey = SettingsSections.KEY_HOME, titleRes = R.string.day_starts, type = SettingsRowType.CHOICE,
        primaryId = R.id.dayStart, layoutId = R.id.dayStartLayout, titleId = R.id.dayStartTitle, valueId = R.id.dayStart),
    row(key = "eveningStart", sectionKey = SettingsSections.KEY_HOME, titleRes = R.string.evening_starts, type = SettingsRowType.CHOICE,
        primaryId = R.id.eveningStart, layoutId = R.id.eveningStartLayout, titleId = R.id.eveningStartTitle, valueId = R.id.eveningStart),
    row(key = "profileSchedule", sectionKey = SettingsSections.KEY_HOME, titleRes = R.string.profile_schedule, type = SettingsRowType.CHOICE,
        primaryId = R.id.profileSchedule, layoutId = R.id.profileScheduleLayout, titleId = R.id.profileScheduleTitle, valueId = R.id.profileSchedule),
    row(key = "contextLineValue", sectionKey = SettingsSections.KEY_HOME, titleRes = R.string.context_line, type = SettingsRowType.TOGGLE,
        primaryId = R.id.contextLineValue, layoutId = R.id.contextLineLayout, titleId = R.id.contextLineTitle, valueId = R.id.contextLineValue),
    row(key = "homeNextEventValue", sectionKey = SettingsSections.KEY_HOME, titleRes = R.string.home_next_event, type = SettingsRowType.TOGGLE,
        primaryId = R.id.homeNextEventValue, layoutId = R.id.homeNextEventLayout, titleId = R.id.homeNextEventTitle, valueId = R.id.homeNextEventValue),
    row(key = "homeNotesValue", sectionKey = SettingsSections.KEY_HOME, titleRes = R.string.home_notes, type = SettingsRowType.TOGGLE,
        primaryId = R.id.homeNotesValue, layoutId = R.id.homeNotesLayout, titleId = R.id.homeNotesTitle, valueId = R.id.homeNotesValue),
    row(key = "alignment", sectionKey = SettingsSections.KEY_HOME, titleRes = R.string.home_layout_alignment, type = SettingsRowType.CHOICE,
        primaryId = R.id.alignment, valueId = R.id.alignment),
    row(key = "swapSides", sectionKey = SettingsSections.KEY_HOME, titleRes = R.string.swap_sides, type = SettingsRowType.ACTION,
        primaryId = R.id.swapSides, layoutId = R.id.swapSidesLayout, titleId = R.id.swapSides),
    )

    private val shortcuts: List<SettingsItem> = listOf(
    row(key = "shortcutIconsOnOff", sectionKey = SettingsSections.KEY_SHORTCUTS, titleRes = R.string.shortcut_icons, type = SettingsRowType.TOGGLE,
        primaryId = R.id.shortcutIconsOnOff, layoutId = R.id.shortcutIconsOnOffLayout, titleId = R.id.shortcutIconsOnOffTitle, valueId = R.id.shortcutIconsOnOff),
    row(key = "shortcutCount", sectionKey = SettingsSections.KEY_SHORTCUTS, titleRes = R.string.icons, type = SettingsRowType.CHOICE,
        primaryId = R.id.shortcutCount, layoutId = R.id.shortcutCountLayout, titleId = R.id.shortcutCountTitle, valueId = R.id.shortcutCount),
    row(key = "shortcutSize", sectionKey = SettingsSections.KEY_SHORTCUTS, titleRes = R.string.size, type = SettingsRowType.CHOICE,
        primaryId = R.id.shortcutSize, layoutId = R.id.shortcutSizeLayout, titleId = R.id.shortcutSizeTitle, valueId = R.id.shortcutSize),
    row(key = "shortcutSide", sectionKey = SettingsSections.KEY_SHORTCUTS, titleRes = R.string.side, type = SettingsRowType.TOGGLE,
        primaryId = R.id.shortcutSide, layoutId = R.id.shortcutSideLayout, titleId = R.id.shortcutSideTitle, valueId = R.id.shortcutSide),
    row(key = "shortcutHeight", sectionKey = SettingsSections.KEY_SHORTCUTS, titleRes = R.string.height, type = SettingsRowType.CHOICE,
        primaryId = R.id.shortcutHeight, layoutId = R.id.shortcutHeightLayout, titleId = R.id.shortcutHeightTitle, valueId = R.id.shortcutHeight),
    row(key = "shortcutIconStyle", sectionKey = SettingsSections.KEY_SHORTCUTS, titleRes = R.string.style, type = SettingsRowType.CHOICE,
        primaryId = R.id.shortcutIconStyle, layoutId = R.id.shortcutIconStyleLayout, titleId = R.id.shortcutIconStyleTitle, valueId = R.id.shortcutIconStyle),
    )

    private val drawer: List<SettingsItem> = listOf(
    row(key = "autoShowKeyboard", sectionKey = SettingsSections.KEY_DRAWER, titleRes = R.string.auto_show_keyboard, type = SettingsRowType.TOGGLE,
        primaryId = R.id.autoShowKeyboard, valueId = R.id.autoShowKeyboard),
    row(key = "drawerBottomSearch", sectionKey = SettingsSections.KEY_DRAWER, titleRes = R.string.search_at_bottom, type = SettingsRowType.TOGGLE,
        primaryId = R.id.drawerBottomSearch, layoutId = R.id.drawerBottomSearchLayout, titleId = R.id.drawerBottomSearchTitle, valueId = R.id.drawerBottomSearch, pref = SettingsBooleanPrefs.drawerBottomSearch),
    row(key = "searchEngine", sectionKey = SettingsSections.KEY_DRAWER, titleRes = R.string.search_engine, type = SettingsRowType.CHOICE,
        primaryId = R.id.searchEngine, layoutId = R.id.searchEngineLayout, titleId = R.id.searchEngineTitle, valueId = R.id.searchEngine),
    row(key = "drawerNewFirst", sectionKey = SettingsSections.KEY_DRAWER, titleRes = R.string.new_apps_first, type = SettingsRowType.TOGGLE,
        primaryId = R.id.drawerNewFirst, layoutId = R.id.drawerNewFirstLayout, titleId = R.id.drawerNewFirstTitle, valueId = R.id.drawerNewFirst, pref = SettingsBooleanPrefs.drawerNewFirst),
    row(key = "drawerLetterStrip", sectionKey = SettingsSections.KEY_DRAWER, titleRes = R.string.letter_strip, type = SettingsRowType.TOGGLE,
        primaryId = R.id.drawerLetterStrip, layoutId = R.id.drawerLetterStripLayout, titleId = R.id.drawerLetterStripTitle, valueId = R.id.drawerLetterStrip, pref = SettingsBooleanPrefs.drawerLetterStrip),
    row(key = "drawerRecents", sectionKey = SettingsSections.KEY_DRAWER, titleRes = R.string.recent_apps, type = SettingsRowType.TOGGLE,
        primaryId = R.id.drawerRecents, layoutId = R.id.drawerRecentsLayout, titleId = R.id.drawerRecentsTitle, valueId = R.id.drawerRecents, pref = SettingsBooleanPrefs.drawerRecents),
    row(key = "searchContacts", sectionKey = SettingsSections.KEY_DRAWER, titleRes = R.string.search_contacts, type = SettingsRowType.TOGGLE,
        primaryId = R.id.searchContacts, layoutId = R.id.searchContactsLayout, titleId = R.id.searchContactsTitle, valueId = R.id.searchContacts),
    row(key = "currencyRates", sectionKey = SettingsSections.KEY_DRAWER, titleRes = R.string.currency_rates, type = SettingsRowType.TOGGLE,
        primaryId = R.id.currencyRates, layoutId = R.id.currencyRatesLayout, titleId = R.id.currencyRatesTitle, valueId = R.id.currencyRates),
    )

    private val wellbeing: List<SettingsItem> = listOf(
    SettingsItem.Heading(R.string.wellbeing_friction, SettingsHeadingStyle.SUB),
    row(key = "distractingApps", sectionKey = SettingsSections.KEY_WELLBEING, titleRes = R.string.distracting_apps, type = SettingsRowType.CHOICE,
        primaryId = R.id.distractingApps, layoutId = R.id.distractingAppsLayout, titleId = R.id.distractingAppsTitle, valueId = R.id.distractingApps),
    row(key = "pauseBeforeAppsOnOff", sectionKey = SettingsSections.KEY_WELLBEING, titleRes = R.string.pause_before_distracting_apps, type = SettingsRowType.TOGGLE,
        primaryId = R.id.pauseBeforeAppsOnOff, layoutId = R.id.pauseBeforeAppsLayout, titleId = R.id.pauseBeforeAppsTitle, valueId = R.id.pauseBeforeAppsOnOff),
    row(key = "pauseAllowance", sectionKey = SettingsSections.KEY_WELLBEING, titleRes = R.string.pause_daily_allowance, type = SettingsRowType.CHOICE, shape = SettingsRowShape.SUBTITLE,
        primaryId = R.id.pauseAllowance, layoutId = R.id.pauseAllowanceLayout, titleId = R.id.pauseAllowanceTitle, valueId = R.id.pauseAllowance, subtitleRes = R.string.pause_daily_allowance_desc),
    row(key = "pauseEscalateOnOff", sectionKey = SettingsSections.KEY_WELLBEING, titleRes = R.string.pause_longer_with_each_open, type = SettingsRowType.TOGGLE,
        primaryId = R.id.pauseEscalateOnOff, layoutId = R.id.pauseEscalateLayout, titleId = R.id.pauseEscalateTitle, valueId = R.id.pauseEscalateOnOff),
    row(key = "pauseAskWhyOnOff", sectionKey = SettingsSections.KEY_WELLBEING, titleRes = R.string.pause_ask_why, type = SettingsRowType.TOGGLE, shape = SettingsRowShape.SUBTITLE,
        primaryId = R.id.pauseAskWhyOnOff, layoutId = R.id.pauseAskWhyLayout, titleId = R.id.pauseAskWhyTitle, valueId = R.id.pauseAskWhyOnOff, subtitleRes = R.string.pause_ask_why_desc),
    row(key = "commitLockOnOff", sectionKey = SettingsSections.KEY_WELLBEING, titleRes = R.string.commit_lock, type = SettingsRowType.TOGGLE, shape = SettingsRowShape.SUBTITLE,
        primaryId = R.id.commitLockOnOff, layoutId = R.id.commitLockLayout, titleId = R.id.commitLockTitle, valueId = R.id.commitLockOnOff, subtitleRes = R.string.commit_lock_desc, subtitleId = R.id.commitLockSubtitle),
    SettingsItem.Heading(R.string.wellbeing_wind_down, SettingsHeadingStyle.SUB),
    row(key = "windDown", sectionKey = SettingsSections.KEY_WELLBEING, titleRes = R.string.wind_down, type = SettingsRowType.CHOICE,
        primaryId = R.id.windDown, layoutId = R.id.windDownLayout, titleId = R.id.windDownTitle, valueId = R.id.windDown),
    row(key = "windDownGreyOnOff", sectionKey = SettingsSections.KEY_WELLBEING, titleRes = R.string.wind_down_grey, type = SettingsRowType.TOGGLE, shape = SettingsRowShape.SUBTITLE,
        primaryId = R.id.windDownGreyOnOff, layoutId = R.id.windDownGreyLayout, titleId = R.id.windDownGreyTitle, valueId = R.id.windDownGreyOnOff, subtitleRes = R.string.wind_down_grey_desc),
    row(key = "systemGreyOnOff", sectionKey = SettingsSections.KEY_WELLBEING, titleRes = R.string.system_grey, type = SettingsRowType.TOGGLE, shape = SettingsRowShape.SUBTITLE,
        primaryId = R.id.systemGreyOnOff, layoutId = R.id.systemGreyLayout, titleId = R.id.systemGreyTitle, valueId = R.id.systemGreyOnOff, subtitleId = R.id.systemGreyStatus, actionIds = intArrayOf(R.id.systemGreyCopy, R.id.systemGreyBedtime),
        actionLabelRes = intArrayOf(R.string.system_grey_copy, R.string.system_grey_bedtime), actionsContainerId = R.id.systemGreyActions),
    SettingsItem.Heading(R.string.wellbeing_notifications, SettingsHeadingStyle.SUB),
    row(key = "holdNotificationsOnOff", sectionKey = SettingsSections.KEY_WELLBEING, titleRes = R.string.hold_notifications_from_distracting_apps, type = SettingsRowType.TOGGLE,
        primaryId = R.id.holdNotificationsOnOff, layoutId = R.id.holdNotificationsLayout, titleId = R.id.holdNotificationsTitle, valueId = R.id.holdNotificationsOnOff),
    row(key = "quietReleaseTimes", sectionKey = SettingsSections.KEY_WELLBEING, titleRes = R.string.release_at, type = SettingsRowType.CHOICE,
        primaryId = R.id.quietReleaseTimes, layoutId = R.id.quietReleaseTimesLayout, titleId = R.id.quietReleaseTimesTitle, valueId = R.id.quietReleaseTimes),
    SettingsItem.Heading(R.string.wellbeing_tracking, SettingsHeadingStyle.SUB),
    row(key = "healthConnectOnOff", sectionKey = SettingsSections.KEY_WELLBEING, titleRes = R.string.use_health_connect, type = SettingsRowType.TOGGLE,
        primaryId = R.id.healthConnectOnOff, layoutId = R.id.healthConnectLayout, titleId = R.id.healthConnectTitle, valueId = R.id.healthConnectOnOff),
    row(key = "screenTimeOnOff", sectionKey = SettingsSections.KEY_WELLBEING, titleRes = R.string.screen_time, type = SettingsRowType.CHOICE,
        primaryId = R.id.screenTimeOnOff, layoutId = R.id.screenTimeLayout, valueId = R.id.screenTimeOnOff),
    row(key = "screenTimeStyle", sectionKey = SettingsSections.KEY_WELLBEING, titleRes = R.string.screen_time_style, type = SettingsRowType.CHOICE,
        primaryId = R.id.screenTimeStyle, layoutId = R.id.screenTimeStyleLayout, titleId = R.id.screenTimeStyleTitle, valueId = R.id.screenTimeStyle),
    row(key = "screenTimeTarget", sectionKey = SettingsSections.KEY_WELLBEING, titleRes = R.string.screen_time_target, type = SettingsRowType.CHOICE,
        primaryId = R.id.screenTimeTarget, layoutId = R.id.screenTimeTargetLayout, titleId = R.id.screenTimeTargetTitle, valueId = R.id.screenTimeTarget),
    SettingsItem.Heading(R.string.nudges, SettingsHeadingStyle.SUB, R.id.nudgesSection),
    row(key = "nudgeWater", sectionKey = SettingsSections.KEY_WELLBEING, titleRes = R.string.nudge_water, type = SettingsRowType.CHOICE,
        primaryId = R.id.nudgeWater, layoutId = R.id.nudgeWaterLayout, titleId = R.id.nudgeWaterTitle, valueId = R.id.nudgeWater),
    row(key = "nudgeWalk", sectionKey = SettingsSections.KEY_WELLBEING, titleRes = R.string.nudge_walk, type = SettingsRowType.CHOICE,
        primaryId = R.id.nudgeWalk, layoutId = R.id.nudgeWalkLayout, titleId = R.id.nudgeWalkTitle, valueId = R.id.nudgeWalk),
    row(key = "nudgeSocial", sectionKey = SettingsSections.KEY_WELLBEING, titleRes = R.string.nudge_social, type = SettingsRowType.CHOICE,
        primaryId = R.id.nudgeSocial, layoutId = R.id.nudgeSocialLayout, titleId = R.id.nudgeSocialTitle, valueId = R.id.nudgeSocial),
    row(key = "nudgeActiveHours", sectionKey = SettingsSections.KEY_WELLBEING, titleRes = R.string.nudge_active_hours, type = SettingsRowType.CHOICE,
        primaryId = R.id.nudgeActiveHours, layoutId = R.id.nudgeActiveHoursLayout, titleId = R.id.nudgeActiveHoursTitle, valueId = R.id.nudgeActiveHours),
    )

    private val today: List<SettingsItem> = listOf(
    row(key = "todayCards", sectionKey = SettingsSections.KEY_TODAY, titleRes = R.string.today_cards, type = SettingsRowType.CHOICE,
        primaryId = R.id.todayCards, layoutId = R.id.todayCardsLayout, titleId = R.id.todayCardsTitle, valueId = R.id.todayCards),
    row(key = "todayOrder", sectionKey = SettingsSections.KEY_TODAY, titleRes = R.string.today_card_order, type = SettingsRowType.TOGGLE,
        primaryId = R.id.todayOrder, layoutId = R.id.todayOrderLayout, titleId = R.id.todayOrderTitle, valueId = R.id.todayOrder),
    row(key = "intentionOnHome", sectionKey = SettingsSections.KEY_TODAY, titleRes = R.string.intention_on_home, type = SettingsRowType.TOGGLE,
        primaryId = R.id.intentionOnHome, layoutId = R.id.intentionOnHomeLayout, titleId = R.id.intentionOnHomeTitle, valueId = R.id.intentionOnHome, pref = SettingsBooleanPrefs.intentionOnHome),
    SettingsItem.Heading(R.string.glance, SettingsHeadingStyle.CARD),
    row(key = "weather", sectionKey = SettingsSections.KEY_TODAY, titleRes = R.string.weather, type = SettingsRowType.CHOICE,
        primaryId = R.id.weather, layoutId = R.id.weatherLayout, titleId = R.id.weatherTitle, valueId = R.id.weather),
    )

    private val deutsch: List<SettingsItem> = listOf(
    row(key = "germanOnOff", sectionKey = SettingsSections.KEY_DEUTSCH, titleRes = R.string.german, type = SettingsRowType.TOGGLE,
        primaryId = R.id.germanOnOff, layoutId = R.id.germanLayout, titleId = R.id.germanTitle, valueId = R.id.germanOnOff),
    row(key = "germanClock", sectionKey = SettingsSections.KEY_DEUTSCH, titleRes = R.string.clock, type = SettingsRowType.CHOICE,
        primaryId = R.id.germanClock, layoutId = R.id.germanClockLayout, titleId = R.id.germanClockTitle, valueId = R.id.germanClock),
    row(key = "dictionaryApp", sectionKey = SettingsSections.KEY_DEUTSCH, titleRes = R.string.dictionary_app, type = SettingsRowType.CHOICE,
        primaryId = R.id.dictionaryApp, layoutId = R.id.dictionaryAppLayout, titleId = R.id.dictionaryAppTitle, valueId = R.id.dictionaryApp),
    row(key = "germanOfflineDictionaryOnOff", sectionKey = SettingsSections.KEY_DEUTSCH, titleRes = R.string.german_offline_dictionary, type = SettingsRowType.TOGGLE, shape = SettingsRowShape.SUBTITLE,
        primaryId = R.id.germanOfflineDictionaryOnOff, layoutId = R.id.germanOfflineDictionaryLayout, titleId = R.id.germanOfflineDictionaryTitle, valueId = R.id.germanOfflineDictionaryOnOff, subtitleRes = R.string.german_offline_dictionary_subtitle, subtitleId = R.id.germanOfflineDictionarySubtitle),
    row(key = "germanLookupOnOff", sectionKey = SettingsSections.KEY_DEUTSCH, titleRes = R.string.german_lookup, type = SettingsRowType.TOGGLE, shape = SettingsRowShape.SUBTITLE,
        primaryId = R.id.germanLookupOnOff, layoutId = R.id.germanLookupLayout, titleId = R.id.germanLookupTitle, valueId = R.id.germanLookupOnOff, subtitleRes = R.string.german_lookup_subtitle, subtitleId = R.id.germanLookupSubtitle),
    row(key = "germanSpellSuggestOnOff", sectionKey = SettingsSections.KEY_DEUTSCH, titleRes = R.string.german_spell_suggest, type = SettingsRowType.TOGGLE, shape = SettingsRowShape.SUBTITLE,
        primaryId = R.id.germanSpellSuggestOnOff, layoutId = R.id.germanSpellSuggestLayout, titleId = R.id.germanSpellSuggestTitle, valueId = R.id.germanSpellSuggestOnOff, subtitleRes = R.string.german_spell_suggest_subtitle, subtitleId = R.id.germanSpellSuggestSubtitle),
    row(key = "germanScreenPickOnOff", sectionKey = SettingsSections.KEY_DEUTSCH, titleRes = R.string.german_screen_pick, type = SettingsRowType.TOGGLE, shape = SettingsRowShape.SUBTITLE,
        primaryId = R.id.germanScreenPickOnOff, layoutId = R.id.germanScreenPickLayout, titleId = R.id.germanScreenPickTitle, valueId = R.id.germanScreenPickOnOff, subtitleRes = R.string.german_screen_pick_subtitle, subtitleId = R.id.germanScreenPickSubtitle),
    row(key = "germanPauseWordOnOff", sectionKey = SettingsSections.KEY_DEUTSCH, titleRes = R.string.german_pause_word, type = SettingsRowType.TOGGLE,
        primaryId = R.id.germanPauseWordOnOff, layoutId = R.id.germanPauseWordLayout, titleId = R.id.germanPauseWordTitle, valueId = R.id.germanPauseWordOnOff),
    row(key = "germanUiOnOff", sectionKey = SettingsSections.KEY_DEUTSCH, titleRes = R.string.german_ui, type = SettingsRowType.TOGGLE,
        primaryId = R.id.germanUiOnOff, layoutId = R.id.germanUiLayout, titleId = R.id.germanUiTitle, valueId = R.id.germanUiOnOff),
    row(key = "aiPracticeOnOff", sectionKey = SettingsSections.KEY_DEUTSCH, titleRes = R.string.ai_practice, type = SettingsRowType.TOGGLE, shape = SettingsRowShape.SUBTITLE,
        primaryId = R.id.aiPracticeOnOff, layoutId = R.id.aiPracticeLayout, titleId = R.id.aiPracticeTitle, valueId = R.id.aiPracticeOnOff, subtitleId = R.id.aiPracticeSubtitle,
        actionIds = intArrayOf(R.id.aiPracticeActionKey), actionLabelRes = intArrayOf(R.string.ai_practice_api_key), actionsContainerId = R.id.aiPracticeActions),
    )

    private val appearance: List<SettingsItem> = listOf(
    row(key = "photoWallpaper", sectionKey = SettingsSections.KEY_APPEARANCE, titleRes = R.string.wallpaper_photos, type = SettingsRowType.CHOICE,
        primaryId = R.id.photoWallpaper, layoutId = R.id.photoWallpaperLayout, titleId = R.id.photoWallpaperTitle, valueId = R.id.photoWallpaper),
    row(key = "statusBar", sectionKey = SettingsSections.KEY_APPEARANCE, titleRes = R.string.notification_bar, type = SettingsRowType.TOGGLE,
        primaryId = R.id.statusBar, valueId = R.id.statusBar),
    row(key = "appThemeText", sectionKey = SettingsSections.KEY_APPEARANCE, titleRes = R.string.theme_mode, type = SettingsRowType.CHOICE,
        primaryId = R.id.appThemeText, valueId = R.id.appThemeText),
    row(key = "accent", sectionKey = SettingsSections.KEY_APPEARANCE, titleRes = R.string.accent_color, type = SettingsRowType.CHOICE,
        primaryId = R.id.accent, valueId = R.id.accent),
    row(key = "fontFamily", sectionKey = SettingsSections.KEY_APPEARANCE, titleRes = R.string.font, type = SettingsRowType.CHOICE,
        primaryId = R.id.fontFamily, valueId = R.id.fontFamily),
    row(key = "boldFont", sectionKey = SettingsSections.KEY_APPEARANCE, titleRes = R.string.bold_font, type = SettingsRowType.TOGGLE,
        primaryId = R.id.boldFont, valueId = R.id.boldFont),
    row(key = "textSizeValue", sectionKey = SettingsSections.KEY_APPEARANCE, titleRes = R.string.text_size, type = SettingsRowType.CHOICE,
        primaryId = R.id.textSizeValue, valueId = R.id.textSizeValue),
    row(key = "haptics", sectionKey = SettingsSections.KEY_APPEARANCE, titleRes = R.string.haptics, type = SettingsRowType.TOGGLE,
        primaryId = R.id.haptics, valueId = R.id.haptics, pref = SettingsBooleanPrefs.haptics),
    row(key = "hapticStrength", sectionKey = SettingsSections.KEY_APPEARANCE, titleRes = R.string.haptic_strength, type = SettingsRowType.SLIDER, shape = SettingsRowShape.SLIDER,
        primaryId = R.id.hapticStrength, layoutId = R.id.hapticStrengthLayout, titleId = R.id.hapticStrengthTitle, valueId = R.id.hapticStrength, seekBarId = R.id.hapticStrengthSeekBar),
    row(key = "hapticDuration", sectionKey = SettingsSections.KEY_APPEARANCE, titleRes = R.string.haptic_duration, type = SettingsRowType.SLIDER, shape = SettingsRowShape.SLIDER,
        primaryId = R.id.hapticDuration, layoutId = R.id.hapticDurationLayout, titleId = R.id.hapticDurationTitle, valueId = R.id.hapticDuration, seekBarId = R.id.hapticDurationSeekBar),
    row(key = "textContrast", sectionKey = SettingsSections.KEY_APPEARANCE, titleRes = R.string.text_on_wallpaper, type = SettingsRowType.TOGGLE,
        primaryId = R.id.textContrast, layoutId = R.id.textContrastLayout, titleId = R.id.textContrastTitle, valueId = R.id.textContrast),
    row(key = "homeDarkness", sectionKey = SettingsSections.KEY_APPEARANCE, titleRes = R.string.home_darkness, type = SettingsRowType.CHOICE,
        primaryId = R.id.homeDarkness, layoutId = R.id.homeWashLayout, titleId = R.id.homeWashTitle, valueId = R.id.homeDarkness),
    row(key = "textBrightness", sectionKey = SettingsSections.KEY_APPEARANCE, titleRes = R.string.text_brightness, type = SettingsRowType.CHOICE,
        primaryId = R.id.textBrightness, layoutId = R.id.textBrightnessLayout, titleId = R.id.textBrightnessTitle, valueId = R.id.textBrightness),
    row(key = "musicPulse", sectionKey = SettingsSections.KEY_APPEARANCE, titleRes = R.string.music_pulse, type = SettingsRowType.TOGGLE,
        primaryId = R.id.musicPulse, layoutId = R.id.musicPulseLayout, titleId = R.id.musicPulseTitle, valueId = R.id.musicPulse),
    row(key = "glowStrength", sectionKey = SettingsSections.KEY_APPEARANCE, titleRes = R.string.glow_strength, type = SettingsRowType.SLIDER, shape = SettingsRowShape.SLIDER,
        primaryId = R.id.glowStrength, layoutId = R.id.glowStrengthLayout, titleId = R.id.glowStrengthTitle, valueId = R.id.glowStrength, seekBarId = R.id.glowStrengthSeekBar),
    row(key = "weatherEffects", sectionKey = SettingsSections.KEY_APPEARANCE, titleRes = R.string.weather_effects, type = SettingsRowType.TOGGLE,
        primaryId = R.id.weatherEffects, layoutId = R.id.weatherEffectsLayout, titleId = R.id.weatherEffectsTitle, valueId = R.id.weatherEffects),
    row(key = "weatherIcons", sectionKey = SettingsSections.KEY_APPEARANCE, titleRes = R.string.weather_icons, type = SettingsRowType.TOGGLE,
        primaryId = R.id.weatherIcons, layoutId = R.id.weatherIconsLayout, titleId = R.id.weatherIconsTitle, valueId = R.id.weatherIcons),
    )

    private val gestures: List<SettingsItem> = listOf(
    row(key = "swipeLeftApp", sectionKey = SettingsSections.KEY_GESTURES, titleRes = R.string.swipe_left_app, type = SettingsRowType.CHOICE,
        primaryId = R.id.swipeLeftApp, valueId = R.id.swipeLeftApp),
    row(key = "swipeRightApp", sectionKey = SettingsSections.KEY_GESTURES, titleRes = R.string.swipe_right_app, type = SettingsRowType.CHOICE,
        primaryId = R.id.swipeRightApp, layoutId = R.id.swipeRightLayout, valueId = R.id.swipeRightApp),
    row(key = "toggleLock", sectionKey = SettingsSections.KEY_GESTURES, titleRes = R.string.double_tap_to_lock_screen, type = SettingsRowType.TOGGLE,
        primaryId = R.id.toggleLock, valueId = R.id.toggleLock),
    row(key = "doubleTapAction", sectionKey = SettingsSections.KEY_GESTURES, titleRes = R.string.gesture_double_tap, type = SettingsRowType.CHOICE,
        primaryId = R.id.doubleTapAction, valueId = R.id.doubleTapAction),
    row(key = "clockTapAction", sectionKey = SettingsSections.KEY_GESTURES, titleRes = R.string.gesture_clock_tap, type = SettingsRowType.CHOICE,
        primaryId = R.id.clockTapAction, valueId = R.id.clockTapAction),
    row(key = "dateTapAction", sectionKey = SettingsSections.KEY_GESTURES, titleRes = R.string.gesture_date_tap, type = SettingsRowType.CHOICE,
        primaryId = R.id.dateTapAction, valueId = R.id.dateTapAction),
    )

    private val backup: List<SettingsItem> = listOf(
    row(key = "exportBackup", sectionKey = SettingsSections.KEY_BACKUP, titleRes = R.string.export_backup, type = SettingsRowType.ACTION,
        primaryId = R.id.exportBackup, layoutId = R.id.exportBackupLayout, titleId = R.id.exportBackup),
    row(key = "autoBackup", sectionKey = SettingsSections.KEY_BACKUP, titleRes = R.string.nightly_backup, type = SettingsRowType.TOGGLE,
        primaryId = R.id.autoBackup, layoutId = R.id.autoBackupLayout, titleId = R.id.autoBackupTitle, valueId = R.id.autoBackup),
    row(key = "autoBackupStatus", sectionKey = SettingsSections.KEY_BACKUP, titleRes = R.string.nightly_backup, type = SettingsRowType.STATUS, shape = SettingsRowShape.STATUS,
        primaryId = R.id.autoBackupStatus, valueId = R.id.autoBackupStatus, searchable = false),
    row(key = "importBackup", sectionKey = SettingsSections.KEY_BACKUP, titleRes = R.string.import_backup, type = SettingsRowType.ACTION,
        primaryId = R.id.importBackup, layoutId = R.id.importBackupLayout, titleId = R.id.importBackup),
    )


    /** Items of each section page, keyed by section key. The section heading is drawn by the adapter. */
    private val itemsBySection: Map<String, List<SettingsItem>> = mapOf(
        SettingsSections.KEY_HOME to home,
        SettingsSections.KEY_SHORTCUTS to shortcuts,
        SettingsSections.KEY_DRAWER to drawer,
        SettingsSections.KEY_WELLBEING to wellbeing,
        SettingsSections.KEY_TODAY to today,
        SettingsSections.KEY_DEUTSCH to deutsch,
        SettingsSections.KEY_APPEARANCE to appearance,
        SettingsSections.KEY_GESTURES to gestures,
        SettingsSections.KEY_BACKUP to backup,
    )

    /** Every item of every section, in section order. */
    val allItems: List<SettingsItem> = SettingsSections.all.flatMap { itemsBySection.getValue(it.key) }

    /** Every row of every section, in display order. */
    val rows: List<SettingsRowSpec> = allItems.filterIsInstance<SettingsItem.Row>().map { it.spec }

    fun itemsFor(sectionKey: String): List<SettingsItem> = itemsBySection[sectionKey].orEmpty()

    fun rowsFor(sectionKey: String): List<SettingsRowSpec> =
        itemsFor(sectionKey).filterIsInstance<SettingsItem.Row>().map { it.spec }

    /** The row that owns [viewId] (any of its layout, title, value, subtitle or slider ids). */
    fun rowByViewId(@IdRes viewId: Int): SettingsRowSpec? {
        if (viewId == 0) return null
        return rows.firstOrNull { viewId in it.viewIds }
    }

    fun rowByKey(key: String): SettingsRowSpec? = rows.firstOrNull { it.key == key }

    /** Section key for a row id or a section/sub-heading container id; null for anything else. */
    fun sectionKeyOfViewId(@IdRes viewId: Int): String? {
        rowByViewId(viewId)?.let { return it.sectionKey }
        return when (viewId) {
            R.id.glanceSection -> SettingsSections.KEY_TODAY
            R.id.nudgesSection -> SettingsSections.KEY_WELLBEING
            else -> SettingsSections.all.firstOrNull { it.containerId == viewId }?.key
        }
    }

    /**
     * The items to draw for a search: every row of [matchingIds] (primary ids), in display
     * order, each run of rows led by its section heading. A sub-heading shows only when a row
     * under it matched. A row that is not searchable (a status line) rides along with the row
     * just before it.
     */
    fun itemsForSearch(matchingIds: Set<Int>): List<SettingsItem> {
        val result = mutableListOf<SettingsItem>()
        for (section in SettingsSections.all) {
            val items = itemsFor(section.key)
            val picked = mutableListOf<SettingsItem>()
            var pendingHeading: SettingsItem.Heading? = null
            var previousPicked = false
            for (item in items) {
                when (item) {
                    is SettingsItem.Heading -> {
                        pendingHeading = item
                        previousPicked = false
                    }
                    is SettingsItem.Row -> {
                        val spec = item.spec
                        val pick = if (spec.searchable) spec.primaryId in matchingIds else previousPicked
                        if (pick) {
                            pendingHeading?.let { picked.add(it); pendingHeading = null }
                            picked.add(item)
                        }
                        if (spec.searchable) previousPicked = pick
                    }
                }
            }
            if (picked.isNotEmpty()) {
                val ledByCardHeading = (picked.first() as? SettingsItem.Heading)?.style == SettingsHeadingStyle.CARD
                if (!ledByCardHeading) result.add(SettingsItem.Heading(section.titleRes, SettingsHeadingStyle.CARD))
                result.addAll(picked)
            }
        }
        return result
    }
}
