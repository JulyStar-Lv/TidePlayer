package io.github.julystar.musicapp.core.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.presentation.media.ArtworkImage
import io.github.julystar.musicapp.core.presentation.media.ArtworkPalette
import io.github.julystar.musicapp.core.presentation.media.rememberArtworkPalette
import io.github.julystar.musicapp.core.presentation.theme.LocalDesignIsDarkTheme
import musicapp.core.presentation.generated.resources.Res as CoreRes
import musicapp.core.presentation.generated.resources.icon_image
import musicapp.core.presentation.generated.resources.icon_chevron_left
import musicapp.core.presentation.generated.resources.icon_chevron_right
import musicapp.core.presentation.generated.resources.playlist_cover_previous
import musicapp.core.presentation.generated.resources.playlist_cover_next
import musicapp.core.presentation.generated.resources.playlist_cover_pattern
import musicapp.core.presentation.generated.resources.playlist_desktop_create
import musicapp.core.presentation.generated.resources.playlist_desktop_create_title
import musicapp.core.presentation.generated.resources.playlist_desktop_description
import musicapp.core.presentation.generated.resources.playlists_dialog_button_cancel
import musicapp.core.presentation.generated.resources.playlist_desktop_name
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

private val PlaylistAccent = Color(0xFFFF3657)
private const val PlaylistCoverPatternCount = 9

