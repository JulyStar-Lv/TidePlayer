package io.github.julystar.musicapp.core.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import musicapp.core.presentation.generated.resources.Res
import musicapp.core.presentation.generated.resources.icon_chevron_left
import org.jetbrains.compose.resources.painterResource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.IconButton

private val LocalDesignBackdrop = staticCompositionLocalOf<Backdrop?> { null }

val LocalDesignBottomContentInset = staticCompositionLocalOf { 0.dp }

@Immutable
data class DesignStickyHeaderState(
    val title: String,
    val subtitle: String?,
    val collapseFraction: Float,
    val onNavigateBack: (() -> Unit)? = null,
    val backContentDescription: String? = null,
    val actions: (@Composable () -> Unit)? = null,
    val compactTitle: Boolean = false,
)

interface DesignStickyHeaderStateSink {
    fun update(owner: Any, state: DesignStickyHeaderState)

    fun clear(owner: Any)
}

val LocalDesignStickyHeaderStateSink =
    staticCompositionLocalOf<DesignStickyHeaderStateSink?> { null }

@Immutable
object DesignLiquidGlassDefaults {
    const val contrast = 1.04f
    const val saturation = 1.10f
    val blurRadius = 18.dp
    val refractionHeight = 8.dp
    val refractionAmount = 14.dp
    const val depthEffect = true
    val highlightWidth = 0.25.dp
    val highlightBlurRadius = 0.5.dp
    const val highlightAlpha = 0.78f
    const val darkSurfaceAlpha = 0.24f
    const val lightSurfaceAlpha = 0.52f
    const val fallbackSurfaceAlpha = 0.90f
}

/**
 * Provides the safe, opaque fallback for glass components.
 *
 * A layer backdrop must exclude every component that samples it. This scene has a single content
 * slot, so recording it would include those components and create a recursive draw on iOS.
 */
@Composable
fun DesignGlassScene(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    CompositionLocalProvider(LocalDesignBackdrop provides null) {
        Box(
            modifier = modifier,
            content = content,
        )
    }
}

/**
 * Records [backdropContent] without any glass consumers, then lets [overlayContent] sample it.
 * Keeping the overlay outside the recorded layer avoids recursive backdrop rendering.
 */
@Composable
fun DesignGlassOverlayScene(
    modifier: Modifier = Modifier,
    contentBottomInset: Dp = 0.dp,
    captureBackdrop: Boolean = true,
    backdropContent: @Composable BoxScope.() -> Unit,
    overlayContent: @Composable BoxScope.() -> Unit,
) {
    val backdrop = rememberLayerBackdrop()

    CompositionLocalProvider(LocalDesignBottomContentInset provides contentBottomInset) {
        Box(modifier = modifier) {
            CompositionLocalProvider(LocalDesignBackdrop provides null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .then(
                            if (captureBackdrop) Modifier.layerBackdrop(backdrop) else Modifier,
                        ),
                    content = backdropContent,
                )
            }
            CompositionLocalProvider(
                LocalDesignBackdrop provides backdrop.takeIf { captureBackdrop },
            ) {
                overlayContent()
            }
        }
    }
}

/**
 * A compact ActionBar that progressively applies the shared liquid-glass treatment.
 */
