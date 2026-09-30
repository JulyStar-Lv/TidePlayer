package io.github.julystar.musicapp.core.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.presentation.theme.LocalDesignIsDarkTheme
import musicapp.core.presentation.generated.resources.Res as CoreRes
import musicapp.core.presentation.generated.resources.icon_chevron_left
import org.jetbrains.compose.resources.painterResource
import top.yukonga.miuix.kmp.basic.Icon

@Composable
fun DesktopBackButton(description: String, onClick: () -> Unit) {
    val isDark = LocalDesignIsDarkTheme.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val background = if (!pressed && !hovered) {
        if (isDark) Color.White.copy(alpha = 0.075f) else Color.White.copy(alpha = 0.55f)
    } else if (isDark) {
        Color.White.copy(alpha = if (pressed) 0.14f else 0.10f)
    } else {
        Color.Black.copy(alpha = if (pressed) 0.13f else 0.085f)
    }
    val shadow = if (isDark) Modifier else Modifier
        .dropShadow(
            shape = CircleShape,
            shadow = Shadow(radius = 36.dp, offset = DpOffset(0.dp, 6.dp), color = Color.Black.copy(alpha = 0.04f)),
        )
        .dropShadow(
            shape = CircleShape,
            shadow = Shadow(radius = 16.dp, offset = DpOffset(0.dp, 5.dp), color = Color.Black.copy(alpha = 0.05f)),
        )
    Box(
        modifier = Modifier
            .size(36.dp)
            .then(shadow)
            .clip(CircleShape)
            .background(background)
            .border(0.5.dp, if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.86f), CircleShape)
            .hoverable(interaction)
            .semantics { contentDescription = description }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(CoreRes.drawable.icon_chevron_left),
            null,
            tint = if (isDark) Color.White.copy(alpha = 0.86f) else Color.Black.copy(alpha = 0.85f),
            modifier = Modifier.size(15.dp).offset(x = (-1.5).dp),
        )
    }
}
