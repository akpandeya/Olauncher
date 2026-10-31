package app.olauncher.ui.words

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.data.store.DrillHubLogic
import app.olauncher.data.store.DrillHubStat
import app.olauncher.data.store.DrillScores
import app.olauncher.data.store.DrillTables
import app.olauncher.data.store.DrillType
import app.olauncher.databinding.FragmentDrillHubBinding
import app.olauncher.databinding.ItemDrillHubNewLabelBinding
import app.olauncher.databinding.ItemDrillHubRowBinding
import app.olauncher.helper.Haptics
import app.olauncher.ui.BaseFragment

/**
 * Full-screen drills hub: the 9 drill types weakest first, with Continue and Practise weakest
 * suggestion buttons on top. A row tap starts a 5-8 item round of that type; Practise weakest
 * starts the mixed round of the 2-3 weakest types. The popup picker it replaces lived in
 * WordsFragment.
 */
class DrillHubFragment : BaseFragment() {

    private var _binding: FragmentDrillHubBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDrillHubBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = view.context.applicationContext
        val prefs = Prefs(context)
        val types = DrillTables.available()
        val scores = DrillScores.decode(prefs.drillScores)
        val lastTypeId = prefs.drillLastType

        val continueType = DrillHubLogic.continueType(types, lastTypeId)
        val weakestMix = DrillHubLogic.weakestMix(types, scores, lastTypeId)
        // DrillTables.available() is a fixed list, so this only guards a future empty table.
        binding.btnContinue.isVisible = continueType != null
        binding.btnWeakest.isVisible = weakestMix.isNotEmpty()
        binding.btnContinue.text = continueType?.let {
            getString(R.string.drill_hub_continue, getString(DrillTables.labelRes(it)))
        }
        binding.btnContinue.setOnClickListener { continueType?.let(::startRound) }

        binding.btnWeakest.text = weakestMix.joinToString(" · ") { getString(DrillTables.labelRes(it)) }
            .let { getString(R.string.drill_hub_practise_weakest, it) }
        binding.btnWeakest.setOnClickListener {
            if (weakestMix.isNotEmpty()) startMixedRound(weakestMix)
        }

        renderRows(types, scores)
    }

    /**
     * Binds the hub rows in one panel: ranked rows weakest first, a "New" group label when
     * both groups exist, then the never-played types in table order.
     */
    private fun renderRows(
        types: List<DrillType>,
        scores: Map<String, DrillScores.Score>,
    ) {
        val b = binding
        b.hubRowsPanel.removeAllViews()
        val rows = DrillHubLogic.rows(types, scores)
        val showNewLabel = DrillHubLogic.showsNewGroupLabel(rows)
        var newLabelShown = false

        for (row in rows) {
            if (showNewLabel && row.neverPlayed && !newLabelShown) {
                newLabelShown = true
                b.hubRowsPanel.addView(
                    ItemDrillHubNewLabelBinding.inflate(layoutInflater, b.hubRowsPanel, false).root
                )
            }
            val item = ItemDrillHubRowBinding.inflate(layoutInflater, b.hubRowsPanel, false)
            item.tvName.text = getString(DrillTables.labelRes(row.type))
            when (val stat = DrillHubLogic.stat(row)) {
                is DrillHubStat.Percentage ->
                    item.tvStat.text = getString(R.string.drill_hub_stat, stat.percent, stat.rounds)
                DrillHubStat.OneRound -> item.tvStat.text = getString(R.string.drill_hub_one_round)
                DrillHubStat.NeverPlayed -> item.tvStat.isVisible = false
            }
            item.scoreBar.setScoreFraction(DrillHubLogic.barFraction(row))
            item.root.setOnClickListener { startRound(row.type) }
            b.hubRowsPanel.addView(item.root)
        }
    }

    /** A tap starts a round of the type and makes it the new last type. */
    private fun startRound(type: DrillType) {
        val b = _binding ?: return
        Haptics.tick(b.root)
        Prefs(b.root.context.applicationContext).drillLastType = type.id
        navigateToRound(listOf(type))
    }

    /** Starts a mixed round over the 2-3 weakest types. */
    private fun startMixedRound(types: List<DrillType>) {
        val b = _binding ?: return
        Haptics.tick(b.root)
        navigateToRound(types)
    }

    private fun navigateToRound(types: List<DrillType>) {
        val navController = findNavController()
        // Ignore a second tap delivered before the first navigation lands.
        if (navController.currentDestination?.id == R.id.drillHubFragment) {
            navController.navigate(
                R.id.action_drillHubFragment_to_drillFragment,
                Bundle().apply { putString("drillType", types.joinToString(",") { it.id }) },
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
