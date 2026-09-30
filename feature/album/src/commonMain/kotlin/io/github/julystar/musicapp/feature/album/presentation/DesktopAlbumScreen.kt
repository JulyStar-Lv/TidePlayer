@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package io.github.julystar.musicapp.feature.album.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.julystar.musicapp.core.presentation.components.LiquidGlassOverlayScene
import io.github.julystar.musicapp.core.presentation.components.LiquidGlassActionBar
import io.github.julystar.musicapp.core.presentation.components.DesktopBackButton
import io.github.julystar.musicapp.core.presentation.components.DesktopPlaybackActionPill
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import io.github.julystar.musicapp.core.presentation.media.ArtworkImage
import io.github.julystar.musicapp.core.presentation.platform.rememberPlatformWindowFocused
import io.github.julystar.musicapp.core.presentation.theme.LocalDesignIsDarkTheme
import musicapp.core.presentation.generated.resources.Res as CoreRes
import musicapp.core.presentation.generated.resources.icon_player_download
import musicapp.core.presentation.generated.resources.icon_player_more
import musicapp.core.presentation.generated.resources.icon_player_search
import musicapp.core.presentation.generated.resources.icon_download
import musicapp.core.presentation.generated.resources.icon_pause
import musicapp.core.presentation.generated.resources.icon_play
import musicapp.core.presentation.generated.resources.icon_search
import musicapp.core.presentation.generated.resources.icon_shuffle
import musicapp.core.presentation.generated.resources.icon_star
import musicapp.core.presentation.generated.resources.icon_star_filled
import musicapp.feature.album.generated.resources.Res
import musicapp.feature.album.generated.resources.album_add_favorite
import musicapp.feature.album.generated.resources.album_add_favorite_menu
import musicapp.feature.album.generated.resources.album_back
import musicapp.feature.album.generated.resources.album_default_title
import musicapp.feature.album.generated.resources.album_detail_summary
import musicapp.feature.album.generated.resources.album_download
import musicapp.feature.album.generated.resources.album_duration_hours
import musicapp.feature.album.generated.resources.album_duration_hours_minutes
import musicapp.feature.album.generated.resources.album_duration_minutes
import musicapp.feature.album.generated.resources.album_duration_minutes_seconds
import musicapp.feature.album.generated.resources.album_duration_seconds
import musicapp.feature.album.generated.resources.album_more_actions
import musicapp.feature.album.generated.resources.album_play
import musicapp.feature.album.generated.resources.album_remove_favorite
import musicapp.feature.album.generated.resources.album_remove_favorite_menu
import musicapp.feature.album.generated.resources.album_search
import musicapp.feature.album.generated.resources.album_shuffle
import musicapp.feature.album.generated.resources.album_track_more_actions
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class AlbumMenuEntry(
    val text: String,
    val icon: DrawableResource,
    val separatorBefore: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
internal fun DesktopAlbumScreen(
    state: AlbumState,
    currentPlayingTrackId: Long?,
    favoriteTrackIds: Set<Long>,
    onToggleFavorite: (Long) -> Unit,
    onAction: (AlbumAction) -> Unit,
) {
    var query by remember(state.albumId) { mutableStateOf("") }
    var selectedTrackId by remember(state.albumId) { mutableStateOf<Long?>(null) }
    val tracks = remember(state.tracks, query) {
        state.tracks.filter { track ->
            query.isBlank() || track.title.contains(query, true) || track.artist.orEmpty().contains(query, true)
        }
    }
    LiquidGlassOverlayScene(
        modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background),
        backdropContent = {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 40.dp, top = DesignTokens.adaptive.compactHeaderHeight, end = 40.dp, bottom = 86.dp),
            ) {
                item("album-hero") {
                    AlbumDesktopHero(state = state, onAction = onAction)
                }
                itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                    AlbumDesktopTrackRow(
                        track = track,
                        number = index + 1,
                        selected = selectedTrackId == track.id,
                        playing = currentPlayingTrackId == track.id,
                        favorite = track.id in favoriteTrackIds,
                        onSelect = { selectedTrackId = track.id },
                        onPlay = { onAction(AlbumAction.PlayTrack(track.id)) },
                        onToggleFavorite = { onToggleFavorite(track.id) },
                        onDownload = track.takeIf { it.canDownload }?.let {
                            { onAction(AlbumAction.DownloadTrack(it)) }
                        },
                    )
                }
                if (!state.isLoading && state.error == null) {
                    item("album-summary") {
                        Text(
                            text = stringResource(
                                Res.string.album_detail_summary,
                                state.tracks.size,
                                desktopAlbumDuration(state.tracks.sumOf { it.durationMs ?: 0L }),
                            ),
                            modifier = Modifier.padding(top = 25.dp, bottom = 12.dp),
                            color = albumDesktopForeground().copy(alpha = 0.48f),
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                        )
                    }
                }
            }
        },
        overlayContent = {
            AlbumDesktopToolbar(
                title = state.title,
                query = query,
                onQueryChange = { query = it },
                canDownload = state.tracks.any { it.canDownload },
                onBack = { onAction(AlbumAction.NavigateBack) },
                onDownloadAll = {
                    state.tracks.filter { it.canDownload }.forEach { onAction(AlbumAction.DownloadTrack(it)) }
                },
                onPlay = { onAction(AlbumAction.PlayAll) },
            )
        },
    )
}

