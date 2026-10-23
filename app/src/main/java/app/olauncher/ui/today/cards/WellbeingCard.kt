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
import app.olauncher.databinding.CardWeekBinding
import app.olauncher.databinding.CardWellbeingBinding
import app.olauncher.helper.appUsagePermissionGranted
import app.olauncher.ui.pause.PauseFormatting
import app.olauncher.ui.today.TodayCard
import app.olauncher.ui.today.TodayOrder
import app.olauncher.ui.today.WellbeingRange
import app.olauncher.ui.today.WellbeingRanges
import app.olauncher.wellbeing.HealthLabels
import app.olauncher.wellbeing.HealthRepository
import app.olauncher.wellbeing.WalkCount
import app.olauncher.wellbeing.HealthSummaryFormatting
import app.olauncher.wellbeing.Nudges
import app.olauncher.wellbeing.UsageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import kotlin.math.max

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
    fun formatPauseWins(notNow: Int, template: String = "Not now × $notNow today"): String? =
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
        ) || WeekCard.isAvailable(context)
    }

    override fun hasContent(context: Context): Boolean = todayHasContent(context) || WeekCard.isAvailable(context)

    /** True when the Today side has a chip or the distracting-apps line to show. */
    internal fun todayHasContent(context: Context): Boolean {
        val prefs = Prefs(context)
        return WellbeingCardLogic.hasContent(
            waterIntervalMin = prefs.nudgeWaterIntervalMin,
            walkIntervalMin = prefs.nudgeWalkIntervalMin,
            distractingApps = prefs.distractingApps,
            usagePermissionGranted = context.appUsagePermissionGranted(),
        )
    }

    /** Ranks like the old week card while the 7 days side shows, else like the old Wellbeing card. */
    override fun priority(context: Context, now: ZonedDateTime): Int {
        val weekSideShown = WeekCard.isAvailable(context) &&
            WellbeingRanges.resolve(Prefs(context).wellbeingShowWeek, todayHasContent(context), true) == WellbeingRange.WEEK
        return TodayOrder.mergedPriority(now.hour, now.dayOfWeek, weekSideShown, super.priority(context, now))
    }

    override fun createView(inflater: LayoutInflater, parent: ViewGroup, fragment: Fragment): View {
        val binding = CardWellbeingBinding.inflate(inflater, parent, false)
        val cardView = WellbeingCardView(binding, fragment)
        binding.root.tag = cardView

        binding.root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                cardView.release()
            }
        })

        binding.tvWater.setOnClickListener {
            Haptics.tick(it)
            cardView.onNudgeClicked(Nudges.WATER)
        }

        binding.tvWalk.setOnClickListener {
            Haptics.tick(it)
            cardView.onNudgeClicked(Nudges.WALK)
        }

        binding.tvRangeToday.setOnClickListener {
            Haptics.tick(it)
            cardView.onRangeClicked(WellbeingRange.TODAY)
        }
        binding.tvRangeWeek.setOnClickListener {
            Haptics.tick(it)
            cardView.onRangeClicked(WellbeingRange.WEEK)
        }

        cardView.render()
        return binding.root
    }

    override fun onResume(view: View) {
        (view.tag as? WellbeingCardView)?.render()
    }
}

