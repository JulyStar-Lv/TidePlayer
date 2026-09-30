package io.github.julystar.musicapp.feature.playlist.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.julystar.musicapp.core.domain.model.Artwork
import kotlinx.collections.immutable.toImmutableList
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CreatePlaylistRoot(
    onNavigateToImport: () -> Unit,
    onNavigateToCoverImport: () -> Unit,
    createPlaylistVM: CreatePlaylistVM = koinViewModel(),
    colorArtwork: Artwork? = null,
) {
    val isOpen by createPlaylistVM.modalOpen.collectAsState()
    val mode by createPlaylistVM.mode.collectAsState()
    val name by createPlaylistVM.name.collectAsState()
    val musicCount by createPlaylistVM.musicCount.collectAsState()
    val recommendNames by createPlaylistVM.recommendPlaylistNames.collectAsState()
    val coverArtwork by createPlaylistVM.coverArtwork.collectAsState()
    val fullImported by createPlaylistVM.fullImported.collectAsState()
    val canSubmit by createPlaylistVM.canSubmit.collectAsState()

    val state = CreatePlaylistState(
        isOpen = isOpen,
        mode = mode,
        name = name,
        musicCount = musicCount,
        recommendNames = recommendNames.toImmutableList(),
        coverArtwork = coverArtwork,
        fullImported = fullImported,
        canSubmit = canSubmit,
    )

    CreatePlaylistScreen(
        state = state,
        colorArtwork = colorArtwork,
        onAction = { action ->
            when (action) {
                CreatePlaylistAction.Close -> createPlaylistVM.closeModal()
                CreatePlaylistAction.SwitchToFull -> createPlaylistVM.updateMode(CreatePlaylistTab.Full)
                CreatePlaylistAction.SwitchToEmpty -> createPlaylistVM.updateMode(CreatePlaylistTab.Empty)
                is CreatePlaylistAction.UpdateName -> createPlaylistVM.updateName(action.name)
                CreatePlaylistAction.PrepareImport -> {
                    createPlaylistVM.prepareImportCreate()
                    onNavigateToImport()
                }
                CreatePlaylistAction.NavigateToImport -> {
                    onNavigateToCoverImport()
                }
                CreatePlaylistAction.ClearCover -> createPlaylistVM.clearCover()
                CreatePlaylistAction.Reset -> createPlaylistVM.reset()
                is CreatePlaylistAction.SubmitWithCover -> {
                    createPlaylistVM.finish(action.coverImage)
                    createPlaylistVM.closeModal()
                }
                CreatePlaylistAction.Submit -> {
                    createPlaylistVM.finish()
                    createPlaylistVM.closeModal()
                }
            }
        },
    )
}
