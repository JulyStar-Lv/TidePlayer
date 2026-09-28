package io.github.julystar.musicapp.core.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import io.github.julystar.musicapp.core.presentation.theme.LocalDesignIsDarkTheme
import musicapp.core.presentation.generated.resources.Res
import musicapp.core.presentation.generated.resources.icon_chevron_left
import org.jetbrains.compose.resources.painterResource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import top.yukonga.miuix.kmp.basic.IconButton

private val LocalDesignBackdrop = staticCompositionLocalOf<Backdrop?> { null }

val LocalDesignBottomContentInset = staticCompositionLocalOf { 0.dp }

@Immutable
data class StickyHeaderState(
    val title: String,
    val subtitle: String?,
    val collapseFraction: Float,
    val onNavigateBack: (() -> Unit)? = null,
    val backContentDescription: String? = null,
    val actions: (@Composable () -> Unit)? = null,
    val compactTitle: Boolean = false,
    val navigationIcon: (@Composable () -> Unit)? = null,
    val content: (@Composable () -> Unit)? = null,
    val extraContentHeight: Dp = 0.dp,
    val transitionKey: String = title,
    val isNavigationTarget: Boolean = true,
    val transitionDurationMillis: Int = 0,
)

@Immutable
data class StickyHeaderTransitionContext(
    val key: String,
    val isNavigationTarget: Boolean,
    val durationMillis: Int,
)

interface StickyHeaderStateSink {
    fun update(owner: Any, state: StickyHeaderState)

    fun clear(owner: Any)
}

val LocalDesignStickyHeaderStateSink =
    staticCompositionLocalOf<StickyHeaderStateSink?> { null }

val LocalStickyHeaderTransitionContext =
    staticCompositionLocalOf<StickyHeaderTransitionContext?> { null }

