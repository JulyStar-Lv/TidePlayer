package io.github.julystar.musicapp.service.playback.presentation.nowplaying

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import top.yukonga.miuix.kmp.basic.Icon
import kotlin.math.PI
import kotlin.math.sin

// Native reference circles lift each background channel by approximately 25/255.
// Additive white retains the artwork's chroma instead of washing it toward gray.
internal const val AppleMusicDesktopControlFillAlpha = 0.10f

internal fun Modifier.appleMusicDesktopControlBackground(
    alpha: Float = AppleMusicDesktopControlFillAlpha,
): Modifier = drawBehind {
    drawCircle(Color.White.copy(alpha = alpha), blendMode = BlendMode.Plus)
}

internal enum class AppleMusicDesktopTransportMotion {
    Standard,
    Shuffle,
    Repeat,
    PlayPause,
    Seek,
}

// Tuned against the desktop reference; these are not official Apple animation timings.
private const val TransportPressDurationMillis = 70

@Composable
internal fun AppleMusicDesktopTransportButton(
    painter: DrawableResource,
    contentDescription: String,
    tint: Color,
    iconSize: Dp,
    buttonWidth: Dp,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    motion: AppleMusicDesktopTransportMotion = AppleMusicDesktopTransportMotion.Standard,
    selected: Boolean? = null,
    seekDirection: Int = 0,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressProgress = remember { Animatable(0f) }
    val symbolProgress = remember { Animatable(1f) }
    var activation by remember { mutableLongStateOf(0L) }
    var animatedActivation by remember { mutableLongStateOf(0L) }
    val iconTint by animateColorAsState(
        targetValue = tint,
        animationSpec = tween(140),
        label = "desktop-transport-tint",
    )
    val selectionAlpha by animateFloatAsState(
        targetValue = if (enabled && selected == true) AppleMusicDesktopControlFillAlpha else 0f,
        animationSpec = tween(180),
        label = "desktop-transport-selection",
    )

    LaunchedEffect(enabled, activation) {
        if (!enabled || activation == 0L) {
            symbolProgress.snapTo(1f)
        } else {
            symbolProgress.snapTo(0f)
            symbolProgress.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
        }
    }

    LaunchedEffect(enabled, pressed, activation) {
        if (!enabled) {
            animatedActivation = activation
            pressProgress.snapTo(0f)
        } else if (pressed) {
            pressProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(TransportPressDurationMillis, easing = FastOutSlowInEasing),
            )
        } else {
            // A short click can start and end between frames. Finish its compression
            // before releasing, including clicks dispatched by keyboard/accessibility.
            if (activation != animatedActivation) {
                animatedActivation = activation
                if (pressProgress.value < 1f) {
                    pressProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = (TransportPressDurationMillis * (1f - pressProgress.value))
                                .toInt().coerceAtLeast(1),
                            easing = FastOutSlowInEasing,
                        ),
                    )
                }
            }
            pressProgress.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 650f),
            )
        }
    }

    Box(
        modifier = modifier
            .width(buttonWidth)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = {
                    if (enabled) {
                        activation++
                        onClick()
                    }
                },
            )
            .semantics {
                this.contentDescription = contentDescription
                if (selected != null) this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        if (selected != null) {
            Box(
                Modifier.requiredSize(buttonWidth + 2.dp)
                    .graphicsLayer {
                        val scale = 1f - 0.10f * pressProgress.value
                        scaleX = scale
                        scaleY = scale
                    }
                    .appleMusicDesktopControlBackground(selectionAlpha),
            )

        }
        AnimatedContent(
            targetState = painter,
            transitionSpec = {
                // Replace through depth instead of superimposing play and pause silhouettes.
                (fadeIn(tween(130, delayMillis = 60)) +
                    scaleIn(spring(dampingRatio = 0.8f, stiffness = 700f), initialScale = 0.35f))
                    .togetherWith(fadeOut(tween(80)) + scaleOut(tween(100), targetScale = 0.35f))
                    .using(SizeTransform(clip = false))
            },
            contentAlignment = Alignment.Center,
            label = "desktop-transport-symbol",
            modifier = Modifier
                .requiredSize(iconSize)
                .graphicsLayer {
                    val progress = if (enabled) pressProgress.value else 0f
                    val compression = if (motion == AppleMusicDesktopTransportMotion.Seek) 0.10f else 0.14f
                    scaleX = 1f - compression * progress
                    scaleY = scaleX
                    alpha = 1f - 0.24f * progress.coerceIn(0f, 1f)
                },
        ) { symbol ->
            val effectiveTint = if (enabled) iconTint else tint.copy(alpha = 0.28f)
            if (motion == AppleMusicDesktopTransportMotion.PlayPause ||
                motion == AppleMusicDesktopTransportMotion.Standard
            ) {
                Icon(
                    painter = painterResource(symbol),
                    contentDescription = null,
                    tint = effectiveTint,
                    modifier = Modifier.requiredSize(iconSize),
                )
            } else {
                TransportLayeredSymbol(symbol, effectiveTint, iconSize, motion, seekDirection) {
                    symbolProgress.value
                }
            }
        }
    }
}

