package io.github.julystar.musicapp.widgets.appbar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import io.github.julystar.musicapp.core.domain.model.PlaylistSummary
import io.github.julystar.musicapp.core.presentation.media.ArtworkImage
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.julystar.musicapp.core.presentation.components.desktopSidebarSurface
import io.github.julystar.musicapp.core.presentation.components.desktopWindowBackgroundColor
import io.github.julystar.musicapp.core.presentation.platform.LocalDesktopTitleBarInset
import io.github.julystar.musicapp.core.presentation.platform.rememberPlatformWindowFocused
import io.github.julystar.musicapp.core.presentation.theme.LocalDesignIsDarkTheme
import io.github.julystar.musicapp.navigation.HomeTab
import musicapp.shared.generated.resources.Res
import musicapp.shared.generated.resources.icon_apple_albums
import musicapp.shared.generated.resources.icon_apple_artists
import musicapp.shared.generated.resources.icon_apple_favorites
import musicapp.shared.generated.resources.icon_apple_genres
import musicapp.shared.generated.resources.icon_apple_home
import musicapp.shared.generated.resources.icon_apple_playlists
import musicapp.shared.generated.resources.icon_apple_recent
import musicapp.shared.generated.resources.icon_apple_search
import musicapp.shared.generated.resources.icon_apple_settings
import musicapp.shared.generated.resources.icon_apple_songs
import musicapp.shared.generated.resources.nav_home
import musicapp.shared.generated.resources.nav_search
import musicapp.shared.generated.resources.nav_settings
import musicapp.shared.generated.resources.sidebar_albums
import musicapp.shared.generated.resources.sidebar_all_playlists
import musicapp.shared.generated.resources.sidebar_artists
import musicapp.shared.generated.resources.sidebar_favorite_songs
import musicapp.shared.generated.resources.sidebar_genres
import musicapp.shared.generated.resources.sidebar_library_section
import musicapp.shared.generated.resources.sidebar_playlists_section
import musicapp.shared.generated.resources.sidebar_recently_added
import musicapp.shared.generated.resources.sidebar_songs
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal val DesktopSidebarWidth = 208.dp

enum class DesktopSidebarDestination(
    val icon: DrawableResource,
    val label: StringResource,
    val rootTab: HomeTab? = null,
    val enabled: Boolean = true,
) {
    SEARCH(Res.drawable.icon_apple_search, Res.string.nav_search, HomeTab.SEARCH),
    HOME(Res.drawable.icon_apple_home, Res.string.nav_home, HomeTab.HOME),
    SETTINGS(Res.drawable.icon_apple_settings, Res.string.nav_settings, HomeTab.SETTINGS),
    SONGS(Res.drawable.icon_apple_songs, Res.string.sidebar_songs, HomeTab.LIBRARY),
    ALBUMS(Res.drawable.icon_apple_albums, Res.string.sidebar_albums),
    ARTISTS(Res.drawable.icon_apple_artists, Res.string.sidebar_artists),
    ALL_PLAYLISTS(Res.drawable.icon_apple_playlists, Res.string.sidebar_all_playlists),
    FAVORITES(Res.drawable.icon_apple_favorites, Res.string.sidebar_favorite_songs),
}

private val PrimaryDestinations = listOf(
    DesktopSidebarDestination.SEARCH,
    DesktopSidebarDestination.HOME,
    DesktopSidebarDestination.SETTINGS,
)
private val LibraryDestinations = listOf(
    DesktopSidebarDestination.SONGS,
    DesktopSidebarDestination.ALBUMS,
    DesktopSidebarDestination.ARTISTS,
)
private val PlaylistDestinations = listOf(
    DesktopSidebarDestination.ALL_PLAYLISTS,
    DesktopSidebarDestination.FAVORITES,
)

