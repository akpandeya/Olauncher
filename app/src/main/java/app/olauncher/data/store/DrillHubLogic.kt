package app.olauncher.data.store

/**
 * One row of the drills hub: the type and its score, null when the type was never played.
 */
data class DrillHubRow(
    val type: DrillType,
    val score: DrillScores.Score?,
) {
    /** True when the type has no played round, so the row sits in the New group. */
    val neverPlayed: Boolean
        get() = score == null || score.asked <= 0
}

/**
 * What a hub row shows beside the type name.
 */
sealed interface DrillHubStat {
    /** Never played: no stat, the row sits in the New group. */
    object NeverPlayed : DrillHubStat

    /** About one round so far: no percentage yet, just "1 round". */
    object OneRound : DrillHubStat

    /** The smoothed percentage with the count of rounds played. */
    data class Percentage(val percent: Int, val rounds: Int) : DrillHubStat
}

/**
 * Context-free rules for the drills hub:
 * - Row order: the scored types weakest first (smoothed score), then the never-played types
 *   in table order under a group labelled "New".
 * - The stat a row shows: a percentage with the round count once the type has about two
 *   rounds, "1 round" before that, nothing when never played.
 * - The suggestion buttons: Continue is the last type played, Practise weakest is the
 *   lowest-ranked type that has a score, or the last type when nothing has a score yet.
 */
object DrillHubLogic {

    /**
     * The hub rows: scored types weakest first (ties keep the [types] order), then the
     * never-played types in [types] order.
     */
    fun rows(types: List<DrillType>, scores: Map<String, DrillScores.Score>): List<DrillHubRow> {
        val ranked = DrillScores.rankedWeakestFirst(types, scores)
        val rankedIds = ranked.map { it.id }.toSet()
        return ranked.map { type -> DrillHubRow(type, scores[type.id]) } +
            types.filterNot { it.id in rankedIds }.map { type -> DrillHubRow(type, scores[type.id]) }
    }

    /** The stat a hub row shows beside the type name. */
    fun stat(row: DrillHubRow): DrillHubStat {
        if (row.neverPlayed) return DrillHubStat.NeverPlayed
        val score = row.score!!
        val percentage = score.displayPercentage ?: return DrillHubStat.OneRound
        return DrillHubStat.Percentage(percentage, score.rounds)
    }

    /** The thin bar's fill fraction: the smoothed score, 0 for a never-played type. */
    fun barFraction(row: DrillHubRow): Float =
        if (row.neverPlayed) 0f else row.score!!.smoothedScore.toFloat().coerceIn(0f, 1f)

    /**
     * True when the "New" group label separates ranked rows from new rows: it is shown only
     * when both groups exist, not when every type is new or every type is ranked.
     */
    fun showsNewGroupLabel(rows: List<DrillHubRow>): Boolean =
        rows.any { !it.neverPlayed } && rows.any { it.neverPlayed }

    /**
     * The Continue button's type: the last type played when it is still available, else the
     * first available type. Null only when no type is available at all.
     */
    fun continueType(types: List<DrillType>, lastTypeId: String?): DrillType? {
        val last = lastTypeId?.let(DrillType::fromId)
        return types.firstOrNull { it == last } ?: types.firstOrNull()
    }

    /**
     * The Practise weakest button's type: the lowest-ranked type that has a score, or the
     * last type when no type has a score yet.
     */
    fun weakestType(
        types: List<DrillType>,
        scores: Map<String, DrillScores.Score>,
        lastTypeId: String?,
    ): DrillType? =
        DrillScores.rankedWeakestFirst(types, scores).firstOrNull() ?: continueType(types, lastTypeId)
}
