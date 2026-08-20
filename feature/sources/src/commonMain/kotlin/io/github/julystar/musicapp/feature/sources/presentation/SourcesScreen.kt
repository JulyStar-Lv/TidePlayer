package io.github.julystar.musicapp.feature.sources.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.presentation.components.DesignCardSurface
import io.github.julystar.musicapp.core.presentation.components.DesignChevron
import io.github.julystar.musicapp.core.presentation.components.DesignChevronDirection
import io.github.julystar.musicapp.core.presentation.components.DesignChip
import io.github.julystar.musicapp.core.presentation.components.DesignStatusBadge
import io.github.julystar.musicapp.core.presentation.components.DesignStatusTone
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import musicapp.feature.sources.generated.resources.Res
import musicapp.feature.sources.generated.resources.dashboard_devices_add
import musicapp.feature.sources.generated.resources.icon_cloud
import musicapp.feature.sources.generated.resources.icon_plus
import musicapp.feature.sources.generated.resources.sources_configured
import musicapp.feature.sources.generated.resources.sources_default_library
import musicapp.feature.sources.generated.resources.sources_logs
import musicapp.feature.sources.generated.resources.sources_music
import musicapp.feature.sources.generated.resources.sources_settings
import musicapp.feature.sources.generated.resources.sources_storage
import musicapp.feature.sources.generated.resources.sources_sync
import musicapp.feature.sources.generated.resources.sources_syncing
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SourcesScreen(
    state: SourcesState,
    onAction: (SourcesAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = DesignTokens.spacing
    BoxWithConstraints(modifier = modifier) {
        val horizontalPadding = if (maxWidth < 600.dp) spacing.pageCompact else spacing.pageMedium
        Column(
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.sources.isEmpty()) {
                EmptySourcesCard(onClick = { onAction(SourcesAction.AddSource) })
                return@Column
            }
            state.sources.forEach { source ->
                SourceCard(
                    source = source,
                    onClick = { onAction(SourcesAction.OpenSource(source.id)) },
                    onSync = { onAction(SourcesAction.SyncSource(source.id)) },
                )
            }
        }
    }
}

@Composable
private fun EmptySourcesCard(onClick: () -> Unit) {
    DesignCardSurface(
        modifier = Modifier.height(96.dp),
        contentPadding = PaddingValues(0.dp),
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.Center),
        ) {
            Icon(
                modifier = Modifier.size(12.dp),
                painter = painterResource(Res.drawable.icon_plus),
                contentDescription = stringResource(Res.string.dashboard_devices_add),
            )
            Box(modifier = Modifier.size(4.dp))
            Text(
                text = stringResource(Res.string.dashboard_devices_add),
                textAlign = TextAlign.Center,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SourceCard(
    source: SourceAccountUi,
    onClick: () -> Unit,
    onSync: () -> Unit,
) {
    val shapes = DesignTokens.shapes
    DesignCardSurface(
        contentPadding = PaddingValues(0.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.height(164.dp))
            Box(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .size(48.dp)
                    .clip(RoundedCornerShape(shapes.md))
                    .background(MiuixTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    painter = painterResource(Res.drawable.icon_cloud),
                    tint = MiuixTheme.colorScheme.primary,
                    contentDescription = source.sourceType,
                )
            }
            Box(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DesignStatusBadge(
                        label = stringResource(Res.string.sources_configured),
                        tone = DesignStatusTone.Success,
                    )
                    Text(
                        text = source.sourceType,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                    )
                }
                Text(
                    text = source.title,
                    style = MiuixTheme.textStyles.title3,
                    color = MiuixTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        Res.string.sources_storage,
                        source.subtitle.ifBlank { stringResource(Res.string.sources_default_library) },
                    ),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(Res.string.sources_music, source.musicCount),
                    color = MiuixTheme.colorScheme.primary,
                    style = MiuixTheme.textStyles.footnote1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                SourceActionStrip(source = source, onSync = onSync)
            }
            DesignChevron(
                direction = DesignChevronDirection.Right,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun SourceActionStrip(
    source: SourceAccountUi,
    onSync: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DesignChip(
            label = stringResource(
                if (source.isSyncing) Res.string.sources_syncing else Res.string.sources_sync,
            ),
            selected = source.isSyncing,
            enabled = source.syncEnabled && !source.isSyncing,
            onClick = onSync,
        )
        DesignChip(
            label = stringResource(Res.string.sources_logs),
            enabled = false,
        )
        DesignChip(
            label = stringResource(Res.string.sources_settings),
            selected = true,
        )
    }
}
