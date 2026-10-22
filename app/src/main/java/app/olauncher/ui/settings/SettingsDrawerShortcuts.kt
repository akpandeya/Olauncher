package app.olauncher.ui.settings

import app.olauncher.helper.createMultiChoiceDialog
import app.olauncher.ui.*
import app.olauncher.ui.SettingsFragment
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.graphics.Typeface
import android.provider.MediaStore
import android.provider.Settings
import android.text.Layout
import android.text.Spannable
import android.text.SpannableString
import android.text.format.DateFormat
import android.text.style.AlignmentSpan
import android.util.TypedValue
import androidx.annotation.FontRes
import androidx.core.content.res.ResourcesCompat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Toast
import android.Manifest
import android.content.pm.PackageManager
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import android.view.WindowInsets
import android.widget.TextView
import app.olauncher.helper.DrawerScrollHelper
import app.olauncher.helper.MusicPulse
import app.olauncher.helper.HandSide
import app.olauncher.helper.HomeSet
import app.olauncher.helper.HomeSets
import app.olauncher.ui.settings.SettingsRow
import app.olauncher.ui.settings.SettingsRowAvailability
import app.olauncher.ui.settings.SettingsSearch
import app.olauncher.ui.settings.SettingsSection
import app.olauncher.ui.settings.SettingsSections
import app.olauncher.ui.today.cards.NextUpCard
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.bundleOf
import app.olauncher.glance.Place
import app.olauncher.glance.WeatherApi
import app.olauncher.glance.WeatherLocation
import app.olauncher.glance.WeatherWorker
import java.util.Locale
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import app.olauncher.wellbeing.HealthRepository
import app.olauncher.wellbeing.NudgeFormatting
import app.olauncher.wellbeing.NudgeScheduler
import app.olauncher.wellbeing.Nudges
import app.olauncher.wellbeing.NotificationPermission
import androidx.work.ExistingWorkPolicy
import app.olauncher.quiet.QuietRelease
import app.olauncher.quiet.ReleaseWorker
import app.olauncher.data.store.AutoBackupStatus
import app.olauncher.data.store.AutoBackupWorker
import app.olauncher.data.store.Backup
import app.olauncher.data.store.BackupData
import app.olauncher.data.store.WordLookup
import app.olauncher.BuildConfig
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.Accent
import android.util.Log
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.FontFamilyResources
import app.olauncher.data.Prefs
import app.olauncher.data.SearchEngine
import app.olauncher.helper.RatesWorker
import app.olauncher.data.SlotCount
import app.olauncher.data.ShortcutOffset
import app.olauncher.data.ShortcutIconStyle
import app.olauncher.data.ShortcutSize
import app.olauncher.ui.today.TodayCards
import app.olauncher.databinding.DialogFontFamilyBinding
import app.olauncher.databinding.DialogTextSizeBinding
import app.olauncher.databinding.FragmentSettingsBinding
import app.olauncher.helper.PhotoSet
import app.olauncher.helper.PhotoWallpaper
import app.olauncher.helper.RotationInterval
import app.olauncher.helper.WallpaperRotateWorker
import app.olauncher.helper.appUsagePermissionGranted
import app.olauncher.helper.createDialog
import app.olauncher.helper.showMessageDialog
import app.olauncher.helper.getAppsList
import app.olauncher.helper.getColorFromAttr
import app.olauncher.helper.hideStatusBar
import app.olauncher.helper.isAccessServiceEnabled
import app.olauncher.helper.isEinkDisplay
import app.olauncher.helper.isOlauncherDefault
import app.olauncher.helper.isTablet
import app.olauncher.helper.openAppInfo
import app.olauncher.helper.openUrl
import app.olauncher.helper.setPlainWallpaperByTheme
import app.olauncher.wellbeing.SystemGrey
import app.olauncher.wellbeing.WindDownWorker
import app.olauncher.helper.WordCaptureActivity
import app.olauncher.helper.WordScreenService
import app.olauncher.helper.OlDialog
import app.olauncher.helper.showMessageDialog
import app.olauncher.helper.showPopupMenu
import app.olauncher.helper.SwipeSettingMenu
import app.olauncher.helper.showStatusBar
import app.olauncher.helper.copyToClipboard
import app.olauncher.helper.showToast
import app.olauncher.helper.showWaitDialog
import app.olauncher.wellbeing.CommitLock
import app.olauncher.wellbeing.DistractingApps
import app.olauncher.wellbeing.FrictionChange
import app.olauncher.wellbeing.FrictionSettings
import app.olauncher.wellbeing.PausePolicy
import app.olauncher.wellbeing.PauseSnooze
import java.time.ZoneId
import app.olauncher.wellbeing.ScreenTimeCue
import app.olauncher.wellbeing.restoreNeedsWait
import app.olauncher.wellbeing.UsageSnapshotWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

