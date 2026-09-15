package io.github.julystar.musicapp.car.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import io.github.julystar.musicapp.car.presentation.theme.LocalCarColors
import io.github.julystar.musicapp.car.presentation.theme.LocalCarFocusVisuals

@Composable
internal fun Modifier.carInteractiveSurface(
    shape: Shape,
    selected: Boolean = false,
    playing: Boolean = false,
    enabled: Boolean = true,
    defaultColor: Color = Color.Transparent,
    showStateBackground: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val colors = LocalCarColors.current
    val focus = LocalCarFocusVisuals.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    val visualState = resolveCarSurfaceState(enabled, pressed, focused, selected, playing)
    val background = if (showStateBackground) {
        when (visualState.base) {
            CarSurfaceBaseState.Disabled -> colors.surface.copy(alpha = 0.45f)
            CarSurfaceBaseState.Pressed -> colors.surfacePressed
            CarSurfaceBaseState.Selected, CarSurfaceBaseState.Playing -> colors.surfaceSelected
            CarSurfaceBaseState.Default -> defaultColor
        }
    } else {
        defaultColor
    }
    return clip(shape)
        .background(background)
        .then(
            if (visualState.showFocusBorder) Modifier.border(focus.borderWidth, colors.focusBorder, shape)
            else Modifier
        )
        .onFocusChanged { focused = it.isFocused }
        .clickable(
            enabled = enabled,
            interactionSource = source,
            indication = null,
            onClick = onClick,
        )
        .focusable(enabled)
}

internal enum class CarSurfaceBaseState { Disabled, Pressed, Selected, Playing, Default }

internal data class CarSurfaceVisualState(
    val base: CarSurfaceBaseState,
    val showFocusBorder: Boolean,
)

internal fun resolveCarSurfaceState(
    enabled: Boolean,
    pressed: Boolean,
    focused: Boolean,
    selected: Boolean,
    playing: Boolean,
): CarSurfaceVisualState = CarSurfaceVisualState(
    base = when {
        !enabled -> CarSurfaceBaseState.Disabled
        pressed -> CarSurfaceBaseState.Pressed
        selected -> CarSurfaceBaseState.Selected
        playing -> CarSurfaceBaseState.Playing
        else -> CarSurfaceBaseState.Default
    },
    showFocusBorder = enabled && focused,
)