@Composable
private fun TransportLayeredSymbol(
    resource: DrawableResource,
    tint: Color,
    iconSize: Dp,
    motion: AppleMusicDesktopTransportMotion,
    direction: Int,
    progress: () -> Float,
) {
    val painter = painterResource(resource)
    Canvas(Modifier.requiredSize(iconSize)) {
        val fraction = progress().coerceIn(0f, 1f)
        val filter = ColorFilter.tint(tint)
        // Match Icon's aspect-fit behavior. The mode assets are 60×44, not square;
        // stretching them to the animation canvas made their arrows too tall.
        val intrinsic = painter.intrinsicSize
        val fit = minOf(size.width / intrinsic.width, size.height / intrinsic.height)
        val symbolSize = Size(intrinsic.width * fit, intrinsic.height * fit)
        fun drawSymbol(alpha: Float = 1f) {
            withTransform({
                translate((size.width - symbolSize.width) / 2f, (size.height - symbolSize.height) / 2f)
            }) {
                with(painter) { draw(symbolSize, alpha = alpha, colorFilter = filter) }
            }
        }
        if (fraction == 0f || fraction == 1f) {
            drawSymbol()
        } else if (motion == AppleMusicDesktopTransportMotion.Seek) {
            val forward = direction >= 0
            // The exported 64px canvases have optical centers at 34px/30px and
            // two 20px triangles. Animate those layers without moving the entire button.
            val split = size.width * (if (forward) 34f else 30f) / 64f
            val travel = size.width * 20f / 64f
            val sign = if (forward) 1f else -1f
            fun triangle(leading: Boolean, translation: Float, scale: Float, alpha: Float) {
                val leftHalf = leading == forward
                val pivot = Offset(split + (if (leftHalf) -0.5f else 0.5f) * travel, center.y)
                withTransform({
                    translate(left = translation)
                    scale(scaleX = scale, scaleY = scale, pivot = pivot)
                }) {
                    clipRect(left = if (leftHalf) 0f else split, right = if (leftHalf) split else size.width) {
                        drawSymbol(alpha)
                    }
                }
            }
            val outgoing = (1f - fraction / 0.55f).coerceIn(0f, 1f)
            val incoming = ((fraction - 0.15f) / 0.85f).coerceIn(0f, 1f)
            triangle(leading = false, translation = 0f, scale = outgoing, alpha = outgoing)
            triangle(leading = true, translation = sign * travel * fraction, scale = 1f, alpha = 1f)
            triangle(leading = true, translation = 0f, scale = incoming, alpha = incoming)
        } else {
            for (half in 0..1) {
                val phase = ((fraction - half * 0.12f) / 0.88f).coerceIn(0f, 1f)
                val pulse = sin(phase * PI.toFloat())
                val sign = if (half == 0) -1f else 1f
                withTransform({
                    if (motion == AppleMusicDesktopTransportMotion.Shuffle) {
                        translate(top = sign * size.height * 0.035f * pulse)
                    } else {
                        translate(left = sign * size.width * 0.05f * pulse)
                    }
                }) {
                    clipRect(top = if (half == 0) 0f else center.y, bottom = if (half == 0) center.y else size.height) {
                        drawSymbol()
                    }
                }
            }
        }
    }
}
