package app.olauncher.ui.settings

import app.olauncher.helper.HapticTuning
import app.olauncher.helper.Haptics
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
import android.view.WindowManager
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
import app.olauncher.helper.WeatherIconStyle
import app.olauncher.helper.HomeSet
import app.olauncher.helper.HomeSets
import app.olauncher.ui.settings.SettingsRow
import app.olauncher.ui.settings.SettingsRowAvailability
import app.olauncher.ui.settings.SettingsSearch
import app.olauncher.ui.settings.SettingsSection
import app.olauncher.ui.settings.SettingsSections
import app.olauncher.ui.today.cards.NextUpCard
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
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
import app.olauncher.helper.AccentSuggestions
import app.olauncher.helper.createChoiceDialog
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

internal fun SettingsFragment.pendingOrCurrentTextSizeScale(): Float =
    if (pendingTextSizeScale > 0) pendingTextSizeScale else prefs.textSizeScale

internal fun SettingsFragment.formatScale(scale: Float): String = String.format("%.1f", scale)

internal fun SettingsFragment.adjustTextSizePreview(delta: Float, dialogBinding: DialogTextSizeBinding) {
    val maxScale = if (isTablet(requireContext())) 2.0f else 1.5f
    val current = pendingOrCurrentTextSizeScale()
    val newScale = Math.round((current + delta) * 10f) / 10f
    val clamped = newScale.coerceIn(0.5f, maxScale)
    if (clamped == current) return
    pendingTextSizeScale = clamped
    val formatted = formatScale(clamped)
    binding.textSizeValue.text = formatted
    dialogBinding.textSizeCurrent.text = formatted
    updateTextSizeSample(dialogBinding.textSizeSample, clamped)
}

internal fun SettingsFragment.updateTextSizeSample(sampleView: TextView, scale: Float) {
    val typedValue = TypedValue()
    resources.getValue(R.dimen.text_large, typedValue, true)
    val baseSp = TypedValue.complexToFloat(typedValue.data)
    val basePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, baseSp, resources.displayMetrics)
    sampleView.setTextSize(TypedValue.COMPLEX_UNIT_PX, basePx * scale)
}