internal fun SettingsFragment.populateKeyboardText() {
    if (prefs.autoShowKeyboard) binding.autoShowKeyboard.text = getString(R.string.on)
    else binding.autoShowKeyboard.text = getString(R.string.off)
}

internal fun SettingsFragment.hasContactsPermission(): Boolean =
    ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

internal fun SettingsFragment.populateSearchContacts() {
    if (prefs.searchContacts && !hasContactsPermission()) prefs.searchContacts = false
    binding.searchContacts.text = getString(if (prefs.searchContacts) R.string.on else R.string.off)
}

internal fun SettingsFragment.populateCurrencyRates() {
    binding.currencyRates.text = getString(if (prefs.currencyRates) R.string.currency_rates_on else R.string.off)
}

internal fun SettingsFragment.toggleCurrencyRates() {
    prefs.currencyRates = !prefs.currencyRates
    if (prefs.currencyRates) {
        RatesWorker.schedulePeriodic(requireContext())
        RatesWorker.runOnce(requireContext())
    } else {
        RatesWorker.cancel(requireContext())
    }
    populateCurrencyRates()
}

internal fun SettingsFragment.toggleSearchContacts() {
    if (prefs.searchContacts) {
        prefs.searchContacts = false
        populateSearchContacts()
    } else if (hasContactsPermission()) {
        prefs.searchContacts = true
        populateSearchContacts()
    } else {
        requireContext().showMessageDialog(
            title = R.string.search_contacts,
            message = R.string.search_contacts_permission_message,
            action = R.string.allow,
        ) {
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }
}

internal fun SettingsFragment.updateHomeBottomAlignment() {
    if (viewModel.isOlauncherDefault.value != true) {
        requireContext().showToast(getString(R.string.please_set_olauncher_as_default_first), Toast.LENGTH_LONG)
        return
    }
    prefs.homeBottomAlignment = !prefs.homeBottomAlignment
    populateAlignment()
    viewModel.updateHomeAlignment(prefs.homeAlignment)
}

internal fun SettingsFragment.populateAlignment() {
    when (prefs.homeAlignment) {
        Gravity.START -> binding.alignment.text = getString(R.string.left)
        Gravity.CENTER -> binding.alignment.text = getString(R.string.center)
        Gravity.END -> binding.alignment.text = getString(R.string.right)
    }
}

internal fun SettingsFragment.swapSides() {
    val layout = HandSide.swap(prefs.shortcutSideStart, prefs.homeAlignment)
    prefs.shortcutSideStart = layout.shortcutSideStart
    viewModel.updateHomeAlignment(layout.alignment)
    populateAlignment()
    populateShortcutIcons()
}

internal fun SettingsFragment.toggleShortcutIcons() {
    prefs.homeShortcuts = !prefs.homeShortcuts
    populateShortcutIcons()
    viewModel.updateHomeAlignment(prefs.homeAlignment)
}

internal fun SettingsFragment.toggleShortcutSide() {
    prefs.shortcutSideStart = !prefs.shortcutSideStart
    populateShortcutIcons()
    viewModel.updateHomeAlignment(prefs.homeAlignment)
}

internal fun SettingsFragment.populateShortcutIcons() {
    val shortcutsOn = prefs.homeShortcuts
    binding.shortcutIconsOnOff.text = getString(if (shortcutsOn) R.string.on else R.string.off)
    binding.shortcutCountLayout.visibility = if (shortcutsOn) View.VISIBLE else View.GONE
    binding.shortcutSizeLayout.visibility = if (shortcutsOn) View.VISIBLE else View.GONE
    binding.shortcutSideLayout.visibility = if (shortcutsOn) View.VISIBLE else View.GONE
    binding.shortcutHeightLayout.visibility = if (shortcutsOn) View.VISIBLE else View.GONE
    binding.shortcutIconStyleLayout.visibility = if (shortcutsOn) View.VISIBLE else View.GONE

    val displayMetrics = resources.displayMetrics
    val density = displayMetrics.density.takeIf { it > 0f } ?: 1f
    val columnHeightDp = (displayMetrics.heightPixels / density).toInt()
    val viewSizeDp = ShortcutSize.slotSizeDp(prefs.shortcutSizeStep)
    val bottomMarginDp = ShortcutOffset.bottomMarginDp(prefs.shortcutOffsetStep)
    val requested = prefs.shortcutCount
    val fitting = SlotCount.fitting(requested, columnHeightDp, viewSizeDp, bottomMarginDp)
    binding.shortcutCount.text = if (fitting < requested) {
        getString(R.string.icons_fit, requested, fitting)
    } else {
        requested.toString()
    }

    binding.shortcutSize.text = when (prefs.shortcutSizeStep) {
        ShortcutSize.SMALL -> getString(R.string.icon_size_small)
        ShortcutSize.MEDIUM -> getString(R.string.icon_size_medium)
        ShortcutSize.LARGE -> getString(R.string.icon_size_large)
        else -> getString(R.string.icon_size_medium)
    }
    binding.shortcutSide.text = getString(if (prefs.shortcutSideStart) R.string.left else R.string.right)
    binding.shortcutHeight.text = ShortcutOffset.format(prefs.shortcutOffsetStep)
    binding.shortcutIconStyle.text = when (prefs.shortcutIconStyle) {
        ShortcutIconStyle.ORIGINAL -> getString(R.string.icon_style_original)
        ShortcutIconStyle.MONOCHROME -> getString(R.string.icon_style_monochrome)
        ShortcutIconStyle.TEXT_COLOUR -> getString(R.string.icon_style_text_colour)
        else -> getString(R.string.icon_style_original)
    }
}

// Home button for recents feature disabled
// private fun toggleHomeButtonRecents() {
//     if (!prefs.homeButtonShowRecents && !isAccessServiceEnabled(requireContext())) {
//         showAccessibilityDialog()
//         return
//     }
//     prefs.homeButtonShowRecents = !prefs.homeButtonShowRecents
//     populateHomeButtonRecents()
// }

// private fun populateHomeButtonRecents() {
//     binding.homeButtonRecents.text = getString(
//         if (prefs.homeButtonShowRecents && isAccessServiceEnabled(requireContext())) R.string.on
//         else R.string.off
//     )
// }

internal fun SettingsFragment.populateLockSettings() {
    binding.toggleLock.text = getString(
        if (prefs.lockModeOn && isAccessServiceEnabled(requireContext())) R.string.on
        else R.string.off
    )
}

internal fun SettingsFragment.populateSwipeApps() {
    binding.swipeLeftApp.text = prefs.appNameSwipeLeft
    binding.swipeRightApp.text = if (prefs.swipeRightAction == Prefs.SWIPE_RIGHT_ACTION_TODAY) {
        getString(R.string.today_page)
    } else {
        prefs.appNameSwipeRight.ifEmpty { getString(R.string.choose_app) }
    }
    if (!prefs.swipeLeftEnabled)
        binding.swipeLeftApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
    else
        binding.swipeLeftApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
    if (!prefs.swipeRightEnabled)
        binding.swipeRightApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
    else
        binding.swipeRightApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
}

internal fun SettingsFragment.toggleTodayOrder() {
    prefs.todayOrderSmart = !prefs.todayOrderSmart
    populateTodayOrder()
}

internal fun SettingsFragment.populateTodayOrder() {
    binding.todayOrder.text = getString(
        if (prefs.todayOrderSmart) R.string.today_order_smart else R.string.today_order_fixed
    )
}

internal fun SettingsFragment.populateTodayCards() {
    val total = TodayCards.all.size
    if (total == 0) {
        binding.todayCards.text = ""
    } else {
        val enabled = TodayCards.all.count { prefs.isTodayCardEnabled(it.key) }
        binding.todayCards.text = "$enabled/$total"
    }
}

internal fun SettingsFragment.showSwipeRightMenu(anchor: View) =
    showSwipeMenu(anchor, SwipeSettingMenu.Side.RIGHT)

internal fun SettingsFragment.showSwipeLeftMenu(anchor: View) =
    showSwipeMenu(anchor, SwipeSettingMenu.Side.LEFT)

/** A tap opens this menu: "Off" first, then what the gesture can do. */
private fun SettingsFragment.showSwipeMenu(anchor: View, side: SwipeSettingMenu.Side) {
    val options = SwipeSettingMenu.options(side)
    anchor.showPopupMenu(
        configure = { menu ->
            options.forEachIndexed { i, option ->
                val title = when (option) {
                    SwipeSettingMenu.Option.OFF -> R.string.off
                    SwipeSettingMenu.Option.TODAY_PAGE -> R.string.today_page
                    SwipeSettingMenu.Option.CHOOSE_APP -> R.string.choose_app
                }
                menu.add(Menu.NONE, i, i, title)
            }
        }
    ) { item ->
        val outcome = SwipeSettingMenu.outcome(side, options[item.itemId])
        when (side) {
            SwipeSettingMenu.Side.LEFT -> prefs.swipeLeftEnabled = outcome.enabled
            SwipeSettingMenu.Side.RIGHT -> {
                prefs.swipeRightEnabled = outcome.enabled
                if (outcome.rightOpensToday == true) prefs.swipeRightAction = Prefs.SWIPE_RIGHT_ACTION_TODAY
            }
        }
        if (outcome.openAppPicker) {
            showAppListIfEnabled(
                if (side == SwipeSettingMenu.Side.LEFT) Constants.FLAG_SET_SWIPE_LEFT_APP
                else Constants.FLAG_SET_SWIPE_RIGHT_APP
            )
        } else {
            populateSwipeApps()
        }
    }
}

internal fun SettingsFragment.showTodayCardsDialog() {
    val cards = TodayCards.all
    val titles = cards.map { getString(it.titleRes) }.toTypedArray()
    val checked = BooleanArray(cards.size) { i -> prefs.isTodayCardEnabled(cards[i].key) }
    todayCardsCheckedState = checked

    val cardsDialog = requireContext().createMultiChoiceDialog(
        title = R.string.today_cards,
        items = titles.toList(),
        checked = checked,
        action = R.string.okay,
        onToggle = { which, isChecked ->
            val card = cards[which]
            if (card.key == NextUpCard.KEY && isChecked &&
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED
            ) {
                requireContext().showMessageDialog(
                    title = R.string.next_up,
                    message = R.string.calendar_permission_message,
                    action = R.string.allow,
                ) {
                    pendingCalendarCardIndex = which
                    calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                }
                false
            } else {
                true
            }
        },
    ) {
        cards.forEachIndexed { i, card ->
            prefs.setTodayCardEnabled(card.key, checked[i])
        }
        populateTodayCards()
    }
    cardsDialog.setOnDismissListener {
        if (todayCardsDialog === cardsDialog) {
            todayCardsDialog = null
            todayCardsCheckedState = null
        }
    }
    dialog?.dismiss()
    dialog = cardsDialog
    todayCardsDialog = cardsDialog
    cardsDialog.showRespectingStatusBar()
}

//    private fun populateDigitalWellbeing() {
//        binding.digitalWellbeing.isVisible = requireContext().isPackageInstalled(Constants.DIGITAL_WELLBEING_PACKAGE_NAME).not()
//                && requireContext().isPackageInstalled(Constants.DIGITAL_WELLBEING_SAMSUNG_PACKAGE_NAME).not()
//                && prefs.hideDigitalWellbeing.not()
//    }
