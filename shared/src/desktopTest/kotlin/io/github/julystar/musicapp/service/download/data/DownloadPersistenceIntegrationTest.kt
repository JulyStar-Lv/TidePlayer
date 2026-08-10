package io.github.julystar.musicapp.service.download.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.julystar.musicapp.core.domain.model.MediaId
import io.github.julystar.musicapp.core.domain.model.MediaType
import io.github.julystar.musicapp.core.domain.model.SourceId
import io.github.julystar.musicapp.database.MIGRATION_3_4
import io.github.julystar.musicapp.database.MIGRATION_20_21
import io.github.julystar.musicapp.database.AppDatabase
import io.github.julystar.musicapp.database.AppDatabaseConstructor
import io.github.julystar.musicapp.service.download.domain.DownloadStatus
import io.github.julystar.musicapp.service.download.domain.DownloadTask
import io.github.julystar.musicapp.service.download.domain.DownloadTaskId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DownloadPersistenceIntegrationTest {
    @Test
    fun migrationTwentyToTwentyOneAddsFinalizationWarning() {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            connection.prepare(
                "CREATE TABLE download_task (id TEXT NOT NULL PRIMARY KEY, status TEXT NOT NULL)"
            ).use { statement -> statement.step() }
            connection.prepare(
                "INSERT INTO download_task VALUES ('download-1', 'Completed')"
            ).use { statement -> statement.step() }

            MIGRATION_20_21.migrate(connection)

            val columns = buildSet {
                connection.prepare("PRAGMA table_info(download_task)").use { statement ->
                    while (statement.step()) add(statement.getText(1))
                }
            }
            assertTrue("finalizationWarning" in columns)
            connection.prepare(
                "SELECT finalizationWarning FROM download_task WHERE id = 'download-1'"
            ).use { statement ->
                assertTrue(statement.step())
                assertTrue(statement.isNull(0))
            }
        } finally {
            connection.close()
        }
    }

    @Test
    fun migrationThreeToFourAddsDownloadTaskTable() {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            MIGRATION_3_4.migrate(connection)

            val tables = buildSet {
                connection.prepare(
                    "SELECT name FROM sqlite_master WHERE type = 'table'"
                ).use { statement ->
                    while (statement.step()) {
                        add(statement.getText(0))
                    }
                }
            }
            val columns = buildSet {
                connection.prepare("PRAGMA table_info(download_task)").use { statement ->
                    while (statement.step()) {
                        add(statement.getText(1))
                    }
                }
            }

            assertTrue("download_task" in tables)
            assertTrue("sourceId" in columns)
            assertTrue("mediaType" in columns)
            assertTrue("status" in columns)
            assertTrue("localPath" in columns)
        } finally {
            connection.close()
        }
    }

    @Test
    fun repositoryPersistsAndObservesDownloadTasks() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder<AppDatabase> {
            AppDatabaseConstructor.initialize()
        }
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
        try {
            val repository = RoomDownloadTaskRepository(database.downloadTaskDao())
            val task = DownloadTask(
                id = DownloadTaskId("download-1"),
                mediaId = MediaId(
                    sourceId = SourceId("webdav"),
                    mediaType = MediaType.Track,
                    remoteId = "music/song.flac",
                ),
                title = "Song",
                status = DownloadStatus.Queued,
                downloadedBytes = 0,
                totalBytes = 100,
                createdAtEpochMs = 1,
                updatedAtEpochMs = 1,
            )

            repository.upsertTask(task)
            repository.updateTask(
                task.copy(
                    status = DownloadStatus.Downloading,
                    downloadedBytes = 40,
                    updatedAtEpochMs = 2,
                )
            )

            assertEquals(DownloadStatus.Downloading, repository.getTask(task.id)?.status)
            assertEquals(40, repository.getTask(task.id)?.downloadedBytes)
            assertEquals(listOf(task.id), repository.observeActiveTasks().first().map { it.id })
        } finally {
            database.close()
        }
    }
}
