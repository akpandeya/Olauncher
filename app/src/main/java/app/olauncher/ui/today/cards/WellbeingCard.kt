package app.olauncher.ui.today.cards

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import app.olauncher.R
import app.olauncher.helper.Haptics
import app.olauncher.data.Prefs
import app.olauncher.databinding.CardWellbeingBinding
import app.olauncher.helper.appUsagePermissionGranted
import app.olauncher.ui.pause.PauseFormatting
import app.olauncher.ui.today.TodayCard
import app.olauncher.ui.today.TodayOrder
import app.olauncher.wellbeing.HealthLabels
import app.olauncher.wellbeing.HealthRepository
import app.olauncher.wellbeing.HealthSummaryFormatting
import app.olauncher.wellbeing.Nudges
import app.olauncher.wellbeing.UsageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime

object WellbeingCardLogic {

    fun isAvailable(
        waterIntervalMin: Int,
        walkIntervalMin: Int,
        socialThresholdMin: Int,
        distractingApps: Set<String>,
    ): Boolean {
        val anyNudgeOn = waterIntervalMin > 0 || walkIntervalMin > 0 || socialThresholdMin > 0
        return anyNudgeOn || distractingApps.isNotEmpty()
    }

    /** True when at least one chip or the distracting-apps line will be shown. */
    fun hasContent(
        waterIntervalMin: Int,
        walkIntervalMin: Int,
        distractingApps: Set<String>,
        usagePermissionGranted: Boolean,
    ): Boolean = waterIntervalMin > 0 ||
        walkIntervalMin > 0 ||
        (distractingApps.isNotEmpty() && usagePermissionGranted)

    fun formatWater(count: Int, label: String = "Water"): String = "$label $count"

    fun formatWalk(count: Int, label: String = "Walk"): String = "$label $count"

    fun formatLine1(waterPart: String?, walkPart: String?): String =
        listOfNotNull(waterPart, walkPart).joinToString(" · ")

    /** Praise line for "Not now" choices; null when there are none. Never counts opens. */
    fun formatPauseWins(notNow: Int, template: String = "🌿 Not now × $notNow today"): String? =
        if (notNow > 0) template else null

    fun formatDuration(millis: Long): String = PauseFormatting.formatDuration(millis)

    fun formatOpens(count: Int): String = if (count == 1) "1 open" else "$count opens"

    fun formatLine2(
        label: String = "Distracting apps",
        durationText: String,
        opens: Int,
    ): String = "$label $durationText · ${formatOpens(opens)}"
}

object WellbeingCard : TodayCard {

    const val KEY = "wellbeing"

    override val key: String = KEY
    override val titleRes: Int = R.string.wellbeing
    override val emoji: String = "🌱"

    var usageRepositoryProvider: ((Context) -> UsageRepository)? = null
    var scopeProvider: (() -> CoroutineScope)? = null

    internal var binding: CardWellbeingBinding? = null
    private var cardFragment: Fragment? = null
    private var usageJob: Job? = null
    private var healthJob: Job? = null

    fun getUsageRepository(context: Context): UsageRepository {
        return usageRepositoryProvider?.invoke(context) ?: UsageRepository(context)
    }

    override fun isAvailable(context: Context): Boolean {
        val prefs = Prefs(context)
        return WellbeingCardLogic.isAvailable(
            waterIntervalMin = prefs.nudgeWaterIntervalMin,
            walkIntervalMin = prefs.nudgeWalkIntervalMin,
            socialThresholdMin = prefs.nudgeSocialThresholdMin,
            distractingApps = prefs.distractingApps,
        )
    }

    override fun hasContent(context: Context): Boolean {
        val prefs = Prefs(context)
        return WellbeingCardLogic.hasContent(
            waterIntervalMin = prefs.nudgeWaterIntervalMin,
            walkIntervalMin = prefs.nudgeWalkIntervalMin,
            distractingApps = prefs.distractingApps,
            usagePermissionGranted = context.appUsagePermissionGranted(),
        )
    }

    override fun priority(context: Context, now: ZonedDateTime): Int =
        TodayOrder.wellbeingPriority(now.hour, super.priority(context, now))

