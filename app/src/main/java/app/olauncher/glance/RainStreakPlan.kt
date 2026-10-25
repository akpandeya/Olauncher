package app.olauncher.glance

import app.olauncher.helper.WeatherLook
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.pow

// ============================================================
// Rain constants from docs/design/today-weather.html lines 921–935
// ============================================================

const val MAX_STREAKS: Int = 90
const val MAX_RIPPLES: Int = 10
const val RAIN_OPACITY: Float = 0.7f
const val RAIN_REF_AREA: Float = 360f * 200f
const val RAIN_TRAVEL_REF: Float = 350f
const val RAIN_EXTRA_FALL: Float = 80f

data class RainLayerSpec(
    val max: Int,
    val duration: Float,
    val jitter: Float,
    val maxDrift: Float,
    val width: Float,
    val length: Float,
    val opacity: Float,
    val opacityRank: Int,
)

val RAIN_LAYERS_FAR = RainLayerSpec(
    max = 50,
    duration = 2.3f,
    jitter = 0.3f,
    maxDrift = 110f,
    width = 1f,
    length = 12f,
    opacity = 0.40f,
    opacityRank = 1,
)

val RAIN_LAYERS_MID = RainLayerSpec(
    max = 28,
    duration = 1.35f,
    jitter = 0.08f,
    maxDrift = 135f,
    width = 1.5f,
    length = 22f,
    opacity = 0.70f,
    opacityRank = 2,
)

val RAIN_LAYERS_NEAR = RainLayerSpec(
    max = 13,
    duration = 0.72f,
    jitter = 0.07f,
    maxDrift = 175f,
    width = 2f,
    length = 40f,
    opacity = 0.90f,
    opacityRank = 3,
)

// ============================================================
// Deterministic 32-bit PRNG: mulberry32 (line 655 of mock)
// ============================================================

class Mulberry32(seed: Long) : () -> Double {
    constructor(seed: Int) : this(seed.toLong())

    private var a: Int = seed.toInt()

    fun next(): Double {
        a += 0x6D2B79F5
        var t = a
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0
    }

    override fun invoke(): Double = next()
}

fun mulberry32(seed: Long): Mulberry32 = Mulberry32(seed)
fun mulberry32(seed: Int): Mulberry32 = Mulberry32(seed.toLong())

private fun randRange(rng: Mulberry32, lo: Double, hi: Double): Double = lo + (hi - lo) * rng()
private fun clamp(v: Float, lo: Float, hi: Float): Float = v.coerceIn(lo, hi)
private fun clamp(v: Double, lo: Double, hi: Double): Double = v.coerceIn(lo, hi)

fun round2(v: Double): Float {
    val rounded = (java.lang.Math.round(v * 100.0) / 100.0).toFloat()
    return if (rounded == -0.0f) 0.0f else rounded
}

fun round2(v: Float): Float = round2(v.toDouble())

operator fun ClosedRange<Int>.contains(value: Float): Boolean =
    value >= start && value <= endInclusive

operator fun ClosedRange<Int>.contains(value: Double): Boolean =
    value >= start && value <= endInclusive

// ============================================================
// Result models
// ============================================================

data class RainStreak(
    val left: Float,
    val dur: Float,
    val delay: Float,
)

data class RainRipple(
    val left: Float,
    val bottom: Float,
    val dur: Float,
    val delay: Float,
) {
    val bottomInt: Int get() = bottom.toInt()
}

data class RainStreakLayer(
    val count: Int,
    val length: Float,
    val width: Float,
    val opacity: Float,
    val dx: Float,
    val tilt: Float,
    val streaks: List<RainStreak>,
    val opacityRank: Int = when {
        opacity >= 0.8f -> 3
        opacity >= 0.6f -> 2
        else -> 1
    },
) {
    val drift: Float get() = dx
    val height: Float get() = length
}

