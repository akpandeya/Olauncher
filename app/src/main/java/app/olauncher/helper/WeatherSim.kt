package app.olauncher.helper

/** 2D point used in bolt paths. Context-free. */
data class WeatherSimPoint(val x: Float, val y: Float)

/** Keep-out rectangle for UI elements on the glass. Context-free. */
data class WeatherSimRect(val x: Float, val y: Float, val w: Float, val h: Float)
typealias KeepOutRect = WeatherSimRect

/** Active wind gust front. Context-free. */
data class Gust(var x: Float, val sigma: Float, val speed: Float, val strength: Float)

/**
 * A water drop resting or sliding on the glass. Context-free.
 * Note: no trail state or trail list exists on drops.
 */
data class RainDrop(
    var x: Float,
    var y: Float,
    var r: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var tilt: Float = 0f,
    var stall: Float = 0f,
    var phase: Float = 0f,
    val wobbleAmp: Float = 0f,
    val wobbleFreq: Float = 0f,
    var gx: Float = x,
    var gy: Float = y,
    var sideways: Float = 0f,
    var dead: Boolean = false,
)

/** A soft wet spot left behind by a sliding drop. Context-free. */
data class SheenSpot(
    val x: Float,
    val y: Float,
    val r: Float,
    var age: Float = 0f,
)

/** A snowflake falling or settling on the glass. Context-free. */
data class SimFlake(
    var x: Float,
    var y: Float,
    var r: Float,
    var depth: Float,
    var vy: Float,
    var phase: Float,
    var swayAmp: Float,
    var swayFreq: Float,
    var settleY: Float,
    var settled: Boolean = false,
    var rest: Float = 0f,
    var melt: Float = 0f,
)

/** A single puff within a cloud. Context-free. */
data class CloudPuff(val dx: Float, val dy: Float, val r: Float)

/** A drifting cloud formed of several puffs. Context-free. */
data class SimCloud(
    var x: Float,
    var y: Float,
    val size: Float,
    val layer: Int,
    val puffs: List<CloudPuff>,
    val speed: Float,
)

/** A soft fog/haze patch. Context-free. */
data class SimHaze(
    var x: Float,
    var y: Float,
    val rx: Float,
    val ry: Float,
    val speed: Float,
    val alpha: Float,
)

/** A twinkling night star. Context-free. */
data class SimStar(
    val x: Float,
    val y: Float,
    val r: Float,
    var phase: Float,
    val freq: Float,
)

/** A small bead of water flung out from a rain drop impact. Context-free. */
data class ImpactBead(
    val dx: Float,
    val dy: Float,
    val r: Float,
    val life: Float,
)

/** A rain drop impact on the glass with growing ring and beads. Context-free. */
data class SimImpact(
    val x: Float,
    val y: Float,
    var age: Float = 0f,
    val ringR: Float,
    val beads: List<ImpactBead>,
    val life: Float,
) {
    fun ring(): RingState? = ringAt(this)
    fun bead(b: ImpactBead): BeadState? = beadAt(this, b)
}

/** Evaluated state of an impact ring at a given age. Context-free. */
data class RingState(val r: Float, val strength: Float)

/** Evaluated position and appearance of an impact bead at a given age. Context-free. */
data class BeadState(val x: Float, val y: Float, val r: Float, val strength: Float)

/** Evaluates the ring state of an impact. Null if ring has faded out. */
fun ringAt(im: SimImpact): RingState? {
    val t = im.age / WeatherSim.RING_SECONDS
    if (t >= 1f) return null
    val grow = 1f - (1f - t) * (1f - t)
    return RingState(r = im.ringR * (0.2f + 0.8f * grow), strength = 1f - t)
}

/** Evaluates the bead position and strength. Null if bead has faded out. */
fun beadAt(im: SimImpact, b: ImpactBead): BeadState? {
    if (im.age >= b.life) return null
    val k = 1f - Math.exp((-WeatherSim.BEAD_SLIDE_RATE * im.age).toDouble()).toFloat()
    val fadeIn = Math.min(1f, im.age / WeatherSim.BEAD_FADE_IN)
    val left = 1f - im.age / b.life
    val fadeOut = Math.min(1f, left / WeatherSim.BEAD_FADE_OUT)
    return BeadState(x = im.x + b.dx * k, y = im.y + b.dy * k, r = b.r, strength = fadeIn * fadeOut)
}