@Composable
internal fun DesktopNavigationSidebar(
    selectedDestination: DesktopSidebarDestination,
    onDestinationSelected: (DesktopSidebarDestination) -> Unit,
    modifier: Modifier = Modifier,
    playlists: List<PlaylistSummary> = emptyList(),
    selectedPlaylistId: Long? = null,
    onPlaylistSelected: (Long) -> Unit = {},
) {
    val titleBarInset = LocalDesktopTitleBarInset.current
    val panelShape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .width(DesktopSidebarWidth)
            .fillMaxHeight()
            .background(desktopWindowBackgroundColor()),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, top = 8.dp, bottom = 8.dp)
                .testTag("apple-music-sidebar-surface")
                .desktopSidebarSurface(panelShape),
        )
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .selectableGroup()
                .padding(top = titleBarInset + 22.dp, bottom = 8.dp),
        ) {
            DesktopDestinationGroup(
                destinations = PrimaryDestinations,
                selectedDestination = selectedDestination.takeIf { selectedPlaylistId == null },
                onDestinationSelected = onDestinationSelected,
            )
            DesktopSectionTitle(stringResource(Res.string.sidebar_library_section))
            DesktopDestinationGroup(
                destinations = LibraryDestinations,
                selectedDestination = selectedDestination.takeIf { selectedPlaylistId == null },
                onDestinationSelected = onDestinationSelected,
            )
            DesktopSectionTitle(stringResource(Res.string.sidebar_playlists_section))
            DesktopDestinationGroup(
                destinations = PlaylistDestinations,
                selectedDestination = selectedDestination.takeIf { selectedPlaylistId == null },
                onDestinationSelected = onDestinationSelected,
            )
            playlists.forEach { playlist ->
                DesktopSidebarRow(
                    label = playlist.title,
                    tag = "desktop-sidebar-playlist-${playlist.id}",
                    selected = selectedPlaylistId == playlist.id,
                    enabled = true,
                    onClick = { onPlaylistSelected(playlist.id) },
                    icon = { _ ->
                        ArtworkImage(
                            modifier = Modifier.size(24.dp).clip(RoundedCornerShape(3.dp)),
                            artwork = playlist.coverArtwork,
                            contentScale = ContentScale.Crop,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun DesktopDestinationGroup(
    destinations: List<DesktopSidebarDestination>,
    selectedDestination: DesktopSidebarDestination?,
    onDestinationSelected: (DesktopSidebarDestination) -> Unit,
) {
    destinations.forEach { destination ->
        DesktopNavigationItem(
            destination = destination,
            selected = selectedDestination == destination,
            onClick = { onDestinationSelected(destination) },
        )
    }
}

@Composable
private fun DesktopSectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(start = 24.dp, top = 15.dp),
        color = if (LocalDesignIsDarkTheme.current) {
            Color.White.copy(alpha = 0.28f)
        } else {
            Color.Black.copy(alpha = 0.30f)
        },
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun DesktopNavigationItem(
    destination: DesktopSidebarDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DesktopSidebarRow(
        label = stringResource(destination.label),
        tag = "apple-music-sidebar-${destination.name.lowercase()}",
        selected = selected,
        enabled = destination.enabled,
        onClick = onClick,
        icon = { tint ->
            Image(
                painter = painterResource(destination.icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(tint),
                modifier = Modifier.size(24.dp),
            )
        },
    )
}

@Composable
private fun DesktopSidebarRow(
    label: String,
    tag: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit,
) {
    val isDark = LocalDesignIsDarkTheme.current
    val isWindowFocused = rememberPlatformWindowFocused()
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val accent = MiuixTheme.colorScheme.primary
    val foreground = if (isDark) Color.White.copy(alpha = 0.95f) else Color.Black
    val tint = when {
        !isWindowFocused -> if (isDark) Color.White.copy(alpha = 0.38f) else Color.Black.copy(alpha = 0.32f)
        selected -> accent
        else -> foreground
    }
    val stateOverlay = if (isDark) Color.White else Color.Black
    val selectedFill = stateOverlay.copy(alpha = if (isDark) 0.08f else 0.045f)
    val hoverFill = stateOverlay.copy(alpha = if (isDark) 0.04f else 0.022f)
    val selectedHoverFill = stateOverlay.copy(alpha = if (isDark) 0.10f else 0.06f)
    val pressedFill = stateOverlay.copy(alpha = if (isDark) 0.13f else 0.085f)
    val containerColor by animateColorAsState(
        targetValue = when {
            isWindowFocused && pressed -> pressedFill
            isWindowFocused && selected && hovered -> selectedHoverFill
            selected -> selectedFill
            isWindowFocused && hovered -> hoverFill
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 90),
        label = "apple-music-sidebar-hover",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .padding(start = 18.dp, end = 10.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(containerColor)
            .testTag(tag)
            .hoverable(
                interactionSource = interactionSource,
                enabled = enabled,
            )
            .selectable(
                selected = selected,
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                role = Role.Tab,
            )
            .padding(start = 15.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon(tint)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
