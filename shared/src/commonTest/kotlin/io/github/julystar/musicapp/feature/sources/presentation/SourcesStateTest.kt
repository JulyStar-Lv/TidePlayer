package io.github.julystar.musicapp.feature.sources.presentation

import io.github.julystar.musicapp.core.domain.model.StorageAccountInfo
import io.github.julystar.musicapp.core.data.toSourceAccountId
import io.github.julystar.musicapp.core.domain.model.toStorageRouteIdOrNull
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.core.domain.model.sanitizeSourceEndpointForDisplay
import io.github.julystar.musicapp.core.domain.model.sanitizeSourceTitleForDisplay
import io.github.julystar.musicapp.source.api.BuiltInSourceIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import uniffi.app_backend.StorageId

class SourcesStateTest {
    @Test
    fun mapsOnlyRemoteSourcesForPresentation() {
        val accounts = listOf(
            StorageAccountInfo(
                accountId = SourceAccountId("storage:1"),
                sourceId = BuiltInSourceIds.Local,
                isLocal = true,
                isOneDrive = false,
                title = "Local",
                subtitle = "",
                musicCount = 0,
            ),
            StorageAccountInfo(
                accountId = SourceAccountId("storage:2"),
                sourceId = BuiltInSourceIds.WebDav,
                isLocal = false,
                isOneDrive = false,
                title = "https://dav.example.com/music",
                subtitle = "https://dav.example.com/music",
                musicCount = 10,
            ),
            StorageAccountInfo(
                accountId = SourceAccountId("storage:3"),
                sourceId = BuiltInSourceIds.OneDrive,
                isLocal = false,
                isOneDrive = false,
                title = "OneDrive",
                subtitle = "drive-id",
                musicCount = 5,
            ),
        )

        val sources = accounts
            .filter { account -> !account.isLocal }
            .map { account ->
                val sourceType = if (account.sourceId == BuiltInSourceIds.WebDav) "WebDAV" else "OneDrive"
                SourceAccountUi(
                    id = account.accountId,
                    title = sanitizeSourceTitleForDisplay(account.title, account.subtitle, sourceType),
                    safeEndpoint = sanitizeSourceEndpointForDisplay(account.subtitle),
                    sourceType = sourceType,
                    musicCount = account.musicCount,
                )
            }

        assertEquals(2, sources.size)
        assertEquals(
            SourceAccountUi(
                id = SourceAccountId("storage:2"),
                title = "WebDAV",
                safeEndpoint = "https://dav.example.com",
                sourceType = "WebDAV",
                musicCount = 10,
            ),
            sources[0],
        )
        assertEquals(
            SourceAccountUi(
                id = SourceAccountId("storage:3"),
                title = "OneDrive",
                safeEndpoint = null,
                sourceType = "OneDrive",
                musicCount = 5,
            ),
            sources[1],
        )
    }

    @Test
    fun mapsTransitionalSourceAccountIdToStorageRouteId() {
        assertEquals(
            42,
            StorageId(42).toSourceAccountId().toStorageRouteIdOrNull(),
        )
        assertNull(SourceAccountId("webdav:account").toStorageRouteIdOrNull())
    }
}
