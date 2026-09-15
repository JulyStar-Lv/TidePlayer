package io.github.julystar.musicapp.car.presentation.component

import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.car.presentation.icon.CarIcon
import io.github.julystar.musicapp.car.presentation.icon.CarIcon as IconView
import io.github.julystar.musicapp.car.presentation.theme.LocalCarColors
import io.github.julystar.musicapp.car.presentation.theme.LocalCarShapes
import io.github.julystar.musicapp.car.presentation.theme.LocalCarSpacing
import io.github.julystar.musicapp.car.presentation.theme.LocalCarTypography
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.domain.repository.ArtworkRepository
import io.github.julystar.musicapp.service.playback.domain.PlayerState
import io.github.julystar.musicapp.service.playback.domain.PlaybackStatus

@Composable
fun CarMiniPlayer(
    state: PlayerState,
    artworkRepository: ArtworkRepository,
    height: Dp,
    controlSize: Dp,
    compact: Boolean = false,
    artist: String? = null,
    onOpen: () -> Unit,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCarColors.current
    val shapes = LocalCarShapes.current
    val spacing = LocalCarSpacing.current
    val item = state.currentItem
    val displayArtist = artist?.takeIf(String::isNotBlank) ?: item?.artist.orEmpty()
    val artworkSize = height * (if (compact) 112f else 72f) / 164f
    if (compact) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .height(height)
                .clip(shapes.panel)
                .border(1.dp, colors.borderDefault, shapes.panel)
                .carInteractiveSurface(shapes.panel, enabled = item != null, onClick = onOpen),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CarArtwork(
                    artwork = item?.libraryTrackId?.let { Artwork.LibraryTrack(it, true) },
                    repository = artworkRepository,
                    size = artworkSize,
                    shape = shapes.artwork,
                )
                BasicText(
                    text = displayArtist,
                    style = LocalCarTypography.current.supporting.copy(
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(top = spacing.small),
                )
            }
        }
        return
    }
    Column(
        modifier = modifier
            .height(height)
            .clip(shapes.panel)
            .border(1.dp, colors.borderDefault, shapes.panel)
            .carInteractiveSurface(shapes.panel, enabled = item != null, onClick = onOpen)
            .padding(spacing.compact),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CarArtwork(
                artwork = item?.libraryTrackId?.let { Artwork.LibraryTrack(it, true) },
                repository = artworkRepository,
                size = artworkSize,
                shape = shapes.artwork,
            )
            Spacer(Modifier.width(spacing.compact))
            Column(Modifier.weight(1f)) {
                BasicText(
                    text = item?.title ?: "尚未播放",
                    style = LocalCarTypography.current.body.copy(
                        color = colors.textPrimary,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                BasicText(
                    text = displayArtist,
                    style = LocalCarTypography.current.supporting.copy(color = colors.textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                MiniControl(CarIcon.Previous, "上一首", controlSize, item != null, onPrevious)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                MiniControl(
                    if (state.status == PlaybackStatus.Playing) CarIcon.Pause else CarIcon.Play,
                    if (state.status == PlaybackStatus.Playing) "暂停" else "播放",
                    controlSize,
                    item != null,
                    onToggle,
                )
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                MiniControl(CarIcon.Next, "下一首", controlSize, item != null, onNext)
            }
        }
    }
}

@Composable
private fun MiniControl(icon: CarIcon, description: String, size: Dp, enabled: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size).carInteractiveSurface(
            LocalCarShapes.current.control,
            enabled = enabled,
            onClick = onClick,
        ),
    ) {
        IconView(
            icon = icon,
            contentDescription = description,
            tint = if (enabled) LocalCarColors.current.textPrimary else LocalCarColors.current.textDisabled,
            modifier = Modifier.size(size),
        )
    }
}
