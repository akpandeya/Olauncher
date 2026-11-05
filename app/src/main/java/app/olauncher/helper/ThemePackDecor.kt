package app.olauncher.helper

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Pure logic for the seasonal home dressing (docs/design/themes/mocks/index.html, `decorHTML`):
 * the decorated families and the animation phase maths the [app.olauncher.ui.HomeDecorView]
 * draws with. Decoration is an app-side presentation choice derived from the pack family —
 * never pack data (docs/design/themes/PACK_FORMAT.md).
 */
object ThemePackDecor {

    const val FAMILY_HALLOWEEN = "halloween"
    const val FAMILY_DIWALI = "diwali"
    const val FAMILY_CHRISTMAS = "christmas"

    /**
     * The decorated family of a pack id, or null for a plain pack. The family is the id prefix
     * before the first `-` (`diwali-dark` -> `diwali`); anything else (Thumbline, Sage, Midnight,
     * Rose) draws nothing.
     */
    fun familyFor(packId: String?): String? {
        if (packId.isNullOrEmpty()) return null
        return when (val prefix = packId.substringBefore('-').lowercase()) {
            FAMILY_HALLOWEEN, FAMILY_DIWALI, FAMILY_CHRISTMAS -> prefix
            else -> null
        }
    }

    /**
     * Pack background scrim opacity: stored as a percent, 0 (wallpaper only) to
     * [BACKGROUND_OPACITY_MAX] (solid pack colour).
     */
    const val BACKGROUND_OPACITY_MAX = 100
    const val BACKGROUND_OPACITY_DEFAULT = BACKGROUND_OPACITY_MAX

    /** Keeps the stored percent inside [0]..[BACKGROUND_OPACITY_MAX]. */
    fun clampBackgroundOpacity(percent: Int): Int = percent.coerceIn(0, BACKGROUND_OPACITY_MAX)

    /** The stored percent as the 0..1 scrim alpha Home draws the pack background with. */
    fun backgroundAlphaFraction(percent: Int): Float = clampBackgroundOpacity(percent) / 100f

    /** Frames per second cap of the dressing loop; the motion is slow, so 30 reads as smooth. */
    const val MAX_FPS = 30
    const val FRAME_INTERVAL_MS = 1000L / MAX_FPS

    /**
     * Animation phase of [elapsedS] into a cycle of [periodS] seconds, shifted by [delayS]
     * (negative delays phase-shift forward, like CSS `animation-delay`), as a fraction
     * wrapped into 0 (inclusive) until 1 (exclusive). 0 when the period is not positive.
     */
    fun phase(elapsedS: Float, periodS: Float, delayS: Float = 0f): Float {
        if (periodS <= 0f) return 0f
        // Truncated remainder (sign of the dividend), folded up into 0 until periodS.
        var shifted = (elapsedS + delayS) % periodS
        if (shifted < 0f) shifted += periodS
        return shifted / periodS
    }

    /**
     * Twinkle opacity (`@keyframes twinkle`): 0.2 at the cycle ends, 1 at the midpoint.
     * [p] is a phase from [phase].
     */
    fun twinkleAlpha(p: Float): Float = 0.2f + 0.4f * (1f - cos(2.0 * PI * p).toFloat())

    /**
     * Diya flame height scale (`@keyframes flicker` vertical part): 0.85 at the cycle start,
     * 1.1 at the midpoint, pivoting on the flame base.
     */
    fun flickerScaleY(p: Float): Float = 0.975f - 0.125f * cos(2.0 * PI * p).toFloat()

    /** Diya flame opacity wobble (`@keyframes flicker` alpha part): 1 at the ends, 0.8 mid-cycle. */
    fun flickerAlpha(p: Float): Float = 1f - 0.1f * (1f - cos(2.0 * PI * p).toFloat())

    /**
     * Fraction of an ease-in-out cycle completed, 0 to 1: 0 at the ends, 1 at the midpoint.
     * The CSS keyframe motions (floaty, drift, flicker) are of this shape.
     */
    fun eased(p: Float): Float = 0.5f * (1f - cos(2.0 * PI * p).toFloat())

