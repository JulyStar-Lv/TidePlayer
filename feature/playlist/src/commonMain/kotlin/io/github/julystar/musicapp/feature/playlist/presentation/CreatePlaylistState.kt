package io.github.julystar.musicapp.feature.playlist.presentation

import androidx.compose.runtime.Immutable
import io.github.julystar.musicapp.core.domain.model.Artwork
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class CreatePlaylistState(
    val isOpen: Boolean = false,
    val mode: CreatePlaylistTab = CreatePlaylistTab.Full,
    val name: String = "",
    val musicCount: Int = 0,
    val recommendNames: ImmutableList<String> = persistentListOf(),
    val coverArtwork: Artwork? = null,
    val fullImported: Boolean = false,
    val canSubmit: Boolean = false,
)

enum class CreatePlaylistTab { Full, Empty }

sealed interface CreatePlaylistAction {
    data object Close : CreatePlaylistAction
    data object SwitchToFull : CreatePlaylistAction
    data object SwitchToEmpty : CreatePlaylistAction
    data class UpdateName(val name: String) : CreatePlaylistAction
    data object PrepareImport : CreatePlaylistAction
    data object NavigateToImport : CreatePlaylistAction
    data object ClearCover : CreatePlaylistAction
    data object Reset : CreatePlaylistAction
    data object Submit : CreatePlaylistAction
    data class SubmitWithCover(val coverImage: ByteArray?) : CreatePlaylistAction
}

@Immutable
data class EditPlaylistState(
    val isOpen: Boolean = false,
    val name: String = "",
    val coverArtwork: Artwork? = null,
    val canSubmit: Boolean = false,
)

sealed interface EditPlaylistAction {
    data object Close : EditPlaylistAction
    data class UpdateName(val name: String) : EditPlaylistAction
    data object NavigateToCoverImport : EditPlaylistAction
    data object ClearCover : EditPlaylistAction
    data object Submit : EditPlaylistAction
    data class SubmitWithCover(val coverImage: ByteArray?) : EditPlaylistAction
}
