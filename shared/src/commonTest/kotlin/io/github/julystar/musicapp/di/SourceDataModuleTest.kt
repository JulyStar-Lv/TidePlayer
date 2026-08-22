package io.github.julystar.musicapp.di

import io.github.julystar.musicapp.source.api.OpenListBrowseClient
import kotlin.test.Test
import kotlin.test.assertNotNull
import org.koin.dsl.koinApplication

class SourceDataModuleTest {
    @Test
    fun resolvesOpenListBrowseClientByInterface() {
        val koinApplication = koinApplication {
            modules(sourceDataModule)
        }

        try {
            assertNotNull(koinApplication.koin.get<OpenListBrowseClient>())
        } finally {
            koinApplication.close()
        }
    }
}