    /**
     * Vertical bob offset in dp (`@keyframes floaty`): 0 at the ends, [amplitudeDp] up mid-cycle.
     * [p] is a phase from [phase].
     */
    fun bobOffsetDp(p: Float, amplitudeDp: Float): Float = -amplitudeDp * eased(p)

    /**
     * Horizontal drift offset in dp (`@keyframes drift` x): 0 at the ends, [amplitudeDp] towards
     * the left mid-cycle.
     */
    fun driftOffsetXDp(p: Float, amplitudeDp: Float): Float = -amplitudeDp * eased(p)

    /** Vertical drift offset in dp (`@keyframes drift` y): 0 at the ends, [amplitudeDp] down mid-cycle. */
    fun driftOffsetYDp(p: Float, amplitudeDp: Float): Float = amplitudeDp * eased(p)

    /**
     * Fall progress of a snowflake, 0 (just above the top edge) to 1 (just past the bottom edge),
     * wrapping top to bottom. [periodS] is one full crossing; [delayS] spreads the flakes out.
     */
    fun snowProgress(elapsedS: Float, periodS: Float, delayS: Float): Float =
        phase(elapsedS, periodS, delayS)

    // ---- Diwali rockets (diwali-rockets-mock.html) ----

    /** Sky cap: at most this many rocket bursts live at once; extras are skipped, not queued. */
    const val MAX_LIVE_BOOMS = 5

    /** Auto salvo cadence: one roll this many milliseconds of animation time (mock: every 7s). */
    const val ROCKET_SALVO_PERIOD_MS = 7000L

    /**
     * Auto-tick salvo size from a 0..1 roll (mock: 55% one rocket, 30% two, 15% three).
     * Rolls outside [0, 1] clamp to the ends, so 0.54 -> 1, 0.56 -> 2, 0.84 -> 2, 0.86 -> 3.
     * In evening hours ([isEveningHour]) the bands shift toward bigger salvos
     * (25% one, 40% two, 35% three) so the show builds after dark.
     */
    fun salvoSizeFor(roll: Float, evening: Boolean = false): Int {
        val r = roll.coerceIn(0f, 1f)
        return when {
            r < (if (evening) 0.25f else 0.55f) -> 1
            r < (if (evening) 0.65f else 0.85f) -> 2
            else -> 3
        }
    }

    /** Evening show time: salvos skew bigger from 19:00 to 23:59 local. */
    fun isEveningHour(hourOfDay: Int): Boolean = hourOfDay in 19..23

    /** Celebration salvo size for the double-tap fireworks: 3 to 5 rockets. */
    fun celebrationSalvoSize(rng: Random = Random): Int = 3 + rng.nextInt(3)

    // ---- Diwali shockwave physics (diwali-physics-mock.html: rings, embers, springs) ----

    /**
     * Shockwave ring speed in dp/s: the front of a detonation ring expands this fast from
     * the boom point (mock: `rad = 40 + age * 620` px, mock px are dp).
     */
    const val SHOCKWAVE_SPEED_DP_S = 620f

    /** Shockwave ring life in animation-time ms: it fades out over this long (mock: 1.4s). */
    const val SHOCKWAVE_LIFE_MS = 1400L

    /** Radius (dp) a ring is spawned with, so it never starts at the boom centre dot. */
    const val SHOCKWAVE_SPAWN_RADIUS_DP = 40f

    /** Ring radius (dp) [ageMs] after detonation: linear growth from the spawn radius. */
    fun ringRadiusAt(ageMs: Long): Float =
        SHOCKWAVE_SPAWN_RADIUS_DP + ageMs / 1000f * SHOCKWAVE_SPEED_DP_S

    /** Ring stroke alpha at [ageMs]: 0.5 at birth fading linearly to 0 at [SHOCKWAVE_LIFE_MS]. */
    fun ringStrokeAlphaAt(ageMs: Long): Float =
        (0.5f * (1f - ageMs.toFloat() / SHOCKWAVE_LIFE_MS)).coerceAtLeast(0f)

    /** Ring stroke width (dp) at [ageMs]: 2.5dp at birth thinning to 1dp at end of life. */
    fun ringStrokeWidthAt(ageMs: Long): Float =
        2.5f - 1.5f * (ageMs.toFloat() / SHOCKWAVE_LIFE_MS).coerceIn(0f, 1f)

