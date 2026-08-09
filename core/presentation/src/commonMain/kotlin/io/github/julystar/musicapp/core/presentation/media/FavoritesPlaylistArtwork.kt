package io.github.julystar.musicapp.core.presentation.media

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import musicapp.core.presentation.generated.resources.Res
import musicapp.core.presentation.generated.resources.icon_heart_filled
import org.jetbrains.compose.resources.painterResource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun FavoritesPlaylistArtwork(
    size: Dp,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val primary = MiuixTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MiuixTheme.colorScheme.tertiaryContainer,
                        MiuixTheme.colorScheme.surfaceVariant,
                    ),
                ),
            )
            .border(1.dp, primary.copy(alpha = 0.16f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(Res.drawable.icon_heart_filled),
            contentDescription = null,
            tint = primary.copy(alpha = 0.14f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = size * (10f / 56f), y = size * (10f / 56f))
                .size(size * (54f / 56f)),
        )
        Box(
            modifier = Modifier
                .size(size * (32f / 56f))
                .clip(CircleShape)
                .background(primary.copy(alpha = 0.14f))
                .border(1.dp, primary.copy(alpha = 0.26f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(Res.drawable.icon_heart_filled),
                contentDescription = null,
                tint = primary,
                modifier = Modifier.size(size * (18f / 56f)),
            )
        }
    }
}
