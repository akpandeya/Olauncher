package app.olauncher.ui

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.provider.MediaStore
import android.provider.Settings
import android.text.format.DateFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.view.WindowManager
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import app.olauncher.wellbeing.NudgeFormatting
import app.olauncher.wellbeing.NudgeScheduler
import app.olauncher.wellbeing.Nudges
import app.olauncher.wellbeing.NotificationPermission
import app.olauncher.data.store.Backup
import app.olauncher.data.store.BackupData
import app.olauncher.BuildConfig
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.data.SlotCount
import app.olauncher.data.ShortcutOffset
import app.olauncher.data.ShortcutIconStyle
import app.olauncher.ui.today.TodayCards
import app.olauncher.databinding.DialogTextSizeBinding
import app.olauncher.databinding.FragmentSettingsBinding
import app.olauncher.helper.PhotoWallpaper
import app.olauncher.helper.WallpaperRotateWorker
import app.olauncher.helper.appUsagePermissionGranted
import app.olauncher.helper.createDialog
import app.olauncher.helper.getAppsList
import app.olauncher.helper.getColorFromAttr
import app.olauncher.helper.hideStatusBar
import app.olauncher.helper.isAccessServiceEnabled
import app.olauncher.helper.isDarkThemeOn
import app.olauncher.helper.isEinkDisplay
import app.olauncher.helper.isOlauncherDefault
import app.olauncher.helper.isTablet
import app.olauncher.helper.openAppInfo
import app.olauncher.helper.openUrl
import app.olauncher.helper.setPlainWallpaper
import app.olauncher.helper.OlDialog
import app.olauncher.helper.showMessageDialog
import app.olauncher.helper.showPopupMenu
import app.olauncher.helper.showStatusBar
import app.olauncher.helper.showToast
import app.olauncher.wellbeing.DistractingApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

class SettingsFragment : BaseFragment(), View.OnClickListener, View.OnLongClickListener {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private var dialog: AlertDialog? = null

