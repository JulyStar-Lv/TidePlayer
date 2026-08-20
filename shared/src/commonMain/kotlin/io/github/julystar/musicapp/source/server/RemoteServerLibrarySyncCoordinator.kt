package io.github.julystar.musicapp.source.server

import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.core.domain.model.toStorageRouteIdOrNull
import io.github.julystar.musicapp.database.ProviderTypes
import io.github.julystar.musicapp.database.SourceAccountDao
import io.github.julystar.musicapp.platform.currentTimeMillis

class RemoteServerLibrarySyncCoordinator(
    private val sourceAccountDao: SourceAccountDao,
    private val navidrome: NavidromeLibrarySyncCoordinator,
    private val openSubsonic: OpenSubsonicLibrarySyncCoordinator,
    private val emby: EmbyLibrarySyncCoordinator,
) {
    suspend fun sync(
        accountId: SourceAccountId,
        scanId: String? = null,
        pageSize: Int = 500,
    ): RemoteServerLibrarySyncResult {
        val sourceAccountId = accountId.toStorageRouteIdOrNull()
            ?: error("remote server account must use a storage route")
        val providerType = sourceAccountDao.get(sourceAccountId)?.providerType
            ?: error("remote server source account does not exist")
        val effectiveScanId = scanId ?: when (providerType) {
            ProviderTypes.Navidrome -> "navidrome-${currentTimeMillis()}"
            ProviderTypes.OpenSubsonic -> "open-subsonic-${currentTimeMillis()}"
            ProviderTypes.Emby -> "emby-${currentTimeMillis()}"
            else -> error("source account is not a remote server provider")
        }
        return when (providerType) {
            ProviderTypes.Navidrome -> navidrome.sync(accountId, effectiveScanId, pageSize)
            ProviderTypes.OpenSubsonic -> openSubsonic.sync(accountId, effectiveScanId, pageSize)
            ProviderTypes.Emby -> emby.sync(accountId, effectiveScanId, pageSize)
            else -> error("source account is not a remote server provider")
        }
    }
}
