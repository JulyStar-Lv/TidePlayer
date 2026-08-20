package io.github.julystar.musicapp.feature.sources.presentation

import androidx.compose.runtime.Immutable
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class SourcesState(
    val sources: ImmutableList<SourceAccountUi> = persistentListOf(),
)

@Immutable
data class SourceAccountUi(
    val id: SourceAccountId,
    val title: String,
    val subtitle: String,
    val sourceType: String,
    val musicCount: Long,
    val syncEnabled: Boolean = true,
    val isSyncing: Boolean = false,
)

sealed interface SourcesAction {
    data object Refresh : SourcesAction
    data object AddSource : SourcesAction
    data class OpenSource(val id: SourceAccountId) : SourcesAction
    data class SyncSource(val id: SourceAccountId) : SourcesAction
}

sealed interface SourcesEvent {
    data object OpenNewSourceEditor : SourcesEvent
    data class OpenSourceEditor(val id: SourceAccountId) : SourcesEvent
}
