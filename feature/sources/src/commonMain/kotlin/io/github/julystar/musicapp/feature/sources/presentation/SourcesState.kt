package io.github.julystar.musicapp.feature.sources.presentation

import androidx.compose.runtime.Immutable
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.core.domain.model.sanitizeSourceEndpointForDisplay
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
    val safeEndpoint: String?,
    val sourceType: String,
    val musicCount: Long,
    val syncEnabled: Boolean = true,
    val isSyncing: Boolean = false,
)

internal sealed interface SourcesRenderItem {
    data class Source(val account: SourceAccountUi) : SourcesRenderItem
    data object AddSource : SourcesRenderItem
}

internal fun SourcesState.renderItems(): List<SourcesRenderItem> = buildList {
    add(SourcesRenderItem.AddSource)
    sources.forEach { source -> add(SourcesRenderItem.Source(source)) }
}

internal fun sanitizeSourceCardEndpoint(rawEndpoint: String): String? {
    return sanitizeSourceEndpointForDisplay(rawEndpoint)
}

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