    override fun createView(inflater: LayoutInflater, parent: ViewGroup, fragment: Fragment): View {
        cardFragment = fragment
        val viewBinding = CardWellbeingBinding.inflate(inflater, parent, false)
        binding = viewBinding

        viewBinding.root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                if (binding === viewBinding) {
                    usageJob?.cancel()
                    usageJob = null
                    healthJob?.cancel()
                    healthJob = null
                    binding = null
                    cardFragment = null
                }
            }
        })

        viewBinding.tvWater.setOnClickListener {
            Haptics.tick(it)
            onNudgeClicked(viewBinding.root.context, Nudges.WATER)
        }

        viewBinding.tvWalk.setOnClickListener {
            Haptics.tick(it)
            onNudgeClicked(viewBinding.root.context, Nudges.WALK)
        }

        render()
        return viewBinding.root
    }

    override fun onResume() {
        render()
    }

    fun onNudgeClicked(context: Context, type: String) {
        val prefs = Prefs(context)
        prefs.incrementNudgeDone(type)
        Nudges.cancel(context, type)
        renderLine1()
    }

    fun render() {
        renderLine1()
        renderLine2()
        renderPauseWins()
        renderHealthLine()
    }

    fun renderHealthLine() {
        val b = binding ?: return
        val context = b.root.context ?: return
        val prefs = Prefs(context)
        healthJob?.cancel()
        if (!prefs.healthConnectEnabled) {
            b.tvHealth.isVisible = false
            return
        }
        val scope = scopeProvider?.invoke()
            ?: (try { cardFragment?.viewLifecycleOwner?.lifecycleScope } catch (_: Exception) { null }
                ?: cardFragment?.lifecycleScope
                ?: CoroutineScope(Dispatchers.Main))
        healthJob = scope.launch {
            val summary = withContext(Dispatchers.IO) {
                HealthRepository(context, { prefs.healthConnectEnabled }).readToday()
            }
            val labels = HealthLabels(
                steps = context.getString(R.string.health_line_steps),
                slept = context.getString(R.string.health_line_slept),
                restingHr = context.getString(R.string.health_line_resting_hr),
                exercise = context.getString(R.string.health_line_exercise),
                heartRate = context.getString(R.string.health_line_heart_rate),
            )
            val line = summary?.let { HealthSummaryFormatting.formatLine(it, labels) }
            val current = binding ?: return@launch
            current.tvHealth.isVisible = line != null
            if (line != null) current.tvHealth.text = line
        }
    }

    fun renderLine1() {
        val b = binding ?: return
        val context = b.root.context ?: return
        val prefs = Prefs(context)

        val waterOn = prefs.nudgeWaterIntervalMin > 0
        val walkOn = prefs.nudgeWalkIntervalMin > 0

        val line1Visible = waterOn || walkOn
        b.llNudges.isVisible = line1Visible

        if (line1Visible) {
            val waterCount = prefs.nudgeDone(Nudges.WATER)
            val walkCount = prefs.nudgeDone(Nudges.WALK)

            b.tvWater.isVisible = waterOn
            if (waterOn) {
                b.tvWater.text = context.getString(R.string.wellbeing_water_chip, waterCount)
            }

            b.tvWalk.isVisible = walkOn
            if (walkOn) {
                b.tvWalk.text = context.getString(R.string.wellbeing_walk_chip, walkCount)
            }

        }
    }

    fun renderPauseWins() {
        val b = binding ?: return
        val context = b.root.context ?: return
        val notNow = Prefs(context).pauseLog.today().notNow
        val text = WellbeingCardLogic.formatPauseWins(notNow, context.getString(R.string.wellbeing_pause_wins, notNow))
        b.tvPauseWins.isVisible = text != null
        if (text != null) b.tvPauseWins.text = text
    }

    fun renderLine2() {
        val b = binding ?: return
        val context = b.root.context ?: return
        val prefs = Prefs(context)

        val distractingPkgs = prefs.distractingApps
        if (!context.appUsagePermissionGranted() || distractingPkgs.isEmpty()) {
            b.tvDistractingApps.isVisible = false
            return
        }

        val scope = scopeProvider?.invoke()
            ?: (try { cardFragment?.viewLifecycleOwner?.lifecycleScope } catch (_: Exception) { null }
                ?: cardFragment?.lifecycleScope
                ?: CoroutineScope(Dispatchers.Main))

        usageJob?.cancel()
        usageJob = scope.launch {
            val repo = getUsageRepository(context)
            val millis = repo.foregroundMillisToday(distractingPkgs)
            val opens = repo.openCountToday(distractingPkgs)

            val currentBinding = binding ?: return@launch
            if (millis == null || opens == null) {
                currentBinding.tvDistractingApps.isVisible = false
                return@launch
            }

            val durationText = WellbeingCardLogic.formatDuration(millis)
            val opensText = if (opens == 1) {
                context.getString(R.string.wellbeing_open_single)
            } else {
                context.getString(R.string.wellbeing_open_plural, opens)
            }
            val label = context.getString(R.string.distracting_apps)
            currentBinding.tvDistractingApps.text =
                context.getString(R.string.wellbeing_distracting_stats, label, durationText, opensText)
            currentBinding.tvDistractingApps.isVisible = true
        }
    }
}