    /**
     * Whether a target at distance [d] from the blast was crossed this frame: the ring front
     * reached it now ([d] <= [radiusNow]) but had not yet on the previous frame
     * ([d] > [radiusPrev]). Exactly on the new front counts as a hit; exactly on the previous
     * front (or behind it, e.g. inside the spawn radius) does not.
     */
    fun hitBetween(d: Float, radiusNow: Float, radiusPrev: Float): Boolean =
        d <= radiusNow && d > radiusPrev

    /** Exponential flare decay per second: a flared diya glow falls back to idle (mock 3.2/s). */
    const val DIYA_FLARE_DECAY_PER_S = 3.2f

    /** Diya flare value [dtS] after being [flare] (1 = just hit by a ring front). */
    fun flareDecay(flare: Float, dtS: Float): Float = flare * exp(-DIYA_FLARE_DECAY_PER_S * dtS)

    /**
     * Diya glow alpha with [flare] applied on top of the [base] idle alpha: rises up to
     * base + 0.5 at full flare (mock: 0.22 idle to 0.72 peaked).
     */
    fun flareGlowAlpha(base: Float, flare: Float): Float = (base + flare * 0.5f).coerceIn(0f, 1f)

    /** Diya glow radius scale with [flare] on top of the [idle] flicker scale (mock: 1 + 1.1 flare). */
    fun flareGlowScale(idle: Float, flare: Float): Float = idle * (1f + flare * 1.1f)

    /** Damped-spring stiffness that pulls scattered rangoli dots home (mock: 120). */
    const val RANGOLI_SPRING_STIFFNESS = 120f

    /** Exponential damping per second of the rangoli scatter spring (mock: 6/s). */
    const val RANGOLI_SPRING_DAMPING_PER_S = 6f

    /** Hard cap (dp) on a rangoli dot's scatter offset from home — a belt-and-braces guard. */
    const val RANGOLI_SCATTER_MAX_DP = 24f

    /** Position (dp offset from home) and velocity of one scattered rangoli dot. */
    data class ScatterState(
        val offsetX: Float,
        val offsetY: Float,
        val vx: Float,
        val vy: Float,
    )

    /**
     * One frame-rate-independent step of the rangoli scatter spring: acceleration
     * -stiffness * offset, exponential damping exp(-6 dt), then integration. Formulated this
     * exact way (not a per-frame semi-implicit variant) because it stays stable at any frame
     * rate; the mock's per-frame form oscillated. The returned offset is clamped to
     * [RANGOLI_SCATTER_MAX_DP] from home as a guard against runaway state.
     */
    fun scatterSpringStep(offsetX: Float, offsetY: Float, vx: Float, vy: Float, dtS: Float): ScatterState {
        var nvx = vx - RANGOLI_SPRING_STIFFNESS * offsetX * dtS
        var nvy = vy - RANGOLI_SPRING_STIFFNESS * offsetY * dtS
        val damp = exp(-RANGOLI_SPRING_DAMPING_PER_S * dtS)
        nvx *= damp
        nvy *= damp
        var nox = offsetX + nvx * dtS
        var noy = offsetY + nvy * dtS
        val dist = sqrt(nox * nox + noy * noy)
        if (dist > RANGOLI_SCATTER_MAX_DP) {
            val scale = RANGOLI_SCATTER_MAX_DP / dist
            nox *= scale
            noy *= scale
        }
        return ScatterState(nox, noy, nvx, nvy)
    }

    /** Clock wobble spring stiffness in 1/s² (mock stepClock: k = 90). */
    const val CLOCK_SPRING_STIFFNESS = 90f

    /** Clock wobble spring damping in 1/s (mock stepClock: c = 6). */
    const val CLOCK_SPRING_DAMPING = 6f

    /** Hard clamp (degrees) on the clock's wobble rotation — it must never tip further. */
    const val CLOCK_WOBBLE_MAX_DEG = 6f

    /** Rotation and angular velocity of the wobbling home clock, in degrees. */
    data class ClockSpringState(
        val angleDeg: Float,
        val angularVelDegS: Float,
    )

