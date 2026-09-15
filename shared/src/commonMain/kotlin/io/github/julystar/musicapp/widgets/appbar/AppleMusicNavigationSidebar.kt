package io.github.julystar.musicapp.widgets.appbar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.julystar.musicapp.core.presentation.components.liquidGlassSurface
import io.github.julystar.musicapp.core.presentation.platform.LocalDesktopTitleBarInset
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

internal val AppleMusicSidebarWidth = 208.dp

enum class AppleMusicSidebarDestination(
    val icon: DrawableResource,
    val label: StringResource,
    val rootTab: HomeTab? = null,
    val enabled: Boolean = true,
) {
    SEARCH(Res.drawable.icon_apple_search, Res.string.nav_search, HomeTab.SEARCH),
    HOME(Res.drawable.icon_apple_home, Res.string.nav_home, HomeTab.HOME),
    SETTINGS(Res.drawable.icon_apple_settings, Res.string.nav_settings, HomeTab.SETTINGS),
    RECENTLY_ADDED(Res.drawable.icon_apple_recent, Res.string.sidebar_recently_added),
    SONGS(Res.drawable.icon_apple_songs, Res.string.sidebar_songs, HomeTab.LIBRARY),
    ALBUMS(Res.drawable.icon_apple_albums, Res.string.sidebar_albums),
    ARTISTS(Res.drawable.icon_apple_artists, Res.string.sidebar_artists),
    GENRES(Res.drawable.icon_apple_genres, Res.string.sidebar_genres),
    ALL_PLAYLISTS(Res.drawable.icon_apple_playlists, Res.string.sidebar_all_playlists),
    FAVORITES(Res.drawable.icon_apple_favorites, Res.string.sidebar_favorite_songs),
}

private val PrimaryDestinations = listOf(
    AppleMusicSidebarDestination.SEARCH,
    AppleMusicSidebarDestination.HOME,
    AppleMusicSidebarDestination.SETTINGS,
)
private val LibraryDestinations = listOf(
    AppleMusicSidebarDestination.RECENTLY_ADDED,
    AppleMusicSidebarDestination.SONGS,
    AppleMusicSidebarDestination.ALBUMS,
    AppleMusicSidebarDestination.ARTISTS,
    AppleMusicSidebarDestination.GENRES,
)
private val PlaylistDestinations = listOf(
    AppleMusicSidebarDestination.ALL_PLAYLISTS,
    AppleMusicSidebarDestination.FAVORITES,
)

@Composable
internal fun AppleMusicNavigationSidebar(
    selectedDestination: AppleMusicSidebarDestination,
    onDestinationSelected: (AppleMusicSidebarDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleBarInset = LocalDesktopTitleBarInset.current
    val isDark = LocalDesignIsDarkTheme.current
    val sidebarBase = if (isDark) Color(0xFF2A2A2A) else Color.White
    val panelShape = RoundedCornerShape(18.dp)
    val panelBrush = if (isDark) {
        Brush.horizontalGradient(
            listOf(Color(0xFF252828).copy(alpha = 0.65f), Color(0xFF373031).copy(alpha = 0.65f)),
        )
    } else {
        Brush.horizontalGradient(
            listOf(Color(0xFFFAF9F9), Color(0xFFF9F9F9)),
        )
    }
    val panelBorder = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.10f)
    Box(
        modifier = modifier
            .width(AppleMusicSidebarWidth)
            .fillMaxHeight()
            .background(sidebarBase),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, top = 8.dp, bottom = 8.dp)
                .liquidGlassSurface(panelShape, intensity = 0.62f)
                .background(panelBrush, panelShape)
                .border(0.5.dp, panelBorder, panelShape),
        )
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .selectableGroup()
                .padding(top = titleBarInset + 22.dp, bottom = 8.dp),
        ) {
            AppleMusicDestinationGroup(
                destinations = PrimaryDestinations,
                selectedDestination = selectedDestination,
                onDestinationSelected = onDestinationSelected,
            )
            AppleMusicSectionTitle(stringResource(Res.string.sidebar_library_section))
            AppleMusicDestinationGroup(
                destinations = LibraryDestinations,
                selectedDestination = selectedDestination,
                onDestinationSelected = onDestinationSelected,
            )
            AppleMusicSectionTitle(stringResource(Res.string.sidebar_playlists_section))
            AppleMusicDestinationGroup(
                destinations = PlaylistDestinations,
                selectedDestination = selectedDestination,
                onDestinationSelected = onDestinationSelected,
            )
        }
    }
}

@Composable
private fun AppleMusicDestinationGroup(
    destinations: List<AppleMusicSidebarDestination>,
    selectedDestination: AppleMusicSidebarDestination,
    onDestinationSelected: (AppleMusicSidebarDestination) -> Unit,
) {
    destinations.forEach { destination ->
        AppleMusicNavigationItem(
            destination = destination,
            selected = selectedDestination == destination,
            onClick = { onDestinationSelected(destination) },
        )
    }
}

@Composable
private fun AppleMusicSectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(start = 24.dp, top = 15.dp),
        color = if (LocalDesignIsDarkTheme.current) {
            Color.White.copy(alpha = 0.42f)
        } else {
            Color(0xFF7B7B7B)
        },
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun AppleMusicNavigationItem(
    destination: AppleMusicSidebarDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val isDark = LocalDesignIsDarkTheme.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val accent = if (isDark) {
        Color(0xFFFF375F)
    } else {
        Color(
            red = 243f / 255f,
            green = 27f / 255f,
            blue = 52f / 255f,
            alpha = 1f,
            colorSpace = ColorSpaces.DisplayP3,
        )
    }
    val foreground = if (isDark) Color.White.copy(alpha = 0.96f) else Color(0xFF1A1919)
    val tint = when {
        selected -> accent
        else -> foreground
    }
    val selectedFill = if (isDark) Color.White.copy(alpha = 0.055f) else Color(0xFFF0EFEF)
    val hoverFill = if (isDark) Color.White.copy(alpha = 0.035f) else Color(0xFFF5F4F4)
    val selectedHoverFill = if (isDark) Color.White.copy(alpha = 0.072f) else Color(0xFFEDECEC)
    val pressedFill = if (isDark) Color.White.copy(alpha = 0.085f) else Color(0xFFEAE9E9)
    val containerColor by animateColorAsState(
        targetValue = when {
            pressed -> pressedFill
            selected && hovered -> selectedHoverFill
            selected -> selectedFill
            hovered -> hoverFill
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
            .testTag("apple-music-sidebar-${destination.name.lowercase()}")
            .hoverable(
                interactionSource = interactionSource,
                enabled = destination.enabled,
            )
            .selectable(
                selected = selected,
                enabled = destination.enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                role = Role.Tab,
            )
            .padding(start = 15.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(destination.icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(destination.label),
            color = tint,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}