/** Exact port of mulberry32 PRNG. Context-free. */
class Mulberry32(seed: Long) : () -> Float {
    private var a: Int = seed.toInt()

    override fun invoke(): Float = nextFloat()

    fun nextFloat(): Float = nextDouble().toFloat()

    fun nextDouble(): Double {
        a += 0x6D2B79F5.toInt()
        var t = a
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + ((t xor (t ushr 7)) * (t or 61)))
        val u = (t xor (t ushr 14)).toLong() and 0xFFFFFFFFL
        return u.toDouble() / 4294967296.0
    }
}

/**
 * Pure Kotlin weather simulation. Context-free.
 * Ported from docs/design/home-weather-mock.html (<sim> and <sim-impacts>).
 */
class WeatherSim(
    val look: WeatherLook,
    val width: Float,
    val height: Float,
    val seed: Long,
    val params: WeatherLookParams = look.params,
    val keepOut: List<WeatherSimRect> = emptyList(),
) {
    companion object {
        const val STEP: Float = 1f / 30f
        const val TAU: Float = (Math.PI * 2.0).toFloat()
        const val MAX_RADIUS: Float = 15f

        const val DROPS_PER_GLASS_UNIT: Float = 2.5f
        const val MAX_DRIFT: Float = 70f
        const val MAX_WOBBLE: Float = 4.5f * 5f // 22.5
        const val MAX_TILT: Float = 0.6f
        const val STALL_BREAK_WIND: Float = 0.7f

        const val GLINT_SECONDS: Float = 0.2f
        const val GLINT_SPACING: Float = 6f
        const val MAX_GLINTS: Int = 240

        val GUST_GAP: FloatArray = floatArrayOf(3f, 9f)
        val GUST_SPEED: FloatArray = floatArrayOf(240f, 320f)
        val GUST_SIGMA: FloatArray = floatArrayOf(60f, 100f)
        val GUST_STRENGTH: FloatArray = floatArrayOf(0.25f, 0.5f)

        const val FLAKES_AT_FULL_DENSITY: Int = 140
        const val FLAKE_FALL_PX: Float = 60f
        const val MAX_FLAKE_DRIFT: Float = 45f
        const val SETTLE_SHARE: Float = 0.35f
        val SETTLE_HEIGHT: FloatArray = floatArrayOf(0.3f, 1.0f)
        val SETTLE_REST: FloatArray = floatArrayOf(3f, 6f)
        const val MELT_SECONDS: Float = 1f
        const val MAX_SETTLED: Int = 40

        val BOLT_GAP: FloatArray = floatArrayOf(4f, 12f)
        const val FLASH_SECONDS: Float = 0.6f
        const val DOUBLE_FLASH_CHANCE: Float = 0.35f
        const val DOUBLE_FLASH_DELAY: Float = 0.18f

        val CLOUD_BASE_SPEED: FloatArray = floatArrayOf(6f, 12f)
        const val CLOUD_WIND_SPEED: Float = 30f
        const val SUN_TURN: Float = 0.08f

        const val IMPACT_REF_AREA: Float = 390f * 844f
        const val MAX_IMPACTS: Int = 7
        const val RING_SECONDS: Float = 0.5f
        val RING_RADIUS: FloatArray = floatArrayOf(5f, 11f)
        const val SPLASH_SECONDS: Float = 0.1f
        const val BEADS_PER_IMPACT_MIN: Int = 2
        const val BEADS_PER_IMPACT_MAX: Int = 4
        val BEAD_RADIUS: FloatArray = floatArrayOf(0.8f, 2.0f)
        val BEAD_LIFE: FloatArray = floatArrayOf(1.0f, 1.8f)
        const val BEAD_SPREAD: Float = 4f
        const val BEAD_SLIDE_WIND: Float = 60f
        val BEAD_SAG: FloatArray = floatArrayOf(1f, 6f)
        const val BEAD_SLIDE_RATE: Float = 2.6f
        const val BEAD_FADE_IN: Float = 0.06f
        const val BEAD_FADE_OUT: Float = 0.4f
        const val KEEP_OUT_SKIP: Float = 0.8f
        const val KEEP_OUT_TRIES: Int = 3
        const val STAY_CHANCE: Float = 0.2f

        fun isImpactLook(look: WeatherLook): Boolean =
            look == WeatherLook.LIGHT_RAIN || look == WeatherLook.HEAVY_RAIN || look == WeatherLook.STORM

        fun randRange(rng: Mulberry32, lo: Float, hi: Float): Float = lo + (hi - lo) * rng.nextFloat()
        fun clamp(v: Float, lo: Float, hi: Float): Float = Math.max(lo, Math.min(hi, v))
        fun wrap(x: Float, lo: Float, hi: Float): Float {
            val span = hi - lo
            return if (x < lo) x + span else if (x >= hi) x - span else x
        }

        fun createForForecast(
            code: Int,
            isDay: Boolean,
            width: Float,
            height: Float,
            seed: Long,
            keepOut: List<WeatherSimRect> = emptyList(),
        ): WeatherSim? {
            val look = WeatherLooks.forForecast(code, isDay) ?: return null
            val params = WeatherLooks.paramsFor(code, isDay) ?: look.params
            return WeatherSim(look, width, height, seed, params, keepOut)
        }
    }

    val rng: Mulberry32 = Mulberry32(seed)
    var time: Float = 0f

    // Wind is forecast-only: windInput is fixed at 1.0
    val windInput: Float = 1f
    var wind: Float = params.wind
    var gust: Gust? = null
    var gustTimer: Float = 0f

    val drops: MutableList<RainDrop> = mutableListOf()
    val glints: MutableList<SheenSpot> = mutableListOf()
    val sheenSpots: List<SheenSpot> get() = glints
    var spawnDebt: Float = 0f

    val flakes: MutableList<SimFlake> = mutableListOf()
    val clouds: MutableList<SimCloud> = mutableListOf()
    val haze: MutableList<SimHaze> = mutableListOf()
    val stars: MutableList<SimStar> = mutableListOf()

    var sunPhase: Float = 0f

    var flash: Float = 0f
    var flashAge: Float = -1f
    var flashDouble: Boolean = false
    var boltPath: List<WeatherSimPoint>? = null
    var boltTimer: Float = 0f

    val impacts: MutableList<SimImpact> = mutableListOf()
    var impactDebt: Float = 0f
    val impactRate: Float = if (isImpactLook(look)) {
        val rate = when (look) {
            WeatherLook.LIGHT_RAIN -> 1.4f
            WeatherLook.HEAVY_RAIN -> 2.6f
            WeatherLook.STORM -> 3.0f
            else -> 0f
        }
        rate * clamp((width * height) / IMPACT_REF_AREA, 0.5f, 1f)
    } else {
        0f
    }

    init {
        gustTimer = randRange(rng, 1f, 4f)
        boltTimer = randRange(rng, 1.5f, 5f)
        initClouds()
        initHaze()
        initStars()
        initSnow()
    }

    // ---------- wind and gusts ----------
    fun updateWind(dt: Float) {
        val g = gust
        if (g != null) {
            g.x += g.speed * dt
            if (g.x - 3f * g.sigma > width) gust = null
            return
        }
        val gustLevel = windInput * params.gustiness
        if (gustLevel < 0.05f) return
        gustTimer -= dt
        if (gustTimer > 0f) return
        gustTimer = randRange(rng, GUST_GAP[0], GUST_GAP[1]) / (0.5f + gustLevel)
        val sigma = randRange(rng, GUST_SIGMA[0], GUST_SIGMA[1])
        gust = Gust(
            x = -3f * sigma,
            sigma = sigma,
            speed = randRange(rng, GUST_SPEED[0], GUST_SPEED[1]),
            strength = randRange(rng, GUST_STRENGTH[0], GUST_STRENGTH[1]) * (0.5f + 0.5f * gustLevel),
        )
    }

    fun gustAt(x: Float): Float {
        val g = gust ?: return 0f
        val u = (x - g.x) / g.sigma
        return (g.strength * Math.exp((-0.5 * u * u).toDouble())).toFloat()
    }

    fun localWind(x: Float): Float = Math.min(1f, wind + gustAt(x))

    // ---------- glass drops ----------
    fun makeDrop(x: Float, y: Float): RainDrop {
        val bias = rng.nextFloat()
        return RainDrop(
            x = x,
            y = y,
            r = 2f + 7f * bias * bias,
            vx = 0f,
            vy = 0f,
            tilt = 0f,
            stall = randRange(rng, 0.5f, 5f),
            phase = randRange(rng, 0f, TAU),
            wobbleAmp = randRange(rng, 1.5f, 4.5f),
            wobbleFreq = randRange(rng, 2f, 5f),
            gx = x,
            gy = y,
        )
    }

    fun spawnDrop() {
        val x = randRange(rng, 6f, width - 6f)
        val y = randRange(rng, 0f, height * 0.85f)
        drops.add(makeDrop(x, y))
    }

    fun terminalSpeed(r: Float): Float = 40f + r * 14f
    fun gravityFor(r: Float): Float = 300f * (r / 9f)

    fun spawnDrops(dt: Float) {
        val target = Math.round(params.glassDrops * DROPS_PER_GLASS_UNIT)
        if (drops.size >= target) {
            spawnDebt = 0f
            return
        }
        spawnDebt += (4f + 16f * params.density) * dt
        while (spawnDebt >= 1f && drops.size < target) {
            spawnDrop()
            spawnDebt -= 1f
        }
    }

    fun moveDrop(d: RainDrop, dt: Float) {
        val lw = localWind(d.x)
        val slide = 0.5f + params.slideSpeed
        if (d.stall > 0f) {
            d.stall -= dt
            d.vy = 0f
            if (lw > STALL_BREAK_WIND && d.r > 3f) d.stall = 0f
        } else {
            d.vy = Math.min(d.vy + gravityFor(d.r) * slide * dt, terminalSpeed(d.r) * slide)
            if (d.r < 5f && rng.nextFloat() < 0.35f * dt) {
                d.stall = randRange(rng, 0.3f, 1.5f)
            }
        }
        d.phase += d.wobbleFreq * dt
        val moving = Math.min(1f, d.vy / 40f)
        val push = MAX_DRIFT * lw * moving * (0.4f + 0.6f * Math.min(1f, d.r / 9f))
        d.vx += (push - d.vx) * Math.min(1f, 4f * dt)
        val wobble = (d.wobbleAmp * d.wobbleFreq * Math.cos(d.phase.toDouble()) * moving).toFloat()
        val sideways = d.vx + wobble
        d.sideways = sideways
        d.x += sideways * dt
        d.y += d.vy * dt
        if (d.x < d.r || d.x > width - d.r) {
            d.x = clamp(d.x, d.r, width - d.r)
            d.vx = 0f
        }
        d.tilt = if (d.vy > 0f) {
            clamp(Math.atan2(sideways.toDouble(), Math.max(d.vy, 20f).toDouble()).toFloat(), -MAX_TILT, MAX_TILT)
        } else {
            0.2f * lw
        }
        leaveGlint(d)
    }

    fun leaveGlint(d: RainDrop) {
        if (d.vy <= 0f) {
            d.gx = d.x
            d.gy = d.y
            return
        }
        if (Math.hypot((d.x - d.gx).toDouble(), (d.y - d.gy).toDouble()).toFloat() < GLINT_SPACING) return
        glints.add(SheenSpot(x = d.gx, y = d.gy, r = d.r * 0.7f, age = 0f))
        d.gx = d.x
        d.gy = d.y
    }

    fun mergeDrops(a: RainDrop, b: RainDrop): RainDrop {
        val ma = a.r * a.r
        val mb = b.r * b.r
        val m = ma + mb
        val big = if (ma >= mb) a else b
        big.x = (a.x * ma + b.x * mb) / m
        big.y = (a.y * ma + b.y * mb) / m
        big.vx = (a.vx * ma + b.vx * mb) / m
        big.vy = (a.vy * ma + b.vy * mb) / m
        big.r = Math.min(Math.sqrt(m.toDouble()).toFloat(), MAX_RADIUS)
        big.stall = 0f
        return big
    }

    fun mergeTouching() {
        val ds = drops
        for (i in 0 until ds.size) {
            val a = ds[i]
            if (a.dead) continue
            for (j in (i + 1) until ds.size) {
                val b = ds[j]
                if (b.dead) continue
                if (Math.hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat() < (a.r + b.r) * 0.85f) {
                    val keep = mergeDrops(a, b)
                    val gone = if (keep === a) b else a
                    gone.dead = true
                    if (gone === a) break
                }
            }
        }
    }

    fun updateDrops(dt: Float) {
        spawnDrops(dt)
        for (d in drops) moveDrop(d, dt)
        mergeTouching()
        for (d in drops) d.x = clamp(d.x, d.r, width - d.r)
        drops.removeAll { it.dead || it.y - it.r > height }
    }

    fun fadeGlints(dt: Float) {
        var w = 0
        for (i in 0 until glints.size) {
            val g = glints[i]
            g.age += dt
            if (g.age < GLINT_SECONDS) {
                glints[w++] = g
            }
        }
        while (glints.size > w) {
            glints.removeAt(glints.size - 1)
        }
        if (glints.size > MAX_GLINTS) {
            val excess = glints.size - MAX_GLINTS
            for (i in 0 until excess) {
                glints.removeAt(0)
            }
        }
    }

    // ---------- snow ----------
    fun makeFlake(y: Float): SimFlake {
        val depth = rng.nextFloat()
        val settles = rng.nextFloat() < SETTLE_SHARE
        return SimFlake(
            x = randRange(rng, 0f, width),
            y = y,
            r = 1f + 2.5f * depth,
            depth = depth,
            vy = FLAKE_FALL_PX * params.fallSpeed * (0.6f + 0.8f * depth),
            phase = randRange(rng, 0f, TAU),
            swayAmp = randRange(rng, 3f, 8f),
            swayFreq = randRange(rng, 0.8f, 1.5f),
            settleY = if (settles) randRange(rng, SETTLE_HEIGHT[0], SETTLE_HEIGHT[1]) * height else Float.POSITIVE_INFINITY,
            settled = false,
            rest = 0f,
            melt = 0f,
        )
    }

    fun initSnow() {
        if (!params.snow) return
        val n = Math.round(params.density * FLAKES_AT_FULL_DENSITY)
        for (i in 0 until n) {
            flakes.add(makeFlake(randRange(rng, -10f, height)))
        }
    }

    fun recycleFlake(f: SimFlake) {
        val fresh = makeFlake(-5f)
        f.x = fresh.x
        f.y = fresh.y
        f.r = fresh.r
        f.depth = fresh.depth
        f.vy = fresh.vy
        f.phase = fresh.phase
        f.swayAmp = fresh.swayAmp
        f.swayFreq = fresh.swayFreq
        f.settleY = fresh.settleY
        f.settled = false
        f.rest = 0f
        f.melt = 0f
    }

    fun updateSnow(dt: Float) {
        var settledCount = flakes.count { it.settled }
        for (f in flakes) {
            if (f.settled) {
                if (f.rest > 0f) f.rest -= dt else f.melt += dt
                if (f.melt >= MELT_SECONDS) {
                    recycleFlake(f)
                    settledCount--
                }
                continue
            }
            val lw = localWind(f.x)
            f.phase += f.swayFreq * dt
            val drift = (MAX_FLAKE_DRIFT * lw * (0.5f + 0.5f * f.depth) + f.swayAmp * f.swayFreq * Math.cos(f.phase.toDouble())).toFloat()
            f.x = wrap(f.x + drift * dt, 0f, width)
            f.y += f.vy * dt
            if (f.y >= f.settleY && settledCount < MAX_SETTLED) {
                f.settled = true
                f.rest = randRange(rng, SETTLE_REST[0], SETTLE_REST[1])
                settledCount++
            } else if (f.y - f.r > height) {
                recycleFlake(f)
            }
        }
    }

    // ---------- sky: clouds, haze, stars ----------
    fun makeCloud(layer: Int, x: Float): SimCloud {
        val size = randRange(rng, 90f, 170f) * (if (layer == 0) 1.3f else 1f)
        val puffs = mutableListOf<CloudPuff>()
        val n = 5 + Math.floor((rng.nextFloat() * 4).toDouble()).toInt()
        for (i in 0 until n) {
            puffs.add(
                CloudPuff(
                    dx = randRange(rng, -0.6f, 0.6f) * size,
                    dy = randRange(rng, -0.2f, 0.15f) * size,
                    r = randRange(rng, 0.3f, 0.5f) * size,
                )
            )
        }
        return SimCloud(
            x = x,
            y = randRange(rng, 0.02f, if (layer == 0) 0.45f else 0.6f) * height,
            size = size,
            layer = layer,
            puffs = puffs,
            speed = CLOUD_BASE_SPEED[layer] * randRange(rng, 0.8f, 1.2f),
        )
    }

    fun initClouds() {
        val perLayer = Math.round(2f + params.clouds * 5f)
        for (layer in 0 until params.cloudLayers) {
            for (i in 0 until perLayer) {
                clouds.add(makeCloud(layer, randRange(rng, -100f, width + 100f)))
            }
        }
    }

    fun updateClouds(dt: Float) {
        for (c in clouds) {
            val windShare = if (c.layer == 0) 0.6f else 1f
            c.x += (c.speed + CLOUD_WIND_SPEED * wind * windShare) * dt
            if (c.x - c.size > width) c.x = -c.size
        }
    }

    fun initHaze() {
        if (params.fog <= 0f) return
        for (i in 0 until 7) {
            haze.add(
                SimHaze(
                    x = randRange(rng, 0f, width),
                    y = randRange(rng, 0f, height),
                    rx = randRange(rng, 160f, 300f),
                    ry = randRange(rng, 50f, 110f),
                    speed = randRange(rng, 4f, 10f),
                    alpha = randRange(rng, 0.18f, 0.32f) * params.fog,
                )
            )
        }
    }

    fun updateHaze(dt: Float) {
        for (h in haze) {
            h.x += h.speed * (1f + 2f * wind) * dt
            if (h.x - h.rx > width) h.x = -h.rx
        }
    }

    fun initStars() {
        val n = Math.round(45f * params.stars)
        for (i in 0 until n) {
            val high = rng.nextFloat()
            stars.add(
                SimStar(
                    x = randRange(rng, 0f, width),
                    y = high * high * height * 0.6f,
                    r = randRange(rng, 0.5f, 1.6f),
                    phase = randRange(rng, 0f, TAU),
                    freq = randRange(rng, 0.6f, 2.2f),
                )
            )
        }
    }

    fun updateStars(dt: Float) {
        for (s in stars) {
            s.phase += s.freq * dt
        }
    }

    // ---------- lightning ----------
    fun makeBoltPath(): List<WeatherSimPoint> {
        val pts = mutableListOf(WeatherSimPoint(randRange(rng, 0.2f, 0.8f) * width, 0f))
        val reach = randRange(rng, 0.3f, 0.55f) * height
        val lean = randRange(rng, -0.3f, 0.3f)
        var last = pts[0]
        while (last.y < reach) {
            last = WeatherSimPoint(
                x = last.x + randRange(rng, -18f, 18f) + lean * 24f,
                y = last.y + randRange(rng, 18f, 34f),
            )
            pts.add(last)
        }
        return pts
    }

    fun flashCurve(age: Float, doubleFlash: Boolean): Float {
        val first = Math.exp((-age * 14f).toDouble()).toFloat()
        val second = if (doubleFlash && age > DOUBLE_FLASH_DELAY) {
            0.8f * Math.exp((-(age - DOUBLE_FLASH_DELAY) * 12f).toDouble()).toFloat()
        } else {
            0f
        }
        return Math.max(first, second)
    }

    fun endFlash() {
        flashAge = -1f
        flash = 0f
        boltPath = null
    }

    fun updateLightning(dt: Float) {
        if (params.bolt <= 0f) return
        if (flashAge >= 0f) {
            flashAge += dt
            flash = flashCurve(flashAge, flashDouble)
            if (flashAge > FLASH_SECONDS) endFlash()
            return
        }
        boltTimer -= dt
        if (boltTimer > 0f) return
        boltTimer = randRange(rng, BOLT_GAP[0], BOLT_GAP[1])
        flashAge = 0f
        flash = 1f
        flashDouble = rng.nextFloat() < DOUBLE_FLASH_CHANCE
        boltPath = makeBoltPath()
    }

    // ---------- impacts ----------
    fun inKeepOut(x: Float, y: Float): Boolean {
        for (r in keepOut) {
            if (x >= r.x && x <= r.x + r.w && y >= r.y && y <= r.y + r.h) return true
        }
        return false
    }

    fun pickImpactSpot(): WeatherSimPoint? {
        for (i in 0 until KEEP_OUT_TRIES) {
            val x = randRange(rng, 8f, width - 8f)
            val y = randRange(rng, height * 0.03f, height * 0.97f)
            if (!inKeepOut(x, y) || rng.nextFloat() >= KEEP_OUT_SKIP) {
                return WeatherSimPoint(x, y)
            }
        }
        return null
    }

    fun makeImpact(x: Float, y: Float): SimImpact {
        val lw = localWind(x)
        val beads = mutableListOf<ImpactBead>()
        val n = BEADS_PER_IMPACT_MIN + Math.floor((rng.nextFloat() * (BEADS_PER_IMPACT_MAX - BEADS_PER_IMPACT_MIN + 1)).toDouble()).toInt()
        for (i in 0 until n) {
            val a = randRange(rng, 0f, TAU)
            val spread = randRange(rng, 0.4f, 1f) * BEAD_SPREAD
            val dx = (Math.cos(a.toDouble()) * spread).toFloat() + BEAD_SLIDE_WIND * lw * randRange(rng, 0.55f, 1f)
            val dy = (Math.sin(a.toDouble()) * spread * 0.6).toFloat() + randRange(rng, BEAD_SAG[0], BEAD_SAG[1])
            val r = randRange(rng, BEAD_RADIUS[0], BEAD_RADIUS[1])
            val life = randRange(rng, BEAD_LIFE[0], BEAD_LIFE[1])
            beads.add(ImpactBead(dx = dx, dy = dy, r = r, life = life))
        }
        val ringR = randRange(rng, RING_RADIUS[0], RING_RADIUS[1])
        var maxLife = RING_SECONDS
        for (b in beads) {
            if (b.life > maxLife) maxLife = b.life
        }
        return SimImpact(x = x, y = y, age = 0f, ringR = ringR, beads = beads, life = maxLife)
    }

    fun spawnImpacts(dt: Float) {
        if (impactRate <= 0f) return
        impactDebt += impactRate * dt
        while (impactDebt >= 1f) {
            impactDebt -= 1f
            if (impacts.size >= MAX_IMPACTS) continue
            val spot = pickImpactSpot() ?: continue
            impacts.add(makeImpact(spot.x, spot.y))
            val target = Math.round(params.glassDrops * DROPS_PER_GLASS_UNIT)
            if (rng.nextFloat() < STAY_CHANCE && drops.size < target) {
                val d = makeDrop(spot.x, spot.y)
                d.r = randRange(rng, 1.5f, 3f)
                drops.add(d)
            }
        }
    }

    fun updateImpacts(dt: Float) {
        spawnImpacts(dt)
        for (im in impacts) {
            im.age += dt
        }
        impacts.removeAll { it.age >= it.life }
    }

    // ---------- frame advance and controls ----------
    fun update(dt: Float) {
        time += dt
        updateWind(dt)
        if (params.glassDrops > 0f) updateDrops(dt)
        if (params.snow) updateSnow(dt)
        updateClouds(dt)
        updateHaze(dt)
        updateStars(dt)
        sunPhase += SUN_TURN * dt
        updateLightning(dt)
        fadeGlints(dt)
    }

    fun updateHome(dt: Float) {
        update(dt)
        updateImpacts(dt)
    }

    fun stillFrame() {
        endFlash()
        glints.clear()
    }
}