    private val pickMultipleMedia = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MediaStore.getPickImagesMaxLimit())
    ) { uris ->
        if (uris.isNotEmpty()) {
            handlePickedPhotos(uris)
        }
    }

    private val exportBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            exportBackup(uri)
        }
    }

    private val importBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            handleImportBackup(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationPermission.register(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        viewModel = activity?.run {
            ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")
        viewModel.isOlauncherDefault()

        binding.homeAppsNum.text = prefs.homeAppsNum.toString()
        populatePhotoWallpaperText()
        populateKeyboardText()
        populateDrawerBottomSearch()
        populateScreenTimeOnOff()
        populateDistractingApps()
        populatePauseBeforeApps()
        populateLockSettings()
        // Home button for recents feature disabled
        // populateHomeButtonRecents()
        populateAppThemeText()
        populateTextSize()
        populateBoldFont()
        populateAlignment()
        populateStatusBar()
        populateDateTime()
        populateSwipeApps()
        populateTodayCards()
        populateNudgeWater()
        populateNudgeWalk()
        populateNudgeSocial()
        populateActiveHours()
        populateFooter()
        initClickListeners()
        initObservers()

    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.olauncherHiddenApps -> showHiddenApps()
            R.id.distractingApps, R.id.distractingAppsTitle, R.id.distractingAppsLayout -> showDistractingAppsDialog()
            R.id.pauseBeforeAppsOnOff, R.id.pauseBeforeAppsTitle, R.id.pauseBeforeAppsLayout -> togglePauseBeforeApps()
            R.id.screenTimeOnOff -> viewModel.showDialog.postValue(Constants.Dialog.DIGITAL_WELLBEING)
            R.id.appInfo -> openAppInfo(requireContext(), Process.myUserHandle(), BuildConfig.APPLICATION_ID)
            R.id.setLauncher -> viewModel.resetLauncherLiveData.call()
            R.id.toggleLock -> toggleLockMode()
            // Home button for recents feature disabled
            // R.id.homeButtonRecents -> toggleHomeButtonRecents()
            R.id.autoShowKeyboard -> toggleKeyboardText()
            R.id.drawerBottomSearch, R.id.drawerBottomSearchTitle, R.id.drawerBottomSearchLayout -> toggleDrawerBottomSearch()
            R.id.homeAppsNum -> showHomeAppsNumMenu(view)
            R.id.alignment -> showAlignmentMenu(view)
            R.id.statusBar -> toggleStatusBar()
            R.id.dateTime -> showDateTimeMenu(view)
            R.id.appThemeText -> showAppThemeMenu(view, showSystem = false)
            R.id.textSizeValue -> showTextSizeDialog()
            R.id.boldFont -> toggleBoldFont()

            R.id.photoWallpaper, R.id.photoWallpaperTitle, R.id.photoWallpaperLayout -> openPhotoPicker()
            R.id.swipeLeftApp -> showAppListIfEnabled(Constants.FLAG_SET_SWIPE_LEFT_APP)
            R.id.swipeRightApp, R.id.swipeRightLayout -> showSwipeRightMenu(binding.swipeRightApp)
            R.id.todayCards, R.id.todayCardsTitle, R.id.todayCardsLayout -> showTodayCardsDialog()
            R.id.nudgeWater, R.id.nudgeWaterTitle, R.id.nudgeWaterLayout -> showNudgeWaterMenu(binding.nudgeWater)
            R.id.nudgeWalk, R.id.nudgeWalkTitle, R.id.nudgeWalkLayout -> showNudgeWalkMenu(binding.nudgeWalk)
            R.id.nudgeSocial, R.id.nudgeSocialTitle, R.id.nudgeSocialLayout -> showNudgeSocialMenu(binding.nudgeSocial)
            R.id.nudgeActiveHours, R.id.nudgeActiveHoursTitle, R.id.nudgeActiveHoursLayout -> showActiveHoursDialog()
            R.id.exportBackup, R.id.exportBackupLayout -> launchExportBackup()
            R.id.importBackup, R.id.importBackupLayout -> launchImportBackup()
            R.id.footer -> requireContext().openUrl(Constants.URL_SOURCE)
        }
    }

    override fun onLongClick(view: View): Boolean {
        when (view.id) {
            R.id.photoWallpaper, R.id.photoWallpaperTitle, R.id.photoWallpaperLayout -> {
                showPhotoWallpaperMenu(binding.photoWallpaper)
            }
            R.id.alignment -> {
                prefs.appLabelAlignment = prefs.homeAlignment
                findNavController().navigate(R.id.action_settingsFragment_to_appListFragment)
                requireContext().showToast(getString(R.string.alignment_changed))
            }

            R.id.appThemeText -> showAppThemeMenu(view, showSystem = true)
            R.id.swipeLeftApp -> toggleSwipeLeft()
            R.id.swipeRightApp, R.id.swipeRightLayout -> toggleSwipeRight()
            R.id.toggleLock -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        return true
    }

    private fun initClickListeners() {
        binding.olauncherHiddenApps.setOnClickListener(this)
        binding.appInfo.setOnClickListener(this)
        binding.setLauncher.setOnClickListener(this)
        binding.autoShowKeyboard.setOnClickListener(this)
        binding.drawerBottomSearch.setOnClickListener(this)
        binding.drawerBottomSearchTitle.setOnClickListener(this)
        binding.drawerBottomSearchLayout.setOnClickListener(this)
        binding.photoWallpaper.setOnClickListener(this)
        binding.photoWallpaperTitle.setOnClickListener(this)
        binding.photoWallpaperLayout.setOnClickListener(this)
        binding.toggleLock.setOnClickListener(this)
        // Home button for recents feature disabled
        // binding.homeButtonRecents.setOnClickListener(this)
        binding.homeAppsNum.setOnClickListener(this)
        binding.screenTimeOnOff.setOnClickListener(this)
        binding.distractingApps.setOnClickListener(this)
        binding.distractingAppsTitle.setOnClickListener(this)
        binding.distractingAppsLayout.setOnClickListener(this)
        binding.pauseBeforeAppsOnOff.setOnClickListener(this)
        binding.pauseBeforeAppsTitle.setOnClickListener(this)
        binding.pauseBeforeAppsLayout.setOnClickListener(this)
        binding.alignment.setOnClickListener(this)
        binding.statusBar.setOnClickListener(this)
        binding.dateTime.setOnClickListener(this)
        binding.swipeLeftApp.setOnClickListener(this)
        binding.swipeRightApp.setOnClickListener(this)
        binding.swipeRightLayout.setOnClickListener(this)
        binding.todayCards.setOnClickListener(this)
        binding.todayCardsTitle.setOnClickListener(this)
        binding.todayCardsLayout.setOnClickListener(this)
        binding.nudgeWater.setOnClickListener(this)
        binding.nudgeWaterTitle.setOnClickListener(this)
        binding.nudgeWaterLayout.setOnClickListener(this)
        binding.nudgeWalk.setOnClickListener(this)
        binding.nudgeWalkTitle.setOnClickListener(this)
        binding.nudgeWalkLayout.setOnClickListener(this)
        binding.nudgeSocial.setOnClickListener(this)
        binding.nudgeSocialTitle.setOnClickListener(this)
        binding.nudgeSocialLayout.setOnClickListener(this)
        binding.nudgeActiveHours.setOnClickListener(this)
        binding.nudgeActiveHoursTitle.setOnClickListener(this)
        binding.nudgeActiveHoursLayout.setOnClickListener(this)
        binding.exportBackup.setOnClickListener(this)
        binding.exportBackupLayout.setOnClickListener(this)
        binding.importBackup.setOnClickListener(this)
        binding.importBackupLayout.setOnClickListener(this)
        binding.appThemeText.setOnClickListener(this)
        binding.textSizeValue.setOnClickListener(this)
        binding.boldFont.setOnClickListener(this)
        binding.footer.setOnClickListener(this)


        binding.photoWallpaper.setOnLongClickListener(this)
        binding.photoWallpaperTitle.setOnLongClickListener(this)
        binding.photoWallpaperLayout.setOnLongClickListener(this)
        binding.alignment.setOnLongClickListener(this)
        binding.appThemeText.setOnLongClickListener(this)
        binding.swipeLeftApp.setOnLongClickListener(this)
        binding.swipeRightApp.setOnLongClickListener(this)
        binding.swipeRightLayout.setOnLongClickListener(this)
        binding.toggleLock.setOnLongClickListener(this)
    }

    private fun initObservers() {
        prefs.firstSettingsOpen = false
        viewModel.isOlauncherDefault.observe(viewLifecycleOwner) {
            if (it) {
                binding.setLauncher.text = getString(R.string.change_default_launcher)
                prefs.toShowHintCounter += 1
            }
        }
        viewModel.homeAppAlignment.observe(viewLifecycleOwner) {
            populateAlignment()
        }
        viewModel.updateSwipeApps.observe(viewLifecycleOwner) {
            populateSwipeApps()
        }
    }

    // Popup menus

    private fun showHomeAppsNumMenu(anchor: View) {
        anchor.showPopupMenu(
            configure = { menu ->
                for (num in 0..8) menu.add(Menu.NONE, num, num, num.toString())
            }
        ) { item -> updateHomeAppsNum(item.itemId) }
    }

    private fun showDateTimeMenu(anchor: View) {
        anchor.showPopupMenu(R.menu.date_time) { item ->
            when (item.itemId) {
                R.id.dateTimeOn -> toggleDateTime(Constants.DateTime.ON)
                R.id.dateTimeOff -> toggleDateTime(Constants.DateTime.OFF)
                R.id.dateOnly -> toggleDateTime(Constants.DateTime.DATE_ONLY)
            }
        }
    }

    private fun showAlignmentMenu(anchor: View) {
        anchor.showPopupMenu(
            R.menu.alignment,
            configure = { menu ->
                menu.findItem(R.id.alignmentBottom).setTitle(
                    if (prefs.homeBottomAlignment) R.string.bottom_on else R.string.bottom_off
                )
                menu.findItem(R.id.alignmentShortcuts).setTitle(
                    if (prefs.homeShortcuts) R.string.shortcuts_on else R.string.shortcuts_off
                )
                val countItem = menu.findItem(R.id.alignmentShortcutCount)
                countItem.isVisible = prefs.homeShortcuts
                countItem.title = getString(R.string.icons_count, prefs.shortcutCount)

                val sideItem = menu.findItem(R.id.alignmentShortcutSide)
                sideItem.isVisible = prefs.homeShortcuts
                sideItem.setTitle(
                    if (prefs.shortcutSideStart) R.string.icons_on_left else R.string.icons_on_right
                )

                val heightItem = menu.findItem(R.id.alignmentShortcutHeight)
                heightItem.isVisible = prefs.homeShortcuts

                val styleItem = menu.findItem(R.id.alignmentShortcutIconStyle)
                styleItem.isVisible = prefs.homeShortcuts
            }
        ) { item ->
            when (item.itemId) {
                R.id.alignmentLeft -> viewModel.updateHomeAlignment(Gravity.START)
                R.id.alignmentCenter -> viewModel.updateHomeAlignment(Gravity.CENTER)
                R.id.alignmentRight -> viewModel.updateHomeAlignment(Gravity.END)
                R.id.alignmentBottom -> updateHomeBottomAlignment()
                R.id.alignmentShortcuts -> {
                    prefs.homeShortcuts = !prefs.homeShortcuts
                    viewModel.updateHomeAlignment(prefs.homeAlignment)
                }
                R.id.alignmentShortcutCount -> showShortcutCountDialog()
                R.id.alignmentShortcutSide -> {
                    prefs.shortcutSideStart = !prefs.shortcutSideStart
                    viewModel.updateHomeAlignment(prefs.homeAlignment)
                }
                R.id.alignmentShortcutHeight -> showShortcutHeightDialog()
                R.id.alignmentShortcutIconStyle -> showShortcutIconStyleDialog()
            }
        }
    }

    private fun showShortcutCountDialog() {
        val counts = (SlotCount.MIN..SlotCount.MAX).toList()
        val items = counts.map { it.toString() }.toTypedArray()
        val checkedIndex = counts.indexOf(prefs.shortcutCount)

        val builder = AlertDialog.Builder(requireContext())
            .setTitle(R.string.icons)
            .setSingleChoiceItems(items, checkedIndex) { dialogInterface, which ->
                prefs.shortcutCount = counts[which]
                viewModel.updateHomeAlignment(prefs.homeAlignment)
                dialogInterface.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)

        val alertDialog = builder.create()
        dialog?.dismiss()
        dialog = alertDialog
        val window = alertDialog.window
        if (window != null && !prefs.showStatusBar) {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            alertDialog.show()
            window.hideStatusBar()
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        } else {
            alertDialog.show()
        }
    }

    private fun showShortcutIconStyleDialog() {
        val styles = listOf(
            ShortcutIconStyle.ORIGINAL,
            ShortcutIconStyle.MONOCHROME,
            ShortcutIconStyle.TEXT_COLOUR,
        )
        val items = arrayOf(
            getString(R.string.icon_style_original),
            getString(R.string.icon_style_monochrome),
            getString(R.string.icon_style_text_colour),
        )
        val checkedIndex = styles.indexOf(prefs.shortcutIconStyle).coerceAtLeast(0)

        val builder = AlertDialog.Builder(requireContext())
            .setTitle(R.string.icon_style)
            .setSingleChoiceItems(items, checkedIndex) { dialogInterface, which ->
                prefs.shortcutIconStyle = styles[which]
                viewModel.updateHomeAlignment(prefs.homeAlignment)
                dialogInterface.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)

        val alertDialog = builder.create()
        dialog?.dismiss()
        dialog = alertDialog
        val window = alertDialog.window
        if (window != null && !prefs.showStatusBar) {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            alertDialog.show()
            window.hideStatusBar()
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        } else {
            alertDialog.show()
        }
    }

    // "System" stays hidden unless the row is long pressed
    private fun showAppThemeMenu(anchor: View, showSystem: Boolean) {
        anchor.showPopupMenu(
            R.menu.app_theme,
            configure = { menu -> menu.findItem(R.id.themeSystem).isVisible = showSystem }
        ) { item ->
            when (item.itemId) {
                R.id.themeLight -> updateTheme(AppCompatDelegate.MODE_NIGHT_NO)
                R.id.themeDark -> updateTheme(AppCompatDelegate.MODE_NIGHT_YES)
                R.id.themeSystem -> updateTheme(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
    }

    // Dialogs

    private fun showDialog(newDialog: OlDialog) {
        dialog?.dismiss()
        dialog = newDialog
        newDialog.showRespectingStatusBar()
    }

    private fun showTextSizeDialog() {
        var stepper: DialogTextSizeBinding? = null
        val dialog = requireContext().createDialog(R.string.text_size, R.string.okay) { container ->
            DialogTextSizeBinding.inflate(layoutInflater, container, false).also { stepper = it }.root
        }
        stepper?.apply {
            textSizeCurrent.text = formatScale(pendingOrCurrentTextSizeScale())
            textSizeMinus.setOnClickListener { adjustTextSizePreview(-0.1f, this) }
            textSizePlus.setOnClickListener { adjustTextSizePreview(0.1f, this) }
        }
        dialog.setOnDismissListener { applyTextSizeScale() }
        showDialog(dialog)
    }

    private fun showShortcutHeightDialog() {
        pendingShortcutOffsetStep = prefs.shortcutOffsetStep
        var stepper: DialogTextSizeBinding? = null
        val dialog = requireContext().createDialog(R.string.icons_height, R.string.okay) { container ->
            DialogTextSizeBinding.inflate(layoutInflater, container, false).also { stepper = it }.root
        }
        stepper?.apply {
            textSizeCurrent.text = ShortcutOffset.format(pendingOrCurrentShortcutOffsetStep())
            textSizeMinus.setOnClickListener { adjustShortcutHeightPreview(-1, this) }
            textSizePlus.setOnClickListener { adjustShortcutHeightPreview(1, this) }
        }
        dialog.setOnDismissListener { applyShortcutOffsetStep() }
        showDialog(dialog)
    }

    // Prominent disclosure before sending the user to accessibility settings
    private fun showAccessibilityDialog() {
        val serviceEnabled = isAccessServiceEnabled(requireContext())
        showDialog(
            requireContext().createDialog(
                title = R.string.gestures,
                action = if (serviceEnabled) R.string.disable else R.string.enable,
                message = R.string.accessibility_disclosure,
                neutral = R.string.not_working,
                onNeutral = { requireContext().openUrl(Constants.URL_DOUBLE_TAP) },
                onAction = { openAccessibilityService() },
            )
        )
    }

    private fun toggleSwipeLeft() {
        prefs.swipeLeftEnabled = !prefs.swipeLeftEnabled
        if (prefs.swipeLeftEnabled) {
            binding.swipeLeftApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
            requireContext().showToast(getString(R.string.swipe_left_app_enabled))
        } else {
            binding.swipeLeftApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
            requireContext().showToast(getString(R.string.swipe_left_app_disabled))
        }
    }

    private fun toggleSwipeRight() {
        prefs.swipeRightEnabled = !prefs.swipeRightEnabled
        if (prefs.swipeRightEnabled) {
            binding.swipeRightApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
            requireContext().showToast(getString(R.string.swipe_right_app_enabled))
        } else {
            binding.swipeRightApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
            requireContext().showToast(getString(R.string.swipe_right_app_disabled))
        }
    }

    private fun toggleStatusBar() {
        prefs.showStatusBar = !prefs.showStatusBar
        populateStatusBar()
    }

    private fun populateStatusBar() {
        if (prefs.showStatusBar) {
            requireActivity().window.showStatusBar()
            binding.statusBar.text = getString(R.string.on)
        } else {
            requireActivity().window.hideStatusBar()
            binding.statusBar.text = getString(R.string.off)
        }
    }

    private fun toggleDateTime(selected: Int) {
        prefs.dateTimeVisibility = selected
        populateDateTime()
        viewModel.toggleDateTime()
    }

    private fun populateDateTime() {
        binding.dateTime.text = getString(
            when (prefs.dateTimeVisibility) {
                Constants.DateTime.DATE_ONLY -> R.string.date
                Constants.DateTime.ON -> R.string.on
                else -> R.string.off
            }
        )
    }

    private fun showHiddenApps() {
        if (prefs.hiddenApps.isEmpty()) {
            requireContext().showToast(getString(R.string.no_hidden_apps))
            return
        }
        viewModel.getHiddenApps()
        findNavController().navigate(
            R.id.action_settingsFragment_to_appListFragment,
            bundleOf(Constants.Key.FLAG to Constants.FLAG_HIDDEN_APPS)
        )
    }

    private fun openAccessibilityService() {
        // prefs.lockModeOn = true
        populateLockSettings()
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun toggleLockMode() {
        if (!prefs.lockModeOn && !isAccessServiceEnabled(requireContext())) {
            showAccessibilityDialog()
            return
        }
        prefs.lockModeOn = !prefs.lockModeOn
        populateLockSettings()
    }

    private fun updateHomeAppsNum(num: Int) {
        binding.homeAppsNum.text = num.toString()
        prefs.homeAppsNum = num
        viewModel.refreshHome(true)
    }

    private var pendingTextSizeScale: Float = -1f

    private fun pendingOrCurrentTextSizeScale(): Float =
        if (pendingTextSizeScale > 0) pendingTextSizeScale else prefs.textSizeScale

    private fun formatScale(scale: Float): String = String.format("%.1f", scale)

    private fun adjustTextSizePreview(delta: Float, dialogBinding: DialogTextSizeBinding) {
        val maxScale = if (isTablet(requireContext())) 2.0f else 1.5f
        val current = pendingOrCurrentTextSizeScale()
        val newScale = Math.round((current + delta) * 10f) / 10f
        val clamped = newScale.coerceIn(0.5f, maxScale)
        if (clamped == current) return
        pendingTextSizeScale = clamped
        val formatted = formatScale(clamped)
        binding.textSizeValue.text = formatted
        dialogBinding.textSizeCurrent.text = formatted
    }

    private fun applyTextSizeScale() {
        if (pendingTextSizeScale < 0 || prefs.textSizeScale == pendingTextSizeScale) {
            pendingTextSizeScale = -1f
            return
        }
        prefs.textSizeScale = pendingTextSizeScale
        pendingTextSizeScale = -1f
        val activity = activity ?: return
        if (activity.isChangingConfigurations.not())
            activity.recreate()
    }

    private var pendingShortcutOffsetStep: Int = -1

    private fun pendingOrCurrentShortcutOffsetStep(): Int =
        if (pendingShortcutOffsetStep in ShortcutOffset.MIN_STEP..ShortcutOffset.MAX_STEP) {
            pendingShortcutOffsetStep
        } else {
            prefs.shortcutOffsetStep
        }

    private fun adjustShortcutHeightPreview(delta: Int, dialogBinding: DialogTextSizeBinding) {
        val current = pendingOrCurrentShortcutOffsetStep()
        val clamped = ShortcutOffset.clamp(current + delta)
        if (clamped == current) return
        pendingShortcutOffsetStep = clamped
        dialogBinding.textSizeCurrent.text = ShortcutOffset.format(clamped)
    }

    private fun applyShortcutOffsetStep() {
        if (pendingShortcutOffsetStep < 0 || prefs.shortcutOffsetStep == pendingShortcutOffsetStep) {
            pendingShortcutOffsetStep = -1
            return
        }
        prefs.shortcutOffsetStep = pendingShortcutOffsetStep
        pendingShortcutOffsetStep = -1
        viewModel.updateHomeAlignment(prefs.homeAlignment)
    }

    private fun toggleKeyboardText() {
        if (prefs.autoShowKeyboard && prefs.keyboardMessageShown.not()) {
            viewModel.showDialog.postValue(Constants.Dialog.KEYBOARD)
            prefs.keyboardMessageShown = true
        } else {
            prefs.autoShowKeyboard = !prefs.autoShowKeyboard
            populateKeyboardText()
        }
    }

    private fun updateTheme(appTheme: Int) {
        if (AppCompatDelegate.getDefaultNightMode() == appTheme) return
        prefs.appTheme = appTheme
        populateAppThemeText(appTheme)
        setAppTheme(appTheme)
    }

    private fun setAppTheme(theme: Int) {
        if (AppCompatDelegate.getDefaultNightMode() == theme) return
        if (prefs.photoWallpaperCount > 0) {
            PhotoWallpaper.applyCurrent(requireContext(), prefs)
        } else {
            setPlainWallpaper(theme)
        }
        requireActivity().recreate()
    }

    private fun openPhotoPicker() {
        viewModel.isAwaitingActivityResult = true
        pickMultipleMedia.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun handlePickedPhotos(uris: List<Uri>) {
        if (uris.size == 1) {
            requireContext().showToast(getString(R.string.saving_photo))
        } else {
            requireContext().showToast(getString(R.string.saving_photos, uris.size))
        }
        // Use the activity scope and app context so leaving Settings mid-save does not cancel it
        val appContext = requireContext().applicationContext
        requireActivity().lifecycleScope.launch {
            val savedCount = PhotoWallpaper.savePickedPhotos(appContext, uris)
            if (savedCount > 0) {
                prefs.photoWallpaperCount = savedCount
                prefs.photoWallpaperIndex = 0
                prefs.photoWallpaperLastDay = PhotoWallpaper.today()
                if (_binding != null) populatePhotoWallpaperText()

                WallpaperRotateWorker.schedulePeriodic(appContext)
                WallpaperRotateWorker.runOnce(appContext)
                withContext(Dispatchers.IO) {
                    PhotoWallpaper.applyCurrent(appContext, prefs)
                }
            } else {
                appContext.showToast(R.string.wallpaper_photos_save_failed)
            }
        }
    }

    private fun populatePhotoWallpaperText() {
        val count = prefs.photoWallpaperCount
        binding.photoWallpaper.text = when {
            count == 0 -> getString(R.string.off)
            count == 1 -> getString(R.string.wallpaper_photo_count, 1)
            else -> getString(R.string.wallpaper_photos_count, count)
        }
    }

    private fun showPhotoWallpaperMenu(anchor: View) {
        if (prefs.photoWallpaperCount == 0) {
            openPhotoPicker()
            return
        }
        anchor.showPopupMenu(
            R.menu.photo_wallpaper,
            configure = { menu ->
                val targetTitle = when (prefs.photoWallpaperTarget) {
                    PhotoWallpaper.TARGET_HOME -> R.string.set_on_home
                    PhotoWallpaper.TARGET_LOCK -> R.string.set_on_lock
                    PhotoWallpaper.TARGET_BOTH -> R.string.set_on_both
                    else -> R.string.set_on_home
                }
                menu.findItem(R.id.photoWallpaperTarget).setTitle(targetTitle)
            }
        ) { item ->
            when (item.itemId) {
                R.id.photoWallpaperChange -> openPhotoPicker()
                R.id.photoWallpaperTarget -> cyclePhotoWallpaperTarget()
                R.id.photoWallpaperNext -> nextPhotoNow()
                R.id.photoWallpaperOff -> turnOffPhotoWallpaper()
            }
        }
    }

    private fun cyclePhotoWallpaperTarget() {
        prefs.photoWallpaperTarget = when (prefs.photoWallpaperTarget) {
            PhotoWallpaper.TARGET_HOME -> PhotoWallpaper.TARGET_LOCK
            PhotoWallpaper.TARGET_LOCK -> PhotoWallpaper.TARGET_BOTH
            else -> PhotoWallpaper.TARGET_HOME
        }
        val targetRes = when (prefs.photoWallpaperTarget) {
            PhotoWallpaper.TARGET_HOME -> R.string.set_on_home
            PhotoWallpaper.TARGET_LOCK -> R.string.set_on_lock
            PhotoWallpaper.TARGET_BOTH -> R.string.set_on_both
            else -> R.string.set_on_home
        }
        requireContext().showToast(targetRes)
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            PhotoWallpaper.applyCurrent(requireContext(), prefs)
        }
    }

    private fun nextPhotoNow() {
        if (prefs.photoWallpaperCount <= 0) return
        val nextIndex = (prefs.photoWallpaperIndex + 1) % prefs.photoWallpaperCount
        prefs.photoWallpaperIndex = nextIndex
        prefs.photoWallpaperLastDay = PhotoWallpaper.today()
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            PhotoWallpaper.applyCurrent(requireContext(), prefs)
        }
    }

    private fun turnOffPhotoWallpaper() {
        WallpaperRotateWorker.cancel(requireContext())
        PhotoWallpaper.deleteAll(requireContext())
        prefs.photoWallpaperCount = 0
        prefs.photoWallpaperIndex = 0
        setPlainWallpaper(prefs.appTheme)
        populatePhotoWallpaperText()
    }

    private fun setPlainWallpaper(appTheme: Int) {
        when (appTheme) {
            AppCompatDelegate.MODE_NIGHT_YES -> setPlainWallpaper(requireContext(), android.R.color.black)
            AppCompatDelegate.MODE_NIGHT_NO -> setPlainWallpaper(requireContext(), android.R.color.white)
            else -> {
                if (requireContext().isDarkThemeOn())
                    setPlainWallpaper(requireContext(), android.R.color.black)
                else setPlainWallpaper(requireContext(), android.R.color.white)
            }
        }
    }

    private fun populateAppThemeText(appTheme: Int = prefs.appTheme) {
        when (appTheme) {
            AppCompatDelegate.MODE_NIGHT_YES -> binding.appThemeText.text = getString(R.string.dark)
            AppCompatDelegate.MODE_NIGHT_NO -> binding.appThemeText.text = getString(R.string.light)
            else -> binding.appThemeText.text = getString(R.string.system_default)
        }
    }

    private fun populateTextSize() {
        binding.textSizeValue.text = formatScale(prefs.textSizeScale)
    }

    private fun toggleBoldFont() {
        prefs.boldFont = !prefs.boldFont
        populateBoldFont()
        requireActivity().recreate()
    }

    private fun populateBoldFont() {
        binding.boldFont.text = getString(if (prefs.boldFont) R.string.on else R.string.off)
    }

    private fun populateScreenTimeOnOff() {
        if (requireContext().appUsagePermissionGranted()) binding.screenTimeOnOff.text = getString(R.string.on)
        else binding.screenTimeOnOff.text = getString(R.string.off)
    }

    private fun populateDistractingApps() {
        val count = prefs.distractingApps.size
        binding.distractingApps.text = if (count == 1) {
            getString(R.string.distracting_app_count, count)
        } else {
            getString(R.string.distracting_apps_count, count)
        }
    }

    private fun populatePauseBeforeApps() {
        binding.pauseBeforeAppsOnOff.text = getString(if (prefs.pauseEnabled) R.string.on else R.string.off)
    }

    private fun togglePauseBeforeApps() {
        if (prefs.pauseEnabled) {
            prefs.pauseEnabled = false
            populatePauseBeforeApps()
        } else {
            if (prefs.firstPauseEnable) {
                prefs.firstPauseEnable = false
                prefs.pauseEnabled = true
                populatePauseBeforeApps()
                if (!requireContext().appUsagePermissionGranted()) {
                    showFirstTimePausePermissionDialog()
                }
            } else {
                prefs.pauseEnabled = true
                populatePauseBeforeApps()
            }
        }
    }

    private fun showFirstTimePausePermissionDialog() {
        val dialog = requireContext().createDialog(
            title = R.string.distracting_apps,
            action = R.string.allow,
            message = R.string.pause_usage_access_message,
            neutral = R.string.skip,
            onAction = {
                try {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                } catch (_: Exception) {
                    requireContext().showToast(getString(R.string.unable_to_open_app))
                }
            },
            onNeutral = {}
        )
        dialog.showRespectingStatusBar()
    }

    private fun showDistractingAppsDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            val apps = getAppsList(requireContext(), prefs, includeRegularApps = true, includeHiddenApps = true)
                .filterIsInstance<AppModel.App>()
                .distinctBy { it.appPackage }
                .sortedWith(compareBy(Collator.getInstance()) { it.appLabel })

            if (!isAdded) return@launch

            val installedPackages = apps.map { it.appPackage }.toSet()
            val selectedPackages = DistractingApps.initialSelection(
                installedPackages = installedPackages,
                saved = prefs.distractingApps,
                hasSaved = prefs.hasSavedDistractingApps()
            ).toMutableSet()

            val labels = apps.map { it.appLabel }.toTypedArray()
            val checked = BooleanArray(apps.size) { i -> apps[i].appPackage in selectedPackages }

            val builder = AlertDialog.Builder(requireContext())
                .setTitle(R.string.distracting_apps)
                .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                    checked[which] = isChecked
                    val pkg = apps[which].appPackage
                    if (isChecked) {
                        selectedPackages.add(pkg)
                    } else {
                        selectedPackages.remove(pkg)
                    }
                }
                .setPositiveButton(R.string.okay) { _, _ ->
                    prefs.distractingApps = selectedPackages
                    populateDistractingApps()
                }
                .setNegativeButton(R.string.cancel, null)

            val alertDialog = builder.create()
            dialog?.dismiss()
            dialog = alertDialog
            val window = alertDialog.window
            if (window != null && !prefs.showStatusBar) {
                window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                alertDialog.show()
                window.hideStatusBar()
                window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            } else {
                alertDialog.show()
            }
        }
    }

    private fun populateKeyboardText() {
        if (prefs.autoShowKeyboard) binding.autoShowKeyboard.text = getString(R.string.on)
        else binding.autoShowKeyboard.text = getString(R.string.off)
    }

    private fun populateDrawerBottomSearch() {
        if (prefs.drawerBottomSearch) binding.drawerBottomSearch.text = getString(R.string.on)
        else binding.drawerBottomSearch.text = getString(R.string.off)
    }

    private fun toggleDrawerBottomSearch() {
        prefs.drawerBottomSearch = !prefs.drawerBottomSearch
        populateDrawerBottomSearch()
    }

    private fun updateHomeBottomAlignment() {
        if (viewModel.isOlauncherDefault.value != true) {
            requireContext().showToast(getString(R.string.please_set_olauncher_as_default_first), Toast.LENGTH_LONG)
            return
        }
        prefs.homeBottomAlignment = !prefs.homeBottomAlignment
        populateAlignment()
        viewModel.updateHomeAlignment(prefs.homeAlignment)
    }

    private fun populateAlignment() {
        when (prefs.homeAlignment) {
            Gravity.START -> binding.alignment.text = getString(R.string.left)
            Gravity.CENTER -> binding.alignment.text = getString(R.string.center)
            Gravity.END -> binding.alignment.text = getString(R.string.right)
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

    private fun populateLockSettings() {
        binding.toggleLock.text = getString(
            if (prefs.lockModeOn && isAccessServiceEnabled(requireContext())) R.string.on
            else R.string.off
        )
    }

    private fun populateSwipeApps() {
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

    private fun populateTodayCards() {
        val total = TodayCards.all.size
        if (total == 0) {
            binding.todayCards.text = ""
        } else {
            val enabled = TodayCards.all.count { prefs.isTodayCardEnabled(it.key) }
            binding.todayCards.text = "$enabled/$total"
        }
    }

    private fun showSwipeRightMenu(anchor: View) {
        if (!prefs.swipeRightEnabled) {
            requireContext().showToast(getString(R.string.long_press_to_enable))
            return
        }
        anchor.showPopupMenu(
            configure = { menu ->
                menu.add(Menu.NONE, 1, 1, R.string.today_page)
                menu.add(Menu.NONE, 2, 2, R.string.choose_app)
            }
        ) { item ->
            when (item.itemId) {
                1 -> {
                    prefs.swipeRightAction = Prefs.SWIPE_RIGHT_ACTION_TODAY
                    populateSwipeApps()
                }
                2 -> {
                    showAppListIfEnabled(Constants.FLAG_SET_SWIPE_RIGHT_APP)
                }
            }
        }
    }

    private fun showTodayCardsDialog() {
        val cards = TodayCards.all
        val titles = cards.map { getString(it.titleRes) }.toTypedArray()
        val checked = BooleanArray(cards.size) { i -> prefs.isTodayCardEnabled(cards[i].key) }

        val builder = AlertDialog.Builder(requireContext())
            .setTitle(R.string.today_cards)
            .setMultiChoiceItems(titles, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton(R.string.okay) { _, _ ->
                cards.forEachIndexed { i, card ->
                    prefs.setTodayCardEnabled(card.key, checked[i])
                }
                populateTodayCards()
            }
            .setNegativeButton(R.string.cancel, null)

        val alertDialog = builder.create()
        dialog?.dismiss()
        dialog = alertDialog
        val window = alertDialog.window
        if (window != null && !prefs.showStatusBar) {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            alertDialog.show()
            window.hideStatusBar()
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        } else {
            alertDialog.show()
        }
    }

//    private fun populateDigitalWellbeing() {
//        binding.digitalWellbeing.isVisible = requireContext().isPackageInstalled(Constants.DIGITAL_WELLBEING_PACKAGE_NAME).not()
//                && requireContext().isPackageInstalled(Constants.DIGITAL_WELLBEING_SAMSUNG_PACKAGE_NAME).not()
//                && prefs.hideDigitalWellbeing.not()
//    }

    private fun showAppListIfEnabled(flag: Int) {
        if ((flag == Constants.FLAG_SET_SWIPE_LEFT_APP) and !prefs.swipeLeftEnabled) {
            requireContext().showToast(getString(R.string.long_press_to_enable))
            return
        }
        if ((flag == Constants.FLAG_SET_SWIPE_RIGHT_APP) and !prefs.swipeRightEnabled) {
            requireContext().showToast(getString(R.string.long_press_to_enable))
            return
        }
        viewModel.getAppList(true)
        findNavController().navigate(
            R.id.action_settingsFragment_to_appListFragment,
            bundleOf(Constants.Key.FLAG to flag)
        )
    }

    private fun populateNudgeWater() {
        binding.nudgeWater.text = formatWaterInterval(prefs.nudgeWaterIntervalMin)
    }

    private fun populateNudgeWalk() {
        binding.nudgeWalk.text = formatWalkInterval(prefs.nudgeWalkIntervalMin)
    }

    private fun populateActiveHours() {
        binding.nudgeActiveHours.text = NudgeFormatting.formatActiveHours(
            prefs.nudgeWindowStartMin,
            prefs.nudgeWindowEndMin,
        )
    }

    private fun formatWaterInterval(intervalMin: Int): String = when (intervalMin) {
        60 -> getString(R.string.nudge_every_1_h)
        90 -> getString(R.string.nudge_every_1_5_h)
        120 -> getString(R.string.nudge_every_2_h)
        180 -> getString(R.string.nudge_every_3_h)
        else -> getString(R.string.off)
    }

    private fun formatWalkInterval(intervalMin: Int): String = when (intervalMin) {
        120 -> getString(R.string.nudge_every_2_h)
        180 -> getString(R.string.nudge_every_3_h)
        240 -> getString(R.string.nudge_every_4_h)
        else -> getString(R.string.off)
    }

    private fun showNudgeWaterMenu(anchor: View) {
        val intervals = listOf(
            0 to getString(R.string.off),
            60 to getString(R.string.nudge_every_1_h),
            90 to getString(R.string.nudge_every_1_5_h),
            120 to getString(R.string.nudge_every_2_h),
            180 to getString(R.string.nudge_every_3_h),
        )
        anchor.showPopupMenu(
            configure = { menu ->
                intervals.forEachIndexed { index, pair ->
                    menu.add(Menu.NONE, index, index, pair.second)
                }
            }
        ) { item ->
            val chosen = intervals.getOrNull(item.itemId)?.first ?: return@showPopupMenu
            updateNudgeInterval(Nudges.WATER, chosen)
        }
    }

    private fun showNudgeWalkMenu(anchor: View) {
        val intervals = listOf(
            0 to getString(R.string.off),
            120 to getString(R.string.nudge_every_2_h),
            180 to getString(R.string.nudge_every_3_h),
            240 to getString(R.string.nudge_every_4_h),
        )
        anchor.showPopupMenu(
            configure = { menu ->
                intervals.forEachIndexed { index, pair ->
                    menu.add(Menu.NONE, index, index, pair.second)
                }
            }
        ) { item ->
            val chosen = intervals.getOrNull(item.itemId)?.first ?: return@showPopupMenu
            updateNudgeInterval(Nudges.WALK, chosen)
        }
    }

    private fun updateNudgeInterval(type: String, intervalMin: Int) {
        if (intervalMin <= 0) {
            when (type) {
                Nudges.WATER -> {
                    prefs.nudgeWaterIntervalMin = 0
                    NudgeScheduler.cancel(requireContext(), Nudges.WATER)
                    Nudges.cancel(requireContext(), Nudges.WATER)
                    populateNudgeWater()
                }
                Nudges.WALK -> {
                    prefs.nudgeWalkIntervalMin = 0
                    NudgeScheduler.cancel(requireContext(), Nudges.WALK)
                    Nudges.cancel(requireContext(), Nudges.WALK)
                    populateNudgeWalk()
                }
            }
            return
        }

        NotificationPermission.request(this) { isGranted ->
            if (!isAdded) return@request
            if (isGranted) {
                when (type) {
                    Nudges.WATER -> {
                        prefs.nudgeWaterIntervalMin = intervalMin
                        NudgeScheduler.schedule(requireContext(), Nudges.WATER)
                        populateNudgeWater()
                    }
                    Nudges.WALK -> {
                        prefs.nudgeWalkIntervalMin = intervalMin
                        NudgeScheduler.schedule(requireContext(), Nudges.WALK)
                        populateNudgeWalk()
                    }
                }
            } else {
                when (type) {
                    Nudges.WATER -> {
                        prefs.nudgeWaterIntervalMin = 0
                        NudgeScheduler.cancel(requireContext(), Nudges.WATER)
                        Nudges.cancel(requireContext(), Nudges.WATER)
                        populateNudgeWater()
                    }
                    Nudges.WALK -> {
                        prefs.nudgeWalkIntervalMin = 0
                        NudgeScheduler.cancel(requireContext(), Nudges.WALK)
                        Nudges.cancel(requireContext(), Nudges.WALK)
                        populateNudgeWalk()
                    }
                }
                requireContext().showToast(R.string.notifications_off_for_thumbline)
            }
        }
    }

    private fun populateNudgeSocial() {
        binding.nudgeSocial.text = formatSocialThreshold(prefs.nudgeSocialThresholdMin)
    }

    private fun formatSocialThreshold(thresholdMin: Int): String = when (thresholdMin) {
        15, 30, 45, 60, 90 -> getString(R.string.nudge_social_after_min, thresholdMin)
        else -> getString(R.string.off)
    }

    private fun showNudgeSocialMenu(anchor: View) {
        val thresholds = listOf(0, 15, 30, 45, 60, 90)
        val items = thresholds.map { min ->
            min to if (min == 0) getString(R.string.off) else getString(R.string.nudge_social_after_min, min)
        }
        anchor.showPopupMenu(
            configure = { menu ->
                items.forEachIndexed { index, pair ->
                    menu.add(Menu.NONE, index, index, pair.second)
                }
            }
        ) { item ->
            val chosen = items.getOrNull(item.itemId)?.first ?: return@showPopupMenu
            updateSocialNudgeThreshold(chosen)
        }
    }

    private fun updateSocialNudgeThreshold(thresholdMin: Int) {
        if (thresholdMin <= 0) {
            prefs.nudgeSocialThresholdMin = 0
            prefs.nudgeSocialLastBucket = -1
            NudgeScheduler.cancel(requireContext(), Nudges.SOCIAL)
            Nudges.cancel(requireContext(), Nudges.SOCIAL)
            populateNudgeSocial()
            return
        }

        NotificationPermission.request(this) { isGranted ->
            if (!isAdded) return@request
            if (!isGranted) {
                prefs.nudgeSocialThresholdMin = 0
                prefs.nudgeSocialLastBucket = -1
                NudgeScheduler.cancel(requireContext(), Nudges.SOCIAL)
                Nudges.cancel(requireContext(), Nudges.SOCIAL)
                populateNudgeSocial()
                requireContext().showToast(R.string.notifications_off_for_thumbline)
                return@request
            }

            if (!requireContext().appUsagePermissionGranted()) {
                prefs.nudgeSocialThresholdMin = 0
                prefs.nudgeSocialLastBucket = -1
                NudgeScheduler.cancel(requireContext(), Nudges.SOCIAL)
                Nudges.cancel(requireContext(), Nudges.SOCIAL)
                populateNudgeSocial()
                requireContext().showToast(R.string.usage_access_off_for_thumbline)
                showSocialNudgeUsageAccessDialog()
                return@request
            }

            prefs.nudgeSocialThresholdMin = thresholdMin
            prefs.nudgeSocialLastBucket = -1
            NudgeScheduler.scheduleSocial(requireContext())
            populateNudgeSocial()
        }
    }

    private fun showSocialNudgeUsageAccessDialog() {
        requireContext().showMessageDialog(
            R.string.nudge_social,
            R.string.nudge_social_usage_access_message,
            R.string.allow,
        ) {
            try {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (_: Exception) {
                requireContext().showToast(getString(R.string.unable_to_open_app))
            }
        }
    }

    private fun showActiveHoursDialog() {
        val currentStart = prefs.nudgeWindowStartMin
        val currentEnd = prefs.nudgeWindowEndMin
        showTimePicker(
            title = R.string.nudge_start_time,
            initialHour = currentStart / 60,
            initialMinute = currentStart % 60,
        ) { startHour, startMinute ->
            val chosenStart = startHour * 60 + startMinute
            showTimePicker(
                title = R.string.nudge_end_time,
                initialHour = currentEnd / 60,
                initialMinute = currentEnd % 60,
            ) { endHour, endMinute ->
                val chosenEnd = endHour * 60 + endMinute
                if (NudgeFormatting.isValidActiveHours(chosenStart, chosenEnd)) {
                    prefs.nudgeWindowStartMin = chosenStart
                    prefs.nudgeWindowEndMin = chosenEnd
                    populateActiveHours()
                    NudgeScheduler.rescheduleAll(requireContext())
                } else {
                    requireContext().showToast(R.string.nudge_active_hours_invalid)
                }
            }
        }
    }

    private fun showTimePicker(
        @StringRes title: Int,
        initialHour: Int,
        initialMinute: Int,
        onTimeSet: (Int, Int) -> Unit,
    ) {
        val picker = TimePickerDialog(
            requireContext(),
            { _, hour, minute -> onTimeSet(hour, minute) },
            initialHour,
            initialMinute,
            DateFormat.is24HourFormat(requireContext()),
        )
        picker.setTitle(getString(title))
        val window = picker.window
        if (window != null && !prefs.showStatusBar) {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            picker.show()
            window.hideStatusBar()
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        } else {
            picker.show()
        }
    }

    private fun launchExportBackup() {
        viewModel.isAwaitingActivityResult = true
        exportBackupLauncher.launch(Backup.defaultBackupFileName())
    }

    private fun exportBackup(uri: Uri) {
        val appContext = context?.applicationContext ?: return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val success = try {
                Backup.export(appContext, uri)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
            withContext(Dispatchers.Main) {
                appContext.showToast(if (success) R.string.backup_exported else R.string.backup_export_failed)
            }
        }
    }

    private fun launchImportBackup() {
        viewModel.isAwaitingActivityResult = true
        importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
    }

    private fun handleImportBackup(uri: Uri) {
        val appContext = context?.applicationContext ?: return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val backupData = try {
                Backup.readBackup(appContext, uri)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                if (backupData == null) {
                    appContext.showToast(R.string.not_a_thumbline_backup)
                    return@withContext
                }
                showImportConfirmationDialog(backupData)
            }
        }
    }

    private fun showImportConfirmationDialog(backupData: BackupData) {
        val message = if (backupData.appVersion.isNotBlank()) {
            getString(R.string.backup_restore_confirm, backupData.appVersion)
        } else {
            getString(R.string.backup_restore_confirm_no_version)
        }
        dialog?.dismiss()
        dialog = requireContext().showMessageDialog(
            title = R.string.import_backup,
            message = message,
            action = R.string.replace,
        ) {
            confirmImportBackup(backupData)
        }
    }

    private fun confirmImportBackup(backupData: BackupData) {
        val appContext = context?.applicationContext ?: return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                Backup.restore(appContext, backupData)
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        appContext.showToast(R.string.not_a_thumbline_backup)
                    }
                }
                return@launch
            }
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                NudgeScheduler.rescheduleAll(appContext)
                activity?.recreate()
            }
        }
    }

    private fun populateFooter() {
        binding.footer.text = getString(R.string.settings_footer, BuildConfig.VERSION_NAME, "Olauncher")
    }

    override fun onDestroyView() {
        // Dismissing the text size dialog applies any pending scale via its dismiss listener
        dialog?.dismiss()
        dialog = null
        applyTextSizeScale()
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        viewModel.checkForMessages.call()
        super.onDestroy()
    }
}
