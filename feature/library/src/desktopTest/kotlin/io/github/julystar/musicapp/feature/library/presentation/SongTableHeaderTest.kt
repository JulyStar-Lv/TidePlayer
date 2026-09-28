package io.github.julystar.musicapp.feature.library.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SongTableHeaderTest {
    @Test
    fun draggingHeaderDividerChangesColumnWidth() = runComposeUiTest {
        lateinit var widths: MutableList<androidx.compose.ui.unit.Dp>
        val deltas = mutableListOf<androidx.compose.ui.unit.Dp>()
        setContent {
            MiuixTheme {
                val state = remember { mutableStateListOf(196.dp, 87.dp, 162.dp, 39.dp, 48.dp) }
                widths = state
                Box(Modifier.size(800.dp, 80.dp)) {
                    SongTableHeader(
                        sort = DesktopSort.Title,
                        sortDescending = false,
                        columnWidths = state,
                        horizontalScroll = rememberScrollState(),
                        onColumnResize = { index, delta -> deltas += delta; state[index] += delta },
                        onSort = {},
                    )
                }
            }
        }
        onNodeWithTag("song-resize-artist", useUnmergedTree = true).performMouseInput {
            moveTo(center)
            press()
            moveTo(center + Offset(3f, 0f))
            moveTo(center + Offset(30f, 0f))
            moveTo(center + Offset(100f, 0f))
            release()
        }
        assertTrue(widths[1] > 87.dp, "Artist column should grow when its divider is dragged: ${widths[1]}, deltas=$deltas")
    }
}
