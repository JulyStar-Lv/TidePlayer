package io.github.julystar.musicapp.service.playback.presentation.nowplaying

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.domain.model.AudioTechnicalInfo
import io.github.julystar.musicapp.core.domain.model.LyricDisplaySettings
import io.github.julystar.musicapp.core.domain.model.PlaybackAudioInfo
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NowPlayingMetadataStabilityTest {
    @Test
    fun changingLanguageTitleLengthAndAudioBadgeKeepsMetadataAndControlsStationary() = runComposeUiTest {
        val track = mutableStateOf(NowPlayingTrackItem(
            id = 1, title = "A short title", artist = "An artist", album = "An album",
            durationMs = 240_000, artwork = null, mediaId = null,
        ))
        setContent {
            MiuixTheme {
                Column(Modifier.width(320.dp).height(180.dp)) {
                    MeloXDesktopMetadataRow(
                        state = NowPlayingState(currentTrack = track.value),
                        lyricDisplaySettings = LyricDisplaySettings.Default,
                        elementScale = 1f, liked = false, onLikedChange = {}, onAction = {},
                        modifier = Modifier.testTag("metadata"),
                    )
                    MeloXDesktopProgress(
                        track = track.value, currentPositionMs = 0, durationMs = 240_000,
                        elementScale = 1f, onAction = {}, modifier = Modifier.testTag("progress"),
                    )
                    androidx.compose.foundation.layout.Box(Modifier.height(36.dp).testTag("controls"))
                }
            }
        }
        waitForIdle()
        val metadata = onNodeWithTag("metadata").getUnclippedBoundsInRoot()
        val progress = onNodeWithTag("progress").getUnclippedBoundsInRoot()
        val controls = onNodeWithTag("controls").getUnclippedBoundsInRoot()
        val titleTop = onNodeWithText(track.value.title).getUnclippedBoundsInRoot().top
        for ((title, artist, audio) in listOf(
            Triple("中文歌曲名称", "中文歌手", AudioTechnicalInfo(lossless = true)),
            Triple("An English title long enough to require the scrolling marquee", "Another artist", AudioTechnicalInfo(codec = "eac3")),
            Triple("中 English 文", "双语 Artist", null),
        )) {
            runOnIdle { track.value = track.value.copy(title = title, artist = artist, playbackAudioInfo = audio?.let { PlaybackAudioInfo(source = it) }) }
            waitForIdle()
            assertEquals(metadata, onNodeWithTag("metadata").getUnclippedBoundsInRoot())
            assertEquals(progress, onNodeWithTag("progress").getUnclippedBoundsInRoot())
            assertEquals(controls, onNodeWithTag("controls").getUnclippedBoundsInRoot())
            assertEquals(titleTop, onNodeWithText(title).getUnclippedBoundsInRoot().top)
        }
    }
}
