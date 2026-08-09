package io.github.julystar.musicapp.di

import io.github.julystar.musicapp.domain.importing.RemoteLibraryImportCoordinator
import io.github.julystar.musicapp.source.storage.MetadataRepository
import io.github.julystar.musicapp.source.storage.RemoteMetadataReader
import io.github.julystar.musicapp.source.storage.RemoteScannerRepository
import io.github.julystar.musicapp.core.data.StorageRepositoryImpl
import io.github.julystar.musicapp.core.domain.repository.StorageRepository
import io.github.julystar.musicapp.source.api.MusicSourceRegistry
import io.github.julystar.musicapp.source.api.RemoteServerGateway
import io.github.julystar.musicapp.source.api.RemoteServerKind
import io.github.julystar.musicapp.source.api.LegacyStorageConnectionTester
import io.github.julystar.musicapp.source.api.LegacyStorageDirectoryLister
import io.github.julystar.musicapp.source.api.LegacyStoragePlaybackResolver
import io.github.julystar.musicapp.source.api.LegacyStorageSearchProvider
import io.github.julystar.musicapp.source.api.LegacySmbServerDirectoryLister
import io.github.julystar.musicapp.source.api.SourceListFailureReason
import io.github.julystar.musicapp.source.api.SourceListResult
import io.github.julystar.musicapp.source.local.LocalMusicSource
import io.github.julystar.musicapp.source.onedrive.OneDriveMusicSource
import io.github.julystar.musicapp.source.smb.SmbMusicSource
import io.github.julystar.musicapp.source.storage.BridgeLegacyPlaybackSessionFactory
import io.github.julystar.musicapp.source.storage.LegacyPlaybackSessionFactory
import io.github.julystar.musicapp.source.storage.LegacyStorageLookup
import io.github.julystar.musicapp.source.storage.LiveStorageSearchProvider
import io.github.julystar.musicapp.source.storage.LiveStorageLookup
import io.github.julystar.musicapp.source.storage.RetainedLegacyStoragePlaybackResolver
import io.github.julystar.musicapp.source.storage.RoomLegacyStorageSearchProvider
import io.github.julystar.musicapp.source.storage.StorageDirectoryLister
import io.github.julystar.musicapp.source.storage.toArgUpsertStorage
import io.github.julystar.musicapp.source.storage.toLegacyStorageIdOrNull
import io.github.julystar.musicapp.source.storage.toSourceListResult
import io.github.julystar.musicapp.source.storage.toSourceAuthResult
import io.github.julystar.musicapp.source.storage.toStorageType
import io.github.julystar.musicapp.source.webdav.WebDavMusicSource
import io.github.julystar.musicapp.source.server.RemoteServerGatewayImpl
import io.github.julystar.musicapp.source.server.ServerMusicSource
import org.koin.dsl.module
import org.koin.core.qualifier.named
import uniffi.app_backend.ListStorageEntryChildrenResp
import uniffi.app_backend.StorageId

val sourceDataModule = module {
    single { StorageRepositoryImpl(get(), get(), get(), get()) }
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
    single<LegacyStoragePlaybackResolver> {
        RetainedLegacyStoragePlaybackResolver(
            storageLookup = get(),
            sessionFactory = get(),
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
    single { SmbMusicSource(get(), get(), get(), get(named("liveSearch")), get()) }
    single<RemoteServerGateway> { RemoteServerGatewayImpl(get(), get()) }
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
                get<SmbMusicSource>(),
                get<ServerMusicSource>(named("navidromeSource")),
                get<ServerMusicSource>(named("openSubsonicSource")),
                get<ServerMusicSource>(named("embySource")),
            )
        )
    }
    single { MetadataRepository(get(), get()) }
    single<RemoteMetadataReader> { get<MetadataRepository>() }
    single { RemoteScannerRepository(get(), get()) }
    single {
        RemoteLibraryImportCoordinator(
            get(), get(), get(), get(), get(), get(), get(), get(), get()
        )
    }
}
