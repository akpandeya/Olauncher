package app.olauncher.data.store

import app.olauncher.data.Prefs
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Tracks accuracy per drill type.
 * Stored in [Prefs.DRILL_SCORES] as a JSON object mapping type id to
 * `{asked, firstTryRight, askedDecayed, rightDecayed, rounds}`.
 *
 * `asked` and `firstTryRight` are the cumulative raw counts kept since the first release.
 * `askedDecayed` and `rightDecayed` are the ranking counts: the old counts are multiplied by
 * [DECAY] each round, so the decayed total tops out at 50 items and recent rounds carry the
 * weight. The ranking score is `(rightDecayed + 3) / (askedDecayed + 4)`: the +3/+4 pull a
 * new type toward 75%, so one lucky round does not top the list.
 */
object DrillScores {

    /** How much of the decayed counts survives each round. */
    const val DECAY = 0.8

    /** Smoothing prior that pulls a new type toward 75%. */
    const val RIGHT_PRIOR = 3.0
    const val ASKED_PRIOR = 4.0

    /** A percentage is shown only once a type has about this many rounds; before that, "1 round". */
    const val ROUNDS_BEFORE_PERCENTAGE = 2

    /** An abandoned round is recorded only when at least this many answers were given. */
    const val MIN_ABANDONED_ANSWERS = 3

    data class Score(
        val asked: Int,
        val firstTryRight: Int,
        /** Decayed ranking counts; seeded from the raw counts when the keys are absent. */
        val askedDecayed: Double = asked.toDouble(),
        val rightDecayed: Double = firstTryRight.toDouble(),
        /** Rounds played, complete or abandoned. */
        val rounds: Int = 0,
    ) {
        /** Raw first-try percentage over every round ever played. */
        val percentage: Int
            get() = if (asked > 0) ((firstTryRight * 100.0) / asked).roundToInt() else 0

        /** Smoothed ranking score over decayed counts: (right + 3) / (asked + 4). */
        val smoothedScore: Double
            get() = (rightDecayed + RIGHT_PRIOR) / (askedDecayed + ASKED_PRIOR)

        /** The smoothed score as a percentage for display. */
        val smoothedPercentage: Int
            get() = (smoothedScore * 100.0).roundToInt()

        /**
         * Percentage to show, or null before about [ROUNDS_BEFORE_PERCENTAGE] rounds;
         * "1 round" is shown instead then.
         */
        val displayPercentage: Int?
            get() = if (rounds >= ROUNDS_BEFORE_PERCENTAGE) smoothedPercentage else null
    }

    /** Evidence one round adds to the decayed counts. */
    data class RoundEvidence(val askedDelta: Double, val rightDelta: Double)

    /**
     * Evidence of one round for the decayed counts.
     *
     * The typed answer is the main evidence; a choice pick can be a guess. For a type with a
     * typed round each item's evidence weighs 1, split 1/3 choice + 2/3 typed, so a full round
     * of 10 items adds exactly 10 to the decayed asked count. Choice-only types weigh each
     * choice answer 1. Answers not given, as in an abandoned round, add nothing.
     */
    fun roundEvidence(
        hasTypedRound: Boolean,
        choiceAnswered: Int,
        choiceRight: Int,
        typedAnswered: Int = 0,
        typedRight: Int = 0,
    ): RoundEvidence {
        if (!hasTypedRound) {
            return RoundEvidence(choiceAnswered.toDouble(), choiceRight.toDouble())
        }
        val choiceWeight = 1.0 / 3.0
        val typedWeight = 2.0 / 3.0
        return RoundEvidence(
            askedDelta = choiceAnswered * choiceWeight + typedAnswered * typedWeight,
            rightDelta = choiceRight * choiceWeight + typedRight * typedWeight,
        )
    }

    /**
     * Evidence of an abandoned round, or null when fewer than [MIN_ABANDONED_ANSWERS] answers
     * were given. Items not reached are not counted.
     */
    fun abandonedRoundEvidence(
        hasTypedRound: Boolean,
        choiceAnswered: Int,
        choiceRight: Int,
        typedAnswered: Int = 0,
        typedRight: Int = 0,
    ): RoundEvidence? =
        if (choiceAnswered + typedAnswered < MIN_ABANDONED_ANSWERS) null
        else roundEvidence(hasTypedRound, choiceAnswered, choiceRight, typedAnswered, typedRight)

