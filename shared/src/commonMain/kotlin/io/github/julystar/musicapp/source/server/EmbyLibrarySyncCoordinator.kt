package io.github.julystar.musicapp.source.server

import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.database.AppDatabase
import io.github.julystar.musicapp.database.MetadataDao
import io.github.julystar.musicapp.database.SourceAccountDao
import io.github.julystar.musicapp.database.SourceItemDao
import io.github.julystar.musicapp.database.TrackDao
import io.github.julystar.musicapp.database.TrackSourceRefDao
import io.github.julystar.musicapp.platform.currentTimeMillis
import io.github.julystar.musicapp.source.api.RemoteServerGateway
import io.github.julystar.musicapp.source.api.RemoteServerKind

typealias EmbyLibrarySyncResult = RemoteServerLibrarySyncResult

class EmbyLibrarySyncCoordinator(
    database: AppDatabase,
    gateway: RemoteServerGateway,
    sourceAccountDao: SourceAccountDao,
    sourceItemDao: SourceItemDao,
    trackSourceRefDao: TrackSourceRefDao,
    trackDao: TrackDao,
    metadataDao: MetadataDao,
) {
    private val delegate = RemoteServerLibrarySyncEngine(
        database = database,
        gateway = gateway,
        sourceAccountDao = sourceAccountDao,
        sourceItemDao = sourceItemDao,
        trackSourceRefDao = trackSourceRefDao,
        trackDao = trackDao,
        metadataDao = metadataDao,
        providerKind = RemoteServerKind.Emby,
    )

    suspend fun sync(
        accountId: SourceAccountId,
        scanId: String = "emby-${currentTimeMillis()}",
        pageSize: Int = 500,
    ): EmbyLibrarySyncResult = delegate.sync(accountId, scanId, pageSize)
}
