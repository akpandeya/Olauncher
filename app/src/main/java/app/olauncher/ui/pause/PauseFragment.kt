package app.olauncher.ui.pause

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Prefs
import app.olauncher.data.store.Leitner
import app.olauncher.data.store.PauseWordPicker
import app.olauncher.data.store.WordsStore
import app.olauncher.databinding.FragmentPauseBinding
import app.olauncher.helper.appUsagePermissionGranted
import app.olauncher.helper.getUserHandleFromString
import app.olauncher.helper.showPopupMenu
import app.olauncher.ui.today.WordReveal
import app.olauncher.wellbeing.DistractingApps
import app.olauncher.wellbeing.PausePolicy
import app.olauncher.wellbeing.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class PauseFragment : Fragment() {

    companion object {
        var storeProvider: ((Context) -> WordsStore)? = null
        var usageRepositoryProvider: ((Context) -> UsageRepository)? = null
        var epochDaySupplier: () -> Long = { LocalDate.now().toEpochDay() }
    }

    private var _binding: FragmentPauseBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var prefs: Prefs
    private var countdownJob: Job? = null
    private var wordReveal: WordReveal? = null

    private val appPackage: String by lazy { arguments?.getString("package").orEmpty() }
    private val activityClassName: String? by lazy { arguments?.getString("class") }
    private val userString: String by lazy { arguments?.getString("user").orEmpty() }
    private val appLabel: String by lazy { arguments?.getString("label").orEmpty() }
    private val shortcutId: String? by lazy { arguments?.getString("shortcutId") }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPauseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())

        binding.appName.text = appLabel

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    popToHome()
                }
            }
        )

        binding.btnNotNow.text = getString(
            if (prefs.isGermanUi) R.string.not_now_de else R.string.not_now
        )
        binding.btnNotNow.setOnClickListener {
            popToHome()
        }

        binding.btnOpen.setOnClickListener {
            openApp()
        }

        binding.btnOpen.setOnLongClickListener {
            offerStopPausing(binding.btnOpen)
            true
        }

        binding.btnOpenContainer.setOnLongClickListener {
            offerStopPausing(binding.btnOpen)
            true
        }

        setupPauseWord()
        startDecisionAndCountdown()
    }

    private fun startDecisionAndCountdown() {
        countdownJob?.cancel()
        countdownJob = viewLifecycleOwner.lifecycleScope.launch {
            updateOpenButton(prefs.pauseSeconds)

            val context = context ?: return@launch
            val baseSeconds = prefs.pauseSeconds
            val allowanceMin = prefs.pauseAllowanceMin
            val escalate = prefs.pauseEscalate
            val distractingPkgs = if (prefs.hasSavedDistractingApps()) {
                prefs.distractingApps
            } else {
                DistractingApps.DEFAULT_PACKAGES
            }

            val (usageLine, decision) = withContext(Dispatchers.IO) {
                if (context.appUsagePermissionGranted()) {
                    val repository = usageRepositoryProvider?.invoke(context) ?: UsageRepository(context)
                    val opens = repository.openCountToday(appPackage) ?: 0
                    val appMillis = repository.foregroundMillisToday(setOf(appPackage)) ?: 0L
                    val sharedMillis = repository.foregroundMillisToday(distractingPkgs)
                    val minutesToday = sharedMillis?.let { (it / 60_000L).toInt() }
                    val decision = PausePolicy.delaySeconds(
                        base = baseSeconds,
                        opensToday = opens,
                        minutesToday = minutesToday,
                        allowanceMin = allowanceMin,
                        escalate = escalate,
                    )
                    val line = PauseFormatting.formatUsageStatsLine(opens, appMillis)
                    line to decision
                } else {
                    val opens = prefs.ownOpenCount(appPackage)
                    val decision = PausePolicy.delaySeconds(
                        base = baseSeconds,
                        opensToday = opens,
                        minutesToday = null,
                        allowanceMin = allowanceMin,
                        escalate = escalate,
                    )
                    val line = PauseFormatting.formatThumblineOpensLine(opens)
                    line to decision
                }
            }

            if (_binding == null) return@launch
            binding.usageStats.text = usageLine

            if (decision.overAllowance && allowanceMin > 0) {
                binding.overAllowance.text = PauseFormatting.formatOverAllowanceLine(allowanceMin)
                binding.overAllowance.isVisible = true
            } else {
                binding.overAllowance.isVisible = false
            }

            val totalSeconds = decision.seconds
            if (totalSeconds <= 0) {
                updateOpenButton(0)
                return@launch
            }

            for (remaining in totalSeconds downTo 1) {
                updateOpenButton(remaining)
                delay(1000L)
            }
            updateOpenButton(0)
        }
    }

    private fun updateOpenButton(remaining: Int) {
        if (_binding == null) return
        val useGerman = prefs.isGermanUi
        if (remaining > 0) {
            binding.btnOpen.isEnabled = false
            binding.btnOpen.alpha = 0.5f
            binding.btnOpen.text = getString(
                if (useGerman) R.string.pause_open_countdown_de else R.string.pause_open_countdown,
                remaining
            )
        } else {
            binding.btnOpen.isEnabled = true
            binding.btnOpen.alpha = 1.0f
            binding.btnOpen.text = getString(
                if (useGerman) R.string.pause_open_de else R.string.pause_open
            )
        }
    }

    private fun openApp() {
        val appModel = buildAppModel()
        viewModel.launchAfterPause(appModel)
        popToHome()
    }

    private fun offerStopPausing(anchor: View) {
        anchor.showPopupMenu(
            configure = { menu ->
                menu.add(
                    Menu.NONE,
                    1,
                    Menu.NONE,
                    getString(R.string.stop_pausing_app, appLabel)
                )
            }
        ) { item ->
            if (item.itemId == 1) {
                prefs.distractingApps = prefs.distractingApps - appPackage
                openApp()
            }
        }
    }

    private fun buildAppModel(): AppModel {
        val userHandle = getUserHandleFromString(requireContext(), userString)
        return if (!shortcutId.isNullOrEmpty()) {
            AppModel.PinnedShortcut(
                shortcutId = shortcutId!!,
                appLabel = appLabel,
                user = userHandle,
                key = null,
                appPackage = appPackage,
                isNew = false
            )
        } else {
            AppModel.App(
                appLabel = appLabel,
                key = null,
                appPackage = appPackage,
                activityClassName = activityClassName,
                isNew = false,
                user = userHandle
            )
        }
    }

    private fun getStore(context: Context): WordsStore {
        return storeProvider?.invoke(context) ?: WordsStore(context)
    }

    private fun setupPauseWord() {
        if (!prefs.germanEnabled || !prefs.germanPauseWord) {
            binding.wordReveal.root.isVisible = false
            return
        }

        val context = context ?: return
        val store = getStore(context)
        val today = epochDaySupplier()
        val dueWords = store.due(today)
        val pickedWord = PauseWordPicker.pick(dueWords, prefs.germanPauseLastWordId)

        if (pickedWord == null) {
            binding.wordReveal.root.isVisible = false
            return
        }

        prefs.germanPauseLastWordId = pickedWord.id
        binding.wordReveal.root.isVisible = true

        val helper = wordReveal ?: WordReveal(
            binding = binding.wordReveal,
            onAnswer = { word, knewIt ->
                val ctx = this@PauseFragment.context
                if (ctx != null) {
                    val s = getStore(ctx)
                    val currentToday = epochDaySupplier()
                    val updated = if (knewIt) {
                        Leitner.knowIt(word, currentToday)
                    } else {
                        Leitner.again(word, currentToday)
                    }
                    s.update(updated)
                }
                if (_binding != null) {
                    binding.wordReveal.root.isVisible = false
                }
            },
            onWordUpdated = { updated ->
                val ctx = this@PauseFragment.context
                if (ctx != null) {
                    val s = getStore(ctx)
                    s.update(updated)
                    wordReveal?.bind(updated)
                }
            },
            onWordDeleted = { word ->
                val ctx = this@PauseFragment.context
                if (ctx != null) {
                    val s = getStore(ctx)
                    s.delete(word.id)
                }
                if (_binding != null) {
                    binding.wordReveal.root.isVisible = false
                }
            }
        ).also { wordReveal = it }

        helper.bind(pickedWord)
    }

    private fun popToHome() {
        findNavController().popBackStack(R.id.mainFragment, false)
    }

    override fun onDestroyView() {
        countdownJob?.cancel()
        wordReveal = null
        _binding = null
        super.onDestroyView()
    }
}