    /**
     * One step of the clock wobble spring (mock stepClock): angular acceleration
     * -k * angle - c * angular velocity, then integration, with the angle hard-clamped to
     * ±[CLOCK_WOBBLE_MAX_DEG].
     */
    fun clockSpringStep(angleDeg: Float, angularVelDegS: Float, dtS: Float): ClockSpringState {
        val nv = angularVelDegS +
            (-CLOCK_SPRING_STIFFNESS * angleDeg - CLOCK_SPRING_DAMPING * angularVelDegS) * dtS
        val na = (angleDeg + nv * dtS).coerceIn(-CLOCK_WOBBLE_MAX_DEG, CLOCK_WOBBLE_MAX_DEG)
        return ClockSpringState(na, nv)
    }

    /**
     * Clock kick from a shockwave front crossing it, in deg/s: ±70..120, the sign from which
     * side of the clock the blast is on (mock: clock left of the blast kicks negative).
     */
    fun clockKickDegS(clockXDp: Float, blastXDp: Float, roll: Float): Float =
        (if (clockXDp < blastXDp) -1f else 1f) * (70f + roll.coerceIn(0f, 1f) * 50f)

    /** Gravity (dp/s²) pulling embers down (mock: 300). */
    const val EMBER_GRAVITY_DP_S2 = 300f

    /** Fraction of speed an ember keeps bouncing off the light strip (mock: 0.45). */
    const val EMBER_RESTITUTION = 0.45f

    /** Fraction of horizontal speed an ember keeps on a bounce (mock: 0.8). */
    const val EMBER_FLOOR_VX_DAMPING = 0.8f

    /** Bounces slower than this (dp/s) settle: the ember stops bouncing and rolls (mock: 20). */
    const val EMBER_SETTLE_SPEED_DP_S = 20f

    /** Position and velocity of one ember, in dp and dp/s. */
    data class EmberState(
        val x: Float,
        val y: Float,
        val vx: Float,
        val vy: Float,
    )

    /**
     * One step of ember physics: gravity, integration, then a bounce off the light strip top
     * ([floorY]) with restitution, horizontal damping, and the settle threshold — a bounce
     * slower than [EMBER_SETTLE_SPEED_DP_S] parks the ember on the floor to roll out its life.
     */
    fun emberStep(x: Float, y: Float, vx: Float, vy: Float, dtS: Float, floorY: Float): EmberState {
        val nvy = vy + EMBER_GRAVITY_DP_S2 * dtS
        val nx = x + vx * dtS
        val ny = y + nvy * dtS
        if (ny > floorY) {
            val bounced = -abs(nvy) * EMBER_RESTITUTION
            val settled = if (abs(bounced) < EMBER_SETTLE_SPEED_DP_S) 0f else bounced
            return EmberState(nx, floorY, vx * EMBER_FLOOR_VX_DAMPING, settled)
        }
        return EmberState(nx, ny, vx, nvy)
    }

    /** Ember twinkle frequency in Hz (mock: sin oscillation at ~9 Hz, phase-randomised). */
    const val EMBER_TWINKLE_HZ = 9f

    /**
     * Draw alpha of an ember [lifeS] into a [ttlS] life: fades out over its life, twinkling
     * between 0.2 and 1 of that at [EMBER_TWINKLE_HZ] with the ember's [phase] (radians).
     */
    fun emberAlpha(lifeS: Float, ttlS: Float, timeS: Float, phase: Float): Float {
        if (ttlS <= 0f) return 0f
        val fade = (1f - lifeS / ttlS).coerceIn(0f, 1f)
        val twinkle = 0.6f + 0.4f * sin((timeS * EMBER_TWINKLE_HZ + phase) * (2.0 * PI).toFloat())
        return (fade * twinkle).coerceIn(0f, 1f)
    }

    /** Embers spawned per detonation from a 0..1 roll: 12 to 18 (mock: 12 + floor(rnd*6)). */
    fun emberCountFor(roll: Float): Int = 12 + (roll.coerceIn(0f, 1f) * 7f).toInt().coerceIn(0, 6)

    /** Total live embers the sky holds; the oldest are removed when a burst exceeds this. */
    const val EMBER_CAP = 90
}
