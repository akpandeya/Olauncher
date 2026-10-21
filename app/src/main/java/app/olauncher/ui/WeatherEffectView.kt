package app.olauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import app.olauncher.helper.EffectParams
import app.olauncher.helper.EffectSpec
import app.olauncher.helper.EffectTint
import app.olauncher.helper.Intensity
import app.olauncher.helper.ShaderParams
import app.olauncher.helper.WeatherEffect
import app.olauncher.helper.WeatherEffects

/**
 * Full-screen weather animation drawn with an AGSL shader over the wallpaper.
 * It only animates while [setRunning] is true (Home visible) and never takes touches.
 * Frames are at most [WeatherEffects.MAX_FPS] per second.
 */
class WeatherEffectView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val paint = Paint()
    private var shader: RuntimeShader? = null
    private var shaderFailed = false
    private var effect = WeatherEffect.NONE
    private var params = EffectParams.of(EffectSpec(WeatherEffect.NONE, Intensity.LIGHT))
    // Normalised y ranges (top1, bottom1, top2, bottom2) where glass drops are faded out for text.
    private var textBand = floatArrayOf(0f, 0f, 0f, 0f)
    private var tint = EffectTint(0, 0)
    private var running = false
    private val startMs = SystemClock.uptimeMillis()

    private val frame = object : Runnable {
        override fun run() {
            if (!running) return
            invalidate()
            postOnAnimationDelayed(this, WeatherEffects.FRAME_INTERVAL_MS)
        }
    }

    fun setEffect(spec: EffectSpec, tint: EffectTint) {
        val newParams = EffectParams.of(spec)
        if (spec.effect == effect && tint == this.tint && newParams == params) return
        this.effect = spec.effect
        this.params = newParams
        this.tint = tint
        invalidate()
    }

    /** Pixel y ranges of the clock block and the app list; glass drops fade out over them. */
    fun setTextBands(top1: Int, bottom1: Int, top2: Int, bottom2: Int) {
        val h = height.toFloat().coerceAtLeast(1f)
        val next = floatArrayOf(top1 / h, bottom1 / h, top2 / h, bottom2 / h)
        if (next.contentEquals(textBand)) return
        textBand = next
        invalidate()
    }

    fun setRunning(run: Boolean) {
        if (running == run) return
        running = run
        removeCallbacks(frame)
        if (run) post(frame)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (effect == WeatherEffect.NONE) return
        val s = shaderOrNull() ?: return
        s.setFloatUniform("iResolution", width.toFloat(), height.toFloat())
        // A stopped view (Home hidden or animations off) draws one fixed, pleasant frame.
        val time = if (running) (SystemClock.uptimeMillis() - startMs) / 1000f else STILL_TIME_S
        s.setFloatUniform("iTime", time)
        s.setFloatUniform("density", params.density)
        s.setFloatUniform("fallSpeed", params.fallSpeed)
        s.setFloatUniform("glassDrops", params.glassDrops)
        s.setFloatUniform("slideSpeed", params.slideSpeed)
        s.setFloatUniform("frost", params.frost)
        s.setFloatUniform("hail", params.hail)
        s.setFloatUniform("bolt", params.bolt)
        s.setFloatUniform("textBand", textBand[0], textBand[1], textBand[2], textBand[3])
        s.setIntUniform("effect", effect.ordinal)
        s.setFloatUniform("tintA", channel(tint.primary, Color::red), channel(tint.primary, Color::green), channel(tint.primary, Color::blue), 1f)
        s.setFloatUniform("tintB", channel(tint.secondary, Color::red), channel(tint.secondary, Color::green), channel(tint.secondary, Color::blue), 1f)
        paint.shader = s
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    override fun onDetachedFromWindow() {
        running = false
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    private fun shaderOrNull(): RuntimeShader? {
        shader?.let { return it }
        if (shaderFailed) return null
        return try {
            RuntimeShader(SHADER_SRC).also { shader = it }
        } catch (e: RuntimeException) {
            // A shader that does not compile on this device just leaves the wallpaper alone.
            shaderFailed = true
            null
        }
    }

    private fun channel(argb: Int, pick: (Int) -> Int): Float = pick(argb) / 255f

    private companion object {
        const val STILL_TIME_S = 7.3f
        // Effect ids match WeatherEffect ordinals: 1 sun, 2 fog, 3 rain, 4 snow, 5 storm, 6 clouds.
        const val SHADER_SRC = """
            uniform float2 iResolution;
            uniform float iTime;
            uniform int effect;
            uniform half4 tintA;
            uniform half4 tintB;
            uniform float density;
            uniform float fallSpeed;
            uniform float glassDrops;
            uniform float slideSpeed;
            uniform float frost;
            uniform float hail;
            uniform float bolt;
            uniform float4 textBand;

            float hash1(float n) { return fract(sin(n * 127.1) * 43758.5453); }
            float hash2(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }

            float vnoise(float2 p) {
                float2 i = floor(p);
                float2 f = fract(p);
                f = f * f * (3.0 - 2.0 * f);
                float a = hash2(i);
                float b = hash2(i + float2(1.0, 0.0));
                float c = hash2(i + float2(0.0, 1.0));
                float d = hash2(i + float2(1.0, 1.0));
                return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
            }

            float fbm(float2 p) {
                float v = 0.0;
                float amp = 0.5;
                float2 x = p;
                for (int i = 0; i < 4; i++) {
                    v += amp * vnoise(x);
                    x = x * 2.02;
                    amp = amp * 0.5;
                }
                return v;
            }

            // Falling streaks in columns; keep is the share of columns that carry a streak.
            float streaks(float2 q, float t, float columns, float speed, float keep) {
                float2 g = float2(q.x * columns, q.y * columns * 0.25);
                float cx = floor(g.x);
                float fx = fract(g.x);
                float h = hash1(cx + 1.0);
                float thin = smoothstep(0.44, 0.48, fx) * (1.0 - smoothstep(0.52, 0.56, fx));
                float y = fract(g.y + h - t * speed * (0.6 + 0.4 * h));
                float body = smoothstep(0.0, 0.02, y) * (1.0 - smoothstep(0.08, 0.30, y));
                return thin * body * step(1.0 - keep, h);
            }

            // Round flakes drifting down and swaying; keep is the share of cells with a flake.
            float flakes(float2 q, float t, float cells, float speed, float keep, float size) {
                float2 g = q * cells;
                g.y = g.y - t * speed;
                float2 id = floor(g);
                float2 f = fract(g);
                float h = hash2(id + float2(7.0, 3.0));
                float sway = 0.3 * sin(t * (0.5 + h) + h * 6.2831);
                float2 c = float2(0.5 + sway * 0.35, 0.2 + 0.6 * fract(h * 13.7));
                float d = length(f - c);
                return (1.0 - smoothstep(size, size * 2.0, d)) * step(1.0 - keep, h);
            }

            // Drops on glass: each grows, then slides down leaving a thin trail. Stylised, no refraction.
            float glassLayer(float2 q, float t, float count, float slide) {
                float cells = 7.0;
                float2 g = q * cells;
                float2 id = floor(g);
                float2 f = fract(g);
                float h = hash2(id + 11.0);
                float present = step(1.0 - count / 30.0, hash2(id + 5.0));
                float life = fract(t * 0.06 + h);
                float grow = clamp(life / 0.45, 0.0, 1.0);
                float s = clamp((life - 0.45) / 0.55, 0.0, 1.0);
                float travel = s * s * (0.35 + 0.35 * slide);
                float2 c = float2(0.2 + 0.6 * hash1(h * 91.0), 0.28 + travel);
                float r = (0.05 + 0.09 * h) * grow;
                float2 d = (f - c) * float2(1.0, 0.85);
                float body = 1.0 - smoothstep(r * 0.7, r, length(d));
                float rim = smoothstep(r * 0.6, r, length(d)) * (1.0 - smoothstep(r, r * 1.15, length(d)));
                float shine = 1.0 - smoothstep(0.0, r * 0.35, length(d - float2(-r * 0.3, -r * 0.3)));
                float trail = step(0.28, f.y) * step(f.y, c.y) * (1.0 - smoothstep(0.012, 0.03, abs(f.x - c.x))) * 0.35 * step(0.001, s);
                float fade = 1.0 - smoothstep(0.92, 1.0, life);
                return present * fade * (0.35 * body + 0.5 * rim + 0.6 * shine + trail);
            }

            float inBand(float y, float lo, float hi) {
                return smoothstep(lo - 0.02, lo, y) * (1.0 - smoothstep(hi, hi + 0.02, y));
            }

            // Jagged bolt from the top, flashing briefly every few seconds.
            float lightning(float2 uv, float t) {
                float cyc = floor(t / 7.0);
                float f = fract(t / 7.0);
                float on = step(0.4, hash1(cyc + 9.0)) * (f < 0.07 ? 1.0 : 0.0) * (1.0 - f / 0.07);
                float x0 = 0.2 + 0.6 * hash1(cyc + 21.0);
                float x = x0 + (vnoise(float2(uv.y * 9.0, cyc)) - 0.5) * 0.14 + (uv.y * 0.1);
                float reach = 0.35 + 0.3 * hash1(cyc + 4.0);
                float core = (1.0 - smoothstep(0.002, 0.008, abs(uv.x - x))) * step(uv.y, reach);
                float glow = (1.0 - smoothstep(0.0, 0.05, abs(uv.x - x))) * step(uv.y, reach) * 0.3;
                return on * (core + glow);
            }

            float flashAmount(float t) {
                float cyc = floor(t / 7.0);
                float f = fract(t / 7.0);
                float burst = f < 0.05 ? (1.0 - f / 0.05) : 0.0;
                return step(0.4, hash1(cyc + 9.0)) * burst * 0.06;
            }

            half4 main(float2 fragCoord) {
                float2 uv = fragCoord / iResolution;
                float2 q = fragCoord / iResolution.x;
                float a = 0.0;
                float extra = 0.0;
                if (effect == 1) {
                    float2 p = q - float2(0.92, -0.04);
                    float rays = 0.5 + 0.5 * sin(atan(p.y, p.x) * 16.0 + iTime * 0.12);
                    rays = rays * rays;
                    float far = 0.5 + 0.5 * sin(atan(p.y, p.x) * 7.0 - iTime * 0.07);
                    float d = length(p);
                    a = (0.08 + 0.2 * rays) * exp(-d * 1.5) + 0.08 * far * exp(-d * 0.9) + 0.25 * exp(-d * 6.0);
                } else if (effect == 2) {
                    float2 w = float2(iTime * 0.02, iTime * 0.008);
                    float back = fbm(q * 1.5 + w * 0.6);
                    float front = fbm(q * 2.6 - w * 1.4 + 5.0);
                    float band = 0.6 + 0.4 * sin(uv.y * 5.0 + iTime * 0.05);
                    a = smoothstep(0.35, 0.8, back) * 0.28 + smoothstep(0.4, 0.8, front) * 0.3 * band;
                } else if (effect == 3) {
                    float keep = 0.25 + 0.6 * density;
                    float sp = fallSpeed;
                    a = streaks(q, iTime, 42.0, sp, keep) + 0.5 * streaks(q + 0.37, iTime, 70.0, sp * 1.3, keep);
                    float glass = glassLayer(q, iTime, glassDrops, slideSpeed);
                    float mask = (1.0 - inBand(uv.y, textBand.x, textBand.y)) * (1.0 - inBand(uv.y, textBand.z, textBand.w));
                    a = a * 0.8 + glass * mask;
                    if (frost > 0.0) {
                        float edge = max(abs(uv.x - 0.5) * 2.0, abs(uv.y - 0.5) * 2.0);
                        float ice = smoothstep(0.7, 1.0, edge) * (0.5 + 0.5 * fbm(q * 9.0));
                        extra = frost * ice * 0.55;
                    }
                } else if (effect == 4) {
                    float keep = 0.2 + 0.6 * density;
                    float sp = fallSpeed;
                    a = flakes(q, iTime, 9.0, sp, keep, 0.07)
                        + 0.7 * flakes(q + 0.21, iTime * 0.9, 16.0, sp * 1.4, keep, 0.06)
                        + 0.45 * flakes(q + 0.53, iTime * 0.8, 28.0, sp * 1.8, keep, 0.05);
                } else if (effect == 5) {
                    float keep = 0.3 + 0.5 * density;
                    a = streaks(q, iTime, 42.0, fallSpeed, keep) + 0.5 * streaks(q + 0.37, iTime, 70.0, fallSpeed * 1.3, keep);
                    if (hail > 0.5) {
                        a = a + 1.4 * flakes(q, iTime * 1.8, 30.0, 2.4, 0.3, 0.05);
                    }
                    extra = lightning(uv, iTime);
                } else if (effect == 6) {
                    float2 w = float2(iTime * 0.012, 0.0);
                    float back = fbm(q * 1.0 + w * 0.5);
                    float mid = fbm(q * 1.4 + w + 3.0);
                    float front = fbm(q * 2.2 + w * 2.0 + 8.0);
                    a = (smoothstep(0.45, 0.8, back) * 0.22 + smoothstep(0.45, 0.8, mid) * 0.26 +
                         smoothstep(0.5, 0.85, front) * 0.2) * (1.0 - uv.y * 0.4);
                }
                float flash = effect == 5 ? flashAmount(iTime) : 0.0;
                float3 rgb = tintA.rgb * min(a, 1.0) + tintB.rgb * (flash + extra);
                float alpha = min(a, 1.0) + flash + extra;
                return half4(half3(rgb), half(min(alpha, 1.0)));
            }
        """
    }
}
