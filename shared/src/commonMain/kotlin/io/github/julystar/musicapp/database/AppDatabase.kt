package io.github.julystar.musicapp.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [
        SourceAccountEntity::class,
        LibraryRootEntity::class,
        SourceItemEntity::class,
        SourceItemPropertyEntity::class,
        TrackEntity::class,
        TrackSourceRefEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        TrackArtistCrossRef::class,
        AlbumArtistCrossRef::class,
        GenreEntity::class,
        TrackGenreCrossRef::class,
        ArtworkEntity::class,
        LyricsEntity::class,
        RawMetadataEntity::class,
        ImportJobEntity::class,
        SourceSyncCursorEntity::class,
        SourceErrorEntity::class,
        DownloadTaskEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        TrackFts::class,
        PluginEntity::class,
        PluginConfigEntity::class,
        ListeningHistoryEntity::class,
    ],
    version = APP_DATABASE_VERSION,
    exportSchema = true,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sourceAccountDao(): SourceAccountDao
    abstract fun libraryRootDao(): LibraryRootDao
    abstract fun sourceItemDao(): SourceItemDao
    abstract fun trackSourceRefDao(): TrackSourceRefDao
    abstract fun trackDao(): TrackDao
    abstract fun trackMergeDao(): TrackMergeDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun metadataDao(): MetadataDao
    abstract fun syncDao(): SyncDao
    abstract fun sourceSyncCursorDao(): SourceSyncCursorDao
    abstract fun sourceErrorDao(): SourceErrorDao
    abstract fun downloadTaskDao(): DownloadTaskDao
    abstract fun trackFtsDao(): TrackFtsDao
    abstract fun pluginDao(): PluginDao
    abstract fun appDataDao(): AppDataDao
    abstract fun listeningStatisticsDao(): ListeningStatisticsDao
}

const val APP_DATABASE_VERSION = 24

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

expect fun databaseBuilder(): RoomDatabase.Builder<AppDatabase>

fun buildDatabase(): AppDatabase = databaseBuilder()
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.Default)
    .addMigrations(MIGRATION_1_2)
    .addMigrations(MIGRATION_2_3)
    .addMigrations(MIGRATION_3_4)
    .addMigrations(MIGRATION_4_5)
    .addMigrations(MIGRATION_5_6)
    .addMigrations(MIGRATION_6_7)
    .addMigrations(MIGRATION_7_8)
    .addMigrations(MIGRATION_8_9)
    .addMigrations(MIGRATION_9_10)
    .addMigrations(MIGRATION_10_11)
    .addMigrations(MIGRATION_11_12)
    .addMigrations(MIGRATION_12_13)
    .addMigrations(MIGRATION_13_14)
    .addMigrations(MIGRATION_14_15)
    .addMigrations(MIGRATION_15_16)
    .addMigrations(MIGRATION_16_17)
    .addMigrations(MIGRATION_17_18)
    .addMigrations(MIGRATION_18_19)
    .addMigrations(MIGRATION_19_20)
    .addMigrations(MIGRATION_20_21)
    .addMigrations(MIGRATION_21_22)
    .addMigrations(MIGRATION_22_23)
    .addMigrations(MIGRATION_23_24)
    .build()
