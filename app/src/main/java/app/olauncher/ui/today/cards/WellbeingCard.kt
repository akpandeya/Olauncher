package app.olauncher.ui.today.cards

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
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
import app.olauncher.ui.today.attachPressScale
import app.olauncher.ui.today.WellbeingRange
import app.olauncher.ui.today.WellbeingRanges
import app.olauncher.wellbeing.HealthLabels
import app.olauncher.wellbeing.HealthRepository
import app.olauncher.wellbeing.WalkCount
import app.olauncher.wellbeing.HealthSummaryFormatting
import app.olauncher.wellbeing.DaySnapshot
import app.olauncher.wellbeing.Nudges
import app.olauncher.wellbeing.PausePolicy
import app.olauncher.wellbeing.SocialTimeWorker
import app.olauncher.wellbeing.UsageRepository
import app.olauncher.wellbeing.WellbeingBento
import app.olauncher.wellbeing.toDaySnapshot
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

    /** True when a tile or the distracting-apps headline (or its permission prompt) will be shown. */
    fun hasContent(
        waterIntervalMin: Int,
        walkIntervalMin: Int,
        distractingApps: Set<String>,
    ): Boolean = waterIntervalMin > 0 ||
        walkIntervalMin > 0 ||
        distractingApps.isNotEmpty()

    fun formatWater(count: Int, label: String = "Water"): String = "$label $count"

    fun formatWalk(count: Int, label: String = "Walk"): String = "$label $count"

    fun formatLine1(waterPart: String?, walkPart: String?): String =
        listOfNotNull(waterPart, walkPart).joinToString(" · ")

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
    override val drawsOwnHeader: Boolean = true

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

    /** True when the Today side has a tile or the distracting-apps headline to show. */
    internal fun todayHasContent(context: Context): Boolean {
        val prefs = Prefs(context)
        return WellbeingCardLogic.hasContent(
            waterIntervalMin = prefs.nudgeWaterIntervalMin,
            walkIntervalMin = prefs.nudgeWalkIntervalMin,
            distractingApps = prefs.distractingApps,
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

        binding.tileWater.attachPressScale()
        binding.tileWalk.attachPressScale()
        binding.tileNotNow.attachPressScale()

        binding.tileWater.setOnClickListener {
            Haptics.tick(it)
            cardView.onNudgeClicked(Nudges.WATER)
        }

        binding.tileWalk.setOnClickListener {
            Haptics.tick(it)
            cardView.onNudgeClicked(Nudges.WALK)
        }

        binding.tvUsageNeeded.setOnClickListener {
            Haptics.tick(it)
            it.context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
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
    private var weekJob: Job? = null

    /** Walks found automatically in Health Connect today; null when unavailable. */
    private var autoWalks: Int? = null

    /** The 7 days side lives in this card; it keeps its own per-view state. */
    private val weekView: WeekCardView = WeekCardView(
        CardWeekBinding.inflate(LayoutInflater.from(binding.root.context), binding.weekContent, true),
    )

    init {
        ViewCompat.setAccessibilityHeading(binding.tvWellbeingTitle, true)
    }

    fun release() {
        released = true
        usageJob?.cancel()
        usageJob = null
        healthJob?.cancel()
        healthJob = null
        weekJob?.cancel()
        weekJob = null
        fragment = null
    }

    private fun scope(): CoroutineScope? {
        if (released) return null
        return WellbeingCard.scopeProvider?.invoke()
            ?: (try { fragment?.viewLifecycleOwner?.lifecycleScope } catch (_: IllegalStateException) { null })
    }

    private fun appLabel(context: Context, prefs: Prefs, pkg: String): String {
        val renamed = prefs.getAppRenameLabel(pkg)
        return if (renamed.isNotBlank()) renamed else SocialTimeWorker.getAppLabel(context, pkg)
    }

    fun onNudgeClicked(type: String) {
        val context = binding.root.context
        Prefs(context).incrementNudgeDone(type)
        Nudges.cancel(context, type)
        renderTiles()
        if (weekShown()) renderWeek()
    }

    fun onRangeClicked(range: WellbeingRange) {
        Prefs(binding.root.context).wellbeingShowWeek = range == WellbeingRange.WEEK
        renderRange()
    }

    private fun weekShown(): Boolean = binding.weekContent.isVisible

    /** Shows the Today or 7 days side. The switch is always there; an empty side shows zeros. */
    private fun renderRange() {
        val b = binding
        val context = b.root.context ?: return
        val range = WellbeingRanges.resolve(Prefs(context).wellbeingShowWeek, todayHasContent = true, weekHasContent = true)
        b.tvRangeToday.isSelected = range == WellbeingRange.TODAY
        b.tvRangeWeek.isSelected = range == WellbeingRange.WEEK
        b.todayContent.isVisible = range == WellbeingRange.TODAY
        b.weekContent.isVisible = range == WellbeingRange.WEEK
        if (range == WellbeingRange.WEEK) renderWeek()
    }

    fun render() {
        renderRange()
        renderTiles()
        renderUsage()
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
                renderTiles()
                if (weekShown()) renderWeek()
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

    /** Water, Walk and Not now tiles for today; Water and Walk only while their reminders are on. */
    fun renderTiles() {
        val b = binding
        val context = b.root.context ?: return
        val prefs = Prefs(context)

        val waterOn = prefs.nudgeWaterIntervalMin > 0
        val walkOn = prefs.nudgeWalkIntervalMin > 0

        b.llNudges.isVisible = true
        b.tileWater.isVisible = waterOn
        if (waterOn) b.tvWater.text = context.getString(R.string.wellbeing_water_chip, prefs.nudgeDone(Nudges.WATER))
        b.tileWalk.isVisible = walkOn
        if (walkOn) {
            b.tvWalk.text = context.getString(R.string.wellbeing_walk_chip, WalkCount.shown(autoWalks, prefs.nudgeDone(Nudges.WALK)))
        }
        b.tvNotNow.text = prefs.pauseLog.today().notNow.toString()
    }

    /** "Opened anyway 18 · mostly "Look something up"", or nothing when no one opened anyway today. */
    fun renderPauseWins() {
        val b = binding
        val context = b.root.context ?: return
        val day = Prefs(context).pauseLog.today()
        if (day.opened <= 0) {
            b.tvPauseWins.isVisible = false
            return
        }
        val topWhy = WellbeingBento.topWhy(day.toDaySnapshot().why)
        b.tvPauseWins.text = if (topWhy != null) {
            context.getString(R.string.wellbeing_opened_anyway_mostly, day.opened, WeekCardText.formatWhy(context, topWhy))
        } else {
            context.getString(R.string.wellbeing_opened_anyway, day.opened)
        }
        b.tvPauseWins.isVisible = true
    }

    /** Headline: distracting time today, opens and the top app. Asks for usage access when it is missing. */
    fun renderUsage() {
        val b = binding
        val context = b.root.context ?: return
        val prefs = Prefs(context)

        val distractingPkgs = prefs.distractingApps
        val granted = context.appUsagePermissionGranted()
        b.tvUsageNeeded.isVisible = distractingPkgs.isNotEmpty() && !granted
        if (!granted || distractingPkgs.isEmpty()) {
            b.llDistracting.isVisible = false
            return
        }

        val scope = scope() ?: return

        usageJob?.cancel()
        usageJob = scope.launch {
            val repo = WellbeingCard.getUsageRepository(context)
            val millis = repo.foregroundMillisToday(distractingPkgs)
            val opens = repo.openCountToday(distractingPkgs)
            val byPackage = repo.foregroundMillisByPackageToday(distractingPkgs).orEmpty()

            if (released) return@launch
            if (millis == null || opens == null) {
                b.llDistracting.isVisible = false
                return@launch
            }

            val opensText = if (opens == 1) {
                context.getString(R.string.wellbeing_open_single)
            } else {
                context.getString(R.string.wellbeing_open_plural, opens)
            }
            val top = WellbeingBento.topAppsOf(byPackage) { appLabel(context, prefs, it) }.firstOrNull()
            val parts = listOfNotNull(
                context.getString(R.string.wellbeing_today_distracting),
                opensText,
                top?.let {
                    context.getString(R.string.wellbeing_today_top_app, it.first, WeekCardText.formatDuration(context, it.second))
                },
            )
            b.tvDistractingValue.text = WeekCardText.formatDuration(context, (millis / 60_000L).toInt())
            b.tvDistractingApps.text = parts.joinToString(" · ")
            b.llDistracting.isVisible = true
        }
    }

    /** Builds the 7 days side from stored days plus today's live numbers. */
    private fun renderWeek() {
        val context = binding.root.context ?: return
        val scope = scope() ?: return
        weekJob?.cancel()
        weekJob = scope.launch {
            val prefs = Prefs(context)
            val granted = context.appUsagePermissionGranted()
            val pkgs = prefs.distractingApps
            val today = WeekCard.todaySupplier()
            val walks = WalkCount.shown(autoWalks, prefs.nudgeDone(Nudges.WALK))
            val pause = prefs.pauseLog.today()

            var minutes: Int? = null
            var opens: Int? = null
            var topApps: List<Pair<String, Int>> = emptyList()
            if (granted && pkgs.isNotEmpty()) {
                val repo = WellbeingCard.getUsageRepository(context)
                minutes = repo.foregroundMillisToday(pkgs)?.let { (it / 60_000L).toInt() }
                opens = repo.openCountToday(pkgs)
                topApps = repo.foregroundMillisByPackageToday(pkgs)
                    ?.let { WellbeingBento.topAppsOf(it) { pkg -> appLabel(context, prefs, pkg) } }
                    .orEmpty()
            }
            val live = DaySnapshot(
                epochDay = today,
                distractingMinutes = minutes,
                distractingOpens = opens,
                topApps = topApps,
                notNow = pause.notNow,
                opened = pause.opened,
                why = pause.toDaySnapshot().why,
                water = prefs.nudgeDone(Nudges.WATER),
                walk = walks,
            )
            val snapshots = withContext(Dispatchers.IO) { WeekCard.getUsageSnapshotStore(context).getAll() }
            val bento = WellbeingBento.week(
                snapshots, today, live,
                PausePolicy.effectiveAllowanceMin(prefs.pauseAllowanceMin, granted),
            )
            if (!released) weekView.render(bento)
        }
    }
}
