package io.github.julystar.musicapp.di

import kotlin.test.Test
import kotlin.test.assertSame
import okio.FileSystem
import org.koin.dsl.koinApplication

class LibraryFeatureModuleTest {
    @Test
    fun providesSystemFileSystemForArtworkComponents() {
        val koinApplication = koinApplication {
            modules(libraryFeatureModule)
        }

        try {
            assertSame(FileSystem.SYSTEM, koinApplication.koin.get<FileSystem>())
        } finally {
            koinApplication.close()
        }
    }
}