internal fun SettingsFragment.applyTextSizeScale() {
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

internal fun SettingsFragment.pendingOrCurrentShortcutOffsetStep(): Int =
    if (pendingShortcutOffsetStep in ShortcutOffset.MIN_STEP..ShortcutOffset.MAX_STEP) {
        pendingShortcutOffsetStep
    } else {
        prefs.shortcutOffsetStep
    }

internal fun SettingsFragment.adjustShortcutHeightPreview(delta: Int, dialogBinding: DialogTextSizeBinding) {
    val current = pendingOrCurrentShortcutOffsetStep()
    val clamped = ShortcutOffset.clamp(current + delta)
    if (clamped == current) return
    pendingShortcutOffsetStep = clamped
    binding.shortcutHeight.text = ShortcutOffset.format(clamped)
    dialogBinding.textSizeCurrent.text = ShortcutOffset.format(clamped)
}

internal fun SettingsFragment.applyShortcutOffsetStep() {
    if (pendingShortcutOffsetStep < 0 || prefs.shortcutOffsetStep == pendingShortcutOffsetStep) {
        pendingShortcutOffsetStep = -1
        populateShortcutIcons()
        return
    }
    prefs.shortcutOffsetStep = pendingShortcutOffsetStep
    pendingShortcutOffsetStep = -1
    populateShortcutIcons()
    viewModel.updateHomeAlignment(prefs.homeAlignment)
}

internal fun SettingsFragment.toggleKeyboardText() {
    if (prefs.autoShowKeyboard && prefs.keyboardMessageShown.not()) {
        viewModel.showDialog.postValue(Constants.Dialog.KEYBOARD)
        prefs.keyboardMessageShown = true
    } else {
        prefs.autoShowKeyboard = !prefs.autoShowKeyboard
        populateKeyboardText()
    }
}

internal fun SettingsFragment.updateTheme(appTheme: Int) {
    if (AppCompatDelegate.getDefaultNightMode() == appTheme) return
    prefs.appTheme = appTheme
    populateAppThemeText(appTheme)
    setAppTheme(appTheme)
}

internal fun SettingsFragment.setAppTheme(theme: Int) {
    if (AppCompatDelegate.getDefaultNightMode() == theme) return
    if (prefs.photoWallpaperCount > 0) {
        PhotoWallpaper.applyCurrent(requireContext(), prefs)
    } else {
        setPlainWallpaperByTheme(requireContext(), theme)
    }
    requireActivity().recreate()
}


internal fun SettingsFragment.populateAppThemeText(appTheme: Int = prefs.appTheme) {
    when (appTheme) {
        AppCompatDelegate.MODE_NIGHT_YES -> binding.appThemeText.text = getString(R.string.dark)
        AppCompatDelegate.MODE_NIGHT_NO -> binding.appThemeText.text = getString(R.string.light)
        else -> binding.appThemeText.text = getString(R.string.system_default)
    }
}

internal fun SettingsFragment.populateAccent() {
    binding.accent.text = when (Accent.fromId(prefs.accent)) {
        Accent.OFF -> getString(R.string.off)
        Accent.SYSTEM -> getString(R.string.accent_system)
        Accent.SAGE -> getString(R.string.accent_sage)
        Accent.SAND -> getString(R.string.accent_sand)
        Accent.SKY -> getString(R.string.accent_sky)
        Accent.ROSE -> getString(R.string.accent_rose)
        Accent.WALLPAPER -> getString(R.string.accent_wallpaper)
    }
}

internal fun SettingsFragment.selectAccent(accent: Accent) {
    if (prefs.accent == accent.id) return
    prefs.accent = accent.id
    populateAccent()
    requireActivity().recreate()
}

private const val ACCENT_FROM_WALLPAPER_ITEM = 100

/** Suggestions come from the wallpaper as it is now, so they follow a wallpaper change. */
internal fun SettingsFragment.showWallpaperAccentDialog() {
    val context = requireContext()
    val wallpaperColors = try {
        android.app.WallpaperManager.getInstance(context).getWallpaperColors(android.app.WallpaperManager.FLAG_SYSTEM)
    } catch (e: Exception) {
        null
    }
    val themeIsDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
    val suggestions = AccentSuggestions.suggest(
        listOfNotNull(wallpaperColors?.primaryColor, wallpaperColors?.secondaryColor, wallpaperColors?.tertiaryColor)
            .map { it.toArgb() }
            .flatMap { listOf(it, AccentSuggestions.fromHsl(AccentSuggestions.hsl(it).let { h -> (h[0] + 30f) % 360f }, AccentSuggestions.hsl(it)[1], AccentSuggestions.hsl(it)[2])) },
        themeIsDark,
    )
    if (suggestions.isEmpty()) {
        Toast.makeText(context, R.string.accent_wallpaper_none, Toast.LENGTH_SHORT).show()
        return
    }
    val labels = suggestions.map { colour ->
        android.text.SpannableString("\u25CF  #%06X".format(colour and 0xFFFFFF)).apply {
            setSpan(android.text.style.ForegroundColorSpan(colour), 0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
    context.createChoiceDialog(title = R.string.accent_from_wallpaper, message = R.string.accent_from_wallpaper_hint, items = labels) { which ->
        prefs.accentCustomColor = suggestions[which]
        prefs.accent = Accent.WALLPAPER.id
        populateAccent()
        requireActivity().recreate()
    }.showRespectingStatusBar()
}

internal fun SettingsFragment.showAccentMenu(anchor: View) {
    val options = listOf(
        Accent.OFF to getString(R.string.off),
        Accent.SYSTEM to getString(R.string.accent_system),
        Accent.SAGE to getString(R.string.accent_sage),
        Accent.SAND to getString(R.string.accent_sand),
        Accent.SKY to getString(R.string.accent_sky),
        Accent.ROSE to getString(R.string.accent_rose),
    )

    anchor.showPopupMenu(
        configure = { menu ->
            options.forEach { (accent, label) ->
                menu.add(Menu.NONE, accent.id, accent.id, label)
            }
            menu.add(Menu.NONE, ACCENT_FROM_WALLPAPER_ITEM, ACCENT_FROM_WALLPAPER_ITEM, getString(R.string.accent_from_wallpaper))
        }
    ) { item ->
        if (item.itemId == ACCENT_FROM_WALLPAPER_ITEM) showWallpaperAccentDialog() else selectAccent(Accent.fromId(item.itemId))
    }
}

internal fun SettingsFragment.populateFontFamily() {
    val resource = FontFamilyResources.forId(prefs.fontFamily)
    binding.fontFamily.text = getString(resource.labelRes)
}

internal fun SettingsFragment.selectFontFamily(family: Int) {
    if (prefs.fontFamily == family) return
    prefs.fontFamily = family
    populateFontFamily()
    requireActivity().recreate()
}

internal fun SettingsFragment.showFontDialog() {
    val interTypeface = loadFontOrNull(FontFamilyResources.INTER.fontRes)
    val plexTypeface = loadFontOrNull(FontFamilyResources.PLEX.fontRes)

    var fontDialog: OlDialog? = null
    val dialog = requireContext().createDialog(
        title = R.string.font,
        action = R.string.cancel,
    ) { container ->
        val fontBinding = DialogFontFamilyBinding.inflate(layoutInflater, container, false)
        fontBinding.fontOptionSystem.typeface = Typeface.DEFAULT
        fontBinding.fontOptionInter.typeface = interTypeface ?: Typeface.DEFAULT
        fontBinding.fontOptionPlex.typeface = plexTypeface ?: Typeface.DEFAULT

        fontBinding.fontOptionSystem.setOnClickListener {
            fontDialog?.dismiss()
            selectFontFamily(Constants.FontFamily.SYSTEM)
        }
        fontBinding.fontOptionInter.setOnClickListener {
            fontDialog?.dismiss()
            selectFontFamily(Constants.FontFamily.INTER)
        }
        fontBinding.fontOptionPlex.setOnClickListener {
            fontDialog?.dismiss()
            selectFontFamily(Constants.FontFamily.PLEX)
        }
        fontBinding.root
    }
    fontDialog = dialog
    showDialog(dialog)
}

internal fun SettingsFragment.loadFontOrNull(@FontRes fontRes: Int?): Typeface? {
    if (fontRes == null) return null
    return try {
        ResourcesCompat.getFont(requireContext(), fontRes)
    } catch (e: Exception) {
        Log.w("SettingsFragment", "Failed to load font $fontRes", e)
        null
    }
}

internal fun SettingsFragment.populateTextSize() {
    binding.textSizeValue.text = formatScale(prefs.textSizeScale)
}

internal fun SettingsFragment.toggleBoldFont() {
    prefs.boldFont = !prefs.boldFont
    populateBoldFont()
    requireActivity().recreate()
}

internal fun SettingsFragment.populateBoldFont() {
    binding.boldFont.text = getString(if (prefs.boldFont) R.string.on else R.string.off)
}

internal fun SettingsFragment.toggleTextContrast() {
    prefs.homeTextContrast =
        if (prefs.homeTextContrast == Prefs.TEXT_CONTRAST_AUTO) Prefs.TEXT_CONTRAST_THEME else Prefs.TEXT_CONTRAST_AUTO
    populateTextContrast()
    viewModel.toggleDateTime()
}

internal fun SettingsFragment.populateTextContrast() {
    binding.textContrast.text = getString(
        if (prefs.homeTextContrast == Prefs.TEXT_CONTRAST_THEME) R.string.text_contrast_follow_theme else R.string.auto
    )
}

internal fun SettingsFragment.cycleHomeDarkness() {
    prefs.homeWashLevel = when (prefs.homeWashLevel) {
        Prefs.WASH_AUTO -> Prefs.WASH_STRONG
        Prefs.WASH_STRONG -> Prefs.WASH_OFF
        else -> Prefs.WASH_AUTO
    }
    populateHomeDarkness()
    viewModel.toggleDateTime()
}

internal fun SettingsFragment.populateHomeDarkness() {
    binding.homeDarkness.text = getString(
        when (prefs.homeWashLevel) {
            Prefs.WASH_STRONG -> R.string.home_darkness_strong
            Prefs.WASH_OFF -> R.string.off
            else -> R.string.auto
        }
    )
}

internal fun SettingsFragment.cycleTextBrightness() {
    prefs.homeTextBrightness = when (prefs.homeTextBrightness) {
        Prefs.BRIGHTNESS_FULL -> Prefs.BRIGHTNESS_SOFT
        Prefs.BRIGHTNESS_SOFT -> Prefs.BRIGHTNESS_DIM
        else -> Prefs.BRIGHTNESS_FULL
    }
    populateTextBrightness()
    viewModel.toggleDateTime()
}

internal fun SettingsFragment.populateTextBrightness() {
    binding.textBrightness.text = getString(
        when (prefs.homeTextBrightness) {
            Prefs.BRIGHTNESS_SOFT -> R.string.text_brightness_soft
            Prefs.BRIGHTNESS_DIM -> R.string.text_brightness_dim
            else -> R.string.text_brightness_full
        }
    )
}

internal fun SettingsFragment.toggleMusicPulse() {
    prefs.homeMusicPulse = !prefs.homeMusicPulse
    populateMusicPulse()
    viewModel.toggleDateTime()
}

internal fun SettingsFragment.populateMusicPulse() {
    binding.musicPulse.text = getString(if (prefs.homeMusicPulse) R.string.on else R.string.off)
}

/** Slider for the music pulse glow strength. Saves on every change; Home picks it up on its next music tick. */
internal fun SettingsFragment.setupGlowStrength() {
    binding.glowStrengthSeekBar.max = MusicPulse.GLOW_STRENGTH_MAX
    binding.glowStrengthSeekBar.progress = prefs.homeGlowStrength
    populateGlowStrength(prefs.homeGlowStrength)
    binding.glowStrengthSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (!fromUser) return
            prefs.homeGlowStrength = progress
            populateGlowStrength(prefs.homeGlowStrength)
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

        override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
    })
}

internal fun SettingsFragment.populateGlowStrength(percent: Int) {
    binding.glowStrength.text = getString(R.string.glow_strength_percent, percent)
}

internal fun SettingsFragment.toggleWeatherEffects() {
    prefs.homeWeatherEffects = !prefs.homeWeatherEffects
    populateWeatherEffects()
    viewModel.toggleDateTime()
}

internal fun SettingsFragment.populateWeatherEffects() {
    binding.weatherEffects.text = getString(if (prefs.homeWeatherEffects) R.string.on else R.string.off)
}

internal fun SettingsFragment.toggleWeatherIcons() {
    prefs.homeWeatherIconStyle = if (prefs.homeWeatherIconStyle == WeatherIconStyle.COLOUR) {
        WeatherIconStyle.MONOCHROME
    } else {
        WeatherIconStyle.COLOUR
    }
    populateWeatherIcons()
    viewModel.toggleDateTime()
}

internal fun SettingsFragment.populateWeatherIcons() {
    binding.weatherIcons.text = getString(
        if (prefs.homeWeatherIconStyle == WeatherIconStyle.MONOCHROME) {
            R.string.weather_icon_style_monochrome
        } else {
            R.string.weather_icon_style_colour
        }
    )
}

/** Strength and duration sliders. Save on every change and buzz once on release so the user can feel the new setting. */
internal fun SettingsFragment.setupHapticSliders() {
    binding.hapticStrengthSeekBar.max = HapticTuning.STRENGTH_MAX
    binding.hapticStrengthSeekBar.progress = prefs.hapticStrength
    populateHapticStrength(prefs.hapticStrength)
    binding.hapticStrengthSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (!fromUser) return
            prefs.hapticStrength = progress
            populateHapticStrength(prefs.hapticStrength)
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

        override fun onStopTrackingTouch(seekBar: SeekBar) {
            Haptics.confirm(seekBar)
        }
    })

    binding.hapticDurationSeekBar.max = HapticTuning.DURATION_MAX_MS - HapticTuning.DURATION_MIN_MS
    binding.hapticDurationSeekBar.progress = prefs.hapticDurationMs - HapticTuning.DURATION_MIN_MS
    populateHapticDuration(prefs.hapticDurationMs)
    binding.hapticDurationSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (!fromUser) return
            prefs.hapticDurationMs = progress + HapticTuning.DURATION_MIN_MS
            populateHapticDuration(prefs.hapticDurationMs)
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

        override fun onStopTrackingTouch(seekBar: SeekBar) {
            Haptics.confirm(seekBar)
        }
    })
}

internal fun SettingsFragment.populateHapticStrength(percent: Int) {
    binding.hapticStrength.text = getString(R.string.glow_strength_percent, percent)
}

internal fun SettingsFragment.populateHapticDuration(ms: Int) {
    binding.hapticDuration.text = getString(R.string.haptic_duration_ms, ms)
}
