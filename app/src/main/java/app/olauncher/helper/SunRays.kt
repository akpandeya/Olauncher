package app.olauncher.helper

typealias SunRayPass = SunRays.Pass

/**
 * A single sun ray definition. Context-free.
 */
data class SunRay(
    val index: Int,
    val angle: Float,
    val lengthFactor: Float,
    val brightness: Float,
    val length: Float = lengthFactor,
) {
    val passes: List<SunRays.Pass> get() = SunRays.PASSES
}

/**
 * Pure sun ray geometry and seeded hash. Context-free.
 * Ported from docs/design/home-weather-mock.html (<sim> and <drawing>).
 */
class SunRays(val sunPhase: Float = 0f) : List<SunRay> by Companion {

    data class Pass(
        val halfAngle: Float,
        val alpha: Float,
    )

    companion object : List<SunRay> {
        const val RAY_COUNT: Int = 18
        const val COUNT: Int = 18
        const val TURN_RATE: Float = 0.08f // rad/s, matches WeatherSim.SUN_TURN

        const val HALO_HALF_ANGLE: Float = 0.11f
        const val HALO_ALPHA: Float = 0.35f
        const val CORE_HALF_ANGLE: Float = 0.04f
        const val CORE_ALPHA: Float = 0.8f

        val PASSES: List<Pass> = listOf(
            Pass(halfAngle = HALO_HALF_ANGLE, alpha = HALO_ALPHA),
            Pass(halfAngle = CORE_HALF_ANGLE, alpha = CORE_ALPHA),
        )

        /** Fixed pseudo-random value in [0, 1) for ray [i] and [salt], matching the mock. */
        fun rayRandom(i: Int, salt: Int): Float {
            val x = Math.sin(i * 12.9898 + salt * 78.233) * 43758.5453
            return (x - Math.floor(x)).toFloat()
        }

        val rays: List<SunRay> = (0 until RAY_COUNT).map { i ->
            val angle = (i.toFloat() / RAY_COUNT) * WeatherSim.TAU + (rayRandom(i, 1) - 0.5f) * 0.12f
            val lenFactor = 0.5f + 0.5f * rayRandom(i, 2)
            val bright = 0.55f + 0.45f * rayRandom(i, 3)
            SunRay(
                index = i,
                angle = angle,
                lengthFactor = lenFactor,
                brightness = bright,
            )
        }

        override val size: Int get() = rays.size
        override fun get(index: Int): SunRay = rays[index]
        override fun isEmpty(): Boolean = rays.isEmpty()
        override fun iterator(): Iterator<SunRay> = rays.iterator()
        override fun listIterator(): ListIterator<SunRay> = rays.listIterator()
        override fun listIterator(index: Int): ListIterator<SunRay> = rays.listIterator(index)
        override fun subList(fromIndex: Int, toIndex: Int): List<SunRay> = rays.subList(fromIndex, toIndex)
        override fun contains(element: SunRay): Boolean = rays.contains(element)
        override fun containsAll(elements: Collection<SunRay>): Boolean = rays.containsAll(elements)
        override fun indexOf(element: SunRay): Int = rays.indexOf(element)
        override fun lastIndexOf(element: SunRay): Int = rays.lastIndexOf(element)

        operator fun invoke(sunPhase: Float = 0f): List<SunRay> = rays
        fun rays(sunPhase: Float = 0f): List<SunRay> = rays
        fun getRays(sunPhase: Float = 0f): List<SunRay> = rays
        fun ray(index: Int, sunPhase: Float = 0f): SunRay = rays[index]
        fun getRay(index: Int, sunPhase: Float = 0f): SunRay = rays[index]
    }

    val rays: List<SunRay> get() = Companion.rays
    fun rays(sunPhase: Float = 0f): List<SunRay> = Companion.rays
    fun getRays(sunPhase: Float = 0f): List<SunRay> = Companion.rays
    fun ray(index: Int, sunPhase: Float = 0f): SunRay = Companion.rays[index]
    fun getRay(index: Int, sunPhase: Float = 0f): SunRay = Companion.rays[index]
}
