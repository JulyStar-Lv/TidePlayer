package io.github.julystar.musicapp.feature.playlist.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.presentation.components.DesktopCreatePlaylistDialog

@Composable
internal fun DesktopCreatePlaylistDialog(
    state: CreatePlaylistState,
    onAction: (CreatePlaylistAction) -> Unit,
    colorArtwork: Artwork? = null,
) {
    DesktopCreatePlaylistDialog(
        show = state.isOpen,
        name = state.name,
        onNameChange = { onAction(CreatePlaylistAction.UpdateName(it)) },
        onCancel = { onAction(CreatePlaylistAction.Close) },
        onCreate = { onAction(CreatePlaylistAction.Submit) },
        coverArtwork = state.coverArtwork,
        onImportCover = { onAction(CreatePlaylistAction.PrepareImport) },
        modifier = Modifier.testTag("desktop-create-playlist-dialog"),
        colorArtwork = colorArtwork,
        onCreateWithCover = { onAction(CreatePlaylistAction.SubmitWithCover(it)) },
    )
}
