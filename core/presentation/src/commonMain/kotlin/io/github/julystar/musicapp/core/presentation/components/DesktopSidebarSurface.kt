package io.github.julystar.musicapp.core.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import io.github.julystar.musicapp.core.presentation.theme.LocalDesignIsDarkTheme

// macOS 26 windowBackgroundColor; shared by the sidebar underlay and content pane.
@Composable
fun desktopWindowBackgroundColor(): Color =
    if (LocalDesignIsDarkTheme.current) Color(0xFF1E1E1E) else Color.White

/**
 * Neutral approximation of MeloX's system-owned sidebarAdaptable material.
 * It samples rendered content while staying independent of theme-derived tint and refraction.
 */
@Composable
fun Modifier.desktopSidebarSurface(shape: Shape): Modifier {
    val isDark = LocalDesignIsDarkTheme.current
    val isWindowFocused = LocalWindowInfo.current.isWindowFocused
    val backdrop = currentDesignBackdrop()
    val surface = when {
        isDark && isWindowFocused -> Color(0xFF1D1D1D)
        isDark -> Color(0xFF222222)
        isWindowFocused -> Color(0xFFF9F9F9)
        else -> Color(0xFFF5F5F5)
    }
    val material = dropShadow(
        shape = shape,
        shadow = Shadow(
            radius = if (isWindowFocused) 24.dp else 18.dp,
            offset = DpOffset(0.dp, if (isWindowFocused) 4.dp else 2.dp),
            color = Color.Black.copy(
                alpha = when {
                    isDark && isWindowFocused -> 0.24f
                    isDark -> 0.16f
                    isWindowFocused -> 0.10f
                    else -> 0.06f
                },
            ),
        ),
    )
    return (if (backdrop != null) {
        material.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                colorControls(saturation = 1.18f)
                blur(132.dp.toPx())
            },
            highlight = { null },
            shadow = { null },
            onDrawSurface = { drawRect(surface.copy(alpha = 0.78f)) },
        )
    } else {
        material.clip(shape).background(
            surface.copy(alpha = 0.78f).compositeOver(desktopWindowBackgroundColor()),
        )
    }).border(
        width = 0.5.dp,
        color = Color.White.copy(
            alpha = when {
                isDark && isWindowFocused -> 0.09f
                isDark -> 0.06f
                isWindowFocused -> 0.85f
                else -> 0.58f
            },
        ),
        shape = shape,
    )
}
