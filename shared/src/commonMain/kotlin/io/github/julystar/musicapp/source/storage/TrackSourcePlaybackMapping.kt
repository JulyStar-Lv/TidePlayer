package io.github.julystar.musicapp.source.storage

import io.github.julystar.musicapp.core.domain.model.MediaId
import io.github.julystar.musicapp.core.domain.model.MediaType
import io.github.julystar.musicapp.core.domain.model.SourceId
import io.github.julystar.musicapp.core.domain.model.storageSourceAccountId
import io.github.julystar.musicapp.database.ProviderTypes
import io.github.julystar.musicapp.database.TrackSourcePlaybackCandidate
import io.github.julystar.musicapp.source.api.BuiltInSourceIds
import io.github.julystar.musicapp.source.api.legacyStorageTrackMediaId

internal fun TrackSourcePlaybackCandidate.toSourceTrackMediaIdOrNull(): MediaId? {
    val sourceId = account.providerType.toSourceId()
    return when (account.providerType) {
        ProviderTypes.Local,
        ProviderTypes.WebDav,
        ProviderTypes.OneDrive,
        ProviderTypes.Smb,
        ProviderTypes.OpenList,
        -> item.canonicalPath?.takeIf { it.isNotBlank() }?.let { path ->
            legacyStorageTrackMediaId(
                sourceId = sourceId,
                accountId = storageSourceAccountId(account.id),
                path = path,
            )
        }
        else -> item.providerItemId.takeIf { it?.isNotBlank() == true }?.let { remoteId ->
            MediaId(
                sourceId = sourceId,
                mediaType = MediaType.Track,
                remoteId = remoteId,
            )
        }
    }
}

private fun String.toSourceId(): SourceId = when (this) {
    ProviderTypes.Local -> BuiltInSourceIds.Local
    ProviderTypes.WebDav -> BuiltInSourceIds.WebDav
    ProviderTypes.OneDrive -> BuiltInSourceIds.OneDrive
    ProviderTypes.Smb -> BuiltInSourceIds.Smb
    ProviderTypes.OpenList -> BuiltInSourceIds.OpenList
    ProviderTypes.Navidrome -> BuiltInSourceIds.Navidrome
    ProviderTypes.OpenSubsonic -> BuiltInSourceIds.OpenSubsonic
    ProviderTypes.Emby -> BuiltInSourceIds.Emby
    else -> SourceId(this)
}