data class RainStreakLayers(
    val far: RainStreakLayer,
    val mid: RainStreakLayer,
    val near: RainStreakLayer,
) {
    operator fun get(name: String): RainStreakLayer? = when (name.lowercase()) {
        "far" -> far
        "mid" -> mid
        "near" -> near
        else -> null
    }

    fun asList(): List<RainStreakLayer> = listOf(far, mid, near)
}

data class RainWindLayer(
    val dx: Float,
    val tilt: Float,
)

data class RainWindResult(
    val far: RainWindLayer,
    val mid: RainWindLayer,
    val near: RainWindLayer,
) {
    operator fun get(name: String): RainWindLayer? = when (name.lowercase()) {
        "far" -> far
        "mid" -> mid
        "near" -> near
        else -> null
    }
}

// ============================================================
// rainWind: lines 986–995 of today-weather.html
// ============================================================

fun rainWind(look: WeatherLook?, windFraction: Float, travel: Float): RainWindResult {
    val p = look?.params
    val w = clamp(windFraction, 0f, 1f) * (p?.wind ?: 0f)

    fun layerWind(maxDrift: Float): RainWindLayer {
        val rawDx = -maxDrift.toDouble() * w
        val dx = round2(rawDx)
        val tilt = round2(
            if (travel > 0f) atan2(-rawDx, travel.toDouble()) * 180.0 / Math.PI else 0.0
        )
        return RainWindLayer(dx = dx, tilt = tilt)
    }

    return RainWindResult(
        far = layerWind(RAIN_LAYERS_FAR.maxDrift),
        mid = layerWind(RAIN_LAYERS_MID.maxDrift),
        near = layerWind(RAIN_LAYERS_NEAR.maxDrift),
    )
}

// ============================================================
// rainPlan: lines 951–981 of today-weather.html
// ============================================================