    /**
     * Adds one round to the score for [typeId]. The decayed counts are multiplied by [DECAY]
     * and the round evidence is added; the raw [asked] and [firstTryRight] keys keep their
     * cumulative meaning. A round with no asked items changes nothing.
     */
    fun recordRound(
        existing: Map<String, Score>,
        typeId: String,
        asked: Int,
        firstTryRight: Int,
        askedEvidence: Double = asked.toDouble(),
        rightEvidence: Double = firstTryRight.toDouble(),
    ): Map<String, Score> {
        if (asked <= 0) return existing
        val current = existing[typeId] ?: Score(0, 0)
        val updated = current.copy(
            asked = current.asked + asked,
            firstTryRight = current.firstTryRight + firstTryRight,
            askedDecayed = current.askedDecayed * DECAY + askedEvidence,
            rightDecayed = current.rightDecayed * DECAY + rightEvidence,
            rounds = current.rounds + 1,
        )
        return existing + (typeId to updated)
    }

    fun recordRound(
        prefs: Prefs,
        typeId: String,
        asked: Int,
        firstTryRight: Int,
        askedEvidence: Double = asked.toDouble(),
        rightEvidence: Double = firstTryRight.toDouble(),
    ) {
        val current = decode(prefs.drillScores)
        val updated = recordRound(current, typeId, asked, firstTryRight, askedEvidence, rightEvidence)
        prefs.drillScores = encode(updated)
    }

    fun recordRound(
        prefs: Prefs,
        type: DrillType,
        asked: Int,
        firstTryRight: Int,
    ) {
        recordRound(prefs, type.id, asked, firstTryRight)
    }

    /**
     * The scored types of [types] ranked weakest first: lowest smoothed score first, ties keep
     * the [types] order. Types without a played round are not ranked.
     */
    fun rankedWeakestFirst(types: List<DrillType>, scores: Map<String, Score>): List<DrillType> =
        types.mapNotNull { type -> scores[type.id]?.takeIf { it.asked > 0 }?.let { type to it } }
            .sortedBy { it.second.smoothedScore }
            .map { it.first }

    /**
     * Decodes the JSON stored in [raw].
     * Returns an empty map on blank input or on any malformed JSON.
     */
    fun decode(raw: String?): Map<String, Score> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val result = mutableMapOf<String, Score>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val scoreObj = obj.optJSONObject(key) ?: continue
                if (!scoreObj.has("asked") || !scoreObj.has("firstTryRight")) continue
                val asked = scoreObj.optInt("asked", -1)
                val firstTryRight = scoreObj.optInt("firstTryRight", -1)
                if (asked >= 0 && firstTryRight >= 0) {
                    // The new keys are optional, so old data decodes unchanged; a type with
                    // only the old keys seeds its decayed counts from them.
                    val askedDecayed = scoreObj.optDouble("askedDecayed")
                        .takeIf { !it.isNaN() } ?: asked.toDouble()
                    val rightDecayed = scoreObj.optDouble("rightDecayed")
                        .takeIf { !it.isNaN() } ?: firstTryRight.toDouble()
                    val rounds = if (scoreObj.has("rounds")) {
                        scoreObj.optInt("rounds").coerceAtLeast(0)
                    } else {
                        estimatedRounds(asked)
                    }
                    result[key] = Score(asked, firstTryRight, askedDecayed, rightDecayed, rounds)
                }
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /** Rounds a type with only the old keys has probably played: raw asked over the round size. */
    private fun estimatedRounds(asked: Int): Int =
        if (asked > 0) (asked + DRILL_ROUND_SIZE - 1) / DRILL_ROUND_SIZE else 0

    /** Encodes [scores] to JSON. */
    fun encode(scores: Map<String, Score>): String {
        val obj = JSONObject()
        for ((key, score) in scores) {
            val scoreObj = JSONObject()
                .put("asked", score.asked)
                .put("firstTryRight", score.firstTryRight)
                .put("askedDecayed", score.askedDecayed)
                .put("rightDecayed", score.rightDecayed)
                .put("rounds", score.rounds)
            obj.put(key, scoreObj)
        }
        return obj.toString()
    }
}
