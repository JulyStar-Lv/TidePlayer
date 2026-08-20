package io.github.julystar.musicapp.source.storage

import uniffi.app_backend.ListStorageEntryChildrenResp
import uniffi.app_backend.OneDriveDeltaPageResult
import uniffi.app_backend.OneDriveDeltaRequest
import uniffi.app_backend.RemoteMusicScanSession
import uniffi.app_backend.StorageEntryLoc
import uniffi.app_backend.StorageId
import uniffi.app_backend.WebDavSyncPageResult
import uniffi.app_backend.WebDavSyncRequest
import uniffi.app_backend.ctListStorageEntryChildren
import uniffi.app_backend.ctListSmbServerEntryChildren
import uniffi.app_backend.ctGetOnedriveDeltaPage
import uniffi.app_backend.ctScanStorageMusicFolder
import uniffi.app_backend.ctStartStorageMusicScan
import uniffi.app_backend.ctStartOpenlistMusicScan
import uniffi.app_backend.ctGetWebdavSyncPage
import io.github.julystar.musicapp.singleton.Bridge
import io.github.julystar.musicapp.core.data.StorageRepositoryImpl
import io.github.julystar.musicapp.core.data.OpenListSessionManager
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.core.domain.model.toStorageRouteIdOrNull

class RemoteScannerRepository(
    private val bridge: Bridge,
    private val storageRepository: StorageRepositoryImpl,
    private val openListSessionManager: OpenListSessionManager? = null,
) {
    suspend fun listDirectory(
        storageId: StorageId,
        path: String,
    ): ListStorageEntryChildrenResp {
        val storage = storageRepository.storageForRust(storageId)
            ?: return ListStorageEntryChildrenResp.Unknown
        return bridge.runRaw {
            ctListStorageEntryChildren(
                it,
                storage,
                StorageEntryLoc(
                    storageId = storageId,
                    path = path,
                ),
            )
        }
    }

    suspend fun listSmbServerDirectory(
        storageId: StorageId,
        path: String,
    ): ListStorageEntryChildrenResp {
        val storage = storageRepository.storageForRust(storageId)
            ?: return ListStorageEntryChildrenResp.Unknown
        return bridge.runRaw {
            ctListSmbServerEntryChildren(
                it,
                storage,
                StorageEntryLoc(
                    storageId = storageId,
                    path = path,
                ),
            )
        }
    }

    suspend fun scanMusicFolder(
        storageId: StorageId,
        path: String,
    ): ListStorageEntryChildrenResp {
        val storage = storageRepository.storageForRust(storageId)
            ?: return ListStorageEntryChildrenResp.Unknown
        return bridge.runRaw {
            ctScanStorageMusicFolder(
                it,
                storage,
                StorageEntryLoc(
                    storageId = storageId,
                    path = path,
                ),
            )
        }
    }

    suspend fun startMusicFolderScan(
        storageId: StorageId,
        path: String,
    ): RemoteMusicScanSession {
        val storage = storageRepository.storageForRust(storageId)
            ?: error("Storage ${storageId.value} is no longer available")
        return bridge.runRaw {
            ctStartStorageMusicScan(
                it,
                storage,
                StorageEntryLoc(
                    storageId = storageId,
                    path = path,
                ),
            )
        }
    }

    suspend fun startOpenListMusicFolderScan(
        accountId: SourceAccountId,
        path: String,
    ): RemoteMusicScanSession {
        val manager = requireNotNull(openListSessionManager) {
            "OpenList session manager is not configured"
        }
        val storageId = accountId.toStorageRouteIdOrNull()
            ?: error("OpenList account has no storage route")
        return manager.validatedAuthorized(accountId) { endpoint, token ->
            bridge.runRaw {
                ctStartOpenlistMusicScan(
                    storageId = StorageId(storageId),
                    baseUrl = endpoint,
                    token = token,
                    path = path,
                )
            }
        }
    }

    suspend fun getOneDriveDeltaPage(
        storageId: StorageId,
        rootRemoteId: String,
        cursor: String?,
        latestOnly: Boolean,
    ): OneDriveDeltaPageResult {
        val storage = storageRepository.storageForRust(storageId)
            ?: return OneDriveDeltaPageResult.ResyncRequired
        return bridge.runRaw {
            ctGetOnedriveDeltaPage(
                it,
                storage,
                OneDriveDeltaRequest(
                    storageId = storageId,
                    rootRemoteId = rootRemoteId,
                    cursor = cursor,
                    latestOnly = latestOnly,
                ),
            )
        }
    }

    suspend fun getWebDavSyncPage(
        storageId: StorageId,
        rootPath: String,
        syncToken: String?,
    ): WebDavSyncPageResult {
        val storage = storageRepository.storageForRust(storageId)
            ?: return WebDavSyncPageResult.ResyncRequired
        return bridge.runRaw {
            ctGetWebdavSyncPage(
                it,
                storage,
                WebDavSyncRequest(
                    storageId = storageId,
                    rootPath = rootPath,
                    syncToken = syncToken,
                ),
            )
        }
    }
}
