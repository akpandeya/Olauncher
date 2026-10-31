package app.olauncher.ui.today.cards

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.data.store.DrillHubLogic
import app.olauncher.data.store.DrillLastRound
import app.olauncher.data.store.DrillRoundPlanner
import app.olauncher.data.store.DrillScores
import app.olauncher.data.store.DrillTables
import app.olauncher.data.store.DrillType
import app.olauncher.databinding.CardDrillsBinding
import app.olauncher.helper.Haptics
import app.olauncher.ui.today.TodayCard
import app.olauncher.ui.today.attachPressScale

/** What the Drills card line shows. */
sealed interface DrillsCardLine {
    /** The drill type the line is about. */
    val typeId: String

    /** No round played yet: the standing offer for the suggested type. */
    data class Suggested(override val typeId: String) : DrillsCardLine

    /** The most recent round: its type and first-try result. */
    data class LastRound(
        override val typeId: String,
        val right: Int,
        val total: Int,
    ) : DrillsCardLine
}

/**
 * Context-free rules for the Today Drills card:
 * - Available whenever German is on, the same rule as the Words card.
 * - The suggested type is the one the hub offers as "Practise weakest", or the last type
 *   when no type has a score yet.
 * - The line is the last round when there is one, else the standing offer.
 */
object DrillsCardLogic {

    fun isAvailable(germanEnabled: Boolean): Boolean = germanEnabled

    /** The card's suggested type: the same one the hub shows as "Practise weakest". */
    fun suggestedType(
        types: List<DrillType>,
        scores: Map<String, DrillScores.Score>,
        lastTypeId: String?,
    ): DrillType? = DrillHubLogic.weakestType(types, scores, lastTypeId)

    /** The line to show: the last round when there is one, else the standing offer. */
    fun line(suggestedTypeId: String?, lastRound: DrillLastRound?): DrillsCardLine? = when {
        lastRound != null -> DrillsCardLine.LastRound(lastRound.typeId, lastRound.right, lastRound.total)
        suggestedTypeId != null -> DrillsCardLine.Suggested(suggestedTypeId)
        else -> null
    }

    /**
     * The card text for [line]. [suggestedTemplate] takes the type name and round size,
     * [lastRoundTemplate] takes the type name and the right-of-total counts. The offered
     * round size is the minimum ([DrillRoundPlanner.MIN_ROUND_SIZE]); a round draws 3-5.
     */
    fun lineText(
        suggestedTemplate: String,
        lastRoundTemplate: String,
        typeName: String,
        line: DrillsCardLine?,
        roundSize: Int = DrillRoundPlanner.MIN_ROUND_SIZE,
    ): String? = when (line) {
        is DrillsCardLine.Suggested -> String.format(suggestedTemplate, typeName, roundSize)
        is DrillsCardLine.LastRound -> String.format(lastRoundTemplate, typeName, line.right, line.total)
        null -> null
    }
}

/**
 * Full-width card: a standing offer to start a drill round. The line shows the suggested type
 * until a round has been played, then the last round's type and result. Tap starts the
 * suggested round. Per-view state lives in [DrillsCardView], stored on the root view.
 */
object DrillsCard : TodayCard {

    const val KEY = "drills"

    override val key: String = KEY
    override val titleRes: Int = R.string.drills
    override val emoji: String = "🎯"

    override fun isAvailable(context: Context): Boolean {
        return DrillsCardLogic.isAvailable(Prefs(context).germanEnabled)
    }

    override fun hasContent(context: Context): Boolean {
        val prefs = Prefs(context)
        return DrillsCardLogic.suggestedType(
            DrillTables.available(),
            DrillScores.decode(prefs.drillScores),
            prefs.drillLastType,
        ) != null
    }

    override fun createView(inflater: LayoutInflater, parent: ViewGroup, fragment: Fragment): View {
        val cardView = DrillsCardView(
            CardDrillsBinding.inflate(inflater, parent, false),
            onStart = { typeId ->
                val context = fragment.context?.applicationContext ?: return@DrillsCardView
                Prefs(context).drillLastType = typeId
                val navController = fragment.findNavController()
                // Ignore a second tap delivered before the first navigation lands.
                if (navController.currentDestination?.id == R.id.todayFragment) {
                    navController.navigate(
                        R.id.action_todayFragment_to_drillFragment,
                        Bundle().apply { putString("drillType", typeId) },
                    )
                }
            },
        )
        cardView.binding.root.tag = cardView
        cardView.binding.root.attachPressScale()
        cardView.render()
        return cardView.binding.root
    }

    override fun onResume(view: View) {
        (view.tag as? DrillsCardView)?.render()
    }
}

/** Holds the state of one Drills card view. */
internal class DrillsCardView(
    val binding: CardDrillsBinding,
    private val onStart: (String) -> Unit,
) {

    private var suggestedTypeId: String? = null

    init {
        binding.root.setOnClickListener {
            Haptics.tick(it)
            startSuggested()
        }
        binding.tvOpen.setOnClickListener {
            Haptics.tick(it)
            startSuggested()
        }
    }

    private fun startSuggested() {
        suggestedTypeId?.let(onStart)
    }

    fun render() {
        val context = binding.root.context ?: return
        val prefs = Prefs(context.applicationContext)
        val suggested = DrillsCardLogic.suggestedType(
            DrillTables.available(),
            DrillScores.decode(prefs.drillScores),
            prefs.drillLastType,
        )
        suggestedTypeId = suggested?.id
        val line = DrillsCardLogic.line(suggested?.id, DrillLastRound.decode(prefs.drillLastRound))
        val typeName = line?.let { l ->
            DrillType.fromId(l.typeId)?.let { context.getString(DrillTables.labelRes(it)) }
        }.orEmpty()
        binding.tvLine.text = DrillsCardLogic.lineText(
            suggestedTemplate = context.getString(R.string.drills_card_suggested),
            lastRoundTemplate = context.getString(R.string.drills_card_last_round),
            typeName = typeName,
            line = line,
        ).orEmpty()
    }
}
