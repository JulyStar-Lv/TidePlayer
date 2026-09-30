package io.github.julystar.musicapp.feature.playlist.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.julystar.musicapp.core.domain.model.Artwork
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun EditPlaylistRoot(
    onNavigateToCoverImport: () -> Unit,
    editPlaylistVM: EditPlaylistVM = koinViewModel(),
    colorArtwork: Artwork? = null,
) {
    val isOpen by editPlaylistVM.modalOpen.collectAsState()
    val name by editPlaylistVM.name.collectAsState()
    val coverArtwork by editPlaylistVM.coverArtwork.collectAsState()
    val canSubmit by editPlaylistVM.canSubmit.collectAsState()

    val state = EditPlaylistState(
        isOpen = isOpen,
        name = name,
        coverArtwork = coverArtwork,
        canSubmit = canSubmit,
    )

    EditPlaylistScreen(
        state = state,
        colorArtwork = colorArtwork,
        onAction = { action ->
            when (action) {
                EditPlaylistAction.Close -> editPlaylistVM.closeModal()
                is EditPlaylistAction.UpdateName -> editPlaylistVM.updateName(action.name)
                EditPlaylistAction.NavigateToCoverImport -> {
                    editPlaylistVM.prepareImportCover()
                    onNavigateToCoverImport()
                }
                EditPlaylistAction.ClearCover -> editPlaylistVM.clearCover()
                EditPlaylistAction.Submit -> editPlaylistVM.finish()
                is EditPlaylistAction.SubmitWithCover -> editPlaylistVM.finish(action.coverImage)
            }
        },
    )
}