@Composable
fun DesignStickyGlassActionBar(
    title: String,
    subtitle: String? = null,
    collapseFraction: Float,
    modifier: Modifier = Modifier,
    statusBarInset: Dp = 0.dp,
    onNavigateBack: (() -> Unit)? = null,
    backContentDescription: String? = null,
    actions: (@Composable () -> Unit)? = null,
    centerTitle: Boolean = false,
    compactTitle: Boolean = false,
) {
    val fraction = collapseFraction.coerceIn(0f, 1f)
    val latestOnNavigateBack = rememberUpdatedState(onNavigateBack)
    val stableOnNavigateBack: (() -> Unit)? = remember(onNavigateBack != null) {
        if (onNavigateBack == null) {
            null
        } else {
            { latestOnNavigateBack.value?.invoke() }
        }
    }
    val latestActions = rememberUpdatedState(actions)
    val stableActions: (@Composable () -> Unit)? = remember(actions != null) {
        if (actions == null) {
            null
        } else {
            { latestActions.value?.invoke() }
        }
    }
    val stateOwner = remember { Any() }
    val stateSink = LocalDesignStickyHeaderStateSink.current
    if (stateSink != null) {
        SideEffect {
            stateSink.update(
                owner = stateOwner,
                state = DesignStickyHeaderState(
                    title = title,
                    subtitle = subtitle,
                    collapseFraction = fraction,
                    onNavigateBack = stableOnNavigateBack,
                    backContentDescription = backContentDescription,
                    actions = stableActions,
                    compactTitle = compactTitle,
                ),
            )
        }
        DisposableEffect(stateSink, stateOwner) {
            onDispose { stateSink.clear(stateOwner) }
        }
        return
    }

    val adaptive = DesignTokens.adaptive
    val titleFraction = ((fraction - 0.72f) / 0.28f).coerceIn(0f, 1f)
    val actionBarTitleStyle = MiuixTheme.textStyles.title2.copy(
        fontSize = if (compactTitle) 22.sp else 24.sp,
        lineHeight = if (compactTitle) 28.sp else 30.sp,
        fontWeight = if (compactTitle) FontWeight.SemiBold else FontWeight.Bold,
    )
    val backdrop = currentDesignBackdrop()
    val glassModifier = if (backdrop != null && fraction > 0f) {
        Modifier.designLiquidGlass(
            backdrop = backdrop,
            shape = RoundedCornerShape(0.dp),
            intensity = fraction,
        )
    } else if (backdrop == null) {
        Modifier.background(MiuixTheme.colorScheme.background.copy(alpha = fraction))
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(adaptive.compactHeaderHeight + statusBarInset)
            .then(glassModifier),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(adaptive.compactHeaderHeight),
            contentAlignment = Alignment.Center,
        ) {
            if (stableOnNavigateBack != null) {
                SmallTopAppBar(
                    title = title,
                    modifier = Modifier.alpha(titleFraction),
                    defaultWindowInsetsPadding = false,
                    navigationIcon = {
                        IconButton(
                            onClick = stableOnNavigateBack,
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.icon_chevron_left),
                                contentDescription = backContentDescription,
                            )
                        }
                    },
                    actions = { stableActions?.invoke() },
                )
            } else {
                TopAppBar(
                    title = title,
                    subtitle = subtitle.orEmpty(),
                    largeTitle = title,
                    defaultWindowInsetsPadding = false,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .alpha(titleFraction),
                )
            }
        }
    }
}

@Composable
internal fun currentDesignBackdrop(): Backdrop? = LocalDesignBackdrop.current

@Composable
fun Modifier.designLiquidGlass(
    backdrop: Backdrop,
    shape: Shape,
    intensity: Float = 1f,
): Modifier {
    val fraction = intensity.coerceIn(0f, 1f)
    if (fraction == 0f) return this

    val defaults = DesignLiquidGlassDefaults
    val surface = MiuixTheme.colorScheme.surfaceContainer
    val surfaceAlpha = if (MiuixTheme.colorScheme.background.luminance() < 0.5f) {
        defaults.darkSurfaceAlpha
    } else {
        defaults.lightSurfaceAlpha
    }
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            colorControls(
                contrast = 1f + (defaults.contrast - 1f) * fraction,
                saturation = 1f + (defaults.saturation - 1f) * fraction,
            )
            blur((defaults.blurRadius * fraction).toPx())
            lens(
                refractionHeight = (defaults.refractionHeight * fraction).toPx(),
                refractionAmount = (defaults.refractionAmount * fraction).toPx(),
                depthEffect = defaults.depthEffect,
            )
        },
        highlight = {
            Highlight(
                width = defaults.highlightWidth,
                blurRadius = defaults.highlightBlurRadius,
                alpha = defaults.highlightAlpha * fraction,
            )
        },
        shadow = { null },
        onDrawSurface = {
            drawRect(surface.copy(alpha = surfaceAlpha * fraction))
        },
    )
}
