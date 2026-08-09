package io.github.julystar.musicapp.migration

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopDataMigrationTest {
    @Test
    fun migratesPreviousBrandDirectoryBeforeOriginalLegacyDirectory() {
        val root = Files.createTempDirectory("musicapp-brand-migration-")
        try {
            val previous = root.resolve("MelodyTrove").createDirectories()
            val original = root.resolve(".tidetunes").createDirectories()
            val destination = root.resolve("TidePlayer")
            val previousDatabase = "SQLite format 3\u0000previous".encodeToByteArray()
            val originalDatabase = "SQLite format 3\u0000original".encodeToByteArray()

            previous.resolve(AppIdentifiers.DATABASE_FILE).writeBytes(previousDatabase)
            previous.resolve(AppIdentifiers.PREFERENCES_FILE).writeText("previous-preferences")
            original.resolve(LegacyPaths.DATABASE_FILE).writeBytes(originalDatabase)

            assertEquals(
                destination,
                DesktopDataMigration.ensureMigrated(
                    destination,
                    listOf(previous, original),
                ),
            )
            assertContentEquals(
                previousDatabase,
                destination.resolve(AppIdentifiers.DATABASE_FILE).readBytes(),
            )
            assertEquals(
                "previous-preferences",
                destination.resolve(AppIdentifiers.PREFERENCES_FILE).readText(),
            )
            assertTrue(
                original.resolve(LegacyPaths.DATABASE_FILE).exists(),
                "Older legacy data must not overwrite the immediately previous brand data",
            )
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun migratesLegacyFilesAndDirectoriesIdempotently() {
        val root = Files.createTempDirectory("musicapp-data-migration-")
        try {
            val legacy = root.resolve("legacy").createDirectories()
            val destination = root.resolve("new")
            val database = "SQLite format 3\u0000test-database".encodeToByteArray()
            legacy.resolve(LegacyPaths.DATABASE_FILE).writeBytes(database)
            legacy.resolve(LegacyPaths.DATABASE_WAL_FILE).writeText("wal")
            legacy.resolve(LegacyPaths.DATABASE_SHM_FILE).writeText("shm")
            legacy.resolve(LegacyPaths.PREFERENCES_FILE).writeText("preferences")
            legacy.resolve("plugins").createDirectories().resolve("plugin.zip").writeText("plugin")

            assertEquals(
                destination,
                DesktopDataMigration.ensureMigrated(destination, legacy),
            )
            assertContentEquals(database, destination.resolve(AppIdentifiers.DATABASE_FILE).readBytes())
            assertEquals(
                "preferences",
                destination.resolve(AppIdentifiers.PREFERENCES_FILE).readText(),
            )
            assertEquals("plugin", destination.resolve("plugins/plugin.zip").readText())
            assertTrue(legacy.exists(), "The legacy root must remain recoverable")

            DesktopDataMigration.ensureMigrated(destination, legacy)
            assertContentEquals(database, destination.resolve(AppIdentifiers.DATABASE_FILE).readBytes())
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun preservesAlreadyInitializedDestination() {
        val root = Files.createTempDirectory("musicapp-data-existing-")
        try {
            val legacy = root.resolve("legacy").createDirectories()
            val destination = root.resolve("new").createDirectories()
            legacy.resolve(LegacyPaths.DATABASE_FILE)
                .writeBytes("SQLite format 3\u0000legacy".encodeToByteArray())
            destination.resolve(AppIdentifiers.DATABASE_FILE)
                .writeBytes("SQLite format 3\u0000current".encodeToByteArray())

            DesktopDataMigration.ensureMigrated(destination, legacy)

            assertContentEquals(
                "SQLite format 3\u0000current".encodeToByteArray(),
                destination.resolve(AppIdentifiers.DATABASE_FILE).readBytes(),
            )
            assertTrue(legacy.resolve(LegacyPaths.DATABASE_FILE).exists())
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun rejectsUnreadableLegacyDatabase() {
        val root = Files.createTempDirectory("musicapp-data-invalid-")
        try {
            val legacy = root.resolve("legacy").createDirectories()
            val destination = root.resolve("new")
            legacy.resolve(LegacyPaths.DATABASE_FILE).writeText("not-sqlite")

            assertFailsWith<IllegalStateException> {
                DesktopDataMigration.ensureMigrated(destination, legacy)
            }
            assertTrue(legacy.exists(), "A failed migration must not delete the legacy root")
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