@Composable
private fun AlbumDesktopToolbar(
    title: String,
    query: String,
    onQueryChange: (String) -> Unit,
    canDownload: Boolean,
    onBack: () -> Unit,
    onDownloadAll: () -> Unit,
    onPlay: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val isDark = LocalDesignIsDarkTheme.current
    val groupedShape = RoundedCornerShape(18.dp)
    LiquidGlassActionBar(
        title = title,
        collapseFraction = 1f,
        content = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(DesignTokens.adaptive.compactHeaderHeight)
                    .padding(start = 8.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DesktopBackButton(stringResource(Res.string.album_back), onBack)
                Text(
                    text = title,
                    modifier = Modifier.weight(1f).padding(horizontal = 20.dp),
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = MiuixTheme.textStyles.title3.fontSize,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier
                        .height(36.dp)
                        .albumToolbarShadow(groupedShape, isDark)
                        .clip(groupedShape)
                        .background(albumToolbarSurfaceColor(isDark))
                        .border(
                            0.5.dp,
                            albumToolbarBorderColor(isDark),
                            groupedShape,
                        ),
                ) {
                    AlbumRoundButton(
                        description = stringResource(Res.string.album_download),
                        onClick = onDownloadAll,
                        enabled = canDownload,
                        standalone = false,
                    ) {
                        Icon(
                            painterResource(CoreRes.drawable.icon_player_download),
                            null,
                            tint = albumToolbarIconColor().copy(alpha = if (canDownload) 1f else 0.28f),
                            modifier = Modifier.size(20.dp).offset(x = 0.5.dp, y = 0.5.dp),
                        )
                    }
                    Box {
                        AlbumRoundButton(
                            stringResource(Res.string.album_more_actions),
                            { menuOpen = true },
                            standalone = false,
                        ) {
                            Icon(
                                painterResource(CoreRes.drawable.icon_player_more),
                                null,
                                tint = albumToolbarIconColor(),
                                modifier = Modifier.size(20.dp).offset(x = (-0.5).dp),
                            )
                        }
                        AlbumCompactMenu(
                            show = menuOpen,
                            onDismiss = { menuOpen = false },
                            entries = listOf(
                                AlbumMenuEntry(stringResource(Res.string.album_download), CoreRes.drawable.icon_download) {
                                    menuOpen = false; onDownloadAll()
                                },
                                AlbumMenuEntry(
                                    text = stringResource(Res.string.album_play),
                                    icon = CoreRes.drawable.icon_play,
                                    separatorBefore = true,
                                ) { menuOpen = false; onPlay() },
                            ),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                AlbumSearchField(query, onQueryChange)
            }
        },
    )
}

@Composable
private fun AlbumSearchField(value: String, onValueChange: (String) -> Unit) {
    val isDark = LocalDesignIsDarkTheme.current
    val foreground = if (isDark) Color.White else Color.Black
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.width(196.dp).height(36.dp)
            .albumToolbarShadow(shape, isDark, contactAlpha = 0.04f)
            .clip(shape)
            .background(albumToolbarSurfaceColor(isDark))
            .border(0.5.dp, albumToolbarBorderColor(isDark), shape)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(CoreRes.drawable.icon_player_search),
            null,
            tint = foreground.copy(alpha = 0.48f),
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MiuixTheme.textStyles.body2.copy(color = foreground.copy(alpha = 0.86f), fontSize = 13.sp),
            cursorBrush = SolidColor(albumAccent()),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            stringResource(Res.string.album_search),
                            color = foreground.copy(alpha = 0.45f),
                            fontSize = 13.sp,
                            maxLines = 1,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
private fun AlbumRoundButton(
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    standalone: Boolean = true,
    content: @Composable () -> Unit,
) {
    val isDark = LocalDesignIsDarkTheme.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val background = if (!standalone && !pressed && !hovered) {
        Color.Transparent
    } else if (!pressed && !hovered) {
        albumToolbarSurfaceColor(isDark)
    } else if (isDark) {
        Color.White.copy(alpha = when { !enabled -> 0.035f; pressed -> 0.14f; hovered -> 0.10f; else -> 0.075f })
    } else {
        Color.Black.copy(alpha = when { !enabled -> 0.03f; pressed -> 0.13f; hovered -> 0.085f; else -> 0.008f })
    }
    Box(
        modifier = Modifier
            .width(if (standalone) 36.dp else 35.dp)
            .height(36.dp)
            .then(
                if (standalone) {
                    Modifier.albumToolbarShadow(CircleShape, isDark)
                } else {
                    Modifier
                },
            )
            .clip(CircleShape)
            .background(background)
            .then(
                if (standalone) {
                    Modifier.border(0.5.dp, albumToolbarBorderColor(isDark), CircleShape)
                } else {
                    Modifier
                },
            )
            .hoverable(interaction)
            .semantics { contentDescription = description }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private fun albumToolbarSurfaceColor(isDark: Boolean): Color =
    if (isDark) Color.White.copy(alpha = 0.075f) else Color.White.copy(alpha = 0.55f)

private fun albumToolbarBorderColor(isDark: Boolean): Color =
    if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.86f)

private fun Modifier.albumToolbarShadow(
    shape: Shape,
    isDark: Boolean,
    contactAlpha: Float = 0.05f,
): Modifier =
    if (isDark) {
        this
    } else {
        dropShadow(
            shape = shape,
            shadow = Shadow(
                radius = 36.dp,
                offset = DpOffset(0.dp, 6.dp),
                color = Color.Black.copy(alpha = 0.04f),
            ),
        ).dropShadow(
            shape = shape,
            shadow = Shadow(
                radius = 16.dp,
                offset = DpOffset(0.dp, 5.dp),
                color = Color.Black.copy(alpha = contactAlpha),
            ),
        )
    }

@Composable
private fun albumToolbarIconColor(): Color =
    if (LocalDesignIsDarkTheme.current) Color.White.copy(alpha = 0.86f) else Color.Black.copy(alpha = 0.85f)

@Composable
private fun AlbumDesktopHero(state: AlbumState, onAction: (AlbumAction) -> Unit) {
    val defaultTitle = stringResource(Res.string.album_default_title)
    BoxWithConstraints(Modifier.fillMaxWidth().height(290.dp)) {
        val artworkSize = if (maxWidth < 650.dp) 230.dp else 270.dp
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier.size(artworkSize).shadow(3.dp, RoundedCornerShape(7.dp), clip = false)
                    .clip(RoundedCornerShape(7.dp))
                    .background(MiuixTheme.colorScheme.surfaceVariant),
            ) {
                state.artwork?.let {
                    ArtworkImage(
                        artwork = it,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f).padding(top = 79.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = state.title.ifBlank { defaultTitle },
                    color = albumDesktopForeground(),
                    fontSize = 27.sp,
                    lineHeight = 31.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.artist.isNotBlank()) {
                    Text(
                        text = state.artist,
                        color = albumAccent(),
                        fontSize = 25.sp,
                        lineHeight = 29.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val metadata = listOfNotNull(state.genre?.takeIf { it.isNotBlank() }, state.year?.toString()).joinToString(" · ")
                if (metadata.isNotBlank()) {
                    Text(
                        text = metadata,
                        color = albumDesktopForeground().copy(alpha = 0.52f),
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                    )
                }
                Spacer(Modifier.height(67.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DesktopPlaybackActionPill(
                        label = stringResource(Res.string.album_play),
                        icon = CoreRes.drawable.icon_play,
                        enabled = state.tracks.isNotEmpty(),
                        onClick = { onAction(AlbumAction.PlayAll) },
                    )
                    DesktopPlaybackActionPill(
                        label = stringResource(Res.string.album_shuffle),
                        icon = CoreRes.drawable.icon_shuffle,
                        enabled = state.tracks.isNotEmpty(),
                        onClick = { onAction(AlbumAction.Shuffle) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumDesktopTrackRow(
    track: AlbumTrackItem,
    number: Int,
    selected: Boolean,
    playing: Boolean,
    favorite: Boolean,
    onSelect: () -> Unit,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: (() -> Unit)?,
) {
    val focused = rememberPlatformWindowFocused()
    val isDark = LocalDesignIsDarkTheme.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var menuOpen by remember(track.id) { mutableStateOf(false) }
    val background = when {
        selected && focused -> albumAccent()
        selected -> if (isDark) Color.White.copy(alpha = 0.13f) else Color.Black.copy(alpha = 0.13f)
        playing -> albumDesktopForeground().copy(alpha = 0.055f)
        hovered -> albumDesktopForeground().copy(alpha = 0.055f)
        number % 2 == 0 -> albumDesktopForeground().copy(alpha = 0.035f)
        else -> Color.Transparent
    }
    val foreground = if (selected && focused) MiuixTheme.colorScheme.onPrimary else albumDesktopForeground()
    Row(
        modifier = Modifier.fillMaxWidth().height(46.dp)
            .background(background, RoundedCornerShape(5.dp))
            .hoverable(interaction)
            .onPointerEvent(PointerEventType.Press) { if (it.buttons.isSecondaryPressed) { onSelect(); menuOpen = true } }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onSelect,
                onDoubleClick = onPlay,
            )
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).offset(x = (-28).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(if (favorite) CoreRes.drawable.icon_star_filled else CoreRes.drawable.icon_star),
                contentDescription = stringResource(
                    if (favorite) Res.string.album_remove_favorite else Res.string.album_add_favorite,
                    track.title,
                ),
                tint = if (selected && focused) foreground else if (favorite) albumAccent() else foreground.copy(alpha = 0.40f),
                modifier = Modifier.size(13.dp).clickable(onClick = onToggleFavorite),
            )
            Spacer(Modifier.width(12.dp))
            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                if (playing) {
                    Icon(
                        painterResource(CoreRes.drawable.icon_pause),
                        null,
                        tint = if (selected && focused) foreground else albumAccent(),
                        modifier = Modifier.size(13.dp),
                    )
                } else {
                    Text(number.toString(), color = foreground.copy(alpha = 0.48f), fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = track.title,
                modifier = Modifier.weight(1f),
                color = foreground,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        onDownload?.let { download ->
            Icon(
                painterResource(CoreRes.drawable.icon_download),
                stringResource(Res.string.album_download),
                tint = foreground.copy(alpha = 0.50f),
                modifier = Modifier.size(14.dp).clickable(onClick = download),
            )
            Spacer(Modifier.width(14.dp))
        }
        Text(
            text = desktopTrackDuration(track.durationMs),
            modifier = Modifier.width(42.dp),
            color = foreground.copy(alpha = 0.52f),
            fontSize = 12.sp,
            textAlign = TextAlign.Right,
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(CoreRes.drawable.icon_player_more),
                stringResource(Res.string.album_track_more_actions, track.title),
                tint = foreground.copy(alpha = 0.52f),
                modifier = Modifier.size(15.dp).clickable { menuOpen = true },
            )
            AlbumCompactMenu(
                show = menuOpen,
                onDismiss = { menuOpen = false },
                entries = buildList {
                    if (onDownload != null) add(
                        AlbumMenuEntry(stringResource(Res.string.album_download), CoreRes.drawable.icon_download) {
                            menuOpen = false; onDownload()
                        },
                    )
                    add(
                        AlbumMenuEntry(
                            text = stringResource(Res.string.album_play),
                            icon = CoreRes.drawable.icon_play,
                            separatorBefore = onDownload != null,
                        ) { menuOpen = false; onPlay() },
                    )
                    add(
                        AlbumMenuEntry(
                            text = stringResource(
                                if (favorite) Res.string.album_remove_favorite_menu
                                else Res.string.album_add_favorite_menu,
                            ),
                            icon = if (favorite) CoreRes.drawable.icon_star_filled else CoreRes.drawable.icon_star,
                            separatorBefore = true,
                        ) { menuOpen = false; onToggleFavorite() },
                    )
                },
            )
        }
    }
}

@Composable
private fun AlbumCompactMenu(
    show: Boolean,
    onDismiss: () -> Unit,
    entries: List<AlbumMenuEntry>,
) {
    if (!show) return
    val isDark = LocalDesignIsDarkTheme.current
    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(8, 18),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            Modifier.width(176.dp)
                .shadow(14.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(if (isDark) Color(0xF22C2C2E) else Color(0xF2F8F8F8))
                .border(
                    0.5.dp,
                    if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.12f),
                    RoundedCornerShape(10.dp),
                )
                .padding(vertical = 3.dp),
        ) {
            entries.forEach { entry ->
                if (entry.separatorBefore) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                            .height(0.5.dp)
                            .background(albumDesktopForeground().copy(alpha = 0.13f)),
                    )
                }
                val interaction = remember(entry.text) { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                Row(
                    Modifier.fillMaxWidth().height(24.dp).padding(horizontal = 5.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (hovered) albumAccent() else Color.Transparent)
                        .hoverable(interaction)
                        .clickable(interactionSource = interaction, indication = null, onClick = entry.onClick)
                        .padding(horizontal = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(entry.icon),
                            null,
                            tint = if (hovered) MiuixTheme.colorScheme.onPrimary else albumDesktopForeground(),
                            modifier = Modifier.size(10.dp),
                        )
                    }
                    Spacer(Modifier.width(9.dp))
                    Text(
                        entry.text,
                        color = if (hovered) MiuixTheme.colorScheme.onPrimary else albumDesktopForeground(),
                        fontSize = 13.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun albumAccent(): Color = MiuixTheme.colorScheme.primary

@Composable
private fun albumDesktopForeground(): Color =
    if (LocalDesignIsDarkTheme.current) MiuixTheme.colorScheme.onBackground else Color.Black

private fun desktopTrackDuration(durationMs: Long?): String {
    val seconds = ((durationMs ?: 0L) / 1_000L).coerceAtLeast(0L)
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

@Composable
private fun desktopAlbumDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1_000).coerceAtLeast(0)
    val hours = totalSeconds / 3_600
    val minutes = totalSeconds / 60 % 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 && minutes > 0 -> stringResource(Res.string.album_duration_hours_minutes, hours, minutes)
        hours > 0 -> stringResource(Res.string.album_duration_hours, hours)
        minutes > 0 && seconds > 0 -> stringResource(Res.string.album_duration_minutes_seconds, minutes, seconds)
        minutes > 0 -> stringResource(Res.string.album_duration_minutes, minutes)
        else -> stringResource(Res.string.album_duration_seconds, seconds)
    }
}