data class RainStreakPlan(
    val travel: Float,
    val fall: Float,
    val far: RainStreakLayer,
    val mid: RainStreakLayer,
    val near: RainStreakLayer,
    val ripples: List<RainRipple>,
    val streaks: Int,
) {
    val layers: RainStreakLayers get() = RainStreakLayers(far, mid, near)

    companion object {
        const val MAX_STREAKS: Int = app.olauncher.glance.MAX_STREAKS
        const val MAX_RIPPLES: Int = app.olauncher.glance.MAX_RIPPLES
        const val RAIN_OPACITY: Float = app.olauncher.glance.RAIN_OPACITY
        const val RAIN_REF_AREA: Float = app.olauncher.glance.RAIN_REF_AREA
        const val RAIN_TRAVEL_REF: Float = app.olauncher.glance.RAIN_TRAVEL_REF
        const val RAIN_EXTRA_FALL: Float = app.olauncher.glance.RAIN_EXTRA_FALL

        val EMPTY = RainStreakPlan(
            travel = 0f,
            fall = 0f,
            far = RainStreakLayer(
                count = 0,
                length = RAIN_LAYERS_FAR.length,
                width = RAIN_LAYERS_FAR.width,
                opacity = RAIN_LAYERS_FAR.opacity,
                dx = 0f,
                tilt = 0f,
                streaks = emptyList(),
                opacityRank = RAIN_LAYERS_FAR.opacityRank,
            ),
            mid = RainStreakLayer(
                count = 0,
                length = RAIN_LAYERS_MID.length,
                width = RAIN_LAYERS_MID.width,
                opacity = RAIN_LAYERS_MID.opacity,
                dx = 0f,
                tilt = 0f,
                streaks = emptyList(),
                opacityRank = RAIN_LAYERS_MID.opacityRank,
            ),
            near = RainStreakLayer(
                count = 0,
                length = RAIN_LAYERS_NEAR.length,
                width = RAIN_LAYERS_NEAR.width,
                opacity = RAIN_LAYERS_NEAR.opacity,
                dx = 0f,
                tilt = 0f,
                streaks = emptyList(),
                opacityRank = RAIN_LAYERS_NEAR.opacityRank,
            ),
            ripples = emptyList(),
            streaks = 0,
        )

        fun plan(look: WeatherLook?, width: Float, height: Float, seed: Long = 0L): RainStreakPlan {
            if (look == null || !look.isRain || width <= 0f || height <= 0f) {
                return EMPTY
            }
            val p = look.params
            val rng = Mulberry32(seed)
            val area = clamp((width * height) / RAIN_REF_AREA, 0.5f, 1f)
            val fall = java.lang.Math.round(height + RAIN_EXTRA_FALL).toFloat()
            val travel = fall + 60f
            val pace = (travel / RAIN_TRAVEL_REF) * p.fallSpeed.toDouble().pow(-0.55)

            val wantedFar = java.lang.Math.round(RAIN_LAYERS_FAR.max * p.density * area).toInt()
            val wantedMid = java.lang.Math.round(RAIN_LAYERS_MID.max * p.density * area).toInt()
            val wantedNear = java.lang.Math.round(RAIN_LAYERS_NEAR.max * p.density * area).toInt()

            val total = wantedFar + wantedMid + wantedNear
            val cut = if (total > MAX_STREAKS) MAX_STREAKS.toDouble() / total else 1.0

            val wind = rainWind(look, 1f, travel)

            fun buildStreaks(spec: RainLayerSpec, wantedCount: Int): List<RainStreak> {
                val n = floor(wantedCount * cut).toInt()
                val list = ArrayList<RainStreak>(n)
                for (i in 0 until n) {
                    val dur = (spec.duration + randRange(rng, -spec.jitter.toDouble(), spec.jitter.toDouble())) * pace
                    val left = round2(randRange(rng, -5.0, 115.0))
                    val durRounded = round2(dur)
                    val delay = round2(-randRange(rng, 0.0, dur))
                    list.add(RainStreak(left = left, dur = durRounded, delay = delay))
                }
                return list
            }

            val farStreaks = buildStreaks(RAIN_LAYERS_FAR, wantedFar)
            val midStreaks = buildStreaks(RAIN_LAYERS_MID, wantedMid)
            val nearStreaks = buildStreaks(RAIN_LAYERS_NEAR, wantedNear)

            val streaks = farStreaks.size + midStreaks.size + nearStreaks.size

            val nr = kotlin.math.min(MAX_RIPPLES, java.lang.Math.round(10f * p.density * area))
            val rdur = round2(1.2 * p.fallSpeed.toDouble().pow(-0.36))
            val ripples = ArrayList<RainRipple>(nr)
            for (i in 0 until nr) {
                val left = round2(randRange(rng, 5.0, 95.0))
                val bottom = java.lang.Math.round(randRange(rng, 2.0, 10.0)).toFloat()
                val delay = round2(-randRange(rng, 0.0, rdur.toDouble()))
                ripples.add(RainRipple(left = left, bottom = bottom, dur = rdur, delay = delay))
            }

            val farLayer = RainStreakLayer(
                count = farStreaks.size,
                length = RAIN_LAYERS_FAR.length,
                width = RAIN_LAYERS_FAR.width,
                opacity = RAIN_LAYERS_FAR.opacity,
                dx = wind.far.dx,
                tilt = wind.far.tilt,
                streaks = farStreaks,
                opacityRank = RAIN_LAYERS_FAR.opacityRank,
            )
            val midLayer = RainStreakLayer(
                count = midStreaks.size,
                length = RAIN_LAYERS_MID.length,
                width = RAIN_LAYERS_MID.width,
                opacity = RAIN_LAYERS_MID.opacity,
                dx = wind.mid.dx,
                tilt = wind.mid.tilt,
                streaks = midStreaks,
                opacityRank = RAIN_LAYERS_MID.opacityRank,
            )
            val nearLayer = RainStreakLayer(
                count = nearStreaks.size,
                length = RAIN_LAYERS_NEAR.length,
                width = RAIN_LAYERS_NEAR.width,
                opacity = RAIN_LAYERS_NEAR.opacity,
                dx = wind.near.dx,
                tilt = wind.near.tilt,
                streaks = nearStreaks,
                opacityRank = RAIN_LAYERS_NEAR.opacityRank,
            )

            return RainStreakPlan(
                travel = travel,
                fall = fall,
                far = farLayer,
                mid = midLayer,
                near = nearLayer,
                ripples = ripples,
                streaks = streaks,
            )
        }

        fun plan(look: WeatherLook?, width: Float, height: Float, seed: Int): RainStreakPlan =
            plan(look, width, height, seed.toLong())

        fun plan(look: WeatherLook?, width: Int, height: Int, seed: Long = 0L): RainStreakPlan =
            plan(look, width.toFloat(), height.toFloat(), seed)

        fun plan(look: WeatherLook?, width: Int, height: Int, seed: Int): RainStreakPlan =
            plan(look, width.toFloat(), height.toFloat(), seed.toLong())

        fun plan(look: WeatherLook?, width: Double, height: Double, seed: Long = 0L): RainStreakPlan =
            plan(look, width.toFloat(), height.toFloat(), seed)

        fun plan(look: WeatherLook?, width: Double, height: Double, seed: Int): RainStreakPlan =
            plan(look, width.toFloat(), height.toFloat(), seed.toLong())

        operator fun invoke(look: WeatherLook?, width: Float, height: Float, seed: Long = 0L): RainStreakPlan =
            plan(look, width, height, seed)

        operator fun invoke(look: WeatherLook?, width: Float, height: Float, seed: Int): RainStreakPlan =
            plan(look, width, height, seed.toLong())

        operator fun invoke(look: WeatherLook?, width: Int, height: Int, seed: Long = 0L): RainStreakPlan =
            plan(look, width.toFloat(), height.toFloat(), seed)

        operator fun invoke(look: WeatherLook?, width: Int, height: Int, seed: Int): RainStreakPlan =
            plan(look, width.toFloat(), height.toFloat(), seed.toLong())

        operator fun invoke(look: WeatherLook?, width: Double, height: Double, seed: Long = 0L): RainStreakPlan =
            plan(look, width.toFloat(), height.toFloat(), seed)

        operator fun invoke(look: WeatherLook?, width: Double, height: Double, seed: Int): RainStreakPlan =
            plan(look, width.toFloat(), height.toFloat(), seed.toLong())

        fun rainWind(look: WeatherLook?, windFraction: Float, travel: Float): RainWindResult =
            app.olauncher.glance.rainWind(look, windFraction, travel)
    }
}

fun rainPlan(look: WeatherLook?, width: Float, height: Float, seed: Long = 0L): RainStreakPlan =
    RainStreakPlan.plan(look, width, height, seed)

fun rainPlan(look: WeatherLook?, width: Float, height: Float, seed: Int): RainStreakPlan =
    RainStreakPlan.plan(look, width, height, seed.toLong())

fun rainPlan(look: WeatherLook?, width: Int, height: Int, seed: Long = 0L): RainStreakPlan =
    RainStreakPlan.plan(look, width.toFloat(), height.toFloat(), seed)

fun rainPlan(look: WeatherLook?, width: Int, height: Int, seed: Int): RainStreakPlan =
    RainStreakPlan.plan(look, width.toFloat(), height.toFloat(), seed.toLong())

object RainStreakPlanner {
    fun plan(look: WeatherLook?, width: Float, height: Float, seed: Long = 0L): RainStreakPlan =
        RainStreakPlan.plan(look, width, height, seed)

    fun plan(look: WeatherLook?, width: Float, height: Float, seed: Int): RainStreakPlan =
        RainStreakPlan.plan(look, width, height, seed.toLong())
}
