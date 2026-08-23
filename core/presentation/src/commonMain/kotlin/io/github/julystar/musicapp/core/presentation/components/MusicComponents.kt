package io.github.julystar.musicapp.core.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.presentation.theme.DesignFontFamilies
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

// --- MediaSkeleton ---
// Shimmer placeholder for loading music content rows.

@Composable
fun MediaSkeleton(
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        repeat(6) {
            MediaSkeletonRow()
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun MediaSkeletonRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBlock(
            width = 48.dp,
            height = 48.dp,
            radius = 6.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            SkeletonBlock(
                height = 14.dp,
                widthFraction = 0.7f,
                radius = 4.dp,
            )
            Spacer(Modifier.height(6.dp))
            SkeletonBlock(
                height = 12.dp,
                widthFraction = 0.5f,
                radius = 4.dp,
            )
        }
        Spacer(Modifier.width(8.dp))
        SkeletonBlock(
            width = 40.dp,
            height = 12.dp,
            radius = 4.dp,
        )
    }
}

@Composable
fun TrackNumberBadge(
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    val shape = RoundedCornerShape(DesignTokens.shapes.full)
    val backgroundBrush = if (active) {
        Brush.linearGradient(
            listOf(
                MiuixTheme.colorScheme.primary,
                MiuixTheme.colorScheme.secondary,
            ),
        )
    } else {
        Brush.linearGradient(
            listOf(
                MiuixTheme.colorScheme.secondaryContainer,
                MiuixTheme.colorScheme.secondaryContainer,
            ),
        )
    }

    Box(
        modifier = modifier
            .size(40.dp)
            .clip(shape)
            .background(backgroundBrush),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.ifBlank { "--" },
            color = if (active) {
                MiuixTheme.colorScheme.onPrimary
            } else {
                MiuixTheme.colorScheme.onSecondaryContainer
            },
            style = MiuixTheme.textStyles.footnote1.copy(fontFamily = DesignFontFamilies.Mono),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
