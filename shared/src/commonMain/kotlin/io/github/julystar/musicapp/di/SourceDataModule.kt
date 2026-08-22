package io.github.julystar.musicapp.di

import io.github.julystar.musicapp.domain.importing.RemoteLibraryImportCoordinator
import io.github.julystar.musicapp.source.storage.MetadataRepository
import io.github.julystar.musicapp.source.storage.RemoteMetadataReader
import io.github.julystar.musicapp.source.storage.RemoteScannerRepository
import io.github.julystar.musicapp.core.data.StorageRepositoryImpl
import io.github.julystar.musicapp.core.data.OpenListAccountMaterial
import io.github.julystar.musicapp.core.data.OpenListAccountMaterialReader
import io.github.julystar.musicapp.core.data.OpenListAuthTransport
import io.github.julystar.musicapp.core.data.OpenListRustAuthTransport
import io.github.julystar.musicapp.core.data.OpenListBrowseTransport
import io.github.julystar.musicapp.core.data.OpenListRustBrowseTransport
import io.github.julystar.musicapp.core.data.OpenListSessionBrowseClient
import io.github.julystar.musicapp.core.data.OpenListSessionManager
import io.github.julystar.musicapp.core.domain.repository.StorageRepository
import io.github.julystar.musicapp.service.librarysync.data.AccountScopedLibrarySyncController
import io.github.julystar.musicapp.service.librarysync.domain.SourceAccountLibrarySyncController
import io.github.julystar.musicapp.source.api.MusicSourceRegistry
import io.github.julystar.musicapp.source.api.RemoteServerGateway
import io.github.julystar.musicapp.source.api.RemoteServerKind
import io.github.julystar.musicapp.source.api.LegacyStorageConnectionTester
import io.github.julystar.musicapp.source.api.OpenListAuthenticator
import io.github.julystar.musicapp.source.api.OpenListBrowseClient
import io.github.julystar.musicapp.source.api.LegacyStorageDirectoryLister
import io.github.julystar.musicapp.source.api.LegacyStoragePlaybackResolver
import io.github.julystar.musicapp.source.api.LegacyStorageSearchProvider
import io.github.julystar.musicapp.source.api.LegacySmbServerDirectoryLister
import io.github.julystar.musicapp.source.api.SourceListFailureReason
import io.github.julystar.musicapp.source.api.SourceListResult
import io.github.julystar.musicapp.source.local.LocalMusicSource
import io.github.julystar.musicapp.source.onedrive.OneDriveMusicSource
import io.github.julystar.musicapp.source.openlist.OpenListMusicSource
import io.github.julystar.musicapp.source.smb.SmbMusicSource
import io.github.julystar.musicapp.source.storage.BridgeLegacyPlaybackSessionFactory
import io.github.julystar.musicapp.source.storage.BridgeOpenListPlaybackSessionCreator
import io.github.julystar.musicapp.source.storage.LegacyPlaybackSessionFactory
import io.github.julystar.musicapp.source.storage.OpenListPlaybackSessionCreator
import io.github.julystar.musicapp.source.storage.OpenListPlaybackSessionFactory
import io.github.julystar.musicapp.source.storage.LegacyStorageLookup
import io.github.julystar.musicapp.source.storage.LiveStorageSearchProvider
import io.github.julystar.musicapp.source.storage.LiveStorageLookup
import io.github.julystar.musicapp.source.storage.RetainedLegacyStoragePlaybackResolver
import io.github.julystar.musicapp.source.storage.SessionManagerOpenListPlaybackSessionFactory
import io.github.julystar.musicapp.source.storage.RoomLegacyStorageSearchProvider
import io.github.julystar.musicapp.source.storage.StorageDirectoryLister
import io.github.julystar.musicapp.source.storage.toArgUpsertStorage
import io.github.julystar.musicapp.source.storage.toLegacyStorageIdOrNull
import io.github.julystar.musicapp.source.storage.toSourceListResult
import io.github.julystar.musicapp.source.storage.toSourceAuthResult
import io.github.julystar.musicapp.source.storage.toStorageType
import io.github.julystar.musicapp.source.webdav.WebDavMusicSource
import io.github.julystar.musicapp.source.server.RemoteServerGatewayImpl
import io.github.julystar.musicapp.source.server.NavidromeLibrarySyncCoordinator
import io.github.julystar.musicapp.source.server.OpenSubsonicLibrarySyncCoordinator
import io.github.julystar.musicapp.source.server.EmbyLibrarySyncCoordinator
import io.github.julystar.musicapp.source.server.RemoteServerLibrarySyncCoordinator
import io.github.julystar.musicapp.source.server.ServerMusicSource
import io.github.julystar.musicapp.source.server.SubsonicRemotePlaylistCoordinator
import org.koin.dsl.module
import org.koin.core.qualifier.named
import uniffi.app_backend.ListStorageEntryChildrenResp
import uniffi.app_backend.StorageId
import io.github.julystar.musicapp.database.SourceAccountDao
import io.github.julystar.musicapp.database.ProviderTypes
import io.github.julystar.musicapp.core.data.security.CredentialStore
import io.github.julystar.musicapp.core.domain.model.toStorageRouteIdOrNull

