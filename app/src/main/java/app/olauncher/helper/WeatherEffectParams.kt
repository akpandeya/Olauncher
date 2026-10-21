package app.olauncher.helper

/**
 * Numbers handed to the weather shader as uniforms. All values are clamped to the ranges below.
 * [density] 0..1 streak/flake amount; [fallSpeed] 0.2..3 fall speed; [glassDrops] 0..30 drops on the glass;
 * [slideSpeed] 0..1.2 how fast glass drops slide; [frost] 0..1 icy edge; [hail] 0 or 1; [bolt] 0 or 1.
 */
data class ShaderParams(
    val density: Float,
    val fallSpeed: Float,
    val glassDrops: Float,
    val slideSpeed: Float,
    val frost: Float,
    val hail: Float,
    val bolt: Float,
)

/** Turns an [EffectSpec] into clamped [ShaderParams]. Context-free. */
object EffectParams {
    const val MAX_GLASS_DROPS = 30f
    const val MIN_FALL_SPEED = 0.2f
    const val MAX_FALL_SPEED = 3f
    const val MAX_SLIDE_SPEED = 1.2f

    fun of(spec: EffectSpec): ShaderParams {
        val level = when (spec.intensity) {
            Intensity.LIGHT -> 0
            Intensity.MODERATE -> 1
            Intensity.HEAVY -> 2
        }
        fun pick(light: Float, moderate: Float, heavy: Float) = floatArrayOf(light, moderate, heavy)[level]
        val raw = when (spec.effect) {
            WeatherEffect.RAIN -> ShaderParams(
                density = pick(0.35f, 0.6f, 0.9f),
                fallSpeed = pick(1.0f, 1.5f, 2.2f),
                glassDrops = pick(6f, 14f, 26f),
                slideSpeed = pick(0.3f, 0.6f, 1.0f) * (if (spec.freezing) 0.4f else 1f),
                frost = if (spec.freezing) 0.7f else 0f,
                hail = 0f,
                bolt = 0f,
            )
            WeatherEffect.STORM -> ShaderParams(
                density = pick(0.6f, 0.75f, 0.95f),
                fallSpeed = pick(1.6f, 1.9f, 2.5f),
                glassDrops = 0f,
                slideSpeed = 0f,
                frost = 0f,
                hail = if (spec.hail) 1f else 0f,
                bolt = 1f,
            )
            WeatherEffect.SNOW -> ShaderParams(
                density = pick(0.3f, 0.6f, 0.9f),
                fallSpeed = pick(0.35f, 0.5f, 0.8f),
                glassDrops = 0f, slideSpeed = 0f, frost = 0f, hail = 0f, bolt = 0f,
            )
            WeatherEffect.SUN, WeatherEffect.FOG, WeatherEffect.CLOUDS ->
                ShaderParams(0.5f, 0.5f, 0f, 0f, 0f, 0f, 0f)
            WeatherEffect.NONE -> ShaderParams(0f, MIN_FALL_SPEED, 0f, 0f, 0f, 0f, 0f)
        }
        return raw.copy(
            density = raw.density.coerceIn(0f, 1f),
            fallSpeed = raw.fallSpeed.coerceIn(MIN_FALL_SPEED, MAX_FALL_SPEED),
            glassDrops = raw.glassDrops.coerceIn(0f, MAX_GLASS_DROPS),
            slideSpeed = raw.slideSpeed.coerceIn(0f, MAX_SLIDE_SPEED),
            frost = raw.frost.coerceIn(0f, 1f),
            hail = raw.hail.coerceIn(0f, 1f),
            bolt = raw.bolt.coerceIn(0f, 1f),
        )
    }
}
