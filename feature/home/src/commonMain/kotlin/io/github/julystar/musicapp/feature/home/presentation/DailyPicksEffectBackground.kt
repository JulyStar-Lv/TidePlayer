package io.github.julystar.musicapp.feature.home.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import top.yukonga.miuix.kmp.shader.RuntimeShader
import top.yukonga.miuix.kmp.shader.asBrush
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun DailyPicksEffectBackground(
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val surface = MiuixTheme.colorScheme.surface
    val shaderSupported = remember { isRuntimeShaderSupported() }
    val preset = remember(dark) { DailyPicksEffectPreset.get(dark) }
    val colorStage = remember { Animatable(0f) }
    val currentColors = remember { FloatArray(16) }
    val windowSize = LocalWindowInfo.current.containerSize
    var effectVisible by remember { mutableStateOf(true) }
    val animationTime = rememberDailyPicksFrameTimeSeconds(playing = effectVisible)
    val effectPainter = if (shaderSupported) {
        remember { DailyPicksEffectPainter() }
    } else {
        null
    }

    LaunchedEffect(preset, effectVisible) {
        if (!effectVisible) return@LaunchedEffect
        var targetStage = colorStage.value.toInt() + 1f
        while (isActive) {
            delay((preset.colorInterpPeriod * 500).toLong())
            colorStage.animateTo(
                targetValue = targetStage,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 35f),
            )
            targetStage += 1f
        }
    }

    Box(
        modifier = modifier.onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInWindow()
            effectVisible = windowSize.width <= 0 || windowSize.height <= 0 ||
                (
                    bounds.right > 0f &&
                        bounds.left < windowSize.width &&
                        bounds.bottom > 0f &&
                        bounds.top < windowSize.height
                )
        },
    ) {
        if (effectPainter != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(surface)
                interpolateDailyPicksColors(
                    preset = preset,
                    stage = colorStage.value,
                    output = currentColors,
                )
                effectPainter.updateResolution(size.width, size.height)
                effectPainter.updatePresetIfNeeded(
                    effectHeight = size.height * 0.78f,
                    height = size.height,
                    width = size.width,
                    dark = dark,
                )
                effectPainter.updateColors(currentColors)
                effectPainter.updateAnimationTime(animationTime())
                drawRect(effectPainter.brush)
            }
        } else {
            DailyPicksFallbackBackground(
                surface = surface,
                preset = preset,
                colorStage = { colorStage.value },
                animationTime = animationTime,
                currentColors = currentColors,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to surface.copy(alpha = if (dark) 0.32f else 0.42f),
                            0.38f to surface.copy(alpha = if (dark) 0.14f else 0.18f),
                            0.68f to Color.Transparent,
                            1f to Color.Transparent,
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun DailyPicksFallbackBackground(
    surface: Color,
    preset: DailyPicksEffectPreset.Config,
    colorStage: () -> Float,
    animationTime: () -> Float,
    currentColors: FloatArray,
    modifier: Modifier,
) {
    Canvas(modifier = modifier) {
        drawRect(surface)
        interpolateDailyPicksColors(
            preset = preset,
            stage = colorStage(),
            output = currentColors,
        )
        val time = animationTime()
        repeat(4) { index ->
            val pointOffset = index * 3
            var pointX = preset.points[pointOffset]
            var pointY = preset.points[pointOffset + 1]
            pointX += sin(time + pointY) * preset.pointOffset
            pointY += cos(time + pointX) * preset.pointOffset
            val colorOffset = index * 4
            val color = Color(
                red = currentColors[colorOffset],
                green = currentColors[colorOffset + 1],
                blue = currentColors[colorOffset + 2],
                alpha = currentColors[colorOffset + 3],
            )
            val center = Offset(
                x = pointX * size.width,
                y = (1f - pointY) * size.height,
            )
            val radius = preset.points[pointOffset + 2] * max(size.width, size.height)
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to color,
                        0.56f to color.copy(alpha = color.alpha * 0.68f),
                        1f to Color.Transparent,
                    ),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
    }
}

private fun interpolateDailyPicksColors(
    preset: DailyPicksEffectPreset.Config,
    stage: Float,
    output: FloatArray,
) {
    val base = stage.toInt()
    val fraction = stage - base
    fun colorsAt(index: Int): FloatArray = when (index % 4) {
        0 -> preset.colors2
        1 -> preset.colors1
        2 -> preset.colors2
        else -> preset.colors3
    }

    val startColors = colorsAt(base)
    val endColors = colorsAt(base + 1)
    output.indices.forEach { index ->
        output[index] = startColors[index] + (endColors[index] - startColors[index]) * fraction
    }
}

private object DailyPicksEffectPreset {
    class Config(
        val points: FloatArray,
        val colors1: FloatArray,
        val colors2: FloatArray,
        val colors3: FloatArray,
        val colorInterpPeriod: Float,
        val lightOffset: Float,
        val saturateOffset: Float,
        val pointOffset: Float,
        val shadowColorMulti: Float = 0.3f,
        val shadowColorOffset: Float = 0.3f,
        val shadowNoiseScale: Float = 5f,
    )

    private val light = Config(
        points = floatArrayOf(
            0.8f, 0.2f, 1f,
            0.8f, 0.9f, 1f,
            0.2f, 0.9f, 1f,
            0.2f, 0.2f, 1f,
        ),
        colors1 = floatArrayOf(
            1f, 0.9f, 0.94f, 1f,
            1f, 0.84f, 0.89f, 1f,
            0.97f, 0.73f, 0.82f, 1f,
            0.64f, 0.65f, 0.98f, 1f,
        ),
        colors2 = floatArrayOf(
            0.58f, 0.74f, 1f, 1f,
            1f, 0.9f, 0.93f, 1f,
            0.74f, 0.76f, 1f, 1f,
            0.97f, 0.77f, 0.84f, 1f,
        ),
        colors3 = floatArrayOf(
            0.98f, 0.86f, 0.9f, 1f,
            0.6f, 0.73f, 0.98f, 1f,
            0.92f, 0.93f, 1f, 1f,
            0.56f, 0.69f, 1f, 1f,
        ),
        colorInterpPeriod = 5f,
        lightOffset = 0.1f,
        saturateOffset = 0.2f,
        pointOffset = 0.2f,
    )

    private val dark = Config(
        points = floatArrayOf(
            0.8f, 0.2f, 1f,
            0.8f, 0.9f, 1f,
            0.2f, 0.9f, 1f,
            0.2f, 0.2f, 1f,
        ),
        colors1 = floatArrayOf(
            0.2f, 0.06f, 0.88f, 0.4f,
            0.3f, 0.14f, 0.55f, 0.5f,
            0f, 0.64f, 0.96f, 0.5f,
            0.11f, 0.16f, 0.83f, 0.4f,
        ),
        colors2 = floatArrayOf(
            0.07f, 0.15f, 0.79f, 0.5f,
            0.62f, 0.21f, 0.67f, 0.5f,
            0.06f, 0.25f, 0.84f, 0.5f,
            0f, 0.2f, 0.78f, 0.5f,
        ),
        colors3 = floatArrayOf(
            0.58f, 0.3f, 0.74f, 0.4f,
            0.27f, 0.18f, 0.6f, 0.5f,
            0.66f, 0.26f, 0.62f, 0.5f,
            0.12f, 0.16f, 0.7f, 0.6f,
        ),
        colorInterpPeriod = 8f,
        lightOffset = 0f,
        saturateOffset = 0.17f,
        pointOffset = 0.4f,
    )

    fun get(dark: Boolean): Config = if (dark) this.dark else light
}

private class DailyPicksEffectPainter {
    private val runtimeShader = RuntimeShader(DailyPicksEffectShader).also(::initializeStaticUniforms)
    private val resolution = FloatArray(2)
    private val bound = FloatArray(4)
    private var animationTime = Float.NaN
    private var cachedDark: Boolean? = null
    private var cachedWidth = Float.NaN
    private var cachedHeight = Float.NaN

    val brush: Brush
        get() = runtimeShader.asBrush()

    private fun initializeStaticUniforms(shader: RuntimeShader) {
        shader.setFloatUniform("uTranslateY", 0f)
        shader.setFloatUniform("uNoiseScale", 1.5f)
        shader.setFloatUniform("uPointRadiusMulti", 1f)
        shader.setFloatUniform("uAlphaMulti", 1f)
        shader.setFloatUniform("uAlphaOffset", 0.1f)
        shader.setFloatUniform("uShadowOffset", 0.01f)
    }

    fun updateResolution(width: Float, height: Float) {
        if (resolution[0] == width && resolution[1] == height) return
        resolution[0] = width
        resolution[1] = height
        runtimeShader.setFloatUniform("uResolution", resolution)
    }

    fun updateAnimationTime(time: Float) {
        if (animationTime == time) return
        animationTime = time
        runtimeShader.setFloatUniform("uAnimTime", time)
    }

    fun updateColors(colors: FloatArray) {
        runtimeShader.setFloatUniform("uColors", colors)
    }

    fun updatePresetIfNeeded(
        effectHeight: Float,
        height: Float,
        width: Float,
        dark: Boolean,
    ) {
        if (cachedDark == dark && cachedWidth == width && cachedHeight == height) return
        updateBound(effectHeight, height, width)
        val preset = DailyPicksEffectPreset.get(dark)
        runtimeShader.setFloatUniform("uPoints", preset.points)
        runtimeShader.setFloatUniform("uPointOffset", preset.pointOffset)
        runtimeShader.setFloatUniform("uLightOffset", preset.lightOffset)
        runtimeShader.setFloatUniform("uSaturateOffset", preset.saturateOffset)
        runtimeShader.setFloatUniform("uBound", bound)
        runtimeShader.setFloatUniform("uShadowColorMulti", preset.shadowColorMulti)
        runtimeShader.setFloatUniform("uShadowColorOffset", preset.shadowColorOffset)
        runtimeShader.setFloatUniform("uShadowNoiseScale", preset.shadowNoiseScale)
        cachedDark = dark
        cachedWidth = width
        cachedHeight = height
    }

    private fun updateBound(effectHeight: Float, totalHeight: Float, totalWidth: Float) {
        val heightRatio = effectHeight / totalHeight
        if (totalWidth <= totalHeight) {
            bound[0] = 0f
            bound[1] = 1f - heightRatio
            bound[2] = 1f
            bound[3] = heightRatio
        } else {
            val aspectRatio = totalWidth / totalHeight
            val contentCenterY = 1f - heightRatio / 2f
            bound[0] = 0f
            bound[1] = contentCenterY - aspectRatio / 2f
            bound[2] = 1f
            bound[3] = aspectRatio
        }
    }
}

@Composable
private fun rememberDailyPicksFrameTimeSeconds(playing: Boolean): () -> Float {
    var time by remember { mutableFloatStateOf(0f) }
    var startOffset by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        if (!playing) {
            startOffset = time
            return@LaunchedEffect
        }
        val start = withFrameNanos { it }
        while (isActive) {
            val now = withFrameNanos { it }
            time = startOffset + (now - start) / 1_000_000_000f
        }
    }
    return { time }
}

private const val DailyPicksEffectShader = """
    uniform vec2 uResolution;
    uniform shader uTex;
    uniform shader uTexBitmap;
    uniform vec2 uTexWH;

    uniform float uAnimTime;
    uniform vec4 uBound;
    uniform float uTranslateY;
    uniform vec3 uPoints[4];
    uniform vec4 uColors[4];
    uniform float uAlphaMulti;
    uniform float uNoiseScale;
    uniform float uPointOffset;
    uniform float uPointRadiusMulti;
    uniform float uSaturateOffset;
    uniform float uLightOffset;
    uniform float uAlphaOffset;
    uniform float uShadowColorMulti;
    uniform float uShadowColorOffset;
    uniform float uShadowNoiseScale;
    uniform float uShadowOffset;

    float hash(vec2 p) {
        vec3 p3 = fract(vec3(p.xyx) * 0.13);
        p3 += dot(p3, p3.yzx + 3.333);
        return fract((p3.x + p3.y) * p3.z);
    }

    float perlin(vec2 x) {
        vec2 i = floor(x); vec2 f = fract(x);
        float a = hash(i); float b = hash(i + vec2(1.0, 0.0));
        float c = hash(i + vec2(0.0, 1.0)); float d = hash(i + vec2(1.0, 1.0));
        vec2 u = f * f * (3.0 - 2.0 * f);
        return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
    }

    float gradientNoise(in vec2 uv) {
        return fract(52.9829189 * fract(dot(uv, vec2(0.06711056, 0.00583715))));
    }

    vec3 rgb2hsv(vec3 c) {
        vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
        vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
        vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
        float d = q.x - min(q.w, q.y);
        float e = 1.0e-10;
        return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
    }

    vec3 hsv2rgb(vec3 c) {
        vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
        vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
        return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
    }

    vec4 main(vec2 fragCoord) {
        vec2 vUv = fragCoord / uResolution;
        vUv.y = 1.0 - vUv.y;
        vec2 uv = vUv;
        uv -= vec2(0.0, uTranslateY);
        uv.xy -= uBound.xy;
        uv.xy /= uBound.zw;

        vec4 color = vec4(0.0);
        float noiseValue = perlin(vUv * uNoiseScale + vec2(-uAnimTime, -uAnimTime));

        for (int i = 0; i < 4; i++) {
            vec4 pointColor = uColors[i];
            pointColor.rgb *= pointColor.a;
            vec2 point = uPoints[i].xy;
            float rad = uPoints[i].z * uPointRadiusMulti;

            point.x += sin(uAnimTime + point.y) * uPointOffset;
            point.y += cos(uAnimTime + point.x) * uPointOffset;

            float d = distance(uv, point);
            float pct = smoothstep(rad, 0.0, d);

            color.rgb = mix(color.rgb, pointColor.rgb, pct);
            color.a = mix(color.a, pointColor.a, pct);
        }

        float oppositeNoise = smoothstep(0.0, 1.0, noiseValue);
        color.rgb /= color.a;
        vec3 hsv = rgb2hsv(color.rgb);
        hsv.y = mix(hsv.y, 0.0, oppositeNoise * uSaturateOffset);
        color.rgb = hsv2rgb(hsv);
        color.rgb += oppositeNoise * uLightOffset;
        color.a = clamp(color.a, 0.0, 1.0) * uAlphaMulti;
        color += (1.0 / 255.0) * gradientNoise(fragCoord.xy) - (0.5 / 255.0);
        return vec4(color.rgb * color.a, color.a);
    }
"""