@Immutable
object LiquidGlassDefaults {
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
fun LiquidGlassScene(
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
fun LiquidGlassOverlayScene(
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

/** Shared fixed-height app bar; buttons remain supplied by each screen. */
@Composable
fun DesignTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    LiquidGlassActionBar(
        title = title,
        subtitle = subtitle.takeIf { it.isNotEmpty() },
        collapseFraction = 1f,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = { Row(verticalAlignment = Alignment.CenterVertically, content = actions) },
    )
}

/**
 * A fixed-height, left-aligned app bar with the shared liquid-glass treatment.
 */
@Composable
fun LiquidGlassActionBar(
    title: String,
    subtitle: String? = null,
    collapseFraction: Float,
    modifier: Modifier = Modifier,
    statusBarInset: Dp = 0.dp,
    contentStartInset: Dp = 0.dp,
    onNavigateBack: (() -> Unit)? = null,
    backContentDescription: String? = null,
    actions: (@Composable () -> Unit)? = null,
    centerTitle: Boolean = false,
    compactTitle: Boolean = false,
    navigationIcon: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
    extraContentHeight: Dp = 0.dp,
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
    val latestNavigationIcon = rememberUpdatedState(navigationIcon)
    val stableNavigationIcon: (@Composable () -> Unit)? = remember(navigationIcon != null) {
        if (navigationIcon == null) null else { { latestNavigationIcon.value?.invoke() } }
    }
    val latestContent = rememberUpdatedState(content)
    val stableContent: (@Composable () -> Unit)? = remember(content != null) {
        if (content == null) null else { { latestContent.value?.invoke() } }
    }
    val stateOwner = remember { Any() }
    val stateSink = LocalDesignStickyHeaderStateSink.current
    val transitionContext = LocalStickyHeaderTransitionContext.current
    if (stateSink != null) {
        SideEffect {
            stateSink.update(
                owner = stateOwner,
                state = StickyHeaderState(
                    title = title,
                    subtitle = subtitle,
                    collapseFraction = fraction,
                    onNavigateBack = stableOnNavigateBack,
                    backContentDescription = backContentDescription,
                    actions = stableActions,
                    compactTitle = compactTitle,
                    navigationIcon = stableNavigationIcon,
                    content = stableContent,
                    extraContentHeight = extraContentHeight,
                    transitionKey = transitionContext?.key ?: title,
                    isNavigationTarget = transitionContext?.isNavigationTarget ?: true,
                    transitionDurationMillis = transitionContext?.durationMillis ?: 0,
                ),
            )
        }
        DisposableEffect(stateSink, stateOwner) {
            onDispose { stateSink.clear(stateOwner) }
        }
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(DesignTokens.adaptive.compactHeaderHeight + extraContentHeight + statusBarInset),
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(DesignTokens.adaptive.compactHeaderHeight + extraContentHeight + statusBarInset)
            .designTopAppBarSurface(),
    ) {
        if (stableContent != null) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(start = contentStartInset)
                .height(DesignTokens.adaptive.compactHeaderHeight + extraContentHeight)) {
                stableContent()
            }
            return
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = contentStartInset)
                .height(DesignTokens.adaptive.compactHeaderHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (stableNavigationIcon != null || stableOnNavigateBack != null) {
                Box(Modifier.padding(start = TopAppBarDefaults.NavigationIconPadding)) {
                    if (stableNavigationIcon != null) {
                        stableNavigationIcon()
                    } else {
                        IconButton(onClick = { stableOnNavigateBack?.invoke() }) {
                            Icon(
                                painter = painterResource(Res.drawable.icon_chevron_left),
                                contentDescription = backContentDescription,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = TopAppBarDefaults.TitlePadding),
            ) {
                Text(
                    text = title,
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = MiuixTheme.textStyles.title3.fontSize,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrEmpty()) {
                    Text(
                        text = subtitle,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.footnote1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(Modifier.padding(end = TopAppBarDefaults.ActionIconPadding)) {
                stableActions?.invoke()
            }
        }
    }
}

/** Shared backdrop treatment for standard and desktop app bars. */
@Composable
fun Modifier.designTopAppBarSurface(): Modifier {
    val backdrop = currentDesignBackdrop()
    val surface = MiuixTheme.colorScheme.surfaceContainer
    val surfaceAlpha = if (LocalDesignIsDarkTheme.current) {
        LiquidGlassDefaults.darkSurfaceAlpha
    } else {
        LiquidGlassDefaults.lightSurfaceAlpha
    }
    val material = if (backdrop != null) {
        drawBackdrop(
            backdrop = backdrop,
            shape = { RoundedCornerShape(0.dp) },
            effects = { blur(LiquidGlassDefaults.blurRadius.toPx()) },
            highlight = { null },
            shadow = { null },
            onDrawSurface = { drawRect(surface.copy(alpha = surfaceAlpha)) },
        )
    } else {
        background(surface.copy(alpha = LiquidGlassDefaults.fallbackSurfaceAlpha))
    }
    return material.pointerInput(Unit) { detectTapGestures { } }
}

@Composable
internal fun currentDesignBackdrop(): Backdrop? = LocalDesignBackdrop.current

/** Frosted menu material without the lens distortion used by liquid-glass controls. */
@Composable
fun Modifier.frostedMenuSurface(shape: Shape, backdropOverride: Backdrop? = null): Modifier {
    val dark = LocalDesignIsDarkTheme.current
    val tint = if (dark) Color(0xFF25251F) else Color(0xFFF8F8F8)
    val backdrop = backdropOverride ?: currentDesignBackdrop()
    return if (backdrop != null) {
        drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = { blur(24.dp.toPx()) },
            highlight = { null },
            shadow = { null },
            onDrawSurface = { drawRect(tint.copy(alpha = if (dark) 0.76f else 0.82f)) },
        )
    } else {
        clip(shape).background(tint.copy(alpha = 0.98f))
    }
}

/**
 * Applies the shared liquid-glass treatment when this surface is hosted by a captured scene.
 * Platforms or layouts without a captured backdrop keep the existing opaque fallback.
 */
@Composable
fun Modifier.liquidGlassSurface(
    shape: Shape,
    intensity: Float = 1f,
    surfaceColor: Color? = null,
    surfaceAlphaScale: Float = 1f,
    vibrant: Boolean = false,
    showHighlight: Boolean = true,
): Modifier {
    val fraction = intensity.coerceIn(0f, 1f)
    val alphaScale = surfaceAlphaScale.coerceIn(0f, 1f)
    val backdrop = currentDesignBackdrop()
    return if (backdrop != null) {
        designLiquidGlass(
            backdrop = backdrop,
            shape = shape,
            intensity = fraction,
            surfaceColor = surfaceColor,
            surfaceAlphaScale = alphaScale,
            vibrant = vibrant,
            showHighlight = showHighlight,
        )
    } else {
        clip(shape)
            .background(
                (surfaceColor ?: MiuixTheme.colorScheme.surfaceContainer).copy(
                    alpha = LiquidGlassDefaults.fallbackSurfaceAlpha * fraction * alphaScale,
                ),
            )
    }
}

@Composable
fun Modifier.designLiquidGlass(
    backdrop: Backdrop,
    shape: Shape,
    intensity: Float = 1f,
    surfaceColor: Color? = null,
    surfaceAlphaScale: Float = 1f,
    vibrant: Boolean = false,
    showHighlight: Boolean = true,
): Modifier {
    val fraction = intensity.coerceIn(0f, 1f)
    val alphaScale = surfaceAlphaScale.coerceIn(0f, 1f)
    if (fraction == 0f) return this

    val defaults = LiquidGlassDefaults
    val surface = surfaceColor ?: MiuixTheme.colorScheme.surfaceContainer
    val surfaceAlpha = if (MiuixTheme.colorScheme.background.luminance() < 0.5f) {
        defaults.darkSurfaceAlpha
    } else {
        defaults.lightSurfaceAlpha
    }
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            if (vibrant) {
                vibrancy()
                blur((8.dp * fraction).toPx())
                lens(
                    refractionHeight = (14.dp * fraction).toPx(),
                    refractionAmount = (28.dp * fraction).toPx(),
                    depthEffect = true,
                )
            } else {
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
            }
        },
        highlight = {
            if (!showHighlight) {
                null
            } else if (vibrant) {
                Highlight.Plain.copy(alpha = fraction)
            } else {
                Highlight(
                    width = defaults.highlightWidth,
                    blurRadius = defaults.highlightBlurRadius,
                    alpha = defaults.highlightAlpha * fraction,
                )
            }
        },
        shadow = { null },
        onDrawSurface = {
            drawRect(surface.copy(alpha = surfaceAlpha * fraction * alphaScale))
        },
    )
}
