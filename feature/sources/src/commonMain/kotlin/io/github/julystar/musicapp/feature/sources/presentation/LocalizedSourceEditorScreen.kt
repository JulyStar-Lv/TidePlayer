package io.github.julystar.musicapp.feature.sources.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import musicapp.feature.sources.generated.resources.Res
import musicapp.feature.sources.generated.resources.icon_back
import musicapp.feature.sources.generated.resources.icon_deleteseep
import musicapp.feature.sources.generated.resources.icon_ok
import musicapp.feature.sources.generated.resources.icon_wifitethering
import musicapp.feature.sources.generated.resources.source_editor_back
import musicapp.feature.sources.generated.resources.source_editor_delete
import musicapp.feature.sources.generated.resources.source_editor_edit
import musicapp.feature.sources.generated.resources.source_editor_new
import musicapp.feature.sources.generated.resources.source_editor_save
import musicapp.feature.sources.generated.resources.source_editor_source
import musicapp.feature.sources.generated.resources.source_editor_test
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Localized production wrapper for the source editor.
 *
 * The provider and credential forms remain in [SourceEditorScreen]. This wrapper
 * only replaces the legacy hard-coded header and delegates all actions to the
 * existing source-editor state machine.
 */
@Composable
fun LocalizedSourceEditorScreen(
    state: SourceEditorState,
    onAction: (SourceEditorAction) -> Unit,
) {
    val testTint = when (state.testStatus) {
        SourceConnectionTestStatus.None -> MiuixTheme.colorScheme.onSurface
        SourceConnectionTestStatus.Testing -> MiuixTheme.colorScheme.onTertiaryContainer
        SourceConnectionTestStatus.Success -> MiuixTheme.colorScheme.primary
        else -> MiuixTheme.colorScheme.error
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SourceEditorScreen(state = state, onAction = onAction)
        Row(
            modifier = Modifier
                .zIndex(2f)
                .fillMaxWidth()
                .height(64.dp)
                .background(MiuixTheme.colorScheme.background)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { onAction(SourceEditorAction.NavigateBack) },
            ) { Icon(painterResource(Res.drawable.icon_back), stringResource(Res.string.source_editor_back)) }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.title.ifBlank {
                        stringResource(Res.string.source_editor_source)
                    },
                    style = MiuixTheme.textStyles.subtitle,
                    color = MiuixTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        if (state.isCreated) {
                            Res.string.source_editor_new
                        } else {
                            Res.string.source_editor_edit
                        },
                    ),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!state.isCreated) {
                    IconButton(
                        onClick = { onAction(SourceEditorAction.OpenRemoveDialog) },
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.icon_deleteseep),
                            contentDescription = stringResource(Res.string.source_editor_delete),
                            tint = MiuixTheme.colorScheme.error,
                        )
                    }
                }
                IconButton(
                    enabled = state.testStatus != SourceConnectionTestStatus.Testing,
                    onClick = { onAction(SourceEditorAction.TestConnection) },
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.icon_wifitethering),
                        contentDescription = stringResource(Res.string.source_editor_test),
                        tint = testTint,
                    )
                }
                IconButton(
                    backgroundColor = MiuixTheme.colorScheme.primary,
                    onClick = { onAction(SourceEditorAction.Save) },
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.icon_ok),
                        contentDescription = stringResource(Res.string.source_editor_save),
                        tint = MiuixTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}