@Composable
fun DesktopCreatePlaylistDialog(
    show: Boolean,
    name: String,
    onNameChange: (String) -> Unit,
    onCancel: () -> Unit,
    onCreate: () -> Unit,
    coverArtwork: Artwork? = null,
    onImportCover: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    colorArtwork: Artwork? = null,
    onCreateWithCover: ((ByteArray?) -> Unit)? = null,
    title: String = stringResource(CoreRes.string.playlist_desktop_create_title),
    confirmText: String = stringResource(CoreRes.string.playlist_desktop_create),
) {
    val dark = LocalDesignIsDarkTheme.current
    val surface = if (dark) Color(0xFF2C2C2E) else Color.White
    val foreground = if (dark) Color(0xFFF5F5F7) else Color(0xFF202020)
    val secondary = if (dark) Color(0xFF9B9B9D) else Color(0xFF777779)
    val field = if (dark) Color(0xFF303032) else Color(0xFFF8F8F8)
    val border = if (dark) Color(0xFF49494B) else Color(0xFFD7D7D9)
    val nameFocus = remember { FocusRequester() }
    var description by remember(show) { mutableStateOf("") }
    var selectedCover by remember(show) { mutableIntStateOf(0) }

    if (!show) return
    val palette = if (colorArtwork != null) rememberArtworkPalette(colorArtwork) else DefaultPlaylistCoverPalette
    PlatformOverlayHost(onDismissRequest = onCancel) {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
                .pointerInput(onCancel) { detectTapGestures { onCancel() } }
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier.widthIn(max = 324.dp).fillMaxWidth().heightIn(max = maxHeight)
                    .clip(RoundedCornerShape(32.dp)).background(surface)
                    .border(0.5.dp, border, RoundedCornerShape(32.dp))
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    LaunchedEffect(show) {
                        if (show) nameFocus.requestFocus()
                    }
                    Text(
                        text = title,
                        color = foreground,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().height(60.dp).padding(top = 13.dp),
                    )
                    PlaylistCoverCarousel(
                        coverArtwork = coverArtwork,
                        palette = palette,
                        selected = selectedCover,
                        onSelect = { selectedCover = it },
                        onImport = onImportCover,
                        dark = dark,
                    )
                    Spacer(Modifier.height(22.dp))
                    DesktopPlaylistField(
                        value = name,
                        onValueChange = onNameChange,
                        label = stringResource(CoreRes.string.playlist_desktop_name),
                        height = 30.dp,
                        singleLine = true,
                        focusRequester = nameFocus,
                        foreground = foreground,
                        secondary = secondary,
                        field = field,
                        border = border,
                    )
                    Spacer(Modifier.height(7.dp))
                    DesktopPlaylistField(
                        value = description,
                        onValueChange = { description = it },
                        label = stringResource(CoreRes.string.playlist_desktop_description),
                        height = 49.dp,
                        singleLine = false,
                        focusRequester = null,
                        foreground = foreground,
                        secondary = secondary,
                        field = field,
                        border = border,
                    )
                    Spacer(Modifier.height(16.dp))
                }
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(border))
                Row(
                    Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlaylistDialogButton(
                        text = stringResource(CoreRes.string.playlists_dialog_button_cancel),
                        enabled = true,
                        foreground = foreground,
                        background = if (dark) Color(0xFF404042) else Color(0xFFECECEE),
                    ) { onCancel() }
                    PlaylistDialogButton(
                        text = confirmText,
                        enabled = name.isNotBlank(),
                        foreground = if (name.isNotBlank()) Color.White else secondary,
                        background = if (name.isNotBlank()) PlaylistAccent else if (dark) Color(0xFF39393B) else Color(0xFFF0F0F2),
                    ) {
                        if (onCreateWithCover == null) onCreate()
                        else onCreateWithCover(if (selectedCover == 0 && coverArtwork != null) null
                            else encodePlaylistCover(selectedCover, palette))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistCoverCarousel(
    coverArtwork: Artwork?,
    palette: ArtworkPalette,
    selected: Int,
    onSelect: (Int) -> Unit,
    onImport: (() -> Unit)?,
    dark: Boolean,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    BoxWithConstraints(Modifier.fillMaxWidth().height(162.dp).clip(RoundedCornerShape(1.dp))
        .hoverable(interaction)) {
        val centerOffset = (maxWidth - 162.dp) / 2
        PlaylistCoverTile((selected + PlaylistCoverPatternCount - 1) % PlaylistCoverPatternCount,
            coverArtwork, palette, false, Modifier.offset(x = centerOffset - 172.dp), onSelect)
        PlaylistCoverTile((selected + 1) % PlaylistCoverPatternCount,
            coverArtwork, palette, false, Modifier.offset(x = centerOffset + 172.dp), onSelect)
        PlaylistCoverTile(selected, coverArtwork, palette, true, Modifier.offset(x = centerOffset), onSelect)
        if (hovered) {
            PlaylistCoverArrow(false, Modifier.align(Alignment.CenterStart).padding(start = 2.dp)) {
                onSelect((selected + PlaylistCoverPatternCount - 1) % PlaylistCoverPatternCount)
            }
            PlaylistCoverArrow(true, Modifier.align(Alignment.CenterEnd).padding(end = 2.dp)) {
                onSelect((selected + 1) % PlaylistCoverPatternCount)
            }
        }
    }
    Row(
        Modifier.fillMaxWidth().height(45.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(CoreRes.drawable.icon_image), null,
            tint = if (dark) Color(0xFF89898B) else Color(0xFF777779),
            modifier = Modifier.size(10.dp).clickable(enabled = onImport != null) { onImport?.invoke() })
        Spacer(Modifier.width(10.dp))
        repeat(PlaylistCoverPatternCount) { index ->
            val description = stringResource(CoreRes.string.playlist_cover_pattern, index + 1)
            Box(
                Modifier.padding(end = if (index == PlaylistCoverPatternCount - 1) 0.dp else 8.dp)
                    .size(6.dp).clip(CircleShape)
                    .background(if (index == selected) {
                        if (dark) Color.White else Color(0xFF303032)
                    } else Color(0xFF6C6C6E))
                    .clickable { onSelect(index) }
                    .semantics { contentDescription = description; this.selected = index == selected },
            )
        }
    }
}

@Composable
private fun PlaylistCoverTile(
    index: Int,
    coverArtwork: Artwork?,
    palette: ArtworkPalette,
    selected: Boolean,
    modifier: Modifier,
    onSelect: (Int) -> Unit,
) {
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier.size(162.dp).clip(shape).clickable { onSelect(index) }
            .background(Color(0xFF353537))
            .border(if (selected) 3.dp else 0.dp, if (selected) PlaylistAccent else Color.Transparent, shape)
            .padding(if (selected) 3.dp else 0.dp).clip(RoundedCornerShape(6.dp)),
    ) {
        if (index == 0 && coverArtwork != null) {
            ArtworkImage(Modifier.fillMaxSize(), coverArtwork, contentScale = ContentScale.Crop)
        } else {
            PlaylistCoverPattern(index, palette, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun PlaylistCoverArrow(next: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.width(30.dp).height(65.dp).background(Color.White.copy(alpha = 0.28f))
        .clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(painterResource(if (next) CoreRes.drawable.icon_chevron_right else CoreRes.drawable.icon_chevron_left),
            stringResource(if (next) CoreRes.string.playlist_cover_next else CoreRes.string.playlist_cover_previous),
            tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.size(18.dp).graphicsLayer { scaleY = 2.2f })
    }
}

@Composable
private fun DesktopPlaylistField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    height: Dp,
    singleLine: Boolean,
    focusRequester: FocusRequester?,
    foreground: Color,
    secondary: Color,
    field: Color,
    border: Color,
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = TextStyle(color = foreground, fontSize = 13.sp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 17.dp).height(height)
            .clip(RoundedCornerShape(8.dp)).background(field)
            .border(if (focused) 3.dp else 0.5.dp,
                if (focused) PlaylistAccent.copy(alpha = 0.64f) else border, RoundedCornerShape(8.dp))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 11.dp, vertical = 6.dp)
            .semantics { contentDescription = label },
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.TopStart) {
                if (value.isEmpty()) Text(label, color = secondary, fontSize = 13.sp)
                innerTextField()
            }
        },
    )
}

@Composable
private fun PlaylistDialogButton(
    text: String,
    enabled: Boolean,
    foreground: Color,
    background: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier.width(54.dp).height(28.dp).clip(CircleShape).background(background)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = foreground, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
