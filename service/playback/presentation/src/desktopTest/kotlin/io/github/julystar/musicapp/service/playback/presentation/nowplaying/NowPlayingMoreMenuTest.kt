package io.github.julystar.musicapp.service.playback.presentation.nowplaying

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.domain.model.LyricDisplaySettings
import musicapp.service.playback.presentation.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NowPlayingMoreMenuTest {
    @Test
    fun popupCanBeCalledWithoutScaffold() = runComposeUiTest {
        val actions = mutableListOf<NowPlayingAction>()
        var download = ""
        setContent {
            MiuixTheme {
                download = stringResource(Res.string.player_download_to_local)
                Box(Modifier.size(640.dp, 480.dp)) {
                    NowPlayingMoreMenuPopup(
                        show = true,
                        nowPlayingState = NowPlayingState(),
                        liked = false,
                        onLikedChange = {},
                        onAction = actions::add,
                        onDismiss = {},
                    )
                }
            }
        }

        onNodeWithText(download).performClick()
        assertEquals(listOf<NowPlayingAction>(NowPlayingAction.DownloadCurrentTrack), actions)
    }

    @Test
    fun playlistSubmenuAddsExistingTrackAndCreatesNamedPlaylist() = runComposeUiTest {
        val actions = mutableListOf<NowPlayingAction>()
        var more = ""
        var add = ""
        var create = ""
        var confirm = ""
        var name = ""
        var download = ""
        setContent {
            MiuixTheme {
                more = stringResource(Res.string.player_more_options)
                add = stringResource(Res.string.player_add_to_playlist)
                create = stringResource(Res.string.player_new_playlist)
                confirm = stringResource(Res.string.player_create_playlist)
                name = stringResource(Res.string.player_playlist_name)
                download = stringResource(Res.string.player_download_to_local)
                Scaffold {
                    Box(Modifier.size(640.dp, 480.dp)) {
                        MeloXDesktopMetadataRow(
                            state = NowPlayingState(
                                currentTrack = NowPlayingTrackItem(
                                    id = 1, title = "Track", durationMs = 180_000,
                                    artwork = null, mediaId = null,
                                ),
                                playlists = listOf(NowPlayingPlaylistItem(7, "Demo")),
                            ),
                            lyricDisplaySettings = LyricDisplaySettings.Default,
                            elementScale = 1f, liked = false, onLikedChange = {},
                            onAction = actions::add,
                        )
                    }
                }
            }
        }
        onNodeWithContentDescription(more).performClick()
        onNodeWithText(add).performMouseInput { moveTo(center) }
        onNodeWithText("Demo").performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(250)
        onNodeWithText(create).assertExists()
        onNodeWithText(download).performMouseInput { moveTo(center) }
        onNodeWithText(create).assertDoesNotExist()
        onNodeWithText(add).performMouseInput { moveTo(center) }
        onNodeWithText("Demo").performMouseInput {
            moveTo(center)
            moveTo(center + Offset(180f, 0f))
        }
        mainClock.advanceTimeBy(250)
        onNodeWithText(create).assertDoesNotExist()
        onNodeWithText(add).performClick()
        onNodeWithText("Demo").performClick()
        assertEquals(listOf<NowPlayingAction>(NowPlayingAction.AddCurrentTrackToPlaylist(7)), actions)

        onNodeWithContentDescription(more).performClick()
        onNodeWithText(add).performClick()
        onNodeWithText(create).performClick()
        onNodeWithContentDescription(name).performTextInput("New Mix")
        onNodeWithText(confirm).performClick()
        assertEquals(NowPlayingAction.CreatePlaylistWithCurrentTrack("New Mix"), actions.last())
    }
}
