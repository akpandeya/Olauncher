package app.olauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import app.olauncher.helper.EffectTint
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

    fun setEffect(effect: WeatherEffect, tint: EffectTint) {
        if (effect == this.effect && tint == this.tint) return
        this.effect = effect
        this.tint = tint
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
        s.setFloatUniform("iTime", (SystemClock.uptimeMillis() - startMs) / 1000f)
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
        // Effect ids match WeatherEffect ordinals: 1 sun, 2 fog, 3 rain, 4 snow, 5 storm, 6 clouds.
        const val SHADER_SRC = """
            uniform float2 iResolution;
            uniform float iTime;
            uniform int effect;
            uniform half4 tintA;
            uniform half4 tintB;

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

            // Falling streaks in columns. q is width-normalised, so cells keep their shape.
            float streaks(float2 q, float t, float columns, float speed) {
                float2 g = float2(q.x * columns, q.y * columns * 0.25);
                float cx = floor(g.x);
                float fx = fract(g.x);
                float h = hash1(cx + 1.0);
                float thin = smoothstep(0.44, 0.48, fx) * (1.0 - smoothstep(0.52, 0.56, fx));
                float y = fract(g.y + h - t * speed * (0.6 + 0.4 * h));
                float body = smoothstep(0.0, 0.02, y) * (1.0 - smoothstep(0.08, 0.30, y));
                return thin * body * step(0.45, h);
            }

            // Round flakes drifting down and swaying.
            float flakes(float2 q, float t, float cells, float speed) {
                float2 g = q * cells;
                g.y = g.y - t * speed;
                float2 id = floor(g);
                float2 f = fract(g);
                float h = hash2(id + float2(7.0, 3.0));
                float sway = 0.3 * sin(t * (0.5 + h) + h * 6.2831);
                float2 c = float2(0.5 + sway * 0.35, 0.2 + 0.6 * fract(h * 13.7));
                float d = length(f - c);
                return (1.0 - smoothstep(0.05, 0.10, d)) * step(0.6, h);
            }

            float flashAmount(float t) {
                float cyc = floor(t / 6.0);
                float f = fract(t / 6.0);
                float burst = f < 0.05 ? (1.0 - f / 0.05) : 0.0;
                return step(0.45, hash1(cyc + 3.0)) * burst * 0.22;
            }

            half4 main(float2 fragCoord) {
                float2 uv = fragCoord / iResolution;
                float2 q = fragCoord / iResolution.x;
                float a = 0.0;
                if (effect == 1) {
                    float2 p = q - float2(0.92, -0.04);
                    float rays = 0.5 + 0.5 * sin(atan(p.y, p.x) * 16.0 + iTime * 0.12);
                    rays = rays * rays;
                    a = (0.08 + 0.2 * rays) * exp(-length(p) * 1.5);
                } else if (effect == 2) {
                    float f = fbm(q * 2.0 + float2(iTime * 0.02, iTime * 0.008));
                    a = smoothstep(0.35, 0.8, f) * 0.45;
                } else if (effect == 3) {
                    a = streaks(q, iTime, 42.0, 1.4) + 0.5 * streaks(q + 0.37, iTime, 70.0, 1.9);
                } else if (effect == 4) {
                    a = flakes(q, iTime, 13.0, 0.5) + 0.6 * flakes(q + 0.21, iTime * 0.8, 22.0, 0.8);
                } else if (effect == 5) {
                    a = streaks(q, iTime, 42.0, 1.8) + 0.5 * streaks(q + 0.37, iTime, 70.0, 2.3);
                } else if (effect == 6) {
                    float f = fbm(q * 1.4 + float2(iTime * 0.012, 0.0));
                    a = smoothstep(0.45, 0.8, f) * 0.4 * (1.0 - uv.y * 0.4);
                }
                float flash = effect == 5 ? flashAmount(iTime) : 0.0;
                float3 rgb = tintA.rgb * a + tintB.rgb * flash;
                return half4(half3(rgb), half(a + flash));
            }
        """
    }
}
