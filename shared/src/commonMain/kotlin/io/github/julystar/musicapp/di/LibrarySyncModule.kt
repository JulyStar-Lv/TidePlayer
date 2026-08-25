package io.github.julystar.musicapp.di

import io.github.julystar.musicapp.domain.importing.RemoteMetadataRefreshController
import io.github.julystar.musicapp.domain.importing.TrackMetadataPrefetcher
import io.github.julystar.musicapp.service.librarysync.data.LegacyLibrarySyncController
import io.github.julystar.musicapp.service.librarysync.data.LegacyLibrarySyncImporter
import io.github.julystar.musicapp.service.librarysync.data.LegacyLibrarySyncStorageProvider
import io.github.julystar.musicapp.service.librarysync.data.RemoteLibraryImportGateway
import io.github.julystar.musicapp.service.librarysync.data.RoomLibrarySyncTaskRepository
import io.github.julystar.musicapp.service.librarysync.data.di.librarySyncDataModule
import io.github.julystar.musicapp.service.librarysync.domain.LibrarySyncController
import io.github.julystar.musicapp.service.librarysync.domain.LibrarySyncTaskRepository
import io.github.julystar.musicapp.service.librarysync.domain.MetadataRefreshController
import io.github.julystar.musicapp.core.data.StorageRepositoryImpl
import org.koin.dsl.module

val librarySyncModule = module {
    includes(librarySyncDataModule)
    single<LibrarySyncTaskRepository> { RoomLibrarySyncTaskRepository(get(), get()) }
    single<LegacyLibrarySyncImporter> { RemoteLibraryImportGateway(get()) }
    single<LegacyLibrarySyncStorageProvider> {
        val storageRepository = get<StorageRepositoryImpl>()
        LegacyLibrarySyncStorageProvider { storageId ->
            storageRepository.storages.value.firstOrNull { storage -> storage.id == storageId }
        }
    }
    single<LibrarySyncController> { LegacyLibrarySyncController(get(), get(), get(), get()) }
    single<MetadataRefreshController> { RemoteMetadataRefreshController(get(), get(), get()) }
    single { TrackMetadataPrefetcher(get(), get(), get()) }
}
