package io.github.julystar.musicapp.service.playback.presentation.nowplaying

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.sp
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.presentation.media.ArtworkImage
import io.github.julystar.musicapp.core.presentation.theme.LocalDesignIsDarkTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.window.WindowDialog
import musicapp.service.playback.presentation.generated.resources.Res
import musicapp.service.playback.presentation.generated.resources.player_create_playlist
import musicapp.service.playback.presentation.generated.resources.player_create_playlist_track_hint
import musicapp.service.playback.presentation.generated.resources.player_new_playlist
import musicapp.service.playback.presentation.generated.resources.player_playlist_name
import musicapp.service.playback.presentation.generated.resources.playlist_cover_moss
import musicapp.service.playback.presentation.generated.resources.playlists_dialog_button_cancel

@Composable
internal fun NowPlayingCreatePlaylistDialog(
    show: Boolean,
    name: String,
    currentTrackTitle: String?,
    currentTrackArtwork: Artwork?,
    onNameChange: (String) -> Unit,
    onCancel: () -> Unit,
    onCreate: () -> Unit,
) {
    val dark = LocalDesignIsDarkTheme.current
    val surface = if (dark) Color(0xFF292929) else Color.White
    val foreground = if (dark) Color.White else Color(0xFF202020)
    val secondary = if (dark) Color(0xFFAFAFAF) else Color(0xFF888888)
    val fieldColor = if (dark) Color(0xFF343434) else Color.White
    val borderColor = if (dark) Color(0xFF5A5A5A) else Color(0xFFE4E4E4)
    val accent = Color(0xFFFF3657)
    val canCreate = name.isNotBlank()
    val maxDialogHeight = (LocalWindowInfo.current.containerDpSize.height - 32.dp).coerceAtLeast(0.dp)
    val nameFocusRequester = remember { FocusRequester() }

    WindowDialog(
        show = show,
        onDismissRequest = onCancel,
        backgroundColor = surface,
        insideMargin = DpSize(0.dp, 0.dp),
        outsideMargin = DpSize(16.dp, 16.dp),
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 344.dp)
                .fillMaxWidth()
                .heightIn(max = maxDialogHeight)
                .verticalScroll(rememberScrollState())
                .testTag("now-playing-create-playlist-dialog"),
        ) {
            LaunchedEffect(show) {
                if (show) nameFocusRequester.requestFocus()
            }
            Text(
                text = stringResource(Res.string.player_new_playlist),
                color = foreground,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 20.dp),
            )

            Box(
                modifier = Modifier
                    .size(172.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(9.dp))
                    .border(3.dp, accent, RoundedCornerShape(9.dp)),
            ) {
                if (currentTrackArtwork != null) {
                    ArtworkImage(
                        modifier = Modifier.fillMaxSize().padding(3.dp).clip(RoundedCornerShape(6.dp)),
                        artwork = currentTrackArtwork,
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Image(
                        painter = painterResource(Res.drawable.playlist_cover_moss),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().padding(3.dp).clip(RoundedCornerShape(6.dp)),
                    )
                }
            }

            Spacer(Modifier.height(38.dp))

            val nameLabel = stringResource(Res.string.player_playlist_name)
            var focused by remember { mutableStateOf(false) }
            BasicTextField(
                value = name,
                onValueChange = onNameChange,
                singleLine = true,
                textStyle = TextStyle(color = foreground, fontSize = 14.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(fieldColor)
                    .border(
                        width = if (focused) 2.dp else 1.dp,
                        color = if (focused) accent.copy(alpha = 0.68f) else borderColor,
                        shape = RoundedCornerShape(9.dp),
                    )
                    .focusRequester(nameFocusRequester)
                    .onFocusChanged { focused = it.isFocused }
                    .padding(horizontal = 11.dp, vertical = 7.dp)
                    .semantics { contentDescription = nameLabel },
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (name.isEmpty()) {
                            Text(text = nameLabel, color = secondary, fontSize = 14.sp)
                        }
                        innerTextField()
                    }
                },
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp)
                    .heightIn(min = 68.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (dark) Color(0xFF333333) else Color(0xFFFAFAFA))
                    .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                    .padding(horizontal = 11.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = stringResource(Res.string.player_create_playlist_track_hint),
                    color = secondary,
                    fontSize = 11.sp,
                )
                if (!currentTrackTitle.isNullOrBlank()) {
                    Text(
                        text = currentTrackTitle,
                        color = foreground,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.fillMaxWidth().height(1.dp).background(borderColor))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DialogPillButton(
                    text = stringResource(Res.string.playlists_dialog_button_cancel),
                    enabled = true,
                    foreground = foreground,
                    background = if (dark) Color(0xFF454545) else Color(0xFFECECEC),
                    onClick = onCancel,
                )
                DialogPillButton(
                    text = stringResource(Res.string.player_create_playlist),
                    enabled = canCreate,
                    foreground = if (canCreate) Color.White else secondary,
                    background = if (canCreate) accent else if (dark) Color(0xFF454545) else Color(0xFFF3F3F3),
                    onClick = onCreate,
                )
            }
        }
    }
}

@Composable
private fun DialogPillButton(
    text: String,
    enabled: Boolean,
    foreground: Color,
    background: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = foreground, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