/** Per-view state of the Wellbeing card; lives and dies with the card's root view. */
internal class WellbeingCardView(
    private val binding: CardWellbeingBinding,
    fragment: Fragment,
) {
    private var fragment: Fragment? = fragment
    private var released = false
    private var usageJob: Job? = null
    private var healthJob: Job? = null

    /** Walks found automatically in Health Connect today; null when unavailable. */
    private var autoWalks: Int? = null

    /** The week side lives in this card; it keeps its own per-view state. */
    private val weekView: WeekCardView = WeekCardView(
        CardWeekBinding.inflate(LayoutInflater.from(binding.root.context), binding.weekContent, true),
    )

    fun release() {
        released = true
        usageJob?.cancel()
        usageJob = null
        healthJob?.cancel()
        healthJob = null
        fragment = null
    }

    private fun scope(): CoroutineScope? {
        if (released) return null
        return WellbeingCard.scopeProvider?.invoke()
            ?: (try { fragment?.viewLifecycleOwner?.lifecycleScope } catch (_: IllegalStateException) { null })
    }

    fun onNudgeClicked(type: String) {
        val context = binding.root.context
        Prefs(context).incrementNudgeDone(type)
        Nudges.cancel(context, type)
        renderLine1()
    }

    fun onRangeClicked(range: WellbeingRange) {
        Prefs(binding.root.context).wellbeingShowWeek = range == WellbeingRange.WEEK
        renderRange()
    }

    /** Shows the Today or 7 days side, and the switch only when both have content. */
    private fun renderRange() {
        val b = binding
        val context = b.root.context ?: return
        val todayHas = WellbeingCard.todayHasContent(context)
        val weekHas = WeekCard.isAvailable(context)
        val range = WellbeingRanges.resolve(Prefs(context).wellbeingShowWeek, todayHas, weekHas)
        b.llRangeSwitch.isVisible = WellbeingRanges.showSwitch(todayHas, weekHas)
        b.tvRangeToday.isSelected = range == WellbeingRange.TODAY
        b.tvRangeWeek.isSelected = range == WellbeingRange.WEEK
        b.todayContent.isVisible = range == WellbeingRange.TODAY
        b.weekContent.isVisible = range == WellbeingRange.WEEK
        if (range == WellbeingRange.WEEK) weekView.render()
    }

    fun render() {
        renderRange()
        renderLine1()
        renderLine2()
        renderPauseWins()
        renderHealthLine()
    }

    fun renderHealthLine() {
        val b = binding
        val context = b.root.context ?: return
        val prefs = Prefs(context)
        healthJob?.cancel()
        if (!prefs.healthConnectEnabled) {
            b.tvHealth.isVisible = false
            autoWalks = null
            return
        }
        val scope = scope() ?: return
        healthJob = scope.launch {
            val repo = HealthRepository(context, { prefs.healthConnectEnabled })
            val summary = withContext(Dispatchers.IO) { repo.readToday() }
            val walks = withContext(Dispatchers.IO) { repo.readWalkDay()?.totalWalks }
            if (walks != null) prefs.autoWalksToday = walks
            if (walks != autoWalks) {
                autoWalks = walks
                renderLine1()
            }
            val labels = HealthLabels(
                steps = context.getString(R.string.health_line_steps),
                slept = context.getString(R.string.health_line_slept),
                restingHr = context.getString(R.string.health_line_resting_hr),
                exercise = context.getString(R.string.health_line_exercise),
                heartRate = context.getString(R.string.health_line_heart_rate),
            )
            val line = summary?.let { HealthSummaryFormatting.formatLine(it, labels) }
            if (released) return@launch
            b.tvHealth.isVisible = line != null
            if (line != null) b.tvHealth.text = line
        }
    }

    fun renderLine1() {
        val b = binding
        val context = b.root.context ?: return
        val prefs = Prefs(context)

        val waterOn = prefs.nudgeWaterIntervalMin > 0
        val walkOn = prefs.nudgeWalkIntervalMin > 0

        val line1Visible = waterOn || walkOn
        b.llNudges.isVisible = line1Visible

        if (line1Visible) {
            val waterCount = prefs.nudgeDone(Nudges.WATER)
            val walkCount = WalkCount.shown(autoWalks, prefs.nudgeDone(Nudges.WALK))

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
        val b = binding
        val context = b.root.context ?: return
        val notNow = Prefs(context).pauseLog.today().notNow
        val text = WellbeingCardLogic.formatPauseWins(notNow, context.getString(R.string.wellbeing_pause_wins, notNow))
        b.tvPauseWins.isVisible = text != null
        if (text != null) b.tvPauseWins.text = text
    }

    fun renderLine2() {
        val b = binding
        val context = b.root.context ?: return
        val prefs = Prefs(context)

        val distractingPkgs = prefs.distractingApps
        if (!context.appUsagePermissionGranted() || distractingPkgs.isEmpty()) {
            b.tvDistractingApps.isVisible = false
            return
        }

        val scope = scope() ?: return

        usageJob?.cancel()
        usageJob = scope.launch {
            val repo = WellbeingCard.getUsageRepository(context)
            val millis = repo.foregroundMillisToday(distractingPkgs)
            val opens = repo.openCountToday(distractingPkgs)

            if (released) return@launch
            if (millis == null || opens == null) {
                b.tvDistractingApps.isVisible = false
                return@launch
            }

            val durationText = WellbeingCardLogic.formatDuration(millis)
            val opensText = if (opens == 1) {
                context.getString(R.string.wellbeing_open_single)
            } else {
                context.getString(R.string.wellbeing_open_plural, opens)
            }
            val label = context.getString(R.string.distracting_apps)
            b.tvDistractingApps.text =
                context.getString(R.string.wellbeing_distracting_stats, label, durationText, opensText)
            b.tvDistractingApps.isVisible = true
        }
    }
}
