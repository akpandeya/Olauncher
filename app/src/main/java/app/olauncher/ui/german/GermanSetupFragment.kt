package app.olauncher.ui.german

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.data.store.WordLookup
import app.olauncher.databinding.FragmentGermanSetupBinding
import app.olauncher.databinding.ItemGermanSetupChoiceBinding
import app.olauncher.helper.Haptics
import app.olauncher.helper.WordCaptureActivity
import app.olauncher.helper.WordScreenService
import app.olauncher.helper.showToast
import app.olauncher.ui.BaseFragment

/**
 * Three-step German setup: pitch, choose what to turn on, optional screen-pick permission.
 * Nothing is stored until the user finishes; "Not now" or back from the first step changes nothing.
 */
class GermanSetupFragment : BaseFragment() {

    private var _binding: FragmentGermanSetupBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: Prefs

    private var step = GermanSetupStep.WELCOME
    private var choices = GermanSetupChoices()
    private var openedAccessibilitySettings = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGermanSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        choices = GermanSetupFlow.initialChoices(
            previouslySetUp = prefs.germanEnabled,
            offlineDictionary = prefs.germanOfflineDictionary,
            lookupOnline = prefs.germanLookup,
            screenPick = prefs.germanScreenPick,
            germanUi = prefs.germanUi,
        )
        savedInstanceState?.let {
            step = GermanSetupStep.entries.getOrElse(it.getInt(KEY_STEP)) { GermanSetupStep.WELCOME }
            choices = GermanSetupChoices(
                offlineDictionary = it.getBoolean(KEY_OFFLINE, true),
                lookupOnline = it.getBoolean(KEY_LOOKUP),
                screenPick = it.getBoolean(KEY_SCREEN_PICK),
                germanNudges = it.getBoolean(KEY_NUDGES),
            )
            openedAccessibilitySettings = it.getBoolean(KEY_OPENED)
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goBack()
        })
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_STEP, step.ordinal)
        outState.putBoolean(KEY_OFFLINE, choices.offlineDictionary)
        outState.putBoolean(KEY_LOOKUP, choices.lookupOnline)
        outState.putBoolean(KEY_SCREEN_PICK, choices.screenPick)
        outState.putBoolean(KEY_NUDGES, choices.germanNudges)
        outState.putBoolean(KEY_OPENED, openedAccessibilitySettings)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun goBack() {
        val previous = step.previous()
        if (previous == null) {
            findNavController().popBackStack()
        } else {
            step = previous
            render()
        }
    }

    private fun render() {
        val b = _binding ?: return
        listOf(b.segment1, b.segment2, b.segment3).forEachIndexed { index, view ->
            view.setBackgroundResource(
                if (index < step.filledSegments) R.drawable.german_setup_segment_on else R.drawable.german_setup_segment_off
            )
        }
        b.progressBar.contentDescription =
            getString(R.string.german_setup_progress, step.filledSegments, GermanSetupStep.SEGMENTS)
        b.bodyLines.removeAllViews()
        b.choicesContainer.removeAllViews()
        b.choicesContainer.isVisible = step == GermanSetupStep.CHOOSE
        b.bodyLines.isVisible = step != GermanSetupStep.CHOOSE
        b.tvNote.isVisible = true
        when (step) {
            GermanSetupStep.WELCOME -> {
                b.tvTitle.setText(R.string.german_setup_title)
                addLines(
                    R.string.german_setup_point_offline,
                    R.string.german_setup_point_review,
                    R.string.german_setup_point_pick,
                )
                b.tvNote.isVisible = false
                bindButtons(
                    R.string.german_setup_start, R.string.german_setup_not_now,
                    onPrimary = { advance() }, onSecondary = { findNavController().popBackStack() },
                )
            }
            GermanSetupStep.CHOOSE -> {
                b.tvTitle.setText(R.string.german_setup_choose_title)
                addChoice(R.string.german_setup_offline, R.string.german_setup_offline_note, choices.offlineDictionary) {
                    choices = choices.copy(offlineDictionary = it)
                }
                addChoice(R.string.german_setup_lookup, R.string.german_setup_lookup_note, choices.lookupOnline) {
                    choices = choices.copy(lookupOnline = it)
                }
                addChoice(R.string.german_setup_screen_pick, R.string.german_setup_screen_pick_note, choices.screenPick) {
                    choices = choices.copy(screenPick = it)
                }
                addChoice(R.string.german_setup_nudges, R.string.german_setup_nudges_note, choices.germanNudges) {
                    choices = choices.copy(germanNudges = it)
                }
                b.tvNote.setText(R.string.german_setup_change_later)
                bindButtons(
                    R.string.german_setup_continue, R.string.german_setup_back,
                    onPrimary = { advance() }, onSecondary = { goBack() },
                )
            }
            GermanSetupStep.PERMISSION -> {
                b.tvTitle.setText(R.string.german_setup_permission_title)
                addLines(
                    R.string.german_setup_permission_how_1,
                    R.string.german_setup_permission_how_2,
                    R.string.german_setup_permission_how_3,
                )
                b.tvNote.setText(R.string.german_setup_permission_skipped)
                bindButtons(
                    R.string.german_setup_open_accessibility, R.string.german_setup_skip,
                    onPrimary = { openAccessibility() }, onSecondary = { finish() },
                )
            }
        }
    }

    private fun addLines(@StringRes vararg lines: Int) {
        val gap = (12 * resources.displayMetrics.density).toInt()
        for (line in lines) {
            val text = TextView(requireContext())
            text.setTextAppearance(R.style.TextMedium)
            text.setText(line)
            text.setPadding(0, 0, 0, gap)
            binding.bodyLines.addView(text)
        }
    }

    private fun addChoice(@StringRes title: Int, @StringRes note: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
        val row = ItemGermanSetupChoiceBinding.inflate(layoutInflater, binding.choicesContainer, false)
        row.tvChoiceTitle.setText(title)
        row.tvChoiceNote.setText(note)
        row.switchChoice.isChecked = checked
        row.switchChoice.contentDescription = getString(title)
        row.switchChoice.setOnCheckedChangeListener { v, isChecked ->
            Haptics.tick(v)
            onChange(isChecked)
        }
        row.root.setOnClickListener { row.switchChoice.toggle() }
        binding.choicesContainer.addView(row.root)
    }

    private fun bindButtons(
        @StringRes primary: Int,
        @StringRes secondary: Int,
        onPrimary: () -> Unit,
        onSecondary: () -> Unit,
    ) {
        binding.btnPrimary.setText(primary)
        binding.btnSecondary.setText(secondary)
        binding.btnPrimary.setOnClickListener { Haptics.tick(it); onPrimary() }
        binding.btnSecondary.setOnClickListener { Haptics.tick(it); onSecondary() }
    }

    private fun advance() {
        step = GermanSetupFlow.next(step, choices) ?: return finish()
        render()
    }

    /** The only place the permission screen is opened: the user pressed the button in step 3. */
    private fun openAccessibility() {
        openedAccessibilitySettings = true
        val context = requireContext()
        finish()
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: Exception) {
            context.showToast(getString(R.string.unable_to_open_app))
        }
    }

    private fun finish() {
        val context = requireContext()
        val result = GermanSetupFlow.result(choices, openedAccessibilitySettings, alreadyOn = prefs.germanScreenPick)
        prefs.germanEnabled = true
        prefs.germanOfflineDictionary = result.offlineDictionary
        prefs.germanLookup = result.lookupOnline
        prefs.germanScreenPick = result.screenPick
        prefs.germanUi = result.germanUi
        WordCaptureActivity.setEnabled(context, true, WordLookup.isLookupAllowed(true, prefs.germanLookup))
        WordScreenService.setEnabled(context, result.screenPick)
        findNavController().popBackStack()
    }

    private companion object {
        const val KEY_STEP = "step"
        const val KEY_OFFLINE = "offline"
        const val KEY_LOOKUP = "lookup"
        const val KEY_SCREEN_PICK = "screenPick"
        const val KEY_NUDGES = "nudges"
        const val KEY_OPENED = "opened"
    }
}