val sourceDataModule = module {
    single<OpenListAccountMaterialReader> {
        OpenListAccountMaterialReader { accountId ->
            val id = accountId.toStorageRouteIdOrNull() ?: return@OpenListAccountMaterialReader null
            val account = get<SourceAccountDao>().get(id) ?: return@OpenListAccountMaterialReader null
            if (account.providerType != ProviderTypes.OpenList) {
                return@OpenListAccountMaterialReader null
            }
            val credential = get<CredentialStore>().load(id) ?: return@OpenListAccountMaterialReader null
            OpenListAccountMaterial(accountId, account.endpoint.orEmpty(), credential, account.providerConfig)
        }
    }
    single<OpenListAuthTransport> { OpenListRustAuthTransport() }
    single { OpenListSessionManager(get(), get()) }
    single<OpenListAuthenticator> { get<OpenListSessionManager>() }
    single<OpenListBrowseTransport> { OpenListRustBrowseTransport() }
    single<OpenListBrowseClient> { OpenListSessionBrowseClient(get(), get(), get()) }
    single {
        StorageRepositoryImpl(
            get(), get(), get(), get(),
            openListAuthenticator = get(),
            openListSessionManager = get(),
            libraryRootDao = get(),
        )
    }
    single<StorageRepository> { get<StorageRepositoryImpl>() }
    single<LegacyStorageLookup> {
        val storageRepository = get<StorageRepositoryImpl>()
        LegacyStorageLookup { storageId ->
            storageRepository.storageForRust(storageId)
        }
    }
    single<LegacyPlaybackSessionFactory> {
        BridgeLegacyPlaybackSessionFactory(get())
    }
    single<OpenListPlaybackSessionCreator> {
        BridgeOpenListPlaybackSessionCreator(get())
    }
    single<OpenListPlaybackSessionFactory> {
        SessionManagerOpenListPlaybackSessionFactory(get(), get())
    }
    single<LegacyStoragePlaybackResolver> {
        RetainedLegacyStoragePlaybackResolver(
            storageLookup = get(),
            sessionFactory = get(),
            openListPlaybackSessionFactory = get(),
        )
    }
    single<LegacyStorageConnectionTester> {
        val storageRepository = get<StorageRepositoryImpl>()
        LegacyStorageConnectionTester { request ->
            storageRepository.test(request.toArgUpsertStorage()).toSourceAuthResult()
        }
    }
    single<LegacyStorageDirectoryLister> {
        val storageRepository = get<StorageRepositoryImpl>()
        val remoteScannerRepository = get<RemoteScannerRepository>()
        LegacyStorageDirectoryLister { accountId, directoryId, expectedStorageKind ->
            val expectedType = expectedStorageKind.toStorageType()
            val storageId = accountId.toLegacyStorageIdOrNull()
                ?: return@LegacyStorageDirectoryLister SourceListResult.Failure(
                    SourceListFailureReason.UnsupportedAccount
                )
            val storage = storageRepository.storageForRust(storageId)
                ?: return@LegacyStorageDirectoryLister SourceListResult.Failure(
                    SourceListFailureReason.UnsupportedAccount
                )
            if (storage.typ != expectedType) {
                return@LegacyStorageDirectoryLister SourceListResult.Failure(
                    SourceListFailureReason.UnsupportedAccount
                )
            }

            remoteScannerRepository
                .listDirectory(
                    storageId = storageId,
                    path = directoryId ?: "/",
                )
                .toSourceListResult(accountId)
        }
    }
    single<LegacySmbServerDirectoryLister> {
        val remoteScannerRepository = get<RemoteScannerRepository>()
        LegacySmbServerDirectoryLister { accountId, directoryId ->
            val storageId = accountId.toLegacyStorageIdOrNull()
                ?: return@LegacySmbServerDirectoryLister SourceListResult.Failure(
                    SourceListFailureReason.UnsupportedAccount
                )
            remoteScannerRepository
                .listSmbServerDirectory(storageId, directoryId ?: "/")
                .toSourceListResult(accountId)
        }
    }
    single<LegacyStorageSearchProvider> {
        val storageRepository = get<StorageRepositoryImpl>()
        RoomLegacyStorageSearchProvider(
            storageLookup = { storageId ->
                storageRepository.storageForRust(storageId)
            },
            trackDao = get(),
        )
    }
    single<LegacyStorageSearchProvider>(named("liveSearch")) {
        val storageRepository = get<StorageRepositoryImpl>()
        LiveStorageSearchProvider(
            directoryLister = get(),
            storageLookup = LiveStorageLookup { storageId ->
                storageRepository.storageForRust(storageId)
            },
        )
    }
    single<StorageDirectoryLister> {
        val remoteScannerRepository = get<RemoteScannerRepository>()
        object : StorageDirectoryLister {
            override suspend fun listDirectory(
                storageId: StorageId,
                path: String,
            ): ListStorageEntryChildrenResp {
                return remoteScannerRepository.listDirectory(storageId, path)
            }
        }
    }
    single { LocalMusicSource(get(), get(), get(named("liveSearch"))) }
    single { WebDavMusicSource(get(), get(), get(), get(named("liveSearch"))) }
    single { OneDriveMusicSource(get(), get(), get(), get(named("liveSearch"))) }
    single {
        OpenListMusicSource(
            authenticator = get(),
            playbackResolver = get(),
            searchProvider = get(named("liveSearch")),
            browseClient = get(),
        )
    }
    single { SmbMusicSource(get(), get(), get(), get(named("liveSearch")), get()) }
    single<RemoteServerGateway> { RemoteServerGatewayImpl(get(), get()) }
    single { NavidromeLibrarySyncCoordinator(get(), get(), get(), get(), get(), get(), get()) }
    single { OpenSubsonicLibrarySyncCoordinator(get(), get(), get(), get(), get(), get(), get()) }
    single { EmbyLibrarySyncCoordinator(get(), get(), get(), get(), get(), get(), get()) }
    single { RemoteServerLibrarySyncCoordinator(get(), get(), get(), get()) }
    single<SourceAccountLibrarySyncController> {
        AccountScopedLibrarySyncController(get(), get(), get(), get(), get())
    }
    single { SubsonicRemotePlaylistCoordinator(get(), get()) }
    single(named("navidromeSource")) {
        ServerMusicSource(RemoteServerKind.Navidrome, get())
    }
    single(named("openSubsonicSource")) {
        ServerMusicSource(RemoteServerKind.OpenSubsonic, get())
    }
    single(named("embySource")) {
        ServerMusicSource(RemoteServerKind.Emby, get())
    }
    single {
        MusicSourceRegistry(
            listOf(
                get<LocalMusicSource>(),
                get<WebDavMusicSource>(),
                get<OneDriveMusicSource>(),
                get<OpenListMusicSource>(),
                get<SmbMusicSource>(),
                get<ServerMusicSource>(named("navidromeSource")),
                get<ServerMusicSource>(named("openSubsonicSource")),
                get<ServerMusicSource>(named("embySource")),
            )
        )
    }
    single { MetadataRepository(get(), get(), getOrNull()) }
    single<RemoteMetadataReader> { get<MetadataRepository>() }
    single { RemoteScannerRepository(get(), get(), getOrNull()) }
    single {
        RemoteLibraryImportCoordinator(
            get(), get(), get(), get(), get(), get(), get(), get(), get()
        )
    }
}
